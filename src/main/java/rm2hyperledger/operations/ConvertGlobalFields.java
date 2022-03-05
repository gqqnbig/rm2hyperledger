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
import java.util.HashSet;

public class ConvertGlobalFields extends GitCommit {


	private final String remodelFile;

	public ConvertGlobalFields(String targetFolder, String remodelFile) {
		super("Convert global fields", targetFolder);
		this.remodelFile = remodelFile;
	}

	@Override
	protected ArrayList<Path> editCommitCore() throws IOException {
		var fileName = FileHelper.getFileNameWithoutExtension(Path.of(remodelFile).getFileName().toString());

		System.out.println(fileName);

		var systemFile = Path.of(targetFolder, "src\\main\\java\\services\\", fileName + "System.java");

		var fields = SystemFieldsCollector.collect(systemFile);


		ArrayList<Path> changedFiles = new ArrayList<>();
		try (DirectoryStream<Path> files = Files.newDirectoryStream(Path.of(targetFolder, "src\\main\\java\\services\\impl"), "*.java")) {
			for (var f : files) {
				try {
					CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(f)));

					JavaParser parser = new JavaParser(tokens);
					TokenStreamRewriter2 rewriter = new TokenStreamRewriter2(tokens);
					var converter = new PKAdder(rewriter, fields);

					converter.visit(parser.compilationUnit());
					if (rewriter.hasChanges()) {
						try (PrintWriter out = new PrintWriter(f.toFile())) {
							out.print(rewriter.getText());
						}
						changedFiles.add(f);
					}
				}
				catch (IOException exception) {
					logger.severe(exception.getMessage());
				}
			}
		}

		return changedFiles;
	}


	static class PKAdder extends JavaParserBaseVisitor<Object> {

		private final TokenStreamRewriter rewriter;
		private final HashSet<String> globalFields;

		public PKAdder(TokenStreamRewriter rewriter, HashSet<String> globalFields) {
			this.rewriter = rewriter;

			this.globalFields = globalFields;
		}

		@Override
		public Object visitClassBodyDeclaration(JavaParser.ClassBodyDeclarationContext ctx) {
			return super.visitClassBodyDeclaration(ctx);
		}

		@Override
		public Object visitFieldDeclaration(JavaParser.FieldDeclarationContext ctx) {
			String fieldName = ctx.variableDeclarators().variableDeclarator(0).variableDeclaratorId().IDENTIFIER().getText();
			if (globalFields.contains(fieldName)) {
				rewriter.insertAfter(ctx.stop, "\n\tprivate Object " + fieldName + "PK;");
			}
			return null;
		}
	}


	/**
	 * Each REModel requires a service whose name is the file name appended with the word "System".
	 * <p>
	 * All fields in the system service will be shared to all other services.
	 */
	static class SystemFieldsCollector extends JavaParserBaseVisitor<Object> {

		public static HashSet<String> collect(Path systemFile) throws IOException {
			CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(systemFile)));
			JavaParser parser = new JavaParser(tokens);
			var collector = new SystemFieldsCollector();

			collector.visit(parser.compilationUnit());
			collector.getters.retainAll(collector.setters);

			return collector.getters;
		}


		HashSet<String> getters = new HashSet<>();
		HashSet<String> setters = new HashSet<>();

		private SystemFieldsCollector() {

		}


		@Override
		public Object visitInterfaceMethodDeclaration(JavaParser.InterfaceMethodDeclarationContext ctx) {
			var identifier = ctx.IDENTIFIER().getText().trim();
			if (identifier.startsWith("get") && ctx.typeTypeOrVoid().getText().equals("void") == false && ctx.formalParameters().children.size() == 2) {
				getters.add(lowercaseFirstLetter(identifier.substring(3)));
			} else if (identifier.startsWith("set") && ctx.typeTypeOrVoid().getText().equals("void") && ctx.formalParameters().children.size() == 3) {
				setters.add(lowercaseFirstLetter(identifier.substring(3)));
			}

			return null;
		}

		private static String lowercaseFirstLetter(String str) {
			if (Character.isUpperCase(str.charAt(0)))
				return Character.toLowerCase(str.charAt(0)) + str.substring(1);
			else
				return str;
		}
	}
}
