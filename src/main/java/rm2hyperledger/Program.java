package rm2hyperledger;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStreamRewriter;
import rm2hyperledger.operations.*;

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
import java.util.stream.Collectors;

public class Program {
	private static final Logger logger = Logger.getLogger("");

	private static boolean canRunGit = false;

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


		String targetFolder = "D:\\rm2pt\\LibraryMS-hyperledger";
		String reModelFile = "D:\\rm2pt\\RM2PT-win32.win32.x86_64-1.2.1\\workspace\\LibraryMS\\RequirementsModel\\LibraryManagementSystem.remodel";

		if (System.getenv("autohotkey") != null)
			Runtime.getRuntime().exec(new String[]{System.getenv("autohotkey"), Path.of("./src/autohotkey/closeTortoisegit.ahk").toAbsolutePath().toString(), targetFolder});
		else
			logger.info("Some processes may lock " + targetFolder + ", set environment variable \"autohotkey\" to close these processes.");


		try {
			if (Files.isDirectory(Path.of(targetFolder, ".git"))) {
				Runtime.getRuntime().exec("git --version", null, new java.io.File(targetFolder)).waitFor();
				canRunGit = true;
			}
		}
		catch (Exception e1) {
			logger.info("Unable to run git: " + e1.getMessage());
		}


		new EntityManagerSaveStates(targetFolder).editCommit();

		convertEntityManagerCallSite(targetFolder);

		new EntityManagerRandomMethods(targetFolder).editCommit();
		new EntityManagerStubGetterSetter(targetFolder).editCommit();

		var addTransactionWrapper = new ContractAddTransactionWrapper(targetFolder);
		addTransactionWrapper.editCommit();

		new TransactionReturnListToArray(targetFolder).editCommit();

		new AddGensonToContract(targetFolder).editCommit();

		removeRefreshMethod(targetFolder);

		//Why can't we run convertEntities before convertReferenceToPK?
		ConvertEntities convertEntities = new ConvertEntities(targetFolder);
		convertEntities.editCommit();

		new MarkGensonConverters(targetFolder, convertEntities.getEntityNames()).editCommit();

		new EntityManagerGetObjectByPK(targetFolder, convertEntities.getEntityNames()).editCommit();

		convertReferenceToPK(reModelFile, targetFolder);

		new SaveModified(targetFolder, addTransactionWrapper.contractTransactions, convertEntities.getEntityNames()).editCommit();
		// ConvertGlobalFields will change field access to getter access, so it's harder for SaveModified to tell what needs to save.
		// so we call SaveModified before ConvertGlobalFields.
		new ConvertContractFields(targetFolder, reModelFile, pkMap).editCommit();

		fixLineEnding(targetFolder);

		copySkeleton(targetFolder);
	}

	private static String getFileNameWithoutExtension(Path file) {
		var f = file.getFileName().toString();
		var p = f.lastIndexOf(".");
		if (p != -1)
			return f.substring(0, p);
		else
			return f;
	}

	static List<FieldDefinition> pkMap;

	private static String findPKType(Path folder, String className, String pkName) {
		String currentClassName = className;
		try {
			while (currentClassName != null) {
				CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(Path.of(folder.toString(), currentClassName + ".java"))));

				JavaParser parser = new JavaParser(tokens);
				var typeName = FieldTypeFinder.findField(parser.compilationUnit(), pkName);
				if (typeName != null)
					return typeName;

				parser.reset();
				currentClassName = SuperClassVisitor.findSuperClass(parser.compilationUnit());
			}
			logger.warning(String.format("Field %s is not found in %s and its super classes.", pkName, className + ".java"));
		}
		catch (IOException e) {
			logger.severe(e.getMessage());
		}
		return null;
	}

	private static void convertReferenceToPK(String reModelFile, String targetFolder) throws IOException {
		var primaryKeyCollector = new PrimaryKeyCollector(reModelFile);

		pkMap = primaryKeyCollector.collect().entrySet().stream().map(s -> {
			String pkName = StringHelper.lowercaseFirstLetter(s.getValue());
			String type = findPKType(Path.of(targetFolder, "src\\main\\java\\entities"), s.getKey(), pkName);
			if (type != null)
				return new FieldDefinition(s.getKey(), pkName, type);
			else
				return null;
		}).filter(Objects::nonNull).collect(Collectors.toList());

		new AddEntityGetPK(targetFolder, pkMap).editCommit();


		//For all entity classes, if they refer to another entity, the reference must be replaced by the PK.
		new EntityChangeEntityReferenceToPK(targetFolder, pkMap).editCommit();
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
			if (canRunGit) {
				try {
					Runtime.getRuntime().exec("git add gradle/wrapper/*", null, new java.io.File(targetFolder)).waitFor();
					Runtime.getRuntime().exec("git add --chmod=+x gradlew", null, new java.io.File(targetFolder)).waitFor();
				}
				catch (Exception e1) {
					logger.warning("Unable to set execution bit for file ~/gradlew. " + e1.getMessage());
				}
			}
		}

		if(canRunGit) {
			try {
				Runtime.getRuntime().exec("git add --force gradle/wrapper/*", null, new java.io.File(targetFolder)).waitFor();
			}
			catch (Exception e1) {
				logger.warning("Failed to run git: " + e1.getMessage());
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
						logger.warning(String.format("IOException for %s: %s", destination, e.getMessage()));
					}
				});
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
				logger.severe(exception.toString());
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
				logger.severe(exception.toString());
			}
		});
	}


	private static void fixLineEnding(String targetFolder) throws IOException {
		Path servicesImplFolder = Path.of(targetFolder, "src\\main\\java");
		assert Files.exists(servicesImplFolder);

		Files.walk(servicesImplFolder).forEach(impl -> {
			try {
				if (Files.isDirectory(impl))
					return;

				String content = Files.readString(impl);
				content = content.replaceAll("((?<!\\r)\\n|\\r(?!\\n))", System.getProperty("line.separator"));

				Files.writeString(impl, content);
			}
			catch (IOException exception) {
				logger.severe(exception.toString());
			}
		});
	}
}