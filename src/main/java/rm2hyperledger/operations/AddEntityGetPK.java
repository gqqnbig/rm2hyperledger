package rm2hyperledger.operations;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStreamRewriter;
import org.antlr.v4.runtime.tree.TerminalNode;
import rm2hyperledger.*;
import rm2hyperledger.checkers.EntityChecker;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class AddEntityGetPK extends GitCommit {
	private final List<FieldDefinition> pkMap;

	public AddEntityGetPK(String targetFolder, List<FieldDefinition> pkMap) {
		super("Add getPK() to each entity class", targetFolder);
		this.pkMap = pkMap;
	}

	@Override
	protected ArrayList<Path> editCommitCore() throws IOException {
		ArrayList<Path> changedFiles = new ArrayList<>();

		Files.list(Path.of(targetFolder, "src\\main\\java\\entities")).forEach(file -> {
			try {
				var fileNameWithoutExtension = FileHelper.getFileNameWithoutExtension(file.getFileName().toString());
				if (fileNameWithoutExtension.equals("EntityManager"))
					return;
				if (EntityChecker.isEntityClass(file) == false)
					return;

				var pk = pkMap.stream().filter(s -> s.ClassName.equals(fileNameWithoutExtension)).findFirst();

				String[] getPK = new String[]{
						"public Object getPK() {",
						"\treturn %s;",
						"}"};
				if (pk.isPresent())
					getPK[1] = String.format(getPK[1], "get" + StringHelper.uppercaseFirstLetter(pk.get().VariableName) + "()");
				else
					getPK[1] = String.format(getPK[1], "guid");


				CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(file)));
				JavaParser parser = new JavaParser(tokens);
				TokenStreamRewriter rewriter = new TokenStreamRewriter(tokens);

				var v = new AddClassMemberVisitor(rewriter, AddClassMemberVisitor.EditLocation.Start, getPK);
				v.visitCompilationUnit(parser.compilationUnit());
				try (PrintWriter out = new PrintWriter(file.toFile())) {
					out.print(rewriter.getText());
				}

				if (pk.isPresent() == false) {
					// add guid
					tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(file)));
					parser = new JavaParser(tokens);
					rewriter = new TokenStreamRewriter(tokens);

					var converter = new EntityGuidAdder(rewriter);

					converter.visit(parser.compilationUnit());
					try (PrintWriter out = new PrintWriter(file.toFile())) {
						out.print(rewriter.getText());
					}
					FieldDefinition e = new FieldDefinition(converter.className, "guid", "String");

					pkMap.add(e);
				}

				changedFiles.add(file);
			}
			catch (IOException exception) {
				logger.severe(exception.toString());
			}
		});

		return changedFiles;
	}

	static class EntityGuidAdder extends ImportsCollector<Object> {
		private String className = "";

		protected EntityGuidAdder(TokenStreamRewriter rewriter) {
			super(rewriter);
		}

		@Override
		public Object visitClassDeclaration(JavaParser.ClassDeclarationContext ctx) {
			var implementsContext = ctx.typeList();
			if (implementsContext != null && implementsContext.getText().contains("Serializable")) {
				className = ctx.IDENTIFIER().getText();
				return super.visitClassDeclaration(ctx);
			} else
				return null;
		}

		@Override
		public Object visitClassBody(JavaParser.ClassBodyContext ctx) {
			rewriter.insertAfter(((TerminalNode) ctx.children.get(0)).getSymbol(),
					"\n\n" +
							"\t// Without @JsonProperty, genson will not set this field during deserialization.\n" +
							"\t@JsonProperty\n" +
							"\tprivate final String guid = EntityManager.getGuid();");
			super.newImports.add("com.owlike.genson.annotation.*");
			return null;
		}
	}
}
