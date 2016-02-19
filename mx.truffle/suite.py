suite = {
  "mxversion" : "5.8.0",
  "name" : "truffle",
  "url" : "http://openjdk.java.net/projects/graal",
  "developer" : {
    "name" : "Truffle and Graal developers",
    "email" : "graal-dev@openjdk.java.net",
    "organization" : "Graal",
    "organizationUrl" : "http://openjdk.java.net/projects/graal",
  },
  "scm" : {
    "url" : "https://github.com/graalvm/truffle",
    "read" : "https://github.com/graalvm/truffle.git",
    "write" : "git@github.com:graalvm/truffle.git",
  },
  "repositories" : {
    "lafo-snapshots" : {
      "url" : "https://curio.ssw.jku.at/nexus/content/repositories/snapshots",
      "licenses" : ["GPLv2-CPE", "UPL"]
    },
  },
  "defaultLicense" : "GPLv2-CPE",
  "libraries" : {

    # ------------- Libraries -------------

    "JLINE" : {
      "urls" : [
        "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/jline-2.11.jar",
        "https://search.maven.org/remotecontent?filepath=jline/jline/2.11/jline-2.11.jar",
      ],
      "sha1" : "9504d5e2da5d78237239c5226e8200ec21182040",
      "sourcePath" : "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/jline-2.11-sources.jar",
      "sourceSha1" : "ef2539b992e5605be966b6db7cfc83930f0da39b",
      "maven" : {
      	"groupId" : "jline",
    	"artifactId" : "jline",
    	"version" : "2.11",
      }
    },
  },

  "projects" : {

    # ------------- Truffle -------------

    "com.oracle.truffle.api" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "javaCompliance" : "1.7",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.vm" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.interop.java",
        "com.oracle.truffle.api.instrumentation",
      ],
      "javaCompliance" : "1.7",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.test" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.profiles",
        "com.oracle.truffle.api.interop",
        "com.oracle.truffle.api.vm",
        "mx:JUNIT",
      ],
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "1.7",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "API,Truffle,Test",
      "jacoco" : "exclude",
    },

    "com.oracle.truffle.api.dsl" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.api"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.7",
      "workingSets" : "API,Truffle,Codegen",
    },

    "com.oracle.truffle.api.dsl.test" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.dsl.processor",
        "com.oracle.truffle.api.vm",
        "mx:JUNIT",
      ],
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "1.7",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "API,Truffle,Codegen,Test",
      "jacoco" : "exclude",
    },

    "com.oracle.truffle.dsl.processor" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.dsl",
        "com.oracle.truffle.api.interop",
        "com.oracle.truffle.api.instrumentation",
        "com.oracle.truffle.api.interop"
      ],
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "1.7",
      "workingSets" : "Truffle,Codegen",
    },

    "com.oracle.truffle.api.interop" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.api"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.7",
      "workingSets" : "API,Truffle",
    },

   "com.oracle.truffle.api.instrumentation" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.api"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.7",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.instrumentation.test" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.vm",
        "com.oracle.truffle.api.dsl.test",
        "mx:JUNIT"
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "javaCompliance" : "1.7",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.interop.java" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.interop",
        "com.oracle.truffle.api.dsl"
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.7",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.interop.java.test" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.interop.java",
        "com.oracle.truffle.api.vm",
        "mx:JUNIT"
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.7",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.object" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.api.interop"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.7",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.profiles" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.api"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.7",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.object" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.api.object"],
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "1.7",
      "workingSets" : "Truffle",
    },

    "com.oracle.truffle.object.basic" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.object"],
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "1.7",
      "workingSets" : "Truffle",
    },

    "com.oracle.truffle.tck" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "TRUFFLE_API",
        "mx:JUNIT"
      ],
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.7",
      "workingSets" : "Truffle,Tools",
    },

    "com.oracle.truffle.tools" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : [
                        "com.oracle.truffle.api.profiles",
                        "com.oracle.truffle.api.instrumentation"],
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.7",
      "workingSets" : "Truffle,Tools",
    },

    "com.oracle.truffle.tools.test" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : [
          "com.oracle.truffle.tools",
          "com.oracle.truffle.api.instrumentation.test",
          "mx:JUNIT"
          ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.7",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle,Tools",
    },

    "com.oracle.truffle.tools.debug.shell" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : [
                        "com.oracle.truffle.tools",
                        "com.oracle.truffle.api.vm",
                        "JLINE"
                        ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.7",
      "workingSets" : "Truffle,Tools",
    },

    "com.oracle.truffle.sl" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "TRUFFLE_API",
      ],
      "javaCompliance" : "1.7",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle,SimpleLanguage",
      "license" : "UPL",
    },

    "com.oracle.truffle.sl.test" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.tck",
        "com.oracle.truffle.sl",
      ],
      "checkstyle" : "com.oracle.truffle.sl",
      "javaCompliance" : "1.7",
      "workingSets" : "Truffle,SimpleLanguage,Test",
      "license" : "UPL",
    },

     "com.oracle.truffle.sl.tools" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.tools.debug.shell",
                        "com.oracle.truffle.sl"],
      "checkstyle" : "com.oracle.truffle.sl",
      "javaCompliance" : "1.7",
      "workingSets" : "Truffle,SimpleLanguage,Tools",
      "license" : "UPL",
    },
  },

  "licenses" : {
    "UPL" : {
      "name" : "Universal Permissive License, Version 1.0",
      "url" : "http://opensource.org/licenses/UPL",
    }
  },

  "distributions" : {

    # ------------- Distributions -------------

    "TRUFFLE_API" : {
      "subDir" : "truffle",
      "javaCompliance" : "1.7",
      "dependencies" : [
        "com.oracle.truffle.api.interop.java",
        "com.oracle.truffle.api.dsl",
        "com.oracle.truffle.api.profiles",
        "com.oracle.truffle.api.vm",
        "com.oracle.truffle.object.basic",
      ],
      "distDependencies" : [
      ],
      "description" : """Truffle is a multi-language framework for executing dynamic languages
        that achieves high performance when combined with Graal.""",
      "javadocType": "api",
    },

    "TRUFFLE_TCK" : {
      "subDir" : "truffle",
      "javaCompliance" : "1.7",
      "dependencies" : [
        "com.oracle.truffle.tck"
      ],
      "distDependencies" : ["TRUFFLE_API"],
      "exclude" : ["mx:JUNIT"],
      "description" : """A collection of tests that can certify language implementation to be compliant
        with most recent requirements of the Truffle infrastructure and tooling.""",
    },

    "TRUFFLE_DSL_PROCESSOR" : {
      "subDir" : "truffle",
      "javaCompliance" : "1.7",
      "dependencies" : ["com.oracle.truffle.dsl.processor"],
      "distDependencies" : ["TRUFFLE_API"],
      "description" : "The Truffle DSL Processor generates source code for nodes that are declared using the DSL.",
    },

    "TRUFFLE_SL" : {
      "subDir" : "truffle",
      "javaCompliance" : "1.7",
      "dependencies" : [
        "com.oracle.truffle.sl",
        "com.oracle.truffle.sl.test"
      ],
      "distDependencies" : [
          "TRUFFLE_API",
          "TRUFFLE_TCK",
          "TRUFFLE_DSL_PROCESSOR"
      ],
      "license" : "UPL",
      "description" : "Truffle SL is an example language implemented using the Truffle API.",
    },

     "TRUFFLE_DEBUG" : {
      "subDir" : "truffle",
      "javaCompliance" : "1.7",
      "dependencies" : [
        "com.oracle.truffle.tools.debug.shell",
        "com.oracle.truffle.tools"
      ],
      "exclude" : ["JLINE"],
      "distDependencies" : [
          "TRUFFLE_API",
      ],
      "description" : "Experimental REPL server to build your debugger console for your language.",
      "allowsJavadocWarnings": True,
     }
  },
}
