package rm2hyperledger;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStreamRewriter;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.Map;

public class EntityPKHelper {

	/**
	 * Add GUID to the class in the file. The GUID field becomes the PK.
	 *
	 * @param file
	 * @return
	 * @throws IOException
	 */
	public static Map.Entry<String, String> addGuid(Path file) throws IOException {

		CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(file)));
		JavaParser parser = new JavaParser(tokens);
		TokenStreamRewriter rewriter = new TokenStreamRewriter(tokens);
		var converter = new EntityGuidAdder(rewriter);

		converter.visit(parser.compilationUnit());
		try (PrintWriter out = new PrintWriter(file.toFile())) {
			out.print(rewriter.getText());
		}

		if (converter.isAdded)
			return new AbstractMap.SimpleEntry<>(converter.className, "guid");
		else
			return null;
	}

	public static void changeReferenceToPK(Path file, Map<String, String> pkMap) throws IOException {
		CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(file)));
		JavaParser parser = new JavaParser(tokens);
		TokenStreamRewriter rewriter = new TokenStreamRewriter(tokens);
		var converter = new EntityReferenceChanger(rewriter, pkMap);

		converter.visit(parser.compilationUnit());
		try (PrintWriter out = new PrintWriter(file.toFile())) {
			out.print(rewriter.getText());
		}
	}

	static class EntityGuidAdder extends JavaParserBaseVisitor<Object> {
		private final TokenStreamRewriter rewriter;
		private boolean isAdded = false;
		private String className = "";

		protected EntityGuidAdder(TokenStreamRewriter rewriter) {
			this.rewriter = rewriter;
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
			isAdded = true;

			return null;
		}
	}

	static class EntityReferenceChanger extends ImportsCollector<Object> {

		private final Map<String, String> pkMap;

		protected EntityReferenceChanger(TokenStreamRewriter rewriter, Map<String, String> pkMap) {
			super(rewriter);
			this.pkMap = pkMap;
		}

		@Override
		public Object visitClassBodyDeclaration(JavaParser.ClassBodyDeclarationContext ctx) {
			var memberDeclaration = ctx.memberDeclaration();
			if (memberDeclaration != null) {
				var fieldDeclaration = memberDeclaration.fieldDeclaration();
				if (fieldDeclaration != null) {
					var typeStr = fieldDeclaration.typeType().getText();
					String variableName = fieldDeclaration.variableDeclarators().getText();
					if (this.pkMap.containsKey(typeStr) && variableName.endsWith("PK") == false) {
						super.rewriter.replace(fieldDeclaration.typeType().start, fieldDeclaration.typeType().stop, "String");
						super.rewriter.replace(fieldDeclaration.variableDeclarators().start, fieldDeclaration.variableDeclarators().stop, variableName + "PK");

						super.rewriter.insertBefore(ctx.start, "@JsonProperty\n\t");
					}
				}
			}

			return super.visitClassBodyDeclaration(ctx);
		}
	}
}