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
		var converter = new FieldDefinitionConverter(rewriter, pkMap);

		converter.visit(parser.compilationUnit());
		try (PrintWriter out = new PrintWriter(file.toFile())) {
			out.print(rewriter.getText());
		}
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

	static class FieldDefinitionConverter extends JavaParserBaseVisitor<Object> {

		private final TokenStreamRewriter rewriter;
		private final Map<String, String> pkMap;

		/**
		 * contains the original field names
		 */
		public ArrayList<String> changedFields = new ArrayList<>();

		protected FieldDefinitionConverter(TokenStreamRewriter rewriter, Map<String, String> pkMap) {
			this.rewriter = rewriter;
			this.pkMap = pkMap;
		}

		@Override
		public Object visitClassBodyDeclaration(JavaParser.ClassBodyDeclarationContext ctx) {
			var memberDeclaration = ctx.memberDeclaration();
			if (memberDeclaration != null) {
				var fieldDeclaration = memberDeclaration.fieldDeclaration();
				if (fieldDeclaration != null) {
					var typeStr = fieldDeclaration.typeType().getText();
					var variableDeclarator = fieldDeclaration.variableDeclarators().variableDeclarator(0);
					String variableName = variableDeclarator.variableDeclaratorId().getText();
					if (this.pkMap.containsKey(typeStr) && variableName.endsWith("PK") == false) {
						rewriter.replace(fieldDeclaration.typeType().start, fieldDeclaration.typeType().stop, "String");
						rewriter.replace(variableDeclarator.start, variableDeclarator.stop, variableName + "PK");

						rewriter.insertBefore(ctx.start, "@JsonProperty\n\t");

						changedFields.add(variableName);
						return null;
					} else {
						var pattern = Pattern.compile("List<([\\w\\d_]+)>");
						var m = pattern.matcher(typeStr);
						if (m.matches() && this.pkMap.containsKey(m.group(1)) && variableName.endsWith("PKs") == false) {
							rewriter.replace(fieldDeclaration.typeType().start, fieldDeclaration.typeType().stop, "List<String>");
							rewriter.replace(variableDeclarator.variableDeclaratorId().start, variableDeclarator.variableDeclaratorId().stop, variableName + "PKs");
							if (variableDeclarator.variableInitializer() != null)
								rewriter.replace(variableDeclarator.variableInitializer().start, variableDeclarator.variableInitializer().stop,
										"new LinkedList<>()");

							rewriter.insertBefore(ctx.start, "@JsonProperty\n\t");

							return null;
						}
					}
				}
			}

			return super.visitClassBodyDeclaration(ctx);
		}
	}


	/**
	 * Add serialization support to entities
	 */
	static class FieldGetterSetterConverter extends ImportsCollector<Object> {
		private final CommonTokenStream tokens;


		private HashSet<String> methodParameters = new HashSet<>();

		/**
		 * old name -> new name
		 */
		private final Set<String> changedNames;

		public FieldGetterSetterConverter(Set<String> changedNames, CommonTokenStream tokens, TokenStreamRewriter rewriter) {
			super(rewriter);
			this.changedNames = changedNames;
			this.tokens = tokens;
		}

		@Override
		public Object visitTypeDeclaration(JavaParser.TypeDeclarationContext ctx) {

			if (ModifierChecker.hasCIModifier(ctx.classOrInterfaceModifier(), JavaParser.PUBLIC) && ctx.classDeclaration() != null) {
				rewriter.insertBefore(ctx.start, "@DataType()\n");
				newImports.add("org.hyperledger.fabric.contract.annotation.*");
				return visitClassBody(ctx.classDeclaration().classBody());
			}
			return super.visitTypeDeclaration(ctx);
		}



		boolean isInReferenceSection = false;

		@Override
		public Object visitClassBodyDeclaration(JavaParser.ClassBodyDeclarationContext ctx) {

			var memberDeclaration = ctx.memberDeclaration();
			if (memberDeclaration != null) {
				var fieldDeclaration = memberDeclaration.fieldDeclaration();
				if (fieldDeclaration != null && ModifierChecker.hasModifier(ctx.modifier(), JavaParser.PRIVATE)) {
					List<Token> hiddenTokens = tokens.getHiddenTokensToLeft(ctx.start.getTokenIndex(), 2);

					if (hiddenTokens != null && hiddenTokens.stream().anyMatch(t -> t.getText().equals("/* all references */")))
						isInReferenceSection = true;

					lowercaseFieldName(ctx, fieldDeclaration);
					return null;
				}
			}
			return super.visitClassBodyDeclaration(ctx);
		}

		@Override
		public Object visitMethodDeclaration(JavaParser.MethodDeclarationContext ctx) {
			var getterPattern= Pattern.compile("get([\\w\\d_]+)");
			var setterPattern= Pattern.compile("set([\\w\\d_]+)");

			var methodName=			ctx.IDENTIFIER().getText();
			var m=getterPattern.matcher(methodName);
			if(m.matches() && changedNames.contains(m.group(1)))
			{
				String s="var o = EntityManager.getCashPaymentByPK(AssoicatedPaymentPK);\n" +
						"if (o != null)\n" +
						"\treturn o;\n" +
						"p = EntityManager.getCardPaymentByPK(AssoicatedPaymentPK);\n" +
						"if (p != null)\n" +
						"\treturn p;\n" +
						"\n" +
						"return null;";

				return null;
			}


			m=setterPattern.matcher(methodName);
			if(m.matches() && changedNames.contains(m.group(1)))
			{

				return null;
			}

			return null;
		}

		private void lowercaseFieldName(JavaParser.ClassBodyDeclarationContext ctx, JavaParser.FieldDeclarationContext fieldDeclaration) {

//		if (primaryTypes.contains(fieldDeclaration.typeType().getText()) == false) {
//			logger.info(String.format("Ignore \"%s\" due to its type.", fieldDeclaration.getText()));
//			return;
//		}

			if (fieldDeclaration.variableDeclarators().children.size() != 1)
				throw new UnsupportedOperationException(String.format("\"%s\" is not supported. Each field must have its own declaration.", ctx.getText()));

			if (isInReferenceSection == false) {
				rewriter.insertBefore(ctx.start, "@Property()\n\t");
				newImports.add("org.hyperledger.fabric.contract.annotation.*");

				JavaParser.VariableDeclaratorContext v = fieldDeclaration.variableDeclarators().variableDeclarator(0);
				String name = v.getText();
				if (Character.isUpperCase(name.charAt(0))) {
					changedNames.add(name);
					name = lowercaseFirstLetter(name);

					rewriter.replace(v.start, v.stop, name);
				}
			}
		}

		private static String lowercaseFirstLetter(String name) {
			return Character.toLowerCase(name.charAt(0)) + name.substring(1);
		}

		boolean isInThis = false;

		@Override
		public Object visitPrimary(JavaParser.PrimaryContext ctx) {
			if (ctx.IDENTIFIER() != null && changedNames.contains(ctx.IDENTIFIER().getText())) {
				var name = ctx.IDENTIFIER().getText();
				if (methodParameters.contains(name) && isInThis == false)
					return null;

				rewriter.replace(ctx.start, ctx.stop, lowercaseFirstLetter(name));
				return null;
			} else
				return super.visitPrimary(ctx);
		}

		@Override
		public Object visitExpression(JavaParser.ExpressionContext ctx) {
			if (ctx.IDENTIFIER() != null && ctx.bop != null && ".".equals(ctx.bop.getText()) &&
					isThis((JavaParser.ExpressionContext) ctx.children.get(0))) {

				TerminalNode id = ctx.IDENTIFIER();
				if (changedNames.contains(id.getText())) {
					rewriter.replace(id.getSymbol().getTokenIndex(), lowercaseFirstLetter(id.getText()));
				}
				return null;
			}
			return super.visitExpression(ctx);
		}

		private static boolean isThis(JavaParser.ExpressionContext ctx) {
			return ctx.primary() != null && ctx.primary().THIS() != null;
		}
	}
}