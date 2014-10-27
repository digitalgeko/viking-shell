package org.viking.shell

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.shell.plugin.support.DefaultBannerProvider
import org.springframework.stereotype.Component
import org.viking.shell.commands.utils.VersionUtils


@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class VikingBannerProvider extends DefaultBannerProvider {

    @Override
    def String getBanner() {
		def txt =
				"""
dk           dk  dk  dk         dk
`dk         dk'  ""  dk         ""
 `dk       dk'       dk
  `dk     dk'    dk  dk   ,d8   dk  dk,dPPYba,    ,adPPYb,d8
   `dk   dk'     dk  dk ,a8"    dk  dkP'   `"8a  a8"    `Ydk
    `dk dk'      dk  dkdk(      dk  dk       dk  8b       dk
     `888'       dk  dk`"dka,   dk  dk       dk  "8a,   ,ddk
      `V'        dk  dk   `dka  dk  dk       dk   `"YbbdP"Y8
                                                  aa,    ,dk
                                                   "Y8bbdP"

------------------------------------------------------------
Viking shell $version
------------------------------------------------------------
         """


		if (!VersionUtils.hasLatestVersion) {
			txt +=
					"""
Viking shell ${VersionUtils.latestVersion} has been released!

To install the latest version of viking-shell, execute the following command in this shell:
install-shell --version latest

or install it manually by downloading from:
https://github.com/digitalgeko/viking-shell/releases/latest

------------------------------------------------------------
"""
		}

		txt
    }

    @Override
    def String getVersion() {
		VersionUtils.currentVersion
    }

    @Override
    def String getWelcomeMessage() {
        "Welcome to Viking!"
    }

    @Override
    def String getProviderName() {
        "Viking banner provider."
    }
}
