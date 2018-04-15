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
                "version": "1851eb4cc86802ec0a398ad48318ac7dd5592cfe",
                "urls" : [
                    {"url" : "https://github.com/oracle/js.git", "kind" : "git"},
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
            {
                "name": "graal-js",
                "subdir": True,
                "dynamic": True,
                "version": "1851eb4cc86802ec0a398ad48318ac7dd5592cfe",
                "urls": [
                    {"url": "https://github.com/oracle/js.git", "kind" : "git"},
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
            {
                "name": "truffleruby",
                "version": "9e0fef23848148471a5bad2490e96650b2593e26",
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
                "version": "617b00b59d76a243a5c33f7fd5c7075bd470f311",
                "dynamic": True,
                "urls": [
                    {"url": "https://github.com/oracle/fastr.git", "kind": "git"},
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
            {
                "name": "sulong",
                "version": "b916e30262b94ce7be80d377f402717e30dd98b7",
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
                "version": "ac854dda4e9f9d93c54304978ff01d2a559f58ec",
                "dynamic": True,
                "urls": [
                    {"url": "https://github.com/oracle/graalpython.git", "kind": "git"},
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
