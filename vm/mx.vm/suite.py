suite = {
    "name" : "vm",
    "mxversion" : "5.144.0",
    "imports" : {
        "suites": [
            {
                "name" : "compiler",
                "subdir": True,
                "urls" : [
                    {"url" : "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind" : "binary"},
                ]
            },
        ]
    },

    "libraries" : {},

    "projects" : {
        "com.oracle.graalvm.locator" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "truffle:TRUFFLE_API",
            ],
            "javaCompliance" : "1.8",
            "checkstyle" : "org.graalvm.word",
            "license" : "GPLv2-CPE",
        },
    },

    "distributions" : {
        "GRAALVM" : {
            "native" : True,
            "class" : "GraalVmLayoutDistribution",
            "platformDependent" : True,
            "description" : "GraalVM distribution",
            "layout" : {
                "./" : [
                    "file:LICENSE",
                    "file:THIRDPARTYLICENSE",
                    "file:README.md",
                ],
                "jre/lib/boot/" : [
                    "dependency:sdk:GRAAL_SDK",
                ],
                "jre/lib/graalvm/" : [
                    "dependency:sdk:LAUNCHER_COMMON",
                ],
                "jre/lib/jvmci/parentClassLoader.classpath" : [
                    "string:../truffle/truffle-api.jar:../truffle/locator.jar:../truffle/truffle-nfi.jar",
                ],
                "jre/lib/truffle/" : [
                    "dependency:truffle:TRUFFLE_API",
                    "dependency:truffle:TRUFFLE_DSL_PROCESSOR",
                    "dependency:truffle:TRUFFLE_NFI",
                    "dependency:truffle:TRUFFLE_TCK",
                    "dependency:LOCATOR",
                    "extracted-dependency:truffle:TRUFFLE_NFI_NATIVE/include",
                ],
            },
        },
        "LOCATOR": {
            "dependencies": ["com.oracle.graalvm.locator"],
            "distDependencies" : [
                "truffle:TRUFFLE_API",
            ],
        },
    },
}
