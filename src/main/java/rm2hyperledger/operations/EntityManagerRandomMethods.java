package rm2hyperledger.operations;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStreamRewriter;
import rm2hyperledger.AddClassMemberVisitor;
import rm2hyperledger.GitCommit;
import rm2hyperledger.JavaLexer;
import rm2hyperledger.JavaParser;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

public class EntityManagerRandomMethods extends GitCommit {

	public EntityManagerRandomMethods(String targetFolder) {
		super("Add getRandom() and getGuid() to EntityManager", targetFolder);
	}

	@Override
	protected ArrayList<Path> editCommitCore() throws IOException {
		Path entityManagerFileName = Paths.get(targetFolder, "src\\main\\java\\entities\\EntityManager.java");
		CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(entityManagerFileName)));

		JavaParser parser = new JavaParser(tokens);
		TokenStreamRewriter rewriter = new TokenStreamRewriter(tokens);

		var member = new String[]{
				"",
				"private static java.util.Random random;",
				"",
				"public static java.util.Random getRandom() {",
				"\tif (random == null)",
				"\t\trandom = new Random(getStub().getTxTimestamp().toEpochMilli());",
				"\treturn random;",
				"}",
				"",
				"public static String getGuid() {",
				"\ttry {",
				"\t\treturn UUID.nameUUIDFromBytes(Long.toString(getRandom().nextLong()).getBytes(\"UTF-8\")).toString();",
				"\t}",
				"\tcatch (UnsupportedEncodingException e) {",
				"\t\tthrow new RuntimeException();",
				"\t}",
				"}"
		};

		new AddClassMemberVisitor(rewriter, AddClassMemberVisitor.EditLocation.End, member, "java.util.*", "java.io.*").visit(parser.compilationUnit());
		try (PrintWriter out = new PrintWriter(entityManagerFileName.toFile())) {
			out.print(rewriter.getText());
		}

		ArrayList<Path> paths = new ArrayList<>();
		paths.add(entityManagerFileName);
		return paths;
	}

}
