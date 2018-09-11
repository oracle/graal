suite = {
    "mxversion" : "5.174.0",
    "name" : "espresso",

    # ------------- licenses

    "licenses" : {
        "GPLv2" : {
            "name" : "GNU General Public License, version 2",
            "url" : "http://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html"
        },
    },
    "defaultLicense" : "GPLv2",

    # ------------- imports

    "imports" : {
        "suites" : [
            {
                "name" : "truffle",
                "subdir" : True,
                "version" : "d763ec481c0d69f0dd5d1617c5f926ed1d4708b3",
                "urls" : [
                    {"url" : "https://github.com/graalvm/graal", "kind" : "git"},
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
        ],
    },

    # ------------- projects

    "projects" : {
        "com.oracle.truffle.espresso" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "truffle:TRUFFLE_API",
            ],
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "javaCompliance" : "1.8+",
            "checkstyleVersion" : "8.8",
        },

        "com.oracle.truffle.espresso.launcher" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "sdk:GRAAL_SDK",
                "sdk:LAUNCHER_COMMON",
            ],
            "javaCompliance" : "1.8+",
            "checkstyleVersion" : "8.8",
        },

        "com.oracle.truffle.espresso.test" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "com.oracle.truffle.espresso",
                "com.oracle.truffle.espresso.launcher",
                "truffle:TRUFFLE_INSTRUMENT_TEST",
                "mx:JUNIT",
            ],
            "javaCompliance" : "1.8+",
            "checkstyle" : "com.oracle.truffle.espresso",
        },
    },

    # ------------- distributions

    "distributions" : {
        "ESPRESSO" : {
            "subDir" : "src",
            "dependencies" : [
                "com.oracle.truffle.espresso",
                "com.oracle.truffle.espresso.test"
            ],
            "distDependencies" : [
                "truffle:TRUFFLE_API",
            ],
        },

        "ESPRESSO_LAUNCHER" : {
            "subDir" : "src",
            "dependencies" : [
                "com.oracle.truffle.espresso.launcher",
            ],
            "distDependencies" : [
                "sdk:GRAAL_SDK",
                "sdk:LAUNCHER_COMMON",
            ],
            "license" : "UPL",
            "description" : "Espresso launcher using the polyglot API.",
            "allowsJavadocWarnings": True,
        },
    }
}
