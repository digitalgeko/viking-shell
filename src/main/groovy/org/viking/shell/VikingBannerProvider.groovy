package org.viking.shell

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.shell.plugin.support.DefaultBannerProvider
import org.springframework.stereotype.Component


@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class VikingBannerProvider extends DefaultBannerProvider {

    @Override
    def String getBanner() {
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
         """
    }

    @Override
    def String getVersion() {
        "0.1"
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
