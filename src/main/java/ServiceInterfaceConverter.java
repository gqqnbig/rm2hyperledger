import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStreamRewriter;

import java.util.ArrayList;
import java.util.logging.Logger;

public class ServiceInterfaceConverter extends ImportsCollector<Object> {
	static Logger logger = Logger.getLogger(ServiceInterfaceConverter.class.getSimpleName());


	private String interfaceName;
	private final ArrayList<String> contractMethods = new ArrayList<>();
//	private final TokenStreamRewriter rewriter;
//	private boolean contextClassNeeded = false;

	public ServiceInterfaceConverter(TokenStreamRewriter rewriter) {
		super(rewriter);
	}

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
			if (ParameterChecker.hasParameter(ctx.formalParameters(), "Context", "ctx") == false) {
				rewriter.insertAfter(ctx.formalParameters().start, "final Context ctx, ");
				newImports.add("org.hyperledger.fabric.contract.Context");
			}
		}
		return null;
	}
}
