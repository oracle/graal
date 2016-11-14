suite = {
  "mxversion" : "5.48.0",
  "name" : "graal-core",

  "imports" : {
    "suites": [
      {
        "name" : "truffle",
        # IMPORTANT: When updating the Truffle import, notify Truffle language developers
        # (by mail to graal-dev@openjdk.java.net) of the pending change.
        "version" : "ca21972635d350fcce90f1934d5882e144621d18",
        "urls" : [
          {"url" : "https://github.com/graalvm/truffle.git", "kind" : "git"},
          {"url" : "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind" : "binary"},
         ]
      },
    ]
  },

  "defaultLicense" : "GPLv2-CPE",

  "jdklibraries" : {
    "JVMCI_SERVICES" : {
      "path" : "lib/jvmci-services.jar",
      "sourcePath" : "lib/jvmci-services.src.zip",
      "optional" : False,
      "jdkStandardizedSince" : "9",
    },
    "JVMCI_API" : {
      "path" : "lib/jvmci/jvmci-api.jar",
      "sourcePath" : "lib/jvmci/jvmci-api.src.zip",
      "dependencies" : [
        "JVMCI_SERVICES",
      ],
      "optional" : False,
      "jdkStandardizedSince" : "9",
    },
    "JVMCI_HOTSPOT" : {
      "path" : "lib/jvmci/jvmci-hotspot.jar",
      "sourcePath" : "lib/jvmci/jvmci-hotspot.src.zip",
      "dependencies" : [
        "JVMCI_API",
      ],
      "optional" : False,
      "jdkStandardizedSince" : "9",
    },
  },

  "libraries" : {

    # ------------- Libraries -------------

    "DACAPO" : {
      "urls" : [
        "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/dacapo-9.12-bach.jar",
        "http://softlayer.dl.sourceforge.net/project/dacapobench/9.12-bach/dacapo-9.12-bach.jar",
      ],
      "sha1" : "2626a9546df09009f6da0df854e6dc1113ef7dd4",
    },

    "DACAPO_SCALA" : {
      "urls" : [
        "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/dacapo-scala-0.1.0-20120216.jar",
        "http://repo.scalabench.org/snapshots/org/scalabench/benchmarks/scala-benchmark-suite/0.1.0-SNAPSHOT/scala-benchmark-suite-0.1.0-20120216.103539-3.jar",
      ],
      "sha1" : "59b64c974662b5cf9dbd3cf9045d293853dd7a51",
    },

    "JAVA_ALLOCATION_INSTRUMENTER" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/java-allocation-instrumenter/java-allocation-instrumenter-8f0db117e64e.jar"],
      "sha1" : "476d9a44cd19d6b55f81571077dfa972a4f8a083",
      "bootClassPathAgent" : "true",
    },

    "HCFDIS" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/hcfdis-3.jar"],
      "sha1" : "a71247c6ddb90aad4abf7c77e501acc60674ef57",
    },

    "C1VISUALIZER_DIST" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/c1visualizer/c1visualizer-1.6.zip"],
      "sha1" : "5309b3fad46067846b9e2ea55933786cdbd6f6dd",
    },

    "IDEALGRAPHVISUALIZER_DIST" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/idealgraphvisualizer/idealgraphvisualizer-8820e1874bf7.zip"],
      "sha1" : "7eb51f6d643ed7833268b6971e273826d44c22b1",
    },

    "JOL_INTERNALS" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/truffle/jol/jol-internals.jar"],
      "sha1" : "508bcd26a4d7c4c44048990c6ea789a3b11a62dc",
    },

    "BATIK" : {
      "sha1" : "122b87ca88e41a415cf8b523fd3d03b4325134a3",
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/batik-all-1.7.jar"],
    },
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
        "JVMCI_API",
        "mx:JUNIT",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
    },

    # ------------- Graal -------------

    "com.oracle.graal.serviceprovider" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : ["JVMCI_SERVICES"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "API,Graal",
    },

    "com.oracle.graal.serviceprovider.processor" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.graal.serviceprovider"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Codegen",
    },

    "com.oracle.graal.options" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "checkstyle" : "com.oracle.graal.graph",
      "dependencies" : ["JVMCI_API"],
      "uses" : ["com.oracle.graal.options.OptionDescriptors"],
      "javaCompliance" : "1.8",
      "workingSets" : "Graal",
    },

    "com.oracle.graal.options.processor" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.options",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Codegen",
    },

    "com.oracle.graal.options.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.options",
        "mx:JUNIT",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal",
    },

    "com.oracle.graal.debug" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "checkstyle" : "com.oracle.graal.graph",
      "uses" : [
        "com.oracle.graal.debug.DebugConfigCustomizer",
        "com.oracle.graal.debug.DebugInitializationParticipant",
        "com.oracle.graal.debug.TTYStreamProvider",
      ],
      "dependencies" : [
        "JVMCI_API",
        "com.oracle.graal.serviceprovider",
        "com.oracle.graal.options"
      ],
      "annotationProcessors" : ["GRAAL_OPTIONS_PROCESSOR"],
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Debug",
    },

    "com.oracle.graal.debug.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JUNIT",
        "com.oracle.graal.debug",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Debug,Test",
    },

    "com.oracle.graal.code" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.graph",
      ],
      "annotationProcessors" : ["GRAAL_SERVICEPROVIDER_PROCESSOR"],
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
        "JVMCI_HOTSPOT",
      ],
      "javaCompliance" : "1.8",
      "workingSets" : "API,Graal",
    },

    "com.oracle.graal.api.runtime" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "JVMCI_API",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "API,Graal",
    },

    "com.oracle.graal.api.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JUNIT",
        "JVMCI_SERVICES",
        "com.oracle.graal.api.runtime",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "API,Graal,Test",
    },

    "com.oracle.graal.api.replacements" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : ["JVMCI_API"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "API,Graal,Replacements",
    },

    "com.oracle.graal.hotspot" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "JVMCI_HOTSPOT",
        "com.oracle.graal.api.runtime",
        "com.oracle.graal.replacements",
        "com.oracle.graal.runtime",
      ],
      "imports" : [
        # All other internal packages are exported dynamically -
        # see com.oracle.graal.hotspot.HotSpotGraalJVMCIAccess.
        "jdk.internal.module",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "annotationProcessors" : [
        "GRAAL_NODEINFO_PROCESSOR",
        "GRAAL_COMPILER_MATCH_PROCESSOR",
        "GRAAL_REPLACEMENTS_VERIFIER",
        "GRAAL_OPTIONS_PROCESSOR",
        "GRAAL_SERVICEPROVIDER_PROCESSOR",
      ],
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,HotSpot",
    },

    "com.oracle.graal.hotspot.aarch64" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.compiler.aarch64",
        "com.oracle.graal.hotspot",
        "com.oracle.graal.replacements.aarch64",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "annotationProcessors" : [
        "GRAAL_SERVICEPROVIDER_PROCESSOR",
        "GRAAL_NODEINFO_PROCESSOR"
      ],
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,HotSpot,AArch64",
    },

    "com.oracle.graal.hotspot.amd64" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.compiler.amd64",
        "com.oracle.graal.hotspot",
        "com.oracle.graal.replacements.amd64",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "annotationProcessors" : [
        "GRAAL_SERVICEPROVIDER_PROCESSOR",
        "GRAAL_NODEINFO_PROCESSOR"
      ],
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,HotSpot,AMD64",
    },

    "com.oracle.graal.hotspot.sparc" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.hotspot",
        "com.oracle.graal.compiler.sparc",
        "com.oracle.graal.replacements.sparc",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "annotationProcessors" : ["GRAAL_SERVICEPROVIDER_PROCESSOR"],
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
      "imports" : [
        "jdk.internal.reflect",
        "jdk.internal.org.objectweb.asm",
      ],
      "annotationProcessors" : ["GRAAL_NODEINFO_PROCESSOR"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,HotSpot,Test",
    },

    "com.oracle.graal.hotspot.lir.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.hotspot",
        "com.oracle.graal.lir.jtt",
        "com.oracle.graal.lir.test",
        "JVMCI_API",
        "JVMCI_HOTSPOT",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,HotSpot,Test",
    },

    "com.oracle.graal.hotspot.aarch64.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.asm.aarch64",
        "com.oracle.graal.hotspot.test",
      ],
      "annotationProcessors" : ["GRAAL_NODEINFO_PROCESSOR"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,HotSpot,AArch64,Test",
    },

    "com.oracle.graal.hotspot.amd64.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.asm.amd64",
        "com.oracle.graal.hotspot.test",
        "com.oracle.graal.lir.amd64",
        "com.oracle.graal.lir.jtt",
      ],
      "annotationProcessors" : ["GRAAL_NODEINFO_PROCESSOR"],
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
      ],
      "javaCompliance" : "1.8",
      "annotationProcessors" : [
        "GRAAL_OPTIONS_PROCESSOR",
        "GRAAL_NODEINFO_PROCESSOR"
      ],
      "workingSets" : "Graal,Graph",
    },

    "com.oracle.graal.graph.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "checkstyle" : "com.oracle.graal.graph",
      "dependencies" : [
        "mx:JUNIT",
        "com.oracle.graal.api.test",
        "com.oracle.graal.graph",
      ],
      "annotationProcessors" : ["GRAAL_NODEINFO_PROCESSOR"],
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Graph,Test",
    },

    "com.oracle.graal.asm" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : ["JVMCI_API"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Assembler",
    },

    "com.oracle.graal.asm.aarch64" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.debug",
        "com.oracle.graal.asm",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Assembler,AArch64",
    },

    "com.oracle.graal.asm.amd64" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.asm",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Assembler,AMD64",
    },

    "com.oracle.graal.asm.sparc" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.debug",
        "com.oracle.graal.asm",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Assembler,SPARC",
    },

    "com.oracle.graal.asm.sparc.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.asm.test",
        "com.oracle.graal.asm.sparc",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Assembler,SPARC,Test",
    },

    "com.oracle.graal.bytecode" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : ["JVMCI_API"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Java",
    },

    "com.oracle.graal.asm.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.api.test",
        "com.oracle.graal.code",
        "com.oracle.graal.runtime",
        "com.oracle.graal.test",
        "com.oracle.graal.debug",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Assembler,Test",
    },

    "com.oracle.graal.asm.aarch64.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.asm.test",
        "com.oracle.graal.asm.aarch64",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Assembler,AArch64,Test",
    },

    "com.oracle.graal.asm.amd64.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.asm.test",
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
        "com.oracle.graal.asm",
        "com.oracle.graal.code",
      ],
      "annotationProcessors" : ["GRAAL_OPTIONS_PROCESSOR"],
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
      "annotationProcessors" : ["GRAAL_NODEINFO_PROCESSOR"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,LIR",
      "findbugs" : "false",
    },

    "com.oracle.graal.lir.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JUNIT",
        "com.oracle.graal.lir",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,LIR",
    },

    "com.oracle.graal.lir.aarch64" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.lir",
        "com.oracle.graal.asm.aarch64",
      ],
      "annotationProcessors" : ["GRAAL_OPTIONS_PROCESSOR"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,LIR,AArch64",
    },

    "com.oracle.graal.lir.amd64" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.lir",
        "com.oracle.graal.asm.amd64",
      ],
      "annotationProcessors" : ["GRAAL_OPTIONS_PROCESSOR"],
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
      "annotationProcessors" : ["GRAAL_NODEINFO_PROCESSOR"],
      "workingSets" : "API,Graal",
    },

    "com.oracle.graal.replacements" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.api.directives",
        "com.oracle.graal.java",
        "com.oracle.graal.loop.phases",
        "com.oracle.graal.word",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "annotationProcessors" : [
        "GRAAL_OPTIONS_PROCESSOR",
        "GRAAL_REPLACEMENTS_VERIFIER",
        "GRAAL_NODEINFO_PROCESSOR",
      ],
      "workingSets" : "Graal,Replacements",
    },

    "com.oracle.graal.replacements.aarch64" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.replacements",
        "com.oracle.graal.lir.aarch64",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "annotationProcessors" : [
        "GRAAL_NODEINFO_PROCESSOR",
        "GRAAL_REPLACEMENTS_VERIFIER",
      ],
      "workingSets" : "Graal,Replacements,AArch64",
    },

    "com.oracle.graal.replacements.amd64" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.replacements",
        "com.oracle.graal.lir.amd64",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "annotationProcessors" : [
        "GRAAL_NODEINFO_PROCESSOR",
        "GRAAL_REPLACEMENTS_VERIFIER",
      ],
      "workingSets" : "Graal,Replacements,AMD64",
    },

    "com.oracle.graal.replacements.sparc" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.replacements",
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
      "annotationProcessors" : [
        "GRAAL_NODEINFO_PROCESSOR",
        "GRAAL_REPLACEMENTS_VERIFIER"
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
        "com.oracle.graal.api.replacements",
        "com.oracle.graal.bytecode",
        "com.oracle.graal.lir",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "annotationProcessors" : [
        "GRAAL_NODEINFO_PROCESSOR",
        "GRAAL_REPLACEMENTS_VERIFIER",
      ],
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
      "annotationProcessors" : ["GRAAL_OPTIONS_PROCESSOR"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Phases",
    },

    "com.oracle.graal.phases.common" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.graal.phases"],
      "annotationProcessors" : [
        "GRAAL_NODEINFO_PROCESSOR",
        "GRAAL_OPTIONS_PROCESSOR"
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Phases",
    },

    "com.oracle.graal.phases.common.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.api.test",
        "com.oracle.graal.runtime",
        "mx:JUNIT",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Test",
    },

    "com.oracle.graal.virtual" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.graal.phases.common"],
      "annotationProcessors" : [
        "GRAAL_OPTIONS_PROCESSOR",
        "GRAAL_NODEINFO_PROCESSOR"
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Phases",
    },

    "com.oracle.graal.virtual.bench" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : ["mx:JMH", "com.oracle.graal.microbenchmarks"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["mx:JMH"],
      "workingSets" : "Graal,Bench",
    },

    "com.oracle.graal.microbenchmarks" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JMH",
        "com.oracle.graal.api.test",
        "com.oracle.graal.java",
        "com.oracle.graal.runtime",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "checkPackagePrefix" : "false",
      "annotationProcessors" : ["mx:JMH"],
      "workingSets" : "Graal,Bench",
    },

    "com.oracle.graal.loop" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.graal.nodes"],
      "annotationProcessors" : ["GRAAL_OPTIONS_PROCESSOR"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal",
    },

    "com.oracle.graal.loop.phases" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
     "com.oracle.graal.loop",
     "com.oracle.graal.phases.common",
       ],
      "annotationProcessors" : ["GRAAL_OPTIONS_PROCESSOR"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Phases",
    },

    "com.oracle.graal.compiler" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.virtual",
        "com.oracle.graal.loop.phases",
      ],
      "uses" : ["com.oracle.graal.compiler.match.MatchStatementSet"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "annotationProcessors" : [
        "GRAAL_SERVICEPROVIDER_PROCESSOR",
        "GRAAL_OPTIONS_PROCESSOR",
      ],
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

    "com.oracle.graal.compiler.aarch64" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.compiler",
        "com.oracle.graal.lir.aarch64",
        "com.oracle.graal.java",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "annotationProcessors" : [
        "GRAAL_NODEINFO_PROCESSOR",
        "GRAAL_COMPILER_MATCH_PROCESSOR",
      ],
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,AArch64",
    },

    "com.oracle.graal.compiler.aarch64.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.lir.jtt",
        "com.oracle.graal.lir.aarch64",
        "JVMCI_HOTSPOT"
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,AArch64,Test",
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
      "annotationProcessors" : [
        "GRAAL_NODEINFO_PROCESSOR",
        "GRAAL_COMPILER_MATCH_PROCESSOR",
      ],
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,AMD64",
    },

    "com.oracle.graal.compiler.amd64.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.lir.jtt",
        "com.oracle.graal.lir.amd64",
        "JVMCI_HOTSPOT"
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
        "com.oracle.graal.lir.sparc",
        "com.oracle.graal.java"
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "annotationProcessors" : [
        "GRAAL_NODEINFO_PROCESSOR",
        "GRAAL_COMPILER_MATCH_PROCESSOR",
      ],
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,SPARC",
    },

    "com.oracle.graal.compiler.sparc.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.lir.jtt",
        "JVMCI_HOTSPOT"
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
      ],
      "annotationProcessors" : ["GRAAL_OPTIONS_PROCESSOR"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Java",
    },

    "com.oracle.graal.compiler.common" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.debug",
      ],
      "annotationProcessors" : ["GRAAL_OPTIONS_PROCESSOR"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Java",
    },

    "com.oracle.graal.printer" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.compiler",
        "com.oracle.graal.java",
      ],
      "uses" : ["com.oracle.graal.code.DisassemblerProvider"],
      "annotationProcessors" : [
        "GRAAL_OPTIONS_PROCESSOR",
        "GRAAL_SERVICEPROVIDER_PROCESSOR"
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Graph",
    },

    "com.oracle.graal.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JUNIT",
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
        "com.oracle.graal.printer",
        "JAVA_ALLOCATION_INSTRUMENTER",
      ],
      "uses" : ["com.oracle.graal.options.OptionDescriptors"],
      "annotationProcessors" : ["GRAAL_NODEINFO_PROCESSOR"],
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
      "findbugs" : "false",
    },

    # ------------- GraalTruffle -------------

    "com.oracle.graal.truffle" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "truffle:TRUFFLE_API",
        "com.oracle.graal.api.runtime",
        "com.oracle.graal.runtime",
        "com.oracle.graal.replacements",
      ],
      "uses" : [
        "com.oracle.truffle.api.object.LayoutFactory",
        "com.oracle.graal.truffle.LoopNodeFactory",
        "com.oracle.graal.truffle.substitutions.TruffleInvocationPluginProvider",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "annotationProcessors" : [
        "GRAAL_NODEINFO_PROCESSOR",
        "GRAAL_REPLACEMENTS_VERIFIER",
        "GRAAL_OPTIONS_PROCESSOR",
        "GRAAL_SERVICEPROVIDER_PROCESSOR",
        "truffle:TRUFFLE_DSL_PROCESSOR",
      ],
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Truffle",
      "jacoco" : "exclude",
    },

    "com.oracle.graal.truffle.bench" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JMH",
        "truffle:TRUFFLE_API",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "annotationProcessors" : [
        "mx:JMH",
      ],
      "workingSets" : "Graal,Truffle,Bench",
    },

    "com.oracle.graal.truffle.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.truffle",
        "com.oracle.graal.compiler.test",
        "truffle:TRUFFLE_SL_TEST",
      ],
      "annotationProcessors" : [
        "GRAAL_NODEINFO_PROCESSOR",
        "truffle:TRUFFLE_DSL_PROCESSOR"
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
      "uses" : [
        "com.oracle.graal.hotspot.HotSpotBackendFactory",
        "com.oracle.graal.nodes.graphbuilderconf.NodeIntrinsicPluginFactory",
        "com.oracle.graal.truffle.hotspot.OptimizedCallTargetInstrumentationFactory",
        "com.oracle.graal.truffle.hotspot.nfi.RawNativeCallNodeFactory",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "annotationProcessors" : [
        "GRAAL_OPTIONS_PROCESSOR",
        "GRAAL_SERVICEPROVIDER_PROCESSOR"
      ],
      "workingSets" : "Graal,Truffle",
    },

    "com.oracle.graal.truffle.hotspot.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.truffle.hotspot",
        "com.oracle.graal.truffle.test",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Truffle,Test",
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
      "annotationProcessors" : [
        "GRAAL_SERVICEPROVIDER_PROCESSOR",
      ],
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
      "annotationProcessors" : ["GRAAL_SERVICEPROVIDER_PROCESSOR"],
      "workingSets" : "Graal,Truffle,SPARC",
    },

    # ------------- Salver -------------

    "com.oracle.graal.salver" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.graal.phases"],
      "annotationProcessors" : [
        "GRAAL_OPTIONS_PROCESSOR",
        "GRAAL_SERVICEPROVIDER_PROCESSOR",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal",
    },
  },

  "distributions" : {

    # ------------- Distributions -------------

    "GRAAL_OPTIONS" : {
      "subDir" : "graal",
      "dependencies" : ["com.oracle.graal.options"],
      "distDependencies" : [
        "JVMCI_API",
      ],
    },

    "GRAAL_OPTIONS_PROCESSOR" : {
      "subDir" : "graal",
      "dependencies" : ["com.oracle.graal.options.processor"],
      "distDependencies" : [
        "GRAAL_OPTIONS",
      ],
    },

    "GRAAL_NODEINFO" : {
      "subDir" : "graal",
      "dependencies" : [
        "com.oracle.graal.nodeinfo",
      ],
    },

    "GRAAL_SERVICEPROVIDER" : {
      "subDir" : "graal",
      "dependencies" : ["com.oracle.graal.serviceprovider"],
      "distDependencies" : [
        "GRAAL_NODEINFO",
        "JVMCI_SERVICES"
      ],
    },

    "GRAAL_API" : {
      "subDir" : "graal",
      "dependencies" : [
        "com.oracle.graal.api.replacements",
        "com.oracle.graal.api.runtime",
        "com.oracle.graal.graph",
      ],
      "distDependencies" : [
        "JVMCI_API",
        "GRAAL_NODEINFO",
        "GRAAL_OPTIONS",
        "GRAAL_SERVICEPROVIDER",
      ],
    },

    "GRAAL_COMPILER" : {
      "subDir" : "graal",
      "dependencies" : [
        "com.oracle.graal.compiler",
      ],
      "distDependencies" : [
        "GRAAL_API",
        "GRAAL_SERVICEPROVIDER",
      ],
    },

    "GRAAL_RUNTIME" : {
      "subDir" : "graal",
      "dependencies" : [
        "com.oracle.graal.replacements",
        "com.oracle.graal.runtime",
        "com.oracle.graal.code",
        "com.oracle.graal.printer",
        "com.oracle.graal.compiler.aarch64",
        "com.oracle.graal.replacements.aarch64",
        "com.oracle.graal.compiler.amd64",
        "com.oracle.graal.replacements.amd64",
        "com.oracle.graal.compiler.sparc",
        "com.oracle.graal.replacements.sparc",
        "com.oracle.graal.salver",
      ],
      "distDependencies" : [
        "GRAAL_API",
        "GRAAL_COMPILER",
      ],
    },

    "GRAAL_HOTSPOT" : {
      "subDir" : "graal",
      "dependencies" : [
        "com.oracle.graal.hotspot.aarch64",
        "com.oracle.graal.hotspot.amd64",
        "com.oracle.graal.hotspot.sparc",
        "com.oracle.graal.hotspot",
      ],
      "distDependencies" : [
        "JVMCI_HOTSPOT",
        "GRAAL_COMPILER",
        "GRAAL_RUNTIME",
      ],
    },

    "GRAAL_TEST" : {
      "subDir" : "graal",
      "dependencies" : [
        "com.oracle.graal.api.test",
        "com.oracle.graal.api.directives.test",
        "com.oracle.graal.asm.sparc.test",
        "com.oracle.graal.asm.aarch64.test",
        "com.oracle.graal.asm.amd64.test",
        "com.oracle.graal.compiler.aarch64.test",
        "com.oracle.graal.compiler.amd64.test",
        "com.oracle.graal.compiler.sparc.test",
        "com.oracle.graal.debug.test",
        "com.oracle.graal.hotspot.aarch64.test",
        "com.oracle.graal.hotspot.amd64.test",
        "com.oracle.graal.hotspot.lir.test",
        "com.oracle.graal.options.test",
        "com.oracle.graal.jtt",
        "com.oracle.graal.lir.jtt",
        "com.oracle.graal.lir.test",
        "com.oracle.graal.nodes.test",
        "com.oracle.graal.phases.common.test",
        "com.oracle.graal.truffle.test",
        "com.oracle.graal.truffle.hotspot.test",
        "com.oracle.nfi.test",
      ],
      "distDependencies" : [
        "GRAAL_HOTSPOT",
        "JVMCI_HOTSPOT",
        "GRAAL_TRUFFLE",
        "GRAAL_TRUFFLE_HOTSPOT",
        "truffle:TRUFFLE_SL_TEST",
      ],
      "exclude" : [
        "mx:JUNIT",
        "JAVA_ALLOCATION_INSTRUMENTER",
      ],
    },

    "GRAAL_TRUFFLE" : {
      "subDir" : "graal",
      "dependencies" : [
        "com.oracle.graal.truffle",
      ],
      "distDependencies" : [
        "GRAAL_RUNTIME",
        "truffle:TRUFFLE_API",
      ],
    },

    "GRAAL_TRUFFLE_HOTSPOT" : {
      "subDir" : "graal",
      "dependencies" : [
        "com.oracle.graal.truffle.hotspot.amd64",
        "com.oracle.graal.truffle.hotspot.sparc"
      ],
      "distDependencies" : [
        "GRAAL_HOTSPOT",
        "GRAAL_TRUFFLE",
        "truffle:TRUFFLE_API",
      ],
    },

    "GRAAL_SERVICEPROVIDER_PROCESSOR" : {
      "subDir" : "graal",
      "dependencies" : ["com.oracle.graal.serviceprovider.processor"],
      "distDependencies" : [
        "GRAAL_SERVICEPROVIDER",
      ],
    },

    "GRAAL_NODEINFO_PROCESSOR" : {
      "subDir" : "graal",
      "dependencies" : ["com.oracle.graal.nodeinfo.processor"],
      "distDependencies" : [
        "GRAAL_NODEINFO",
      ],
    },

    "GRAAL_REPLACEMENTS_VERIFIER" : {
      "subDir" : "graal",
      "dependencies" : ["com.oracle.graal.replacements.verifier"],
      "distDependencies" : [
        "GRAAL_API",
        "GRAAL_SERVICEPROVIDER",
        "GRAAL_SERVICEPROVIDER_PROCESSOR",
      ]
    },

    "GRAAL_COMPILER_MATCH_PROCESSOR" : {
      "subDir" : "graal",
      "dependencies" : ["com.oracle.graal.compiler.match.processor"],
      "distDependencies" : [
        "GRAAL_COMPILER",
        "GRAAL_SERVICEPROVIDER_PROCESSOR",
      ]
    },

    "GRAAL" : {
      # This distribution defines a module.
      "moduleName" : "com.oracle.graal.graal_core",
      "subDir" : "graal",
      "overlaps" : [
        "GRAAL_OPTIONS",
        "GRAAL_NODEINFO",
        "GRAAL_API",
        "GRAAL_COMPILER",
        "GRAAL_RUNTIME",
        "GRAAL_HOTSPOT",
        "GRAAL_SERVICEPROVIDER",
        "GRAAL_TRUFFLE",
        "GRAAL_TRUFFLE_HOTSPOT",
      ],
      "dependencies" : [
        "com.oracle.graal.options",
        "com.oracle.graal.nodeinfo",
        "com.oracle.graal.api.replacements",
        "com.oracle.graal.api.runtime",
        "com.oracle.graal.graph",
        "com.oracle.graal.compiler",
        "com.oracle.graal.replacements",
        "com.oracle.graal.runtime",
        "com.oracle.graal.code",
        "com.oracle.graal.printer",
        "com.oracle.graal.compiler.aarch64",
        "com.oracle.graal.replacements.aarch64",
        "com.oracle.graal.compiler.amd64",
        "com.oracle.graal.replacements.amd64",
        "com.oracle.graal.compiler.sparc",
        "com.oracle.graal.replacements.sparc",
        "com.oracle.graal.salver",
        "com.oracle.graal.hotspot.aarch64",
        "com.oracle.graal.hotspot.amd64",
        "com.oracle.graal.hotspot.sparc",
        "com.oracle.graal.hotspot",
        "com.oracle.graal.truffle",
        "com.oracle.graal.truffle.hotspot.amd64",
        "com.oracle.graal.truffle.hotspot.sparc"
      ],
      "distDependencies" : [
        "truffle:TRUFFLE_API",
      ],
      "exclude" : [
        "JVMCI_SERVICES",
        "JVMCI_API",
        "JVMCI_HOTSPOT",
      ],
    },
  },
}
