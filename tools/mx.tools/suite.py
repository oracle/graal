suite = {
    "mxversion": "5.176.0",
    "name": "tools",
    "defaultLicense" : "GPLv2-CPE",

    "groupId" : "org.graalvm.tools",
    "version": "1.0.0-rc8",
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
            ],
            "exports" : [
              "<package-info>", # exports all packages containing package-info.java
              "com.oracle.truffle.tools.chromeinspector.instrument to com.oracle.truffle.truffle_api"
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
              "com.oracle.truffle.tools.profiler.impl to com.oracle.truffle.truffle_api",
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
        "VISUALVM_COMMON" : {
            "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm-606.tar.gz"],
            "sha1" : "4cb0f27e677582e760fdce1d71900048cb30c3e8",
        },
        "VISUALVM_PLATFORM_SPECIFIC" : {
            "os_arch" : {
                "linux" : {
                    "amd64" : {
                        "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm-606-linux-amd64.tar.gz"],
                        "sha1" : "cbd7d1b4378f024a9fc5a71ac79ed90ad5f8a173",
                    }
                },
                "darwin" : {
                    "amd64" : {
                        "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm-606-macosx-x86_64.tar.gz"],
                        "sha1" : "6e5a902272f1d10db9b16407fe1f6faf40ddd90b",
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
    },
}
