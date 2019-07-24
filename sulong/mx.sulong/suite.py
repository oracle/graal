suite = {
  "mxversion" : "5.223.0",
  "name" : "sulong",
  "versionConflictResolution" : "latest",

  "imports" : {
    "suites" : [
      {
        "name" : "truffle",
        "subdir" : True,
        "urls" : [
          {"url" : "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind" : "binary"},
        ]
      },
    ],
  },

  "javac.lint.overrides" : "none",

  "libraries" : {
    "LLVM_TEST_SUITE" : {
      "packedResource" : True,
      "urls" : [
        "https://lafo.ssw.uni-linz.ac.at/pub/sulong-deps/test-suite-3.2.src.tar.gz",
        "https://llvm.org/releases/3.2/test-suite-3.2.src.tar.gz",
      ],
      "sha1" : "e370255ca2540bcd66f316fe5b96f459382f3e8a",
    },
    "LLVM_ORG" : {
      "os_arch" : {
        "linux" : {
          "amd64" : {
            "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/llvm-org/llvm-llvmorg-8.0.0-4-gd563e33a79-bgae3b177eaa-linux-amd64.tar.gz"],
            "sha1" : "df4c1f784294d02a82d78664064248283bfcc297",
          }
        },
        "darwin" : {
          "amd64" : {
            "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/llvm-org/llvm-llvmorg-8.0.0-4-gd563e33a79-bgae3b177eaa-darwin-amd64.tar.gz"],
            "sha1" : "0fa1af180755fa4cc018ee9be33f2d7d827593c4",
          }
        },
      }
    },
    "LLVM_ORG_COMPILER_RT_LINUX" : {
      # we really want linux, also on non-linux platforms for cross-compilation
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/llvm-org/compiler-rt-llvmorg-8.0.0-4-gd563e33a79-bgae3b177eaa-linux-amd64.tar.gz"],
      "sha1" : "5001adab652fc4eb35e30cdefbb0765442f8b7db",
    },
    "LLVM_ORG_LIBCXX_SRC" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/llvm-org/libcxx-src-llvmorg-8.0.0-4-gd563e33a79-bgae3b177eaa.tar.gz"],
      "sha1" : "0cec15ffc7487f87743f2d258e277f2b48795c33",
    },
    "GCC_SOURCE" : {
      "packedResource" : True,
      "urls" : [
        "https://lafo.ssw.uni-linz.ac.at/pub/sulong-deps/gcc-5.2.0.tar.gz",
        "https://mirrors-usa.go-parts.com/gcc/releases/gcc-5.2.0/gcc-5.2.0.tar.gz",
      ],
      "sha1" : "713211883406b3839bdba4a22e7111a0cff5d09b",
    },
    "SHOOTOUT_SUITE" : {
      "packedResource" : True,
      "urls" : [
        "https://lafo.ssw.uni-linz.ac.at/pub/sulong-deps/benchmarksgame-scm-latest.tar.gz",
      ],
      "sha1" : "9684ca5aaa38ff078811f9b42f15ee65cdd259fc",
    },
    "NWCC_SUITE" : {
      "packedResource" : True,
      "urls" : [
        "https://lafo.ssw.uni-linz.ac.at/pub/sulong-deps/nwcc_0.8.3.tar.gz",
      ],
      "sha1" : "2ab1825dc1f8bd5258204bab19e8fafad93fef26",
    },
  },

  "projects" : {

    "com.oracle.truffle.llvm.tests" : {
      "subDir" : "tests",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.llvm",
        "com.oracle.truffle.llvm.tests.pipe",
        "truffle:TRUFFLE_TCK",
        "mx:JUNIT",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "javaCompliance" : "1.8",
      "javaProperties" : {
        "test.sulongtest.lib" : "<path:SULONG_TEST_NATIVE>/<lib:sulongtest>",
        "test.sulongtest.lib.path" : "<path:SULONG_TEST_NATIVE>",
      },
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
      "testProject" : True,
      "jacoco" : "exclude",
    },
    "com.oracle.truffle.llvm.tests.native" : {
      "subDir" : "tests",
      "native" : True,
      "vpath" : True,
      "results" : [
        "bin/<lib:sulongtest>",
      ],
      "buildDependencies" : [
        "SULONG_LLVM_ORG",
      ],
      "buildEnv" : {
        "LIBSULONGTEST" : "<lib:sulongtest>",
        "CLANG" : "<path:SULONG_LLVM_ORG>/bin/clang",
        "OS" : "<os>",
      },
      "license" : "BSD-new",
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.types" : {
      "subDir" : "tests",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.llvm.runtime",
        "mx:JUNIT",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
      "testProject" : True,
      "jacoco" : "exclude",
    },
    "com.oracle.truffle.llvm.api" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "truffle:TRUFFLE_API",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
      "jacoco" : "include",
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
      "jacoco" : "include",
    },

    "com.oracle.truffle.llvm.nfi" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "truffle:TRUFFLE_API",
        "truffle:TRUFFLE_NFI",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
      "jacoco" : "include",
    },

    "com.oracle.truffle.llvm.runtime" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "truffle:TRUFFLE_API",
        "com.oracle.truffle.llvm.api",
        "com.oracle.truffle.llvm.spi",
        "com.oracle.truffle.llvm.instruments",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "checkstyleVersion" : "8.8",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
      "jacoco" : "include",
    },

    "com.oracle.truffle.llvm.instruments" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "truffle:TRUFFLE_API"
      ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
      "jacoco" : "include",
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
      "jacoco" : "include",
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
      "jacoco" : "include",
    },

    "com.oracle.truffle.llvm" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.llvm.parser.factories",
        "SULONG_API",
       ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "javaCompliance" : "1.8",
      "javaProperties" : {
        "llvm.toolchainRoot" : "<path:SULONG_BOOTSTRAP_TOOLCHAIN>",
      },
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
      "jacoco" : "include",
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
      "jacoco" : "include",
    },

    "com.oracle.truffle.llvm.tools" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.llvm.parser",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
      "jacoco" : "exclude",
    },

    "com.oracle.truffle.llvm.toolchain.launchers" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "sdk:LAUNCHER_COMMON",
      ],
      "javaProperties" : {
        "llvm.bin.dir" : "<path:SULONG_LLVM_ORG>/bin",
        "llvm.home": "<path:SULONG_LIBS>",
      },
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
      "jacoco" : "include",
    },

    "bootstrap-toolchain-launchers": {
      "subDir": "projects",
      "class" : "ToolchainLauncherProject",
      "native": True,
      "vpath": True,
      "platformDependent": False,
      "buildEnv" : {
        "MX_EXE" : "<mx_exe>",
      },
      "buildDependencies" : [
        "SULONG_LLVM_ORG",
      ],
      "license" : "BSD-new",
    },

    "toolchain-launchers-tests": {
      "subDir": "tests",
      "native": True,
      "vpath": True,
      "platformDependent": False,
      "max_jobs": "1",
      "buildEnv" : {
        "SULONG_EXE" : "<mx_exe> lli",
        "CLANG": "<toolchainGetToolPath:native,CC>",
        "CLANGXX": "<toolchainGetToolPath:native,CXX>",
        "OS": "<os>",
        "JACOCO": "<jacoco>",
      },
      "buildDependencies" : [
        "SULONG",
        "SULONG_LAUNCHER",
        "SULONG_TOOLCHAIN_LAUNCHERS",
        "SULONG_BOOTSTRAP_TOOLCHAIN",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },

    "com.oracle.truffle.llvm.asm.amd64" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.llvm.nodes",
        "truffle:ANTLR4",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
      "jacoco" : "include",
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
      "jacoco" : "include",
    },

    "com.oracle.truffle.llvm.tests.pipe" : {
      "subDir" : "tests",
      "sourceDirs" : ["src"],
      "jniHeaders" : True,
      "javaProperties" : {
        "test.pipe.lib" : "<path:SULONG_TEST_NATIVE>/<lib:pipe>",
      },
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "javaCompliance" : "1.8",
      "license" : "BSD-new",
      "testProject" : True,
      "defaultBuild" : False,
      "jacoco" : "exclude",
    },

    "com.oracle.truffle.llvm.tests.pipe.native" : {
      "subDir" : "tests",
      "native" : True,
      "vpath" : True,
      "results" : [
        "bin/<lib:pipe>",
      ],
      "buildDependencies" : [
        "com.oracle.truffle.llvm.tests.pipe",
      ],
      "buildEnv" : {
        "CPPFLAGS" : "-I<jnigen:com.oracle.truffle.llvm.tests.pipe>",
        "LIBPIPE" : "<lib:pipe>",
        "OS" : "<os>",
      },
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "license" : "BSD-new",
      "testProject" : True,
      "defaultBuild" : False,
      "jacoco" : "exclude",
    },
    "com.oracle.truffle.llvm.libraries.bitcode" : {
      "subDir" : "projects",
      "native" : True,
      "vpath" : True,
      "results" : [
        "bin/libsulong.bc",
        "bin/libsulong++.bc",
        "bin/libsulong-src.tar.gz",
      ],
      "headers" : [
        "include/polyglot.h",
      ],
      "buildDependencies" : [
        "SULONG_LLVM_ORG",
      ],
      "buildEnv" : {
        "CFLAGS" : "-Xclang -disable-O0-optnone",
        "CLANG" : "<path:SULONG_LLVM_ORG>/bin/clang",
        "CLANGXX" : "<path:SULONG_LLVM_ORG>/bin/clang++",
        "OPT" : "<path:SULONG_LLVM_ORG>/bin/opt",
        "LLVM_LINK" : "<path:SULONG_LLVM_ORG>/bin/llvm-link",
        "OS" : "<os>",
      },
      "license" : "BSD-new",
    },
    "com.oracle.truffle.llvm.libraries.native" : {
      "subDir" : "projects",
      "native" : True,
      "vpath" : True,
      "results" : [
        "bin/<lib:sulong>",
        "bin/<lib:polyglot-mock>",
      ],
      "buildDependencies" : [
        "truffle:TRUFFLE_NFI_NATIVE",
        "com.oracle.truffle.llvm.libraries.bitcode",
        "SULONG_LLVM_ORG",
      ],
      "buildEnv" : {
        "CLANG" : "<path:SULONG_LLVM_ORG>/bin/clang",
        "LIBSULONG" : "<lib:sulong>",
        "LIBPOLYGLOT" : "<lib:polyglot-mock>",
        "CPPFLAGS" : "-I<path:truffle:TRUFFLE_NFI_NATIVE>/include -I<path:com.oracle.truffle.llvm.libraries.bitcode>/include",
        "OS" : "<os>",
      },
      "license" : "BSD-new",
    },
    "sulong-doc": {
      "class": "SulongDocsProject",
      "outputDir": "",
      "prefix": "",
    },

    "com.oracle.truffle.llvm.tests.debug.native" : {
      "subDir" : "tests",
      "class" : "SulongTestSuite",
      "variants" : ["O1", "O0", "O0_MEM2REG"],
      "buildRef" : False,
      "buildEnv" : {
        "SUITE_CFLAGS" : "<clangImplicitArgs> -g",
        "SUITE_CPPFLAGS" : "-I<path:SULONG_LEGACY>/include -I<path:SULONG_HOME>/include -g",
      },
      "dependencies" : [
        "SULONG_TEST",
      ],
      "buildDependencies" : [
        "SULONG_HOME",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.irdebug.native" : {
      "subDir" : "tests",
      "class" : "SulongTestSuite",
      "variants" : ["O0"],
      "buildRef" : False,
      "buildEnv" : {
        "SUITE_CFLAGS" : "<clangImplicitArgs>",
        "SUITE_CPPFLAGS" : "-I<path:SULONG_LEGACY>/include -I<path:SULONG_HOME>/include -g",
      },
      "dependencies" : [
        "SULONG_TEST",
      ],
      "buildDependencies" : [
        "SULONG_HOME",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.interop.native" : {
      "subDir" : "tests",
      "class" : "SulongTestSuite",
      "variants" : ["O0_MEM2REG"],
      "buildRef" : False,
      "buildSharedObject" : True,
      "buildEnv" : {
        "SUITE_CPPFLAGS" : "-I<path:SULONG_LEGACY>/include -I<path:SULONG_HOME>/include -g",
        "OS" : "<os>",
      },
      "os_arch" : {
        "darwin": {
          "<others>" : {
            "buildEnv" : {
              "SUITE_LDFLAGS" : "-lpolyglot-mock -L<path:SULONG_HOME>/native/lib -lsulongtest -L<path:SULONG_TEST_NATIVE>",
            },
          },
        },
        "<others>": {
          "<others>": {
            "buildEnv" : {
              "SUITE_LDFLAGS" : "--no-undefined -lpolyglot-mock -L<path:SULONG_HOME>/native/lib -Wl,--undefined=callbackPointerArgTest -lsulongtest -L<path:SULONG_TEST_NATIVE>",
            },
          },
        },
      },
      "dependencies" : [
        "SULONG_TEST",
      ],
      "buildDependencies" : [
        "SULONG_HOME",
        "SULONG_TEST_NATIVE",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.other.native" : {
      "subDir" : "tests",
      "class" : "SulongTestSuite",
      "variants" : ["O0_MEM2REG"],
      "buildRef" : False,
      "buildEnv" : {
        "SUITE_CPPFLAGS" : "-I<path:SULONG_LEGACY>/include -I<path:SULONG_HOME>/include -g",
      },
      "dependencies" : [
        "SULONG_TEST",
      ],
      "buildDependencies" : [
        "SULONG_HOME",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.sulong.native" : {
      "subDir" : "tests",
      "class" : "SulongTestSuite",
      "variants" : ["O0", "O0_MISC_OPTS", "O1", "O2", "O3", "gcc_O0"],
      "buildEnv" : {
        "SUITE_LDFLAGS" : "-lm",
        "OS" : "<os>",
      },
      "dependencies" : [
        "SULONG_TEST",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.bitcode.native" : {
      "subDir" : "tests",
      "class" : "SulongTestSuite",
      "variants" : ["O0"],
      "buildEnv" : {
        "OS" : "<os>",
      },
      "dependencies" : [
        "SULONG_TEST",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.sulongavx.native" : {
      "subDir" : "tests",
      "class" : "SulongTestSuite",
      "variants" : ["O1", "O2", "O3"],
      "buildEnv" : {
        "SUITE_CFLAGS" : "-mavx2",
        "SUITE_LDFLAGS" : "-lm",
        "OS" : "<os>",
      },
      "dependencies" : [
        "SULONG_TEST",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.sulongcpp.native" : {
      "subDir" : "tests",
      "class" : "SulongTestSuite",
      "variants" : ["O0", "O0_MISC_OPTS", "O1"],
      "buildEnv" : {
        "OS" : "<os>",
      },
      "dependencies" : [
        "SULONG_TEST",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.libc.native" : {
      "subDir" : "tests",
      "class" : "SulongTestSuite",
      "variants" : ["O0"],
      "buildEnv" : {
        "SUITE_LDFLAGS" : "-lm",
        "OS" : "<os>",
      },
      "dependencies" : [
        "SULONG_TEST",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "inlineassemblytests" : {
      "subDir" : "tests",
      "class" : "SulongTestSuite",
      "variants" : ["O0"],
      "buildEnv" : {
        "SUITE_CPPFLAGS" : "-I<path:SULONG_LEGACY>/include",
      },
      "dependencies" : [
        "SULONG_TEST_SUITES",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "other" : {
      "subDir" : "tests",
      "class" : "SulongTestSuite",
      "variants" : ["O1"],
      "buildRef" : False,
      "buildEnv" : {
        "SUITE_CPPFLAGS" : "-I<path:SULONG_LEGACY>/include -lm",
      },
      "dependencies" : [
        "SULONG_TEST",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.bitcodeformat.native": {
      "subDir": "tests",
      "native": True,
      "vpath": True,
      "results": [
        "bitcodeformat/hello-linux-emit-llvm.bc",
        "bitcodeformat/hello-linux-compile-fembed-bitcode.o",
        "bitcodeformat/hello-linux-link-fembed-bitcode",
        "bitcodeformat/hello-linux-link-fembed-bitcode.so",
        "bitcodeformat/hello-darwin-emit-llvm.bc",
        "bitcodeformat/hello-darwin-compile-fembed-bitcode.o",
        "bitcodeformat/hello-darwin-link-fembed-bitcode",
        "bitcodeformat/hello-darwin-link-fembed-bitcode.dylib",
      ],
      "buildEnv": {
        "SUITE_CFLAGS": "<clangImplicitArgs>",
        "SUITE_CPPFLAGS": "-I<path:SULONG_LEGACY>/include -I<path:SULONG_HOME>/include",
      },
      "dependencies": [
        "SULONG_TEST",
      ],
      "buildDependencies": [
        "SULONG_HOME",
      ],
      "testProject": True,
      "defaultBuild": False,
    },
    "com.oracle.truffle.llvm.tests.linker.native" : {
      "class" : "GlobNativeProject",
      "subDir" : "tests",
      "native": True,
      "vpath": True,
      "buildEnv" : {
        "OS" : "<os>",
        "CLANG": "<toolchainGetToolPath:native,CC>",
      },
      "dependencies" : [
        "SULONG_TEST",
        "SULONG_TOOLCHAIN_LAUNCHERS",
        "SULONG_BOOTSTRAP_TOOLCHAIN",
      ],
      "results" : [
        "linker/ref.out",
        "linker/ref.bc",
        "linker/lib/<libsuffix:*>",
        "rpath/lib/<libsuffix:*>",
        "rpath/<libsuffix:*>",
        "rpath/not-inherited/ref.out",
        "rpath/not-inherited/ref.bc",
        "rpath/main-override/ref.out",
        "rpath/main-override/ref.bc",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "gcc_c" : {
      "subDir" : "tests/gcc",
      "class" : "ExternalTestSuite",
      "testDir" : "gcc-5.2.0/gcc/testsuite",
      "fileExts" : [".c"],
      "native" : True,
      "vpath" : True,
      "variants" : ["O0"],
      "buildRef" : True,
      "buildEnv" : {
        "SUITE_CPPFLAGS" : "-I<path:SULONG_LEGACY>/include",
      },
      "dependencies" : [
        "SULONG_TEST",
      ],
      "buildDependencies" : [
        "GCC_SOURCE",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "gcc_cpp" : {
      "subDir" : "tests/gcc",
      "class" : "ExternalTestSuite",
      "testDir" : "gcc-5.2.0/gcc/testsuite",
      "fileExts" : [".cpp", ".C", ".cc"],
      "native" : True,
      "vpath" : True,
      "variants" : ["O0"],
      "buildRef" : True,
      "buildEnv" : {
        "SUITE_CPPFLAGS" : "-I<path:SULONG_LEGACY>/include",
      },
      "dependencies" : [
        "SULONG_TEST",
      ],
      "buildDependencies" : [
        "GCC_SOURCE",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "gcc_fortran" : {
      "subDir" : "tests/gcc",
      "class" : "ExternalTestSuite",
      "testDir" : "gcc-5.2.0/gcc/testsuite",
      "fileExts" : [".f90", ".f", ".f03"],
      "requireDragonegg" : True,
      "native" : True,
      "vpath" : True,
      "variants" : ["O0"],
      "extraLibs" : ["libgfortran.so.3"],
      "single_job" : True, # problem with parallel builds and temporary module files
      "buildRef" : True,
      "buildEnv" : {
        "SUITE_CPPFLAGS" : "-I<path:SULONG_LEGACY>/include",
      },
      "dependencies" : [
        "SULONG_TEST",
      ],
      "buildDependencies" : [
        "GCC_SOURCE",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "parserTorture" : {
      "subDir" : "tests/gcc",
      "class" : "ExternalTestSuite",
      "testClasses" : ["com.oracle.truffle.llvm.tests.ParserTortureSuite"],
      "testDir" : "gcc-5.2.0/gcc/testsuite/gcc.c-torture/compile",
      "configDir" : "configs/gcc.c-torture/compile",
      "fileExts" : [".c"],
      "native" : True,
      "vpath" : True,
      "variants" : ["O0"],
      "buildRef" : False,
      "buildEnv" : {
        "SUITE_CPPFLAGS" : "-I<path:SULONG_LEGACY>/include",
      },
      "dependencies" : [
        "SULONG_TEST",
      ],
      "buildDependencies" : [
        "GCC_SOURCE",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "llvm" : {
      "subDir" : "tests/llvm",
      "class" : "ExternalTestSuite",
      "testClasses" : ["com.oracle.truffle.llvm.tests.LLVMSuite"],
      "testDir" : "test-suite-3.2.src",
      "fileExts" : [".c", ".cpp", ".C", ".cc", ".m"],
      "native" : True,
      "vpath" : True,
      "variants" : ["O0"],
      "buildRef" : True,
      "buildEnv" : {
        "SUITE_CPPFLAGS" : "-I<path:SULONG_LEGACY>/include",
      },
      "dependencies" : [
        "SULONG_TEST",
      ],
      "buildDependencies" : [
        "LLVM_TEST_SUITE",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "shootout" : {
      "subDir" : "tests/benchmarksgame",
      "class" : "ExternalTestSuite",
      "testClasses" : ["com.oracle.truffle.llvm.tests.ShootoutsSuite"],
      "testDir" : "benchmarksgame-2014-08-31/benchmarksgame/bench/",
      "fileExts" : [".c", ".cpp", ".C", ".cc", ".m", ".gcc", ".cint", ".gpp"],
      "native" : True,
      "vpath" : True,
      "variants" : ["O1"],
      "extraLibs" : ["libgmp.so.10"],
      "buildRef" : True,
      "buildEnv" : {
        "SUITE_CPPFLAGS" : "-I<path:SULONG_LEGACY>/include",
        "SUITE_LDFLAGS" : "-lm -lgmp",
      },
      "dependencies" : [
        "SULONG_TEST",
      ],
      "buildDependencies" : [
        "SHOOTOUT_SUITE",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "nwcc" : {
      "subDir" : "tests/nwcc",
      "class" : "ExternalTestSuite",
      "testClasses" : ["com.oracle.truffle.llvm.tests.NWCCSuite"],
      "testDir" : "nwcc_0.8.3",
      "fileExts" : [".c"],
      "native" : True,
      "vpath" : True,
      "variants" : ["O0"],
      "buildRef" : True,
      "buildEnv" : {
        "SUITE_CPPFLAGS" : "-I<path:SULONG_LEGACY>/include",
      },
      "dependencies" : [
        "SULONG_TEST",
      ],
      "buildDependencies" : [
        "NWCC_SUITE",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
  },

  "distributions" : {
    "SULONG" : {
      "subDir" : "projects",
      "dependencies" : [
        "com.oracle.truffle.llvm",
      ],
      "distDependencies" : [
        "truffle:TRUFFLE_API",
        "truffle:ANTLR4",
        "SULONG_HOME",
        "SULONG_API",
      ],
      "javaProperties" : {
        "llvm.home": "<sulong_home>",
      },
      "license" : "BSD-new",
    },

    "SULONG_API" : {
      "subDir" : "projects",
      "dependencies" : [
        "com.oracle.truffle.llvm.api",
      ],
      "distDependencies" : [
        "truffle:TRUFFLE_API",
      ],
      "license" : "BSD-new",
    },
    "SULONG_NFI" : {
      "subDir" : "projects",
      "dependencies" : [
        "com.oracle.truffle.llvm.nfi",
      ],
      "distDependencies" : [
        "truffle:TRUFFLE_NFI",
        "SULONG",
      ],
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

    # legacy - to be removed
    "SULONG_LIBS" : {
      "native" : True,
      "relpath" : False,
      "platformDependent" : True,
      "overlaps" : [
        "SULONG_HOME"
      ],
      "layout" : {
        "./" : [
          "dependency:com.oracle.truffle.llvm.libraries.bitcode/bin/libsulong.bc",
          "dependency:com.oracle.truffle.llvm.libraries.bitcode/bin/libsulong++.bc",
          "dependency:com.oracle.truffle.llvm.libraries.native/bin/*",
          "dependency:com.oracle.truffle.llvm.libraries.bitcode/include/*"
          ],
        "./native/lib/" : [
          "dependency:com.oracle.truffle.llvm.libraries.bitcode/bin/libsulong.bc",
          "dependency:com.oracle.truffle.llvm.libraries.bitcode/bin/libsulong++.bc",
          "dependency:com.oracle.truffle.llvm.libraries.native/bin/*",
        ],
        "./include/" : [
          "dependency:com.oracle.truffle.llvm.libraries.bitcode/include/*"
        ],
      },
      "dependencies" : [
        "com.oracle.truffle.llvm.libraries.bitcode",
        "com.oracle.truffle.llvm.libraries.native",
      ],
      "license" : "BSD-new",
    },

    "SULONG_HOME" : {
      "native" : True,
      "relpath" : False,
      "platformDependent" : True,
      "layout" : {
        "./native/lib/" : [
          "dependency:com.oracle.truffle.llvm.libraries.bitcode/bin/libsulong.bc",
          "dependency:com.oracle.truffle.llvm.libraries.bitcode/bin/libsulong++.bc",
          "dependency:com.oracle.truffle.llvm.libraries.native/bin/*",
        ],
        "./include/" : [
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
        "SULONG_HOME"
      ],
      "layout" : {
        "./" : "dependency:com.oracle.truffle.llvm.libraries.bitcode/bin/libsulong-src.tar.gz",
      },
      "dependencies" : [
        "com.oracle.truffle.llvm.libraries.bitcode",
      ],
      "license" : "BSD-new",
    },

    "SULONG_TOOLCHAIN_LAUNCHERS": {
      "subDir" : "projects",
      "dependencies" : ["com.oracle.truffle.llvm.toolchain.launchers"],
      "distDependencies" : [
        "sdk:LAUNCHER_COMMON",
      ],
      "license" : "BSD-new",
    },

    "SULONG_BOOTSTRAP_TOOLCHAIN": {
      "native": True,
      "relpath": False,
      "platformDependent": False,
      "layout": {
        "./": "dependency:bootstrap-toolchain-launchers/*",
      },
      "dependencies": [
        "bootstrap-toolchain-launchers",
      ],
      "distDependencies" : [
        "SULONG_TOOLCHAIN_LAUNCHERS",
      ],
      "license": "BSD-new",
    },


    "SULONG_TEST" : {
      "subDir" : "tests",
      "dependencies" : [
        "com.oracle.truffle.llvm.tests",
        "com.oracle.truffle.llvm.tests.types",
        "com.oracle.truffle.llvm.tests.pipe"
      ],
      "exclude" : [
       "mx:JUNIT"
      ],
      "distDependencies" : [
        "truffle:TRUFFLE_API",
        "truffle:TRUFFLE_TCK",
        "sulong:SULONG",
        "sulong:SULONG_NFI",
        "sulong:SULONG_LEGACY",
        "SULONG_TEST_NATIVE",
      ],
      "javaProperties" : {
        "sulongtest.testSuitePath" : "<path:SULONG_TEST_SUITES>"
      },
      "license" : "BSD-new",
      "testDistribution" : True,
    },

    "SULONG_TEST_NATIVE" : {
      "native" : True,
      "platformDependent" : True,
      "output" : "mxbuild/<os>-<arch>/sulong-test-native",
      "dependencies" : [
        "com.oracle.truffle.llvm.tests.pipe.native",
        "com.oracle.truffle.llvm.tests.native",
      ],
      "license" : "BSD-new",
      "testDistribution" : True,
      "defaultBuild" : False,
    },

    "SULONG_TEST_SUITES" : {
      "native" : True,
      "relpath" : True,
      "platformDependent" : True,
      "output" : "mxbuild/<os>-<arch>/sulong-test-suites",
      "dependencies" : [
        "com.oracle.truffle.llvm.tests.bitcode.native",
        "com.oracle.truffle.llvm.tests.bitcodeformat.native",
        "com.oracle.truffle.llvm.tests.debug.native",
        "com.oracle.truffle.llvm.tests.irdebug.native",
        "com.oracle.truffle.llvm.tests.interop.native",
        "com.oracle.truffle.llvm.tests.other.native",
        "com.oracle.truffle.llvm.tests.sulong.native",
        "com.oracle.truffle.llvm.tests.sulongavx.native",
        "com.oracle.truffle.llvm.tests.sulongcpp.native",
        "com.oracle.truffle.llvm.tests.libc.native",
        "com.oracle.truffle.llvm.tests.linker.native",
      ],
      "license" : "BSD-new",
      "testDistribution" : True,
      "defaultBuild" : False,
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
    "SULONG_LEGACY" : {
      "native" : True,
      "layout" : {
        "./include/" : [
          "file:include/truffle.h",
        ],
      },
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
    "SULONG_LLVM_ORG": {
      "native": True,
      "description": "LLVM with general purpose patches used by Sulong",
      "layout": {
        "./": [
          "extracted-dependency:LLVM_ORG",
          "extracted-dependency:LLVM_ORG_COMPILER_RT_LINUX",
          "file:3rd_party_license_llvm-toolchain.txt",
        ],
        "./patches/" : [
          "file:patches/*"
        ],
      },
      "maven": False,
      "license" : "NCSA",
    },
  }
}
