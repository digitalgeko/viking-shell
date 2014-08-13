package org.viking.shell.models

/**
 * User: mardo
 * Date: 5/23/14
 * Time: 9:31 AM
 */
class VikingProject {

	String dir
	String name
	String path
	String portletsPath
	String themePath
	String liferayPath
	String tomcatPath

	def getPort() {
		def serverXmlFile = new File(tomcatPath, "conf/server.xml")
		def xml = new XmlSlurper().parseText(serverXmlFile.text)
		xml.Service.Connector.find{it.@protocol.text().startsWith("HTTP")}.collect{ it.@port }.first()
	}

	def isRunning () {
		try {
			new URL("http://localhost:"+port).text
			return true
		} catch (e) {
			return false
		}
	}

	static isVikingPortletsProject(File file) {
		def childrenNames = file.listFiles().collect {it.name}
		childrenNames.containsAll(['viking', 'public', 'conf', 'build.gradle'])
	}
}
