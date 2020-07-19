suite = {
    "mxversion": "5.223.0",
    "name": "tools",
    "defaultLicense" : "GPLv2-CPE",

    "groupId" : "org.graalvm.tools",
    "version" : "20.3.0",
    "release" : False,
    "url" : "http://openjdk.java.net/projects/graal",
    "developer" : {
        "name" : "GraalVM Development",
        "email" : "graalvm-dev@oss.oracle.com",
        "organization" : "Oracle Corporation",
        "organizationUrl" : "http://www.graalvm.org/",
    },
    "scm" : {
        "url" : "https://github.com/oracle/graal",
        "read" : "https://github.com/oracle/graal.git",
        "write" : "git@github.com:oracle/graal.git",
    },

    "imports": {
        "suites": [
            {
              "name" : "truffle",
              "subdir" : True,
              "urls" : [
                {"url" : "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind" : "binary"},
              ]
            },
        ]
    },

    "projects" : {
        "com.oracle.truffle.tools.chromeinspector" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "truffle:TRUFFLE_API",
                "TRUFFLE_PROFILER",
                "NanoHTTPD",
                "NanoHTTPD-WebSocket",
                "TruffleJSON",
                "SLF4J_SIMPLE",
                "Java-WebSocket",
            ],
            "exports" : [
              "<package-info>", # exports all packages containing package-info.java
              "com.oracle.truffle.tools.chromeinspector.instrument to org.graalvm.truffle"
            ],
            "javaCompliance" : "8+",
            "checkstyleVersion" : "8.8",
            "checkstyle" : "com.oracle.truffle.tools.chromeinspector",
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "workingSets" : "Tools",
        },
        "com.oracle.truffle.tools.chromeinspector.test" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "com.oracle.truffle.tools.chromeinspector",
                "truffle:TRUFFLE_TEST",
                "truffle:TRUFFLE_SL",
                "mx:JUNIT",
            ],
            "javaCompliance" : "8+",
            "checkstyle" : "com.oracle.truffle.tools.chromeinspector",
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "workingSets" : "Tools",
        },
        "org.graalvm.tools.insight" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "truffle:TRUFFLE_API",
            ],
            "exports" : [
              "<package-info>", # exports all packages containing package-info.java
            ],
            "javaCompliance" : "8+",
            "checkstyle" : "com.oracle.truffle.tools.chromeinspector",
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "workingSets" : "Tools",
        },
        "com.oracle.truffle.tools.agentscript" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "org.graalvm.tools.insight",
            ],
            "exports" : [
              "<package-info>", # exports all packages containing package-info.java
            ],
            "javaCompliance" : "8+",
            "checkstyle" : "com.oracle.truffle.tools.chromeinspector",
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "workingSets" : "Tools",
        },
        "com.oracle.truffle.tools.agentscript.test" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "com.oracle.truffle.tools.agentscript",
                "truffle:TRUFFLE_TEST",
                "mx:JUNIT"
            ],
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "checkstyle" : "com.oracle.truffle.tools.chromeinspector",
            "javaCompliance" : "8+",
            "workingSets" : "Tools",
        },
        "com.oracle.truffle.tools.profiler" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "truffle:TRUFFLE_API",
                "TruffleJSON",
                ],
            "exports" : [
              "<package-info>", # exports all packages containing package-info.java
              "com.oracle.truffle.tools.profiler.impl to org.graalvm.truffle",
            ],
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "checkstyle" : "com.oracle.truffle.tools.chromeinspector",
            "javaCompliance" : "8+",
            "workingSets" : "Tools",
        },
        "com.oracle.truffle.tools.profiler.test" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "com.oracle.truffle.tools.profiler",
                "truffle:TRUFFLE_TEST",
                "mx:JUNIT"
            ],
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "checkstyle" : "com.oracle.truffle.tools.chromeinspector",
            "javaCompliance" : "8+",
            "workingSets" : "Tools",
        },
        "com.oracle.truffle.tools.coverage" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "truffle:TRUFFLE_API",
                "TruffleJSON",
                ],
            "exports" : [
              "<package-info>", # exports all packages containing package-info.java
              "com.oracle.truffle.tools.coverage.impl to org.graalvm.truffle",
            ],
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "checkstyle" : "com.oracle.truffle.tools.chromeinspector",
            "javaCompliance" : "8+",
            "workingSets" : "Tools",
        },
        "com.oracle.truffle.tools.coverage.test" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "com.oracle.truffle.tools.coverage",
                "truffle:TRUFFLE_TEST",
                "mx:JUNIT"
            ],
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "checkstyle" : "com.oracle.truffle.tools.chromeinspector",
            "javaCompliance" : "8+",
            "workingSets" : "Tools",
        },
        "com.oracle.truffle.tools.warmup" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "truffle:TRUFFLE_API",
                "TruffleJSON",
                ],
            "exports" : [
              "<package-info>", # exports all packages containing package-info.java
              # "com.oracle.truffle.tools.warmup.impl to org.graalvm.truffle",
            ],
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "checkstyle" : "com.oracle.truffle.tools.chromeinspector",
            "javaCompliance" : "8+",
            "workingSets" : "Tools",
        },
        "com.oracle.truffle.tools.warmup.test" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "com.oracle.truffle.tools.warmup",
                "truffle:TRUFFLE_TEST",
                "mx:JUNIT"
            ],
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "checkstyle" : "com.oracle.truffle.tools.chromeinspector",
            "javaCompliance" : "8+",
            "workingSets" : "Tools",
        },
        "org.graalvm.tools.api.lsp": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "truffle:TRUFFLE_API",
            ],
            "checkstyle": "com.oracle.truffle.tools.chromeinspector",
            "javaCompliance": "8+",
            "annotationProcessors": ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "workingSets": "Tools",
        },
        "org.graalvm.tools.lsp": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "org.graalvm.tools.api.lsp",
                "TruffleJSON"
            ],
            "checkstyle": "com.oracle.truffle.tools.chromeinspector",
            "javaCompliance": "8+",
            "annotationProcessors": ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "workingSets": "Tools",
        },
        "org.graalvm.tools.lsp.test": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "org.graalvm.tools.lsp",
                "truffle:TRUFFLE_SL",
                "mx:JUNIT"
            ],
            "checkstyle": "com.oracle.truffle.tools.chromeinspector",
            "javaCompliance": "8+",
            "annotationProcessors": ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "workingSets": "Tools",
        },
    },

    "libraries": {
        "NanoHTTPD" : {
            "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/nanohttpd-2.3.2-efb2ebf85a2b06f7c508aba9eaad5377e3a01e81.jar"],
            "sha1" : "7d28e2828bfe2ac04dcb8779aded934ac7dc1e52",
        },
        "NanoHTTPD-WebSocket" : {
            "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/nanohttpd-websocket-2.3.2-efb2ebf85a2b06f7c508aba9eaad5377e3a01e81.jar"],
            "sha1" : "a8f5b9e7387e00a57d31be320a8246a7c8128aa4",
        },
        "TruffleJSON" : {
          "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/trufflejson-20180813.jar"],
          "sha1" : "c556821b83878d3a327bc07dedc1bf2998f99a8f",
        },
        "Java-WebSocket" : {
            "sha1" : "382b302303c830a7edb20c9ed61c4ac2cdf7a7a4",
            "maven" : {
                "groupId" : "org.java-websocket",
                "artifactId" : "Java-WebSocket",
                "version" : "1.5.1",
            },
            "dependencies" : ["SLF4J_API"],
        },
        "SLF4J_API" : {
            "sha1" : "b5a4b6d16ab13e34a88fae84c35cd5d68cac922c",
            "maven" : {
                "groupId" : "org.slf4j",
                "artifactId" : "slf4j-api",
                "version" : "1.7.30",
            }
        },
        "SLF4J_SIMPLE" : {
            "sha1" : "e606eac955f55ecf1d8edcccba04eb8ac98088dd",
            "maven" : {
                "groupId" : "org.slf4j",
                "artifactId" : "slf4j-simple",
                "version" : "1.7.30",
            },
            "dependencies" : ["SLF4J_API"]
        },
        "VISUALVM_COMMON" : {
            "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm/visualvm-830.tar.gz"],
            "sha1" : "dbf7cdee54f2797776b93bfaebd4f107ad8755f8",
        },
        "VISUALVM_PLATFORM_SPECIFIC" : {
            "os_arch" : {
                "linux" : {
                    "amd64" : {
                        "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm/visualvm-830-linux-amd64.tar.gz"],
                        "sha1" : "40d39ad1b1853fbe9bc452cd586de206411e0594",
                    },
                    "aarch64" : {
                        "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm/visualvm-830-linux-aarch64.tar.gz"],
                        "sha1" : "5de19e2d908b193dc023bc9da7062c0e2963de85",
                    }
                },
                "darwin" : {
                    "amd64" : {
                        "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm/visualvm-830-macosx-x86_64.tar.gz"],
                        "sha1" : "9d0daca5bb63a9da535bd2081c3344b5084f2f02",
                    }
                },
                "windows" : {
                    "amd64" : {
                        "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm/visualvm-830-windows-amd64.tar.gz"],
                        "sha1" : "ac526df68ef683c8794e021cf32d3ead42fdc87a",
                    }
                },
            }
        },
    },

    "distributions": {
        "CHROMEINSPECTOR": {
            "subDir": "src",
            # This distribution defines a module.
            "moduleName" : "com.oracle.truffle.tools.chromeinspector",
            "dependencies": ["com.oracle.truffle.tools.chromeinspector"],
            "distDependencies" : [
                "truffle:TRUFFLE_API",
                "TRUFFLE_PROFILER",
            ],
            "maven" : {
              "artifactId" : "chromeinspector",
            },
            "description" : "The bridge between truffle tools and the chrome inspector.",
        },
        "CHROMEINSPECTOR_TEST": {
            "subDir": "src",
            "dependencies": ["com.oracle.truffle.tools.chromeinspector.test"],
            "distDependencies" : [
                "truffle:TRUFFLE_API",
                "CHROMEINSPECTOR",
                "truffle:TRUFFLE_TEST",
                "truffle:TRUFFLE_SL",
            ],
            "exclude": [
              "mx:HAMCREST",
              "mx:JUNIT",
              "truffle:JLINE",
            ],
        },
        "CHROMEINSPECTOR_GRAALVM_SUPPORT" : {
            "native" : True,
            "description" : "Truffle Chrome Inspector support distribution for the GraalVM",
            "layout" : {
                "native-image.properties" : "file:mx.tools/tools-chromeinspector.properties",
            },
        },
        "AGENTSCRIPT": {
            "subDir": "src",
            # This distribution defines a module.
            "moduleName" : "com.oracle.truffle.tools.agentscript",
            "dependencies": [
                "org.graalvm.tools.insight",
                "com.oracle.truffle.tools.agentscript"
            ],
            "distDependencies" : [
                "truffle:TRUFFLE_API",
            ],
            "maven" : {
              "artifactId" : "agentscript",
            },
            "description" : "Script driven tracing and instrumentation Agent",
        },
        "AGENTSCRIPT_TEST": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.truffle.tools.agentscript.test",
            ],
            "distDependencies" : [
                "truffle:TRUFFLE_TEST",
                "AGENTSCRIPT",
            ],
            "description" : "Tests for the script driven tracing and instrumentation Agent.",
            "maven" : False,
        },
        "AGENTSCRIPT_GRAALVM_SUPPORT" : {
            "native" : True,
            "description" : "Script driven tracing and instrumentation Agent for the GraalVM",
            "layout" : {
                "native-image.properties" : "file:mx.tools/tools-agentscript.properties",
            },
        },
        "TRUFFLE_PROFILER": {
            "subDir": "src",
            # This distribution defines a module.
            "moduleName" : "com.oracle.truffle.tools.profiler",
            "dependencies": [
                "com.oracle.truffle.tools.profiler",
            ],
            "distDependencies" : [
                "truffle:TRUFFLE_API",
            ],
            "maven" : {
              "artifactId" : "profiler",
            },
            "javadocType" : "api",
            "description" : "The truffle profiler, supporting CPU sampling and tracing. Memory tracing support is experimental"
        },
        "TRUFFLE_PROFILER_TEST": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.truffle.tools.profiler.test",
            ],
            "distDependencies" : [
                "truffle:TRUFFLE_TEST",
                "TRUFFLE_PROFILER",
            ],
            "description" : "Tests for the truffle profiler.",
            "maven" : False,
        },
        "TRUFFLE_PROFILER_GRAALVM_SUPPORT" : {
            "native" : True,
            "description" : "Truffle Profiler support distribution for the GraalVM",
            "layout" : {
                "native-image.properties" : "file:mx.tools/tools-profiler.properties",
            },
        },
        "TRUFFLE_COVERAGE": {
            "subDir": "src",
            # This distribution defines a module.
            "moduleName" : "com.oracle.truffle.tools.coverage",
            "dependencies": [
                "com.oracle.truffle.tools.coverage",
            ],
            "distDependencies" : [
                "truffle:TRUFFLE_API",
            ],
            "maven" : {
              "artifactId" : "coverage",
            },
            "description" : "Truffle code coverage tool.",
            "javadocType" : "api",
        },
        "TRUFFLE_COVERAGE_TEST": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.truffle.tools.coverage.test",
            ],
            "distDependencies" : [
                "truffle:TRUFFLE_TEST",
                "TRUFFLE_COVERAGE",
            ],
            "description" : "Tests for the truffle coverage tool.",
            "maven" : False,
         },
        "TRUFFLE_COVERAGE_GRAALVM_SUPPORT" : {
            "native" : True,
            "description" : "Truffle Code coverage support distribution for the GraalVM",
            "layout" : {
                "native-image.properties" : "file:mx.tools/tools-coverage.properties",
            },
        },
        "TRUFFLE_WARMUP_ESTIMATOR": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.truffle.tools.warmup",
            ],
            "distDependencies" : [
                "truffle:TRUFFLE_API",
            ],
            "maven" : {
              "artifactId" : "warmup",
            },
            "description" : "Truffle warmup estimation tool.",
            "javadocType" : "api",
        },
        "TRUFFLE_WARMUP_ESTIMATOR_TEST": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.truffle.tools.warmup.test",
            ],
            "distDependencies" : [
                "truffle:TRUFFLE_TEST",
                "TRUFFLE_WARMUP_ESTIMATOR",
            ],
            "description" : "Tests for the truffle warmup estimator tool.",
            "maven" : False,
        },
        "TRUFFLE_WARMUP_ESTIMATOR_GRAALVM_SUPPORT" : {
            "native" : True,
            "description" : "Truffle Profiler support distribution for the GraalVM",
            "layout" : {
                "native-image.properties" : "file:mx.tools/tools-profiler.properties",
            },
        },
        "VISUALVM_GRAALVM_SUPPORT": {
            "native": True,
            "platformDependent": True,
            "description": "VisualVM support distribution for the GraalVM",
            "layout": {
                "./": [
                    "extracted-dependency:VISUALVM_COMMON/lib/visualvm/*",
                    "extracted-dependency:VISUALVM_PLATFORM_SPECIFIC/./lib/visualvm/*",
                ],
            },
        },
        "LSP_API": {
            "subDir": "src",
            # This distribution defines a module.
            "moduleName" : "org.graalvm.tools.api.lsp",
            "dependencies": ["org.graalvm.tools.api.lsp"],
            "distDependencies" : [
                "truffle:TRUFFLE_API",
            ],
            "maven" : {
              "artifactId" : "lsp_api",
            },
            "description" : "Truffle Language Server backend API.",
            "javadocType" : "api",
        },
        "LSP": {
            "subDir": "src",
            # This distribution defines a module.
            "moduleName" : "org.graalvm.tools.lsp",
            "dependencies": [
                "org.graalvm.tools.api.lsp",
                "org.graalvm.tools.lsp"
            ],
            "distDependencies" : [
                "LSP_API",
            ],
            "maven" : {
              "artifactId" : "lsp",
            },
            "description" : "Truffle Language Server backend implementation.",
        },
        "LSP_TEST": {
            "dependencies": ["org.graalvm.tools.lsp.test"],
            "distDependencies" : [
                "LSP",
                "truffle:TRUFFLE_SL",
            ],
            "description" : "Tests for the Truffle Language Server backend.",
        },
        "LSP_GRAALVM_SUPPORT" : {
            "native" : True,
            "description" : "Truffle Language Server backend for the GraalVM",
            "layout" : {
                "native-image.properties" : "file:mx.tools/tools-lsp.properties",
            },
        },
    },
}
