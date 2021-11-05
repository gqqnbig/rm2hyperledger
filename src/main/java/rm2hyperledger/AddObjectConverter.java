package rm2hyperledger;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStreamRewriter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AddObjectConverter extends JavaParserBaseVisitor<Object> {
	final static Pattern methodNamePattern = Pattern.compile("add(\\w+)Object");

	final TokenStreamRewriter rewriter;
	final String lineEnding;
	boolean hasLoadList = false;

	public AddObjectConverter(TokenStreamRewriter rewriter, String lineEnding) {
		this.rewriter = rewriter;
		this.lineEnding = lineEnding;
	}

	@Override
	public Object visitMethodDeclaration(JavaParser.MethodDeclarationContext ctx) {
		String methodName = ctx.IDENTIFIER().getText();
		Matcher m = methodNamePattern.matcher(methodName);

		if (ctx.typeTypeOrVoid().getText().equals("boolean")
				&& m.matches()
				&& ctx.formalParameters().children.size() == 3) {
			String type = ctx.formalParameters().children.get(1).getChild(0).getChild(0).getText();

			if (m.group(1).equals(type)) {
//				System.out.println(type);


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

				rewriter.replace(ctx.methodBody().start, ctx.methodBody().stop, String.join(lineEnding, lines));

			}
		}

		if ("loadList".equals(methodName))
			hasLoadList = true;

		return null;
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


		if (hasLoadList == false) {
			String line = String.join(lineEnding, loadList) + lineEnding;
			rewriter.insertBefore(ctx.stop, line);

			System.out.println("loadList method added");
		}
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
