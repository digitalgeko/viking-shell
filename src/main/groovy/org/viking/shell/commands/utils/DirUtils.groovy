package org.viking.shell.commands.utils

import org.springframework.shell.support.util.OsUtils

/**
 * User: mardo
 * Date: 8/5/14
 * Time: 11:09 AM
 */
class DirUtils {

	static getVikingProjectsDir() {
		if (OsUtils.isWindows()) {
			return "C:\\viking-projects"
		}
		return "~/viking-projects"
	}

}
