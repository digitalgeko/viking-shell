package org.viking.shell

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.shell.Bootstrap
import org.viking.shell.commands.ConfReader

class Main {

	@Autowired
	def ConfReader confReader

    static void main(String[] args) {
        Bootstrap.main(args)
    }
}

