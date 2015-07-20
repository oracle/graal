suite = {
  "mxversion" : "4.3.4",
  "name" : "truffle",
  "libraries" : {

    # ------------- Libraries -------------

    "JLINE" : {
      "path" : "lib/jline-2.11.jar",
      "urls" : [
        "http://lafo.ssw.uni-linz.ac.at/graal-external-deps/jline-2.11.jar",
        "https://search.maven.org/remotecontent?filepath=jline/jline/2.11/jline-2.11.jar",
      ],
      "sha1" : "9504d5e2da5d78237239c5226e8200ec21182040",
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
        "JUNIT",
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
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.7",
      "workingSets" : "API,Truffle,Codegen",
    },

    "com.oracle.truffle.api.dsl.test" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.dsl.processor",
        "JUNIT",
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
        "JUNIT"
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.7",
      "workingSets" : "Truffle,Tools",
    },

    "com.oracle.truffle.tools" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.api"],
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
          "JUNIT"
          ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.7",
      "workingSets" : "Truffle,Tools",
    },

    "com.oracle.truffle.tools.debug.engine" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.tools"],
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.7",
      "workingSets" : "Truffle,Tools",
    },

    "com.oracle.truffle.tools.debug.shell" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.tools.debug.engine",
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
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "1.7",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle,SimpleLanguage",
    },

    "com.oracle.truffle.sl.test" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.tck",
        "com.oracle.truffle.sl"
      ],
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "1.7",
      "workingSets" : "Truffle,SimpleLanguage,Test",
    },

     "com.oracle.truffle.sl.tools" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.tools.debug.shell",
                        "com.oracle.truffle.sl"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.7",
      "workingSets" : "Truffle,SimpleLanguage,Tools",
    },
  },

  "distributions" : {

    # ------------- Distributions -------------

    "TRUFFLE" : {
      "path" : "build/truffle-api.jar",
      "subDir" : "truffle",
      "sourcesPath" : "build/truffle.src.zip",
      "javaCompliance" : "1.7",
      "dependencies" : [
        "com.oracle.truffle.api.dsl",
        "com.oracle.truffle.object.basic",
        "com.oracle.truffle.tools"
      ],
      "distDependencies" : [
      ],
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
    },

    "TRUFFLE_DSL_PROCESSOR" : {
      "path" : "build/truffle-dsl-processor.jar",
      "subDir" : "truffle",
      "sourcesPath" : "build/truffle-dsl-processor.src.zip",
      "javaCompliance" : "1.7",
      "dependencies" : ["com.oracle.truffle.dsl.processor"],
      "distDependencies" : ["TRUFFLE"],
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
     }
  },
}
