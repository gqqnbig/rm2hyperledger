import org.antlr.v4.runtime.TokenStreamRewriter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AddObjectConverter extends JavaParserBaseVisitor<Object> {
	final static Pattern methodNamePattern = Pattern.compile("add(\\w+)Object");

	final TokenStreamRewriter rewriter;

	public AddObjectConverter(TokenStreamRewriter rewriter) {
		this.rewriter = rewriter;
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
				System.out.println(type);


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

				rewriter.replace(ctx.methodBody().start, ctx.methodBody().stop, String.join("\n", lines));

			}
		}

//		return super.visitMethodDeclaration(ctx);
		return null;
	}


}
