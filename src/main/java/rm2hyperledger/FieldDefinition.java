package rm2hyperledger;

public class FieldDefinition {
	public String ClassName;
	public String VariableName;
	public String VariableType;


	public FieldDefinition(String className, String variableName, String variableType) {
		ClassName = className;
		VariableName = variableName;
		VariableType = variableType;
	}
}
