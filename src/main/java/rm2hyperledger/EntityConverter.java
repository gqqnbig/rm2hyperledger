package rm2hyperledger;

import org.antlr.v4.runtime.TokenStreamRewriter;
import rm2hyperledger.checkers.ModifierChecker;

public class EntityConverter extends ImportsCollector<Object> {

	public EntityConverter(TokenStreamRewriter rewriter) {
		super(rewriter);
	}

	@Override
	public Object visitTypeDeclaration(JavaParser.TypeDeclarationContext ctx) {

		if (ModifierChecker.hasModifier(ctx.classOrInterfaceModifier(), JavaParser.PUBLIC) &&
				ctx.classDeclaration() != null) {
			rewriter.insertBefore(ctx.start, "@DataType()\n");
			super.newImports.add("org.hyperledger.fabric.contract.annotation.*");
			return visitClassBody(ctx.classDeclaration().classBody());
		}
		return super.visitTypeDeclaration(ctx);
	}
}
