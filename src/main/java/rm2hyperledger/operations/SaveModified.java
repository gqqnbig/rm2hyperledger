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
import java.util.HashMap;
import java.util.HashSet;
import java.util.stream.Collectors;

public class SaveModified extends GitCommit {
	private final HashSet<String> entityNames;

	public SaveModified(String targetFolder, HashSet<String> entityNames) {
		super("Call savedModified\n\n" +
						"If a local variable or a field, of entity type, is modified in a contract, the changes must be saved back unless AddObject() or deleteObject() is called on it.",
				targetFolder);
		this.entityNames = entityNames;
	}

	@Override
	protected ArrayList<Path> editCommitCore() throws IOException {
		ArrayList<Path> changedFiles = new ArrayList<>();
		try (DirectoryStream<Path> files = Files.newDirectoryStream(Path.of(targetFolder, "src\\main\\java\\services\\impl"), "*.java")) {
			for (var f : files) {
				try {
					CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(f)));

					JavaParser parser = new JavaParser(tokens);

					var entityFieldsCollector = new EntityFieldsCollector(entityNames);
					entityFieldsCollector.visit(parser.compilationUnit());


					TokenStreamRewriter2 rewriter = new TokenStreamRewriter2(tokens);
					var converter = new ModifiedVariablesCollector(entityNames, entityFieldsCollector.entityFields, rewriter);
					parser.reset();
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


		Path entityManagerPath = Path.of(targetFolder, "src\\main\\java\\entities\\EntityManager.java");
		CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(entityManagerPath)));
		JavaParser parser = new JavaParser(tokens);
		TokenStreamRewriter2 rewriter = new TokenStreamRewriter2(tokens);
		var converter = new AddClassMemberVisitor(rewriter, AddClassMemberVisitor.EditLocation.End,
				new String[]{"",
						"public static <T> boolean saveModified(Class<T> clazz) {",
						"\tList<T> list = loadList(clazz);",
						"\tString json = genson.serialize(list);",
						"\tstub.putStringState(clazz.getSimpleName(), json);",
						"\treturn true;",
						"}"});
		converter.visitCompilationUnit(parser.compilationUnit());
		try (PrintWriter out = new PrintWriter(entityManagerPath.toFile())) {
			out.print(rewriter.getText());
		}
		changedFiles.add(entityManagerPath);

		return changedFiles;
	}

	/**
	 * In a class, collect its all fields whose type is an Entity type.
	 */
	static class EntityFieldsCollector extends JavaParserBaseVisitor<Object> {
		private final HashSet<String> entityTypes;

		/**
		 * field name, type
		 */
		public HashMap<String, String> entityFields = new HashMap<>();

		public EntityFieldsCollector(HashSet<String> entityTypes) {
			this.entityTypes = entityTypes;
		}


		@Override
		public Object visitFieldDeclaration(JavaParser.FieldDeclarationContext ctx) {
			if (entityTypes.contains(ctx.typeType().getText())) {

				entityFields.put(ctx.variableDeclarators().variableDeclarator(0).variableDeclaratorId().IDENTIFIER().getText(), ctx.typeType().getText());
			}
			return null;
		}
	}

	static class ModifiedVariablesCollector extends JavaParserBaseVisitor<Object> {
		private final HashSet<String> entityTypes;

		HashMap<String, String> definedVariables;
		HashMap<String, String> variableTypesToSave;
		private final HashMap<String, String> classFields;
		private final TokenStreamRewriter rewriter;

		public ModifiedVariablesCollector(HashSet<String> entityTypes, HashMap<String, String> classFields, TokenStreamRewriter rewriter) {
			this.entityTypes = entityTypes;
			this.classFields = classFields;
			this.rewriter = rewriter;
		}


		@Override
		public Object visitClassBodyDeclaration(JavaParser.ClassBodyDeclarationContext ctx) {
			if (ctx.modifier().size() > 0 && ctx.modifier(0).getText().contains("@Transaction") && ctx.modifier(0).getText().contains("Transaction.TYPE.SUBMIT")) {

				var methodDeclaration = ctx.memberDeclaration().methodDeclaration();
				if (methodDeclaration != null)
					return visitMethodBody(methodDeclaration.methodBody());
			}

			return null;
		}

		@Override
		public Object visitMethodBody(JavaParser.MethodBodyContext ctx) {
			definedVariables = new HashMap<>();
			variableTypesToSave = new HashMap<>();
			super.visitMethodBody(ctx);

			if (variableTypesToSave.size() > 0) {
				new AddSaveModifiedVisitor(new HashSet<>(variableTypesToSave.values()), rewriter).visitMethodBody(ctx);
			}
			return null;
		}

		@Override
		public Object visitLocalVariableDeclaration(JavaParser.LocalVariableDeclarationContext ctx) {
			var typeStr = ctx.typeType().getText();
			if (entityTypes.contains(typeStr)) {
				definedVariables.put(ctx.variableDeclarators().variableDeclarator(0).variableDeclaratorId().getText(), typeStr);
			}

			return null;
		}

		@Override
		public Object visitExpression(JavaParser.ExpressionContext ctx) {
			if (ctx.expression().size() > 0) {
				String text = ctx.expression(0).getText();
				if ((definedVariables.containsKey(text) || classFields.containsKey(text)) && ctx.methodCall() != null) {
					var methodName = ctx.methodCall().IDENTIFIER();
					if (methodName != null &&
							(methodName.getText().startsWith("set") || methodName.getText().startsWith("add") || methodName.getText().startsWith("delete"))) {
						if (definedVariables.containsKey(text))
							variableTypesToSave.put(text, definedVariables.get(text));
						else
							variableTypesToSave.put(text, classFields.get(text));
					}
				}
			}

			return super.visitExpression(ctx);
		}

		@Override
		public Object visitMethodCall(JavaParser.MethodCallContext ctx) {
			var expressionList = ctx.expressionList();
			if (expressionList != null) {
				var arg1 = expressionList.expression(1);
				if (ctx.IDENTIFIER().getText().equals("addObject") || ctx.IDENTIFIER().getText().equals("deleteObject"))
					variableTypesToSave.remove(arg1.getText());
			}
			return super.visitMethodCall(ctx);
		}

		static class AddSaveModifiedVisitor extends JavaParserBaseVisitor<Object> {
			private final HashSet<String> typesToSave;
			private final TokenStreamRewriter rewriter;
			boolean canAdd = false;

			public AddSaveModifiedVisitor(HashSet<String> typesToSave, TokenStreamRewriter rewriter) {
				this.typesToSave = typesToSave;
				this.rewriter = rewriter;
			}

			@Override
			public Object visitStatement(JavaParser.StatementContext ctx) {
				if (ctx.parExpression() != null && ctx.statement(0).getText().equals("{thrownewPostconditionException();}")) {
					canAdd = true;
					visitExpression(ctx.parExpression().expression());
					canAdd = false;
					return null;
				} else
					return super.visitStatement(ctx);
			}

			@Override
			public Object visitExpression(JavaParser.ExpressionContext ctx) {
				if (canAdd && ctx.bop != null && "&&".equals(ctx.bop.getText()) && ctx.expression(1).getText().equals("true")) {
					String str = typesToSave.stream().map(s -> "EntityManager.saveModified(" + s + ".class)").collect(Collectors.joining(" && "));

					String indent = "\t".repeat(ctx.expression(1).start.getCharPositionInLine());
					rewriter.insertBefore(ctx.expression(1).start, str + "\n" + indent + " &&\n" + indent);
					return null;
				} else
					return super.visitExpression(ctx);
			}
		}
	}
}
