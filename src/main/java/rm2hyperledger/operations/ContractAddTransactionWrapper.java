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
import java.util.*;
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
		private final Set<String> serializableTypes = Set.of("byte", "short", "int", "long", "float", "double", "boolean", "char", "String");

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
			String customGenson = null;
			if (ctx.formalParameters().formalParameterList() != null) {
				var formalParameterContexts = ctx.formalParameters().formalParameterList().formalParameter();
				if (formalParameterContexts.stream().anyMatch(p -> p.typeType().getText().equals("LocalDate"))) {
					customGenson = "\tvar genson = new GensonBuilder().withConverters(new LocalDateConverter()).create();";
					// We do not add "com.owlike.genson.*" because com.owlike.genson.Context conflicts with the org.hyperledger.fabric.contract.Context.
					newImports.add("com.owlike.genson.GensonBuilder");
					newImports.add("converters.*");
				}

				parameters = formalParameterContexts.stream().
						map(p -> {
							return ", " + (serializableTypes.contains(p.typeType().getText()) ? p.typeType().getText() : "String") +
									" " + p.variableDeclaratorId().getText();
						}).collect(Collectors.joining());

				arguments = formalParameterContexts.stream().
						map(p -> {
							String type = p.typeType().getText();
							if (serializableTypes.contains(type))
								return p.variableDeclaratorId().IDENTIFIER().getText();
							else if ("LocalDate".equals(type))
								return String.format("genson.deserialize(%s, %s.class)", "\"\\\"\" + " + p.variableDeclaratorId().IDENTIFIER().getText() + " + \"\\\"\"", type);
							else
								return String.format("genson.deserialize(%s, %s.class)", p.variableDeclaratorId().IDENTIFIER().getText(), type);
						}).collect(Collectors.joining(", "));
			} else {
				parameters = "";
				arguments = "";
			}

			ArrayList<String> lines;
			if (ctx.typeTypeOrVoid().getText().equals("void"))
				lines = new ArrayList<>(List.of(
						"@Transaction(intent = Transaction.TYPE.SUBMIT)",
						"public %1$s %2$s(final Context ctx%3$s)%4$s {",
						"\tChaincodeStub stub = ctx.getStub();",
						"\tEntityManager.setStub(stub);",
						"",
						"\t%2$s(%5$s);",
						"}"));
			else
				lines = new ArrayList<>(List.of(
						"@Transaction(intent = Transaction.TYPE.SUBMIT)",
						"public %1$s %2$s(final Context ctx%3$s)%4$s {",
						"\tChaincodeStub stub = ctx.getStub();",
						"\tEntityManager.setStub(stub);",
						"",
						"\tvar res = %2$s(%5$s);",
						"\treturn res;",
						"}"));
			if (customGenson != null)
				lines.add(5, customGenson);
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
