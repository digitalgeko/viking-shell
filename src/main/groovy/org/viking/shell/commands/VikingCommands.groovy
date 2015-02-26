package org.viking.shell.commands

import jline.Completor
import jline.ConsoleReader
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.api.errors.InvalidRemoteException
import org.fusesource.jansi.Ansi
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.shell.core.CommandMarker
import org.springframework.shell.core.annotation.CliCommand
import org.springframework.shell.core.annotation.CliOption
import org.springframework.shell.support.util.OsUtils
import org.springframework.stereotype.Component
import org.viking.shell.commands.utils.CommandUtils
import org.viking.shell.commands.utils.GitUtils
import org.viking.shell.commands.utils.InvalidURLException
import org.viking.shell.commands.utils.LiferayVersionUtils
import org.viking.shell.commands.utils.ReloadUtils
import org.viking.shell.commands.utils.VersionUtils

import org.viking.shell.models.VikingProject

import java.nio.file.Files

@Component
class VikingCommands implements CommandMarker {

    public static final TEMPLATES_ROOT = "templates/themes/"
    public static final AVAILABLE_VERSIONS = [
            "6.1": "6.1.2",
            "6.2": "6.2.1"
    ]

    @Autowired
    VarCommands varCommands

	@Autowired
	def ConfReader confReader

	def tailLogProc

	VikingProject getActiveProject() {
		return confReader.activeProject
	}

	public boolean isOnlineCommandAvailable() {
		return activeProject.isRunning();
	}

	public boolean isOfflineCommandAvailable() {
		return activeProject == null || !activeProject.isRunning();
	}

	@CliCommand(value = "start", help = "Starts Liferay for the active project.")
    def startLiferay(@CliOption(
			key = "skipLogs", specifiedDefaultValue = "true", unspecifiedDefaultValue = "false"
	) String skipLogs) {

		if (isOfflineCommandAvailable()) {
			try {
				if (activeProject) {
					liferayManager("startScript")
					if (!OsUtils.isWindows()) {
						println "Starting Liferay... please be patient!"
						if (skipLogs.toBoolean()) {
							Thread.sleep(2500)
							new URL("http://localhost:"+activeProject.port).text
							return "Something is listening in port $activeProject.port... might be your Liferay!"
						} else {
							tailLog()
							return ""
						}
					} else {
						return ""
					}
				}
			} catch (e) {
				e.printStackTrace()
			}

			return "Please set an active project."
		} else {
			return "Liferay is running, this command can not be executed."
		}
    }

    @CliCommand(value = "stop", help = "Stops Liferay for the active project.")
    def stopLiferay() {
        if (varCommands.get("activeProject", null)) {
            liferayManager("stopScript")
			if (tailLogProc) {
				tailLogProc.destroy()
				tailLogProc = null
			}
            return "Stopping Liferay..."
        }
        return "Please set an active project."
    }

	@CliCommand(value = "pwd", help = "Shows project paths.")
	def pwd() {
		if (activeProject) {
			println "Project path:\t\t$activeProject.path"
			println "Portlets path:\t\t$activeProject.portletsPath"
			println "Theme path:\t\t$activeProject.themePath"
			println "Liferay path:\t\t$activeProject.liferayPath"
			println "Tomcat path:\t\t$activeProject.tomcatPath"
			return
		}
		return "Please set an active project."
	}

    def liferayManager(status) {
        def scriptPath = varCommands.get(status, null)
        if (scriptPath) {
            def startScript = new File(scriptPath)
			if (OsUtils.isWindows()) {
				def batName = status == "startScript" ? "startup.bat" : "shutdown.bat"

				def binPath = scriptPath - batName
				CommandLine cmdLine = CommandLine.parse("cmd /c $batName");
				DefaultExecutor executor = new DefaultExecutor();
				executor.setWorkingDirectory(new File(binPath));
				executor.execute(cmdLine);
				return;
			} else {
				CommandUtils.execCommand(scriptPath)
			}
        }
    }

