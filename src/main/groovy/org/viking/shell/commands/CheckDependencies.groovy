package org.viking.shell.commands

import org.apache.commons.lang.SystemUtils
import org.springframework.shell.support.util.OsUtils
import org.springframework.stereotype.Component
import org.viking.shell.commands.utils.CommandUtils

import java.util.logging.Logger

@Component
class CheckDependencies {

    def log = Logger.getLogger(this.getClass().name)

    def performUpdate = true

    def check() {

		if (SystemUtils.IS_OS_LINUX) {
			if (CommandUtils.isInstalled("apt-get")) {
				if (!CommandUtils.isInstalled("git")) {
					println "Git not found, installing..."
					updateAptGet(performUpdate)
					CommandUtils.execCommand("sudo apt-get -y install git", true)
				}


				if (!CommandUtils.isInstalled("gradle")) {
					println "Gradle not found, installing..."
					CommandUtils.execCommand("sudo apt-add-repository -y ppa:cwchien/gradle", true)
					CommandUtils.execCommand("sudo apt-get -y update")
					CommandUtils.execCommand("sudo apt-get -y install gradle")
				}

				if (!CommandUtils.isInstalled("mysql")) {
					println "MySQL not found, installing..."
					updateAptGet(performUpdate)
					CommandUtils.execCommand("sudo apt-get -y install mysql-server mysql-client", true)
				}

				if (!CommandUtils.isInstalled("mvn")) {
					println "Maven not found, installing..."
					updateAptGet(performUpdate)
					CommandUtils.execCommand("sudo apt-get -y install maven", true)
				}

				if (!CommandUtils.isInstalled("node")) {
					println "Node not found, installing..."
					updateAptGet(performUpdate)
					CommandUtils.execCommand("sudo apt-get -y install nodejs-legacy", true)
					CommandUtils.execCommand("sudo apt-get -y install npm", true)
				}

				if (CommandUtils.isInstalled("npm")) {
					if (!CommandUtils.isInstalled("coffee")) {
						println "CoffeeScript not found, installing..."
						CommandUtils.execCommand("sudo npm install -g coffee-script", true)
					}
				}

				if (!CommandUtils.isInstalled("mongod")) {
					log.warning("You should install mongodb, http://docs.mongodb.org/manual/tutorial/install-mongodb-on-ubuntu/")
				}
			}
		}

		if (SystemUtils.IS_OS_MAC_OSX) {
			if (!CommandUtils.isInstalled("brew")) {
				println """Please install homebrew by running:
ruby -e "\$(curl -fsSL https://raw.github.com/Homebrew/homebrew/go/install)"
in a terminal.
Please install also Xcode's latest version."""
				System.exit(1)
			}

			if (!CommandUtils.isInstalled("git")) {
				println "Git not found, installing..."
				updateBrew(performUpdate)
				CommandUtils.execCommand("brew install git", true)
			}

			if (!CommandUtils.isInstalled("gradle")) {
				println "Gradle not found, installing..."
				updateBrew(performUpdate)
				CommandUtils.execCommand("brew install gradle", true)
			}

			if (!CommandUtils.isInstalled("mysql")) {
				println "MySQL not found, installing..."
				updateBrew(performUpdate)
				CommandUtils.execCommand("brew install mysql", true)
			}

			if (!CommandUtils.isInstalled("mongod")) {
				println "MongoDB not found, installing..."
				updateBrew(performUpdate)
				CommandUtils.execCommand("brew install mongodb", true)
				CommandUtils.execCommand("ln -sfv /usr/local/opt/mongodb/*.plist ~/Library/LaunchAgents", true)
				CommandUtils.execCommand("launchctl load ~/Library/LaunchAgents/homebrew.mxcl.mongodb.plist", true)
			}

			if (!CommandUtils.isInstalled("node")) {
				println "Node not found, installing..."
				updateBrew(performUpdate)
				CommandUtils.execCommand("brew install node", true)
				CommandUtils.execCommand("npm install -g coffee-script", true)
			}

			if (!CommandUtils.isInstalled("mvn")) {
				println "Maven not found, installing..."
				updateBrew(performUpdate)
				CommandUtils.execCommand("brew install maven", true)
			}
		}
    }

	def updateAptGet(update) {
		if (update) {
			CommandUtils.execCommand("sudo apt-get -y update")
		}
		performUpdate = false
	}

    def updateBrew(update) {
        if (update) {
            CommandUtils.execCommand("brew update")
        }
        performUpdate = false
    }

}
