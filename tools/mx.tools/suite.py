suite = {
    "mxversion": "5.128.5",
    "name": "tools",
    "defaultLicense" : "GPLv2-CPE",

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
                "org.json",
            ],
            "javaCompliance" : "1.8",
            "checkstyle" : "com.oracle.truffle.api",
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "workingSets" : "Tools",
            "license" : "GPLv2-CPE",
        },
        "com.oracle.truffle.tools.chromeinspector.test" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "com.oracle.truffle.tools.chromeinspector",
                "mx:JUNIT",
            ],
            "javaCompliance" : "1.8",
            "checkstyle" : "com.oracle.truffle.tools.chromeinspector.test",
            "checkstyleVersion" : "8.8",
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "workingSets" : "Tools",
            "license" : "GPLv2-CPE",
        },
        "com.oracle.truffle.tools.profiler" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : ["truffle:TRUFFLE_API"],
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "checkstyle" : "com.oracle.truffle.api",
            "javaCompliance" : "1.8",
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
            "checkstyle" : "com.oracle.truffle.api",
            "javaCompliance" : "1.8",
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
        "org.json" : {
          "path" : "lib/json-20160810.jar",
          "urls" : [
            "https://search.maven.org/remotecontent?filepath=org/json/json/20160810/json-20160810.jar",
          ],
          "sha1" : "aca5eb39e2a12fddd6c472b240afe9ebea3a6733",
          "maven" : {
            "groupId" : "org.json",
            "artifactId" : "json",
            "version" : "20160810",
          }
        },
    },

    "distributions": {
        "CHROMEINSPECTOR": {
            "dependencies": ["com.oracle.truffle.tools.chromeinspector"],
            "distDependencies" : [
                "truffle:TRUFFLE_API",
                "TRUFFLE_PROFILER",
            ],
        },
        "CHROMEINSPECTOR_TEST": {
            "dependencies": ["com.oracle.truffle.tools.chromeinspector.test"],
            "distDependencies" : [
                "truffle:TRUFFLE_API",
                "CHROMEINSPECTOR",
                "truffle:TRUFFLE_SL",
            ],
            "exclude": [
              "mx:HAMCREST",
              "mx:JUNIT",
              "truffle:JLINE",
            ],
        },
        "TRUFFLE_PROFILER": {
            "dependencies": [
                "com.oracle.truffle.tools.profiler",
            ],
            "distDependencies" : [
                "truffle:TRUFFLE_API",
            ],
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
    },
}
