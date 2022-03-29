package rm2hyperledger;

public class SuperClassVisitor extends JavaParserBaseVisitor<Void> {
	public static String findSuperClass(JavaParser.CompilationUnitContext ctx) {

		var finder = new SuperClassVisitor();
		try {
			finder.visit(ctx);
			return null;
		}
		catch (SuperClassVisitor.TargetFoundException e) {
			return finder.superClass;
		}
	}


	private String superClass;

	@Override
	public Void visitClassDeclaration(JavaParser.ClassDeclarationContext ctx) {
		if (ctx.typeType() != null) {
			superClass = ctx.typeType().getText();
			throw new TargetFoundException();
		}
		return null;
	}

	static class TargetFoundException extends RuntimeException {
	}
}