	def requestOption (options, message, promptMessage, errorMessage) {
		ConsoleReader cr = new ConsoleReader()
		def indexOptions = 1..options.size()
		def index = -1
		while (!indexOptions.contains(index)) {
			println message
			options.eachWithIndex { item, int i ->
				println "(${i+1}) $item"
			}
			try {
				index = cr.readLine(promptMessage).toInteger()
			} catch (e) {
				index = -1
			}

			if (!indexOptions.contains(index)) {
				println errorMessage
			}
		}
		index-1
	}

	def requestLiferayVersion() {
		def versions = varCommands.variables["liferayVersions"]
		def liferayVersion = requestOption(versions, "The following Liferay versions are available:", "liferay version: ", "Invalid Liferay version.")
		return versions.keySet()[liferayVersion]
	}

	@CliCommand(value = "setup-project", help = "Setup project.")
	def setupProject(@CliOption(
			key = "isNewProject",
			unspecifiedDefaultValue = "false"
	) String isNewProject) {
		
		if (activeProject) {
			ConsoleReader cr = new ConsoleReader()

			def projectDir = new File(activeProject.path)
			def projectName = activeProject.name

			def templatesDir = new File("$CommandUtils.homeDir${File.separator}.viking-shell${File.separator}templates")

			def lrVersionKey

			if (!new File(activeProject.liferayPath).exists()) {

				lrVersionKey = requestLiferayVersion()

				//Check if all the dependencies are met ???
				//Download liferay if needed
				println "--> Viking will download Liferay if needed. This could take several minutes depending on your connection speed."

				try {
					if (!new File("${CommandUtils.homeDir}/.viking-shell/downloads/$lrVersionKey").exists()) {
						CommandUtils.download(varCommands.variables["liferayURLs"][lrVersionKey], "${lrVersionKey}")
					}
				} catch (InvalidURLException e) {
					println ("The provided URL is not correct.")
					return
				}

				//Create environment for the project
				println "--> Creating a new Liferay environment for the project."

				try {
					new File("${CommandUtils.homeDir}/.viking-shell/downloads/${lrVersionKey}/").eachFile {
						if (it.name.endsWith(".zip")) {
							CommandUtils.unzip(it.path, "${projectDir}")
						}
					}

					def deployFile = new File("${CommandUtils.getLiferayDir(projectDir)}/deploy")
					if (!deployFile.exists()) deployFile.mkdirs()

					def dbpass = varCommands.get("dbpass", "databaseConnection")
					if (!dbpass || dbpass == null) {
						dbpass = ""
					}

					CommandUtils.generate("templates/baseConf/configFiles/portal-ext.properties", "${CommandUtils.getLiferayDir(projectDir)}/portal-ext.properties", [
							"projectName":projectName,
							"dbuser": varCommands.get("dbuser", "databaseConnection"),
							"dbpass": dbpass,
					])

					def setEnvName
					if (OsUtils.isWindows()) {
						setEnvName = "setenv.bat"
					} else {
						setEnvName = "setenv.sh"
					}
					CommandUtils.generate("templates/baseConf/configFiles/tomcat-testable-files/$setEnvName", "${CommandUtils.getTomcatPath(projectDir)}/bin/$setEnvName", [
							"projectName":projectName,
							"tomcatPath": CommandUtils.getTomcatPath(projectDir),
							"dbuser": varCommands.get("dbuser", "databaseConnection"),
							"dbpass": dbpass,
					])


					def tomcatManagerDir = new File("${CommandUtils.homeDir}${File.separator}.viking-shell", "templates/baseConf/configFiles/tomcat-testable-files/manager")
					def webappsDir = new File("${CommandUtils.getTomcatPath(projectDir)}/webapps")
					FileUtils.copyDirectoryToDirectory(tomcatManagerDir, webappsDir)

					def tomcatUsersXMLFile = new File("${CommandUtils.homeDir}${File.separator}.viking-shell", "templates/baseConf/configFiles/tomcat-testable-files/tomcat-users.xml")
					def tomcatConfDir = new File("${CommandUtils.getTomcatPath(projectDir)}/conf")
					FileUtils.copyFileToDirectory(tomcatUsersXMLFile, tomcatConfDir)

					if (OsUtils.isWindows()) {
						def binDir = "${CommandUtils.getTomcatPath(projectDir)}\\bin"
						def startScript = "$binDir\\startup.bat"
						println "Start script: $startScript"
						varCommands.set("startScript", startScript, null)
						def stopScript = "$binDir\\shutdown.bat"
						println "Stop script: $stopScript"
						varCommands.set("stopScript", stopScript, null)
					} else {
						def startScript = CommandUtils.getStartScript(projectDir)
						def binDir = startScript - "startup.sh"
						CommandUtils.execCommand("chmod +x $binDir/*.sh", true)
						println "Start script: $startScript"
						varCommands.set("startScript", startScript, null)
						def stopScript = CommandUtils.getStopScript(projectDir)
						println "Stop script: $stopScript"
						varCommands.set("stopScript", stopScript, null)
					}

				} catch (e) {
					e.printStackTrace()
					return
				}

			}

			try {
				def backupsDir = new File("${projectDir}/backups")

				if (!backupsDir.exists()) {
					if (!lrVersionKey) {
						lrVersionKey = requestLiferayVersion()
					}
					backupsDir.mkdirs()

					CommandUtils.generateDir(new File("$CommandUtils.homeDir${File.separator}.viking-shell", "templates/backups/${lrVersionKey}").path, "${projectDir}/backups/${lrVersionKey}", [
							projectName: projectName
					])
				}

				def themeOutputDir = "${projectDir}${File.separator}${projectName}-theme"
				if (!new File(themeOutputDir).exists()) {
					if (!lrVersionKey) {
						lrVersionKey = requestLiferayVersion()
					}
					// Create theme
					def themeInputDir = "$templatesDir.path${File.separator}themes${File.separator}${lrVersionKey}"
					CommandUtils.generateDir(themeInputDir, themeOutputDir, [
							"projectName": projectName,
							"liferayVersion": LiferayVersionUtils.getLiferayThemeVersion(lrVersionKey),
							"webappsProjectDir": "${CommandUtils.getTomcatPath(projectDir)}/webapps/${projectName}-theme",
					])
				}

				if (!new File("${projectDir}/.gitignore").exists()) {
					CommandUtils.generate("templates/baseConf/gitignore_template", "${projectDir}/.gitignore", [
							"projectName": projectName
					])
				}
				if (!new File("${projectDir}/project.conf").exists()) {
					CommandUtils.generate("templates/baseConf/project.conf", "${projectDir}/project.conf", [
							"projectName": projectName
					])
				}
				fullDeploy()
				restoreDatabase(isNewProject)
			} catch(e) {
				e.printStackTrace()
			}

			return "Project setup."
		}
		return "Please set an active project."
	}

