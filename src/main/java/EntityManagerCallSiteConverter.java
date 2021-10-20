import org.antlr.v4.runtime.TokenStreamRewriter;

public class EntityManagerCallSiteConverter extends JavaParserBaseVisitor<Object> {

	final TokenStreamRewriter rewriter;

	public EntityManagerCallSiteConverter(TokenStreamRewriter rewriter) {
		this.rewriter = rewriter;
	}

	@Override
	public Object visitMethodCall(JavaParser.MethodCallContext ctx) {
		if (ctx.IDENTIFIER().getText().equals("getAllInstancesOf") && ctx.expressionList() != null && ctx.expressionList().children.size() == 1) {
			var arg = (JavaParser.ExpressionContext) ctx.expressionList().children.get(0);
			var text = arg.getText();
			if (text.startsWith("\"") && text.endsWith("\"")) {
				rewriter.replace(arg.start, arg.stop, text.substring(1, text.length() - 1) + ".class");
			}
		}

		return super.visitMethodCall(ctx);
	}
}
