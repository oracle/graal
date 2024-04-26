suite = {
    "name": "vm",
    "version" : "24.1.0",
    "mxversion": "6.41.0",
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
            },
            {
                "name": "truffle",
                "subdir": True,
            },
            # Dynamic imports for components:
            {
                "name": "graal-nodejs",
                "subdir": True,
                "dynamic": True,
                "version": "a54e3ea08c9d8783c3941430c8a8834d1481d6cf",
                "urls" : [
                    {"url" : "https://github.com/graalvm/graaljs.git", "kind" : "git"},
                ]
            },
            {
                "name": "graal-js",
                "subdir": True,
                "dynamic": True,
                "version": "a54e3ea08c9d8783c3941430c8a8834d1481d6cf",
                "urls": [
                    {"url": "https://github.com/graalvm/graaljs.git", "kind" : "git"},
                ]
            },
            {
                "name": "truffleruby",
                "version": "51b497f921dacf71441f568ed653a6c5799d2ff0",
                "dynamic": True,
                "urls": [
                    {"url": "https://github.com/oracle/truffleruby.git", "kind": "git"},
                ]
            },
            {
                "name": "fastr",
                "version": "20424ee856de472c2b0f38a6256700b62b6e7c20",
                "dynamic": True,
                "urls": [
                    {"url": "https://github.com/oracle/fastr.git", "kind": "git"},
                ]
            },
            {
                "name": "graalpython",
                "version": "f131a6f8f5144cd6b80316842c4bdbb4e48e4509",
                "dynamic": True,
                "urls": [
                    {"url": "https://github.com/graalvm/graalpython.git", "kind": "git"},
                ]
            },
            {
                "name": "polybenchmarks",
                "version": "9c11e267b9925f44e42b1187fee466b47c2ffcf3",
                "dynamic": True,
                "urls": [
                    {"url": "https://github.com/graalvm/polybenchmarks.git", "kind": "git"},
                ]
            },
        ]
    },

    "projects": {
        "org.graalvm.polybench" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "javaCompliance" : "17+",
            "license" : "GPLv2-CPE",
            "checkstyleVersion" : "10.7.0",
            "dependencies": [
                "sdk:LAUNCHER_COMMON",
                "sdk:POLYGLOT",
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
            "javaCompliance" : "17+",
            "license" : "GPLv2-CPE",
            "checkstyle": "org.graalvm.polybench",
            "dependencies": [
                "truffle:TRUFFLE_API",
            ],
            "annotationProcessors": [
                "truffle:TRUFFLE_DSL_PROCESSOR",
            ],
            "spotbugsIgnoresGenerated" : True,
        },
        "org.graalvm.polybench.instruments" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "javaCompliance" : "17+",
            "license" : "GPLv2-CPE",
            "checkstyle": "org.graalvm.polybench",
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
    },

    "distributions": {
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
                "sdk:POLYGLOT",
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
