suite = {
    "name": "vm",
    "version" : "23.0.0",
    "mxversion" : "6.11.4",
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
                "version": "18f7699906ca47c0d13bca91ffd784ce304fdadc",
                "urls" : [
                    {"url" : "https://github.com/graalvm/graaljs.git", "kind" : "git"},
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
            {
                "name": "graal-js",
                "subdir": True,
                "dynamic": True,
                "version": "18f7699906ca47c0d13bca91ffd784ce304fdadc",
                "urls": [
                    {"url": "https://github.com/graalvm/graaljs.git", "kind" : "git"},
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
            {
                "name": "truffleruby",
                "version": "57e53f8adf99402776f62633873a366e38ea3d61",
                "dynamic": True,
                "urls": [
                    {"url": "https://github.com/oracle/truffleruby.git", "kind": "git"},
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
            {
                "name": "fastr",
                "version": "e1badd6dd8d338f92a87fa8a675f0379e63d93a5",
                "dynamic": True,
                "urls": [
                    {"url": "https://github.com/oracle/fastr.git", "kind": "git"},
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
            {
                "name": "graalpython",
                "version": "4736a48c2d802740df85863d3a276598063aaaf3",
                "dynamic": True,
                "urls": [
                    {"url": "https://github.com/graalvm/graalpython.git", "kind": "git"},
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
            {
                "name": "polybenchmarks",
                "version": "a54325418d93db80147811fdf3fbf2395a54c80f",
                "dynamic": True,
                "urls": [
                    {"url": "https://github.com/graalvm/polybenchmarks.git", "kind": "git"},
                ]
            },
        ]
    },

    "projects": {
        "org.graalvm.component.installer" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "javaCompliance" : "11+",
            "license" : "GPLv2-CPE",
            "checkstyleVersion" : "8.36.1",
            "dependencies": [
                "sdk:LAUNCHER_COMMON",
                "truffle:TruffleJSON",
            ],
            "requires" : ["java.logging"],
        },
        "org.graalvm.component.installer.test" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies": [
                "mx:JUNIT",
                "org.graalvm.component.installer"
            ],
            "javaCompliance" : "11+",
            "checkstyle": "org.graalvm.component.installer",
            "license" : "GPLv2-CPE",
            "requires" : ["java.logging"],
        },
        "org.graalvm.polybench" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "javaCompliance" : "11+",
            "license" : "GPLv2-CPE",
            "checkstyle": "org.graalvm.component.installer",
            "dependencies": [
                "sdk:LAUNCHER_COMMON",
                "truffle:VISUALVM-LIB-JFLUID-HEAP",
            ],
            "requires": [
                "java.logging",
                "jdk.management",
            ],
        },
        "org.graalvm.polybench.micro" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "javaCompliance" : "11+",
            "license" : "GPLv2-CPE",
            "checkstyle": "org.graalvm.component.installer",
            "dependencies": [
                "truffle:TRUFFLE_API",
            ],
            "annotationProcessors": [
                "truffle:TRUFFLE_DSL_PROCESSOR",
            ],
        },
        "org.graalvm.polybench.instruments" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "javaCompliance" : "11+",
            "license" : "GPLv2-CPE",
            "checkstyle": "org.graalvm.component.installer",
            "dependencies": [
                "truffle:TRUFFLE_API",
            ],
            "requires": [
                "jdk.management",
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
            "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/polybench/warmup-benchmarks-0.4.tar.gz"],
            "digest" : "sha512:3ccf2fde4765561681ee530ee7ff6af823e89f447261e87e155f47e6ef29820ffd0f9ddaa39333893834df9c15463077cf1995b659644a79ab1595fd14ff2091"
        },
        "GRAALPYTHON_PYFLATE_BENCHMARK_RESOURCE" : {
            # just any reasonably sized .tar.gz or .tar.bz2 for running the benchmark
            "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm/visualvm-944-linux-amd64.tar.gz"],
            "digest" : "sha512:72982ca01cce9dfa876687ec7b9627b81e241e6cddc8dedb976a5d06d058a067f83f5c063dc07d7ed19730ffb54af8343eae8ca0cc156353f7b18530eef73c50"
        },
        "ORG_NETBEANS_API_ANNOTATIONS_COMMON" : {
           "digest" : "sha512:37e9cee0756a3f84e3e6352ed3d358169855d16cdda2300eb5041219c867abf886db2de41fe79c37f39123eaac310041f9a2e65aa1b9a7e8384528cfbd40de5b",
            "maven" : {
                "groupId" : "org.netbeans.api",
                "artifactId" : "org-netbeans-api-annotations-common",
                "version" : "RELEASE123",
            },
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
                 "truffle:VISUALVM-LIB-JFLUID-HEAP",
            ],
            "maven" : False,
        },
        "POLYBENCH_INSTRUMENTS": {
            "subDir": "src",
            "dependencies": [
                "org.graalvm.polybench.instruments",
            ],
            "distDependencies": [
                "truffle:TRUFFLE_API",
            ],
            "maven" : False,
        },
        "POLYBENCH_INSTRUMENTS_SUPPORT" : {
            "native" : True,
            "description" : "Truffle Profiler support distribution for the GraalVM",
            "layout" : {
                "native-image.properties" : "file:mx.vm/polybench-instruments.properties",
            },
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
                    "file:benchmarks/warmup/*.py",
                    "dependency:GRAALPYTHON_PYFLATE_BENCHMARK_RESOURCE",
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
