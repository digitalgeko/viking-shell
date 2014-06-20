package org.viking.shell

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.shell.plugin.support.DefaultPromptProvider
import org.springframework.stereotype.Component
import org.viking.shell.commands.CheckDependencies
import org.viking.shell.commands.ConfReader
import org.viking.shell.commands.VarCommands

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class VikingPromptProvider  extends DefaultPromptProvider {

    @Autowired
    def ConfReader confReader

    @Autowired
    def CheckDependencies checkDeps

	@Autowired
	VarCommands varCommands

    def readConf = true

    def mustCheckDependencies = true


    def String getPrompt() {
        if (mustCheckDependencies) {
            checkDeps.check()
            mustCheckDependencies = false
        }
        if (readConf) {
            confReader.readConf()
            readConf = false
        }
		def activeProject = varCommands.get("activeProject", null)
		activeProject != "Undefined" ? "$activeProject> " : "viking> "
    }

    def String getProviderName() {
        "Viking Portlets Framework"
    }

}