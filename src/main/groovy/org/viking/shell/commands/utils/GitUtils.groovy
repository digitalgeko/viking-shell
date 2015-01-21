package org.viking.shell.commands.utils

import org.eclipse.jgit.api.CheckoutCommand
import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.PullCommand
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.StoredConfig
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.viking.shell.commands.VarCommands

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
		ensureCloneRepo()
		FileRepositoryBuilder builder = new FileRepositoryBuilder()
		Repository repository = builder
				.readEnvironment()
				.findGitDir(getRepoFolder())
				.build();
		def git = new Git(repository)

		StoredConfig config = git.repository.config
		config.setString("branch", branch, "merge", "refs/heads/$branch")
		config.save()

		git.checkout()
				.setCreateBranch(false)
				.setName(branch)
				.call()

		PullCommand pull = git.pull()

		println "Pulling from $gitRepo..."

		if (username && password) {
			pull.setCredentialsProvider(getGitCredentials())
		}
		def result = pull.call()
		result
	}
}
