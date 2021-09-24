import java.util.ArrayList;
import java.util.logging.Logger;

public class ContractCollector extends JavaParserBaseVisitor<Object> {
	static Logger logger = Logger.getLogger("ContractCollector");


	private String interfaceName;
	private ArrayList<String> contractMethods = new ArrayList<>();

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
		} else
			contractMethods.add(methodName);

		return null;
	}

	public String getInterfaceName() {
		return interfaceName;
	}

	public ArrayList<String> getContractMethods() {
		return contractMethods;
	}
}
