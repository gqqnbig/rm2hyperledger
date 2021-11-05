package rm2hyperledger;

import org.antlr.v4.runtime.TokenStreamRewriter;

import java.util.Objects;

/**
 * The refresh method is to synchronize system states.
 */
public class RefreshRemover extends JavaParserBaseVisitor<Boolean> {
	private final TokenStreamRewriter rewriter;

	public RefreshRemover(TokenStreamRewriter rewriter) {
		this.rewriter = rewriter;
	}

	@Override
	public Boolean visitClassBodyDeclaration(JavaParser.ClassBodyDeclarationContext ctx) {
		var memberDeclaration = ctx.memberDeclaration();
		if (memberDeclaration != null && Objects.equals(true, visit(memberDeclaration))) {
			rewriter.delete(ctx.start, ctx.stop);
		}

		return super.visitClassBodyDeclaration(ctx);
	}

	@Override
	public Boolean visitMethodDeclaration(JavaParser.MethodDeclarationContext ctx) {
		if (ctx.IDENTIFIER().getText().equals("refresh") && ctx.typeTypeOrVoid().getText().equals("void") && ctx.formalParameters().children.size() == 2) {

			return true;
		} else
			return super.visitMethodDeclaration(ctx);
	}

	@Override
	public Boolean visitMethodCall(JavaParser.MethodCallContext ctx) {
		if (ctx.IDENTIFIER().getText().equals("refresh") && ctx.expressionList() == null) {
//			rewriter.insertAfter(ctx.stop, "\n");
			rewriter.delete(ctx.start, ctx.stop);
		}

		return false;
	}
}
