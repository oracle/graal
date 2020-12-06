suite = {
    "name": "vm",
    "version" : "21.1.0",
    "mxversion" : "5.256.0",
    "release" : False,
    "groupId" : "org.graalvm",

    "url" : "http://www.graalvm.org/",
    "developer" : {
        "name" : "GraalVM Development",
        "email" : "graalvm-dev@oss.oracle.com",
        "organization" : "Oracle Corporation",
        "organizationUrl" : "http://www.graalvm.org/",
    },
    "scm" : {
      "url" : "https://github.com/oracle/graal",
      "read" : "https://github.com/oracle/graal.git",
    "  write" : "git@github.com:oracle/graal.git",
    },
    "defaultLicense" : "GPLv2-CPE",
    "imports": {
        "suites": [
            {
                "name": "sdk",
                "subdir": True,
                "urls": [
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
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
                "version": "714384094e1f949fd912a99745f2b6668bb8b399",
                "urls" : [
                    {"url" : "https://github.com/graalvm/graaljs.git", "kind" : "git"},
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
            {
                "name": "graal-js",
                "subdir": True,
                "dynamic": True,
                "version": "714384094e1f949fd912a99745f2b6668bb8b399",
                "urls": [
                    {"url": "https://github.com/graalvm/graaljs.git", "kind" : "git"},
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
            {
                "name": "truffleruby",
                "version": "1c8f978a92a36c800a1d96aa2df8f37d80fae52d",
                "dynamic": True,
                "urls": [
                    {"url": "https://github.com/oracle/truffleruby.git", "kind": "git"},
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
            {
                "name": "fastr",
                "version": "1897802c9e8643fb0e39d52217db188116a89408",
                "dynamic": True,
                "urls": [
                    {"url": "https://github.com/oracle/fastr.git", "kind": "git"},
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
            {
                "name": "graalpython",
                "version": "b6e12bf38c0002afb76129d22c21b8e314774d1a",
                "dynamic": True,
                "urls": [
                    {"url": "https://github.com/graalvm/graalpython.git", "kind": "git"},
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
        ]
    },

    "projects": {
        "org.graalvm.component.installer" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "javaCompliance" : "1.8+",
            "license" : "GPLv2-CPE",
            "checkstyleVersion" : "8.8",
            "dependencies": [
                "sdk:LAUNCHER_COMMON",
            ],
        },
        "org.graalvm.component.installer.test" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies": [
                "mx:JUNIT",
                "org.graalvm.component.installer"
            ],
            "javaCompliance" : "1.8+",
            "checkstyle": "org.graalvm.component.installer",
            "license" : "GPLv2-CPE",
        },
        "org.graalvm.polybench" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "javaCompliance" : "1.8+",
            "license" : "GPLv2-CPE",
            "checkstyle": "org.graalvm.component.installer",
            "dependencies": [
                "sdk:LAUNCHER_COMMON",
            ],
        },
        "org.graalvm.polybench.jdk11" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "org.graalvm.polybench",
            ],
            "requires" : [
                "java.logging",
                "jdk.jfr",
            ],
            "javaCompliance" : "11+",
            "license" : "GPLv2-CPE",
            "checkstyle": "org.graalvm.component.installer",
            "checkPackagePrefix" : "false",
      		"overlayTarget" : "org.graalvm.polybench",
      		"multiReleaseJarVersion" : "11",
        },
    },

    "distributions": {
        "INSTALLER": {
            "subDir": "src",
            "mainClass": "org.graalvm.component.installer.ComponentInstaller",
            "dependencies": [
                "org.graalvm.component.installer",
            ],
            "distDependencies": [
                "sdk:LAUNCHER_COMMON",
            ],
            "maven" : False,
        },
        "INSTALLER_TESTS": {
            "subDir": "src",
            "dependencies": ["org.graalvm.component.installer.test"],
            "exclude": [
                "mx:HAMCREST",
                "mx:JUNIT",
            ],
            "distDependencies": [
                "INSTALLER",
            ],
            "maven": False,
        },
        "INSTALLER_GRAALVM_SUPPORT": {
            "native": True,
            "platformDependent": True,
            "description": "GraalVM Installer support distribution for the GraalVM",
            "layout": {
                "components/polyglot/.registry" : "string:",
            },
            "maven": False,
        },
        "VM_GRAALVM_SUPPORT": {
            "native": True,
            "description": "VM support distribution for the GraalVM",
            "layout": {
                "./": ["file:GRAALVM-README.md"],
                "LICENSE.txt": "file:LICENSE_GRAALVM_CE",
                "THIRD_PARTY_LICENSE.txt": "file:THIRD_PARTY_LICENSE_CE.txt",
            },
            "maven": False,
        },
        "POLYBENCH": {
            "subDir": "src",
            "mainClass": "org.graalvm.polybench.PolyBenchLauncher",
            "dependencies": [
                "org.graalvm.polybench",
            ],
            "distDependencies": [
                "sdk:LAUNCHER_COMMON",
            ],
            "maven" : False,
        },
    },
}
