suite = {
  "mxversion" : "5.239.0",
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
    "GCC_SOURCE" : {
      "packedResource" : True,
      # original: https://mirrors-usa.go-parts.com/gcc/releases/gcc-5.2.0/gcc-5.2.0.tar.gz
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/sulong-deps/gcc-5.2.0.tar.gz"],
      "sha1" : "713211883406b3839bdba4a22e7111a0cff5d09b",
    },
    "SHOOTOUT_SUITE" : {
      "packedResource" : True,
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/sulong-deps/benchmarksgame-scm-latest.tar.gz"],
      "sha1" : "9684ca5aaa38ff078811f9b42f15ee65cdd259fc",
    },
    "NWCC_SUITE" : {
      "packedResource" : True,
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/sulong-deps/nwcc_0.8.3.tar.gz"],
      "sha1" : "2ab1825dc1f8bd5258204bab19e8fafad93fef26",
    },
    # Support Libraries.
    # Projects depending on these will *not be built* if the 'optional' is 'True' for the given OS/architecture.
    # This is a dummy library for dragonegg support.
    "DRAGONEGG_SUPPORT" : {
      "os_arch" : {
        "linux" : {
          "amd64" : {
            "path": "tests/support.txt",
            "sha1": "81177e981eeb52730854e3d763e96015881c3bab",
          },
          "<others>": {
            "optional": True,
          }
        },
        "<others>": {
          "<others>": {
            "optional": True,
          }
        },
      },
    },
    # This is a dummy library for malloc.h support.
    "MALLOC_H_SUPPORT" : {
      "os_arch" : {
        "linux" : {
          "amd64" : {
            "path": "tests/support.txt",
            "sha1": "81177e981eeb52730854e3d763e96015881c3bab",
          },
          "<others>": {
            "optional": True,
          }
        },
        "<others>": {
          "<others>": {
            "optional": True,
          }
        },
      },
    },
    # This is a dummy library for alias() support.
    "ALIAS_SUPPORT" : {
      "os_arch" : {
        "linux" : {
          "amd64" : {
            "path": "tests/support.txt",
            "sha1": "81177e981eeb52730854e3d763e96015881c3bab",
          },
          "<others>": {
            "optional": True,
          }
        },
        "<others>": {
          "<others>": {
            "optional": True,
          }
        },
      },
    },
    # This is a dummy library for linux amd64 support.
    "LINUX_AMD64_SUPPORT" : {
      "os_arch" : {
        "linux" : {
          "amd64" : {
            "path": "tests/support.txt",
            "sha1": "81177e981eeb52730854e3d763e96015881c3bab",
          },
          "<others>": {
            "optional": True,
          }
        },
        "<others>": {
          "<others>": {
            "optional": True,
          }
        },
      },
    },
    # This is a dummy library for amd64 support.
    "AMD64_SUPPORT" : {
      "os_arch" : {
        "<others>" : {
          "amd64" : {
            "path": "tests/support.txt",
            "sha1": "81177e981eeb52730854e3d763e96015881c3bab",
          },
          "<others>": {
            "optional": True,
          }
        },
      },
    },
  },

  "projects" : {

    "com.oracle.truffle.llvm.docs" : {
      "class" : "DocumentationProject",
      "subDir" : "docs",
      "dir" : "docs",
      "sourceDirs" : ["src"],
      "license" : "BSD-new",
      "defaultBuild" : False,
    },
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
      "javaCompliance" : "1.8+",
      "javaProperties" : {
        "test.sulongtest.lib" : "<path:SULONG_TEST_NATIVE>/<lib:sulongtest>",
        "test.sulongtest.lib.path" : "<path:SULONG_TEST_NATIVE>",
        "sulongtest.projectRoot" : "<path:com.oracle.truffle.llvm>/../",
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
        "sdk:LLVM_TOOLCHAIN",
      ],
      "buildEnv" : {
        "LIBSULONGTEST" : "<lib:sulongtest>",
        "CLANG" : "<path:LLVM_TOOLCHAIN>/bin/clang",
        "OS" : "<os>",
      },
      "license" : "BSD-new",
      "testProject" : True,
    },
    "com.oracle.truffle.llvm.tests.types" : {
      "subDir" : "tests",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.llvm.runtime",
        "mx:JUNIT",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "javaCompliance" : "1.8+",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
      "testProject" : True,
      "jacoco" : "exclude",
    },
    "com.oracle.truffle.llvm.tests.tck" : {
      "subDir" : "tests",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.llvm.runtime",
        "mx:JUNIT",
        "sdk:POLYGLOT_TCK",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "javaCompliance" : "1.8+",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
      "testProject" : True,
      "jacoco" : "exclude",
    },
    "com.oracle.truffle.llvm.tests.tck.native" : {
      "subDir" : "tests",
      "native" : True,
      "vpath" : True,
      "results" : [
        "bin/"
      ],
      "buildDependencies" : [
        "SULONG_BOOTSTRAP_TOOLCHAIN",
        "SULONG_HOME",
      ],
      "buildEnv" : {
        "SULONGTCKTEST" : "<lib:sulongtck>",
        "CLANG" : "<toolchainGetToolPath:native,CC>",
      },
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "license" : "BSD-new",
      "testProject" : True,
      "jacoco" : "exclude",
    },
    "com.oracle.truffle.llvm.toolchain.config" : {
      "description" : "Provide constants from llvm-config",
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "javaCompliance" : "1.8+",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
      "jacoco" : "exclude",
    },
    "com.oracle.truffle.llvm.api" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "truffle:TRUFFLE_API",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "javaCompliance" : "1.8+",
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
      "javaCompliance" : "1.8+",
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
      "javaCompliance" : "1.8+",
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
        "com.oracle.truffle.llvm.toolchain.config",
        "truffle:ANTLR4",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "checkstyleVersion" : "8.8",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "javaCompliance" : "1.8+",
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
      "javaCompliance" : "1.8+",
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
      "javaCompliance" : "1.8+",
      "javaProperties" : {
        "llvm.toolchainRoot" : "<nativeToolchainRoot>",
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
      "javaCompliance" : "1.8+",
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
      "javaCompliance" : "1.8+",
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
        "llvm.bin.dir" : "<path:LLVM_TOOLCHAIN>/bin",
        "org.graalvm.language.llvm.home": "<path:SULONG_HOME>",
      },
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "javaCompliance" : "1.8+",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
      "jacoco" : "include",
    },

    "bootstrap-toolchain-launchers": {
      "subDir": "projects",
      "class" : "BootstrapToolchainLauncherProject",
      "buildDependencies" : [
        "sdk:LLVM_TOOLCHAIN",
        "com.oracle.truffle.llvm.toolchain.launchers",
      ],
      "license" : "BSD-new",
    },

    "toolchain-launchers-tests": {
      "subDir": "tests",
      "native": True,
      "vpath": True,
      "platformDependent": True,
      "max_jobs": "1",
      "buildEnv" : {
        "SULONG_EXE" : "<mx_exe> lli",
        "CLANG": "<toolchainGetToolPath:native,CC>",
        "CLANGXX": "<toolchainGetToolPath:native,CXX>",
        "TOOLCHAIN_LD": "<toolchainGetToolPath:native,LD>",
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
        "com.oracle.truffle.llvm.runtime",
        "truffle:ANTLR4",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "javaCompliance" : "1.8+",
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
      "javaCompliance" : "1.8+",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
      "jacoco" : "include",
    },

    "com.oracle.truffle.llvm.tools.fuzzing.native" : {
      "subDir" : "projects",
      "native" : True,
      "vpath" : True,
      "headers" : ["src/fuzzmain.c"],
      "results" : [
        "bin/<exe:llvm-reduce>",
        "bin/<exe:llvm-stress>",
      ],
      "buildDependencies" : [
        "sdk:LLVM_TOOLCHAIN_FULL",
      ],
      "buildEnv" : {
        "LLVM_CONFIG" : "<path:LLVM_TOOLCHAIN_FULL>/bin/llvm-config",
        "CXX" : "<path:LLVM_TOOLCHAIN_FULL>/bin/clang++",
        "LLVM_REDUCE" :"bin/<exe:llvm-reduce>",
        "LLVM_STRESS" :"bin/<exe:llvm-stress>",
        "LLVM_ORG_SRC" : "<path:LLVM_ORG_SRC>",
        "OS" : "<os>",
      },
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "license" : "BSD-new",
      "testProject" : True,
      "jacoco" : "exclude",
    },

    "com.oracle.truffle.llvm.tests.pipe" : {
      "subDir" : "tests",
      "sourceDirs" : ["src"],
      "jniHeaders" : True,
      "javaProperties" : {
        "test.pipe.lib" : "<path:SULONG_TEST_NATIVE>/<lib:pipe>",
      },
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "javaCompliance" : "1.8+",
      "license" : "BSD-new",
      "testProject" : True,
      "jacoco" : "exclude",
    },

    "com.oracle.truffle.llvm.tests.llirtestgen" : {
      "subDir" : "tests",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.llvm.tests",
      ],
      "javaProperties" : {
        "llirtestgen.prelude": "prelude.ll",
      },
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "javaCompliance" : "1.8+",
      "license" : "BSD-new",
      "testProject" : True,
      "defaultBuild" : False,
      "jacoco" : "exclude",
    },
    "com.oracle.truffle.llvm.tests.llirtestgen.generated" : {
      "class": "GeneratedTestSuite",
      "subDir" : "tests",
      "native" : True,
      "vpath" : True,
      "variants" : ["O0"],
      "buildDependencies" : [
        "LLIR_TEST_GEN",
        "SULONG_HOME",
      ],
      "buildEnv" : {
        "LDFLAGS": "-lm",
        "LLIRTESTGEN_CMD" : "<get_jvm_cmd_line:LLIR_TEST_GEN>",
        "OS" : "<os>",
      },
      "license" : "BSD-new",
      "testProject" : True,
      "defaultBuild" : False,
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
      "jacoco" : "exclude",
    },
    "com.oracle.truffle.llvm.libraries.bitcode" : {
      "subDir" : "projects",
      "native" : True,
      "vpath" : True,
      "results" : [
        "bin/<lib:sulong>",
        "bin/<lib:sulong++>",
      ],
      "headers" : [
        "include/polyglot.h",
        "include/llvm/api/toolchain.h",
      ],
      "buildDependencies" : [
        "sdk:LLVM_TOOLCHAIN",
        "sdk:LLVM_ORG_SRC",
      ],
      "buildEnv" : {
        "CFLAGS" : "-Xclang -disable-O0-optnone",
        "CLANG" : "<path:LLVM_TOOLCHAIN>/bin/clang",
        "CLANGXX" : "<path:LLVM_TOOLCHAIN>/bin/clang++",
        "OPT" : "<path:LLVM_TOOLCHAIN>/bin/opt",
        "LLVM_LINK" : "<path:LLVM_TOOLCHAIN>/bin/llvm-link",
        "LLVM_TOOLCHAIN_LIB" : "<path:LLVM_TOOLCHAIN>/lib",
        "LIBSULONG" : "<lib:sulong>",
        "LIBSULONGXX" : "<lib:sulong++>",
        "LIBCXX_SRC" : "<path:sdk:LLVM_ORG_SRC>",
        "OS" : "<os>",
      },
      "license" : "BSD-new",
    },
    "com.oracle.truffle.llvm.libraries.mock" : {
      "subDir" : "projects",
      "native" : True,
      "vpath" : True,
      "results" : [
        "bin/<lib:polyglot-mock>",
      ],
      "buildDependencies" : [
        "com.oracle.truffle.llvm.libraries.bitcode",
        "SULONG_TOOLCHAIN_LAUNCHERS",
        "SULONG_BOOTSTRAP_TOOLCHAIN",
      ],
      "buildEnv" : {
        "LIBPOLYGLOT_MOCK" : "<lib:polyglot-mock>",
        "CLANG" : "<toolchainGetToolPath:native,CC>",
        "CFLAGS" : "-Xclang -disable-O0-optnone",
        "CPPFLAGS" : "-I<path:com.oracle.truffle.llvm.libraries.bitcode>/include",
        "OS" : "<os>",
      },
      "license" : "BSD-new",
    },
    "com.oracle.truffle.llvm.libraries.native" : {
      "subDir" : "projects",
      "native" : True,
      "vpath" : True,
      "results" : [
        "bin/<lib:sulong-native>",
      ],
      "buildDependencies" : [
        "truffle:TRUFFLE_NFI_NATIVE",
        "com.oracle.truffle.llvm.libraries.bitcode",
        "sdk:LLVM_TOOLCHAIN",
      ],
      "buildEnv" : {
        "CLANG" : "<path:LLVM_TOOLCHAIN>/bin/clang",
        "LIBSULONG" : "<lib:sulong-native>",
        "LIBPOLYGLOT" : "<lib:polyglot-mock>",
        "CPPFLAGS" : "-I<path:truffle:TRUFFLE_NFI_NATIVE>/include -I<path:com.oracle.truffle.llvm.libraries.bitcode>/include",
        "OS" : "<os>",
      },
      "license" : "BSD-new",
    },
    "com.oracle.truffle.llvm.libraries.bitcode.libcxx" : {
      "subDir" : "projects",
      "vpath" : True,
      "sourceDir" : "<path:sdk:LLVM_ORG_SRC>/llvm",
      "class" : "CMakeProject",
      "makeTarget" : ["install-libcxxabi", "install-libcxx"],
      "results" : ["native"],
      "cmakeConfig" : {
        "LLVM_ENABLE_PROJECTS" : "libcxx;libcxxabi",
        "LLVM_INCLUDE_DOCS" : "NO",
        "LLVM_TARGETS_TO_BUILD" : "X86",
        "LIBCXXABI_INCLUDE_TESTS": "NO",
        "LIBCXXABI_LIBCXX_INCLUDES" : "<path:sdk:LLVM_ORG_SRC>/libcxx/include",
        "LIBCXXABI_LIBCXX_PATH" : "<path:sdk:LLVM_ORG_SRC>/libcxx",
        "LIBCXXABI_ENABLE_STATIC" : "NO",
        "LIBCXX_INCLUDE_BENCHMARKS": "NO",
        "LIBCXX_INCLUDE_TESTS": "NO",
        "LIBCXX_CXX_ABI" : "libcxxabi",
        # shouldn't this be detected automatically?
        "LIBCXX_CXX_ABI_LIBRARY_PATH" : "<path:com.oracle.truffle.llvm.libraries.bitcode.libcxx>/native/lib",
        # shouldn't this be detected automatically?
        "LIBCXX_CXX_ABI_INCLUDE_PATHS" : "<path:sdk:LLVM_ORG_SRC>/libcxxabi/include",
        "LIBCXX_ENABLE_STATIC" : "NO",
        "LIBCXX_ENABLE_EXPERIMENTAL_LIBRARY" : "NO",
        "CMAKE_C_COMPILER" : "<toolchainGetToolPath:native,CC>",
        "CMAKE_CXX_COMPILER" :  "<toolchainGetToolPath:native,CXX>",
        # Work around for mx not liking $ signs. We use '{{}}' as a placeholder and replace that in the CMakeProject.
        "CMAKE_SHARED_LINKER_FLAGS" : "-Wl,-rpath,{{}}ORIGIN",
        "CMAKE_INSTALL_PREFIX" : "native",
      },
      "buildDependencies" : [
        "sdk:LLVM_ORG_SRC",
        "SULONG_BOOTSTRAP_TOOLCHAIN",
        "sdk:LLVM_TOOLCHAIN",
      ],
      "clangFormat" : False,
    },

    "com.oracle.truffle.llvm.tests.debug.native" : {
      "subDir" : "tests",
      "class" : "SulongTestSuite",
      "variants" : ["O1", "O0", "O0_MEM2REG"],
      "buildRef" : False,
      "buildEnv" : {
        "SUITE_CFLAGS" : "-g",
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
    "com.oracle.truffle.llvm.tests.debugexpr.native" : {
      "subDir" : "tests",
      "class" : "SulongTestSuite",
      "variants" : ["O1", "O0", "O0_MEM2REG"],
      "buildRef" : False,
      "buildEnv" : {
        "SUITE_CFLAGS" : "-g",
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
      "variants" : ["O1"],
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
      "fileExts" : [".ll"],
      "buildEnv" : {
        "OS" : "<os>",
        "SUITE_LDFLAGS" : "-lm",
      },
      "dependencies" : [
        "SULONG_TEST",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.bitcode.uncommon.native" : {
      "subDir" : "tests",
      "class" : "SulongTestSuite",
      # This should be the O1 variant (and the CFLAGS buildEnv entry
      # below should be changed to -O1) but it currently breaks the
      # tests in the project (difference in behavior between O0 and
      # O1). This issue is related to the vstore.ll.ignored test in
      # that we should fix it once we have a solution for the general
      # issue in exeuction mistmatches. Until then the Sulong behavior
      # is the more accurate one.
      "variants" : ["O0"],
      "fileExts" : [".ll"],
      "buildEnv" : {
        "OS" : "<os>",
        "CFLAGS" : "-O0",
      },
      "dependencies" : [
        "SULONG_TEST",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.bitcode.other.native" : {
      "subDir" : "tests",
      "class" : "SulongTestSuite",
      "variants" : ["O0"],
      "fileExts" : [".ll"],
      "buildRef" : False,
      "buildEnv" : {
        "OS" : "<os>",
        "CFLAGS" : "-O0",
      },
      "dependencies" : [
        "SULONG_TEST",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.bitcode.amd64.native" : {
      "subDir" : "tests",
      "class" : "SulongTestSuite",
      "variants" : ["O0"],
      "fileExts" : [".ll"],
      "buildRef" : True,
      "buildEnv" : {
        "OS" : "<os>",
        "CFLAGS" : "-O0",
        "SUITE_LDFLAGS" : "-lm",
      },
      "dependencies" : [
        "SULONG_TEST",
      ],
      "buildDependencies" : [
        # "AMD64_SUPPORT" currently not working on darwin GR-21946,
        "LINUX_AMD64_SUPPORT",
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
      "buildDependencies" : [
        "LINUX_AMD64_SUPPORT",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.sulongcpp.native" : {
      "subDir" : "tests",
      "class" : "SulongTestSuite",
      "variants" : ["O0_OUT", "O1_OUT"],
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
      "buildDependencies" : [
        "LINUX_AMD64_SUPPORT",
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
        "bitcodeformat/hello-darwin-link.bundle",
      ],
      "buildEnv": {
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
      "subDir" : "tests",
      "native": True,
      "vpath": True,
      "buildEnv" : {
        "OS" : "<os>",
        "CLANG": "<toolchainGetToolPath:native,CC>",
        "SRC_DIR": "<path:com.oracle.truffle.llvm.tests.linker.native>",
      },
      "dependencies" : [
        "SULONG_TEST",
        "SULONG_TOOLCHAIN_LAUNCHERS",
        "SULONG_BOOTSTRAP_TOOLCHAIN",
      ],
      "results": [
        "dynLink",
        "linker",
        "rpath",
        "reload",
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
        "ALIAS_SUPPORT",
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
      "variants" : ["O0_OUT"],
      "buildRef" : True,
      "buildEnv" : {
        "SUITE_CPPFLAGS" : "-I<path:SULONG_LEGACY>/include",
      },
      "dependencies" : [
        "SULONG_TEST",
      ],
      "buildDependencies" : [
        "GCC_SOURCE",
        "ALIAS_SUPPORT",
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
        "DRAGONEGG_SUPPORT",
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
        "ALIAS_SUPPORT",
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
      "variants" : ["O0_OUT"],
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
      "variants" : ["O1_OUT"],
      "extraLibs" : ["libgmp.so.10"],
      "buildRef" : True,
      "buildEnv" : {
        "SUITE_LDFLAGS" : "-lm -lgmp",
      },
      "dependencies" : [
        "SULONG_TEST",
      ],
      "buildDependencies" : [
        "SHOOTOUT_SUITE",
        "MALLOC_H_SUPPORT",
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
        "SULONG_TOOLCHAIN_CONFIG",
      ],
      "javaProperties" : {
        "org.graalvm.language.llvm.home": "<sulong_home>",
      },
      "license" : "BSD-new",
    },

    "SULONG_API" : {
      "subDir" : "projects",
      "dependencies" : [
        "com.oracle.truffle.llvm.api",
        "com.oracle.truffle.llvm.spi",
      ],
      "distDependencies" : [
        "truffle:TRUFFLE_API",
      ],
      "license" : "BSD-new",
    },
    "SULONG_TOOLCHAIN_CONFIG" : {
      "subDir" : "projects",
      "dependencies" : [
        "com.oracle.truffle.llvm.toolchain.config",
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

    "SULONG_HOME" : {
      "native" : True,
      "relpath" : False,
      "platformDependent" : True,
      "layout" : {
        "./": [
          "dependency:com.oracle.truffle.llvm.libraries.bitcode.libcxx/*",
        ],
        "./native/lib/" : [
          "dependency:com.oracle.truffle.llvm.libraries.bitcode/bin/<lib:sulong>",
          "dependency:com.oracle.truffle.llvm.libraries.bitcode/bin/<lib:sulong++>",
          "dependency:com.oracle.truffle.llvm.libraries.native/bin/*",
          "dependency:com.oracle.truffle.llvm.libraries.mock/bin/*",
        ],
        "./include/" : [
          "dependency:com.oracle.truffle.llvm.libraries.bitcode/include/*"
        ]
      },
      "dependencies" : [
        "com.oracle.truffle.llvm.libraries.bitcode",
        "com.oracle.truffle.llvm.libraries.native",
        "com.oracle.truffle.llvm.libraries.mock",
        "com.oracle.truffle.llvm.libraries.bitcode.libcxx",
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
        "SULONG_TOOLCHAIN_LAUNCHERS",
      ],
      "distDependencies" : [
        "SULONG_TOOLCHAIN_LAUNCHERS",
      ],
      "license": "BSD-new",
    },

    "SULONG_TOOLS": {
      "native": True,
      "relpath": False,
      "platformDependent": True,
      "layout": {
        "./": "dependency:com.oracle.truffle.llvm.tools.fuzzing.native/*",
      },
      "license": "BSD-new",
    },

    "SULONG_TEST" : {
      "subDir" : "tests",
      "dependencies" : [
        "com.oracle.truffle.llvm.tests",
        "com.oracle.truffle.llvm.tests.types",
        "com.oracle.truffle.llvm.tests.pipe",
        "com.oracle.truffle.llvm.tests.tck"
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
        "sulongtest.testSuitePath" : "<path:SULONG_TEST_SUITES>",
        "sulongtest.llTestSuitePath" : "<path:SULONG_LL_TEST_SUITES>",
        "test.sulongtck.path" : "<path:SULONG_TCK_NATIVE>/bin"
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
    },

    "LLIR_TEST_GEN" : {
      "relpath" : True,
      "mainClass" : "com.oracle.truffle.llvm.tests.llirtestgen.LLIRTestGen",
      "dependencies" : [
        "com.oracle.truffle.llvm.tests.llirtestgen",
      ],
      "distDependencies" : [
        "SULONG_TEST",
      ],
      "license" : "BSD-new",
      "testDistribution" : True,
      "defaultBuild" : False,
    },

    "SULONG_TEST_SUITES" : {
      "native" : True,
      "relpath" : True,
      "platformDependent" : True,
      "layout" : {
        "./" : [
          "dependency:com.oracle.truffle.llvm.tests.bitcodeformat.native/*",
          "dependency:com.oracle.truffle.llvm.tests.debug.native/*",
          "dependency:com.oracle.truffle.llvm.tests.debugexpr.native/*",
          "dependency:com.oracle.truffle.llvm.tests.llirtestgen.generated/*",
          "dependency:com.oracle.truffle.llvm.tests.irdebug.native/*",
          "dependency:com.oracle.truffle.llvm.tests.interop.native/*",
          "dependency:com.oracle.truffle.llvm.tests.other.native/*",
          "dependency:com.oracle.truffle.llvm.tests.sulong.native/*",
          "dependency:com.oracle.truffle.llvm.tests.sulongavx.native/*",
          "dependency:com.oracle.truffle.llvm.tests.sulongcpp.native/*",
          "dependency:com.oracle.truffle.llvm.tests.libc.native/*",
          "dependency:com.oracle.truffle.llvm.tests.linker.native/*",
        ],
      },
      "license" : "BSD-new",
      "testDistribution" : True,
      "defaultBuild" : False,
    },
    "SULONG_LL_TEST_SUITES" : {
      "native" : True,
      "relpath" : True,
      "platformDependent" : True,
      "layout" : {
        "./" : [
          "dependency:com.oracle.truffle.llvm.tests.bitcode.native/*",
          "dependency:com.oracle.truffle.llvm.tests.bitcode.uncommon.native/*",
          "dependency:com.oracle.truffle.llvm.tests.bitcode.other.native/*",
          "dependency:com.oracle.truffle.llvm.tests.bitcode.amd64.native/*",
        ],
      },
      "license" : "BSD-new",
      "testDistribution" : True,
      "defaultBuild" : False,
    },
    "SULONG_TCK_NATIVE" : {
      "native" : True,
      "relpath" : True,
      "platformDependent" : True,
      "layout" : {
        "./" : [
          "dependency:com.oracle.truffle.llvm.tests.tck.native/*",
        ],
      },
      "license" : "BSD-new",
      "testDistribution" : True,
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
  }
}