	def projectNameIsValid(String name) {
		name && name.matches("[a-zA-Z]+")
	}

	@CliCommand(value = "init-dev-conf", help = "Initializes dev.conf file of your portlets project.")
	def initDevConf() {
		if (activeProject) {
			def devConfFile = new File("$activeProject.portletsPath/conf/dev.conf")
			if (!devConfFile.exists()) {
				CommandUtils.generate("templates/baseConf/.templates/conf/dev.conf", devConfFile.path, [
						projectDir: new File(activeProject.portletsPath)
				])
				return "dev.conf initialized at ${devConfFile.path}"
			} else {
				return "dev.conf file already exists"
			}

		}
		return "Please set an active project."
	}

	@CliCommand(value = "test", help = "Runs project tests.")
	def test(
			@CliOption(key = "regex", help = "Regex to match which tests should run") String regex,
			@CliOption(key = "clean", specifiedDefaultValue = "true", unspecifiedDefaultValue = "false") String clean
	) {
		if (activeProject) {
			def testCommand = ""
			if (clean == "true") {
				testCommand += " clean "
			}
			if (regex) {
				testCommand += " -Dtest.single=\"$regex\" "
			}
			testCommand += " test "
			CommandUtils.executeGradle(activeProject.portletsPath, testCommand)

			return "Tests executed"
		}
		return "Please set an active project."
	}

