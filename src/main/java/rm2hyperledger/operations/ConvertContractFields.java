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
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ConvertContractFields extends GitCommit {


	private final String remodelFile;
	private final List<FieldDefinition> pkMap;

	public ConvertContractFields(String targetFolder, String remodelFile, List<FieldDefinition> pkMap) {
		super("All fields of entity type in contract classes must be referenced by PK\n\nGlobal/system fields are retrieved from the system key.", targetFolder);
		this.remodelFile = remodelFile;
		this.pkMap = pkMap;
	}

	@Override
	protected ArrayList<Path> editCommitCore() throws IOException {
		var fileName = FileHelper.getFileNameWithoutExtension(Path.of(remodelFile).getFileName().toString());
		var systemFile = Path.of(targetFolder, "src\\main\\java\\services\\", fileName + "System.java");

		Set<String> globalFields = SystemFieldsCollector.collect(systemFile).keySet();


		ArrayList<Path> changedFiles = new ArrayList<>();
		Set<String> entityNames = pkMap.stream().map(d -> d.ClassName).collect(Collectors.toSet());//.collect(Collectors.toSet());
		try (DirectoryStream<Path> files = Files.newDirectoryStream(Path.of(targetFolder, "src\\main\\java\\services\\impl"), "*.java")) {
			for (var f : files) {
				if (f.getFileName().toString().equals("ServiceManager.java"))
					continue;

				try {
					CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(f)));

					JavaParser parser = new JavaParser(tokens);

					var entityFieldsCollector = new SaveModified.EntityFieldsCollector(entityNames);
					entityFieldsCollector.visit(parser.compilationUnit());
					parser.reset();

					TokenStreamRewriter2 rewriter = new TokenStreamRewriter2(tokens);
					var fields = entityFieldsCollector.entityFields.entrySet().stream().
							map(e -> new EntityField(e.getKey(), e.getValue(), pkMap.stream().filter(m -> m.ClassName.equals(e.getValue())).map(d -> d.VariableType).findFirst().get())).
							collect(Collectors.toList());
					var converter = new PKAdder(rewriter, globalFields, fields);

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
		}

		return changedFiles;
	}


	static class PKAdder extends ImportsCollector<Object> {
		static Pattern listPattern = Pattern.compile("List<([\\w\\d_]+)>");


		private final Set<String> globalFields;
		private final List<EntityField> classFields;

		private String className;

		public PKAdder(TokenStreamRewriter rewriter, Set<String> globalFields, List<EntityField> classFields) {
			super(rewriter);

			this.globalFields = globalFields;
			this.classFields = classFields;
		}

		@Override
		public Object visitClassDeclaration(JavaParser.ClassDeclarationContext ctx) {
			className = ctx.IDENTIFIER().getText();
			return super.visit(ctx.classBody());
		}

		@Override
		public Object visitClassBodyDeclaration(JavaParser.ClassBodyDeclarationContext ctx) {
			if (ctx.memberDeclaration() != null) {
				if (ctx.memberDeclaration().fieldDeclaration() != null) {
					String str = checkFieldDeclaration(ctx.memberDeclaration().fieldDeclaration());
					if (str != null) {
						rewriter.insertBefore(ctx.start, str + "\n\t");
					}
					return null;
				}
			}
			return super.visitClassBodyDeclaration(ctx);
		}

		String checkFieldDeclaration(JavaParser.FieldDeclarationContext ctx) {
			String fieldName = ctx.variableDeclarators().variableDeclarator(0).variableDeclaratorId().IDENTIFIER().getText();
			if (classFields.stream().anyMatch(f -> f.fieldName.equals(fieldName))) {
				if (listPattern.matcher(ctx.typeType().getText()).matches())
					return String.format("private List<Object> %sPKs;", fieldName);
				else
					return String.format("private Object %sPK;", fieldName);
			}
			return null;
		}

		@Override
		public Object visitMethodDeclaration(JavaParser.MethodDeclarationContext ctx) {
			String methodName = ctx.IDENTIFIER().getText();
			String returnType = ctx.typeTypeOrVoid().getText();

			var p = Pattern.compile("(get|set)([\\w\\d_]+)");
			var m = p.matcher(methodName);

			if (m.matches() == false)
				return super.visitMethodDeclaration(ctx);


			var fieldDefinition = classFields.stream().filter(f -> f.fieldName.equals(StringHelper.lowercaseFirstLetter(m.group(2)))).findFirst();
			if (fieldDefinition.isPresent()) {
				if (m.group(1).equals("get")) {
					var m2 = listPattern.matcher(returnType);

					if (m2.matches()) {
						String entityType = m2.group(1);

						var lines = new ArrayList<>(Arrays.asList(
								"if (%1$s == null)",
								"\t%1$s = %3$sPKs().stream().map(EntityManager::get%2$sByPK).collect(Collectors.toList());",
								"return %1$s;"));
						FormatHelper.increaseIndent(lines, 2);

						rewriter.replace(ctx.methodBody().start, ctx.methodBody().stop,
								"{\n" + String.format(String.join("\n", lines), fieldDefinition.get().fieldName, entityType, methodName) + "\n\t}");
						super.newImports.add("java.util.stream.*");


						var getPK = new ArrayList<>(Arrays.asList(
								"private List<Object> %1$sPKs() {",
								"\tif (%2$s == null)",
								"\t\t%2$s = (List) GensonHelper.deserializeList(genson, EntityManager.stub.getStringState(\"%4$s.%2$s\"), %3$s.class);",
								"\treturn %2$s;",
								"}"));
						FormatHelper.increaseIndent(getPK, 1);

						rewriter.insertAfter(ctx.stop, "\n\n" + String.format(String.join("\n", getPK), methodName, StringHelper.lowercaseFirstLetter(m.group(2)) + "PKs", EntityChangeEntityReferenceToPK.FieldDefinitionConverter.castToReferenceType(fieldDefinition.get().pkType),
								globalFields.contains(StringHelper.lowercaseFirstLetter(m.group(2))) ? "system" : className));
					} else {
						rewriter.replace(ctx.methodBody().start, ctx.methodBody().stop,
								"{\n\t\t" + String.format("return EntityManager.get%1$sByPK(%2$s());", returnType, methodName + "PK") + "\n\t}");

						var lines = new ArrayList<>(Arrays.asList(
								"private Object %1$sPK() {",
								"\tif (%2$s == null)",
								"\t\t%2$s = genson.deserialize(EntityManager.stub.getStringState(\"%4$s.%2$s\"), %3$s.class);",
								"",
								"\treturn %2$s;",
								"}"));
						FormatHelper.increaseIndent(lines, 1);

						rewriter.insertAfter(ctx.stop, "\n\n" + String.format(String.join("\n", lines), methodName, StringHelper.lowercaseFirstLetter(m.group(2)) + "PK", EntityChangeEntityReferenceToPK.FieldDefinitionConverter.castToReferenceType(fieldDefinition.get().pkType),
								globalFields.contains(StringHelper.lowercaseFirstLetter(m.group(2))) ? "system" : className));
					}
					return null;
				} else if (m.group(1).equals("set")) {
					var parameterName = ctx.formalParameters().formalParameterList().formalParameter(0).variableDeclaratorId().IDENTIFIER().getText();

					var m2 = listPattern.matcher(ctx.formalParameters().formalParameterList().formalParameter(0).typeType().getText());
					if (m2.matches()) {
						String[] setterBody = new String[]{
								"%2$sPKs(%1$s.stream().map(LoanRequest::getPK).collect(Collectors.toList()));",
								"this.%3$s = %1$s;"
						};
						FormatHelper.increaseIndent(setterBody, 2);
						rewriter.replace(ctx.methodBody().start, ctx.methodBody().stop,
								"{\n" + String.format(String.join("\n", setterBody), parameterName, methodName, StringHelper.lowercaseFirstLetter(m.group(2))) + "\n\t}");


						ArrayList<String> lines = new ArrayList<>(Arrays.asList(
								"private void %1$sPKs(List<Object> %2$s) {",
								"\tString json = genson.serialize(%2$s);",
								"\tEntityManager.stub.putStringState(\"%3$s.%2$s\", json);",
								"\tthis.%2$s = %2$s;",
								"}"));
						FormatHelper.increaseIndent(lines, 1);

						rewriter.insertAfter(ctx.stop, "\n\n" + String.format(String.join("\n", lines), methodName, StringHelper.lowercaseFirstLetter(m.group(2)) + "PKs",
								globalFields.contains(StringHelper.lowercaseFirstLetter(m.group(2))) ? "system" : className));
					} else {
						String[] setterBody = new String[]{
								"if (%1$s != null)",
								"\t%2$sPK(%1$s.getPK());",
								"else",
								"\t%2$sPK(null);",
								"this.%3$s = %1$s;"
						};
						FormatHelper.increaseIndent(setterBody, 2);

						rewriter.replace(ctx.methodBody().start, ctx.methodBody().stop,
								"{\n" + String.format(String.join("\n", setterBody), parameterName, methodName, StringHelper.lowercaseFirstLetter(m.group(2))) + "\n\t}");


						ArrayList<String> lines = new ArrayList<>(Arrays.asList(
								"private void %1$sPK(Object %2$s) {",
								"\tString json = genson.serialize(%2$s);",
								"\tEntityManager.stub.putStringState(\"%3$s.%2$s\", json);",
								"\t//If we set %2$s to null, the getter thinks this fields is not initialized, thus will read the old value from chain.",
								"\tif (%2$s != null)",
								"\t\tthis.%2$s = %2$s;",
								"\telse",
								"\t\tthis.%2$s = EntityManager.getGuid();",
								"}"));
						FormatHelper.increaseIndent(lines, 1);

						rewriter.insertAfter(ctx.stop, "\n\n" + String.format(String.join("\n", lines), methodName, StringHelper.lowercaseFirstLetter(m.group(2)) + "PK",
								globalFields.contains(StringHelper.lowercaseFirstLetter(m.group(2))) ? "system" : className));
					}
					return null;
				}
			}

			return super.visitMethodDeclaration(ctx);
		}

		@Override
		public Object visitPrimary(JavaParser.PrimaryContext ctx) {
			if (ctx.IDENTIFIER() != null) {
				var id = ctx.IDENTIFIER().getText();
				if (classFields.stream().anyMatch(f -> f.fieldName.equals(id))) {

					rewriter.replace(ctx.start, ctx.stop, "get" + StringHelper.uppercaseFirstLetter(id) + "()");
					return null;
				}
			}
			return super.visitPrimary(ctx);
		}

	}


	/**
	 * Each REModel requires a service whose name is the file name appended with the word "System".
	 * <p>
	 * All fields in the system service will be shared to all other services.
	 */
	static class SystemFieldsCollector extends JavaParserBaseVisitor<Object> {

		public static HashMap<String, String> collect(Path systemFile) throws IOException {
			CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(systemFile)));
			JavaParser parser = new JavaParser(tokens);
			var collector = new SystemFieldsCollector();

			collector.visit(parser.compilationUnit());
//			collector.getters.retainAll(collector.setters);

			return collector.getters;
		}


		HashMap<String, String> getters = new HashMap<>();

		private SystemFieldsCollector() {

		}


		@Override
		public Object visitInterfaceMethodDeclaration(JavaParser.InterfaceMethodDeclarationContext ctx) {
			var identifier = ctx.IDENTIFIER().getText().trim();
			if (identifier.startsWith("get") && ctx.typeTypeOrVoid().getText().equals("void") == false && ctx.formalParameters().children.size() == 2) {
				getters.put(lowercaseFirstLetter(identifier.substring(3)), ctx.typeTypeOrVoid().getText());
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

	/**
	 * One occurrence of a field whose type is an entity type.
	 */
	static class EntityField {
		String fieldName;
		String fieldType;
		String pkType;

		public EntityField(String fieldName, String fieldType, String pkType) {
			this.fieldName = fieldName;
			this.fieldType = fieldType;
			this.pkType = pkType;
		}
	}
}
