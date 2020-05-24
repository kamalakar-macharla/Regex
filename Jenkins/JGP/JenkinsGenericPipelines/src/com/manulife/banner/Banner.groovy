package com.manulife.banner

import com.cloudbees.groovy.cps.*

/**
 *
 * Prints the banner in the Jenkins jobs' console
 *
 **/
class Banner implements Serializable {
    Script scriptObj

    Banner(Script scriptObj) {
        this.scriptObj = scriptObj
    }

    def print() {
        int random = Math.abs(new Random().nextInt() % 5)
        if (random == 0) {
            banner1()
        }
        else if (random == 1) {
            banner2()
        }
        else if (random == 2) {
            banner3()
        }
        else if (random == 3) {
            banner4()
        }
        else if (random == 4) {
            banner5()
        }

        scriptObj.echo('Documentation: https://gitlab.manulife.com/CDT_Common/JenkinsGenericPipelines/tree/release/1.2')
    }

    // To Generate new banners:  http://patorjk.com/software/taag/#p=display&f=Serifcap&t=

    def banner1() {
        scriptObj.echo('''


                8""""8                                          8""""8
                8    8 eeeee e   e  e eeee eeeee  eeee eeeee    8    8   e    e
                8eeee8 8  88 8   8  8 8    8   8  8    8   8    8eeee8ee 8    8
                88     8   8 8e  8  8 8eee 8eee8e 8eee 8e  8    88     8 8eeee8
                88     8   8 88  8  8 88   88   8 88   88  8    88     8   88
                88     8eee8 88ee8ee8 88ee 88   8 88ee 88ee8    88eeeee8   88

                             8
                             8  eeee eeeee e   e  e  eeeee eeeee
                             8e 8    8   8 8   8  8  8   8 8   "
                             88 8eee 8e  8 8eee8e 8e 8e  8 8eeee
                         e   88 88   88  8 88   8 88 88  8    88
                         8eee88 88ee 88  8 88   8 88 88  8 8ee88

    8""""8                                   8""""8
    8    " eeee eeeee eeee eeeee  e  eeee    8    8 e  eeeee eeee e     e  eeeee eeee eeeee
    8e     8    8   8 8    8   8  8  8  8    8eeee8 8  8   8 8    8     8  8   8 8    8   "
    88  ee 8eee 8e  8 8eee 8eee8e 8e 8e      88     8e 8eee8 8eee 8e    8e 8e  8 8eee 8eeee
    88   8 88   88  8 88   88   8 88 88      88     88 88    88   88    88 88  8 88      88
    88eee8 88ee 88  8 88ee 88   8 88 88e8    88     88 88    88ee 88eee 88 88  8 88ee 8ee88

                   88   8                                     88      eeee
                   88   8 eeee eeeee  eeeee e  eeeee eeeee     8         8
                   88  e8 8    8   8  8   " 8  8  88 8   8     8         8
                   "8  8  8eee 8eee8e 8eeee 8e 8   8 8e  8     8      eee8
                    8  8  88   88   8    88 88 8   8 88  8    8888    8
                    8ee8  88ee 88   8 8ee88 88 8eee8 88  8    8888 88 8eee


    ''')
    }

    def banner2() {
        scriptObj.echo('''


                o--o                              o     o--o
                |   |                             |     |   |
                O--o  o-o o   o   oo-o o-o o-o  o-O     O--o  o  o
                |     | |  \\ / \\ / |-' |   |-' |  |     |   | |  |
                o     o-o   o   o  o-o o   o-o  o-o     o--o  o--O
                                                                 |
                                                              o--o
                            o          o
                            |          | /  o
                            | o-o o-o  OO     o-o  o-o
                        \\   o |-' |  | | \\  | |  |  \\
                         o-o  o-o o  o o  o | o  o o-o


     o-o                              o--o             o
    o                      o          |   | o          | o
    |  -o o-o o-o  o-o o-o    o-o     O--o    o-o  o-o |   o-o  o-o o-o
    o   | |-' |  | |-' |   | |        |     | |  | |-' | | |  | |-'  \\
     o-o  o-o o  o o-o o   |  o-o     o     | O-o  o-o o | o  o o-o o-o
                                              |
                                              o
                o   o                              0      --
                |   |             o               /|     o  o
                o   o o-o o-o o-o   o-o o-o      o |       /
                 \\ /  |-' |    \\  | | | |  |       |      /
                  o   o-o o   o-o | o-o o  o     o-o-o O o--o


    ''')
    }

