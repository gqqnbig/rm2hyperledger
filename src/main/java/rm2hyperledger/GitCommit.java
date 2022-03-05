package rm2hyperledger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.stream.Stream;

public abstract class GitCommit {
	protected final String targetFolder;
	protected Logger logger = Logger.getLogger(this.getClass().getSimpleName());

	private final String commitMessage;

	public GitCommit(String commitMessage, String targetFolder) {
		this.commitMessage = commitMessage;
		this.targetFolder = targetFolder;
	}

	/**
	 *
	 * @return changed files to be committed in Git
	 */
	protected abstract ArrayList<Path> editCommitCore() throws IOException;

	public void editCommit() {
		try {
			ArrayList<Path> changedFiles = editCommitCore();
			fixLineEnding(changedFiles);

			Runtime.getRuntime().exec(Stream.concat(Stream.of("git", "add"), changedFiles.stream().map(Path::toString)).toArray(String[]::new),
					null, new java.io.File(targetFolder)).waitFor();

			Runtime.getRuntime().exec(new String[]{"git", "commit", "-m", commitMessage}, null, new java.io.File(targetFolder)).waitFor();
		}
		catch (IOException | InterruptedException exception) {
			logger.severe(exception.getMessage());
		}
	}


	protected void fixLineEnding(ArrayList<Path> changedFiles) throws IOException {

		for (Path file : changedFiles) {
			String content = Files.readString(file);
			content = content.replaceAll("((?<!\\r)\\n|\\r(?!\\n))", System.getProperty("line.separator"));

			Files.writeString(file, content);
		}
	}
}
