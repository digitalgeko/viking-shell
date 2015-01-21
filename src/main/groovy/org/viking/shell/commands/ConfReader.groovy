package org.viking.shell.commands

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.shell.core.JLineShellComponent
import org.springframework.stereotype.Component
import org.viking.shell.commands.utils.CommandUtils
import org.viking.shell.commands.utils.GitUtils
import org.viking.shell.commands.utils.VersionUtils
import org.viking.shell.models.VikingProject

/**
 * Created by juanitoramonster on 12/16/13.
 */
@Component
class ConfReader {

    @Autowired
    def JLineShellComponent shell

	@Autowired
	VarCommands varCommands

	def INIT_FILE = "init.conf"

    def void readConf() {

        def homeDir = System.getenv("HOME")

        def defaultDirs = ["conf", "downloads"]

        defaultDirs.each { dirName ->
            def currentDir = new File("$homeDir${File.separator}.viking-shell${File.separator}$dirName")
            if (!currentDir.exists()) {
                currentDir.mkdirs()
            }
        }
        def vikingShellConfDir = new File("$homeDir${File.separator}.viking-shell${File.separator}conf")
		def templatesDir = new File("$CommandUtils.homeDir${File.separator}.viking-shell${File.separator}templates")
		if (!templatesDir.exists() || templatesDir.listFiles().size() == 0) {
			updateTemplates()
		}

        def defaultInitFile = new File(INIT_FILE)

		if (!defaultInitFile.exists()) {
			def srcInitFile = new File(templatesDir, "baseConf${File.separator}$INIT_FILE")
			def destInitFile = new File("$homeDir${File.separator}.viking-shell${File.separator}conf", INIT_FILE)
			if (srcInitFile.bytes && !destInitFile.exists()) {
				destInitFile << srcInitFile.bytes
			}
        }

        vikingShellConfDir.listFiles().each { file ->
            file.readLines().each { line ->
                shell.executeCommand(line)
            }
        }
    }

	def void updateTemplates() {

		def templatesDir = new File("$CommandUtils.homeDir${File.separator}.viking-shell${File.separator}templates")

		def branch = varCommands.get("templatesBranch", "")
		if (!branch) {
			if (VersionUtils.currentVersion == "develop") {
				branch = "develop"
			} else {
				branch = "viking-shell-templates-${VersionUtils.currentVersion}"
			}
		}

		def gitUtils = new GitUtils(branch: branch)
		gitUtils.localFolderPath = templatesDir.path
		gitUtils.gitRepo = varCommands.get("templatesRepo", "")
		if (gitUtils.gitRepo == null) {
			gitUtils.gitRepo = "https://github.com/digitalgeko/viking-shell-templates.git"
		}
		gitUtils.pull()

	}

	def getProjectConf () {
		new ConfigSlurper().parse(new File(activeProject.path, "project.conf").toURI().toURL())
	}

	def getActiveProject () {

		def activeProject = varCommands.get("activeProject", null)
		if (activeProject) {
			def projectDir = varCommands.get("activeProjectDir", null)
			def projectName = varCommands.get("activeProject", null)
			def projectsDir = varCommands.get("projectsDir",null)

			def path = "${CommandUtils.homeDir}${File.separator}${projectsDir}${File.separator}${projectDir}"
			return new VikingProject(
					dir: projectDir,
					name: projectName,
					path: path,
					portletsPath: "${path}${File.separator}${projectName}",
					themePath: "${path}${File.separator}${projectName}-theme",
					liferayPath: CommandUtils.getLiferayDir(new File(path)),
					tomcatPath: CommandUtils.getTomcatPath(new File(path)),
			)
		}

		return null
	}
}