	@CliCommand(value = "new-project", help = "Create a new Viking project.")
    def newProject() {
		try {
			ConsoleReader cr = new ConsoleReader()

			//Verify if Liferay is running
			def activeLiferay = null
			
			try {
				if (activeProject) {
					activeLiferay = new URL("http://localhost:"+activeProject.port).text
				}

			} catch (Exception e) {
				// TODO
			}
			if (activeLiferay) {
				println "It seems that a Liferay instance is running..."
				def shouldStopLiferay = cr.readLine("Do you want to stop it? (y/n) ")
				switch (shouldStopLiferay) {
					case ["y", "yes", "Y", "Yes", "YES"]:
						if (varCommands.get("activeProject", null) == null) {
							println "Please set an active project."
							return
						}
						println stopLiferay()
						break
					default:
						println """The new project will become the active project.
To stop the Liferay instance that is currently running select the associated project as active.
You may now proceed with the new project creation."""

				}
			}

			//Ask for project name
			def projectName = cr.readLine("project name: ").capitalize()

			while(!projectNameIsValid(projectName)) {
				println "Project name should only contain letters"
				projectName = cr.readLine("project name: ").capitalize()
			}

			//Create project structure
			def projectDir = new File("${CommandUtils.homeDir}/${varCommands.variables["projectsDir"]}/${projectName}-env")
			projectDir.mkdirs()

			def projectStructure = [projectName,
					"$projectName/.templates"]

			projectStructure.each {
				new File("${projectDir.absolutePath}/$it").mkdirs()
			}

			def outputDir = "${projectDir}${File.separator}${projectName}"
			def templatesDir = new File("$CommandUtils.homeDir${File.separator}.viking-shell${File.separator}templates")

			// Copy templates
			CommandUtils.copyPaths("$templatesDir.path${File.separator}baseConf${File.separator}.templates", outputDir)
			CommandUtils.generate("templates/baseConf/.templates/build.gradle", "$outputDir/build.gradle", [
					projectName: projectName
			])
			// run gradle new
			CommandUtils.executeGradle("${projectDir}${File.separator}${projectName}", "new")

			// Configure environment
			println "--> Configuring management scripts..."
			varCommands.set("activeProject", projectName, null)
			varCommands.set("activeProjectDir", projectDir.name, null)

			addPortlet(projectName)
			setupProject(true.toString())
			println "** Project ${projectName} was successfully created and is now the active project. **"
		} catch (e) {
			e.printStackTrace()
		}
    }

    @CliCommand(value = "list-projects", help = "Lists the available projects.")
    def void listProjects() {
        def projectList = new File("${CommandUtils.homeDir}/${varCommands.get("projectsDir", null)}").listFiles().findAll { it.name.endsWith("-env") }
        projectList.eachWithIndex { f, i ->
            if (f.name.endsWith("-env")) {
                println "(${i + 1}) ${f.name - "-env"}${(f.name == varCommands.get("activeProjectDir",null)) ? " - active" : ""}"
            }
        }
    }

    @CliCommand(value = "use-project", help = "Set the active project.")
    def useProject() {

        def projectList = new File("${CommandUtils.homeDir}/${varCommands.get("projectsDir", null)}").listFiles().findAll { it.name.endsWith("-env") }
        def validProjectIds = 0..(projectList.size() - 1)
        projectList.eachWithIndex { f, i ->
            println "(${i + 1}) ${f.name - "-env"}${(f.name == varCommands.get("activeProjectDir", null)) ? " - active" : ""}"
        }
        //Read the new active project
        ConsoleReader cr = new ConsoleReader()
		cr.addCompletor(new Completor() {
			@Override
			int complete(String s, int i, List list) {
				list.addAll projectList.findAll{it.name.startsWith(s)}.collect {it.name - "-env"}
				return 0
			}
		})
        def projectChoice = cr.readLine("Select new active project: ")
		def newProjectDir
		if (projectChoice.isNumber()) {
			def projectId = (projectChoice as Integer) - 1
			if (validProjectIds.contains(projectId)) {
				newProjectDir = projectList[projectId]
			}
		} else {
			newProjectDir = projectList.find {it.name == "$projectChoice-env"}
		}
        if(newProjectDir) {
			def newProjectDirName = newProjectDir.name
            varCommands.set("activeProject", newProjectDirName - "-env", null)
            varCommands.set("activeProjectDir", newProjectDirName, null)
            def startScript = CommandUtils.getStartScript(new File("${CommandUtils.homeDir}/${varCommands.get("projectsDir", null)}/$newProjectDirName"))
            varCommands.set("startScript", startScript, null)
            def stopScript = CommandUtils.getStopScript(new File("${CommandUtils.homeDir}/${varCommands.get("projectsDir", null)}/$newProjectDirName"))
            varCommands.set("stopScript", stopScript, null)

			ReloadUtils.listenForChanges(activeProject.path, activeProject.name)
        } else {
            return  "Invalid project."
        }

        "Active project: ${varCommands.get("activeProject", null)}"
    }

