suite = {
    "name": "vm",
    "version" : "21.3.0",
    "mxversion" : "5.309.2",
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
                "version": "66faeb56dd170e65e018de522069cfd826eb79f4",
                "urls" : [
                    {"url" : "https://github.com/graalvm/graaljs.git", "kind" : "git"},
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
            {
                "name": "graal-js",
                "subdir": True,
                "dynamic": True,
                "version": "66faeb56dd170e65e018de522069cfd826eb79f4",
                "urls": [
                    {"url": "https://github.com/graalvm/graaljs.git", "kind" : "git"},
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
            {
                "name": "truffleruby",
                "version": "ac4aa22b7901ca7f3f782cf07cdb09016704d6a2",
                "dynamic": True,
                "urls": [
                    {"url": "https://github.com/oracle/truffleruby.git", "kind": "git"},
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
            {
                "name": "fastr",
                "version": "22466837f3e7f5af43311088fd43a692b9aa6002",
                "dynamic": True,
                "urls": [
                    {"url": "https://github.com/oracle/fastr.git", "kind": "git"},
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
            {
                "name": "graalpython",
                "version": "f677494026f6bb850c42530df537f8ce7cd4ad82",
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
            "checkstyleVersion" : "8.36.1",
            "dependencies": [
                "sdk:LAUNCHER_COMMON",
                "truffle:TruffleJSON",
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
        "org.graalvm.polybench.micro" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "javaCompliance" : "1.8+",
            "license" : "GPLv2-CPE",
            "checkstyle": "org.graalvm.component.installer",
            "dependencies": [
                "truffle:TRUFFLE_API",
            ],
            "annotationProcessors": [
                "truffle:TRUFFLE_DSL_PROCESSOR",
            ],
        },
        "nfi-native" : {
            "subDir" : "benchmarks",
            "native" : "shared_lib",
            "deliverable" : "microbench",
            "buildDependencies" : [
                "truffle:TRUFFLE_NFI_GRAALVM_SUPPORT",
            ],
            "cflags" : [
                "-I<path:truffle:TRUFFLE_NFI_GRAALVM_SUPPORT>/include",
            ],
            "testProject" : True,
            "defaultBuild": False,
        },
    },

    "libraries" : {
        # Note: small warmup benchmarks can be placed directly under `graal/vm/benchmarks/warmup`
        # and uncomment the corresponding line for the `layout` of `POLYBENCH_BENCHMARKS` in current suite.
        "WARMUP_BENCHMARKS" : {
            "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/polybench/warmup-benchmarks-0.1.tar.gz"],
            "sha1" : "4e4a81c096ecc4ba7c4de9ab8ca5180e93c85e60"
        }
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
            "exclude" : [
                "truffle:TruffleJSON"
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
        "PMH": {
            "subDir": "src",
            "dependencies": [
                "org.graalvm.polybench.micro",
            ],
            "distDependencies": [
                "truffle:TRUFFLE_API",
            ],
            "maven" : False,
        },
        "PMH_SUPPORT": {
            "native": True,
            "layout": {
                "native-image.properties": "file:mx.vm/language-pmh.properties",
            },
            "maven": False,
        },
        "POLYBENCH_BENCHMARKS": {
            "native": True,
            "description": "Distribution for polybench benchmarks",
            # llvm bitcode is platform dependent
            "platformDependent": True,
            "layout": {
                # The layout may be modified via mx_vm.mx_register_dynamic_suite_constituents() to include dynamic projects.
                "./interpreter/": [
                    "file:benchmarks/interpreter/*.js",
                    "file:benchmarks/interpreter/*.rb",
                    "file:benchmarks/interpreter/*.py",
                ],
                "./interpreter/dependencies/": [
                    "file:benchmarks/interpreter/dependencies/*",
                ],
                "./compiler/": [
                    "file:benchmarks/compiler/*",
                ],
                "./warmup/": [
                    # "file:benchmarks/warmup/*.js",
                    # "file:benchmarks/warmup/*.rb",
                    # "file:benchmarks/warmup/*.py",
                    "extracted-dependency:WARMUP_BENCHMARKS/*"
                ],
                "./nfi/": [
                    "file:benchmarks/nfi/*.pmh",
                ],
                "./nfi-native/": [
                    "dependency:nfi-native",
                ],
            },
            "defaultBuild": False,
        },
    },
}
