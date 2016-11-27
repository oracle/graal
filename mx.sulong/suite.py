suite = {
  "mxversion" : "5.55.0",
  "name" : "sulong",
  "versionConflictResolution" : "latest",

  "imports" : {
    "suites" : [
        {
           "name" : "graal-core",
           "version" : "ca165d0f0de274911d1b36b0113e3fd4c6952787",
           "urls" : [
                {"url" : "https://github.com/graalvm/graal-core", "kind" : "git"},
                {"url" : "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind" : "binary"},
            ]
        },
        {
               "name" : "truffle",
               "version" : "ca21972635d350fcce90f1934d5882e144621d18",
               "urls" : [
                    {"url" : "https://github.com/graalvm/truffle", "kind" : "git"},
                    {"url" : "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind" : "binary"},
                ]
        },
    ],
  },

  "javac.lint.overrides" : "none",

  "libraries" : {
    "ARGON2" : {
      "path" : "tests/phc-winner-argon2-20160406.tar.gz",
      "urls" : [
        "https://lafo.ssw.uni-linz.ac.at/pub/sulong-deps/20160406.tar.gz",
        "https://github.com/P-H-C/phc-winner-argon2/archive/20160406.tar.gz",
      ],
      "sha1" : "5552052e53fcd7fe40c558866c9cd51027c17322",
    },
    "LLVM_TEST_SUITE" : {
      "path" : "tests/test-suite-3.2.src.tar.gz",
      "urls" : [
        "https://lafo.ssw.uni-linz.ac.at/pub/sulong-deps/test-suite-3.2.src.tar.gz",
        "http://llvm.org/releases/3.2/test-suite-3.2.src.tar.gz",
      ],
      "sha1" : "e370255ca2540bcd66f316fe5b96f459382f3e8a",
    },
    "GCC_SOURCE" : {
      "path" : "tests/gcc-5.2.0.tar.gz",
      "urls" : [
        "https://lafo.ssw.uni-linz.ac.at/pub/sulong-deps/gcc-5.2.0.tar.gz",
        "http://gd.tuwien.ac.at/gnu/gcc/releases/gcc-5.2.0/gcc-5.2.0.tar.gz",
        "ftp://ftp.fu-berlin.de/unix/languages/gcc/releases/gcc-5.2.0/gcc-5.2.0.tar.gz",
        "http://mirrors-usa.go-parts.com/gcc/releases/gcc-5.2.0/gcc-5.2.0.tar.gz",
      ],
      "sha1" : "713211883406b3839bdba4a22e7111a0cff5d09b",
    },
    "SHOOTOUT_SUITE" : {
      "path" : "tests/benchmarksgame-scm-latest.tar.gz",
      "urls" : [
        "https://lafo.ssw.uni-linz.ac.at/pub/sulong-deps/benchmarksgame-scm-latest.tar.gz",
      ],
      "sha1" : "9684ca5aaa38ff078811f9b42f15ee65cdd259fc",
    },
    "NWCC_SUITE" : {
      "path" : "tests/nwcc_0.8.3.tar.gz",
      "urls" : [
        "https://lafo.ssw.uni-linz.ac.at/pub/sulong-deps/nwcc_0.8.3.tar.gz",
      ],
      "sha1" : "2ab1825dc1f8bd5258204bab19e8fafad93fef26",
    },
    "LTA_REF" : {
      "path" : "tests/lifetime-analysis-ref.tar.gz",
      "urls" : [
        "https://lafo.ssw.uni-linz.ac.at/pub/sulong-deps/lifetime-analysis-ref.tar.gz",
      ],
      "sha1" : "8bb0cd644b0dc9ec2f3000ad9cac50e9432d4e17",
    },
  },

  "projects" : {

    "com.oracle.truffle.llvm.test" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.llvm",
        "com.oracle.truffle.llvm.pipe",
        "truffle:TRUFFLE_TCK",
        "mx:JUNIT",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.test",
      "annotationProcessors" : ["SULONG_OPTIONS"],
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
    },

    "com.oracle.truffle.llvm.bench" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.llvm",
      ],
      "checkstyle" : "com.oracle.truffle.llvm",
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
    },

    "com.oracle.truffle.llvm.types" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.llvm.runtime",
        "truffle:TRUFFLE_API",
        "graal-core:GRAAL_TRUFFLE_HOTSPOT",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.nodes",
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
    },

    "com.oracle.truffle.llvm.types.test" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.llvm.types",
        "mx:JUNIT",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.test",
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
    },

    "com.oracle.truffle.llvm.runtime" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "truffle:TRUFFLE_API",
        "com.oracle.truffle.llvm.option"
      ],
      "checkstyle" : "com.oracle.truffle.llvm.nodes",
      "annotationProcessors" : ["SULONG_OPTIONS"],
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
    },

    "com.oracle.truffle.llvm.nodes" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "truffle:TRUFFLE_API",
        "com.oracle.truffle.llvm.types"
      ],
      "checkstyle" : "com.oracle.truffle.llvm.nodes",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
    },

    "com.oracle.truffle.llvm.nodes.impl" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.llvm.context",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.nodes",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
    },

    "com.oracle.truffle.llvm.parser" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.llvm.types",
       ],
      "checkstyle" : "com.oracle.truffle.llvm",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
    },

    "com.oracle.truffle.llvm.parser.base" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.llvm.parser",
        "com.oracle.truffle.llvm.nodes",
        ],
      "checkstyle" : "com.oracle.truffle.llvm",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
    },

    "com.oracle.truffle.llvm" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.llvm.parser.factories",
        "com.oracle.truffle.llvm.parser.bc.impl",
       ],
      "checkstyle" : "com.oracle.truffle.llvm",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
    },

    "com.oracle.truffle.llvm.asm.amd64" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.llvm.nodes.impl",
       ],
      "checkstyle" : "com.oracle.truffle.llvm",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
    },

    "com.oracle.truffle.llvm.parser.factories" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.llvm.asm.amd64",
       ],
      "checkstyle" : "com.oracle.truffle.llvm",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
    },

    "com.oracle.truffle.llvm.context" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "graal-core:GRAAL_TRUFFLE_HOTSPOT",
        "com.oracle.truffle.llvm.parser.base"
       ],
      "checkstyle" : "com.oracle.truffle.llvm",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
    },

    "com.oracle.truffle.llvm.parser.bc.impl" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.llvm.context",
       ],
      "checkstyle" : "com.oracle.truffle.llvm",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
    },

    "com.oracle.truffle.llvm.pipe" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.llvm.runtime",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.test",
      "javaCompliance" : "1.8",
      "license" : "BSD-new",
    },

    "com.oracle.truffle.llvm.pipe.native" : {
      "subDir" : "projects",
      "native" : True,
      "vpath" : True,
      "results" : [
        "bin/libpipe.so"
      ],
      "dependencies" : [
        "com.oracle.truffle.llvm.pipe",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.test",
      "license" : "BSD-new",
    },

    "com.oracle.truffle.llvm.option" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "checkstyle" : "com.oracle.truffle.llvm",
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
    },

    "com.oracle.truffle.llvm.option.processor" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.llvm.option",
      ],
      "checkstyle" : "com.oracle.truffle.llvm",
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
    },
  },

  "distributions" : {
    "SULONG" : {
      "path" : "build/sulong.jar",
      "subDir" : "graal",
      "sourcesPath" : "build/sulong.src.zip",
      "mainClass" : "com.oracle.truffle.llvm.LLVM",
      "dependencies" : ["com.oracle.truffle.llvm"],
      "distDependencies" : [
        "truffle:TRUFFLE_API",
        "SULONG_OPTIONS",
        "graal-core:GRAAL_API",
        "graal-core:GRAAL_COMPILER",
        "graal-core:GRAAL_HOTSPOT",
        "graal-core:GRAAL_TRUFFLE_HOTSPOT",
      ]
    },

    "SULONG_OPTIONS" : {
      "path" : "build/sulong_options.jar",
      "subDir" : "graal",
      "javaCompliance" : "1.8",
      "dependencies" : ["com.oracle.truffle.llvm.option.processor"],
      "description" : "The Sulong Option Processor generates an option class declared using options annotations.",
    },

    "SULONG_TEST" : {
      "path" : "build/sulong_test.jar",
      "subDir" : "graal",
      "sourcesPath" : "build/sulong_test.src.zip",
      "dependencies" : [
        "com.oracle.truffle.llvm.test",
        "com.oracle.truffle.llvm.types.test",
        "com.oracle.truffle.llvm.pipe"
      ],
      "exclude" : [
       "mx:JUNIT"
      ],
      "distDependencies" : [
        "truffle:TRUFFLE_API",
        "truffle:TRUFFLE_TCK",
        "graal-core:GRAAL_API",
        "graal-core:GRAAL_COMPILER",
        "graal-core:GRAAL_HOTSPOT",
        "graal-core:GRAAL_TRUFFLE_HOTSPOT",
        "sulong:SULONG"
      ]
    },
 }
}
