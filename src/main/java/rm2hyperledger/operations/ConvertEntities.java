package rm2hyperledger.operations;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStreamRewriter;
import org.antlr.v4.runtime.tree.TerminalNode;
import rm2hyperledger.*;
import rm2hyperledger.checkers.ModifierChecker;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class ConvertEntities extends GitCommit {

	private final HashSet<String> entityNames = new HashSet<>();

	public ConvertEntities(String targetFolder) {
		super("Add general (de)serialization support to entity classes", targetFolder);
	}

	public HashSet<String> getEntityNames() {
		return entityNames;
	}

	@Override
	protected ArrayList<Path> editCommitCore() throws IOException {
		Path folder = Path.of(targetFolder, "src\\main\\java\\entities");
		assert Files.exists(folder);

		ArrayList<Path> changedFiles = new ArrayList<>();
		Files.list(folder).forEach(file -> {
			try {
				if (file.toString().endsWith("EntityManager.java"))
					return;

				CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(file)));


				JavaParser parser = new JavaParser(tokens);
				TokenStreamRewriter rewriter = new TokenStreamRewriter(tokens);
				var converter = new EntityConverter(tokens, rewriter);

				converter.visit(parser.compilationUnit());
				if (converter.entityName != null) {
					entityNames.add(converter.entityName);
					try (PrintWriter out = new PrintWriter(file.toFile())) {
						out.print(rewriter.getText());
					}
					changedFiles.add(file);
				}
			}
			catch (IOException exception) {
				logger.severe(exception.toString());
			}
		});

		return changedFiles;
	}

	/**
	 * Add serialization support to entities
	 */
	public static class EntityConverter extends ImportsCollector<Object> {
		private final CommonTokenStream tokens;


		private HashSet<String> methodParameters = new HashSet<>();

		/**
		 * The old name, uppercase name.
		 */
		private final HashSet<String> changedNames = new HashSet<>();

		public String entityName;

		public EntityConverter(CommonTokenStream tokens, TokenStreamRewriter rewriter) {
			super(rewriter);
			this.tokens = tokens;
		}

		@Override
		public Object visitTypeDeclaration(JavaParser.TypeDeclarationContext ctx) {

			if (ModifierChecker.hasCIModifier(ctx.classOrInterfaceModifier(), JavaParser.PUBLIC) && ctx.classDeclaration() != null) {
				var classDeclaration = ctx.classDeclaration();
				if (classDeclaration.typeList() != null && classDeclaration.typeList().getText().contains("Serializable")) {
					entityName = ctx.classDeclaration().IDENTIFIER().getText();
					rewriter.insertBefore(ctx.start, "@DataType()\n");
					newImports.add("org.hyperledger.fabric.contract.annotation.*");
					return visitClassBody(ctx.classDeclaration().classBody());
				}
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

			FormalParameterCollector collector = new FormalParameterCollector();
			collector.visit(ctx.formalParameters());
			methodParameters = collector.parameterNames;

			super.visit(ctx.methodBody());

			methodParameters = null;
			return null;
		}

		private void lowercaseFieldName(JavaParser.ClassBodyDeclarationContext ctx, JavaParser.FieldDeclarationContext fieldDeclaration) {

	//		if (primaryTypes.contains(fieldDeclaration.typeType().getText()) == false) {
	//			logger.info(String.format("Ignore \"%s\" due to its type.", fieldDeclaration.getText()));
	//			return;
	//		}

			if (fieldDeclaration.variableDeclarators().children.size() != 1)
				throw new UnsupportedOperationException(String.format("\"%s\" is not supported. Each field must have its own declaration.", ctx.getText()));

			var v = fieldDeclaration.variableDeclarators().variableDeclarator(0).variableDeclaratorId();
			String name = v.IDENTIFIER().getText();
			if (isInReferenceSection == false && name.equals("guid") == false) {
				rewriter.insertBefore(ctx.start, "@Property()\n\t");
				newImports.add("org.hyperledger.fabric.contract.annotation.*");

				if (Character.isUpperCase(name.charAt(0))) {
					changedNames.add(name);
					name = StringHelper.lowercaseFirstLetter(name);

					rewriter.replace(v.start, v.stop, name);
				}
			}
		}

		boolean isInThis = false;

		@Override
		public Object visitPrimary(JavaParser.PrimaryContext ctx) {
			if (ctx.IDENTIFIER() != null && changedNames.contains(ctx.IDENTIFIER().getText())) {
				var name = ctx.IDENTIFIER().getText();
				if (methodParameters.contains(name) && isInThis == false)
					return null;

				rewriter.replace(ctx.start, ctx.stop, StringHelper.lowercaseFirstLetter(name));
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
					rewriter.replace(id.getSymbol().getTokenIndex(), StringHelper.lowercaseFirstLetter(id.getText()));
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
