package org.viking.shell.commands.utils

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.PullCommand
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

/**
 * User: mardo
 * Date: 12/3/13
 * Time: 12:02 PM
 */
class GitUtils {

	String localFolderPath
	String gitRepo
	String username
	String password

	String branch = "master"

	def getRepoFolder() {
		def file = new File(localFolderPath)
		if (!file.exists()) file.mkdirs()
		file
	}

	def getGitCredentials() {
		new UsernamePasswordCredentialsProvider(username, password)
	}

	def ensureCloneRepo() {
		File gitRepoFile = getRepoFolder()
		if (!new File(gitRepoFile, ".git").exists()) {
			println "Cloning $gitRepo..."
			def gitCloneRepo = Git.cloneRepository()
			if (username && password) {
				gitCloneRepo.setCredentialsProvider(getGitCredentials())
			}

			gitCloneRepo
					.setURI(gitRepo)
					.setDirectory(gitRepoFile)
					.setBare(false).setBranch(branch)
					.call()
		}
	}

	def pull() {
		ensureCloneRepo();

		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		Repository repository = builder
				.readEnvironment()
				.findGitDir(getRepoFolder())
				.build();

		PullCommand pull = new PullCommand(repository)

		println "Pulling from $gitRepo..."

		if (username && password) {
			pull.setCredentialsProvider(getGitCredentials())
		}
		pull.call()
	}
}
