public class ParameterChecker extends JavaParserBaseVisitor<Boolean> {
	String type;
	String name;

	private ParameterChecker(String type, String name) {
		this.type = type;
		this.name = name;
	}

	public static boolean hasParameter(JavaParser.FormalParametersContext parameters, String type, String name) {
		var checker = new ParameterChecker(type, name);

		return checker.visit(parameters);
	}

	@Override
	public Boolean visitFormalParameter(JavaParser.FormalParameterContext ctx) {
		return ctx.typeType().getText().equals(type) && ctx.variableDeclaratorId().getText().equals(name);
	}

	@Override
	public Boolean visitFormalParameterList(JavaParser.FormalParameterListContext ctx) {
		for (var p : ctx.formalParameter()) {
			if (visit(p))
				return true;
		}
		return false;
	}

	@Override
	public Boolean visitFormalParameters(JavaParser.FormalParametersContext ctx) {
		return ctx.formalParameterList() != null && visit(ctx.formalParameterList());
	}
}
