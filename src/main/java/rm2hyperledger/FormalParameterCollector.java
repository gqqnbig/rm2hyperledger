package rm2hyperledger;

import java.util.HashSet;

public class FormalParameterCollector extends JavaParserBaseVisitor<Object> {

	public HashSet<String> parameterNames = new HashSet<>();


	@Override
	public Object visitFormalParameter(JavaParser.FormalParameterContext ctx) {
		parameterNames.add(ctx.variableDeclaratorId().IDENTIFIER().getText());
		return null;
	}


}
