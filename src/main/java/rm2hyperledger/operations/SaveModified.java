package rm2hyperledger.operations;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStreamRewriter;
import rm2hyperledger.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SaveModified extends GitCommit {
	private final HashMap<String, List<String>> contractTransactions;
	private final HashSet<String> entityNames;

	public SaveModified(String targetFolder, HashMap<String, List<String>> contractTransactions, HashSet<String> entityNames) {
		super("Call savedModified\n\n" +
						"If a local variable or a field, of entity type, is modified in a contract, the changes must be saved back unless AddObject() or deleteObject() is called on it.",
				targetFolder);
		this.contractTransactions = contractTransactions;
		this.entityNames = entityNames;
	}

	@Override
	protected ArrayList<Path> editCommitCore() throws IOException {
		ArrayList<Path> changedFiles = new ArrayList<>();

		for (var entry : contractTransactions.entrySet()) {
			var file = Path.of(targetFolder, "src\\main\\java\\services\\impl", entry.getKey() + ".java");

			try {
				CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(file)));

				JavaParser parser = new JavaParser(tokens);

				var entityFieldsCollector = new EntityFieldsCollector(entityNames);
				entityFieldsCollector.visit(parser.compilationUnit());


				TokenStreamRewriter2 rewriter = new TokenStreamRewriter2(tokens);
				var converter = new ModifiedVariablesCollector(entry.getValue(), entityNames, entityFieldsCollector.entityFields, rewriter);
				parser.reset();
				converter.visit(parser.compilationUnit());
				if (rewriter.hasChanges()) {
					try (PrintWriter out = new PrintWriter(file.toFile())) {
						out.print(rewriter.getText());
					}
					changedFiles.add(file);
				}
			}
			catch (IOException exception) {
				logger.severe(exception.toString());
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
		static Pattern pattern = Pattern.compile("List<([\\w\\d_]+)>");

		private final Set<String> entityTypes;

		/**
		 * field name, type
		 */
		public HashMap<String, String> entityFields = new HashMap<>();

		public EntityFieldsCollector(Set<String> entityTypes) {
			this.entityTypes = entityTypes;
		}


		@Override
		public Object visitFieldDeclaration(JavaParser.FieldDeclarationContext ctx) {
			if (entityTypes.contains(ctx.typeType().getText())) {
				entityFields.put(ctx.variableDeclarators().variableDeclarator(0).variableDeclaratorId().IDENTIFIER().getText(), ctx.typeType().getText());
			} else {
				var m = pattern.matcher(ctx.typeType().getText());
				if (m.matches())
					entityFields.put(ctx.variableDeclarators().variableDeclarator(0).variableDeclaratorId().IDENTIFIER().getText(), m.group(1));
			}
			return null;
		}
	}

	static class ModifiedVariablesCollector extends JavaParserBaseVisitor<Object> {
		private final List<String> methods;
		private final HashSet<String> entityTypes;

		HashMap<String, String> definedVariables;
		HashMap<String, String> variableTypesToSave;
		private final HashMap<String, String> classFields;
		private final TokenStreamRewriter rewriter;

		public ModifiedVariablesCollector(List<String> methods, HashSet<String> entityTypes, HashMap<String, String> classFields, TokenStreamRewriter rewriter) {
			this.methods = methods;
			this.entityTypes = entityTypes;
			this.classFields = classFields;
			this.rewriter = rewriter;
		}


		@Override
		public Object visitClassBodyDeclaration(JavaParser.ClassBodyDeclarationContext ctx) {
			if (ctx.memberDeclaration() == null)
				return null;

			var methodDeclaration = ctx.memberDeclaration().methodDeclaration();
			if (methodDeclaration != null && methods.contains(methodDeclaration.IDENTIFIER().getText()))
				return visitMethodBody(methodDeclaration.methodBody());

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
