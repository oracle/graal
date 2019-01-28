suite = {
    "mxversion": "5.223.0",
    "name": "tools",
    "defaultLicense" : "GPLv2-CPE",

    "groupId" : "org.graalvm.tools",
    "version" : "20.0.0",
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
        "com.oracle.truffle.tools.agentscript" : {
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
        "com.oracle.truffle.tools.agentscript.test" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "com.oracle.truffle.tools.agentscript",
                "truffle:TRUFFLE_INSTRUMENT_TEST",
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
                "truffle:TRUFFLE_INSTRUMENT_TEST",
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
                "truffle:TRUFFLE_INSTRUMENT_TEST",
                "mx:JUNIT"
            ],
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "checkstyle" : "com.oracle.truffle.tools.chromeinspector",
            "javaCompliance" : "8+",
            "workingSets" : "Tools",
        },
        "org.graalvm.tools.lsp.api": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [ ],
            "checkstyle": "org.graalvm.tools.lsp",
            "javaCompliance": "1.8",
            "workingSets": "LSP"
        },
        "org.graalvm.tools.lsp": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "org.graalvm.tools.lsp.api",
                "truffle:TRUFFLE_API",
                "truffle:TRUFFLE_NFI",
                "LSP4J"
            ],
            "checkstyleVersion" : "8.8",
            "checkstyle": "org.graalvm.tools.lsp",
            "javaCompliance": "1.8",
            "annotationProcessors": ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "workingSets": "LSP",
        },
        "org.graalvm.tools.lsp.launcher": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "org.graalvm.tools.lsp.api",
                "sdk:GRAAL_SDK",
                "sdk:LAUNCHER_COMMON",
            ],
            "checkstyle": "org.graalvm.tools.lsp",
            "javaCompliance": "1.8",
            "workingSets": "LSP"
        },
        "org.graalvm.tools.lsp.test": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "org.graalvm.tools.lsp.api",
                "org.graalvm.tools.lsp",
                "org.graalvm.tools.lsp.launcher",
                "truffle:TRUFFLE_SL",
                "mx:JUNIT"
            ],
            "checkstyle": "org.graalvm.tools.lsp",
            "javaCompliance": "1.8",
            "annotationProcessors": ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "workingSets": "LSP",
        },
    },

    "libraries": {
        "NanoHTTPD" : {
            "urls" : [
                "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/nanohttpd-2.3.2-efb2ebf85a2b06f7c508aba9eaad5377e3a01e81.jar",
            ],
            "sha1" : "7d28e2828bfe2ac04dcb8779aded934ac7dc1e52",
        },
        "NanoHTTPD-WebSocket" : {
            "urls" : [
                "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/nanohttpd-websocket-2.3.2-efb2ebf85a2b06f7c508aba9eaad5377e3a01e81.jar",
            ],
            "sha1" : "a8f5b9e7387e00a57d31be320a8246a7c8128aa4",
        },
        "LSP4J" : {
            "sha1" : "90e34b7c7e0257e3993ca5a939ae94f889d31340",
            "sourceSha1": "6dd33739fe6dc7f306b819d88a6f9a8f9279da51",
            "maven" : {
                "groupId" : "org.eclipse.lsp4j",
                "artifactId" : "org.eclipse.lsp4j",
                "version" : "0.4.1",
            },
            "dependencies" : ["LSP4J-JSONRPC", "LSP4J-GENERATOR"],
        },
        "LSP4J-JSONRPC" : {
            "sha1" : "f3f93f50bbeb7d58b50e6ffca615cbfc76491846",
            "sourceSha1": "2cb08b2bcc262bb984822c274987a87664a75fed",
            "maven" : {
                "groupId" : "org.eclipse.lsp4j",
                "artifactId" : "org.eclipse.lsp4j.jsonrpc",
                "version" : "0.4.1",
            },
            "dependencies" : ["GSON"],
        },
        "LSP4J-GENERATOR" : {
            "sha1" : "467f27e91fd694c05eb663532f2ede0404025afe",
            "sourceSha1": "80fc1d3f970fd3e666ecd7f70781e22d4e7f70ee",
            "maven" : {
                "groupId" : "org.eclipse.lsp4j",
                "artifactId" : "org.eclipse.lsp4j.generator",
                "version" : "0.4.1",
            },
            "dependencies" : ["XTEND-LIB"],
        },
        "XTEXT-XBASE-LIB" : {
            "sha1" : "ea0734bda785af01c6f02298d25ed3189dd5a2ac",
            "sourceSha1": "d2ed94bab5bae700d752a6f638edf08c19298464",
            "maven" : {
                "groupId" : "org.eclipse.xtext",
                "artifactId" : "org.eclipse.xtext.xbase.lib",
                "version" : "2.13.0",
            },
            "dependencies" : ["GUAVA"],
        },
        "XTEND-LIB" : {
            "sha1" : "accfb60dda659a31dddb5823d4fbcc7c0c1aa4ae",
            "sourceSha1": "c8841f7735714cc794a980094178a9fd31b50754",
            "maven" : {
                "groupId" : "org.eclipse.xtend",
                "artifactId" : "org.eclipse.xtend.lib",
                "version" : "2.13.0",
            },
            "dependencies" : ["XTEND-LIB-MACRO", "XTEXT-XBASE-LIB"],
        },
        "XTEND-LIB-MACRO" : {
            "sha1" : "04897a782f69cee9326ea1ae7e10078b4d738463",
            "sourceSha1": "67abbc9540e78a8aba1c6e4fad3ba1b2183f7be7",
            "maven" : {
                "groupId" : "org.eclipse.xtend",
                "artifactId" : "org.eclipse.xtend.lib.macro",
                "version" : "2.13.0",
            }
        },
        "GSON" : {
            "sha1" : "751f548c85fa49f330cecbb1875893f971b33c4e",
            "sourceSha1": "bbb63ca253b483da8ee53a50374593923e3de2e2",
            "maven" : {
                "groupId" : "com.google.code.gson",
                "artifactId" : "gson",
                "version" : "2.7",
            }
        },
        "GUAVA" : {
            "sha1" : "6ce200f6b23222af3d8abb6b6459e6c44f4bb0e9",
            "sourceSha1": "91a4d115400e904f22b03a78deb355e9ea803cd4",
            "maven" : {
                "groupId" : "com.google.guava",
                "artifactId" : "guava",
                "version" : "19.0",
            }
        },
        "TruffleJSON" : {
          "urls" : [
            "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/trufflejson-20180813.jar",
          ],
          "sha1" : "c556821b83878d3a327bc07dedc1bf2998f99a8f",
        },
        "Java-WebSocket" : {
            "path" : "lib/Java-WebSocket-1.3.9.jar",
            "sha1" : "e6e60889b7211a80b21052a249bd7e0f88f79fee",
            "maven" : {
                "groupId" : "org.java-websocket",
                "artifactId" : "Java-WebSocket",
                "version" : "1.3.9",
            }
        },
        "VISUALVM_COMMON" : {
            "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm/visualvm-725.tar.gz"],
            "sha1" : "a0a3db1a8cb2cf85dc6480319f8cb59a41889555",
        },
        "VISUALVM_PLATFORM_SPECIFIC" : {
            "os_arch" : {
                "linux" : {
                    "amd64" : {
                        "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm/visualvm-725-linux-amd64.tar.gz"],
                        "sha1" : "f854f17cd076f6f64676e08d5de3b2c8c54cca98",
                    },
                    "aarch64" : {
                        "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm/visualvm-725-linux-aarch64.tar.gz"],
                        "sha1" : "c2dd11fb0ce8b693c192c206e0aabde1ea3d90b0",
                    }
                },
                "darwin" : {
                    "amd64" : {
                        "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm/visualvm-725-macosx-x86_64.tar.gz"],
                        "sha1" : "95a56e2619681823559cb606f32898673a13ed6d",
                    }
                },
                "windows" : {
                    "amd64" : {
                        "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm/visualvm-725-windows-amd64.tar.gz"],
                        "sha1" : "4ec342c6a8c454c7cf9d4ab1b02972bacb2a00d2",
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
            "dependencies": ["com.oracle.truffle.tools.agentscript"],
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
                "truffle:TRUFFLE_INSTRUMENT_TEST",
                "AGENTSCRIPT",
            ],
            "description" : "Tests for the script driven tracing and instrumentation Agent.",
            "maven" : False,
        },
        "AGENTSCRIPT_GRAALVM_SUPPORT" : {
            "native" : True,
            "description" : "Script driven tracing and instrumentation Agentfor the GraalVM",
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
                "truffle:TRUFFLE_INSTRUMENT_TEST",
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
                "truffle:TRUFFLE_INSTRUMENT_TEST",
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
        "LSP-API": {
            "dependencies": ["org.graalvm.tools.lsp.api"],
            "distDependencies" : [ ],
        },
        "LSP": {
            "dependencies": ["org.graalvm.tools.lsp"],
            "distDependencies" : [
                "LSP-API",
                "truffle:TRUFFLE_API",
                "truffle:TRUFFLE_NFI",
                "LSP4J"
            ],
        },
        "LSP-LAUNCHER": {
            "dependencies": ["org.graalvm.tools.lsp.launcher"],
            "distDependencies" : [
                "LSP-API",
                "sdk:GRAAL_SDK",
                "sdk:LAUNCHER_COMMON",
            ],
        },
    },
}
