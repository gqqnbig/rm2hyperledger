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
					logger.severe(exception.getMessage());
				}
			}
		}

		return changedFiles;
	}


	static class PKAdder extends ImportsCollector<Object> {

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
		public Object visitFieldDeclaration(JavaParser.FieldDeclarationContext ctx) {
			String fieldName = ctx.variableDeclarators().variableDeclarator(0).variableDeclaratorId().IDENTIFIER().getText();
			if (classFields.stream().anyMatch(f -> f.fieldName.equals(fieldName))) {
				rewriter.replace(ctx.start, ctx.stop, "Object " + fieldName + "PK;");
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
					rewriter.replace(ctx.methodBody().start, ctx.methodBody().stop,
							"{\n\t\t" + String.format("return EntityManager.get%1$sByPK(%2$s());", returnType, methodName + "PK") + "\n\t}");
					super.newImports.add("java.util.*");

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
					return null;
				} else if (m.group(1).equals("set")) {
					var parameterName = ctx.formalParameters().formalParameterList().formalParameter(0).variableDeclaratorId().IDENTIFIER().getText();

					rewriter.replace(ctx.methodBody().start, ctx.methodBody().stop, "{\n\t\t" + String.format("%sPK(%s.getPK());", methodName, parameterName) + "\n\t}");

					ArrayList<String> lines = new ArrayList<>(Arrays.asList(
							"private void %1$sPK(Object %2$s) {",
							"\tString json = genson.serialize(%2$s);",
							"\tEntityManager.stub.putStringState(\"%3$s.%2$s\", json);",
							"\tthis.%2$s = %2$s;",
							"}"));
					FormatHelper.increaseIndent(lines, 1);

					rewriter.insertAfter(ctx.stop, "\n\n" + String.format(String.join("\n", lines), methodName, StringHelper.lowercaseFirstLetter(m.group(2)) + "PK",
							globalFields.contains(StringHelper.lowercaseFirstLetter(m.group(2))) ? "system" : className));
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