    @CliCommand(value = "status", help = "Liferay status.")
    def liferayStatus() {
		if (isOnlineCommandAvailable()) {
			if (activeProject) {
				try {
					new URL("http://localhost:"+activeProject.port).text
					return """Active project: $activeProject.name
Something is listening in port $activeProject.port... might be your Liferay!"""


				} catch (Exception e) {
					return """Active project: $activeProject.name
Port $activeProject.port is not responding, or is responding with an error (i.e. error 500) ..."""
				}
			}
		} else {
			return "Liferay is offline, this command can not be executed."
		}

    }

	def deployWar (String warPath) {
		def warFile = new File(warPath)
		def warName = warFile.name
		def tmpdir = System.getProperty("java.io.tmpdir")
		FileUtils.copyFileToDirectory(warFile, new File(tmpdir))
		def destFile = new File("${activeProject.liferayPath}/deploy", warName)
		if (destFile.exists()) {
			destFile.delete()
		}
		FileUtils.moveFileToDirectory(new File("$tmpdir/$warName"), new File("${activeProject.liferayPath}/deploy"), true)
	}

    @CliCommand(value = "deploy", help = "Build and deploy the active project")
    def deploy( @CliOption(
			key = "target"
	) String target) {
		if (activeProject) {
			if (target == "theme") {
				new File(activeProject.path).listFiles().each {
					if (it.name.endsWith("-theme")) {
						CommandUtils.execCommand("mvn package -f \"${it.path}/pom.xml\"", true)
						def warFile = new File("${it.path}/target").listFiles().find {it.name.endsWith(".war")}
						deployWar(warFile.path)
					}
				}
			} else {
				CommandUtils.executeGradle(activeProject.portletsPath, "war")
				def warFile = new File("${activeProject.portletsPath}/build/libs").listFiles().find { it.name.endsWith(".war") }
				deployWar(warFile.path)
			}
			return "$activeProject.name successfully deployed."
		}
		return "Please set an active project."
    }

	@CliCommand(value = "prod-war", help = "Builds a WAR file without the dev.conf file, ready to deploy to production.")
	def prodWar() {

		if (activeProject) {
			CommandUtils.executeGradle(activeProject.portletsPath, "clean war -Penv=prod")
			return "Prod WAR file generated in ${activeProject.portletsPath}/build/libs."
		}
		return "Please set an active project."
	}


