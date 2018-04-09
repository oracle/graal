suite = {
    "name": "vm",
    "version": "1.0.0-dev",
    "mxversion": "5.144.0",
    "imports": {
        "suites": [
            {
                "name": "truffle",
                "subdir": True,
                "urls": [
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
        ]
    },

    "libraries": {},

    "projects": {
        "com.oracle.graalvm.locator": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "truffle:TRUFFLE_API",
            ],
            "javaCompliance": "1.8",
            "checkstyle": "org.graalvm.word",
            "license": "GPLv2-CPE",
        },
        "org.graalvm.component.installer" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "javaCompliance" : "1.8",
            "license" : "GPLv2-CPE",
        },
        "org.graalvm.component.installer.test" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies": [
                "mx:JUNIT",
                "org.graalvm.component.installer"
            ],
            "javaCompliance" : "1.8",
            "license" : "GPLv2-CPE",
            "workingSets" : "Tools",
        },
        "polyglot.launcher": {
            "class": "GraalVmPolyglotLauncher",
            "launcherConfig": {
                "build_args": [
                    "-H:-ParseRuntimeOptions",
                    "-H:Features=org.graalvm.launcher.PolyglotLauncherFeature",
                    "--Language:all"
                ],
                "jar_distributions": [
                    "dependency:sdk:LAUNCHER_COMMON",
                ],
                "main_class": "org.graalvm.launcher.PolyglotLauncher",
                "destination": "polyglot",
            }
        }
    },

    "distributions": {
        "GRAALVM": {
            "native": True,
            "class": "GraalVmLayoutDistribution",
            "platformDependent": True,
            "description": "GraalVM distribution",
            "layout": {
                "<jdk_base>/": [
                    "file:LICENSE",
                    "file:THIRDPARTYLICENSE",
                    "file:README.md",
                ],
                "<jdk_base>/jre/lib/": ["extracted-dependency:truffle:TRUFFLE_NFI_NATIVE/include"],
                "<jdk_base>/jre/bin/polyglot": "dependency:polyglot.launcher",
                "<jdk_base>/bin/polyglot": "link:../jre/bin/polyglot",
                "<jdk_base>/jre/lib/boot/": [
                    "dependency:sdk:GRAAL_SDK",
                ],
                "<jdk_base>/jre/lib/graalvm/": [
                    "dependency:sdk:LAUNCHER_COMMON",
                ],
                "<jdk_base>/jre/lib/jvmci/parentClassLoader.classpath": [
                    "string:../truffle/truffle-api.jar:../truffle/locator.jar:../truffle/truffle-nfi.jar",
                ],
                "<jdk_base>/jre/lib/truffle/": [
                    "dependency:truffle:TRUFFLE_API",
                    "dependency:truffle:TRUFFLE_DSL_PROCESSOR",
                    "dependency:truffle:TRUFFLE_NFI",
                    "dependency:truffle:TRUFFLE_TCK",
                    "dependency:LOCATOR",
                    "extracted-dependency:truffle:TRUFFLE_NFI_NATIVE/include",
                ],
            },
            "os_arch": {
                "<others>": {
                    "<others>": {
                        "layout": {
                            "<jdk_base>/jre/lib/<arch>/": "extracted-dependency:truffle:TRUFFLE_NFI_NATIVE/bin/<lib:trufflenfi>",
                        }
                    },
                },
                "darwin": {
                    "<others>": {
                        "layout": {
                            "<jdk_base>/jre/lib/": "extracted-dependency:truffle:TRUFFLE_NFI_NATIVE/bin/<lib:trufflenfi>",
                        }
                    },
                },
            },
        },
        "LOCATOR": {
            "dependencies": ["com.oracle.graalvm.locator"],
            "distDependencies": [
                "truffle:TRUFFLE_API",
            ],
        },
        "INSTALLER": {
            "subDir" : "src",
            "mainClass" : "org.graalvm.component.installer.ComponentInstaller",
            "dependencies": ["org.graalvm.component.installer"],
        },
        "INSTALLER_TESTS": {
            "subDir" : "src",
            "dependencies": ["org.graalvm.component.installer.test"],
            "exclude": [
                "mx:HAMCREST",
                "mx:JUNIT",
            ],
            "distDependencies": [
                "INSTALLER",
            ],
        },
        "INSTALLER_GRAALVM_SUPPORT" : {
            "native" : True,
            "platformDependent" : True,
            "description" : "GraalVM Installer support distribution for the GraalVM",
            "layout" : {
                "./" : "dependency:vm:INSTALLER",
                "bin/" : "file:mx.vm/gu",
            },
        },
    },
}
