package rm2hyperledger;

import rm2hyperledger.checkers.ParameterChecker;
import org.antlr.v4.runtime.TokenStreamRewriter;

import java.util.List;

public class ServiceImplementationConverter extends ImportsCollector<TransactionIntent> {
	private final List<String> methodsToRewrite;

	public ServiceImplementationConverter(TokenStreamRewriter rewriter, List<String> methodsToRewrite) {
		super(rewriter);
		this.methodsToRewrite = methodsToRewrite;
	}

	@Override
	public TransactionIntent visitMethodBody(JavaParser.MethodBodyContext ctx) {
		var text = ctx.getText();
		if (text.contains("ChaincodeStubstub=ctx.getStub();") == false && text.contains("EntityManager.stub=stub;") == false) {
			rewriter.insertAfter(ctx.start, "\n\t\tChaincodeStub stub = ctx.getStub();\n\t\tEntityManager.stub = stub;");
			newImports.add("org.hyperledger.fabric.shim.*");
		}

		return TransactionIntent.SUBMIT;
//		return super.visitMethodBody(ctx);
	}

	@Override
	public TransactionIntent visitMethodDeclaration(JavaParser.MethodDeclarationContext ctx) {
		String identifier = ctx.IDENTIFIER().getText();
		if (methodsToRewrite.contains(identifier) == false)
			return null;

		var formalParameters = ctx.formalParameters();
		if (ParameterChecker.hasParameter(ctx.formalParameters(), "Context", "ctx") == false) {
			rewriter.insertAfter(formalParameters.start, "final Context ctx" + (ctx.formalParameters().children.size() == 2 ? "" : ", "));
			newImports.add("org.hyperledger.fabric.contract.*");
		}


		return visit(ctx.methodBody());
	}


	@Override
	public TransactionIntent visitClassBodyDeclaration(JavaParser.ClassBodyDeclarationContext ctx) {
		var memberDeclaration = ctx.memberDeclaration();
		if (memberDeclaration != null) {
			var intent = visit(memberDeclaration);
			if (intent != null) {
				if (ctx.modifier() == null) {
					rewriter.insertBefore(memberDeclaration.start, String.format("@Transaction(intent = Transaction.TYPE.%s)\n\t", intent));
					newImports.add("org.hyperledger.fabric.contract.annotation.*");
				} else if (hasAnnotation(ctx.modifier(), "Transaction") == false) {
					rewriter.insertBefore(ctx.modifier(0).start, String.format("@Transaction(intent = Transaction.TYPE.%s)\n\t", intent));
					newImports.add("org.hyperledger.fabric.contract.annotation.*");
				}
			}
			return null;
		} else
			return super.visitClassBodyDeclaration(ctx);
	}


	private static boolean hasAnnotation(List<JavaParser.ModifierContext> trees, String annotation) {
		for (var tree : trees) {
			if (tree.classOrInterfaceModifier() == null)
				continue;

			var t1 = tree.classOrInterfaceModifier();
			if (t1.annotation() == null)
				continue;

			var t2 = t1.annotation();
			if (t2.qualifiedName() != null && t2.qualifiedName().getText().equals(annotation))
				return true;
		}
		return false;
	}
}

