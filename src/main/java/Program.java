import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStreamRewriter;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Program {
	private static final Logger logger = Logger.getLogger("");

//	static Logger logger = Logger.getLogger("ContractCollector");

	public static void main(String[] args) throws IOException, URISyntaxException {
//		System.setProperty("user.language", "en");
		Locale.setDefault(new Locale("en", "US"));
//		java.util.Locale.setDefault(Locale.Category.DISPLAY, Locale.US);
//		java.util.Locale.setDefault(Locale.Category.FORMAT, Locale.US);

		List<String> argsList = Arrays.asList(args);
		try {
			int i = argsList.indexOf("--log");
			if (i > -1 && i + 1 < argsList.size()) {
				Handler defaultConsoleHandler = logger.getHandlers()[0];
//		LOGGER.info("Default log level of PARENT_LOGGER: " +PARENT_LOGGER.getLevel().toString());
//		LOGGER.info("Default Console Handler Log Level: "+defaultConsoleHandler.getLevel().toString());

				Level logLevel = Level.parse(argsList.get(i + 1).toUpperCase());

				defaultConsoleHandler.setLevel(logLevel);
				logger.setLevel(logLevel);
//				logger.log(Level.FINE, "here is finest log");
//				logger.log(Level.SEVERE, "here is severe log");

//				rootLogger.getHandlers()[0].setLevel(Level.ALL);
			}
//

		}
		catch (IllegalArgumentException ignored) {

		}


		String targetFolder = "D:\\rm2pt\\cocome-hyperledger";

//		copySkeleton(targetFolder);
//
//
//		Path EntityManagerFileName = Paths.get(targetFolder, "src\\main\\java\\entities\\EntityManager.java");
//		CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(EntityManagerFileName)));
//
//		JavaParser parser = new JavaParser(tokens);
//		TokenStreamRewriter rewriter = new TokenStreamRewriter(tokens);
//
//		AddObjectConverter converter = new AddObjectConverter(rewriter, getFileLineEnding(EntityManagerFileName));
//		converter.visit(parser.compilationUnit());
////		System.out.print(rewriter.getText());
//		try (PrintWriter out = new PrintWriter(EntityManagerFileName.toFile())) {
//			out.print(rewriter.getText());
//		}


		convertContracts(targetFolder);
	}

	private static void convertContracts(String targetFolder) throws IOException {

		File servicesImplFolder = new File(targetFolder, "src\\main\\java\\services\\");
		HashMap<String, ArrayList<String>> contracts = new HashMap<>();
		for (File interfaceFile : servicesImplFolder.listFiles()) {
			if (interfaceFile.isDirectory())
				continue;

			ArrayList<String> methodsToRewrite = new ArrayList<>();
			String interfaceCode = rewriteInterface(interfaceFile, methodsToRewrite);
			if (interfaceCode == null)
				continue;

			File implementationFile = new File(new File(interfaceFile.getParent(), "impl"), FileHelper.getFileNameWithoutExtension(interfaceFile.getName()) + "Impl.java");
			String implementationCode = rewriteImplementation(implementationFile, methodsToRewrite);

			System.out.println(interfaceFile.toString() + ":");
			System.out.println(interfaceCode);
			System.out.println();
			System.out.println(implementationFile.toString() + ":");
			System.out.println(implementationCode);
			break;
		}
	}

	private static String rewriteImplementation(File implementationFile, ArrayList<String> methodsToRewrite) throws IOException {
		CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromFileName(implementationFile.getPath())));
		TokenStreamRewriter rewriter = new TokenStreamRewriter(tokens);

		JavaParser parser = new JavaParser(tokens);
		var contractCollector = new ServiceImplementationConverter(rewriter, methodsToRewrite);
		contractCollector.visit(parser.compilationUnit());

		return rewriter.getText();
	}

	private static String rewriteInterface(File interfaceFile, ArrayList<String> methodsToRewrite) throws IOException {
		CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromFileName(interfaceFile.getPath())));
		TokenStreamRewriter rewriter = new TokenStreamRewriter(tokens);

		JavaParser parser = new JavaParser(tokens);
		ServiceInterfaceConverter contractCollector = new ServiceInterfaceConverter(rewriter);
		contractCollector.visit(parser.compilationUnit());

		if (contractCollector.getContractMethods().size() == 0)
			return null;

		if (interfaceFile.getName().equals(contractCollector.getInterfaceName() + ".java") == false)
			ServiceInterfaceConverter.logger.warning(String.format("Interface %s found in file %s.", contractCollector.getInterfaceName(), interfaceFile.getName()));

		if (ServiceInterfaceConverter.logger.isLoggable(Level.FINE)) {
			String msg = contractCollector.getInterfaceName() + " has contracts: ";
			msg += String.join(", ", contractCollector.getContractMethods());
			ServiceInterfaceConverter.logger.fine(msg);
		}

		methodsToRewrite.addAll(contractCollector.getContractMethods());
		return rewriter.getText();
	}

	private static void copySkeleton(String targetFolder) throws URISyntaxException, IOException {
		URI path = Program.class.getProtectionDomain().getCodeSource().getLocation().toURI();
		URI resourcesPath = path.resolve("../../../resources/main/project-skeleton");

		copyDirectory(Paths.get(resourcesPath).toString(), targetFolder);
		Files.move(Paths.get(targetFolder, "gitignore"), Paths.get(targetFolder, ".gitignore"), StandardCopyOption.REPLACE_EXISTING);

		try {
			Path gradlewFile = Paths.get(targetFolder, "gradlew");
			var permissions = Files.getPosixFilePermissions(gradlewFile);
			permissions.add(PosixFilePermission.OWNER_EXECUTE);
			permissions.add(PosixFilePermission.GROUP_EXECUTE);
			permissions.add(PosixFilePermission.OTHERS_EXECUTE);
			Files.setPosixFilePermissions(gradlewFile, permissions);
		}
		catch (UnsupportedOperationException e) {
			//On Windows
			try {
				File f = new File(targetFolder, ".git");
				if (f.isDirectory()) {
					Runtime.getRuntime().exec("git add --chmod=+x gradlew", null, new File(targetFolder)).waitFor();
				}
			}
			catch (Exception e1) {
				logger.warning("Unable to set execution bit for file ~/gradlew. "+ e1.getMessage());
			}
		}
	}

	private static void copyDirectory(String sourceDirectoryLocation, String destinationDirectoryLocation) throws IOException {
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

	private static String getFileLineEnding(Path fileName) throws IOException {
		String content = Files.readString(fileName);
		if (content.contains("\r\n"))
			return "\r\n";
		else if (content.contains("\n"))
			return "\n";
		else if (content.contains("\r"))
			return "\r";
		else
			return System.getProperty("line.separator");
	}
}