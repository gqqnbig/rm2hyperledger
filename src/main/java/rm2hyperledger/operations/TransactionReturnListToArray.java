package rm2hyperledger.operations;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStreamRewriter;
import rm2hyperledger.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class TransactionReturnListToArray extends GitCommit {
	private static final Logger logger = Logger.getLogger(TransactionReturnListToArray.class.getName());

	private ArrayList<Path> changedFiles;

	public TransactionReturnListToArray(String targetFolder) {
		super("Transactions cannot return list\n\nThey must return array.", targetFolder);
	}

	@Override
	protected ArrayList<Path> editCommitCore() throws IOException {
		changedFiles = new ArrayList<>();
		try (DirectoryStream<Path> stream2 = Files.newDirectoryStream(Path.of(targetFolder, "src\\main\\java\\services\\impl"), "*.java")) {
			for (var f : stream2)
				editFile(f);
		}
		return changedFiles;
	}

	private void editFile(Path f) {
		try {
			CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(f)));

			JavaParser parser = new JavaParser(tokens);
			TokenStreamRewriter2 rewriter = new TokenStreamRewriter2(tokens);
			var converter = new Converter(rewriter);

			converter.visit(parser.compilationUnit());
			if (rewriter.hasChanges()) {
				try (PrintWriter out = new PrintWriter(f.toFile())) {
					out.print(rewriter.getText());
				}
				changedFiles.add(f);
			}
		}
		catch (IOException exception) {
			logger.severe(exception.toString());
		}
	}

	static class Converter extends JavaParserBaseVisitor<Object> {

		private final TokenStreamRewriter rewriter;
		private String returnBaseType = null;
		public boolean changed = false;

		public Converter(TokenStreamRewriter rewriter) {
			this.rewriter = rewriter;
		}

		@Override
		public Object visitInterfaceMethodDeclaration(JavaParser.InterfaceMethodDeclarationContext ctx) {
			if (convertReturnType(ctx.typeTypeOrVoid()) != null)
				return null;

			return super.visitInterfaceMethodDeclaration(ctx);
		}

		@Override
		public Object visitClassBodyDeclaration(JavaParser.ClassBodyDeclarationContext ctx) {
			if (ctx.modifier().stream().anyMatch(m -> m.getText().contains("@Transaction"))) {
				var methodDeclaration = ctx.memberDeclaration().methodDeclaration();

				if (methodDeclaration != null) {
					String returnType = convertReturnType(methodDeclaration.typeTypeOrVoid());
					if (returnType != null) {
						returnBaseType = returnType;
						visitMethodBody(methodDeclaration.methodBody());
						returnBaseType = null;
						return null;
					}
				}
			}

			return super.visitClassBodyDeclaration(ctx);
		}

		@Override
		public Object visitStatement(JavaParser.StatementContext ctx) {
			if (returnBaseType != null && ctx.RETURN() != null && ctx.expression().size() == 1) {
				var expression = ctx.expression(0);
				rewriter.insertAfter(expression.stop, ".toArray(" + returnBaseType + "[]::new)");
				return null;
			}

			return super.visitStatement(ctx);
		}

		private String convertReturnType(JavaParser.TypeTypeOrVoidContext typeTypeOrVoid) {
			var pattern = Pattern.compile("List<([\\w\\d_]+)>");
			var m = pattern.matcher(typeTypeOrVoid.getText());
			if (m.matches()) {
				rewriter.replace(typeTypeOrVoid.start, typeTypeOrVoid.stop, m.group(1) + "[]");
				return m.group(1);
			}
			return null;
		}
	}
}
