suite = {
    "mxversion": "5.138.0",
    "name": "substratevm",

    "defaultLicense" : "GPLv2-CPE",

    "versionConflictResolution": "latest",

    "imports": {
        "suites": [
            {
                "name": "compiler",
                "subdir": True,
                "urls" : [
                    {"url" : "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind" : "binary"},
                ]
            },
            {
                "name": "regex",
                "subdir": True,
                "urls" : [
                    {"url" : "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind" : "binary"},
                ]
            },
        ]
    },

    "projects": {
        "com.oracle.svm.core": {
            "subDir": "src",
            "sourceDirs": [
                "src",
                "headers",
            ],
            "dependencies": [
                "sdk:GRAAL_SDK",
                "compiler:GRAAL_RUNTIME",
            ],
            "javaCompliance": "1.8",
            "checkstyleVersion" : "8.8",
            "annotationProcessors": [
                "compiler:GRAAL_NODEINFO_PROCESSOR",
                "compiler:GRAAL_REPLACEMENTS_VERIFIER",
                "compiler:GRAAL_OPTIONS_PROCESSOR",
            ],
            "workingSets": "SVM",
        },

        "com.oracle.svm.core.genscavenge": {
            "subDir": "src",
            "sourceDirs": [
                "src",
            ],
            "dependencies": [
                "com.oracle.svm.core.graal",
            ],
            "checkstyle": "com.oracle.svm.core",
            "javaCompliance": "1.8",
            "annotationProcessors": [
                "compiler:GRAAL_NODEINFO_PROCESSOR",
                "compiler:GRAAL_REPLACEMENTS_VERIFIER",
                "compiler:GRAAL_OPTIONS_PROCESSOR",
            ],
            "workingSets": "SVM",
        },

        "com.oracle.svm.core.graal": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.core.posix",
                "compiler:GRAAL",
            ],
            "checkstyle": "com.oracle.svm.core",
            "javaCompliance": "1.8",
            "annotationProcessors": [
                "compiler:GRAAL_NODEINFO_PROCESSOR",
                "compiler:GRAAL_REPLACEMENTS_VERIFIER",
                "compiler:GRAAL_OPTIONS_PROCESSOR",
            ],
            "workingSets": "SVM",
        },

        "com.oracle.svm.core.posix": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": ["com.oracle.svm.core"],
            "checkstyle": "com.oracle.svm.core",
            "javaCompliance": "1.8",
            "annotationProcessors": [
                "compiler:GRAAL_NODEINFO_PROCESSOR",
                "compiler:GRAAL_REPLACEMENTS_VERIFIER",
                "compiler:GRAAL_OPTIONS_PROCESSOR",
            ],
            "workingSets": "SVM",
            "findbugs": "false",
        },

        "com.oracle.graal.pointsto": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "compiler:GRAAL_RUNTIME",
            ],
            "checkstyle": "com.oracle.graal.pointsto",
            "javaCompliance": "1.8",
            "checkstyleVersion" : "8.8",
            "annotationProcessors": [
                "compiler:GRAAL_NODEINFO_PROCESSOR",
                "compiler:GRAAL_REPLACEMENTS_VERIFIER",
                "compiler:GRAAL_OPTIONS_PROCESSOR",
            ],
            "workingSets": "SVM",
        },

        "com.oracle.svm.hosted": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.objectfile",
                "com.oracle.svm.core.graal",
                "com.oracle.graal.pointsto",
            ],
            "javaCompliance": "1.8",
            "checkstyleVersion" : "8.8",
            "annotationProcessors": [
                "compiler:GRAAL_NODEINFO_PROCESSOR",
                "compiler:GRAAL_REPLACEMENTS_VERIFIER",
                "compiler:GRAAL_OPTIONS_PROCESSOR",
            ],
            "workingSets": "SVM",
        },

        "com.oracle.svm.native": {
            "native": True,
            "subDir": "src",
            "output": "clibraries",
            "os_arch": {
                "linux": {
                    "amd64": {
                        "results": [
                            "linux-amd64/libstrictmath.a",
                            "linux-amd64/liblibchelper.a",
                            "linux-amd64/include/cpufeatures.h",
                            "linux-amd64/include/jni.h",
                            "linux-amd64/include/jni_md.h",
                        ],
                    },
                    "<others>": {
                        "results": [],
                    },
                },
                "darwin": {
                    "amd64": {
                        "results": [
                            "darwin-amd64/libstrictmath.a",
                            "darwin-amd64/liblibchelper.a",
                            "darwin-amd64/include/cpufeatures.h",
                            "darwin-amd64/include/jni.h",
                            "darwin-amd64/include/jni_md.h",
                        ],
                    },
                },
                "solaris": {
                    "<others>": {
                        "results": [],
                    },
                },
            }
        },

        "com.oracle.svm.jni": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.hosted",
            ],
            "checkstyle": "com.oracle.svm.core",
            "workingSets": "SVM",
            "annotationProcessors": [
                "compiler:GRAAL_NODEINFO_PROCESSOR",
                "compiler:GRAAL_REPLACEMENTS_VERIFIER",
                "compiler:GRAAL_OPTIONS_PROCESSOR",
            ],
            "javaCompliance": "1.8",
            "findbugs": "false",
        },

        "com.oracle.svm.driver": {
            "subDir": "src",
            "sourceDirs": [
                "src",
                "resources"
            ],
            "dependencies": [
                "com.oracle.svm.graal",
                "com.oracle.svm.reflect",
                "com.oracle.svm.jni",
            ],
            "checkstyle": "com.oracle.svm.driver",
            "checkstyleVersion" : "8.8",
            "workingSets": "SVM",
            "annotationProcessors": [
                "compiler:GRAAL_NODEINFO_PROCESSOR",
                "compiler:GRAAL_REPLACEMENTS_VERIFIER",
                "compiler:GRAAL_OPTIONS_PROCESSOR",
            ],
            "javaCompliance": "1.8",
            "findbugs": "false",
        },

        "com.oracle.svm.junit": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.reflect",
                "mx:JUNIT_TOOL",
            ],
            "checkstyle": "com.oracle.svm.core",
            "workingSets": "SVM",
            "annotationProcessors": [
                "compiler:GRAAL_OPTIONS_PROCESSOR",
            ],
            "javaCompliance": "1.8",
            "findbugs": "false",
        },

        "com.oracle.svm.reflect": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.hosted",
            ],
            "checkstyle": "com.oracle.svm.core",
            "workingSets": "SVM",
            "annotationProcessors": [
                "compiler:GRAAL_OPTIONS_PROCESSOR",
            ],
            "javaCompliance": "1.8",
            "findbugs": "false",
        },

    "com.oracle.svm.tutorial" : {
      "subDir": "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.svm.core"],
      "checkstyle" : "com.oracle.svm.truffle",
      "javaCompliance" : "1.8",
      "annotationProcessors" : [
        "compiler:GRAAL_NODEINFO_PROCESSOR",
        "compiler:GRAAL_REPLACEMENTS_VERIFIER",
        "compiler:GRAAL_OPTIONS_PROCESSOR",
      ],
      "workingSets" : "SVM",
      "findbugs" : "false",
    },

    "com.oracle.objectfile" : {
      "subDir": "src",
      "sourceDirs" : ["src"],
      "dependencies" : [],
      "checkstyle" : "com.oracle.svm.hosted",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["compiler:GRAAL_OPTIONS_PROCESSOR"],
      "workingSets" : "SVM",
      "findbugs" : "false",
    },

        "com.oracle.svm.graal": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.hosted",
                "truffle:TRUFFLE_API",
                "truffle:TRUFFLE_DEBUG",
            ],
            "checkstyle": "com.oracle.svm.hosted",
            "javaCompliance": "1.8",
            "annotationProcessors": [
                "compiler:GRAAL_NODEINFO_PROCESSOR",
                "compiler:GRAAL_REPLACEMENTS_VERIFIER",
                "compiler:GRAAL_OPTIONS_PROCESSOR",
            ],
        },

        "com.oracle.svm.thirdparty": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.core",
            ],
            "checkstyle": "com.oracle.svm.truffle",
            "javaCompliance": "1.8",
            "annotationProcessors": [
                "compiler:GRAAL_NODEINFO_PROCESSOR",
                "compiler:GRAAL_REPLACEMENTS_VERIFIER",
                "compiler:GRAAL_OPTIONS_PROCESSOR",
            ],
            "workingSets": "SVM",
        },

        "com.oracle.svm.truffle": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.graal",
            ],
            "checkstyle": "com.oracle.svm.truffle",
            "javaCompliance": "1.8",
            "checkstyleVersion" : "8.8",
            "annotationProcessors": [
                "compiler:GRAAL_NODEINFO_PROCESSOR",
                "compiler:GRAAL_REPLACEMENTS_VERIFIER",
                "compiler:GRAAL_OPTIONS_PROCESSOR",
            ],
            "workingSets": "SVM",
        },

        "com.oracle.svm.truffle.nfi": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.truffle",
                "truffle:TRUFFLE_NFI",
            ],
            "checkstyle": "com.oracle.svm.truffle",
            "javaCompliance": "1.8",
            "annotationProcessors": [
                "truffle:TRUFFLE_DSL_PROCESSOR",
            ],
            "workingSets": "SVM",
        },

        "com.oracle.svm.libffi": {
            "subDir": "src",
            "native": True,
            "vpath": True,
            "results": [
                "<os>-<arch>/libffi.a",
                "<os>-<arch>/include/ffi.h",
                "<os>-<arch>/include/ffitarget.h",
                "<os>-<arch>/include/trufflenfi.h",
                "<os>-<arch>/include/svm_libffi.h",
            ],
            "buildEnv": {
                "LIBFFI_SRC": "<path:truffle:LIBFFI>",
                "TRUFFLE_NFI": "<path:truffle:TRUFFLE_NFI_NATIVE>",
                "ARCH": "<arch>",
                "OS": "<os>"
            },
            "buildDependencies": [
                "truffle:TRUFFLE_NFI_NATIVE",
            ],
        },

        "com.oracle.svm.jline": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.core",
                "truffle:JLINE",
            ],
            "checkstyle": "com.oracle.svm.truffle",
            "javaCompliance": "1.8",
            "annotationProcessors": [
                "compiler:GRAAL_NODEINFO_PROCESSOR",
                "compiler:GRAAL_REPLACEMENTS_VERIFIER",
                "compiler:GRAAL_OPTIONS_PROCESSOR",
            ],
            "workingSets": "SVM",
            "findbugs": "false",
        },

        "com.oracle.svm.polyglot": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "generatedDependencies": [
                "com.oracle.svm.graal",
            ],
            "checkstyle": "com.oracle.svm.truffle",
            "javaCompliance": "1.8",
            "annotationProcessors": [
                "compiler:GRAAL_NODEINFO_PROCESSOR",
                "compiler:GRAAL_REPLACEMENTS_VERIFIER",
                "compiler:GRAAL_OPTIONS_PROCESSOR",
            ],
            "workingSets": "SVM",
            "findbugs": "false",
        },

        "org.graalvm.polyglot.nativeapi" : {
            "subDir": "src",
            "sourceDirs" : [
                "src",
                "resources",
            ],
            "dependencies" : [
                "sdk:GRAAL_SDK",
                "com.oracle.svm.hosted",
            ],
            "buildDependencies" : [
                "org.graalvm.polyglot.nativeapi.native",
            ],
            "checkstyle": "org.graalvm.polyglot.nativeapi",
            "checkstyleVersion" : "8.8",
            "javaCompliance" : "1.8",
            "annotationProcessors" : [
                "compiler:GRAAL_NODEINFO_PROCESSOR",
                "compiler:GRAAL_REPLACEMENTS_VERIFIER",
                "compiler:GRAAL_OPTIONS_PROCESSOR",
            ],
            "workingSets" : "SVM",
            "findbugs": "false",
        },

        "org.graalvm.polyglot.nativeapi.native" : {
            "subDir" : "src",
            "native" : True,
            "output" : "mxbuild/org.graalvm.polyglot.nativeapi.native",
            "os_arch" : {
                "linux": {
                    "amd64" : {
                        "results" : ["linux-amd64/polyglot-nativeapi.o"],
                    },
                },
                "darwin": {
                    "amd64" : {
                        "results" : ["darwin-amd64/polyglot-nativeapi.o"],
                    },
                },
            }
        },

        "bootstrap.native-image" : {
            "class" : "BootstrapNativeImage",
            "buildDependencies": [
                "SVM_DRIVER",
                'tools:CHROMEINSPECTOR',
                'tools:TRUFFLE_PROFILER',
            ],
            "svm" : [
                "SVM"
            ],
            "svmSupport" : [
                "LIBRARY_SUPPORT"
            ],
            "graal" : [
                "compiler:GRAAL"
            ],
        },
    },

    "distributions": {
        #
        # External Distributions
        #
        "SVM": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.svm.hosted",
                "com.oracle.svm.truffle.nfi",
                "com.oracle.svm.junit",
                "com.oracle.svm.core",
                "com.oracle.svm.core.posix",
                "com.oracle.svm.core.genscavenge",
            ],
            "overlaps" : [
                "SVM_CORE"
            ],
            "distDependencies": [
                "SVM_HOSTED_NATIVE",
                "sdk:GRAAL_SDK",
                "OBJECTFILE",
                "POINTSTO",
                "mx:JUNIT_TOOL",
                "truffle:TRUFFLE_DEBUG",
                "truffle:TRUFFLE_NFI",
                "compiler:GRAAL",
            ]
        },

        "SVM_CORE": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.svm.core",
                "com.oracle.svm.core.posix",
                "com.oracle.svm.core.graal",
                "com.oracle.svm.core.genscavenge",
            ],
            "distDependencies": [
                "sdk:GRAAL_SDK",
                "compiler:GRAAL_RUNTIME",
                "compiler:GRAAL",
            ],
            "exclude": [
            ]
        },

        "LIBRARY_SUPPORT": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.svm.jni",
                "com.oracle.svm.jline",
                "com.oracle.svm.polyglot",
                "com.oracle.svm.thirdparty",
            ],
            "distDependencies": [
                "sdk:GRAAL_SDK",
                "SVM",
                "OBJECTFILE",
                "compiler:GRAAL",
            ],
            "exclude": [
                "truffle:JLINE",
            ]
        },

        "OBJECTFILE": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.objectfile"
            ]
        },

        #
        # Native Projects
        #
        "SVM_HOSTED_NATIVE": {
            "dependencies": [
                "com.oracle.svm.native",
                "com.oracle.svm.libffi"
            ],
            "native": True,
            "relpath": True,
            "output": "clibraries",
        },

    #
    # Internal Distributions
    #
    "SVM_DRIVER": {
      "subDir": "src",
      "dependencies": [
        "com.oracle.svm.driver",
      ],
      "distDependencies": [
        "LIBRARY_SUPPORT",
      ],
    },

   "POINTSTO": {
      "subDir": "src",
      "dependencies": [
        "com.oracle.graal.pointsto",
      ],
      "distDependencies": [
        "compiler:GRAAL_RUNTIME",
      ],
      "exclude": [
      ]
    },

    "NATIVE_IMAGE": {
      "native": True,
      "dependencies": [
        "bootstrap.native-image",
      ],
      "output": "svmbuild/native-image-root",
    },

    "POLYGLOT_NATIVE_API" : {
      "dependencies": [
        "org.graalvm.polyglot.nativeapi",
      ],
      "distDependencies": [
        "sdk:GRAAL_SDK",
        "SVM",
      ],
    },
  },
}
