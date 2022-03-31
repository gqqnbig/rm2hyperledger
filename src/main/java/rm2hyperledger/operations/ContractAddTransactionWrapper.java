package rm2hyperledger.operations;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.TokenStreamRewriter;
import rm2hyperledger.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class ContractAddTransactionWrapper extends GitCommit {
	public HashMap<String, List<String>> contractTransactions = new HashMap<>();


	public ContractAddTransactionWrapper(String targetFolder) {
		super("Add transaction wrapper", targetFolder);
	}

	@Override
	protected ArrayList<Path> editCommitCore() throws IOException {
		ArrayList<Path> changedFiles = new ArrayList<>();

		Path servicesImplFolder = Path.of(targetFolder, "src\\main\\java\\services\\");
		assert Files.exists(servicesImplFolder);
		Files.list(servicesImplFolder).forEach(interfaceFile -> {
			if (Files.isDirectory(interfaceFile))
				return;

			try {
				ArrayList<String> methodsToRewrite = collectTransactionFunction(interfaceFile);
				if (methodsToRewrite == null || methodsToRewrite.size() == 0)
					return;

				var implementationFile = Path.of(servicesImplFolder.toString(), "impl", FileHelper.getFileNameWithoutExtension(interfaceFile.getFileName().toString()) + "Impl.java");

				CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(implementationFile)));
				TokenStreamRewriter rewriter = new TokenStreamRewriter(tokens);

				JavaParser parser = new JavaParser(tokens);
				var contractCollector = new AddWrapperVisitor(rewriter, methodsToRewrite);
				contractCollector.visit(parser.compilationUnit());

				try (PrintWriter out = new PrintWriter(implementationFile.toString())) {
					out.print(rewriter.getText());
				}

				contractTransactions.put(FileHelper.getFileNameWithoutExtension(implementationFile.getFileName().toString()), methodsToRewrite);

				changedFiles.add(implementationFile);
			}
			catch (IOException exception) {
				logger.severe(exception.toString());
			}
		});

		return changedFiles;

	}

	/**
	 * @param interfaceFile
	 * @return null if the file is not rewritable.
	 * @throws IOException
	 */
	private static ArrayList<String> collectTransactionFunction(Path interfaceFile) throws IOException {
		CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(interfaceFile)));

		JavaParser parser = new JavaParser(tokens);
		ServiceInterfaceConverter contractCollector = new ServiceInterfaceConverter();
		contractCollector.visit(parser.compilationUnit());

		if (contractCollector.getContractMethods().size() == 0)
			return null;

		if (interfaceFile.getFileName().toString().equals(contractCollector.getInterfaceName() + ".java") == false)
			ServiceInterfaceConverter.logger.warning(String.format("Interface %s found in file %s.", contractCollector.getInterfaceName(), interfaceFile.getFileName()));

		if (ServiceInterfaceConverter.logger.isLoggable(Level.FINE)) {
			String msg = contractCollector.getInterfaceName() + " has contracts: ";
			msg += String.join(", ", contractCollector.getContractMethods());
			ServiceInterfaceConverter.logger.fine(msg);
		}

		return contractCollector.getContractMethods();
	}


	static class AddWrapperVisitor extends ImportsCollector<TransactionIntent> {
		private final List<String> methodsToRewrite;

		public AddWrapperVisitor(TokenStreamRewriter rewriter, List<String> methodsToRewrite) {
			super(rewriter);
			this.methodsToRewrite = methodsToRewrite;
		}

//		@Override
//		public TransactionIntent visitMethodBody(JavaParser.MethodBodyContext ctx) {
//			var text = ctx.getText();
//			if (text.contains("ChaincodeStubstub=ctx.getStub();") == false && text.contains("EntityManager.stub=stub;") == false) {
//				rewriter.insertAfter(ctx.start, "\n\t\tChaincodeStub stub = ctx.getStub();\n\t\tEntityManager.stub = stub;");
//				newImports.add("org.hyperledger.fabric.shim.*");
//			}
//
//			return TransactionIntent.SUBMIT;
//			//		return super.visitMethodBody(ctx);
//		}

		String getWrapperText(JavaParser.MethodDeclarationContext ctx) {
			String identifier = ctx.IDENTIFIER().getText();
			if (methodsToRewrite.contains(identifier) == false)
				return null;

			String throwsList = null;
			if (ctx.qualifiedNameList() != null) {
				throwsList = ctx.qualifiedNameList().qualifiedName().stream().map(RuleContext::getText).collect(Collectors.joining(", "));
			}

			String parameters;
			String arguments;
			if (ctx.formalParameters().formalParameterList() != null) {
				parameters = ctx.formalParameters().formalParameterList().formalParameter().stream().
						map(p -> ", " + p.typeType().getText() + " " + p.variableDeclaratorId().getText()).collect(Collectors.joining());

				arguments = ctx.formalParameters().formalParameterList().formalParameter().stream().
						map(p -> p.variableDeclaratorId().IDENTIFIER().getText()).collect(Collectors.joining(", "));
			} else {
				parameters = "";
				arguments = "";
			}

			String[] lines;
			if (ctx.typeTypeOrVoid().getText().equals("void"))
				lines = new String[]{
						"@Transaction(intent = Transaction.TYPE.SUBMIT)",
						"public %1$s %2$s(final Context ctx%3$s)%4$s {",
						"\tChaincodeStub stub = ctx.getStub();",
						"\tEntityManager.setStub(stub);",
						"",
						"\t%2$s(%5$s);",
						"}"};
			else
				lines = new String[]{
						"@Transaction(intent = Transaction.TYPE.SUBMIT)",
						"public %1$s %2$s(final Context ctx%3$s)%4$s {",
						"\tChaincodeStub stub = ctx.getStub();",
						"\tEntityManager.setStub(stub);",
						"",
						"\tvar res = %2$s(%5$s);",
						"\treturn res;",
						"}"};


			FormatHelper.increaseIndent(lines, 1);
			String str = String.format(String.join("\n", lines),
					ctx.typeTypeOrVoid().getText(), identifier, parameters, throwsList == null ? "" : " throws " + throwsList, arguments);

			newImports.add("org.hyperledger.fabric.shim.*");
			newImports.add("org.hyperledger.fabric.contract.*");


			return str;
		}


		@Override
		public TransactionIntent visitClassBodyDeclaration(JavaParser.ClassBodyDeclarationContext ctx) {
			var memberDeclaration = ctx.memberDeclaration();
			if (memberDeclaration != null && memberDeclaration.methodDeclaration() != null) {
				String text = getWrapperText(memberDeclaration.methodDeclaration());
				if (text != null) {
					rewriter.insertBefore(ctx.start, "\n" + text + "\n\n\t");
				}
				return null;
			} else
				return super.visitClassBodyDeclaration(ctx);
		}

		@Override
		public TransactionIntent visitTypeDeclaration(JavaParser.TypeDeclarationContext ctx) {
			if (ctx.classDeclaration() != null) {
				rewriter.insertBefore(ctx.start, "@Contract\n");
				newImports.add("org.hyperledger.fabric.contract.annotation.*");

				var implementsList = ctx.classDeclaration().typeList();
				rewriter.insertAfter(implementsList.stop, ", ContractInterface");
				newImports.add("org.hyperledger.fabric.contract.*");
			}

			return super.visitTypeDeclaration(ctx);
		}

		private static boolean hasAnnotation(List<JavaParser.ModifierContext> trees, String annotation) {
			for (var tree : trees) {
				if (tree.classOrInterfaceModifier() == null)
					continue;

				var t1 = tree.classOrInterfaceModifier();
				if (t1.annotation() == null)
					continue;

				var t2 = t1.annotation();
				if (t2.qualifiedName() != null && t2.qualifiedName().getText().equals(annotation))
					return true;
			}
			return false;
		}
	}
}
