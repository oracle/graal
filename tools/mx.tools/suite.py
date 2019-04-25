suite = {
    "mxversion": "5.210.2",
    "name": "tools",
    "defaultLicense" : "GPLv2-CPE",

    "groupId" : "org.graalvm.tools",
    "version" : "20.0.0-beta.1",
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
    },

    "libraries": {
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
            "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm-670.tar.gz"],
            "sha1" : "aee7dd2a9663701df57a96e501b9219a7c90cbc3",
        },
        "VISUALVM_PLATFORM_SPECIFIC" : {
            "os_arch" : {
                "linux" : {
                    "amd64" : {
                        "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm-670-linux-amd64.tar.gz"],
                        "sha1" : "e607a0c40716f4a232de3726ab101f5c54798cfa",
                    }
                },
                "darwin" : {
                    "amd64" : {
                        "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm-670-macosx-x86_64.tar.gz"],
                        "sha1" : "ea45e21c35f24d05e74d46bbdc068bf48865aa8f",
                    }
                },
                "windows" : {
                    "amd64" : {
                        "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm-670-windows-amd64.tar.gz"],
                        "sha1" : "6a4fa4b6a0c1fceeee63567af9f70d63a5ad6334",
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
    },
}
