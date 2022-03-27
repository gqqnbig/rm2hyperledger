package rm2hyperledger;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStreamRewriter;
import org.antlr.v4.runtime.tree.TerminalNode;
import rm2hyperledger.checkers.ModifierChecker;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

public class EntityPKHelper {

	/**
	 * Add GUID to the class in the file. The GUID field becomes the PK.
	 *
	 * @param file
	 * @return
	 * @throws IOException
	 */
	public static FieldDefinition addGuid(Path file) throws IOException {

		CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(file)));
		JavaParser parser = new JavaParser(tokens);
		TokenStreamRewriter rewriter = new TokenStreamRewriter(tokens);
		var converter = new EntityGuidAdder(rewriter);

		converter.visit(parser.compilationUnit());
		try (PrintWriter out = new PrintWriter(file.toFile())) {
			out.print(rewriter.getText());
		}

		if (converter.isAdded)
			return new FieldDefinition(converter.className, "guid", "String");
		else
			return null;
	}

	static class EntityGuidAdder extends ImportsCollector<Object> {
		private boolean isAdded = false;
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

			isAdded = true;

			return null;
		}
	}


}