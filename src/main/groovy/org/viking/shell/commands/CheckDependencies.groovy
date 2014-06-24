package org.viking.shell.commands

import org.springframework.stereotype.Component
import org.viking.shell.commands.utils.CommandUtils

import java.util.logging.Logger

@Component
class CheckDependencies {

    def log = Logger.getLogger(this.getClass().name)

    def performUpdate = true

    def check() {
        if (!CommandUtils.isInstalled("brew")) {
            println """Please install homebrew by running:
ruby -e "\$(curl -fsSL https://raw.github.com/Homebrew/homebrew/go/install)"
in a terminal.
Please install also Xcode's latest version."""
            System.exit(1)
        }

        if (!CommandUtils.isInstalled("git")) {
            updateBrew(performUpdate)
            CommandUtils.execCommand("brew install git", true)
        }

        if (!CommandUtils.isInstalled("svn")) {
            updateBrew(performUpdate)
            CommandUtils.execCommand("brew install svn", true)
        }

        if (!CommandUtils.isInstalled("mysql")) {
            updateBrew(performUpdate)
            CommandUtils.execCommand("brew install mysql", true)
        }

        if (!CommandUtils.isInstalled("mongod")) {
            updateBrew(performUpdate)
            CommandUtils.execCommand("brew install mongodb", true)
            CommandUtils.execCommand("ln -sfv /usr/local/opt/mongodb/*.plist ~/Library/LaunchAgents", true)
            CommandUtils.execCommand("launchctl load ~/Library/LaunchAgents/homebrew.mxcl.mongodb.plist", true)
        }

        if (!CommandUtils.isInstalled("node")) {
            updateBrew(performUpdate)
            CommandUtils.execCommand("brew install node", true)
            CommandUtils.execCommand("npm install -g coffee-script", true)
        }

    }

    def updateBrew(update) {
        if (update) {
            CommandUtils.execCommand("brew update")
        }
        performUpdate = false
    }

}