	@CliCommand(value = "clean", help = "Cleans build folders, and tomcat webapps, temp and work if specified")
	def clean(
			@CliOption(key = "portletsBuild",	specifiedDefaultValue = "true", unspecifiedDefaultValue = "false") String portletsBuild,
			@CliOption(key = "themeBuild",	specifiedDefaultValue = "true", unspecifiedDefaultValue = "false") String themeBuild,
			@CliOption(key = "portletsWebapp",	specifiedDefaultValue = "true", unspecifiedDefaultValue = "false") String portletsWebapp,
			@CliOption(key = "themeWebapp",	specifiedDefaultValue = "true", unspecifiedDefaultValue = "false") String themeWebapp,
			@CliOption(key = "tomcatTempAndWork",	specifiedDefaultValue = "true", unspecifiedDefaultValue = "false") String tomcatTempAndWork,
			@CliOption(key = "everything",	specifiedDefaultValue = "true", unspecifiedDefaultValue = "false") String everything
		) {
		if (activeProject) {

			boolean cleanEverything = everything != "false"

			if (portletsBuild != "false" || cleanEverything) {
				CommandUtils.executeGradle(activeProject.portletsPath, "clean")
			}

			if (themeBuild != "false" || cleanEverything) {
				CommandUtils.execCommand("mvn clean -f \"${activeProject.themePath}/pom.xml\"", true)
			}

			if (portletsWebapp != "false" || cleanEverything) {
				FileUtils.deleteQuietly(new File(activeProject.tomcatPath, "webapps/${activeProject.name}"))
				println "webapps/${activeProject.name} deleted"
			}

			if (themeWebapp != "false" || cleanEverything) {
				FileUtils.deleteQuietly(new File(activeProject.tomcatPath, "webapps/${activeProject.name}-theme"))
				println "webapps/${activeProject.name}-theme deleted"
			}

			if (tomcatTempAndWork != "false" || cleanEverything) {
				if (isOfflineCommandAvailable()) {
					new File(activeProject.tomcatPath, "temp").listFiles().each { FileUtils.deleteQuietly(it) }
					println "tomcat temp dir deleted"
					new File(activeProject.tomcatPath, "work").listFiles().each { FileUtils.deleteQuietly(it) }
					println "tomcat work dir deleted"
				} else {
					println "** Tomcat temp and work can not be deleted because liferay is running"
				}
			}

			if (portletsBuild == 'false' && themeBuild == 'false' && portletsWebapp == 'false' && themeWebapp == 'false' && tomcatTempAndWork == 'false' && everything == 'false') {
				println "You need to define at least one clean target (i.e. 'clean --portletBuild' or 'clean --portletBuild --themeBuild'), please run 'help clean' for more details"
			}
			return ""
		}
		return "Please set an active project."

	}

	@CliCommand(value = "install-shell", help = "Installs the version specified of viking-shell")
	def installShell(@CliOption(
			key = "version",
			mandatory = true
	) String version) {
		String installationPath = varCommands.get("viking-installation-dir", null)
		def installationDir = new File(installationPath)
		if (installationDir.name == "viking-shell") {
			installationPath = installationDir.parent
			installationDir = installationDir.parentFile
		}
		if (installationPath) {
			if (version == "latest") {
				version = VersionUtils.latestVersion
			}
			def versionZipURL = "https://github.com/digitalgeko/viking-shell/releases/download/viking-shell-$version/viking-shell-${version}.zip"
			try {
				CommandUtils.download(versionZipURL, ".", "viking-shell-${version}.zip", installationPath)
			} catch (e) {
				println "Version '$version' is not valid."
				return
			}

			def zipFile = new File(installationPath, "viking-shell-${version}.zip")
			CommandUtils.unzip(zipFile.path, installationPath)
			zipFile.delete()
			if (CommandUtils.execCommand("viking-shell version", false, true).toString().contains(version)) {
				println "Viking shell successfully updated to the latest version."
				println "Please start viking-shell again."
				System.exit(0)
			} else {
				println """
Viking shell is still not at the latest version, Are you sure you have your 'viking-installation-dir' correctly set? viking-installation-dir: $installationPath
If this directory is not correct please change it in your ~/.viking-shell/conf/init.conf, or install viking-shell manually:
https://github.com/digitalgeko/viking-shell/releases/latest
"""
			}
		} else {
			println """
Please edit the ~/.viking-shell/conf/init.conf and set a variable 'viking-installation-dir' where you have installed viking-shell, for example:
var set --name viking-installation-dir --value "/opt/viking-shell"

Reload the initial configuration by executing:
var reload

Then execute install-shell again.
"""
		}

	}

	@CliCommand(value = "add-portlet", help = "Adds a portlet to the active project")
	def addPortlet( @CliOption(
			key = "name"
	) String portletName) {
		
		if (activeProject) {
			try {
				portletName = portletName.capitalize()
				println "Adding new portlet: ${portletName}"

				def controllerFile = new File("$activeProject.portletsPath/viking/controllers/${portletName}Portlet.groovy")

				def viewsFolder = new File("$activeProject.portletsPath/viking/views/${portletName}Portlet")
				if (!viewsFolder.exists()) {
					viewsFolder.mkdirs()
				}

				CommandUtils.generate("templates/baseConf/.templates/classes/controllers/Portlet.groovy", controllerFile.path, [
						portletName: portletName
				])

				CommandUtils.generateDir("templates/baseConf/.templates/views/Portlet", viewsFolder.path, [
						portletName: portletName
				], true)
			} catch (e) {
				e.printStackTrace()
			}


			return "$portletName portlet added."
		}
		return "Please set an active project."
	}

