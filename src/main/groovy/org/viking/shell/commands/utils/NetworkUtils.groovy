package org.viking.shell.commands.utils

/**
 * User: mardo
 * Date: 10/1/14
 * Time: 12:42 PM
 */
class NetworkUtils {
	static getHasInternet() {
		try {
			URL url = new URL("http://www.google.com")
			HttpURLConnection con = (HttpURLConnection) url.openConnection()
			con.connectTimeout = 10 * 1000
			con.connect()
			return true
		} catch (e) {
			return false
		}
	}
}
