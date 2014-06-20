package org.viking.shell.commands.utils

/**
 * User: mardo
 * Date: 5/15/14
 * Time: 11:11 AM
 */
class LiferayVersionUtils {

	public static final THEME_VERSIONS = [
			"LR612GA3": "6.1.2",
			"LR621GA2": "6.2.0-RC5"
	]

	static String getLiferayThemeVersion(liferayKey) {
		THEME_VERSIONS[liferayKey]
	}

}
