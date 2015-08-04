suite = {
  "mxversion" : "5.2",
  "name" : "truffle",
  "url" : "http://openjdk.java.net/projects/graal",
  "developer" : {
    "name" : "Truffle and Graal developers",
    "email" : "graal-dev@openjdk.java.net",
    "organization" : "Graal",
    "organizationUrl" : "http://openjdk.java.net/projects/graal",
  },
  "defaultLicence" : "GPLv2-CPE",
  "libraries" : {

    # ------------- Libraries -------------

    "JLINE" : {
      "path" : "lib/jline-2.11.jar",
      "urls" : [
        "http://lafo.ssw.uni-linz.ac.at/graal-external-deps/jline-2.11.jar",
        "https://search.maven.org/remotecontent?filepath=jline/jline/2.11/jline-2.11.jar",
      ],
      "sha1" : "9504d5e2da5d78237239c5226e8200ec21182040",
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
      "netbeans.project.properties" : "main.class=com.oracle.truffle.api.impl.Accessor",
    },

    "com.oracle.truffle.api.test" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.interop",
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
      "dependencies" : ["com.oracle.truffle.api.dsl"],
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

    "com.oracle.truffle.api.object" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.api.interop"],
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
        "com.oracle.truffle.api.dsl",
        "com.oracle.truffle.api.interop",
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
      "dependencies" : ["com.oracle.truffle.api"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.7",
      "workingSets" : "Truffle,Tools",
    },

    "com.oracle.truffle.tools.test" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : [
          "com.oracle.truffle.tools",
          "mx:JUNIT"
          ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.7",
      "workingSets" : "Truffle,Tools",
    },

    "com.oracle.truffle.tools.debug.shell" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.tools",
                        "JLINE"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.7",
      "workingSets" : "Truffle,Tools",
    },

    "com.oracle.truffle.sl" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.dsl",
        "com.oracle.truffle.api.object",
        "com.oracle.truffle.tools",
      ],
      "javaCompliance" : "1.7",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle,SimpleLanguage",
      "licence" : "UPL",
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
      "licence" : "UPL",
    },

     "com.oracle.truffle.sl.tools" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.tools.debug.shell",
                        "com.oracle.truffle.sl"],
      "checkstyle" : "com.oracle.truffle.sl",
      "javaCompliance" : "1.7",
      "workingSets" : "Truffle,SimpleLanguage,Tools",
      "licence" : "UPL",
    },
  },

  "licences" : {
    "UPL" : {
      "name" : "Universal Permissive License, Version 1.0",
      "url" : "http://opensource.org/licenses/UPL",
    }
  },

  "distributions" : {

    # ------------- Distributions -------------

    "TRUFFLE" : {
      "path" : "build/truffle-api.jar",
      "subDir" : "truffle",
      "sourcesPath" : "build/truffle-api.src.zip",
      "javaCompliance" : "1.7",
      "dependencies" : [
        "com.oracle.truffle.api.dsl",
        "com.oracle.truffle.object.basic",
        "com.oracle.truffle.tools"
      ],
      "distDependencies" : [
      ],
      "description" : """Truffle is a multi-language framework for executing dynamic languages
        that achieves high performance when combined with Graal.""",
    },

    "TRUFFLE_TCK" : {
      "path" : "build/truffle-tck.jar",
      "subDir" : "truffle",
      "sourcesPath" : "build/truffle-tck.src.zip",
      "javaCompliance" : "1.7",
      "dependencies" : [
        "com.oracle.truffle.tck"
      ],
      "distDependencies" : ["TRUFFLE"],
      "exclude" : ["mx:JUNIT"],
      "description" : """A collection of tests that can certify language implementation to be compliant
        with most recentrequirements of the Truffle infrastructure and tooling.""",
    },

    "TRUFFLE_DSL_PROCESSOR" : {
      "path" : "build/truffle-dsl-processor.jar",
      "subDir" : "truffle",
      "sourcesPath" : "build/truffle-dsl-processor.src.zip",
      "javaCompliance" : "1.7",
      "dependencies" : ["com.oracle.truffle.dsl.processor"],
      "distDependencies" : ["TRUFFLE"],
      "description" : "The Truffle DSL Processor generates source code for nodes that are declared using the DSL.",
    },

    "TRUFFLE_SL" : {
      "path" : "build/truffle-sl.jar",
      "subDir" : "truffle",
      "sourcesPath" : "build/truffle-sl.src.zip",
      "javaCompliance" : "1.7",
      "dependencies" : [
        "com.oracle.truffle.sl",
        "com.oracle.truffle.sl.test"
      ],
      "distDependencies" : [
          "TRUFFLE",
          "TRUFFLE_TCK",
          "TRUFFLE_DSL_PROCESSOR"
      ],
      "licence" : "UPL",
      "description" : "Truffle SL is an example language implemented using the Truffle API.",
    },

     "TRUFFLE_DEBUG" : {
      "path" : "build/truffle-debug.jar",
      "subDir" : "truffle",
      "sourcesPath" : "build/truffle-debug.src.zip",
      "javaCompliance" : "1.7",
      "dependencies" : [
        "com.oracle.truffle.tools.debug.shell",
      ],
      "exclude" : ["JLINE"],
      "distDependencies" : [
          "TRUFFLE",
      ],
      "description" : ".",
     }
  },
}
