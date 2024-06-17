suite = {
  "mxversion": "6.43.0",
  "name" : "sulong",
  "versionConflictResolution" : "latest",
  "groupId": "org.graalvm.llvm",
  "url": "http://www.graalvm.org/",
  "developer": {
    "name": "GraalVM Development",
    "email": "graalvm-dev@oss.oracle.com",
    "organization": "Oracle Corporation",
    "organizationUrl": "http://www.graalvm.org/",
  },
  "scm": {
    "url": "https://github.com/oracle/graal",
    "read": "https://github.com/oracle/graal.git",
    "write": "git@github.com:oracle/graal.git",
  },

  "imports" : {
    "suites" : [
      {
        "name" : "truffle",
        "subdir" : True,
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
      "digest" : "sha512:8cc9b4fc97d87a16a5f5b0bd91ebc8a4e7865a50dbfd98f1456f5830fa121860145b6b9aaabf624d9fd5eb5164e2a909e7ccb21375daf54dc24e990db7d716ba",
    },
    "GCC_SOURCE" : {
      "packedResource" : True,
      # original: https://mirrors-usa.go-parts.com/gcc/releases/gcc-5.2.0/gcc-5.2.0.tar.gz
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/sulong-deps/gcc-5.2.0.tar.gz"],
      "digest" : "sha512:d2cf088c08754af0f06cd36cef83544a05bf75c2fa5d9486eec4babece8b32258449f04bcb6506bf3ea6681948574ba56812bc9881497ba0f5460f8358e8fce5",
    },
    "SHOOTOUT_SUITE" : {
      "packedResource" : True,
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/sulong-deps/benchmarksgame-scm-latest.tar.gz"],
      "digest" : "sha512:1a94a02b1633320c2078f6adbe33b31052676fa1c07217f2fb3b3792bfb6a94410812ac6382295a9f4d8828cdb19dd31d1485f264535e01beb0230b79acc7068",
    },
    "NWCC_SUITE" : {
      "packedResource" : True,
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/sulong-deps/nwcc_0.8.3.tar.gz"],
      "digest" : "sha512:f6af50bd18e13070b512bfac6659f49d10d3ad65ea2c4c5ca3f199c8b87540ec145c7dbbe97272f48903ca1c8afaf58c146ec763c851da0b352d5980746f94f6",
    },
    # Support Libraries.
    # Projects depending on these will *not be built* if the 'optional' is 'True' for the given OS/architecture.
    # This is a dummy library for dragonegg support.
    "DRAGONEGG_SUPPORT" : {
      "os_arch" : {
        "linux" : {
          "amd64" : {
            "path": "tests/support.txt",
            "digest": "sha512:c02b248975b267f4200603ff2ae40b9d0cdefad4a792f386d610f2b14fb4e67e288c235fd11ed596dd8c91a3dae62fdd741bf97b5c01b5f085485f221702f0a1",
          },
          "<others>": {"optional": True},
        },
        "<others>": {"<others>" : {"optional": True}},
      },
    },
    # This is a dummy library for malloc.h support.
    "MALLOC_H_SUPPORT" : {
      "os_arch" : {
        "linux" : {
          "amd64" : {
            "path": "tests/support.txt",
            "digest": "sha512:c02b248975b267f4200603ff2ae40b9d0cdefad4a792f386d610f2b14fb4e67e288c235fd11ed596dd8c91a3dae62fdd741bf97b5c01b5f085485f221702f0a1",
          },
          "<others>": {"optional": True},
        },
        "<others>": {"<others>" : {"optional": True}},
      },
    },
    # This is a dummy library for alias() support.
    "ALIAS_SUPPORT" : {
      "os_arch" : {
        "linux" : {
          "amd64" : {
            "path": "tests/support.txt",
            "digest": "sha512:c02b248975b267f4200603ff2ae40b9d0cdefad4a792f386d610f2b14fb4e67e288c235fd11ed596dd8c91a3dae62fdd741bf97b5c01b5f085485f221702f0a1",
          },
          "<others>": {"optional": True},
        },
        "windows": {
          "<others>" : {
            "path": "tests/support.txt",
            "sha1": "9b3f44dd60da58735fce6b7346b4b3ef571b768e",
          },
        },
        "<others>": {"<others>" : {"optional": True}},
      },
    },
    # This is a dummy library for linux amd64 support.
    "LINUX_AMD64_SUPPORT" : {
      "os_arch" : {
        "linux" : {
          "amd64" : {
            "path": "tests/support.txt",
            "digest": "sha512:c02b248975b267f4200603ff2ae40b9d0cdefad4a792f386d610f2b14fb4e67e288c235fd11ed596dd8c91a3dae62fdd741bf97b5c01b5f085485f221702f0a1",
          },
          "<others>": {"optional": True},
        },
        "<others>" : {"<others>": {"optional": True}},
      },
    },
    # This is a dummy library for amd64 support.
    "AMD64_SUPPORT" : {
      "arch" : {
        "amd64" : {
          "path": "tests/support.txt",
          "digest": "sha512:c02b248975b267f4200603ff2ae40b9d0cdefad4a792f386d610f2b14fb4e67e288c235fd11ed596dd8c91a3dae62fdd741bf97b5c01b5f085485f221702f0a1",
        },
        "<others>": {"optional": True},
      },
    },
    # This is a dummy library for amd64 support.
    "AARCH64_SUPPORT" : {
      "arch" : {
        "aarch64" : {
          "path": "tests/support.txt",
          "digest": "sha512:c02b248975b267f4200603ff2ae40b9d0cdefad4a792f386d610f2b14fb4e67e288c235fd11ed596dd8c91a3dae62fdd741bf97b5c01b5f085485f221702f0a1",
        },
        "<others>": {"optional": True},
      },
    },
    # This is a dummy library for marking sulong native mode support.
    "NATIVE_MODE_SUPPORT" : {
      "os" : {
        "windows" : {"optional": True},
        "<others>" : {
          "path": "tests/support.txt",
          "digest": "sha512:c02b248975b267f4200603ff2ae40b9d0cdefad4a792f386d610f2b14fb4e67e288c235fd11ed596dd8c91a3dae62fdd741bf97b5c01b5f085485f221702f0a1",
        },
      },
    },
    # This is a dummy library for disabling tests that won't compile because of missing GNU make.
    "UNIX_SUPPORT" : {
      "os" : {
        "windows" : {"optional": True},
        "<others>" : {
          "path": "tests/support.txt",
          "digest": "sha512:c02b248975b267f4200603ff2ae40b9d0cdefad4a792f386d610f2b14fb4e67e288c235fd11ed596dd8c91a3dae62fdd741bf97b5c01b5f085485f221702f0a1",
        },
      },
    },
    # This is a dummy library for projects that are only compiled on windows
    "WINDOWS_SUPPORT" : {
      "os" : {
        "windows" : {
          "path": "tests/support.txt",
          "digest": "sha512:c02b248975b267f4200603ff2ae40b9d0cdefad4a792f386d610f2b14fb4e67e288c235fd11ed596dd8c91a3dae62fdd741bf97b5c01b5f085485f221702f0a1",
        },
        "<others>" : {"optional": True},
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
        "com.oracle.truffle.llvm.tests.pipe",
        "com.oracle.truffle.llvm.tests.harness",
        "truffle:TRUFFLE_TCK",
        "mx:JUNIT",
      ],
      "requires" : [
        "java.logging",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "javaCompliance" : "17+",
      "javaProperties" : {
        "test.sulongtest.harness" : "<path:com.oracle.truffle.llvm.tests.harness>/TestHarness/bin",
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
    "com.oracle.truffle.llvm.tests.api" : {
      "subDir" : "tests",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.llvm.api",
        "com.oracle.truffle.llvm.tests.pipe",
        "truffle:TRUFFLE_TCK",
        "mx:JUNIT",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "javaCompliance" : "17+",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
      "testProject" : True,
      "jacoco" : "exclude",
    },
    "com.oracle.truffle.llvm.tests.debug" : {
      "subDir" : "tests",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.llvm.tests",
        "com.oracle.truffle.llvm.tests.pipe",
        "truffle:TRUFFLE_TCK",
        "mx:JUNIT",
      ],
      "requires" : [
        "java.logging",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "javaCompliance" : "17+",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
      "testProject" : True,
      "jacoco" : "exclude",
    },
    "com.oracle.truffle.llvm.tests.interop" : {
      "subDir" : "tests",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.llvm",
        "com.oracle.truffle.llvm.tests",
        "truffle:TRUFFLE_TCK",
        "mx:JUNIT",
      ],
      "requires" : [
        "jdk.unsupported", # sun.misc.Unsafe
      ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "javaCompliance" : "17+",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
      "testProject" : True,
      "jacoco" : "exclude",
    },
    "com.oracle.truffle.llvm.tests.harness" : {
      "subDir" : "tests",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.llvm.tests.pipe",
        "truffle:TRUFFLE_API",
        "mx:JUNIT",
      ],
      "requires" : [
        "java.logging",
        "jdk.unsupported", # sun.misc.Unsafe
      ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "javaCompliance" : "17+",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
      "testProject" : True,
      "jacoco" : "exclude",
    },
    "com.oracle.truffle.llvm.tests.native" : {
      "class" : "CMakeNinjaProject",
      "toolchain" : "SULONG_BOOTSTRAP_TOOLCHAIN",
      "subDir" : "tests",
      "ninja_targets" : [
        "default",
      ],
      "results" : ["<lib:sulongtest>"],
      "os" : {
        "windows" : {"results" : ["<staticlib:sulongtest>"]},
        "<others>" : {},
      },
      "buildDependencies" : ["SULONG_BOOTSTRAP_TOOLCHAIN"],
      "license" : "BSD-new",
      "testProject" : True,
    },
    "com.oracle.truffle.llvm.tests.internal" : {
      "subDir" : "tests",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.llvm.tests",
        "com.oracle.truffle.llvm.runtime",
        "truffle:TRUFFLE_TCK",
        "mx:JUNIT",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "javaCompliance" : "17+",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
      "testProject" : True,
      "jacoco" : "exclude",
    },
    "com.oracle.truffle.llvm.tests.tck" : {
      "subDir" : "tests",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JUNIT",
        "sdk:POLYGLOT_TCK",
      ],
      "buildDependencies" : [
        "NATIVE_MODE_SUPPORT",
        "SULONG_TCK_NATIVE",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "javaCompliance" : "17+",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
      "testProject" : True,
      "jacoco" : "exclude",
    },
    "com.oracle.truffle.llvm.tests.tck.native" : {
      "subDir" : "tests",
      "native" : True,
      "vpath" : True,
      "results" : ["bin/"],
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
    "com.oracle.truffle.llvm.api" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : ["truffle:TRUFFLE_API"],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "javaCompliance" : "17+",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
      "jacoco" : "include",
    },
    "com.oracle.truffle.llvm.spi" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : ["truffle:TRUFFLE_API"],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
      "jacoco" : "include",
    },

    "com.oracle.truffle.llvm.nfi.test.native" : {
      "subDir" : "projects",
      "class" : "CopiedNativeProject",
      "srcFrom" : "truffle:com.oracle.truffle.nfi.test.native",
      "toolchain" : "SULONG_BOOTSTRAP_TOOLCHAIN",
      "buildDependencies" : [
        "SULONG_BOOTSTRAP_TOOLCHAIN",
        "truffle:TRUFFLE_NFI_NATIVE",
      ],
      "workingSets" : "Truffle, LLVM",
      "testProject" : True,
      "defaultBuild" : False,
      "jacoco" : "exclude",
      "os_arch" : {
        "windows" : {
          "<others>" : {
            "ldflags" : ["-shared"]
          },
        },
        "<others>" : {"<others>" : {}},
      },
    },

    "com.oracle.truffle.llvm.nfi.test.native.isolation" : {
      "subDir" : "projects",
      "class" : "CopiedNativeProject",
      "srcFrom" : "truffle:com.oracle.truffle.nfi.test.native.isolation",
      "toolchain" : "SULONG_BOOTSTRAP_TOOLCHAIN",
      "buildDependencies" : [
        "SULONG_BOOTSTRAP_TOOLCHAIN",
        "truffle:TRUFFLE_NFI_NATIVE",
      ],
      "workingSets" : "Truffle, LLVM",
      "testProject" : True,
      "defaultBuild" : False,
      "jacoco" : "exclude",
      "os_arch" : {
        "windows" : {
          "<others>" : {
            "ldflags" : ["-shared"]
          },
        },
        "<others>" : {"<others>" : {}},
      },
    },

    "com.oracle.truffle.llvm.nativemode" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "truffle:TRUFFLE_NFI",
        "SULONG_CORE"
      ],
      "requires" : [
        "jdk.unsupported", # sun.misc.Unsafe
      ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "javaCompliance" : "17+",
      "workingSets" : "Truffle, LLVM",
      "license" : "BSD-new",
      "jacoco" : "include",
    },

    "com.oracle.truffle.llvm.nativemode.resources" : {
      "subDir" : "projects",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "sdk:NATIVEIMAGE",
        "truffle:TRUFFLE_API",
        "sulong:SULONG_API",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "javaCompliance" : "17+",
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
        "truffle:TRUFFLE_ANTLR4",
        "com.oracle.truffle.llvm.api",
        "com.oracle.truffle.llvm.spi",
      ],
      "requires" : [
        "java.logging",
        "jdk.unsupported", # sun.misc.Signal
      ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "checkstyleVersion" : "10.7.0",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "javaCompliance" : "17+",
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
      "requires" : [
        "java.logging",
        "java.xml",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
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
      "class" : "CMakeNinjaProject",
      "subDir": "tests",
      "vpath": True,
      "platformDependent": True,
      "ninja_targets" : ["all"],
      "ninja_install_targets" : ["test"],
      "results" : ["main.out"],
      "cmakeConfig" : {
        "SULONG_EXE" : "<lli_path>",
        "CMAKE_C_COMPILER": "<toolchainGetToolPath:native,CC>",
        "CMAKE_CXX_COMPILER": "<toolchainGetToolPath:native,CXX>",
        "SULONG_C_COMPILER": "<toolchainGetToolPath:native,CC>",
        "SULONG_CXX_COMPILER": "<toolchainGetToolPath:native,CXX>",
        "SULONG_LINKER": "<toolchainGetToolPath:native,LD>",
        "SULONG_LIB" : "<path:SULONG_HOME>/native/lib",
        "SULONG_OBJDUMP" : "<path:LLVM_TOOLCHAIN>/bin/<exe:llvm-objdump>",
        "SULONG_NATIVE_BUILD" : "True",
        "STANDALONE_MODE" : "<llvm_standalone_mode>",
      },
      "buildEnv" : {
        "CTEST_PARALLEL_LEVEL" : "16",
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
        "truffle:TRUFFLE_ANTLR4",
      ],
      "checkstyle" : "com.oracle.truffle.llvm.runtime",
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
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
      "os" : {
        "windows" : {},
        "solaris" : {
          "cflags" : ["-g", "-Wall", "-Werror", "-m64"],
          "ldflags" : ["-m64"],
        },
        "<others>" : {
          "cflags" : ["-g", "-Wall", "-Werror"],
        },
      },
    },
    "com.oracle.truffle.llvm.libraries.bitcode" : {
      "subDir" : "projects",
      "class" : "CMakeNinjaProject",
      "toolchain" : "SULONG_BOOTSTRAP_TOOLCHAIN_NO_HOME",
      # NinjaBuildTask uses only 1 job otherwise
      "max_jobs" : "8",
      "vpath" : True,
      "ninja_targets" : [
        "<lib:sulong>",
        "<lib:sulong++>",
      ],
      "results" : [
        "bin/<lib:sulong>",
        "bin/<lib:sulong++>",
      ],
      "ninja_install_targets" : ["install"],
      "buildDependencies" : [
        "sdk:LLVM_TOOLCHAIN",
        "sdk:LLVM_ORG_SRC",
        "SULONG_BOOTSTRAP_TOOLCHAIN_NO_HOME",
        "SULONG_NATIVE_HOME",
      ],
      "cmakeConfig" : {
        "CMAKE_OSX_DEPLOYMENT_TARGET" : "10.13",
        "GRAALVM_LLVM_INCLUDE_DIR" : "<path:com.oracle.truffle.llvm.libraries.graalvm.llvm>/include",
        "GRAALVM_LLVM_LIBS_INCLUDE_DIR" : "<path:com.oracle.truffle.llvm.libraries.graalvm.llvm.libs>/include",
        "GRAALVM_LLVM_LIB_DIR" : "<path:SULONG_NATIVE_HOME>/native/lib",
        "LIBCXX_ISYSTEM" : "<path:SULONG_NATIVE_HOME>/include/c++/v1",
        "LIBCXX_SRC" : "<path:sdk:LLVM_ORG_SRC>",
        "MX_OS" : "<os>",
        "MX_ARCH" : "<arch>",
      },
      "os_arch" : {
        "windows" : {
          "<others>" : {
            "cmakeConfig" : {
              "GRAALVM_PTHREAD_INCLUDE_DIR" : "<path:com.oracle.truffle.llvm.libraries.pthread>/include",
            },
          },
        },
        "<others>" : {"<others>" : {}},
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
        "include/graalvm/llvm/polyglot-buffer.h",
        "include/graalvm/llvm/polyglot-time.h",
        "include/graalvm/llvm/toolchain-api.h",
        "include/graalvm/llvm/internal/handles-impl.h",
        "include/graalvm/llvm/internal/polyglot-impl.h",
        "include/graalvm/llvm/internal/polyglot-time-impl.h",
        # for source compatibility
        "include/polyglot.h",
        "include/llvm/api/toolchain.h",
      ],
      "license" : "BSD-new",
    },
    "com.oracle.truffle.llvm.libraries.graalvm.llvm.libs" : {
      "subDir" : "projects",
      "class" : "CMakeNinjaProject",
      "toolchain" : "SULONG_BOOTSTRAP_TOOLCHAIN_NO_HOME",
      # NinjaBuildTask uses only 1 job otherwise
      "max_jobs" : "8",
      "vpath" : True,
      "ninja_install_targets" : ["install"],
      "ninja_targets" : ["<libv:graalvm-llvm.1>"],
      # We on purpose exclude the symlink from the results because the layout
      # distribution would dereference it and create a copy instead of keeping
      # the symlink. The symlink is added manually in the layout definition of
      # the distribution.
      "results" : ["bin/<libv:graalvm-llvm.1>"],
      "os" : {
        "windows" : {
          "ninja_targets" : ["<staticlib:graalvm-llvm>"],
          "results" : ["bin/<staticlib:graalvm-llvm>"],
        },
        "<others>" : {},
      },
      "buildDependencies" : [
        "SULONG_BOOTSTRAP_TOOLCHAIN_NO_HOME",
        "com.oracle.truffle.llvm.libraries.graalvm.llvm",
      ],
      "cmakeConfig" : {
        "CMAKE_OSX_DEPLOYMENT_TARGET" : "10.13",
        "GRAALVM_LLVM_INCLUDE_DIR" : "<path:com.oracle.truffle.llvm.libraries.graalvm.llvm>/include",
      },
      "license" : "BSD-new",
    },

    "com.oracle.truffle.llvm.libraries.pthread" : {
      "subDir" : "projects",
      "class" : "CMakeNinjaProject",
      "toolchain" : "SULONG_BOOTSTRAP_TOOLCHAIN_NO_HOME",
      # NinjaBuildTask uses only 1 job otherwise
      "max_jobs" : "8",
      "vpath" : True,
      "results" : [
        "lib/<lib:pthread>",
        "lib/<staticlib:pthread>",
        "include/pthread.h",
      ],
      "ninja_install_targets" : ["install"],
      "buildDependencies": [
        "SULONG_BOOTSTRAP_TOOLCHAIN_NO_HOME",
        "WINDOWS_SUPPORT",
      ],
     "license" : "BSD-new",
    },

    "com.oracle.truffle.llvm.libraries.native" : {
      "subDir" : "projects",
      "class" : "CMakeNinjaProject",
      "toolchain" : "SULONG_BOOTSTRAP_TOOLCHAIN_NO_HOME",
      # NinjaBuildTask uses only 1 job otherwise
      "max_jobs" : "8",
      "vpath" : True,
      "ninja_install_targets" : ["install"],
      "buildDependencies" : [
        "SULONG_BOOTSTRAP_TOOLCHAIN_NO_HOME",
        "truffle:TRUFFLE_NFI_NATIVE",
        "sdk:LLVM_TOOLCHAIN",
      ],
      "cmakeConfig" : {
        "CMAKE_OSX_DEPLOYMENT_TARGET" : "10.13",
        "TRUFFLE_NFI_NATIVE_INCLUDE" : "<path:truffle:TRUFFLE_NFI_NATIVE>/include",
      },
      "ninja_targets" : ["<lib:sulong-native>"],
      "results" : ["bin/<lib:sulong-native>"],
      "os" : {
        "windows" : {
          "ninja_targets" : ["<staticlib:sulong-native>"],
          "results" : ["bin/<staticlib:sulong-native>"],
          "cmakeConfig" : {
            "CMAKE_WINDOWS_EXPORT_ALL_SYMBOLS" : "YES",
          },
        },
        "<others>" : {
          "cmakeConfig" : {
            "CMAKE_SHARED_LINKER_FLAGS" : "-lm"
          },
        },
      },
      "license" : "BSD-new",
    },

    "com.oracle.truffle.llvm.libraries.oldnames" : {
      "subDir" : "projects",
      "class" : "CMakeNinjaProject",
      "toolchain" : "SULONG_BOOTSTRAP_TOOLCHAIN_NO_HOME",
      # NinjaBuildTask uses only 1 job otherwise
      "max_jobs" : "8",
      "vpath" : True,
      "ninja_targets" : ["oldnames"],
      "results" : ["<staticlib:oldnames>"],
      "buildDependencies" : [
        "WINDOWS_SUPPORT",
        "SULONG_BOOTSTRAP_TOOLCHAIN_NO_HOME",
      ],
      "license" : "BSD-new",
    },

    "com.oracle.truffle.llvm.libraries.bitcode.libcxx" : {
      "subDir" : "projects",
      "vpath" : True,
      "sourceDir" : "<path:sdk:LLVM_ORG_SRC>",
      "cmakeSubdir" : "runtimes",
      "symlinkSource" : True,
      "class" : "CMakeNinjaProject",
      "toolchain" : "SULONG_BOOTSTRAP_TOOLCHAIN_NO_HOME",
      # NinjaBuildTask uses only 1 job otherwise
      "max_jobs" : "8",
      "cmakeConfig" : {
        "LIBCXXABI_INCLUDE_TESTS": "NO",
        "LIBCXXABI_ENABLE_STATIC" : "NO",
        "LIBCXX_INCLUDE_BENCHMARKS": "NO",
        "LIBCXX_INCLUDE_TESTS": "NO",
        "LIBCXX_ENABLE_STATIC" : "NO",
        "CMAKE_INSTALL_PREFIX" : "native",
        # workaround for build problem with cmake >=3.22
        # see https://lists.llvm.org/pipermail/llvm-dev/2021-December/154144.html
        "CMAKE_BUILD_WITH_INSTALL_RPATH" : "YES",
      },
      "ninja_targets" : ["cxx"],
      "ninja_install_targets" : ["install-cxx"],
      "os" : {
        "<others>" : {
          "ninja_targets" : ["cxxabi", "unwind"],
          "ninja_install_targets" : ["install-cxxabi", "install-unwind"],
          "results" : ["native"],
          "cmakeConfig" : {
            "CMAKE_INSTALL_RPATH" : "\\$ORIGIN",
            "LLVM_ENABLE_RUNTIMES" : "libcxx;libcxxabi;libunwind",
          },
        },
        "linux-musl" : {
          "ninja_targets" : ["cxxabi", "unwind"],
          "ninja_install_targets" : ["install-cxxabi", "install-unwind"],
          "results" : ["native"],
          "cmakeConfig" : {
            "CMAKE_INSTALL_RPATH" : "\\$ORIGIN",
            "LLVM_ENABLE_RUNTIMES" : "libcxx;libcxxabi;libunwind",
            "LIBCXX_HAS_MUSL_LIBC" : "YES",
          },
        },
        "darwin" : {
          "ninja_targets" : ["cxxabi", "unwind"],
          "ninja_install_targets" : ["install-cxxabi", "install-unwind"],
          "results" : ["native"],
          "cmakeConfig" : {
            "CMAKE_INSTALL_RPATH" : "@loader_path/",
            "LLVM_ENABLE_RUNTIMES" : "libcxx;libcxxabi;libunwind",
            "CMAKE_LIBTOOL" : "<path:LLVM_TOOLCHAIN>/bin/llvm-libtool-darwin",
          },
        },
        "windows" : {
          "results" : ["native/lib/c++.lib", "native/bin/<lib:c++>", "native/include"],
          "cmakeConfig" : {
            "LLVM_ENABLE_RUNTIMES" : "libcxx",
            "SULONG_CMAKE_PRE_315" : "True",
            # On Windows libcxx must be compiled using the cl compatible
            # compiler rather than the clang toolchain
            "CMAKE_AR" : "<path:LLVM_TOOLCHAIN>/bin/llvm-lib.exe",
            "CMAKE_C_COMPILER" : "<path:SULONG_BOOTSTRAP_TOOLCHAIN_NO_HOME>/bin/<cmd:clang-cl>",
            "CMAKE_CXX_COMPILER" : "<path:SULONG_BOOTSTRAP_TOOLCHAIN_NO_HOME>/bin/<cmd:clang-cl>",
            "CMAKE_LINKER" : "<path:SULONG_BOOTSTRAP_TOOLCHAIN_NO_HOME>/bin/<cmd:lld-link>",
            "CMAKE_C_FLAGS" : "/MD -flto -DNDEBUG -O1",
            "CMAKE_CXX_FLAGS" : "/MD -flto -DNDEBUG -O1",
            "CMAKE_SHARED_LINKER_FLAGS" : "/debug",
          }
        },
      },
      "buildDependencies" : [
        "sdk:LLVM_ORG_SRC",
        "SULONG_BOOTSTRAP_TOOLCHAIN_NO_HOME",
        "sdk:LLVM_TOOLCHAIN",
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
      "dependencies" : ["SULONG_TEST"],
      "buildDependencies" : ["SULONG_HOME"],
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
      "dependencies" : ["SULONG_TEST"],
      "buildDependencies" : ["SULONG_HOME"],
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
      "dependencies" : ["SULONG_TEST"],
      "buildDependencies" : ["SULONG_HOME"],
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
        "CMAKE_C_FLAGS" : "-Wno-unused-function -I<path:SULONG_LEGACY>/include -pthread",
        "CMAKE_C_LINK_FLAGS" : "-pthread",
        "CMAKE_CXX_FLAGS" : "-Wno-unused-function -I<path:SULONG_LEGACY>/include",
      },
      "os" : {
        "linux": {
          "cmakeConfig" : {
            "CMAKE_SHARED_LINKER_FLAGS" : "--no-undefined -Wl,--undefined=callbackPointerArgTest -lsulongtest -L<path:SULONG_TEST_NATIVE>",
          },
        },
        "<others>": {
          "cmakeConfig" : {
            "CMAKE_SHARED_LINKER_FLAGS" : "-lsulongtest -L<path:SULONG_TEST_NATIVE>",
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
      "dependencies" : ["SULONG_TEST"],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.sulong.Os.native" : {
      "subDir" : "tests",
      "class" : "SulongCMakeTestSuite",
      "variants" : ["bitcode-Os"],
      "dependencies" : ["SULONG_TEST"],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.bitcode.native" : {
      "subDir" : "tests",
      "class" : "SulongCMakeTestSuite",
      "bundledLLVMOnly" : True,
      "variants" : ["bitcode-O0"],
      "fileExts" : [".ll"],
      "os" : {
        "<others>" : {
          "cmakeConfig" : {
            "CMAKE_C_LINK_FLAGS" : "-lm",
          },
        },
        "windows" : {},
      },
      "dependencies" : ["SULONG_TEST"],
      "buildDependencies" : ["LINUX_AMD64_SUPPORT"],
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
      "buildDependencies" : ["LINUX_AMD64_SUPPORT"],
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
      "dependencies" : ["SULONG_TEST"],
      "buildDependencies" : ["LINUX_AMD64_SUPPORT"],
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
      "dependencies" : ["SULONG_TEST"],
      "buildDependencies" : ["LINUX_AMD64_SUPPORT"],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.pthread.native" : {
      "subDir" : "tests",
      "class" : "SulongCMakeTestSuite",
      "variants" : ["bitcode-O0"],
      "dependencies" : ["SULONG_TEST"],
      "os" : {
        "windows" : {
          "cmakeConfig" : {
            "CMAKE_C_FLAGS" : "-I<path:SULONG_HOME>/include -I<path:com.oracle.truffle.llvm.tests.libc.native>/include/win -L<path:SULONG_HOME>/native/lib",
            "TOOLCHAIN_CLANG" : "<toolchainGetToolPath:native,CC>",
            "TOOLCHAIN_CLANGXX" : "<toolchainGetToolPath:native,CXX>",
          },
          "buildDependencies" : [
            "SULONG_HOME",
          ],
        },
        "<others>" : {},
      },
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
      "dependencies" : ["SULONG_TEST"],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.sulongcpp.native" : {
      "subDir" : "tests",
      "class" : "SulongCMakeTestSuite",
      "variants" : ["toolchain-plain"],
      "cmakeConfig" : {
        "CMAKE_CXX_FLAGS" : "-pthread",
        "TOOLCHAIN_CLANG" : "<toolchainGetToolPath:native,CC>",
        "TOOLCHAIN_CLANGXX" : "<toolchainGetToolPath:native,CXX>",
      },
      "dependencies" : [
        "SULONG_HOME",
        "SULONG_TEST",
        "SULONG_BOOTSTRAP_TOOLCHAIN",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.libc.native" : {
      "subDir" : "tests",
      "class" : "SulongCMakeTestSuite",
      "variants" : ["toolchain-plain"],
      "cmakeConfig" : {
        "TOOLCHAIN_CLANG" : "<toolchainGetToolPath:native,CC>",
        "TOOLCHAIN_CLANGXX" : "<toolchainGetToolPath:native,CXX>",
      },
      "dependencies" : ["SULONG_TEST"],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.inlineasm.native" : {
      "subDir" : "tests",
      "class" : "SulongCMakeTestSuite",
      "variants" : ["bitcode-O0"],
      "buildDependencies" : ["LINUX_AMD64_SUPPORT"],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.inlineasm.aarch64.native" : {
      "subDir" : "tests",
      "class" : "SulongCMakeTestSuite",
      "variants" : ["bitcode-O0"],
      "buildDependencies" : ["AARCH64_SUPPORT"],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.standalone.other.native" : {
      "subDir" : "tests",
      "class" : "SulongCMakeTestSuite",
      "variants" : ["bitcode-O1"],
      "buildRef" : False,
      "os" : {
        "<others>" : {
          "cmakeConfig" : {
            "CMAKE_EXE_LINKER_FLAGS" : "-lm",
          },
        },
        "windows" : {},
      },
      "dependencies" : ["SULONG_TEST"],
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
        "bitcodeformat/KERNEL32.dll",
      ],
      "buildEnv": {
        "SUITE_CPPFLAGS": "-I<path:SULONG_LEGACY>/include -I<path:SULONG_HOME>/include",
      },
      "dependencies": ["SULONG_TEST"],
      "buildDependencies": [
        "SULONG_HOME",
        "UNIX_SUPPORT"
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
      "buildDependencies" : ["UNIX_SUPPORT"],
      "results": [
        "dynLink",
        "linker",
        "runtimepath",
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
      "buildDependencies" : ["UNIX_SUPPORT"],
      "results": [
        "dlopenAbsolute",
        "dlopenLocator",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.embedded.custom.native" : {
      "subDir" : "tests",
      "class" : "CMakeNinjaProject",
      "description" : "Embedded tests with custom cmake files",
      "ninja_targets" : ["default"],
      "results": ["interop"],
      "cmakeConfig" : {
        "CMAKE_BUILD_TYPE" : "Sulong",
        "CMAKE_C_COMPILER": "<toolchainGetToolPath:native,CC>",
        "CMAKE_CXX_COMPILER": "<toolchainGetToolPath:native,CXX>",
        "GRAALVM_LLVM_INCLUDE_DIR": "<path:com.oracle.truffle.llvm.libraries.graalvm.llvm>/include",
        "GRAALVM_LLVM_LIB_DIR" : "<path:SULONG_NATIVE_HOME>/native/lib",
      },
      "dependencies" : [
        "SULONG_TEST",
        "SULONG_TOOLCHAIN_LAUNCHERS",
        "SULONG_BOOTSTRAP_TOOLCHAIN",
      ],
      "testProject" : True,
      "defaultBuild" : False,
    },
    "com.oracle.truffle.llvm.tests.va.native" : {
      "subDir" : "tests",
      "native": True,
      "vpath": True,
      # TODO: can this section be simplified?
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
        },
		"<others>": {
          "<others>" : {
            "buildEnv" : {
              "PLATFORM" : "unknown_platform",
            },
          },
        },
      },
      "buildEnv" : {
        "OS" : "<os>",
        "CLANG": "<toolchainGetToolPath:native,CC>",
        "SRC_DIR": "<path:com.oracle.truffle.llvm.tests.va.native>",
      },
      "buildDependencies" : ["UNIX_SUPPORT"],
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
    "com.oracle.truffle.llvm.tests.sulongobjc.native" : {
      "subDir" : "tests",
      "native": True,
      "vpath": True,
      "results": ["objc"],
      "buildEnv" : {
        "OS" : "<os>",
        "CLANG": "<toolchainGetToolPath:native,CC>",
        "SRC_DIR": "<path:com.oracle.truffle.llvm.tests.sulongobjc.native>",
      },
      "buildDependencies" : ["UNIX_SUPPORT"],
      "dependencies" : [
        "SULONG_TEST",
        "SULONG_TOOLCHAIN_LAUNCHERS",
        "SULONG_BOOTSTRAP_TOOLCHAIN",
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
      "variants" : ["toolchain-O0"],
      "buildRef" : True,
      "os" : {
        "windows" : {
          "cmakeConfig" : {
            "CMAKE_C_FLAGS" : "-Wno-everything -include stdio.h",
          },
        },
        "<others>": {
          "cmakeConfig" : {
            "CMAKE_C_FLAGS" : "-Wno-everything",
          },
        },
      },
      "dependencies" : ["SULONG_TEST"],
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
      "variants" : ["toolchain-O0"],
      "buildRef" : True,
      "os" : {
        "windows" : {
          "cmakeConfig" : {
            "CMAKE_CXX_FLAGS" : "-Wno-everything -include stdio.h",
          },
        },
        "<others>": {
          "cmakeConfig" : {
            "CMAKE_CXX_FLAGS" : "-Wno-everything",
          },
        },
      },
      "dependencies" : ["SULONG_TEST"],
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
      "dependencies" : ["SULONG_TEST"],
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
      "dependencies" : ["SULONG_TEST"],
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
      "variants" : ["toolchain-O0"],
      "buildRef" : True,
      "os_arch" : {
        "darwin": {
          "aarch64" : {
            "cmakeConfig" : {
              "CMAKE_C_FLAGS" : "-Wno-everything",
              "CMAKE_CXX_FLAGS" : "-Wno-everything",
              "CMAKE_EXE_LINKER_FLAGS" : "-L/opt/homebrew/lib -lm -lgmp",
            },
          },
          "amd64": {
            "cmakeConfig" : {
              "CMAKE_C_FLAGS" : "-Wno-everything",
              "CMAKE_CXX_FLAGS" : "-Wno-everything",
            },
            "buildEnv" : {
              "CMAKE_EXE_LINKER_FLAGS" : "-lm -lgmp",
            },
          },
        },
		"windows": {
          "<others>": {
            "cmakeConfig" : {
              "CMAKE_C_FLAGS" : "-Wno-everything -include stdio.h",
              "CMAKE_CXX_FLAGS" : "-Wno-everything -include stdio.h",
            },
            "buildEnv" : {
              "CMAKE_EXE_LINKER_FLAGS" : "-lm -lgmp",
            },
          },
        },
		"<others>": {
          "<others>": {
            "cmakeConfig" : {
              "CMAKE_C_FLAGS" : "-Wno-everything",
              "CMAKE_CXX_FLAGS" : "-Wno-everything",
            },
            "buildEnv" : {
              "CMAKE_EXE_LINKER_FLAGS" : "-lm -lgmp",
            },
          },
        },
      },
      "dependencies" : ["SULONG_TEST"],
      "buildDependencies" : ["LLVM_TEST_SUITE"],
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
        "CMAKE_C_FLAGS" : "-Wno-everything -include stdio.h",
        "CMAKE_EXE_LINKER_FLAGS" : "-lm -lgmp",
      },
      "dependencies" : ["SULONG_TEST"],
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
      "os_arch" : {
        "windows" : {
          "<others>" : {
            "cmakeConfig" : {
              "CMAKE_C_FLAGS" : "-Wno-everything -include stdio.h -include memory.h",
            },
          },
        },
        "<others>" : {
          "<others>" : {
            "cmakeConfig" : {
              "CMAKE_C_FLAGS" : "-Wno-everything",
            },
          },
        },
      },
      "dependencies" : ["SULONG_TEST"],
      "buildDependencies" : ["NWCC_SUITE"],
      "testProject" : True,
      "defaultBuild" : False,
    },
  },

  "distributions" : {
    "SULONG_CORE" : {
      "description" : "Sulong core functionality (parser, execution engine, launcher)",
      "moduleInfo" : {
        "name" : "org.graalvm.llvm_community",
        "exports" : [
          "* to org.graalvm.llvm.nativemode,org.graalvm.llvm,org.graalvm.llvm.managed,org.graalvm.llvm.nativemode_community",
        ],
        "uses" : [
          "com.oracle.truffle.llvm.runtime.config.ConfigurationFactory",
          "com.oracle.truffle.llvm.spi.internal.LLVMResourceProvider",
        ],
        "requires": [
          "org.graalvm.collections",
          "org.graalvm.polyglot",
        ],
      },
      "useModulePath" : True,
      "subDir" : "projects",
      "dependencies" : [
        "com.oracle.truffle.llvm",
        "com.oracle.truffle.llvm.parser.factories",
      ],
      "distDependencies" : [
        "truffle:TRUFFLE_API",
        "truffle:TRUFFLE_NFI",
        "truffle:TRUFFLE_ANTLR4",
        "SULONG_API",
      ],
      "javaProperties" : {
        "org.graalvm.language.llvm.home": "<path:SULONG_HOME>",
      },
      "maven": {
        "artifactId": "llvm-language",
        "tag": ["default", "public"],
      },
      "license": "BSD-new",
      "noMavenJavadoc": True,
    },

    "LLVM_NATIVE_COMMUNITY": {
      "type": "pom",
      "runtimeDependencies": [
        "SULONG_CORE",
        "SULONG_NATIVE",
        "SULONG_NATIVE_RESOURCES",
        "SULONG_NFI",
        "truffle:TRUFFLE_RUNTIME",
      ],
      "maven": {
        "artifactId": "llvm-native-community",
        "tag": ["default", "public"],
      },
      "description": "Graal native LLVM engine.",
      "license": "BSD-new",
    },

    "LLVM_COMMUNITY": {
      "type": "pom",
      "runtimeDependencies": [
        "LLVM_NATIVE_COMMUNITY",
      ],
      "maven": {
        "artifactId": "llvm-community",
        "tag": ["default", "public"],
      },
      "description": "Graal LLVM engine.",
      "license": "BSD-new",
    },

    "SULONG_API" : {
      "moduleInfo" : {
        "name" : "org.graalvm.llvm.api",
        "exports" : [
          "com.oracle.truffle.llvm.api",
          "com.oracle.truffle.llvm.spi",
          "com.oracle.truffle.llvm.spi.internal to org.graalvm.llvm_community,org.graalvm.llvm.nativemode.resources,org.graalvm.llvm.managed.resources",
        ],
      },
      "useModulePath" : True,
      "subDir" : "projects",
      "dependencies" : [
        "com.oracle.truffle.llvm.api",
        "com.oracle.truffle.llvm.spi",
      ],
      "distDependencies" : ["truffle:TRUFFLE_API"],
      "description" : "Graal LLVM API.",
      "maven" : {
        "artifactId" : "llvm-api",
        "tag": ["default", "public"],
      },
      "license" : "BSD-new",
      "allowsJavadocWarnings": True,  # GR-47782
    },
    "SULONG_NATIVE" : {
      "description" : "Sulong Native functionality (native memory support, native library support)",
      "moduleInfo" : {
        "name" : "org.graalvm.llvm.nativemode_community",
        "exports" : [
          "* to org.graalvm.llvm.nativemode",
        ],
        "requires": [
          "org.graalvm.collections",
          "org.graalvm.polyglot",
          "org.graalvm.truffle",
        ],
      },
      "useModulePath" : True,
      "subDir" : "projects",
      "dependencies" : ["com.oracle.truffle.llvm.nativemode"],
      "distDependencies" : [
        "SULONG_CORE",
        "truffle:TRUFFLE_NFI",
        "truffle:TRUFFLE_NFI_LIBFFI",
      ],
      "maven" : {
        "artifactId" : "llvm-language-native",
        "tag": ["default", "public"],
      },
      "license" : "BSD-new",
    },
    "SULONG_NATIVE_RESOURCES" : {
      "description" : "Module that contains resources needed by Sulong Native mode.",
      "moduleInfo" : {
        "name" : "org.graalvm.llvm.nativemode.resources",
        "requires" : [
          "static org.graalvm.nativeimage",
        ],
      },
      "useModulePath" : True,
      "subDir" : "projects",
      "dependencies" : ["com.oracle.truffle.llvm.nativemode.resources"],
      "distDependencies" : [
        "sdk:NATIVEIMAGE",
        "truffle:TRUFFLE_API",
        "SULONG_API",
        "SULONG_NATIVE_LIB_RESOURCES",
        "SULONG_NATIVE_BITCODE_RESOURCES",
      ],
      "maven" : {
        "artifactId" : "llvm-language-native-resources",
        "tag": ["default", "public"],
      },
      "license" : "BSD-new",
    },
    "SULONG_NFI" : {
      "description" : "Sulong NFI backend",
      "moduleInfo" : {
        "name" : "org.graalvm.llvm.nfi",
      },
      "useModulePath" : True,
      "subDir" : "projects",
      "dependencies" : ["com.oracle.truffle.llvm.nfi"],
      "distDependencies" : [
        "truffle:TRUFFLE_NFI",
        "SULONG_CORE",  # The SULONG_NFI in the runtime needs llvm language
      ],
      "maven" : {
        "artifactId" : "llvm-language-nfi",
        "tag": ["default", "public"],
      },
      "license" : "BSD-new",
    },

    "SULONG_LAUNCHER" : {
      "moduleInfo" : {
        "name" : "org.graalvm.llvm.launcher",
        "exports" : [
          "com.oracle.truffle.llvm.launcher to org.graalvm.launcher",
        ],
        "requires": [
          "org.graalvm.polyglot",
        ],
      },
      "useModulePath" : True,
      "subDir" : "projects",
      "mainClass" : "com.oracle.truffle.llvm.launcher.LLVMLauncher",
      "dependencies" : ["com.oracle.truffle.llvm.launcher"],
      "distDependencies" : ["sdk:LAUNCHER_COMMON"],
      "license" : "BSD-new",
      "maven" : False,
    },

    "SULONG_CMAKE_TOOLCHAIN" : {
      "native" : True,
      "relpath" : False,
      "platformDependent" : True,
      "license" : "BSD-new",
      "layout" : {
        "./cmake/" : ["file:cmake/toolchain.cmake"],
      }
    },

    "SULONG_NATIVE_HOME" : {
      "fileListPurpose": 'native-image-resources',
      "native" : True,
      "relpath" : False,
      "platformDependent" : True,
      "license" : "BSD-new",
      "layout" : {
        "./native/" : "extracted-dependency:SULONG_CMAKE_TOOLCHAIN/*",
        "./native/lib/" : [
          "dependency:com.oracle.truffle.llvm.libraries.native/bin/*",
          "dependency:com.oracle.truffle.llvm.libraries.graalvm.llvm.libs/bin/*",
        ],
      },
      "os" : {
        "windows" : {
          "layout" : {
            "./" : ["dependency:com.oracle.truffle.llvm.libraries.bitcode.libcxx/native/include"],
            "./native/lib/" : [
              "dependency:com.oracle.truffle.llvm.libraries.pthread/lib/*",
              "dependency:com.oracle.truffle.llvm.libraries.bitcode.libcxx/native/bin/*",
              "dependency:com.oracle.truffle.llvm.libraries.bitcode.libcxx/native/lib/*",
              "dependency:com.oracle.truffle.llvm.libraries.oldnames/<staticlib:oldnames>",
            ],
          },
        },
        "<others>" : {
          "layout" : {
            "./": ["dependency:com.oracle.truffle.llvm.libraries.bitcode.libcxx/*"],
            "./native/lib/<lib:graalvm-llvm>": "link:<libv:graalvm-llvm.1>",
            # for source compatibility
            "./native/lib/<lib:polyglot-mock>": "link:<lib:graalvm-llvm>",
          },
        },
      },
    },

    "SULONG_BITCODE_HOME" : {
      "fileListPurpose": 'native-image-resources',
      "native": True,
      "relpath": False,
      "license": "BSD-new",
      "layout" : {
        "./native/lib/" : [
          "dependency:com.oracle.truffle.llvm.libraries.bitcode/bin/<lib:sulong>",
          "dependency:com.oracle.truffle.llvm.libraries.bitcode/bin/<lib:sulong++>",
        ],
      },
    },

    "SULONG_CORE_HOME" : {
      "fileListPurpose": 'native-image-resources',
      "native" : True,
      "relpath" : False,
      "platformDependent" : True,
      "os" : {
        "windows" : {
          "layout" : {
            "./include/" : [
              "dependency:com.oracle.truffle.llvm.libraries.pthread/include/*",
              "dependency:com.oracle.truffle.llvm.libraries.graalvm.llvm/include/*",
            ],
          },
        },
        "<others>" :  {
          "layout" : {
            "./include/" : [
              "dependency:com.oracle.truffle.llvm.libraries.graalvm.llvm/include/*"
            ],
          }
        }
      },
      "license" : "BSD-new",
    },

    "SULONG_HOME_NATIVEMODE" : {
      "description" : "Only used as build dependency.",
      "native" : True,
      "relpath" : False,
      "platformDependent" : True,
      "layout" : {
        "./" : [
          "extracted-dependency:SULONG_NATIVE_HOME",
          "extracted-dependency:SULONG_BITCODE_HOME",
          "extracted-dependency:SULONG_CORE_HOME",
        ],
      },
      "license" : "BSD-new",
    },

    "SULONG_NATIVE_BITCODE_RESOURCES" : {
      "description" : "Contains the runtime dependencies needed by the LLVM runtime in native mode.",
      "type" : "dir",
      "platformDependent" : True,
      "hashEntry" :  "META-INF/resources/llvm/native/<os>/<arch>/sha256",
      "fileListEntry" : "META-INF/resources/llvm/native/<os>/<arch>/files",
      "platforms" : [
          "linux-amd64",
          "linux-aarch64",
          "darwin-amd64",
          "darwin-aarch64",
          "windows-amd64",
      ],
      "layout" : {
        "META-INF/resources/llvm/native/<os>/<arch>/lib/": [
          "extracted-dependency:SULONG_BITCODE_HOME/native/lib/*",
          "extracted-dependency:SULONG_NATIVE_HOME/native/lib/<libv:*.1>",
        ],
      },
      "defaultDereference": "always",
      "maven": False,
    },

    "SULONG_NATIVE_LIB_RESOURCES" : {
      "description" : "Contains the runtime dependencies needed by the LLVM runtime in native mode.",
      "type" : "dir",
      "platformDependent" : True,
      "hashEntry" :  "META-INF/resources/llvm/native-lib/<os>/<arch>/sha256",
      "fileListEntry" : "META-INF/resources/llvm/native-lib/<os>/<arch>/files",
      "platforms" : [
          "linux-amd64",
          "linux-aarch64",
          "darwin-amd64",
          "darwin-aarch64",
          "windows-amd64",
      ],
      "layout" : {
        "META-INF/resources/llvm/native-lib/<os>/<arch>/": [
          "dependency:com.oracle.truffle.llvm.libraries.native/bin/*",
        ],
      },
      "defaultDereference": "always",
      "maven": False,
    },

    "SULONG_TOOLCHAIN_LAUNCHERS": {
      "subDir" : "projects",
      "dependencies" : ["com.oracle.truffle.llvm.toolchain.launchers"],
      "distDependencies" : ["sdk:LAUNCHER_COMMON"],
      "license" : "BSD-new",
      "maven" : False,
    },

    "SULONG_BOOTSTRAP_TOOLCHAIN": {
      "native": True,
      "relpath": False,
      "platformDependent": True,
      "layout": {
        "./": [
          "dependency:bootstrap-toolchain-launchers/*",
          "extracted-dependency:SULONG_CMAKE_TOOLCHAIN/*"
        ],
      },
      "asm_requires_cpp": False,
      "buildDependencies" : ["SULONG_TOOLCHAIN_LAUNCHERS"],
      "license": "BSD-new",
    },

    "SULONG_BOOTSTRAP_TOOLCHAIN_NO_HOME": {
      "description" : "Bootstrap toolchain without an llvm.home. Use for bootstrapping libraries that should be contained in llvm.home.",
      "native": True,
      "relpath": False,
      "platformDependent": True,
      "layout": {
        "./": [
          "dependency:bootstrap-toolchain-launchers-no-home/*",
          "extracted-dependency:SULONG_CMAKE_TOOLCHAIN/*"
        ],
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
        "com.oracle.truffle.llvm.tests.pipe",
      ],
      "exclude" : ["mx:JUNIT"],
      "unittestConfig" : "sulong",
      "distDependencies" : [
        "truffle:TRUFFLE_TCK",
      ],
      "os" : {
        # not SULONG_TCK_NATIVE on windows
        "windows" : {},
        "<others>" : {
          "javaProperties" : {
            "test.sulongtck.path" : "<path:SULONG_TCK_NATIVE>/bin"
          },
        },
      },
      "license" : "BSD-new",
      "testDistribution" : True,
    },

    "SULONG_TEST_API" : {
      "subDir" : "tests",
      "dependencies" : [
        "com.oracle.truffle.llvm.tests.api",
      ],
      "exclude" : [
       "mx:JUNIT"
      ],
      "unittestConfig" : "sulong-internal",
      "distDependencies" : [
        "sulong:SULONG_API",
        "sulong:SULONG_TEST",
      ],
      "license" : "BSD-new",
      "testDistribution" : True,
    },

    "SULONG_TEST_INTERNAL" : {
      "subDir" : "tests",
      "dependencies" : [
        "com.oracle.truffle.llvm.tests.debug",
        "com.oracle.truffle.llvm.tests.internal",
        "com.oracle.truffle.llvm.tests.interop",
        "com.oracle.truffle.llvm.tests.tck"
      ],
      "exclude" : [
       "mx:JUNIT"
      ],
      "unittestConfig" : "sulong-internal",
      "distDependencies" : [
        "truffle:TRUFFLE_API",
        "truffle:TRUFFLE_TCK",
        "sulong:SULONG_NATIVE",
        "sulong:SULONG_CORE",
        "sulong:SULONG_NFI",
        "sulong:SULONG_LEGACY",
        "sulong:SULONG_TEST",
        "SULONG_TEST_NATIVE",
      ],
      "license" : "BSD-new",
      "testDistribution" : True,
    },

    "SULONG_TEST_NATIVE" : {
      "native" : True,
      "platformDependent" : True,
      "layout" : {
        "./": [
          "dependency:com.oracle.truffle.llvm.tests.pipe.native",
          "dependency:com.oracle.truffle.llvm.tests.native/*",
        ],
      },
      "license" : "BSD-new",
      "testDistribution" : True,
    },

    "LLIR_TEST_GEN" : {
      "relpath" : True,
      "mainClass" : "com.oracle.truffle.llvm.tests.llirtestgen.LLIRTestGen",
      "dependencies" : ["com.oracle.truffle.llvm.tests.llirtestgen"],
      "distDependencies" : ["SULONG_TEST"],
      "license" : "BSD-new",
      "testDistribution" : True,
      "defaultBuild" : False,
    },

    "LLIR_TEST_GEN_SOURCES" : {
      "description" : "Distribution for the generated ll source files.",
      "native" : True,
      "relpath" : True,
      "platformDependent" : True,
      "layout" : {
        "./" : ["dependency:com.oracle.truffle.llvm.tests.llirtestgen.generated/*"],
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
          "dependency:com.oracle.truffle.llvm.tests.sulong.Os.native/*",
          "dependency:com.oracle.truffle.llvm.tests.sulongavx.native/*",
          "dependency:com.oracle.truffle.llvm.tests.sulongcpp.native/*",
          "dependency:com.oracle.truffle.llvm.tests.sulongobjc.native/*",
          "dependency:com.oracle.truffle.llvm.tests.libc.native/*",
          "dependency:com.oracle.truffle.llvm.tests.linker.native/*",
          "dependency:com.oracle.truffle.llvm.tests.va.native/*",
          "dependency:com.oracle.truffle.llvm.tests.inlineasm.native/*",
          "dependency:com.oracle.truffle.llvm.tests.inlineasm.aarch64.native/*",
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
          # these are hand-crafted invalid bitcode files
          "file:tests/com.oracle.truffle.llvm.tests.bitcodeformat.invalid/invalid",
        ],
      },
      "license" : "BSD-new",
      "testDistribution" : True,
      "defaultBuild" : False,
    },
    "SULONG_NFI_TESTS" : {
      "description" : "The Truffle NFI test suite, but compiled with the Sulong toolchain.",
      "native" : True,
      "relpath" : True,
      "platformDependent" : True,
      "layout" : {
        "./" : [
            "dependency:com.oracle.truffle.llvm.nfi.test.native/*",
            "dependency:com.oracle.truffle.llvm.nfi.test.native.isolation/*",
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
        "./" : ["dependency:gcc_c/*"],
      },
      "testDistribution" : True,
      "defaultBuild" : False,
    },
    "SULONG_GCC_CPP_TEST_SUITE" : {
      "native" : True,
      "relpath" : True,
      "platformDependent" : True,
      "layout" : {
        "./" : ["dependency:gcc_cpp/*"],
      },
      "testDistribution" : True,
      "defaultBuild" : False,
    },
    "SULONG_GCC_FORTRAN_TEST_SUITE" : {
      "native" : True,
      "relpath" : True,
      "platformDependent" : True,
      "layout" : {
        "./" : ["dependency:gcc_fortran/*"],
      },
      "testDistribution" : True,
      "defaultBuild" : False,
    },
    "SULONG_PARSER_TORTURE" : {
      "native" : True,
      "relpath" : True,
      "platformDependent" : True,
      "layout" : {
        "./" : ["dependency:parserTorture/*"],
      },
      "testDistribution" : True,
      "defaultBuild" : False,
    },
    "SULONG_LLVM_TEST_SUITE" : {
      "native" : True,
      "relpath" : True,
      "platformDependent" : True,
      "layout" : {
        "./" : ["dependency:llvm/*"],
      },
      "testDistribution" : True,
      "defaultBuild" : False,
    },
    "SULONG_SHOOTOUT_TEST_SUITE" : {
      "native" : True,
      "relpath" : True,
      "platformDependent" : True,
      "layout" : {
        "./" : ["dependency:shootout/*"],
      },
      "testDistribution" : True,
      "defaultBuild" : False,
    },
    "SULONG_NWCC_TEST_SUITE" : {
      "native" : True,
      "relpath" : True,
      "platformDependent" : True,
      "layout" : {
        "./" : ["dependency:nwcc/*"],
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
        "./" : ["dependency:com.oracle.truffle.llvm.tests.tck.native/*"],
      },
      "license" : "BSD-new",
      "testDistribution" : True,
    },
    "SULONG_LEGACY" : {
      "native" : True,
      "layout" : {
        "./include/" : ["file:include/truffle.h"],
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
    "SULONG_GRAALVM_LICENSES" : {
      "fileListPurpose": 'native-image-resources',
      "native" : True,
      "platformDependent" : True,
      "description" : "Sulong license files for the GraalVM",
      "layout" : {
        "LICENSE_SULONG.txt" : "file:LICENSE",
        "THIRD_PARTY_LICENSE_SULONG.txt" : "file:THIRD_PARTY_LICENSE.txt",
      },
      "license" : "BSD-new",
    },
  }
}