	@CliCommand(value = "build-site", help = "Runs site builder")
	def buildSite() {
		if (isOnlineCommandAvailable()) {
			if (activeProject) {
				println "Building the site..."
				CommandUtils.executeGradle(activeProject.portletsPath, "build-site")
				return "Site built."
			}
			return "Please set an active project."
		} else {
			return "Liferay is offline, this command can not be executed."
		}
	}

    @CliCommand(value = "tail-log", help = "Show Liferay's log.")
    def tailLog() {
		if (activeProject) {
			if (!tailLogProc) {
				Thread.start {
					def command = "tail -f \"${activeProject.tomcatPath}/logs/catalina.out\""
					if (OsUtils.isWindows()) {
						tailLogProc = Runtime.getRuntime().exec(["cmd.exe","/C",command] as String[])
					} else {
						tailLogProc = Runtime.getRuntime().exec(["bash","-c",command] as String[])
					}
					CommandUtils.handleInputStream(tailLogProc.in, true, false)
				}
				return "Tailing liferay logs..."
			} else {
				return "Tail already running"
			}
		}
		return "Please set an active project."
    }

	@CliCommand(value = "install-project", help = "Installs a project from a git repository.")
	def installProject(
			@CliOption(key = "gitRepository") String gitRepository,
			@CliOption(key = "gitBranch", unspecifiedDefaultValue = "master") String branch
	) {
		ConsoleReader cr = new ConsoleReader()

		while(!gitRepository) {
			gitRepository = cr.readLine("Git repository: ")
		}

		try {
			def tempDir = Files.createTempDirectory("temp_viking_project_dir").toFile()
			def gitUtils = new GitUtils(branch: branch)
			gitUtils.localFolderPath = tempDir.path
			gitUtils.gitRepo = gitRepository
			gitUtils.pull()

			def vikingPortletsProject = tempDir.listFiles().find {VikingProject.isVikingPortletsProject(it)}

			if (vikingPortletsProject) {
				def projectName = vikingPortletsProject.name - "-env"
				def projectDir = new File("${CommandUtils.homeDir}/${varCommands.variables["projectsDir"]}/${projectName}-env")
				if (!projectDir.exists()) {
					tempDir.renameTo(projectDir)
					varCommands.set("activeProject", projectName, null)
					varCommands.set("activeProjectDir", projectDir.name, null)
					setupProject(false.toString())
					return "Project $projectName successfully installed"
				} else {
					return "Project $projectName already exists, please delete the project to install it again. Existing project's path is: $projectDir.path"
				}

			} else {
				return "No portlets project found in repository"
			}

		} catch (InvalidRemoteException e) {
			return "Git repository is not valid."
		} catch (e) {
			e.printStackTrace()
			return e.localizedMessage
		}
	}

