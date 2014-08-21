package org.viking.shell.commands

import jline.Completor
import jline.ConsoleReader
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.api.errors.InvalidRemoteException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.shell.core.CommandMarker
import org.springframework.shell.core.annotation.CliAvailabilityIndicator
import org.springframework.shell.core.annotation.CliCommand
import org.springframework.shell.core.annotation.CliOption
import org.springframework.shell.support.util.OsUtils
import org.springframework.stereotype.Component
import org.viking.shell.commands.utils.CommandUtils
import org.viking.shell.commands.utils.GitUtils
import org.viking.shell.commands.utils.InvalidURLException
import org.viking.shell.commands.utils.LiferayVersionUtils
import org.viking.shell.commands.utils.ReloadUtils
import org.viking.shell.models.VikingProject

import java.nio.channels.CompletionHandler
import java.nio.file.Files
import java.util.concurrent.Executor

@Component
class VikingCommands implements CommandMarker {

    public static final TEMPLATES_ROOT = "templates/themes/"
    public static final AVAILABLE_VERSIONS = [
            "6.1": "6.1.2",
            "6.2": "6.2.0-RC5"
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
        if (varCommands.get("activeProject", null) != "Undefined") {
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

	def requestLiferayVersion() {
		ConsoleReader cr = new ConsoleReader()
		def versionOptions = 1..varCommands.variables["liferayVersions"].size()
		def liferayVersion = ""
		while (versionOptions.contains(liferayVersion)) {
			println "The following Liferay versions are available:"
			println varCommands.list("liferayVersions")

			liferayVersion = cr.readLine("liferay version: ") as Integer

			if (!versionOptions.contains(liferayVersion)) {
				println "Invalid Liferay version."

			}
		}
		return varCommands.variables["liferayVersions"].keySet()[liferayVersion -1]
	}

	@CliCommand(value = "setup-project", help = "Setup project.")
	def setupProject() {
		
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
						if (it.name.endsWith(".zip")){
							CommandUtils.unzip(it.path, "${projectDir}")
						}
					}

					def deployFile = new File("${CommandUtils.getLiferayDir(projectDir)}/deploy")
					if (!deployFile.exists()) deployFile.mkdirs()

					def dbpass = varCommands.get("dbpass", "databaseConnection")
					if (!dbpass || dbpass == "Undefined") {
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
					CommandUtils.generate("templates/baseConf/configFiles/$setEnvName", "${CommandUtils.getTomcatPath(projectDir)}/bin/$setEnvName", [
							"projectName":projectName,
							"tomcatPath": CommandUtils.getTomcatPath(projectDir),
							"dbuser": varCommands.get("dbuser", "databaseConnection"),
							"dbpass": dbpass,
					])

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

					def sqlDir = new File("${projectDir}/sql")

					if (!sqlDir.exists()) {
						sqlDir.mkdirs()
						CommandUtils.generate("templates/sql/${lrVersionKey}.sql", "${projectDir}/sql/${lrVersionKey}.sql", [
								projectName: projectName
						])
					}
				} catch (e) {
					e.printStackTrace()
					return
				}

			}

			try {
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
				restoreDatabase()
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

	@CliCommand(value = "init-dev-conf", help = "Initializes dev.conf file.")
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
						if (varCommands.get("activeProject", null) == "Undefined") {
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

			setupProject()

			restoreDatabase()
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
Port $activeProject.port is not responding..."""
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
				def warFile = new File("${activeProject.portletsPath}/build/libs").listFiles().find {it.name.endsWith(".war")}
				deployWar(warFile.path)
			}
			return "$activeProject.name successfully deployed."
		}
		return "Please set an active project."
    }

	@CliCommand(value = "prod-war", help = "Build and deploy the active project")
	def prodWar() {
		
		if (activeProject) {
			CommandUtils.executeGradle(activeProject.portletsPath, "war -Penv=prod")
			return "Prod WAR file generated in ${activeProject.portletsPath}/build/libs."
		}
		return "Please set an active project."
	}

	@CliCommand(value = "add-portlet", help = "Adds a portlet to the active project")
	def addPortlet( @CliOption(
			key = "name"
	) String name) {
		
		if (activeProject) {
			name = name.capitalize()
			CommandUtils.executeGradle(activeProject.portletsPath, "add --portletName=$name")
			return "$name portlet added."
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
			} else {
				return "Tail already running"
			}
		}
		return "Please set an active project."
    }

	@CliCommand(value = "update", help = "Updates templates located in ~/.viking-shell/templates.")
	def update() {
		println "Updating templates..."
		confReader.updateTemplates()
	}

	@CliCommand(value = "install-project", help = "Updates templates located in ~/.viking-shell/templates.")
	def installProject(@CliOption(
			key = "gitRepository"
	) String gitRepository) {
		ConsoleReader cr = new ConsoleReader()

		while(!gitRepository) {
			gitRepository = cr.readLine("Git repository: ")
		}

		try {
			def tempDir = Files.createTempDirectory("temp_viking_project_dir").toFile()
			def gitUtils = new GitUtils()
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
					setupProject()
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
	def restoreDatabase() {
		if (isOfflineCommandAvailable()) {
			if (activeProject) {
				ConsoleReader cr = new ConsoleReader()

				def sqlBackupFolder = new File("$activeProject.path/sql")
				def sqlBackupFile = sqlBackupFolder.listFiles().first()
				// TODO: use conf database connection
				def user = varCommands.get("dbuser", "databaseConnection")
				def pass = varCommands.get("dbpass", "databaseConnection")

				def databaseExists
				if (pass && pass != "Undefined") {
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

				if (pass && pass != "Undefined") {
					CommandUtils.execCommand("mysql -u $user -p$pass < $sqlBackupFile.path")
				} else {
					CommandUtils.execCommand("mysql -u $user < $sqlBackupFile.path")
				}
				return "Backup restored"
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


