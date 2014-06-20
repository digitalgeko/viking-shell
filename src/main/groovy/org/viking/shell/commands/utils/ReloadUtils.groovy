package org.viking.shell.commands.utils

import org.apache.commons.vfs2.FileChangeEvent
import org.apache.commons.vfs2.FileListener
import org.apache.commons.vfs2.FileObject
import org.apache.commons.vfs2.FileSystemManager
import org.apache.commons.vfs2.VFS
import org.apache.commons.vfs2.impl.DefaultFileMonitor

import java.nio.file.FileSystemException
import static groovy.io.FileType.FILES

/**
 * User: mardo
 * Date: 5/15/14
 * Time: 12:40 PM
 */
class ReloadUtils {

	static monitorsMap = [:]

	static shouldIgnore(srcFile) {
		srcFile.path.contains(".listen_test") || srcFile.path.contains(".svn")
	}

	static listenForChanges(String projectPath, String projectName) {
		try {
			if (!monitorsMap["$projectPath/$projectName"]) {
				def RELOADABLES = [
						[projectType: "portlet", path:"$projectPath/$projectName/public", basePath: "$projectPath/$projectName"],
						[projectType: "theme", path:"$projectPath/$projectName-theme/src/main/webapp", basePath: "$projectPath/$projectName-theme"],
				]

				def tomcatPath = CommandUtils.getTomcatPath(new File(projectPath))
				FileSystemManager fsManager = null;

				try {
					fsManager = VFS.getManager();
				} catch (FileSystemException e) {
					e.printStackTrace()
				}

				def getDestTempTomcatDir = { suffix ->
					new File(tomcatPath, "temp").listFiles().findAll() { it.name.endsWith(suffix) }.max {new Integer(it.name - "-$suffix")}
				}

				def getDestWebappsDir = { contextPath ->
					new File(tomcatPath, "webapps").listFiles().find() { it.name.toLowerCase().contains(contextPath.toLowerCase()) }
				}

				def writeDestWithSource = null
				writeDestWithSource = { File srcFile ->
					try {
						def reloadable = RELOADABLES.find {srcFile.path.startsWith(it.path)}
						def relativePath = srcFile.path - reloadable.path

						File destFile = null
						switch (reloadable.projectType) {
							case "portlet":
								destFile = new File(getDestTempTomcatDir(projectName), relativePath)
								break
							case "theme":
								destFile = new File(getDestWebappsDir("$projectName-theme"), relativePath)
								break
						}

						if (srcFile.name.endsWith(".coffee")) {
							CommandUtils.execCommand(["coffee", "--bare", "--output", destFile.parent.replace("/coffee", "/js"), "--compile", srcFile.path])
						} else {
							destFile.text = srcFile.text
						}

						// Reload sass files that import srcFile
						if (reloadable.projectType == "theme" && srcFile.name.startsWith("_") && srcFile.name.endsWith("css") ) {
							def srcFileNameWithoutExtension = srcFile.name.substring(0, srcFile.name.lastIndexOf("."))
							new File(reloadable.path).eachFileRecurse(FILES) {
								if (!it.name.startsWith("_") && it.name.endsWith("css") && it.text.contains("@import") && it.text.contains(srcFileNameWithoutExtension)) {
									writeDestWithSource(it)
								}
							}
						}
					} catch (e) {
						e.printStackTrace()
					}

				}

				def fm = new DefaultFileMonitor(new FileListener() {
					public void fileCreated(FileChangeEvent event) throws Exception {
						def srcFile = event.file.localFile
						if (!shouldIgnore(srcFile)) {
							writeDestWithSource(srcFile)
						}
					}

					public void fileDeleted(FileChangeEvent event) throws Exception {
						def srcFile = event.file.localFile
						if (!shouldIgnore(srcFile)) {
							getDestFile(srcFile).delete()
						}
					}

					public void fileChanged(FileChangeEvent event) throws Exception {
						def srcFile = event.file.localFile
						if (!shouldIgnore(srcFile)) {
							writeDestWithSource(srcFile)
						}
					}
				})

				fm.delay = 250
				fm.setRecursive(true)
				RELOADABLES.each {
					fm.addFile(fsManager.resolveFile(it.path))
				}
				fm.start();

				monitorsMap["$projectPath/$projectName"] = fm
			}
		} catch (e) {
			e.printStackTrace()
		}

	}
}