	@CliCommand(value = "restore-database", help = "Restore database.")
	def restoreDatabase(@CliOption(
			key = "isNewProject",
			unspecifiedDefaultValue = "false"
	) String isNewProject) {
		if (isOfflineCommandAvailable()) {
			if (activeProject) {
				ConsoleReader cr = new ConsoleReader()

				def backupsFolder = new File("$activeProject.path/backups")
				def backupDirs = backupsFolder.listFiles().findAll{!it.name.startsWith(".")}
				File backupDir
				def options = backupDirs.collect{it.name} + 'No backup (Just drop and create database)'
				def backupDirIndex
				if (isNewProject != "false") {
					backupDirIndex = 0
				} else {
					backupDirIndex = requestOption(options, "The following backups are available:", "backup index: ", "Invalid backup.")
				}
				backupDir = backupDirIndex < backupDirs.size() ? backupDirs[backupDirIndex] : null

				// TODO: use conf database connection
				def user = varCommands.get("dbuser", "databaseConnection")
				def pass = varCommands.get("dbpass", "databaseConnection")

				def databaseExists
				if (pass) {
					databaseExists = CommandUtils.execCommand("mysqlshow --user=$user --password=$pass", false, true).contains(activeProject.name)
				} else {
					databaseExists = CommandUtils.execCommand("mysqlshow --user=$user", false, true).contains(activeProject.name)
				}

				if (databaseExists) {
					def restoreConfirmation = ""
					while (restoreConfirmation != "restore $activeProject.name") {
						restoreConfirmation = cr.readLine("Database $activeProject.name already exits, please type 'restore $activeProject.name' to remove the database contents continue with the restore process, or 'cancel' to keep the database as is: ")
						if (restoreConfirmation == 'cancel') {
							return "Operation cancelled."
						}
					}
				}

				if (backupDir) {
					def sqlBackupFile = new File(backupDir, "backup.sql")
					def dataDirectory = new File(backupDir, "data")
					def destDataDirectory = new File(activeProject.liferayPath, "data")

					if (pass) {
						CommandUtils.execCommand("mysql -u $user -p$pass < $sqlBackupFile.path")
					} else {
						CommandUtils.execCommand("mysql -u $user < $sqlBackupFile.path")
					}

					if (destDataDirectory.exists()) {
						FileUtils.deleteDirectory(destDataDirectory)
					}
					FileUtils.copyDirectoryToDirectory(dataDirectory, new File(activeProject.liferayPath))
					return "Backup restored"
				} else {
					if (pass) {
						CommandUtils.execCommand("mysql -u $user -p$pass -e \"DROP DATABASE IF EXISTS $activeProject.name; CREATE DATABASE $activeProject.name;\"")
					} else {
						CommandUtils.execCommand("mysql -u $user -e \"DROP DATABASE IF EXISTS $activeProject.name; CREATE DATABASE $activeProject.name;\"")
					}
					return "No backup selected. Database erased, please start liferay to populate with default data."
				}
			}
			return "Please set an active project."
		} else {
			return "Liferay is running, this command can not be executed."
		}
	}


	@CliCommand(value = "full-deploy", help = "Deploy the project and its dependencies.")
	def fullDeploy() {
		if (activeProject) {
			println "Deploying project and its dependencies..."
			deployDependencies()
			deploy("portlets")
			deploy("theme")

			return "Successfully deployed the project and its dependencies"
		}
		return "Please set an active project."
	}

	@CliCommand(value = "dependencies-deploy", help = "Deploy the project's dependencies.")
	def deployDependencies() {
		if (activeProject) {
			println "Deploying project's dependencies..."

			def projectsDir = varCommands.get("projectsDir",null)

			try {
				confReader.projectConf.dependencies.each { Map dependency ->

					switch (dependency.type) {
						case "LOCAL_WAR":
							if (!dependency.path.startsWith("/")) {
								dependency.path = "$activeProject.path/$dependency.path"
							}
							deployWar(dependency.path)
							break;

						case "URL":
							def downloadsDir = "$activeProject.path"
							def dependencyFile = new File(downloadsDir, "dependencies/$dependency.name")
							if (!dependencyFile.exists()) {
								CommandUtils.download(dependency.src, "dependencies", dependency.name, downloadsDir)
							}
							deployWar(dependencyFile.path)
							break;

						case "LOCAL_PROJECT":
							def dependencyPath = "${CommandUtils.homeDir}${File.separator}${projectsDir}${File.separator}${dependency.name}-env${File.separator}${dependency.name}"
							if (!new File("${dependencyPath}/build/libs/${dependency.name}.war").exists()) {
								CommandUtils.execCommand("gradle -p \"${dependencyPath}\" war", true)
							}
							deployWar("${dependencyPath}/build/libs/${dependency.name}.war")
							break;

						default:
							println "Dependency $dependency is not a supported type, please use 'LOCAL_WAR', 'URL' or 'LOCAL_PROJECT'"
					}

				}
			} catch (e) {
				e.printStackTrace()
			}

			return "Successfully deployed the project's dependencies"
		}
		return "Please set an active project."
	}
}


