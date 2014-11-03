package org.viking.shell.commands.utils

import org.apache.commons.vfs2.FileChangeEvent
import org.apache.commons.vfs2.FileListener
import org.apache.commons.vfs2.FileSystemManager
import org.apache.commons.vfs2.VFS
import org.apache.commons.vfs2.impl.DefaultFileMonitor

import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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
			if (!monitorsMap["$projectPath${File.separator}$projectName"]) {
				def RELOADABLES = [
						[projectType: "portlet", path:"$projectPath${File.separator}$projectName${File.separator}public", basePath: "$projectPath${File.separator}$projectName"],
						[projectType: "portlet", path:"$projectPath${File.separator}$projectName${File.separator}viking/views", basePath: "$projectPath${File.separator}$projectName"]
				]

				new File(projectPath).listFiles().each {
					if (it.name.endsWith("-theme")) {
						RELOADABLES.add([projectType: "theme", path:"$projectPath${File.separator}$it.name${File.separator}src${File.separator}main${File.separator}webapp", basePath: "$projectPath${File.separator}$it.name"])
					}
				}

				def tomcatPath = CommandUtils.getTomcatPath(new File(projectPath))
				FileSystemManager fsManager = null;

				try {
					fsManager = VFS.getManager();
				} catch (FileSystemException e) {
					e.printStackTrace()
				}

				def getDestTempTomcatDir = { suffix ->
					new File(tomcatPath, "temp").listFiles().findAll { it.name.endsWith(suffix) }.max { it.lastModified() }
				}

				def getDestWebappsDir = { contextPath ->
					new File(tomcatPath, "webapps").listFiles().find() { it.name.toLowerCase().contains(contextPath.toLowerCase()) }
				}

				def writeDestWithSource = null
				writeDestWithSource = { File srcFile ->
					try {
						def reloadable = RELOADABLES.find { srcFile.path.startsWith(it.path) }
						def relativePath = srcFile.path - reloadable.path
						if (srcFile.path.contains("/viking/views")) {
							if (srcFile.name.endsWith(".js")) {
								relativePath = "/js"+relativePath
							} else if (srcFile.name.endsWith(".coffee")) {
								relativePath = "/coffee"+relativePath
							}
						}

						File destFile = null
						switch (reloadable.projectType) {
							case "portlet":
								destFile = new File(getDestTempTomcatDir(projectName), relativePath)
								break
							case "theme":
								def themeName = reloadable.basePath.substring(reloadable.basePath.lastIndexOf(File.separator)+1)
								destFile = new File(getDestWebappsDir(themeName), relativePath)
								break
						}

						if (srcFile.name.endsWith(".coffee")) {
							def destPath = destFile.parent.replace("${File.separator}coffee", "${File.separator}js")
							CommandUtils.execCommand(["coffee", "--bare", "--output", destPath, "--compile", srcFile.path])
						} else {
							Files.copy(srcFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
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
//						e.printStackTrace()
					}
				}

				def fm = new DefaultFileMonitor(new FileListener() {
					public void fileCreated(FileChangeEvent event) throws Exception {
						def srcFile = new File(event.file.getURL().toURI())
						if (!shouldIgnore(srcFile)) {
							writeDestWithSource(srcFile)
						}
					}

					public void fileDeleted(FileChangeEvent event) throws Exception {
						def srcFile = new File(event.file.getURL().toURI())
						if (!shouldIgnore(srcFile)) {
							getDestFile(srcFile).delete()
						}
					}

					public void fileChanged(FileChangeEvent event) throws Exception {
						def srcFile = new File(event.file.getURL().toURI())
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

				monitorsMap["$projectPath${File.separator}$projectName"] = fm
			}
		} catch (e) {
//			e.printStackTrace()
		}

	}
}
