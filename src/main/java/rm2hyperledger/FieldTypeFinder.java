package rm2hyperledger;

public class FieldTypeFinder extends JavaParserBaseVisitor<Void> {

	/**
	 * @param ctx
	 * @param name
	 * @return field type. Null if the field is not found.
	 */
	public static String findField(JavaParser.CompilationUnitContext ctx, String name) {
		var finder = new FieldTypeFinder(name);
		try {
			finder.visit(ctx);
			return null;
		}
		catch (TargetFoundException e) {
			return finder.fieldType;
		}
	}


	private final String fieldName;
	private String fieldType;


	private FieldTypeFinder(String fieldName) {
		this.fieldName = fieldName;
	}

	@Override
	public Void visitFieldDeclaration(JavaParser.FieldDeclarationContext ctx) {
		for (var v : ctx.variableDeclarators().variableDeclarator()) {
			if (fieldName.equals(v.variableDeclaratorId().IDENTIFIER().getText())) {
				fieldType = ctx.typeType().getText();
				throw new TargetFoundException();
			}
		}
		return null;
	}


	static class TargetFoundException extends RuntimeException {
	}
}