    def banner3() {
        scriptObj.echo('''


                   ______                                _    ______
                  (_____ \\                              | |  (____  \\
                   _____) )__  _ _ _  ____  ____ ____ _ | |   ____)  )_   _
                  |  ____/ _ \\| | | |/ _  )/ ___) _  ) || |  |  __  (| | | |
                  | |   | |_| | | | ( (/ /| |  ( (/ ( (_| |  | |__)  ) |_| |
                  |_|    \\___/ \\____|\\____)_|   \\____)____|  |______/ \\__  |
                                                                     (____/
                             _____           _     _
                            (_____)         | |   (_)
                               _  ____ ____ | |  _ _ ____   ___
                              | |/ _  )  _ \\| | / ) |  _ \\ /___)
                           ___| ( (/ /| | | | |< (| | | | |___ |
                          (____/ \\____)_| |_|_| \\_)_|_| |_(___/

      ______                         _          ______ _             _ _
     / _____)                       (_)        (_____ (_)           | (_)
    | /  ___  ____ ____   ____  ____ _  ____    _____) ) ____   ____| |_ ____   ____  ___
    | | (___)/ _  )  _ \\ / _  )/ ___) |/ ___)  |  ____/ |  _ \\ / _  ) | |  _ \\ / _  )/___)
    | \\____/( (/ /| | | ( (/ /| |   | ( (___   | |    | | | | ( (/ /| | | | | ( (/ /|___ |
     \\_____/ \\____)_| |_|\\____)_|   |_|\\____)  |_|    |_| ||_/ \\____)_|_|_| |_|\\____|___/
                                                        |_|
                 _    _               _                 __   ______
                | |  | |             (_)               /  | (_____ \\
                | |  | |___  ____ ___ _  ___  ____    /_/ |   ____) )
                 \\ \\/ / _  )/ ___)___) |/ _ \\|  _ \\     | |  /_____/
                  \\  ( (/ /| |  |___ | | |_| | | | |    | |_ _______
                   \\/ \\____)_|  (___/|_|\\___/|_| |_|    |_(_|_______)



    ''')
    }

    def banner4() {
        scriptObj.echo('''

                 ___                               _   ___
                | . \\ ___  _ _ _  ___  _ _  ___  _| | | . > _ _
                |  _// . \\| | | |/ ._>| '_>/ ._>/ . | | . \\| | |
                |_|  \\___/|__/_/ \\___.|_|  \\___.\\___| |___/`_. |
                                                           <___'
                         _            _    _
                        | | ___ ._ _ | |__<_>._ _  ___
                       _| |/ ._>| ' || / /| || ' |<_-<
                       \\__/\\___.|_|_||_\\_\\|_||_|_|/__/

     ___                       _        ___  _            _  _
    /  _>  ___ ._ _  ___  _ _ <_> ___  | . \\<_> ___  ___ | |<_>._ _  ___  ___
    | <_/\\/ ._>| ' |/ ._>| '_>| |/ | ' |  _/| || . \\/ ._>| || || ' |/ ._><_-<
    `____/\\___.|_|_|\\___.|_|  |_|\\_|_. |_|  |_||  _/\\___.|_||_||_|_|\\___./__/
                                                  |_|
                 _ _                _             _     ___
                | | | ___  _ _  ___<_> ___ ._ _  / |   <_  >
                | ' |/ ._>| '_><_-<| |/ . \\| ' | | | _  / /
                |__/ \\___.|_|  /__/|_|\\___/|_|_| |_|<_><___>


    ''')
    }

    def banner5() {
        scriptObj.echo('''


                         _____                               ______
                        (, /   )                       /)   (, /    )
                         _/__ / ____   _  _  __   _  _(/      /---(
                         /     (_) (_(/ _(/_/ (__(/_(_(_   ) / ____) (_/_
                      ) /                                 (_/ (     .-/
                     (_/                                           (_/
                                _____
                               (, /         /)   ,
                                 /   _ __  (/_    __   _
                             ___/___(/_/ (_/(___(_/ (_/_)_
                           /   /
                          (__ /
         _____)                           _____
       /                         ,       (, /   ) ,          /) ,
      /   ___    _ __    _  __     _      _/__ /   __    _  //   __    _  _
     /     / ) _(/_/ (__(/_/ (__(_(__     /     _(_/_)__(/_(/__(_/ (__(/_/_)_
    (____ /                            ) /      .-/
                                      (_/      (_/
                       __    __)                         _        _
                      (, )  /             ,            / /       '  )
                         | /  _  __  _      _____       /       ,--'
                         |/ _(/_/ (_/_)__(_(_) / (_    /    o  /___
                         |                            /


    ''')
    }
}