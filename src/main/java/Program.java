import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStreamRewriter;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;

public class Program {
	public static void main(String[] args) throws IOException, URISyntaxException {
		String targetFolder = "D:\\rm2pt\\cocome-hyperledger";

		URI path = Program.class.getProtectionDomain().getCodeSource().getLocation().toURI();
		URI resourcesPath = path.resolve("../../../resources/main/project-skeleton");

		copyDirectory(Paths.get(resourcesPath).toString(), targetFolder);
		Files.move(Paths.get(targetFolder, "gitignore"), Paths.get(targetFolder, ".gitignore"), StandardCopyOption.REPLACE_EXISTING);


		Path EntityManagerFileName = Paths.get(targetFolder, "src\\main\\java\\entities\\EntityManager.java");
		CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(EntityManagerFileName)));

		JavaParser parser = new JavaParser(tokens);
		TokenStreamRewriter rewriter = new TokenStreamRewriter(tokens);

		AddObjectConverter converter = new AddObjectConverter(rewriter);
		converter.visit(parser.compilationUnit());


//		System.out.print(rewriter.getText());

		try (PrintWriter out = new PrintWriter(EntityManagerFileName.toFile())) {
			out.print(rewriter.getText());
		}
	}

	public static void copyDirectory(String sourceDirectoryLocation, String destinationDirectoryLocation) throws IOException {
		Files.walk(Paths.get(sourceDirectoryLocation))
				.forEach(source -> {
					Path destination = Paths.get(destinationDirectoryLocation, source.toString().substring(sourceDirectoryLocation.length()));
					try {
						Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
					}
					catch (DirectoryNotEmptyException ignored) {
					}
					catch (IOException e) {
						e.printStackTrace();
					}
				});
	}
}