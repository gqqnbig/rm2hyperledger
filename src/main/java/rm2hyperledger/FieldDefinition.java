package rm2hyperledger;

public class FieldDefinition {
	public String ClassName;
	public String VariableName;
	public String TypeName;


	public FieldDefinition(String className, String variableName, String typeName) {
		ClassName = className;
		VariableName = variableName;
		TypeName = typeName;
	}
}
