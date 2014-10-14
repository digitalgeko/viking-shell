package org.viking.shell

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.viking.shell.commands.CheckDependencies
import org.viking.shell.commands.ConfReader
import org.viking.shell.commands.VarCommands
import org.viking.shell.commands.utils.NetworkUtils
import org.viking.shell.commands.utils.VersionUtils

import java.util.logging.Logger

/**
 * User: mardo
 * Date: 10/1/14
 * Time: 10:54 AM
 */

@Component
class InitManager {

	@Autowired
	def ConfReader confReader

	@Autowired
	def CheckDependencies checkDeps

	def log = Logger.getLogger(this.getClass().name)

	def init() {

		log.info "Checking dependencies..."
		checkDeps.check()

		log.info "Reading configuration..."
		confReader.readConf()

		if (NetworkUtils.hasInternet) {
			log.info "Updating templates..."
			confReader.updateTemplates()
		}

	}



}
