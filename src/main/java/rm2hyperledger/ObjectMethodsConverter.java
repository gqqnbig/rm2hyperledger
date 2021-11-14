package rm2hyperledger;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStreamRewriter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ObjectMethodsConverter extends JavaParserBaseVisitor<Object> {
	final static Pattern addObjectMethodName = Pattern.compile("add(\\w+)Object");
	final static Pattern deleteObjectMethodName = Pattern.compile("delete(\\w+)Object");

	final TokenStreamRewriter rewriter;
	final String lineEnding;
	boolean hasLoadList = false;

	public ObjectMethodsConverter(TokenStreamRewriter rewriter, String lineEnding) {
		this.rewriter = rewriter;
		this.lineEnding = lineEnding;
	}

	@Override
	public Object visitMethodDeclaration(JavaParser.MethodDeclarationContext ctx) {
		String methodName = ctx.IDENTIFIER().getText();
		Matcher m;

		if (ctx.typeTypeOrVoid().getText().equals("boolean")
				&& (m = addObjectMethodName.matcher(methodName)).matches()
				&& ctx.formalParameters().children.size() == 3) {
			String type = ctx.formalParameters().children.get(1).getChild(0).getChild(0).getText();

			if (m.group(1).equals(type))
				rewriteAddObject(ctx.methodBody(), type);
		} else if (ctx.typeTypeOrVoid().getText().equals("boolean")
				&& (m = deleteObjectMethodName.matcher(methodName)).matches()
				&& ctx.formalParameters().children.size() == 3) {
			String type = ctx.formalParameters().children.get(1).getChild(0).getChild(0).getText();

			if (m.group(1).equals(type))
				rewriteDeleteObject(ctx.methodBody(), type);
		}

		if ("loadList".equals(methodName))
			hasLoadList = true;

		return null;
	}

	private void rewriteAddObject(JavaParser.MethodBodyContext methodBody, String type) {
		// @formatter:off
		ArrayList<String> lines = new ArrayList<>(Arrays.asList(
		  String.format("	List<%1$s> list = loadList(%1$s.class);", type),
						"	if (list.add(o)) {",
						"		String json = genson.serialize(list);",
		 String.format("		stub.putStringState(\"%s\", json);", type),
						"		return true;",
						"	} else",
						"		return false;",
						"}"));
		// @formatter:on
		FormatHelper.increaseIndent(lines, 1);
		lines.add(0, "{");

		rewriter.replace(methodBody.start, methodBody.stop, String.join(lineEnding, lines));
	}

	private void rewriteDeleteObject(JavaParser.MethodBodyContext methodBody, String type) {
		// @formatter:off
		ArrayList<String> lines = new ArrayList<>(Arrays.asList(
  String.format("	List<%1$s> list = loadList(%1$s.class);", type),
				"	if (list.remove(o)) {",
				"		String json = genson.serialize(list);",
  String.format("		stub.putStringState(\"%s\", json);", type),
				"		return true;",
				"	} else",
				"		return false;",
				"}"));
		// @formatter:on
		FormatHelper.increaseIndent(lines, 1);
		lines.add(0, "{");

		rewriter.replace(methodBody.start, methodBody.stop, String.join(lineEnding, lines));
	}

	HashSet<String> imports = new HashSet<>(Arrays.asList(
			"com.owlike.genson.Genson",
			"org.hyperledger.fabric.shim.ChaincodeStub"));


	@Override
	public Object visitImportDeclaration(JavaParser.ImportDeclarationContext ctx) {
		String name = ctx.qualifiedName().getText();
		imports.remove(name);

		return null;
	}

	Token typeStartToken = null;

	@Override
	public Object visitTypeDeclaration(JavaParser.TypeDeclarationContext ctx) {
		if (typeStartToken == null)
			typeStartToken = ctx.start;

		return super.visitTypeDeclaration(ctx);
	}

	@Override
	public Object visitClassBody(JavaParser.ClassBodyContext ctx) {
		super.visitClassBody(ctx);

		var fields = Arrays.asList("private static final Genson genson = new Genson();",
				"public static ChaincodeStub stub;");
		FormatHelper.increaseIndent(fields, 1);

		rewriter.insertAfter(ctx.start, "\n\n" + String.join("\n\n", fields));

		if (hasLoadList == false) {
			String line = String.join(lineEnding, loadList) + lineEnding;
			rewriter.insertBefore(ctx.stop, line);
		}

		var getAllInstancesOf = Arrays.asList("public static <T> List<T> getAllInstancesOf(Class<T> clazz) {",
				"\tList<T> list = loadList(clazz);",
				"\treturn list;",
				"}\n\n");
		FormatHelper.increaseIndent(getAllInstancesOf, 1);
		rewriter.insertBefore(ctx.stop, "\n" + String.join("\n", getAllInstancesOf));

		return null;
	}

	@Override
	public Object visitCompilationUnit(JavaParser.CompilationUnitContext ctx) {
		super.visitCompilationUnit(ctx);

		if (typeStartToken != null) {
			if (imports.size() > 0) {
				rewriter.insertBefore(typeStartToken, lineEnding);
				for (String name : imports)
					rewriter.insertBefore(typeStartToken, "import " + name + ";"+lineEnding);
			}
		}

		return null;
	}

	private static ArrayList<String> loadList = new ArrayList<>(Arrays.asList(
			"	private static <T> List<T> loadList(Class<T> clazz) {",
			"		String key = clazz.getSimpleName();",
			"		List<T> list = AllInstance.get(key);",
			"		if (list == null || list.size() == 0) {",
			"			String json = stub.getStringState(key);",
			"			System.out.printf(\"loadList %s: %s\\n\", key, json);",
			"			if (json != null && Objects.equals(json, \"\") == false)",
			"				list = GensonHelper.deserializeList(genson, json, clazz);",
			"			else",
			"				list = new LinkedList<>();",
			"			AllInstance.put(key, list);",
			"		}",
			"		return list;",
			"	}"
	));


}
