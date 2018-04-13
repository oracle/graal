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
                "version": "cbf9ff3a1223bd2eedef552d826a937b7bbfcf39",
                "urls" : [
                    {"url" : "https://github.com/oracle/js.git", "kind" : "git"},
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
            {
                "name": "graal-js",
                "subdir": True,
                "dynamic": True,
                "version": "cbf9ff3a1223bd2eedef552d826a937b7bbfcf39",
                "urls": [
                    {"url": "https://github.com/oracle/js.git", "kind" : "git"},
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
            {
                "name": "truffleruby",
                "version": "70b414f021238617224e24447c298f4279c57df5",
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
                "version": "3a193290f369f2f9cb5ce0d1b0221613bf853f98",
                "dynamic": True,
                "urls": [
                    {"url": "https://github.com/oracle/fastr.git", "kind": "git"},
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
            {
                "name": "sulong",
                "version": "e281f4fb1b97d2c3781d5708a42d9146989398af",
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
                "version": "7cc9f37adb5381aabd2c1f0734c6ba2c6cd1f4ed",
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
        "VM_GRAALVM_SUPPORT" : {
            "native" : True,
            "description" : "VM support distribution for the GraalVM",
            "layout" : {
                "./" : [
                    "file:GraalCE_license_3rd_party_license.txt",
                ],
            },
        },
    },
}
