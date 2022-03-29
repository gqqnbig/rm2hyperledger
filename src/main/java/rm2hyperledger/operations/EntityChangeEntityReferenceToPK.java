package rm2hyperledger.operations;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStreamRewriter;
import rm2hyperledger.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

public class EntityChangeEntityReferenceToPK extends GitCommit {
	private final List<FieldDefinition> pkMap;

	public EntityChangeEntityReferenceToPK(String targetFolder, List<FieldDefinition> pkMap) {
		super("In entity class, if a field is a reference to entity, add a corresponding PK field\n\nWe don't remove the old field because we need to support clone.",
				targetFolder);
		this.pkMap = pkMap;
	}

	@Override
	protected ArrayList<Path> editCommitCore() throws IOException {
		ArrayList<Path> changedFiles = new ArrayList<>();

		Files.list(Path.of(targetFolder, "src\\main\\java\\entities")).forEach(file -> {
			try {
				if (file.toString().endsWith("EntityManager.java"))
					return;

				CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(file)));
				JavaParser parser = new JavaParser(tokens);
				TokenStreamRewriter2 rewriter = new TokenStreamRewriter2(tokens);
				var converter = new FieldDefinitionConverter(rewriter, pkMap);

				converter.visit(parser.compilationUnit());
				if (rewriter.hasChanges()) {
					try (PrintWriter out = new PrintWriter(file.toFile())) {
						out.print(rewriter.getText());
					}
					changedFiles.add(file);


					tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(file)));
					parser = new JavaParser(tokens);
					rewriter = new TokenStreamRewriter2(tokens);

					var c2 = new FieldGetterSetterConverter(converter.changedFields, rewriter);
					c2.visit(parser.compilationUnit());
					try (PrintWriter out = new PrintWriter(file.toFile())) {
						out.print(rewriter.getText());
					}
				}
			}
			catch (IOException exception) {
				logger.severe(exception.toString());
			}
		});

		return changedFiles;
	}

	public static class FieldDefinitionConverter extends ImportsCollector<Object> {
		private final List<FieldDefinition> pkMap;

		/**
		 * original field names, is list
		 */
		public HashMap<String, Boolean> changedFields = new HashMap<>();

		public FieldDefinitionConverter(TokenStreamRewriter rewriter, List<FieldDefinition> pkMap) {
			super(rewriter);
			this.pkMap = pkMap;
		}

		public static String castToReferenceType(String type) {
			switch (type) {
				case "byte":
					return "Byte";
				case "short":
					return "Short";
				case "int":
					return "Integer";
				case "long":
					return "Long";
				case "float":
					return "Float";
				case "double":
					return "Double";
				case "boolean":
					return "Boolean";
				case "char":
					return "Character";
				default:
					return type;
			}
		}

		@Override
		public Object visitClassBodyDeclaration(JavaParser.ClassBodyDeclarationContext ctx) {
			var memberDeclaration = ctx.memberDeclaration();
			if (memberDeclaration != null) {
				var fieldDeclaration = memberDeclaration.fieldDeclaration();
				if (fieldDeclaration != null) {
					var typeStr = fieldDeclaration.typeType().getText();
					var variableDeclarator = fieldDeclaration.variableDeclarators().variableDeclarator(0);
					String fieldName = variableDeclarator.variableDeclaratorId().getText();
					if (this.pkMap.stream().anyMatch(s -> s.ClassName.equals(typeStr)) && fieldName.endsWith("PK") == false) {
//						var d = this.pkMap.stream().filter(s -> s.ClassName.equals(typeStr)).findAny().get();
						rewriter.insertBefore(ctx.start, "@JsonProperty\n\t" + String.format("private Object %sPK;", fieldName) + "\n\t");

						super.newImports.add("com.owlike.genson.annotation.*");

						changedFields.put(fieldName, false);
						return null;
					} else {
						var pattern = Pattern.compile("List<([\\w\\d_]+)>");
						var m = pattern.matcher(typeStr);
						if (m.matches() && this.pkMap.stream().anyMatch(s -> s.ClassName.equals(m.group(1))) && fieldName.endsWith("PKs") == false) {
//							var d = this.pkMap.stream().filter(s -> s.ClassName.equals(m.group(1))).findAny().get();

							rewriter.insertBefore(ctx.start, "@JsonProperty\n\t" + String.format("private List<Object> %sPKs = new LinkedList<>();", fieldName) + "\n\t");
							super.newImports.add("com.owlike.genson.annotation.*");

							changedFields.put(fieldName, true);
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

		/**
		 * old name, is list
		 */
		private final HashMap<String, Boolean> changedFields;

		public FieldGetterSetterConverter(HashMap<String, Boolean> changedFields, TokenStreamRewriter rewriter) {
			super(rewriter);
			this.changedFields = changedFields;
		}

		@Override
		public Object visitClassBodyDeclaration(JavaParser.ClassBodyDeclarationContext ctx) {
			if (ctx.memberDeclaration() != null) {
				if (ctx.memberDeclaration().methodDeclaration() != null) {
					if (rewriteMethodDeclaration(ctx.memberDeclaration().methodDeclaration())) {
						//The getter or setter of PK fields must be marked with @JsonIgnore.
						rewriter.insertBefore(ctx.start, "@JsonIgnore\n\t");
						newImports.add("com.owlike.genson.annotation.*");
						return null;
					}
				}
			}
			return super.visitClassBodyDeclaration(ctx);
		}

		boolean rewriteMethodDeclaration(JavaParser.MethodDeclarationContext ctx) {
			var pattern = Pattern.compile("(get|set|add|delete)([\\w\\d_]+)");

			var methodName = ctx.IDENTIFIER().getText();
			var m = pattern.matcher(methodName);
			if (m.matches() == false)
				return false;

			var item = changedFields.keySet().stream().filter(f -> f.equalsIgnoreCase(m.group(2))).findFirst();
			if (item.isEmpty())
				return false;

			if (m.group(1).equals("get")) {
				String returnType = ctx.typeTypeOrVoid().getText();
				var listPattern = Pattern.compile("List<([\\w\\d_]+)>");
				var m2 = listPattern.matcher(returnType);

				if (m2.matches()) {
					String entityType = m2.group(1);

					var lines = new ArrayList<>(Arrays.asList(
							"if (%1$s == null)",
							"\t%1$s = %1$sPKs.stream().map(EntityManager::get%2$sByPK).collect(Collectors.toList());",
							"return %1$s;"));
					FormatHelper.increaseIndent(lines, 2);

					rewriter.replace(ctx.methodBody().start, ctx.methodBody().stop,
							"{\n" + String.format(String.join("\n", lines), item.get(), entityType) + "\n\t}");
					super.newImports.add("java.util.stream.*");
				} else {
					var lines = new ArrayList<>(Arrays.asList(
							"if (%1$s == null)",
							"\t%1$s = EntityManager.get%2$sByPK(%1$sPK);",
							"return %1$s;"));
					FormatHelper.increaseIndent(lines, 2);

					rewriter.replace(ctx.methodBody().start, ctx.methodBody().stop,
							"{\n" + String.format(String.join("\n", lines), item.get(), returnType) + "\n\t}");
				}
				return true;
			} else if (m.group(1).equals("set")) {
				var parameter = ctx.formalParameters().formalParameterList().formalParameter(0).variableDeclaratorId().IDENTIFIER().getText();

				rewriter.insertBefore(ctx.methodBody().stop,
						"\t" + String.format("this.%sPK = %s.getPK();", item.get(), parameter) + "\n\t");
			} else if (m.group(1).equals("add")) {
				var parameter = ctx.formalParameters().formalParameterList().formalParameter(0).variableDeclaratorId().IDENTIFIER().getText();

				//get%1$s() is to initialize the fields.
				rewriter.insertAfter(ctx.methodBody().start,
						"\n\t\t" + String.format("get%1$s();\n\t\tthis.%1$sPKs.add(%2$s.getPK());", item.get(), parameter));

			} else if (m.group(1).equals("delete")) {
				var parameter = ctx.formalParameters().formalParameterList().formalParameter(0).variableDeclaratorId().IDENTIFIER().getText();

				rewriter.insertAfter(ctx.methodBody().start,
						"\n\t\t" + String.format("get%1$s();\n\t\tthis.%1$sPKs.remove(%2$s.getPK());", item.get(), parameter));
			}

			return false;
		}
	}
}
