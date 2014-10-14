package org.viking.shell

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.shell.plugin.support.DefaultPromptProvider
import org.springframework.stereotype.Component
import org.viking.shell.commands.CheckDependencies
import org.viking.shell.commands.ConfReader
import org.viking.shell.commands.VarCommands

import java.util.logging.Logger

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class VikingPromptProvider  extends DefaultPromptProvider {

	@Autowired
	VarCommands varCommands

	@Autowired
	InitManager initManager

	def mustInit = true

    def String getPrompt() {

		if (mustInit) {
			initManager.init()
			mustInit = false
		}

		def activeProject = varCommands.get("activeProject", null)
		activeProject ? "$activeProject> " : "viking> "
    }

    def String getProviderName() {
        "Viking Portlets Framework"
    }

}