package rm2hyperledger;

import rm2hyperledger.checkers.ParameterChecker;
import org.antlr.v4.runtime.TokenStreamRewriter;

import java.util.ArrayList;
import java.util.logging.Logger;

public class ServiceInterfaceConverter extends JavaParserBaseVisitor<Object> {
	public static Logger logger = Logger.getLogger(ServiceInterfaceConverter.class.getSimpleName());


	private String interfaceName;
	private final ArrayList<String> contractMethods = new ArrayList<>();

	public String getInterfaceName() {
		return interfaceName;
	}

	public ArrayList<String> getContractMethods() {
		return contractMethods;
	}


	@Override
	public Object visitInterfaceDeclaration(JavaParser.InterfaceDeclarationContext ctx) {
		if (interfaceName == null)
			interfaceName = ctx.IDENTIFIER().getText();

		return visit(ctx.interfaceBody());
	}

	@Override
	public Object visitInterfaceMethodDeclaration(JavaParser.InterfaceMethodDeclarationContext ctx) {
		String methodName = ctx.IDENTIFIER().getText();
		JavaParser.TypeTypeOrVoidContext returnType = ctx.typeTypeOrVoid();

		if (methodName.startsWith("get") && ctx.formalParameters().children.size() == 2 && returnType.VOID() == null) {
			logger.finer("Skip " + methodName);
		} else if (methodName.startsWith("set") && ctx.formalParameters().children.size() == 3 && returnType.VOID() != null) {
			logger.finer("Skip " + methodName);
		} else {
			contractMethods.add(methodName);
		}
		return null;
	}
}
