suite = {
    "name": "vm",
    "version": "1.0.0-rc1-dev",
    "mxversion": "5.151.0",
    "defaultLicense" : "GPLv2-CPE",
    "imports": {
        "suites": [
            {
                "name": "truffle",
                "subdir": True,
                "urls": [
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
            # Dynamic imports for components:
            {
                "name": "graal-nodejs",
                "subdir": True,
                "dynamic": True,
                "version": "90ebc1e6811729be3aa9b13c3a10ee06ec9b83b1",
                "urls" : [
                    {"url" : "https://github.com/oracle/js.git", "kind" : "git"},
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
            {
                "name": "graal-js",
                "subdir": True,
                "dynamic": True,
                "version": "90ebc1e6811729be3aa9b13c3a10ee06ec9b83b1",
                "urls": [
                    {"url": "https://github.com/oracle/js.git", "kind" : "git"},
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
            {
                "name": "truffleruby",
                "version": "86a287aae820fb9cca4cad050ed4a44ca5151e8d",
                "dynamic": True,
                "urls": [
                    {"url": "https://github.com/oracle/truffleruby.git", "kind": "git"},
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ],
                "os_arch": {
                    "linux": {
                        "sparcv9": {
                            "ignore": True
                        },
                        "<others>": {
                            "ignore": False
                        }
                    },
                    "<others>": {
                        "<others>": {
                            "ignore": False
                        }
                    }
                }
            },
            {
                "name": "fastr",
                "version": "2c164fafbbf84dbde64fb041b65e9967ba7ca465",
                "dynamic": True,
                "urls": [
                    {"url": "https://github.com/oracle/fastr.git", "kind": "git"},
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
            {
                "name": "sulong",
                "version": "499438a69432653afe744e42fb1a3808e9ee4b6b",
                "dynamic": True,
                "urls": [
                    {"url": "https://github.com/graalvm/sulong.git", "kind": "git"},
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ],
                "os_arch": {
                    "<others>": {
                        "sparcv9": {
                            "ignore": True
                        },
                        "<others>": {
                            "ignore": False
                        }
                    }
                }
            },
            {
                "name": "graalpython",
                "version": "45b4443d3c422e07dd9d0b07f848a7b742dfeeed",
                "dynamic": True,
                "urls": [
                    {"url": "https://github.com/oracle/graalpython.git", "kind": "git"},
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
            # {
            #     "name": "tools",
            #     "subdir": True,
            #     "dynamic": True,
            #     "urls": [
            #         {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
            #     ],
            # },
            # {
            #     "name": "compiler",
            #     "subdir": True,
            #     "dynamic": True,
            #     "urls": [
            #         {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
            #     ],
            # },
            # {
            #     "name": "substratevm",
            #     "subdir": True,
            #     "dynamic": True,
            #     "urls": [
            #         {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
            #     ],
            #     "os_arch": {
            #         "linux": {
            #             "sparcv9": {
            #                 "ignore": True
            #             },
            #             "<others>": {
            #                 "ignore": False
            #             }
            #         },
            #         "darwin": {
            #             "<others>": {
            #                 "ignore": False
            #             }
            #         },
            #         "<others>": {
            #             "<others>": {
            #                 "ignore": True
            #             }
            #         }
            #     }
            # },
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
            "license": "GPLv2-CPE",
        },
        "org.graalvm.component.installer" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "javaCompliance" : "1.8",
            "checkstyle": "com.oracle.graalvm.locator",
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
            "checkstyle": "com.oracle.graalvm.locator",
            "license" : "GPLv2-CPE",
        },
        "polyglot.launcher": {
            "class": "GraalVmPolyglotLauncher",
            "launcherConfig": {
                "build_args": [
                    "-H:-ParseRuntimeOptions",
                    "-H:Features=org.graalvm.launcher.PolyglotLauncherFeature",
                    "--language:all"
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
                "components/polyglot/.registry" : "string:",
            },
        },
    },
}
