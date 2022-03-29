package rm2hyperledger.operations;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStreamRewriter;
import rm2hyperledger.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class AddGensonToContract extends GitCommit {
	public AddGensonToContract(String targetFolder) {
		super("Add static genson field to all contract classes", targetFolder);
	}

	@Override
	protected ArrayList<Path> editCommitCore() throws IOException {
		ArrayList<Path> changedFiles = new ArrayList<>();
		try (DirectoryStream<Path> files = Files.newDirectoryStream(Path.of(targetFolder, "src\\main\\java\\services\\impl"), "*.java")) {
			for (var f : files) {
				if (FileHelper.getFileNameWithoutExtension(f.getFileName().toString()).endsWith("Impl")) {
					try {
						CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(f)));
						JavaParser parser = new JavaParser(tokens);
						TokenStreamRewriter rewriter = new TokenStreamRewriter(tokens);

						var converter = new AddClassMemberVisitor(rewriter, AddClassMemberVisitor.EditLocation.Start,
								new String[]{"private static final Genson genson = new Genson();"}, "com.owlike.genson.Genson");
						converter.visitCompilationUnit(parser.compilationUnit());
						try (PrintWriter out = new PrintWriter(f.toFile())) {
							out.print(rewriter.getText());
						}
						changedFiles.add(f);
					}
					catch (IOException exception) {
						logger.severe(exception.toString());
					}
				}
			}
		}
		return changedFiles;
	}
}
