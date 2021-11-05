package rm2hyperledger;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStreamRewriter;

import java.io.IOException;
import java.io.PrintWriter;
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


	public static void main(String[] args) throws IOException, URISyntaxException {
		Locale.setDefault(new Locale("en", "US"));
		List<String> argsList = Arrays.asList(args);
		try {
			int i = argsList.indexOf("--log");
			if (i > -1 && i + 1 < argsList.size()) {
				Handler defaultConsoleHandler = logger.getHandlers()[0];

				Level logLevel = Level.parse(argsList.get(i + 1).toUpperCase());

				defaultConsoleHandler.setLevel(logLevel);
				logger.setLevel(logLevel);
			}
		}
		catch (IllegalArgumentException ignored) {

		}


		String targetFolder = "D:\\rm2pt\\cocome-hyperledger";
		String reModelFile = "D:\\rm2pt\\RM2PT-win32.win32.x86_64-1.2.1\\workspace\\CoCoMe\\RequirementsModel\\cocome.remodel";

		convertEntities(targetFolder);

//		convertSystemFields(reModelFile, targetFolder);
//		new SystemFieldsCollector(reModelFile).collect();
//		var primaryKeyCollector = new PrimaryKeyCollector();
//		var map = primaryKeyCollector.collect();
//		for (var pair : map.entrySet()) {
//			System.out.printf("%s -> %s\n", pair.getKey(), pair.getValue());
//		}
//
//		if (true)
//			throw new UnsupportedOperationException();

//		CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromFileName("D:\\rm2pt\\cocome-hyperledger\\src\\main\\java\\services\\impl\\CoCoMESystemImpl.java")));
//		TokenStreamRewriter rewriter = new TokenStreamRewriter(tokens);
//		JavaParser parser = new JavaParser(tokens);
//		new EntityManagerCallSiteConverter(rewriter).visit(parser.compilationUnit());
//		System.out.println(rewriter.getText());


		copySkeleton(targetFolder);


		Path EntityManagerFileName = Paths.get(targetFolder, "src\\main\\java\\entities\\EntityManager.java");
		CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(EntityManagerFileName)));

		JavaParser parser = new JavaParser(tokens);
		TokenStreamRewriter rewriter = new TokenStreamRewriter(tokens);

		AddObjectConverter converter = new AddObjectConverter(rewriter, FileHelper.getFileLineEnding(EntityManagerFileName));
		converter.visit(parser.compilationUnit());
		try (PrintWriter out = new PrintWriter(EntityManagerFileName.toFile())) {
			out.print(rewriter.getText());
		}
		convertEntityManagerCallSite(targetFolder);


		convertContracts(targetFolder);

		removeRefreshMethod(targetFolder);
	}

	private static void convertContracts(String targetFolder) throws IOException {

		Path servicesImplFolder = Path.of(targetFolder, "src\\main\\java\\services\\");
		assert Files.exists(servicesImplFolder);
		Files.list(servicesImplFolder).forEach(interfaceFile -> {
			if (Files.isDirectory(interfaceFile))
				return;

			ArrayList<String> methodsToRewrite = new ArrayList<>();
			try {
				String interfaceCode = null;
				interfaceCode = rewriteInterface(interfaceFile, methodsToRewrite);

				if (interfaceCode == null)
					return;

				var implementationFile = Path.of(servicesImplFolder.toString(), "impl", FileHelper.getFileNameWithoutExtension(interfaceFile.getFileName().toString()) + "Impl.java");
				String implementationCode = rewriteImplementation(implementationFile, methodsToRewrite);
//				System.out.println(implementationCode);
				try (PrintWriter out = new PrintWriter(interfaceFile.toString())) {
					out.print(interfaceCode);
				}
				try (PrintWriter out = new PrintWriter(implementationFile.toString())) {
					out.print(implementationCode);
				}
			}
			catch (IOException exception) {
				logger.severe(exception.getMessage());
			}
//			System.out.println(interfaceFile.toString() + ":");
//			System.out.println(interfaceCode);
//			System.out.println();
//			System.out.println(implementationFile.toString() + ":");
//			System.out.println(implementationCode);
//			break;
		});
	}

	private static String rewriteImplementation(Path implementationFile, ArrayList<String> methodsToRewrite) throws IOException {
		if ("\n".equals(FileHelper.getFileLineEnding(implementationFile)) == false)
			logger.warning(String.format("Unix line ending is required for %s. The file ends up with mixed line ending afterwards.", implementationFile.getFileName()));


		CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(implementationFile)));
		TokenStreamRewriter rewriter = new TokenStreamRewriter(tokens);

		JavaParser parser = new JavaParser(tokens);
		var contractCollector = new ServiceImplementationConverter(rewriter, methodsToRewrite);
		contractCollector.visit(parser.compilationUnit());

		return rewriter.getText();
	}

	/**
	 * @param interfaceFile
	 * @param methodsToRewrite
	 * @return null if the file is not rewritable.
	 * @throws IOException
	 */
	private static String rewriteInterface(Path interfaceFile, ArrayList<String> methodsToRewrite) throws IOException {
		CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(interfaceFile)));
		TokenStreamRewriter rewriter = new TokenStreamRewriter(tokens);

		JavaParser parser = new JavaParser(tokens);
		ServiceInterfaceConverter contractCollector = new ServiceInterfaceConverter(rewriter);
		contractCollector.visit(parser.compilationUnit());

		if (contractCollector.getContractMethods().size() == 0)
			return null;

		if (interfaceFile.getFileName().equals(contractCollector.getInterfaceName() + ".java") == false)
			ServiceInterfaceConverter.logger.warning(String.format("Interface %s found in file %s.", contractCollector.getInterfaceName(), interfaceFile.getFileName()));

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
				if (Files.isDirectory(Path.of(targetFolder, ".git"))) {
					Runtime.getRuntime().exec("git add --chmod=+x gradlew", null, new java.io.File(targetFolder)).waitFor();
				}
			}
			catch (Exception e1) {
				logger.warning("Unable to set execution bit for file ~/gradlew. " + e1.getMessage());
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


	private static void convertSystemFields(String reModelPath, String targetFolder) throws IOException {
		var fileName = FileHelper.getFileNameWithoutExtension(Path.of(reModelPath).getFileName().toString());

		System.out.println(fileName);

		var systemFile = Path.of(targetFolder, "src\\main\\java\\services\\", fileName + "System.java");

		var fields = SystemFieldsCollector.collect(systemFile);

		System.out.println("System fields: " + String.join(", ", fields));
	}

	private static void convertEntityManagerCallSite(String targetFolder) throws IOException {
		Path servicesImplFolder = Path.of(targetFolder, "src\\main\\java\\services\\impl");
		assert Files.exists(servicesImplFolder);

		Files.list(servicesImplFolder).forEach(impl -> {
			try {
				CommonTokenStream tokens;
				tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(impl)));


				JavaParser parser = new JavaParser(tokens);
				TokenStreamRewriter rewriter = new TokenStreamRewriter(tokens);
				var converter = new EntityManagerCallSiteConverter(rewriter);

				converter.visit(parser.compilationUnit());
				try (PrintWriter out = new PrintWriter(impl.toFile())) {
					out.print(rewriter.getText());
				}
			}
			catch (IOException exception) {
				logger.severe(exception.getMessage());
			}
		});
	}


	private static void removeRefreshMethod(String targetFolder) throws IOException {

		Path servicesImplFolder = Path.of(targetFolder, "src\\main\\java\\services\\impl");
		assert Files.exists(servicesImplFolder);

		Files.list(servicesImplFolder).forEach(impl -> {
			try {
				CommonTokenStream tokens;
				tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(impl)));


				JavaParser parser = new JavaParser(tokens);
				TokenStreamRewriter rewriter = new TokenStreamRewriter(tokens);
				var refreshRemover = new RefreshRemover(rewriter);

				refreshRemover.visit(parser.compilationUnit());
				// System.out.print(rewriter.getText());
				try (PrintWriter out = new PrintWriter(impl.toFile())) {
					out.print(rewriter.getText());
				}
			}
			catch (IOException exception) {
				logger.severe(exception.getMessage());
			}
		});
	}

	private static void convertEntities(String targetFolder) throws IOException {

		Path servicesImplFolder = Path.of(targetFolder, "src\\main\\java\\entities");
		assert Files.exists(servicesImplFolder);

		Files.list(servicesImplFolder).forEach(impl -> {
			try {
				CommonTokenStream tokens;
				tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(impl)));


				JavaParser parser = new JavaParser(tokens);
				TokenStreamRewriter rewriter = new TokenStreamRewriter(tokens);
				var converter = new EntityConverter(rewriter);

				converter.visit(parser.compilationUnit());
//				try (PrintWriter out = new PrintWriter(impl.toFile())) {
//					out.print(rewriter.getText());
//				}
			}
			catch (IOException exception) {
				logger.severe(exception.getMessage());
			}
		});
	}
}