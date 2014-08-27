package org.viking.shell.commands.utils

import groovy.text.StreamingTemplateEngine
import org.apache.commons.io.FileUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.shell.support.util.OsUtils
import org.viking.shell.commands.ConfReader
import org.zeroturnaround.zip.ZipUtil
import org.apache.commons.io.IOUtils

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Created by juanitoramonster on 4/8/14.
 */
class CommandUtils {

    def static homeDir = System.getenv("HOME")

    def static isInstalled(pkg) {
        def command = "type -a $pkg"
        try {
			execCommand(command, true, true)
			return true
		} catch (e) {
			return false
		}

    }

    def static execCommand(String command, Boolean verbose = true, Boolean returnOutput = false) {
//        def proc = command.execute()
        def proc
		if (OsUtils.isWindows()) {
			proc = Runtime.getRuntime().exec(["cmd.exe","/C",command] as String[])
		} else {
			proc = Runtime.getRuntime().exec(["bash","-c",command] as String[])
		}
        def output = handleInputStream(proc.in, verbose, returnOutput)
		def errOutput = handleInputStream(proc.err, true, returnOutput)

		proc.waitFor()
        if (proc.exitValue() == 0) {
            return output
        } else {
            throw new Exception(errOutput)
        }
    }

	static handleInputStream(inputStream, printOutput, returnOutput) {
		BufferedReader br
		def output = ""
		try {
			br = new BufferedReader(new InputStreamReader(inputStream))
			String line
			while ((line = br.readLine()) != null) {
				if (printOutput) {
					println(line);
				}
				if (returnOutput) {
					output += line
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			IOUtils.closeQuietly(br)
		}
		return output
	}

	def static execCommand(List command, Boolean verbose = false) {
		def proc = command.execute()
		proc.waitFor()
		if (verbose) {
			println "stderr: ${proc.err.text}"
			println "stdout: ${proc.in.text}"
		}
		proc
	}

	def static copyPaths(String srcPath, String destPath) {
		FileUtils.copyDirectoryToDirectory(new File(srcPath), new File(destPath))
	}

    def static unzip(pathTozip, destPath) {

//         this.execCommand("unzip $pathTozip -d $destPath")
        ZipUtil.unpack(new File(pathTozip), new File(destPath));


    }

    def static String getTomcatPath(projectDir) {
        def tomcatDir = "NOT FOUND"
        projectDir.eachFile { f ->
            if (f.name.contains("liferay-portal") && f.isDirectory()) {
                f.eachFile { sf ->
                    if (sf.name.contains("tomcat")) {
                        tomcatDir = "${sf.path}"
                    }
                }
            }
        }
        return tomcatDir
    }

    def static String getLiferayDir(projectDir) {
        def liferayDir = "NOT FOUND"
        projectDir.eachFile { f ->
            if (f.name.contains("liferay-portal") && f.isDirectory()) {
                liferayDir = "${f.path}"
            }
        }
        return liferayDir
    }

    def static String getStartScript(projectDir) {
        def script = "NOT FOUND"
        projectDir.eachFile { f ->
            if (f.name.contains("liferay-portal") && f.isDirectory()) {
                f.eachFile { sf ->
                    if (sf.name.contains("tomcat")) {
						if (OsUtils.isWindows()) {
							script = "${sf.path}${File.separator}bin${File.separator}startup.bat"
						} else {
							script = "${sf.path}/bin/startup.sh"
						}
                    }
                }
            }
        }
        return script
    }

    def static String getStopScript(projectDir) {
        def script = "NOT FOUND"
        projectDir.eachFile { f ->
            if (f.name.contains("liferay-portal") && f.isDirectory()) {
                f.eachFile { sf ->
                    if (sf.name.contains("tomcat")) {
						if (OsUtils.isWindows()) {
							script = "${sf.path}${File.separator}bin${File.separator}shutdown.bat"
						} else {
							script = "${sf.path}/bin/shutdown.sh"
						}
                    }
                }
            }
        }
        return script
    }

	static boolean isTextFile(String filePath) throws Exception {
		File f = new File(filePath);
		if(!f.exists())
			return false;
		FileInputStream is = new FileInputStream(f);
		int size = is.available();
		if(size > 1000)
			size = 1000;
		byte[] data = new byte[size];
		is.read(data);
		is.close();
		String s = new String(data, "ISO-8859-1");
		String s2 = s.replaceAll(
				"[a-zA-Z0-9ßöäü\\.\\*!\"§\\\$\\%&/()=\\?@~'#:,;\\"+
						"+><\\|\\[\\]\\{\\}\\^°²³\\\\ \\n\\r\\t_\\-`´âêîô"+
						"ÂÊÔÎáéíóàèìòÁÉÍÓÀÈÌÒ©‰¢£¥€±¿»«¼½¾™ª]", "");
		// will delete all text signs

		double d = (double)(s.length() - s2.length()) / (double)(s.length());
		// percentage of text signs in the text

		return d > 0.95;
	}


	def static generate(source, dest, bindingData, useDefaultDir = true) {
		def content = useDefaultDir ? new File("$homeDir${File.separator}.viking-shell", source) : new File(source)
		def output = new File(dest)

		if (isTextFile(content.path)) {
			try {
				def engine = new StreamingTemplateEngine()
				if (output.exists()) {
					output.delete()
				}
				output.write(engine.createTemplate(new BufferedReader(new FileReader(content))).make(bindingData).toString())
			} catch (e) {
				FileUtils.copyFile(content, output)
			}
		} else {
			FileUtils.copyFile(content, output)
		}
    }

	def static generateDir(inputDir, outputDir, bindingData) {
		new File(inputDir).eachFileRecurse {
			if (!it.isDirectory()) {
				try {
					def filePath = it.path - inputDir
					def outputFile =  new File("${outputDir}${filePath}")
					if (!outputFile.exists()) {
						outputFile.parentFile.mkdirs()
					}
					generate(inputDir+filePath, outputFile.path, bindingData, false)
				} catch (e) {
					e.printStackTrace()
				}
			}
		}
	}

    def static download(url, dest="", fileName = null, downloadsDir = null) {
		if (downloadsDir == null) {
			downloadsDir = "$homeDir/.viking-shell/downloads"
		}

		MessageDigest md5 = MessageDigest.getInstance("MD5");
		md5.update(url.bytes);
		BigInteger hash = new BigInteger(1, md5.digest());
		def urlHash = hash.toString(16)

		// Verify that the destination directory exists and create it if not
        def destDir = new File("$downloadsDir/$dest")
        if (!destDir.exists()) {
            destDir.mkdirs()
        }

		// Download file
		if (fileName == null) {
			fileName = url.split("/").last().toString()
		}
		File destFile = new File(destDir, fileName)

		def cachedFile = new File("$homeDir/.viking-shell/downloads/cache", urlHash)
		if (!cachedFile.parentFile.exists()) {
			cachedFile.parentFile.mkdirs()
		}
		if (!destFile.exists()) {
			if (cachedFile.exists()) {
				Files.copy(cachedFile.toPath(), destFile.toPath())
			} else {
				println "Downloading $url"
				// Verify that the url is correct
				if (!new URL(url).openConnection().responseCode == "200") {
					throw new InvalidURLException()
				}
				def output = new BufferedOutputStream(new FileOutputStream(destFile))
				output << new URL(url).openStream()
				output.close()
				Files.copy(destFile.toPath(), cachedFile.toPath())
			}
		}
    }

    def static executeGradle(String project, tasks) {
		execCommand("gradle -p \"$project\" $tasks")
//        def projectConnection = GradleConnector.newConnector().forProjectDirectory(new File(project)).connect()
//        try {
//            projectConnection.newBuild().forTasks(tasks).run()
//        } finally {
//            projectConnection.close()
//        }
    }

    def static copyFromJar(source, dest){
        def srcFile = Thread.currentThread().contextClassLoader.getResource(source)
        def destFile = new File(dest)
        if (destFile.exists()) {
            destFile.delete()
        }
        destFile << srcFile.content
    }
}

class InvalidURLException extends Exception {

    public InvalidURLException() {}

    public  InvalidURLException(String message) {
        super(message)
    }

}
