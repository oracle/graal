suite = {
  "mxversion" : "5.176.2",
  "name" : "sulong",
  "versionConflictResolution" : "latest",

  "imports" : {
    "suites" : [
      {
        "name" : "truffle",
        "subdir" : True,
        "version" : "337a2747a1d750693853cc7c8f6ec2aaa2afc4ba",
        "urls" : [
          {"url" : "https://github.com/graalvm/graal", "kind" : "git"},
          {"url" : "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind" : "binary"},
        ]
      },
    ],
  },

  "javac.lint.overrides" : "none",

  "libraries" : {
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
    "ANTLR4": {
      "sha1" : "30b13b7efc55b7feea667691509cf59902375001",
      "maven" : {
        "groupId" : "org.antlr",
        "artifactId" : "antlr4-runtime",
        "version" : "4.7",
      }
    },
    "ANTLR4_COMPLETE": {
      "urls": [
        "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/antlr-4.7-complete.jar",
        "http://www.antlr.org/download/antlr-4.7-complete.jar"
      ],
      "sha1": "5b3a8824334069979a0862ce67ede796c3a4d1b1",
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
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
    },
    "com.oracle.truffle.llvm.test.native" : {
      "subDir" : "projects",
      "native" : True,
      "vpath" : True,
      "results" : [
        "bin/<lib:sulongtest>",
      ],
      "buildEnv" : {
        "LIBSULONGTEST" : "<lib:sulongtest>",
      },
      "license" : "BSD-new",
      "testProject" : True,
    },

    "com.oracle.truffle.llvm.types.test" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.llvm.runtime",
        "mx:JUNIT",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
    },

    "com.oracle.truffle.llvm.spi" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "truffle:TRUFFLE_API",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
    },

    "com.oracle.truffle.llvm.runtime" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "truffle:TRUFFLE_API",
        "truffle:TRUFFLE_NFI",
        "com.oracle.truffle.llvm.spi",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "checkstyleVersion" : "8.8",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
    },


    "com.oracle.truffle.llvm.nodes" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.llvm.runtime",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
    },

    "com.oracle.truffle.llvm.parser" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.llvm.runtime",
       ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
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
       ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
    },

    "com.oracle.truffle.llvm.launcher" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "sdk:LAUNCHER_COMMON",
       ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
    },

    "com.oracle.truffle.llvm.asm.amd64" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.llvm.nodes",
        "ANTLR4",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
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
        "com.oracle.truffle.llvm.parser",
       ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
    },

    "com.oracle.truffle.llvm.pipe" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "jniHeaders" : True,
      "javaProperties" : {
        "test.pipe.lib" : "<path:SULONG_TEST_NATIVE>/<lib:pipe>",
      },
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "javaCompliance" : "1.8",
      "license" : "BSD-new",
      "testProject" : True,
    },

    "com.oracle.truffle.llvm.pipe.native" : {
      "subDir" : "projects",
      "native" : True,
      "vpath" : True,
      "results" : [
        "bin/<lib:pipe>",
      ],
      "buildDependencies" : [
        "com.oracle.truffle.llvm.pipe",
      ],
      "buildEnv" : {
        "CPPFLAGS" : "-I<jnigen:com.oracle.truffle.llvm.pipe>",
        "LIBPIPE" : "<lib:pipe>",
        "OS" : "<os>",
      },
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "license" : "BSD-new",
      "testProject" : True,
    },
    "com.oracle.truffle.llvm.libraries.bitcode" : {
      "subDir" : "projects",
      "native" : True,
      "vpath" : True,
      "results" : [
        "bin/libsulong.bc",
        "bin/libsulong-src.tar.gz",
      ],
      "headers" : [
        "include/polyglot.h",
      ],
      "buildEnv" : {
        "CFLAGS" : "<clangImplicitArgs>",
      },
      "license" : "BSD-new",
    },
    "com.oracle.truffle.llvm.libraries.native" : {
      "subDir" : "projects",
      "native" : True,
      "vpath" : True,
      "results" : [
        "bin/<lib:sulong>",
      ],
      "buildDependencies" : [
        "truffle:TRUFFLE_NFI_NATIVE",
      ],
      "buildEnv" : {
        "LIBSULONG" : "<lib:sulong>",
        "CPPFLAGS" : "-I<path:truffle:TRUFFLE_NFI_NATIVE>/include",
        "OS" : "<os>",
      },
      "license" : "BSD-new",
    },
    "sulong-doc": {
      "class": "SulongDocsProject",
      "outputDir": "",
      "prefix": "",
    },

    "com.oracle.truffle.llvm.tests.debug" : {
      "subDir" : "tests",
      "class" : "SulongTestSuite",
      "variants" : ["O1", "O0", "O0_MEM2REG"],
      "buildRef" : False,
      "buildEnv" : {
        "CFLAGS" : "<clangImplicitArgs> -g",
        "CPPFLAGS" : "-I<sulong_include> -I<path:SULONG_LIBS> -g",
      },
      "buildDependencies" : [
        "SULONG_LIBS",
      ],
      "testProject" : True,
    },
    "com.oracle.truffle.llvm.tests.irdebug" : {
      "subDir" : "tests",
      "class" : "SulongTestSuite",
      "variants" : ["O0"],
      "buildRef" : False,
      "buildEnv" : {
        "CFLAGS" : "<clangImplicitArgs>",
        "CPPFLAGS" : "-I<sulong_include> -I<path:SULONG_LIBS>",
      },
      "buildDependencies" : [
        "SULONG_LIBS",
      ],
      "testProject" : True,
    },
    "com.oracle.truffle.llvm.tests.interop" : {
      "subDir" : "tests",
      "class" : "SulongTestSuite",
      "variants" : ["O0_MEM2REG"],
      "buildRef" : False,
      "buildEnv" : {
        "CPPFLAGS" : "-I<sulong_include> -I<path:SULONG_LIBS>",
      },
      "buildDependencies" : [
        "SULONG_LIBS",
      ],
      "testProject" : True,
    },
    "com.oracle.truffle.llvm.tests.nfi" : {
      "subDir" : "tests",
      "class" : "SulongTestSuite",
      "variants" : ["O0_MEM2REG"],
      "buildRef" : False,
      "buildEnv" : {
        "CPPFLAGS" : "-I<sulong_include> -I<path:SULONG_LIBS>",
      },
      "buildDependencies" : [
        "SULONG_LIBS",
      ],
      "testProject" : True,
    },
    "com.oracle.truffle.llvm.tests.sulong" : {
      "subDir" : "tests",
      "class" : "SulongTestSuite",
      "variants" : ["O0", "O0_MISC_OPTS", "O1", "O2", "O3", "gcc_O0"],
      "buildEnv" : {
        "LDFLAGS" : "-lm",
        "OS" : "<os>",
      },
      "testProject" : True,
    },
    "com.oracle.truffle.llvm.tests.sulongavx" : {
      "subDir" : "tests",
      "class" : "SulongTestSuite",
      "variants" : ["O1", "O2", "O3"],
      "buildEnv" : {
        "CFLAGS" : "-mavx2",
        "LDFLAGS" : "-lm",
        "OS" : "<os>",
      },
      "testProject" : True,
    },
    "com.oracle.truffle.llvm.tests.sulongcpp" : {
      "subDir" : "tests",
      "class" : "SulongTestSuite",
      "variants" : ["O0", "O0_MISC_OPTS"],
      "buildEnv" : {
        "OS" : "<os>",
      },
      "testProject" : True,
    },
    "com.oracle.truffle.llvm.tests.libc" : {
      "subDir" : "tests",
      "class" : "SulongTestSuite",
      "variants" : ["O0"],
      "buildEnv" : {
        "LDFLAGS" : "-lm",
        "OS" : "<os>",
      },
      "testProject" : True,
    },
  },

  "distributions" : {
    "SULONG" : {
      "subDir" : "projects",
      "dependencies" : ["com.oracle.truffle.llvm"],
      "distDependencies" : [
        "truffle:TRUFFLE_API",
        "truffle:TRUFFLE_NFI",
        "SULONG_LIBS",
      ],
      "javaProperties" : {
        "llvm.home": "<path:SULONG_LIBS>",
      },
      "license" : "BSD-new",
    },

    "SULONG_LAUNCHER" : {
      "subDir" : "projects",
      "mainClass" : "com.oracle.truffle.llvm.launcher.LLVMLauncher",
      "dependencies" : ["com.oracle.truffle.llvm.launcher"],
      "distDependencies" : [
        "sdk:LAUNCHER_COMMON",
      ],
      "license" : "BSD-new",
    },

    "SULONG_LIBS" : {
      "native" : True,
      "relpath" : False,
      "platformDependent" : True,
      "layout" : {
        "./" : [
          "dependency:com.oracle.truffle.llvm.libraries.bitcode/bin/libsulong.bc",
          "dependency:com.oracle.truffle.llvm.libraries.native/bin/*",
          "dependency:com.oracle.truffle.llvm.libraries.bitcode/include/*"
          ]
      },
      "dependencies" : [
        "com.oracle.truffle.llvm.libraries.bitcode",
        "com.oracle.truffle.llvm.libraries.native",
      ],
      "license" : "BSD-new",
    },

    "SULONG_LIB_SRC" : {
      "native" : True,
      "overlaps" : [
        "SULONG_LIBS"
      ],
      "layout" : {
        "./" : "dependency:com.oracle.truffle.llvm.libraries.bitcode/bin/libsulong-src.tar.gz",
      },
      "dependencies" : [
        "com.oracle.truffle.llvm.libraries.bitcode",
      ],
      "license" : "BSD-new",
    },

    "SULONG_TEST" : {
      "subDir" : "projects",
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
        "sulong:SULONG",
        "SULONG_TEST_NATIVE",
      ],
      "javaProperties" : {
        "sulongtest.testSuitePath" : "<path:SULONG_TEST_SUITES>"
      },
      "license" : "BSD-new",
    },

    "SULONG_TEST_NATIVE" : {
      "native" : True,
      "platformDependent" : True,
      "output" : "mxbuild/<os>-<arch>/sulong-test-native",
      "dependencies" : [
        "com.oracle.truffle.llvm.pipe.native",
        "com.oracle.truffle.llvm.test.native",
      ],
      "license" : "BSD-new",
      "testDistribution" : True,
    },

    "SULONG_TEST_SUITES" : {
      "native" : True,
      "relpath" : True,
      "platformDependent" : True,
      "output" : "mxbuild/<os>-<arch>/sulong-test-suites",
      "dependencies" : [
        "com.oracle.truffle.llvm.tests.debug",
        "com.oracle.truffle.llvm.tests.irdebug",
        "com.oracle.truffle.llvm.tests.interop",
        "com.oracle.truffle.llvm.tests.nfi",
        "com.oracle.truffle.llvm.tests.sulong",
        "com.oracle.truffle.llvm.tests.sulongavx",
        "com.oracle.truffle.llvm.tests.sulongcpp",
        "com.oracle.truffle.llvm.tests.libc",
      ],
      "license" : "BSD-new",
      "testDistribution" : True,
    },
    "SULONG_DOC": {
      "native": True, # Not Java
      "relpath": True,
      "dependencies": [
        "sulong-doc",
      ],
      "description": "Sulong documentation, license",
      "license" : "BSD-new",
    },
    "SULONG_GRAALVM_DOCS" : {
      "native" : True,
      "platformDependent" : True,
      "description" : "Sulong documentation files for the GraalVM",
      "layout" : {
        "./" : [
          "file:mx.sulong/native-image.properties",
          "file:README.md",
        ],
      },
      "license" : "BSD-new",
    },
  }
}
