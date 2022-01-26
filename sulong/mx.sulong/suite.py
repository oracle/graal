suite = {
  "mxversion" : "5.317.0",
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
    # This is a dummy library for marking sulong native mode support.
    "NATIVE_MODE_SUPPORT" : {
      "os_arch" : {
        "<others>" : {
          "<others>" : {
            "path": "tests/support.txt",
            "sha1": "81177e981eeb52730854e3d763e96015881c3bab",
          },
        },
        "windows" : {
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
        "sulongtest.source.GCC_SOURCE" : "<path:GCC_SOURCE>",
        "sulongtest.source.LLVM_TEST_SUITE" : "<path:LLVM_TEST_SUITE>",
        "sulongtest.source.NWCC_SUITE" : "<path:NWCC_SUITE>",
      },
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
      "testProject" : True,
      "jacoco" : "exclude",
    },
    "com.oracle.truffle.llvm.tests.native" : {
      "subDir" : "tests",
      "native" : "shared_lib",
      "deliverable" : "sulongtest",
      "use_jdk_headers" : False,
      "buildDependencies" : [
        "NATIVE_MODE_SUPPORT",
      ],
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
      "buildDependencies" : [
        "NATIVE_MODE_SUPPORT",
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
        "NATIVE_MODE_SUPPORT",
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

    "com.oracle.truffle.llvm.nativemode" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "truffle:TRUFFLE_NFI",
        "SULONG_CORE"
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
        "truffle:TRUFFLE_NFI",
        "com.oracle.truffle.llvm.api",
        "com.oracle.truffle.llvm.spi",
        "com.oracle.truffle.llvm.toolchain.config",
        "truffle:ANTLR4",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "checkstyleVersion" : "8.8",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "javaCompliance" : "1.8+",
      "spotbugsIgnoresGenerated" : True,
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
      "jacoco" : "include",
      # Using finalizer in signals implementation. GR-7018
      "javac.lint.overrides" : "-deprecation",
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
        "com.oracle.truffle.llvm.parser",
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

    "bootstrap-toolchain-launchers-no-home": {
      "subDir": "projects",
      "class" : "BootstrapToolchainLauncherProject",
      "buildDependencies" : [
        "sdk:LLVM_TOOLCHAIN",
        "com.oracle.truffle.llvm.toolchain.launchers",
      ],
      "javaProperties" : {
        # we intentionally set llvm home to a non-existent location to avoid picking up outdated files
        "org.graalvm.language.llvm.home" : "<path:SULONG_BOOTSTRAP_TOOLCHAIN_NO_HOME>/nonexistent",
      },
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
        "SULONG_CORE",
        "SULONG_NATIVE",
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
      # warnings in generated code
      "javac.lint.overrides" : "none",
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
        "NATIVE_MODE_SUPPORT",
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
      "defaultBuild" : False,
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
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "javaCompliance" : "1.8+",
      "license" : "BSD-new",
      "testProject" : True,
      "jacoco" : "exclude",
    },
    "com.oracle.truffle.llvm.tests.llirtestgen.generated" : {
      "subDir" : "tests",
      "native" : True,
      "vpath" : True,
      "bundledLLVMOnly" : True,
      "results" : ["gen"],
      "buildDependencies" : [
        "LLIR_TEST_GEN",
        "SULONG_HOME",
        "sdk:LLVM_TOOLCHAIN",
        "SULONG_BOOTSTRAP_TOOLCHAIN",
        "LINUX_AMD64_SUPPORT",
      ],
      "buildEnv": {
        "LLIRTESTGEN_CMD" : "<get_jvm_cmd_line:LLIR_TEST_GEN>",
      },
      "license" : "BSD-new",
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.llirtestgen.native" : {
      "class": "ExternalCMakeTestSuite",
      "subDir" : "tests",
      "testSourceDir" : "<path:LLIR_TEST_GEN_SOURCES>",
      "native" : True,
      "vpath" : True,
      "bundledLLVMOnly" : True,
      "variants" : ["bitcode-O0"],
      "fileExts" : [".ll"],
      "buildDependencies" : [
        "LLIR_TEST_GEN_SOURCES",
        "LINUX_AMD64_SUPPORT",
      ],
      "cmakeConfig": {
        "CMAKE_C_LINK_FLAGS" : "-lm",
      },
      "license" : "BSD-new",
      "testProject" : True,
      "defaultBuild" : False,
    },

    "com.oracle.truffle.llvm.tests.pipe.native" : {
      "subDir" : "tests",
      "native" : "shared_lib",
      "deliverable" : "pipe",
      "use_jdk_headers" : True,
      "buildDependencies" : [
        "com.oracle.truffle.llvm.tests.pipe",
      ],
      "license" : "BSD-new",
      "testProject" : True,
      "os_arch" : {
        "windows" : {
          "<others>" : {
            "cflags" : []
          }
        },
        "solaris" : {
          "<others>" : {
            "cflags" : ["-g", "-Wall", "-Werror", "-m64"],
            "ldflags" : ["-m64"],
          },
        },
        "<others>" : {
          "<others>" : {
            "cflags" : ["-g", "-Wall", "-Werror"],
          },
        },
      },
    },
    "com.oracle.truffle.llvm.libraries.bitcode" : {
      "subDir" : "projects",
      "class" : "CMakeNinjaProject",
      # NinjaBuildTask uses only 1 job otherwise
      "max_jobs" : "8",
      "vpath" : True,
      "os_arch" : {
        "<others>" : {
          "<others>" : {
            "ninja_targets" : [
              "<lib:sulong>",
              "<lib:sulong++>",
            ],
            "results" : [
              "bin/<lib:sulong>",
              "bin/<lib:sulong++>",
            ],
          },
        },
        "windows" : {
          "<others>" : {
            "ninja_targets" : [
              "<lib:sulong>",
            ],
            "results" : [
              "bin/<lib:sulong>",
            ],
          },
        },
      },
      "ninja_install_targets" : ["install"],
      "buildDependencies" : [
        "sdk:LLVM_TOOLCHAIN",
        "sdk:LLVM_ORG_SRC",
        "SULONG_BOOTSTRAP_TOOLCHAIN_NO_HOME",
      ],
      "cmakeConfig" : {
        "CMAKE_OSX_DEPLOYMENT_TARGET" : "10.13",
        "CMAKE_C_COMPILER" : "<path:SULONG_BOOTSTRAP_TOOLCHAIN_NO_HOME>/bin/<cmd:clang>",
        "CMAKE_CXX_COMPILER" : "<path:SULONG_BOOTSTRAP_TOOLCHAIN_NO_HOME>/bin/<cmd:clang++>",
        "GRAALVM_LLVM_INCLUDE_DIR" : "<path:com.oracle.truffle.llvm.libraries.graalvm.llvm>/include",
        "LIBCXX_SRC" : "<path:sdk:LLVM_ORG_SRC>",
        "LLVM_LINK" : "<path:LLVM_TOOLCHAIN>/bin/<exe:llvm-link>",
        "MX_OS" : "<os>",
        "MX_ARCH" : "<arch>",
      },
      "license" : "BSD-new",
    },
    "com.oracle.truffle.llvm.libraries.graalvm.llvm" : {
      "class" : "HeaderProject",
      "subDir" : "projects",
      "native" : True,
      "vpath" : True,
      "results" : [],
      "headers" : [
        "include/graalvm/llvm/handles.h",
        "include/graalvm/llvm/polyglot.h",
        "include/graalvm/llvm/toolchain-api.h",
        "include/graalvm/llvm/internal/handles-impl.h",
        "include/graalvm/llvm/internal/polyglot-impl.h",
        # for source compatibility
        "include/polyglot.h",
        "include/llvm/api/toolchain.h",
      ],
      "license" : "BSD-new",
    },
    "com.oracle.truffle.llvm.libraries.graalvm.llvm.libs" : {
      "subDir" : "projects",
      "class" : "CMakeNinjaProject",
      # NinjaBuildTask uses only 1 job otherwise
      "max_jobs" : "8",
      "vpath" : True,
      "ninja_targets" : [
        "<libv:graalvm-llvm.1>",
      ],
      "ninja_install_targets" : ["install"],
      "results" : [
        # "bin/<lib:graalvm-llvm>",
        # We on purpose exclude the symlink from the results because the layout distribution would dereference it and
        # create a copy instead of keeping the symlink.
        # The symlink is added manually in the layout definition of the distribution.
        "bin/<libv:graalvm-llvm.1>",
      ],
      "buildDependencies" : [
        "SULONG_BOOTSTRAP_TOOLCHAIN_NO_HOME",
        "com.oracle.truffle.llvm.libraries.graalvm.llvm",
      ],
      "cmakeConfig" : {
        "CMAKE_OSX_DEPLOYMENT_TARGET" : "10.13",
        "CMAKE_C_COMPILER" : "<path:SULONG_BOOTSTRAP_TOOLCHAIN_NO_HOME>/bin/<cmd:clang>",
        "GRAALVM_LLVM_INCLUDE_DIR" : "<path:com.oracle.truffle.llvm.libraries.graalvm.llvm>/include",
        "LLVM_LINK" : "<path:LLVM_TOOLCHAIN>/bin/<exe:llvm-link>",
      },
      "license" : "BSD-new",
    },
    "com.oracle.truffle.llvm.libraries.native" : {
      "subDir" : "projects",
      "class" : "CMakeNinjaProject",
      # NinjaBuildTask uses only 1 job otherwise
      "max_jobs" : "8",
      "vpath" : True,
      "ninja_targets" : [
        "<lib:sulong-native>",
      ],
      "ninja_install_targets" : ["install"],
      "results" : [
        "bin/<lib:sulong-native>",
      ],
      "buildDependencies" : [
        "truffle:TRUFFLE_NFI_NATIVE",
        "sdk:LLVM_TOOLCHAIN",
      ],
      "cmakeConfig" : {
        "CMAKE_OSX_DEPLOYMENT_TARGET" : "10.13",
        "CMAKE_C_COMPILER" : "<path:LLVM_TOOLCHAIN>/bin/<exe:clang>",
        "TRUFFLE_NFI_NATIVE_INCLUDE" : "<path:truffle:TRUFFLE_NFI_NATIVE>/include",
        "CMAKE_WINDOWS_EXPORT_ALL_SYMBOLS" : "YES",
      },
      "os_arch" : {
        "windows" : {
          "<others>" : {
            "cmakeConfig" : {
              "CMAKE_SHARED_LINKER_FLAGS" : "",
            },
          },
        },
        "<others>" : {
          "<others>" : {
            "cmakeConfig" : {
              "CMAKE_SHARED_LINKER_FLAGS" : "-lm",
            },
          },
        },
      },
      "license" : "BSD-new",
    },
    "com.oracle.truffle.llvm.libraries.bitcode.libcxx" : {
      "subDir" : "projects",
      "vpath" : True,
      "sourceDir" : "<path:sdk:LLVM_ORG_SRC>/runtimes",
      "class" : "CMakeNinjaProject",
      # NinjaBuildTask uses only 1 job otherwise
      "max_jobs" : "8",
      "ninja_targets" : ["cxxabi", "cxx"],
      "ninja_install_targets" : ["install-cxxabi", "install-cxx"],
      "results" : ["native"],
      "cmakeConfig" : {
        "LLVM_ENABLE_RUNTIMES" : "libcxx;libcxxabi",
        "LIBCXXABI_INCLUDE_TESTS": "NO",
        "LIBCXXABI_ENABLE_STATIC" : "NO",
        "LIBCXX_INCLUDE_BENCHMARKS": "NO",
        "LIBCXX_INCLUDE_TESTS": "NO",
        "LIBCXX_ENABLE_STATIC" : "NO",
        "LIBCXX_ENABLE_EXPERIMENTAL_LIBRARY" : "NO",
        "CMAKE_C_COMPILER" : "<path:SULONG_BOOTSTRAP_TOOLCHAIN_NO_HOME>/bin/clang",
        "CMAKE_CXX_COMPILER" : "<path:SULONG_BOOTSTRAP_TOOLCHAIN_NO_HOME>/bin/clang++",
        "CMAKE_INSTALL_PREFIX" : "native",
        # workaround for build problem with cmake >=3.22
        # see https://lists.llvm.org/pipermail/llvm-dev/2021-December/154144.html
        "CMAKE_BUILD_WITH_INSTALL_RPATH" : "YES",
      },
      "buildDependencies" : [
        "sdk:LLVM_ORG_SRC",
        "SULONG_BOOTSTRAP_TOOLCHAIN_NO_HOME",
        "sdk:LLVM_TOOLCHAIN",
        "NATIVE_MODE_SUPPORT",
      ],
      "clangFormat" : False,
    },

    "com.oracle.truffle.llvm.tests.cmake" : {
      "description" : "Common CMake files",
      "class" : "HeaderProject",
      "subDir" : "tests",
      "native" : True,
    },

    "com.oracle.truffle.llvm.tests.debug.native" : {
      "subDir" : "tests",
      "class" : "SulongCMakeTestSuite",
      "variants" : ["bitcode-O1", "bitcode-O0", "bitcode-O0-MEM2REG"],
      "buildRef" : False,
      "cmakeConfig" : {
        "CMAKE_C_FLAGS" : "-Wno-bitfield-constant-conversion",
        "CMAKE_CXX_FLAGS" : "-Wno-bitfield-constant-conversion",
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
      "class" : "SulongCMakeTestSuite",
      "variants" : ["bitcode-O1", "bitcode-O0", "bitcode-O0-MEM2REG"],
      "buildRef" : False,
      "cmakeConfig" : {
        "CMAKE_C_FLAGS" : "-Wno-everything",
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
      "class" : "SulongCMakeTestSuite",
      "variants" : ["bitcode-O0"],
      "fileExts" : [".ll"],
      "buildRef" : False,
      "bundledLLVMOnly" : True,
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
      "class" : "SulongCMakeTestSuite",
      "variants" : ["toolchain-plain"],
      "buildRef" : False,
      "buildSharedObject" : True,
      "bundledLLVMOnly" : True,
      "cmakeConfig" : {
        "CMAKE_C_FLAGS" : "-Wno-unused-function -I<path:SULONG_LEGACY>/include -I<path:SULONG_HOME>/include",
        "CMAKE_CXX_FLAGS" : "-Wno-unused-function -I<path:SULONG_LEGACY>/include -I<path:SULONG_HOME>/include",
        "TOOLCHAIN_CLANG" : "<toolchainGetToolPath:native,CC>",
        "TOOLCHAIN_CLANGXX" : "<toolchainGetToolPath:native,CXX>",
      },
      "os_arch" : {
        "darwin": {
          "<others>" : {
            "cmakeConfig" : {
              "CMAKE_SHARED_LINKER_FLAGS" : "-lgraalvm-llvm -L<path:SULONG_HOME>/native/lib -lsulongtest -L<path:SULONG_TEST_NATIVE>",
            },
          },
        },
        "<others>": {
          "<others>": {
            "cmakeConfig" : {
              "CMAKE_SHARED_LINKER_FLAGS" : "--no-undefined -lgraalvm-llvm -L<path:SULONG_HOME>/native/lib -Wl,--undefined=callbackPointerArgTest -lsulongtest -L<path:SULONG_TEST_NATIVE>",
            },
          },
        },
      },
      "buildDependencies" : [
        "SULONG_HOME",
        "SULONG_TEST_NATIVE",
        "SULONG_BOOTSTRAP_TOOLCHAIN",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.sulong.native" : {
      "subDir" : "tests",
      "class" : "SulongCMakeTestSuite",
      "variants" : ["bitcode-O0", "bitcode-O1", "bitcode-O2", "bitcode-O3", "gcc-O0"],
      "cmakeConfig" : {
        "CMAKE_EXE_LINKER_FLAGS" : "-lm",
      },
      "dependencies" : [
        "SULONG_TEST",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.bitcode.native" : {
      "subDir" : "tests",
      "class" : "SulongCMakeTestSuite",
      "bundledLLVMOnly" : True,
      "variants" : ["bitcode-O0"],
      "fileExts" : [".ll"],
      "cmakeConfig" : {
        "CMAKE_C_LINK_FLAGS" : "-lm",
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
    "com.oracle.truffle.llvm.tests.bitcode.uncommon.native" : {
      "subDir" : "tests",
      "class" : "SulongCMakeTestSuite",
      "bundledLLVMOnly" : True,
      # This should be the O1 variant (and the CFLAGS buildEnv entry
      # below should be changed to -O1) but it currently breaks the
      # tests in the project (difference in behavior between O0 and
      # O1). This issue is related to the vstore.ll.ignored test in
      # that we should fix it once we have a solution for the general
      # issue in exeuction mistmatches. Until then the Sulong behavior
      # is the more accurate one.
      "variants" : ["bitcode-O0"],
      "fileExts" : [".ll"],
      "buildDependencies" : [
        "LINUX_AMD64_SUPPORT",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.bitcode.other.native" : {
      "subDir" : "tests",
      "class" : "SulongCMakeTestSuite",
      "bundledLLVMOnly" : True,
      "variants" : ["bitcode-O0"],
      "fileExts" : [".ll"],
      "buildRef" : False,
      "buildDependencies" : [
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.bitcode.amd64.native" : {
      "subDir" : "tests",
      "class" : "SulongCMakeTestSuite",
      "bundledLLVMOnly" : True,
      "variants" : ["bitcode-O0"],
      "fileExts" : [".ll"],
      "buildRef" : True,
      "cmakeConfig" : {
        "CMAKE_C_LINK_FLAGS" : "-lm",
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
    "com.oracle.truffle.llvm.tests.sulongavx.native" : {
      "subDir" : "tests",
      "class" : "SulongCMakeTestSuite",
      "variants" : ["bitcode-O1", "bitcode-O2", "bitcode-O3"],
      "cmakeConfig" : {
        "CMAKE_C_FLAGS" : "-mavx2",
        "CMAKE_EXE_LINKER_FLAGS" : "-lm",
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
    "com.oracle.truffle.llvm.tests.pthread.native" : {
      "subDir" : "tests",
      "class" : "SulongCMakeTestSuite",
      "variants" : ["bitcode-O0"],
      "cmakeConfig" : {
        "CMAKE_C_FLAGS" : "-pthread",
        "CMAKE_C_LINK_FLAGS" : "-pthread",
      },
      "dependencies" : [
        "SULONG_TEST",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.embedded.pthread.native" : {
      "subDir" : "tests",
      "class" : "SulongCMakeTestSuite",
      "variants" : ["toolchain-plain"],
      "buildRef" : False,
      "buildSharedObject" : True,
      "bundledLLVMOnly" : True,
      "cmakeConfig" : {
        "CMAKE_C_FLAGS" : "-pthread",
        "CMAKE_C_LINK_FLAGS" : "-pthread",
        "TOOLCHAIN_CLANG" : "<toolchainGetToolPath:native,CC>",
        "TOOLCHAIN_CLANGXX" : "<toolchainGetToolPath:native,CXX>",
      },
      "dependencies" : [
        "SULONG_TEST",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.sulongcpp.native" : {
      "subDir" : "tests",
      "class" : "SulongCMakeTestSuite",
      "variants" : ["executable-O0", "executable-O1"],
      "dependencies" : [
        "SULONG_TEST",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.libc.native" : {
      "subDir" : "tests",
      "class" : "SulongCMakeTestSuite",
      "variants" : ["executable-O0", "toolchain-plain"],
      "cmakeConfig" : {
        "TOOLCHAIN_CLANG" : "<toolchainGetToolPath:native,CC>",
        "TOOLCHAIN_CLANGXX" : "<toolchainGetToolPath:native,CXX>",
      },
      "dependencies" : [
        "SULONG_TEST",
      ],
      "testProject" : True,
      "defaultBuild" : False,
      "os_arch" : {
        "darwin": {
          "<others>" : {
            "cmakeConfig" : {
              "CMAKE_EXE_LINKER_FLAGS" : "-lm",
              "CMAKE_C_FLAGS" : "-Wno-deprecated-declarations",
            },
          },
        },
        "<others>": {
          "<others>" : {
            "cmakeConfig" : {
              "CMAKE_EXE_LINKER_FLAGS" : "-lm -lrt",
            },
          },
        },
      },
    },
    "com.oracle.truffle.llvm.tests.inlineasm.native" : {
      "subDir" : "tests",
      "class" : "SulongCMakeTestSuite",
      "variants" : ["bitcode-O0"],
      "buildDependencies" : [
        "LINUX_AMD64_SUPPORT",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.standalone.other.native" : {
      "subDir" : "tests",
      "class" : "SulongCMakeTestSuite",
      "variants" : ["bitcode-O1"],
      "buildRef" : False,
      "cmakeConfig" : {
        "CMAKE_EXE_LINKER_FLAGS" : "-lm",
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
        "bitcodeformat/hello-windows-compile-fembed-bitcode.o",
        "bitcodeformat/hello-windows-link-fembed-bitcode.exe",
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
    "com.oracle.truffle.llvm.tests.dynloader.native" : {
      "subDir" : "tests",
      "native": True,
      "vpath": True,
      "buildEnv" : {
        "OS" : "<os>",
        "CLANG": "<toolchainGetToolPath:native,CC>",
        "SRC_DIR": "<path:com.oracle.truffle.llvm.tests.dynloader.native>",
      },
      "dependencies" : [
        "SULONG_TEST",
        "SULONG_TOOLCHAIN_LAUNCHERS",
        "SULONG_BOOTSTRAP_TOOLCHAIN",
      ],
      "results": [
        "dlopenAbsolute",
        "dlopenLocator",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.embedded.custom.native" : {
      "description" : "Embedded tests with custom Makefiles",
      "subDir" : "tests",
      "native": True,
      "vpath": True,
      "buildEnv" : {
        "OS" : "<os>",
        "CLANG": "<toolchainGetToolPath:native,CC>",
        "SRC_DIR": "<path:com.oracle.truffle.llvm.tests.embedded.custom.native>",
      },
      "dependencies" : [
        "SULONG_TEST",
        "SULONG_TOOLCHAIN_LAUNCHERS",
        "SULONG_BOOTSTRAP_TOOLCHAIN",
      ],
      "results": [
        "interop",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.va.native" : {
      "subDir" : "tests",
      "native": True,
      "vpath": True,
      "os_arch" : {
		"linux": {
          "aarch64" : {
            "buildEnv" : {
              "PLATFORM" : "aarch64",
            },
          },
          "amd64": {
            "buildEnv" : {
              "PLATFORM" : "x86_64",
            },
          },
          "<others>": {
            "buildEnv" : {
              "PLATFORM" : "unknown_platform",
            },
          },
        },
        "darwin": {
          "amd64": {
            "buildEnv" : {
              "PLATFORM" : "x86_64",
            },
          },
        },
		"<others>": {
          "<others>": {
            "buildEnv" : {
              "PLATFORM" : "unknown_platform",
            },
          },
        }
      },
      "buildEnv" : {
        "OS" : "<os>",
        "CLANG": "<toolchainGetToolPath:native,CC>",
        "SRC_DIR": "<path:com.oracle.truffle.llvm.tests.va.native>",
      },
      "buildDependencies" : [],
      "dependencies" : [
        "SULONG_TEST",
        "SULONG_TOOLCHAIN_LAUNCHERS",
        "SULONG_BOOTSTRAP_TOOLCHAIN",
      ],
      "results": [
        "valist",
        "va_arg"
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "gcc_c" : {
      "subDir" : "tests/gcc",
      "class" : "ExternalCMakeTestSuite",
      "testDir" : "gcc-5.2.0/gcc/testsuite",
      "fileExts" : [".c"],
      "native" : True,
      "vpath" : True,
      "variants" : ["executable-O0"],
      "buildRef" : True,
      "cmakeConfig" : {
        "CMAKE_C_FLAGS" : "-Wno-everything",
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
      "class" : "ExternalCMakeTestSuite",
      "testDir" : "gcc-5.2.0/gcc/testsuite",
      "fileExts" : [".cpp", ".C", ".cc"],
      "native" : True,
      "vpath" : True,
      "variants" : ["executable-O0"],
      "buildRef" : True,
      "cmakeConfig" : {
        "CMAKE_CXX_FLAGS" : "-Wno-everything",
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
      # The Ninja generator used by mx (version 1.8.2) does not support Fortran using Ninja version [GR-30808]
      # "class" : "ExternalCMakeTestSuite",
      # "variants" : ["executable-O0"],
      "class" : "ExternalTestSuite",
      "variants" : ["O0_OUT"],
      "testDir" : "gcc-5.2.0/gcc/testsuite",
      "fileExts" : [".f90", ".f", ".f03"],
      "requireDragonegg" : True,
      "native" : True,
      "vpath" : True,
      "single_job" : True, # problem with parallel builds and temporary module files
      "buildRef" : True,
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
      "class" : "ExternalCMakeTestSuite",
      "testDir" : "gcc-5.2.0/gcc/testsuite/gcc.c-torture/compile",
      "configDir" : "configs/gcc.c-torture/compile",
      "fileExts" : [".c"],
      "native" : True,
      "vpath" : True,
      "variants" : ["bitcode-O0"],
      "buildRef" : False,
      "cmakeConfig" : {
        "CMAKE_C_FLAGS" : "-Wno-everything",
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
      "class" : "ExternalCMakeTestSuite",
      "testDir" : "test-suite-3.2.src",
      "fileExts" : [".c", ".cpp", ".C", ".cc", ".m"],
      "native" : True,
      "vpath" : True,
      "variants" : ["executable-O0"],
      "buildRef" : True,
      "cmakeConfig" : {
        "CMAKE_C_FLAGS" : "-Wno-everything",
        "CMAKE_CXX_FLAGS" : "-Wno-everything",
        "CMAKE_EXE_LINKER_FLAGS" : "-lm -lgmp",
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
      "class" : "ExternalCMakeTestSuite",
      "testDir" : "benchmarksgame-2014-08-31/benchmarksgame/bench/",
      "fileExts" : [".c", ".cpp", ".C", ".cc", ".m", ".gcc", ".cint", ".gpp"],
      "native" : True,
      "vpath" : True,
      "variants" : ["executable-O1"],
      "buildRef" : True,
      "cmakeConfig" : {
        "CMAKE_C_FLAGS" : "-Wno-everything",
        "CMAKE_EXE_LINKER_FLAGS" : "-lm -lgmp",
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
      "class" : "ExternalCMakeTestSuite",
      "testDir" : "nwcc_0.8.3",
      "fileExts" : [".c"],
      "native" : True,
      "vpath" : True,
      "variants" : ["bitcode-O0"],
      "buildRef" : True,
      "cmakeConfig" : {
        "CMAKE_C_FLAGS" : "-Wno-everything",
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
    "SULONG_CORE" : {
      "description" : "Sulong core functionality (parser, execution engine, launcher)",
      "subDir" : "projects",
      "dependencies" : [
        "com.oracle.truffle.llvm",
        "com.oracle.truffle.llvm.parser.factories",
      ],
      "distDependencies" : [
        "truffle:TRUFFLE_API",
        "truffle:TRUFFLE_NFI",
        "truffle:ANTLR4",
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
    "SULONG_NATIVE" : {
      "description" : "Sulong Native functionality (native memory support, native library support)",
      "subDir" : "projects",
      "dependencies" : [
        "com.oracle.truffle.llvm.nativemode",
      ],
      "distDependencies" : [
        "SULONG_CORE",
        "truffle:TRUFFLE_NFI",
        "truffle:TRUFFLE_NFI_LIBFFI",
      ],
      "license" : "BSD-new",
    },
    "SULONG_NFI" : {
      "description" : "Sulong NFI backend",
      "subDir" : "projects",
      "dependencies" : [
        "com.oracle.truffle.llvm.nfi",
      ],
      "distDependencies" : [
        "truffle:TRUFFLE_NFI",
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

    "SULONG_NATIVE_HOME" : {
      "native" : True,
      "relpath" : False,
      "platformDependent" : True,
      "license" : "BSD-new",
      "os_arch" : {
        "windows" : {
          "<others>": {
            "layout" : {
              "./": [
                #"dependency:com.oracle.truffle.llvm.libraries.bitcode.libcxx/*",
              ],
              "./native/lib/" : [
                "dependency:com.oracle.truffle.llvm.libraries.bitcode/bin/<lib:sulong>",
                #"dependency:com.oracle.truffle.llvm.libraries.bitcode/bin/<lib:sulong++>",
                "dependency:com.oracle.truffle.llvm.libraries.native/bin/*",
                "dependency:com.oracle.truffle.llvm.libraries.graalvm.llvm.libs/bin/*",
              ],
              #"./native/lib/<lib:graalvm-llvm>": "link:<libv:graalvm-llvm.1>",
              # for source compatibility
              #"./native/lib/<lib:polyglot-mock>": "link:<lib:graalvm-llvm>",
            },
          },
        },
        "<others>" : {
          "<others>": {
            "layout" : {
              "./": [
                "dependency:com.oracle.truffle.llvm.libraries.bitcode.libcxx/*",
              ],
              "./native/lib/" : [
                "dependency:com.oracle.truffle.llvm.libraries.bitcode/bin/<lib:sulong>",
                "dependency:com.oracle.truffle.llvm.libraries.bitcode/bin/<lib:sulong++>",
                "dependency:com.oracle.truffle.llvm.libraries.native/bin/*",
                "dependency:com.oracle.truffle.llvm.libraries.graalvm.llvm.libs/bin/*",
              ],
              "./native/lib/<lib:graalvm-llvm>": "link:<libv:graalvm-llvm.1>",
              # for source compatibility
              "./native/lib/<lib:polyglot-mock>": "link:<lib:graalvm-llvm>",
            },
          },
        },
      },
    },

    "SULONG_CORE_HOME" : {
      "native" : True,
      "relpath" : False,
      "platformDependent" : True,
      "layout" : {
        "./include/" : [
          "dependency:com.oracle.truffle.llvm.libraries.graalvm.llvm/include/*"
        ],
      },
      "license" : "BSD-new",
    },

    "SULONG_HOME" : {
      "description" : "Only used as build dependency.",
      "native" : True,
      "relpath" : False,
      "platformDependent" : True,
      "layout" : {
        "./" : [
          "extracted-dependency:SULONG_NATIVE_HOME",
          "extracted-dependency:SULONG_CORE_HOME",
        ],
      },
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
      "platformDependent": True,
      "layout": {
        "./": "dependency:bootstrap-toolchain-launchers/*",
      },
      "asm_requires_cpp": False,
      "buildDependencies" : [
        "SULONG_TOOLCHAIN_LAUNCHERS",
      ],
      "license": "BSD-new",
    },

    "SULONG_BOOTSTRAP_TOOLCHAIN_NO_HOME": {
      "description" : "Bootstrap toolchain without an llvm.home. Use for bootstrapping libraries that should be contained in llvm.home.",
      "native": True,
      "relpath": False,
      "platformDependent": True,
      "layout": {
        "./": "dependency:bootstrap-toolchain-launchers-no-home/*",
      },
      "buildDependencies": [
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
      "defaultBuild" : False,
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
        "sulong:SULONG_NATIVE",
        "sulong:SULONG_CORE",
        "sulong:SULONG_NFI",
        "sulong:SULONG_LEGACY",
        "SULONG_TEST_NATIVE",
      ],
      "os_arch" : {
        "windows" : {
          "<others>": {
              # not SULONG_TCK_NATIVE on windows
          }
        },
        "<others>" : {
          "<others>" : {
            "javaProperties" : {
              "test.sulongtck.path" : "<path:SULONG_TCK_NATIVE>/bin"
            },
          },
        },
      },
      "license" : "BSD-new",
      "testDistribution" : True,
    },

    "SULONG_TEST_NATIVE" : {
      "native" : True,
      "platformDependent" : True,
      "layout" : {
          "./": [
            "dependency:com.oracle.truffle.llvm.tests.pipe.native",
            "dependency:com.oracle.truffle.llvm.tests.native",
          ],
      },
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
    },

    "LLIR_TEST_GEN_SOURCES" : {
      "description" : "Distribution for the generated ll source files.",
      "native" : True,
      "relpath" : True,
      "platformDependent" : True,
      "layout" : {
        "./" : [
          "dependency:com.oracle.truffle.llvm.tests.llirtestgen.generated/*",
        ],
      },
      "license" : "BSD-new",
      "testDistribution" : True,
      "defaultBuild" : False,
    },
    "SULONG_STANDALONE_TEST_SUITES" : {
      "description" : "Tests with a reference executable that is used to verify the result.",
      "native" : True,
      "relpath" : True,
      "platformDependent" : True,
      "layout" : {
        "./" : [
          "dependency:com.oracle.truffle.llvm.tests.llirtestgen.native/*",
          "dependency:com.oracle.truffle.llvm.tests.sulong.native/*",
          "dependency:com.oracle.truffle.llvm.tests.sulongavx.native/*",
          "dependency:com.oracle.truffle.llvm.tests.sulongcpp.native/*",
          "dependency:com.oracle.truffle.llvm.tests.libc.native/*",
          "dependency:com.oracle.truffle.llvm.tests.linker.native/*",
          "dependency:com.oracle.truffle.llvm.tests.va.native/*",
          "dependency:com.oracle.truffle.llvm.tests.inlineasm.native/*",
          "dependency:com.oracle.truffle.llvm.tests.bitcode.native/*",
          "dependency:com.oracle.truffle.llvm.tests.bitcode.uncommon.native/*",
          "dependency:com.oracle.truffle.llvm.tests.bitcode.amd64.native/*",
          "dependency:com.oracle.truffle.llvm.tests.pthread.native/*",
          "dependency:com.oracle.truffle.llvm.tests.dynloader.native/*",
        ],
      },
      "license" : "BSD-new",
      "testDistribution" : True,
      "defaultBuild" : False,
    },
    "SULONG_EMBEDDED_TEST_SUITES" : {
      "description" : "Tests without a reference executable that require a special JUnit test class.",
      "native" : True,
      "relpath" : True,
      "platformDependent" : True,
      "layout" : {
        "./" : [
          "dependency:com.oracle.truffle.llvm.tests.standalone.other.native/*",
          "dependency:com.oracle.truffle.llvm.tests.debug.native/*",
          "dependency:com.oracle.truffle.llvm.tests.bitcodeformat.native/*",
          "dependency:com.oracle.truffle.llvm.tests.interop.native/*",
          "dependency:com.oracle.truffle.llvm.tests.debugexpr.native/*",
          "dependency:com.oracle.truffle.llvm.tests.irdebug.native/*",
          "dependency:com.oracle.truffle.llvm.tests.embedded.custom.native/*",
          "dependency:com.oracle.truffle.llvm.tests.embedded.pthread.native/*",
          "dependency:com.oracle.truffle.llvm.tests.bitcode.other.native/*",
          # the reload tests are not only ran as standalone test (SulongSuite) but also as embedded test (LoaderTest)
          "dependency:com.oracle.truffle.llvm.tests.linker.native/reload",
        ],
      },
      "license" : "BSD-new",
      "testDistribution" : True,
      "defaultBuild" : False,
    },
    "SULONG_TEST_SUITES" : {
      "native" : True,
      "relpath" : True,
      "platformDependent" : True,
      "license" : "BSD-new",
      "testDistribution" : True,
      "defaultBuild" : False,
      "ignore" : "No longer available. Use either SULONG_STANDALONE_TEST_SUITES or SULONG_EMBEDDED_TEST_SUITES.",
    },
    # <editor-fold desc="External Test Suites">
    "SULONG_GCC_C_TEST_SUITE" : {
      "native" : True,
      "relpath" : True,
      "platformDependent" : True,
      "layout" : {
        "./" : [
          "dependency:gcc_c/*",
        ],
      },
      "testDistribution" : True,
      "defaultBuild" : False,
    },
    "SULONG_GCC_CPP_TEST_SUITE" : {
      "native" : True,
      "relpath" : True,
      "platformDependent" : True,
      "layout" : {
        "./" : [
          "dependency:gcc_cpp/*",
        ],
      },
      "testDistribution" : True,
      "defaultBuild" : False,
    },
    "SULONG_GCC_FORTRAN_TEST_SUITE" : {
      "native" : True,
      "relpath" : True,
      "platformDependent" : True,
      "layout" : {
        "./" : [
          "dependency:gcc_fortran/*",
        ],
      },
      "testDistribution" : True,
      "defaultBuild" : False,
    },
    "SULONG_PARSER_TORTURE" : {
      "native" : True,
      "relpath" : True,
      "platformDependent" : True,
      "layout" : {
        "./" : [
          "dependency:parserTorture/*",
        ],
      },
      "testDistribution" : True,
      "defaultBuild" : False,
    },
    "SULONG_LLVM_TEST_SUITE" : {
      "native" : True,
      "relpath" : True,
      "platformDependent" : True,
      "layout" : {
        "./" : [
          "dependency:llvm/*",
        ],
      },
      "testDistribution" : True,
      "defaultBuild" : False,
    },
    "SULONG_SHOOTOUT_TEST_SUITE" : {
      "native" : True,
      "relpath" : True,
      "platformDependent" : True,
      "layout" : {
        "./" : [
          "dependency:shootout/*",
        ],
      },
      "testDistribution" : True,
      "defaultBuild" : False,
    },
    "SULONG_NWCC_TEST_SUITE" : {
      "native" : True,
      "relpath" : True,
      "platformDependent" : True,
      "layout" : {
        "./" : [
          "dependency:nwcc/*",
        ],
      },
      "testDistribution" : True,
      "defaultBuild" : False,
    },
    # </editor-fold>
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
