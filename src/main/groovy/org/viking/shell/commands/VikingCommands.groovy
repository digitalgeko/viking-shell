package org.viking.shell.commands

import jline.ConsoleReader
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.shell.core.CommandMarker
import org.springframework.shell.core.annotation.CliAvailabilityIndicator
import org.springframework.shell.core.annotation.CliCommand
import org.springframework.shell.core.annotation.CliOption
import org.springframework.stereotype.Component
import org.viking.shell.commands.utils.CommandUtils
import org.viking.shell.commands.utils.InvalidURLException
import org.viking.shell.commands.utils.LiferayVersionUtils
import org.viking.shell.commands.utils.ReloadUtils

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

	@CliAvailabilityIndicator(["status","deploy","full-deploy","dependencies-deploy","build-site","tail-log"])
	public boolean isOnlineCommandAvailable() {
		return confReader.activeProject.isRunning();
	}

	@CliAvailabilityIndicator(["start", "restore-database"])
	public boolean isOfflineCommandAvailable() {
		return confReader.activeProject == null || !confReader.activeProject.isRunning();
	}

	@CliCommand(value = "start", help = "Starts Liferay for the active project.")
    def startLiferay() {
		try {
			def activeProject = confReader.activeProject
			if (activeProject) {
				liferayManager("startScript")
				println "Starting Liferay... please be patient!"
				Thread.sleep(2500)
				new URL("http://localhost:"+activeProject.port).text
				return "Something is listening in port $activeProject.port... might be your Liferay!"
			}
		} catch (e) {
			e.printStackTrace()
		}

        return "Please set an active project."
    }

    @CliCommand(value = "stop", help = "Stops Liferay for the active project.")
    def stopLiferay() {
        if (varCommands.get("activeProject", null) != "Undefined") {
            liferayManager("stopScript")
            return "Stopping Liferay..."
        }
        return "Please set an active project."
    }

    def liferayManager(status) {
        def scriptPath = varCommands.get(status, null)
        if (scriptPath) {
            def startScript = new File(scriptPath)
            CommandUtils.execCommand(scriptPath, true)
        }
    }

	@CliCommand(value = "setup-project", help = "Setup project.")
	def setupProject() {
		def activeProject = confReader.activeProject
		if (activeProject) {
			ConsoleReader cr = new ConsoleReader()

			def projectDir = new File(activeProject.path)
			def projectName = activeProject.name

			def templatesDir = new File("$CommandUtils.homeDir${File.separator}.viking-shell${File.separator}templates")

			if (!new File(activeProject.liferayPath).exists()) {
				println "The following Liferay versions are available:"
				println varCommands.list("liferayVersions")

				def liferayVersion = cr.readLine("liferay version: ") as Integer
				def versionOptions = 1..varCommands.variables["liferayVersions"].size()
				if (!versionOptions.contains(liferayVersion)) {
					println "Invalid Liferay version."
					return
				}
				def lrVersionKey = varCommands.variables["liferayVersions"].keySet()[liferayVersion -1]

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

					CommandUtils.execCommand("mkdir ${CommandUtils.getLiferayDir(projectDir)}/deploy")

					def dbpass = varCommands.get("dbpass", "databaseConnection")
					if (!dbpass || dbpass == "Undefined") {
						dbpass = ""
					}
					CommandUtils.generate("templates/baseConf/configFiles/portal-ext.properties", "${CommandUtils.getLiferayDir(projectDir)}/portal-ext.properties", [
							"projectName":projectName,
							"dbuser": varCommands.get("dbuser", "databaseConnection"),
							"dbpass": dbpass,
					])
					CommandUtils.generate("templates/baseConf/configFiles/setenv.sh", "${CommandUtils.getTomcatPath(projectDir)}/bin/setenv.sh", [
							"projectName":projectName,
							"dbuser": varCommands.get("dbuser", "databaseConnection"),
							"dbpass": dbpass,
					])

					def startScript = CommandUtils.getStartScript(projectDir)
					def binDir = startScript - "startup.sh"
					CommandUtils.execCommand("chmod +x $binDir/*.sh", true)
					println "Start script: $startScript"
					varCommands.set("startScript", startScript, null)
					def stopScript = CommandUtils.getStopScript(projectDir)
					println "Stop script: $stopScript"
					varCommands.set("stopScript", stopScript, null)


					def sqlDir = new File("${projectDir}/sql")
					if (!sqlDir.exists()) {
						sqlDir.mkdirs()
						CommandUtils.generate("templates/sql/${lrVersionKey}.sql", "${projectDir}/sql/${lrVersionKey}.sql", [
								projectName: projectName
						])
					}

					def themeOutputDir = "${projectDir}${File.separator}${projectName}-theme"
					if (!new File(themeOutputDir).exists()) {
						// Create theme
						def themeInputDir = "$templatesDir.path${File.separator}themes${File.separator}${lrVersionKey}"
						CommandUtils.generateDir(themeInputDir, themeOutputDir, [
								"projectName": projectName,
								"liferayVersion": LiferayVersionUtils.getLiferayThemeVersion(lrVersionKey),
								"webappsProjectDir": "${CommandUtils.getTomcatPath(projectDir)}/webapps/${projectName}-theme",
						])
					}
				} catch (e) {
					e.printStackTrace()
					return
				}

			}

			try {
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
		name.matches("[a-zA-Z]+")
	}

    @CliCommand(value = "new-project", help = "Create a new Viking project.")
    def newProject() {
        ConsoleReader cr = new ConsoleReader()

        //Verify if Liferay is running
        def activeLiferay = null
		def activeProject = confReader.activeProject
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
		CommandUtils.execCommand(["cp", "-r", "$templatesDir.path${File.separator}baseConf${File.separator}.templates", "$outputDir"])
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
        def projectId = (cr.readLine("Select new active project: ") as Integer) - 1
        if(validProjectIds.contains(projectId)) {
            def newProjectDirName = projectList[projectId].name
            varCommands.set("activeProject", newProjectDirName - "-env", null)
            varCommands.set("activeProjectDir", newProjectDirName, null)
            def startScript = CommandUtils.getStartScript(new File("${CommandUtils.homeDir}/${varCommands.get("projectsDir", null)}/$newProjectDirName"))
            varCommands.set("startScript", startScript, null)
            def stopScript = CommandUtils.getStopScript(new File("${CommandUtils.homeDir}/${varCommands.get("projectsDir", null)}/$newProjectDirName"))
            varCommands.set("stopScript", stopScript, null)

			def activeProject = confReader.activeProject
			ReloadUtils.listenForChanges(activeProject.path, activeProject.name)

			try {
				println "Configured port:"+activeProject.port
			} catch (e) {
				e.printStackTrace()
			}

        } else {
            return  "Invalid project."
        }

        "Active project: ${varCommands.get("activeProject", null)}"
    }

    @CliCommand(value = "status", help = "Liferay status.")
    def liferayStatus() {
		def activeProject = confReader.activeProject
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
    }

	def deployWar (String warPath) {
		def warName = warPath.split("/").last()
		def tmpdir = System.getProperty("java.io.tmpdir")
		CommandUtils.execCommand("cp \"${warPath}\" $tmpdir")
		CommandUtils.execCommand("mv \"$tmpdir/$warName\" ${confReader.activeProject.liferayPath}/deploy")
	}

    @CliCommand(value = "deploy", help = "Build and deploy the active project")
    def deploy( @CliOption(
			key = "target"
	) String target) {
        def activeProject = confReader.activeProject
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
		def activeProject = confReader.activeProject
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
		def activeProject = confReader.activeProject
		if (activeProject) {
			name = name.capitalize()
			CommandUtils.executeGradle(activeProject.portletsPath, "add --portletName=$name")
			return "$name portlet added."
		}
		return "Please set an active project."
	}

	@CliCommand(value = "build-site", help = "Runs site builder")
	def buildSite() {
		def activeProject = confReader.activeProject
		if (activeProject) {
			println "Building the site..."
			CommandUtils.executeGradle(activeProject.portletsPath, "build-site")
			return "Site built."
		}
		return "Please set an active project."
	}

    @CliCommand(value = "tail-log", help = "Show Liferay's log.")
    def tailLog() {
        def activeProject = confReader.activeProject
        if (activeProject) {
            CommandUtils.execCommand("tail -f \"${activeProject.tomcatPath}/logs/catalina.out\"", true)
        }
        return "Please set an active project."
    }

	@CliCommand(value = "update", help = "Updates templates located in ~/.viking-shell/templates.")
	def update() {
		println "Updating templates..."
		confReader.updateTemplates()
	}

	@CliCommand(value = "restore-database", help = "Restore database.")
	def restoreDatabase() {
		def activeProject = confReader.activeProject
		if (activeProject) {
			def sqlBackupFolder = new File("$activeProject.path/sql")
			def sqlBackupFile = sqlBackupFolder.listFiles().first()
			// TODO: use conf database connection
			def user = varCommands.get("dbuser", "databaseConnection")
			def pass = varCommands.get("dbpass", "databaseConnection")
			if (pass && pass != "Undefined") {
				CommandUtils.execCommand("mysql -u $user -p$pass < $sqlBackupFile.path")
			} else {
				CommandUtils.execCommand("mysql -u $user < $sqlBackupFile.path")
			}

			return "Backup restored"
		}
		return "Please set an active project."
	}


	@CliCommand(value = "full-deploy", help = "Deploy the project and its dependencies.")
	def fullDeploy() {
		def activeProject = confReader.activeProject
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
		def activeProject = confReader.activeProject
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
							def dependencyPath = "${CommandUtils.homeDir}/${projectsDir}/${dependency.name}-env/${dependency.name}"
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


