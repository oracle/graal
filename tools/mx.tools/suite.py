suite = {
    "mxversion": "5.176.0",
    "name": "tools",
    "defaultLicense" : "GPLv2-CPE",

    "groupId" : "org.graalvm.tools",
    "version": "1.0.0-rc10",
    "release" : False,
    "url" : "http://openjdk.java.net/projects/graal",
    "developer" : {
      "name" : "Truffle and Graal developers",
      "email" : "graal-dev@openjdk.java.net",
      "organization" : "Graal",
      "organizationUrl" : "http://openjdk.java.net/projects/graal",
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
            "checkstyleVersion" : "8.8",
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
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
            "checkstyleVersion" : "8.8",
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
            "checkstyleVersion" : "8.8",
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
       "NanoHTTPD" : {
          "path" : "lib/nanohttpd-2.3.1.jar",
          "urls" : [
            "https://search.maven.org/remotecontent?filepath=org/nanohttpd/nanohttpd/2.3.1/nanohttpd-2.3.1.jar",
          ],
          "sha1" : "a8d54d1ca554a77f377eff6bf9e16ca8383c8f6c",
          "maven" : {
            "groupId" : "org.nanohttpd",
            "artifactId" : "nanohttpd-webserver",
            "version" : "2.3.1",
          }
        },
       "NanoHTTPD-WebSocket" : {
          "path" : "lib/nanohttpd-websocket-2.3.1.jar",
          "urls" : [
            "https://search.maven.org/remotecontent?filepath=org/nanohttpd/nanohttpd-websocket/2.3.1/nanohttpd-websocket-2.3.1.jar",
          ],
          "sha1" : "f2cfb09cee12469ff64f0d698b13de19903bb4f7",
          "maven" : {
            "groupId" : "org.nanohttpd",
            "artifactId" : "nanohttpd-websocket",
            "version" : "2.3.1",
          }
        },
        "TruffleJSON" : {
          "urls" : [
            "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/trufflejson-20180130.jar",
          ],
          "sha1" : "8819cea8bfe22c9c63f55465e296b3855ea41786",
        },
        "Java-WebSocket" : {
            "path" : "lib/Java-WebSocket-1.3.9.jar",
            "urls" : [
                "https://search.maven.org/remotecontent?filepath=org/java-websocket/Java-WebSocket/1.3.9/Java-WebSocket-1.3.9.jar",
            ],
            "sha1" : "e6e60889b7211a80b21052a249bd7e0f88f79fee",
            "maven" : {
                "groupId" : "org.java-websocket",
                "artifactId" : "Java-WebSocket",
                "version" : "1.3.9",
            }
        },
        "VISUALVM_COMMON" : {
            "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm-615.tar.gz"],
            "sha1" : "c8d5efde5a21cc46ce61cc0f0dd53c55baf0fcbd",
        },
        "VISUALVM_PLATFORM_SPECIFIC" : {
            "os_arch" : {
                "linux" : {
                    "amd64" : {
                        "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm-615-linux-amd64.tar.gz"],
                        "sha1" : "bd7b07ab872a4fc672c237abc52e3ef8905c3ff9",
                    }
                },
                "darwin" : {
                    "amd64" : {
                        "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm-615-macosx-x86_64.tar.gz"],
                        "sha1" : "956899a5cddd9abe837b338aa4946463f26aad50",
                    }
                },
            }
        },
    },

    "distributions": {
        "CHROMEINSPECTOR": {
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
        "TRUFFLE_PROFILER": {
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
