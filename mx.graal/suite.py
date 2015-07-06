suite = {
  "mxversion" : "4.0.0",
  "name" : "graal",

  "imports" : {
    "suites": [
            {
               "name" : "jvmci",
               "version" : "ed35cb99842854869c6a18a3626064932db32968",
               "urls" : [
                    {"url" : "http://lafo.ssw.uni-linz.ac.at/hg/graal-jvmci-8", "kind" : "hg"},
                ]
            },
            {
               "name" : "truffle",
               "version" : "4858c5e074e9005075e89cf5b1c2347ee73a5d31",
               "urls" : [
                    {"url" : "http://lafo.ssw.uni-linz.ac.at/hg/truffle", "kind" : "hg"},
                    {"url" : "http://lafo.ssw.uni-linz.ac.at/nexus/content/repositories/snapshots", "kind" : "binary", "version-adjust" : "0.8-{version}-SNAPSHOT"},
                ]
            },
    ]
   },

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

    "HCFDIS" : {
      "path" : "lib/hcfdis-2.jar",
      "urls" : ["http://lafo.ssw.uni-linz.ac.at/hcfdis-2.jar"],
      "sha1" : "bc8b2253436485e9dbaf81771c259ccfa1a24c80",
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

    "DACAPO" : {
      "path" : "lib/dacapo-9.12-bach.jar",
      "urls" : [
        "http://lafo.ssw.uni-linz.ac.at/graal-external-deps/dacapo-9.12-bach.jar",
        "http://softlayer.dl.sourceforge.net/project/dacapobench/9.12-bach/dacapo-9.12-bach.jar",
      ],
      "sha1" : "2626a9546df09009f6da0df854e6dc1113ef7dd4",
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
    },
  },

  "jrelibraries" : {
    "JFR" : {
      "jar" : "jfr.jar",
    }
  },

  "projects" : {

    # ------------- NFI -------------

    "com.oracle.nfi" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.7",
    },

    "com.oracle.nfi.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["test"],
      "dependencies" : [
        "com.oracle.nfi",
        "jvmci:JVMCI_API",
        "JUNIT",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.7",
    },

    # ------------- Graal -------------

    "com.oracle.graal.code" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "jvmci:JVMCI_SERVICE",
        "jvmci:JVMCI_API",
      ],
      "annotationProcessors" : ["jvmci:JDK_INTERNAL_JVMCI_SERVICE_PROCESSOR"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal",
    },

    "com.oracle.graal.api.collections" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "API,Graal",
    },

    "com.oracle.graal.api.directives" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "API,Graal",
    },

    "com.oracle.graal.api.directives.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "checkstyle" : "com.oracle.graal.graph",
      "dependencies" : [
        "com.oracle.graal.compiler.test",
      ],
      "javaCompliance" : "1.8",
      "workingSets" : "API,Graal",
    },

    "com.oracle.graal.api.runtime" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "jvmci:JVMCI_SERVICE",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "API,Graal",
    },

    "com.oracle.graal.api.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "JUNIT",
        "com.oracle.graal.api.runtime",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "API,Graal,Test",
    },

    "com.oracle.graal.api.replacements" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : ["jvmci:JVMCI_API"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "API,Graal,Replacements",
    },

    "com.oracle.graal.hotspot" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "jvmci:JVMCI_HOTSPOT",
        "com.oracle.graal.replacements",
        "com.oracle.graal.runtime",
        "com.oracle.graal.code",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "annotationProcessors" : [
        "com.oracle.graal.replacements.verifier",
        "jvmci:JDK_INTERNAL_JVMCI_SERVICE_PROCESSOR",
      ],
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,HotSpot",
    },

    "com.oracle.graal.hotspot.amd64" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "jvmci:JVMCI_HOTSPOT",
        "com.oracle.graal.compiler.amd64",
        "com.oracle.graal.hotspot",
        "com.oracle.graal.replacements.amd64",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "annotationProcessors" : ["jvmci:JDK_INTERNAL_JVMCI_SERVICE_PROCESSOR"],
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,HotSpot,AMD64",
    },

    "com.oracle.graal.hotspot.sparc" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.hotspot",
        "jvmci:JVMCI_HOTSPOT",
        "com.oracle.graal.compiler.sparc",
        "com.oracle.graal.replacements.sparc",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "annotationProcessors" : ["jvmci:JDK_INTERNAL_JVMCI_SERVICE_PROCESSOR", "com.oracle.graal.compiler.match.processor"],
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,HotSpot,SPARC",
    },

    "com.oracle.graal.hotspot.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.replacements.test",
        "com.oracle.graal.hotspot",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,HotSpot,Test",
    },

    "com.oracle.graal.hotspot.amd64.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.asm.amd64",
        "com.oracle.graal.hotspot.test",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,HotSpot,AMD64,Test",
    },

    "com.oracle.graal.nodeinfo" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Graph",
    },

    "com.oracle.graal.nodeinfo.processor" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "checkstyle" : "com.oracle.graal.graph",
      "dependencies" : [
        "com.oracle.graal.nodeinfo",
      ],
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Graph",
    },

    "com.oracle.graal.graph" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.nodeinfo",
        "com.oracle.graal.compiler.common",
        "com.oracle.graal.api.collections",
        "com.oracle.graal.api.runtime",
      ],
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["com.oracle.graal.nodeinfo.processor"],
      "workingSets" : "Graal,Graph",
    },

    "com.oracle.graal.graph.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "checkstyle" : "com.oracle.graal.graph",
      "dependencies" : [
        "JUNIT",
        "com.oracle.graal.graph",
      ],
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Graph,Test",
    },

    "com.oracle.graal.asm" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : ["jvmci:JVMCI_API"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Assembler",
    },

    "com.oracle.graal.asm.amd64" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.asm",
        "jvmci:JVMCI_HOTSPOT",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Assembler,AMD64",
    },

    "com.oracle.graal.asm.sparc" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.asm",
        "jvmci:JVMCI_HOTSPOT",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Assembler,SPARC",
    },

    "com.oracle.graal.bytecode" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Java",
    },

    "com.oracle.graal.asm.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.code",
        "com.oracle.graal.test",
        "jvmci:JVMCI_API",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Assembler,Test",
    },

    "com.oracle.graal.asm.amd64.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.asm.test",
        "jvmci:JVMCI_API",
        "com.oracle.graal.asm.amd64",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Assembler,AMD64,Test",
    },

    "com.oracle.graal.lir" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.compiler.common",
        "com.oracle.graal.asm",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,LIR",
    },

    "com.oracle.graal.lir.jtt" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.jtt",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,LIR",
    },

    "com.oracle.graal.lir.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "JUNIT",
        "com.oracle.graal.lir",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,LIR",
    },

    "com.oracle.graal.lir.amd64" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.lir",
        "com.oracle.graal.asm.amd64",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,LIR,AMD64",
    },

    "com.oracle.graal.lir.sparc" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.asm.sparc",
        "com.oracle.graal.lir",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,LIR,SPARC",
    },

    "com.oracle.graal.word" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.graal.nodes"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "API,Graal",
    },

    "com.oracle.graal.replacements" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.api.directives",
        "com.oracle.graal.java",
        "com.oracle.graal.loop",
        "com.oracle.graal.word",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "annotationProcessors" : [
        "com.oracle.graal.replacements.verifier",
        "jvmci:JDK_INTERNAL_JVMCI_SERVICE_PROCESSOR",
      ],
      "workingSets" : "Graal,Replacements",
    },

    "com.oracle.graal.replacements.amd64" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
          "com.oracle.graal.replacements",
          "com.oracle.graal.lir.amd64",
          "com.oracle.graal.compiler",
          ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["jvmci:JDK_INTERNAL_JVMCI_SERVICE_PROCESSOR"],
      "workingSets" : "Graal,Replacements,AMD64",
    },

    "com.oracle.graal.replacements.sparc" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
          "com.oracle.graal.replacements",
          "com.oracle.graal.compiler",
          ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Replacements,SPARC",
    },

    "com.oracle.graal.replacements.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.compiler.test",
        "com.oracle.graal.replacements",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Replacements,Test",
      "jacoco" : "exclude",
    },

    "com.oracle.graal.replacements.verifier" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.api.replacements",
        "com.oracle.graal.graph",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Replacements",
    },

    "com.oracle.graal.nodes" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.graph",
        "com.oracle.graal.api.replacements",
        "com.oracle.graal.lir",
        "com.oracle.graal.bytecode",
        "jvmci:JVMCI_HOTSPOT",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["com.oracle.graal.replacements.verifier"],
      "workingSets" : "Graal,Graph",
    },

    "com.oracle.graal.nodes.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.graal.compiler.test"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Graph",
    },

    "com.oracle.graal.phases" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.graal.nodes"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Phases",
    },

    "com.oracle.graal.phases.common" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.graal.phases"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Phases",
    },

    "com.oracle.graal.phases.common.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.runtime",
        "JUNIT",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Test",
    },

    "com.oracle.graal.virtual" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.graal.phases.common"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Phases",
    },

    "com.oracle.graal.virtual.bench" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : ["JMH"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Bench",
    },

    "com.oracle.graal.loop" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.graal.phases.common"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Phases",
    },

    "com.oracle.graal.compiler" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.virtual",
        "com.oracle.graal.loop",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["jvmci:JDK_INTERNAL_JVMCI_SERVICE_PROCESSOR"],
      "workingSets" : "Graal",
    },

    "com.oracle.graal.compiler.match.processor" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.compiler",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Codegen",
    },

    "com.oracle.graal.compiler.amd64" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.compiler",
        "com.oracle.graal.lir.amd64",
        "com.oracle.graal.java",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "annotationProcessors" : ["com.oracle.graal.compiler.match.processor"],
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,AMD64",
    },

    "com.oracle.graal.compiler.amd64.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "jvmci:JVMCI_HOTSPOT",
        "com.oracle.graal.lir.jtt",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,AMD64,Test",
    },

    "com.oracle.graal.compiler.sparc" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.compiler",
        "com.oracle.graal.lir.sparc"
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,SPARC",
    },

    "com.oracle.graal.compiler.sparc.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "jvmci:JVMCI_HOTSPOT",
        "com.oracle.graal.compiler.test",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,SPARC,Test",
    },

    "com.oracle.graal.runtime" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.graal.compiler"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal",
    },

    "com.oracle.graal.java" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.phases",
        "com.oracle.graal.graphbuilderconf",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "annotationProcessors" : ["jvmci:JDK_INTERNAL_JVMCI_SERVICE_PROCESSOR"],
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Java",
    },

    "com.oracle.graal.graphbuilderconf" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.nodes",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Java",
    },

    "com.oracle.graal.compiler.common" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "jvmci:JVMCI_API",
      ],
      "annotationProcessors" : ["jvmci:JDK_INTERNAL_JVMCI_OPTIONS_PROCESSOR"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Java",
    },

    "com.oracle.graal.printer" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.code",
        "com.oracle.graal.java",
        "com.oracle.graal.compiler",
      ],
      "annotationProcessors" : ["jvmci:JDK_INTERNAL_JVMCI_SERVICE_PROCESSOR"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Graph",
    },

    "com.oracle.graal.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "JUNIT",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Test",
    },

    "com.oracle.graal.compiler.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.api.directives",
        "com.oracle.graal.java",
        "com.oracle.graal.test",
        "com.oracle.graal.runtime",
        "com.oracle.graal.graph.test",
        "JAVA_ALLOCATION_INSTRUMENTER",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Test",
      "jacoco" : "exclude",
    },

    "com.oracle.graal.jtt" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.compiler.test",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Test",
      "jacoco" : "exclude",
    },

    # ------------- GraalTruffle -------------

    "com.oracle.graal.truffle" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "truffle:TRUFFLE",
        "com.oracle.graal.runtime",
        "com.oracle.graal.replacements",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Truffle",
      "jacoco" : "exclude",
    },

    "com.oracle.graal.truffle.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.truffle",
        "com.oracle.graal.compiler.test",
        "truffle:TRUFFLE_SL",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Truffle,Test",
      "jacoco" : "exclude",
    },

    "com.oracle.graal.truffle.hotspot" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.truffle",
        "com.oracle.graal.hotspot",
        "com.oracle.nfi",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["jvmci:JDK_INTERNAL_JVMCI_SERVICE_PROCESSOR"],
      "workingSets" : "Graal,Truffle",
    },

    "com.oracle.graal.truffle.hotspot.amd64" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.truffle.hotspot",
        "com.oracle.graal.hotspot.amd64",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["jvmci:JDK_INTERNAL_JVMCI_SERVICE_PROCESSOR"],
      "workingSets" : "Graal,Truffle",
    },

    "com.oracle.graal.truffle.hotspot.sparc" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.truffle.hotspot",
        "com.oracle.graal.asm.sparc",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["jvmci:JDK_INTERNAL_JVMCI_SERVICE_PROCESSOR"],
      "workingSets" : "Graal,Truffle,SPARC",
    }
  },

  "distributions" : {

    # ------------- Distributions -------------

    "GRAAL" : {
      "path" : "build/graal.jar",
      "subDir" : "graal",
      "sourcesPath" : "build/graal.src.zip",
      "dependencies" : [
        "com.oracle.graal.hotspot.amd64",
        "com.oracle.graal.hotspot.sparc",
        "com.oracle.graal.hotspot",
        "com.oracle.graal.printer",
      ],
      "distDependencies" : [
        "JVMCI_API",
        "JVMCI_SERVICE",
        "JVMCI_HOTSPOT",
      ],
    },

    "GRAAL_TRUFFLE" : {
      "path" : "build/graal-truffle.jar",
      "subDir" : "graal",
      "sourcesPath" : "build/graal-truffle.src.zip",
      "dependencies" : [
        "com.oracle.graal.truffle",
        "com.oracle.graal.truffle.hotspot.amd64",
        "com.oracle.graal.truffle.hotspot.sparc"
      ],
      "distDependencies" : [
        "GRAAL",
        "truffle:TRUFFLE",
      ],
    },

    "GRAAL_TRUFFLE_TEST" : {
      "path" : "build/graal-truffle-test.jar",
      "subDir" : "graal",
      "sourcesPath" : "build/graal-truffle-test.src.zip",
      "dependencies" : [
        "com.oracle.graal.truffle.test"
      ],
      "distDependencies" : [
        "GRAAL_TRUFFLE"
      ],
    },
  },
}
