suite = {
  "mxversion" : "1.0",
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

    "JUNIT" : {
      "path" : "lib/junit-4.11.jar",
      "urls" : [
        "http://lafo.ssw.uni-linz.ac.at/graal-external-deps/junit-4.11.jar",
        "https://search.maven.org/remotecontent?filepath=junit/junit/4.11/junit-4.11.jar",
      ],
      "sha1" : "4e031bb61df09069aeb2bffb4019e7a5034a4ee0",
      "eclipse.container" : "org.eclipse.jdt.junit.JUNIT_CONTAINER/4",
      "sourcePath" : "lib/junit-4.11-sources.jar",
      "sourceUrls" : [
        "http://lafo.ssw.uni-linz.ac.at/graal-external-deps/junit-4.11-sources.jar",
        "https://search.maven.org/remotecontent?filepath=junit/junit/4.11/junit-4.11-sources.jar",
      ],
      "sourceSha1" : "28e0ad201304e4a4abf999ca0570b7cffc352c3c",
      "dependencies" : ["HAMCREST"],
    },

    "CHECKSTYLE" : {
      "path" : "lib/checkstyle-6.0-all.jar",
      "urls" : [
        "http://lafo.ssw.uni-linz.ac.at/graal-external-deps/checkstyle-6.0-all.jar",
        "jar:http://sourceforge.net/projects/checkstyle/files/checkstyle/6.0/checkstyle-6.0-bin.zip/download!/checkstyle-6.0/checkstyle-6.0-all.jar",
      ],
      "sha1" : "2bedc7feded58b5fd65595323bfaf7b9bb6a3c7a",
    },

    "HAMCREST" : {
      "path" : "lib/hamcrest-core-1.3.jar",
      "urls" : [
        "http://lafo.ssw.uni-linz.ac.at/graal-external-deps/hamcrest-core-1.3.jar",
        "https://search.maven.org/remotecontent?filepath=org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar",
      ],
      "sha1" : "42a25dc3219429f0e5d060061f71acb49bf010a0",
      "sourcePath" : "lib/hamcrest-core-1.3-sources.jar",
      "sourceUrls" : [
        "http://lafo.ssw.uni-linz.ac.at/graal-external-deps/hamcrest-core-1.3-sources.jar",
        "https://search.maven.org/remotecontent?filepath=org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3-sources.jar",
      ],
      "sourceSha1" : "1dc37250fbc78e23a65a67fbbaf71d2e9cbc3c0b",
    },

    "HCFDIS" : {
      "path" : "lib/hcfdis-2.jar",
      "urls" : ["http://lafo.ssw.uni-linz.ac.at/hcfdis-2.jar"],
      "sha1" : "bc8b2253436485e9dbaf81771c259ccfa1a24c80",
    },

    "FINDBUGS_DIST" : {
      "path" : "lib/findbugs-dist-3.0.0.zip",
      "urls" : [
        "http://lafo.ssw.uni-linz.ac.at/graal-external-deps/findbugs-3.0.0.zip",
        "http://sourceforge.net/projects/findbugs/files/findbugs/3.0.0/findbugs-3.0.0.zip/download",
      ],
      "sha1" : "6e56d67f238dbcd60acb88a81655749aa6419c5b",
    },

    "C1VISUALIZER_DIST" : {
      "path" : "lib/c1visualizer_2014-04-22.zip",
      "urls" : ["https://java.net/downloads/c1visualizer/c1visualizer_2014-04-22.zip"],
      "sha1" : "220488d87affb569b893c7201f8ce5d2b0e03141",
    },

    "JOL_INTERNALS" : {
      "path" : "lib/jol-internals.jar",
      "urls" : ["http://lafo.ssw.uni-linz.ac.at/truffle/jol/jol-internals.jar"],
      "sha1" : "508bcd26a4d7c4c44048990c6ea789a3b11a62dc",
    },

    "JACOCOAGENT" : {
      "path" : "lib/jacocoagent.jar",
      "urls" : ["http://lafo.ssw.uni-linz.ac.at/jacoco/jacocoagent-0.7.1-1.jar"],
      "sha1" : "2f73a645b02e39290e577ce555f00b02004650b0",
    },

    "JACOCOREPORT" : {
      "path" : "lib/jacocoreport.jar",
      "urls" : ["http://lafo.ssw.uni-linz.ac.at/jacoco/jacocoreport-0.7.1-2.jar"],
      "sha1" : "a630436391832d697a12c8f7daef8655d7a1efd2",
    },

    "DACAPO_SCALA" : {
      "path" : "lib/dacapo-scala-0.1.0-20120216.jar",
      "urls" : [
        "http://lafo.ssw.uni-linz.ac.at/graal-external-deps/dacapo-scala-0.1.0-20120216.jar",
        "http://repo.scalabench.org/snapshots/org/scalabench/benchmarks/scala-benchmark-suite/0.1.0-SNAPSHOT/scala-benchmark-suite-0.1.0-20120216.103539-3.jar",
      ],
      "sha1" : "59b64c974662b5cf9dbd3cf9045d293853dd7a51",
    },

    "JAVA_ALLOCATION_INSTRUMENTER" : {
      "path" : "lib/java-allocation-instrumenter.jar",
      "sourcePath" : "lib/java-allocation-instrumenter.jar",
      "urls" : ["http://lafo.ssw.uni-linz.ac.at/java-allocation-instrumenter/java-allocation-instrumenter-8f0db117e64e.jar"],
      "sha1" : "476d9a44cd19d6b55f81571077dfa972a4f8a083",
      "bootClassPathAgent" : "true",
    },

    "VECMATH" : {
      "path" : "lib/vecmath-1.3.1.jar",
      "urls" : [
        "http://lafo.ssw.uni-linz.ac.at/graal-external-deps/vecmath-1.3.1.jar",
        "https://search.maven.org/remotecontent?filepath=java3d/vecmath/1.3.1/vecmath-1.3.1.jar",
      ],
      "sha1" : "a0ae4f51da409fa0c20fa0ca59e6bbc9413ae71d",
    },

    "JMH" : {
      "path" : "lib/jmh-runner-1.4.2.jar",
      "sha1" : "f44bffaf237305512002303a306fc5ce3fa63f76",
      "urls" : ["http://lafo.ssw.uni-linz.ac.at/jmh/jmh-runner-1.4.2.jar"],
      "annotationProcessor" : "true"
    },

    "BATIK" : {
      "path" : "lib/batik-all-1.7.jar",
      "sha1" : "122b87ca88e41a415cf8b523fd3d03b4325134a3",
      "urls" : ["http://lafo.ssw.uni-linz.ac.at/graal-external-deps/batik-all-1.7.jar"],
    }
},

  "jrelibraries" : {
    "JFR" : {
      "jar" : "jfr.jar",
    }
  },

  "projects" : {

    # ------------- Truffle -------------

    "com.oracle.truffle.api" : {
      "subDir" : "truffle",
      "sourceDirs" : ["src"],
      "javaCompliance" : "1.7",
      "workingSets" : "API,Truffle",
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
      "annotationProcessors" : ["com.oracle.truffle.dsl.processor"],
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
        "JUNIT",
      ],
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "1.7",
      "annotationProcessors" : ["com.oracle.truffle.dsl.processor"],
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
      "annotationProcessors" : ["com.oracle.truffle.dsl.processor"],
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
      "annotationProcessors" : ["com.oracle.truffle.dsl.processor"],
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
      "path" : "build/truffle.jar",
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
