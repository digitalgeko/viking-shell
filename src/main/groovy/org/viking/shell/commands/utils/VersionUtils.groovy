package org.viking.shell.commands.utils

/**
 * User: mardo
 * Date: 10/1/14
 * Time: 10:57 AM
 */
class VersionUtils {

	static getCurrentVersion () {
		def projectInfo = new ConfigSlurper().parse(VersionUtils.classLoader.getResource("project-info.conf"))
		projectInfo.version
	}

	static getLatestVersionURL () {
		getRedirectURL(new URL("https://github.com/digitalgeko/viking-shell/releases/latest")).toString()
	}

	static getLatestVersion () {

		if (NetworkUtils.hasInternet) {

			def versionMatcher = latestVersionURL =~ /.*\D(\d+\.\d+\.\d+)/
			if (versionMatcher.matches()) {
				return versionMatcher.group(1)
			}
		}

		return currentVersion
	}

	static getHasLatestVersion() {
		currentVersion == latestVersion
	}

	private static getRedirectURL(URL url) {
		HttpURLConnection conn = url.openConnection()
		conn.instanceFollowRedirects = false
		conn.requestMethod = 'HEAD'
		if(conn.responseCode in [301,302]) {
			if (conn.headerFields.'Location') {
				return getRedirectURL(conn.headerFields.Location.first().toURL())
			} else {
				throw new RuntimeException('Failed to follow redirect')
			}
		}
		return url
	}
}
