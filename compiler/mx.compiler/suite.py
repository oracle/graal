suite = {
  "mxversion": "6.27.1",
  "name" : "compiler",
  "sourceinprojectwhitelist" : [],

  "groupId" : "org.graalvm.compiler",
  "version" : "23.1.5",
  "release" : False,
  "url" : "http://www.graalvm.org/",
  "developer" : {
    "name" : "GraalVM Development",
    "email" : "graalvm-dev@oss.oracle.com",
    "organization" : "Oracle Corporation",
    "organizationUrl" : "http://www.graalvm.org/",
  },
  "scm" : {
    "url" : "https://github.com/oracle/graal",
    "read" : "https://github.com/oracle/graal.git",
    "write" : "git@github.com:oracle/graal.git",
  },

  "imports" : {
    "suites": [
      {
        "name" : "truffle",
        "subdir": True,
      },
      {
        "name" : "regex",
        "subdir": True
      },
      {
        "name" : "java-benchmarks",
        "subdir": True
      }
    ]
  },

  "defaultLicense" : "GPLv2-CPE",
  "snippetsPattern" : ".*JavadocSnippets.*",
  "javac.lint.overrides": "-path",

  "libraries" : {

    # ------------- Libraries -------------

    "JAVA_ALLOCATION_INSTRUMENTER" : {
      "digest" : "sha512:25fe57cd6d3ecabb52f411c884f801109ece37570a2dd19fa1e5b83cc2039ed02a90787600eb9303eaa730aabf0dc70b506fb9fe40ca6c3417428bb89c2c8940",
      "maven" : {
        "groupId" : "com.google.code.java-allocation-instrumenter",
        "artifactId" : "java-allocation-instrumenter",
        "version" : "3.1.0",
      },
      "bootClassPathAgent" : "true",
    },

    "HCFDIS" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/hcfdis/hcfdis-3.jar"],
      "digest" : "sha512:207b178aaab27754e331e9ce9e931ccda1cd4906aeb96f425028f58b3865f8527e8564757c10a8acdcbba9808abaaf5d55d9663d597dab029785da1e12cae20d",
    },

    "C1VISUALIZER_DIST" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/c1visualizer/c1visualizer-1.10.zip"],
      "digest" : "sha512:40c505dd03ca0bb102f1091b89b90672126922f290bd8370eef9a7afc5d9c1e7b5db08c448a0948ef46bf57d850e166813e2d68bf7b1c88a46256d839b6b0201",
      "packedResource": True,
    },
    "IDEALGRAPHVISUALIZER-0_31-0A82D7A0D60_DIST" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/idealgraphvisualizer/idealgraphvisualizer-0.31-0a82d7a0d60-all.zip"],
      "digest" : "sha512:20a3d87927fbecfe9b61dcd6c59f12f4c25e7da1ca926ea6d2571594424782751261e581e1c6e5113aeaeb8f31b8141b1941f7bbef1c6c07c55b9a753812b6db",
      "packedResource": True,
    },

    "JOL_CLI" : {
      "digest" : "sha512:aeefbf80b51e6aa546f7522b7dfd6a405529fc0d07be4b11fda56103b5b187a03f3b202c1d7ab65ffaa166630a0ec9a4684efccdf224743a3f79b4ca7504819c",
      "maven" : {
        "groupId" : "org.openjdk.jol",
        "artifactId" : "jol-cli",
        "version" : "0.9",
        "classifier" : "full",
      },
    },

    "BATIK" : {
      "digest" : "sha512:cefc274dab0f3cd8064f135a8a3bccb59b8168864acd2143f8a5563c6feacd9651a740bcfc9998031d78b6c219168b7e5ba3341d1d11e429f1bf53629000566d",
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/batik-all-1.7.jar"],
    },

    "ASM_9.5" : {
      "digest" : "sha512:9e65f2983783725bae196ca939b45246958731246df1c495089c8ea5ce646de77c4e01a5a9ba10642016bb3258e1727e9ebcece6e74d9e3c32f528025d76b955",
      "maven" : {
        "groupId" : "org.ow2.asm",
        "artifactId" : "asm",
        "version" : "9.5",
      },
    },

    "ASM_TREE_9.5" : {
      "digest" : "sha512:816de8f84c216a7bd97b2458bde64a4b99a039b7b59fbd1ef52edf8bf869edabb93967736fe0c61e8eb3e1520e0cefe69ba59cda12df30f9f85db75fb6c064f3",
      "maven" : {
        "groupId" : "org.ow2.asm",
        "artifactId" : "asm-tree",
        "version" : "9.5",
      },
      "dependencies" : ["ASM_9.5"],
    },

    "ASM_UTIL_9.5" : {
      "digest" : "sha512:f68284d8f8fd029f3f428112225b2035ed3a4216cf3b34e0aacc83c32a6d44ab5e5d128b60a13ef768e3396041a62cf63f7fd3445dc5a05ce0ae03a2b2ed3080",
      "maven" : {
        "groupId" : "org.ow2.asm",
        "artifactId" : "asm-util",
        "version" : "9.5",
      },
      "dependencies" : ["ASM_9.5"],
    },

    "HSDIS" : {
      "urlbase" : "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/hsdis",
      "packedResource" : True,
      "os_arch" : {
        "linux" : {
          "amd64" : {
            "digest" : "sha512:38c2af202546d2c2fd2bb6936f028b1feda8a5da40e2e374f2ad9caddb639988fac26bacf87925ff76cf7f88537f4f55206c50e0f9dffb290f0c630992582e76",
            "urls" : ["{urlbase}/intel/hsdis-amd64-linux-0d031013db9a80d6c88330c42c983fbfa7053193.tar.gz"],
          },
          "aarch64" : {
            "digest" : "sha512:422e1078fe5d9e2f71c04ca2bbefef4e09cf9675d132c7531f1fb17330e2b1f9441470541b66c8db2f3d8e105d167e25a78dc11aada524ed623b1ae9a4cfdeeb",
            "urls" : ["{urlbase}/hsdis-aarch64-linux-fcc9b70ac91c00db8a50b0d4345490a68e3743e1.tar.gz"],
          },
          "riscv64" : {
            "optional" : True,
          }
        },
        "darwin" : {
          "amd64" : {
            "digest" : "sha512:754931b55975ceb47f46d4803930c915d48aaf04d6633944751ff9e7f8c2df076473f0a134f77aab80d54159ec6a011ada6b44cf10a3bbe55d0356c9c22cfa86",
            "urls" : ["{urlbase}/intel/hsdis-amd64-darwin-67f6d23cbebd8998450a88b5bef362171f66f11a.tar.gz"],
          },
          "aarch64" : {
            "digest" : "sha512:2ce96d16865a180cb6352377aea1c2e4a85ebbd8b57bd157eafb551188d3bd005d1ca7118fe99480ccca0f59d1c128c25a5612bc809077cbac3c19b6a6d4246b",
            "urls" : ["{urlbase}/hsdis-aarch64-darwin-073b5f6f10a4c8530417f165d03c19093a2c0680.tar.gz"],
          }
        },
        "windows" : {
          "amd64" : {
            "digest" : "sha512:92d79ec235cbe4480c6887d92003519f0340f571a55207d326b59d42163ecb984752d5d614d590400542a9097f1ea8233720c18f85728eaccce86225930918fe",
            "urls" : ["{urlbase}/intel/hsdis-amd64-windows-6a388372cdd5fe905c1a26ced614334e405d1f30-2.zip"],
          },
          "aarch64" : {
            "optional" : True,
          }
        },
      },
    },
  },

  "projects" : {

    # ------------- Graal -------------

    "jdk.internal.vm.compiler" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "sdk:WORD",
        "sdk:COLLECTIONS",
        "truffle:TRUFFLE_COMPILER",
      ],
      "requires" : [
        "jdk.internal.vm.ci",
        "jdk.unsupported",
        "java.logging",
      ],
      "requiresConcealed" : {
        "jdk.internal.vm.ci" : [
          "jdk.vm.ci.meta",
          "jdk.vm.ci.code",
          "jdk.vm.ci.code.site",
          "jdk.vm.ci.code.stack",
          "jdk.vm.ci.common",
          "jdk.vm.ci.amd64",
          "jdk.vm.ci.aarch64",
          "jdk.vm.ci.services",
          "jdk.vm.ci.runtime",
          "jdk.vm.ci.hotspot",
          "jdk.vm.ci.hotspot.amd64",
          "jdk.vm.ci.hotspot.aarch64",
        ],
      },
      "uses" : [
        "org.graalvm.compiler.hotspot.meta.HotSpotInvocationPluginProvider",
        "org.graalvm.compiler.lir.LIRInstructionVerifier",
        "org.graalvm.compiler.core.common.CompilerProfiler",
        "org.graalvm.compiler.truffle.compiler.substitutions.GraphBuilderInvocationPluginProvider",
        "org.graalvm.compiler.truffle.compiler.phases.inlining.InliningPolicyProvider",
      ],
      "annotationProcessors" : [
        "GRAAL_PROCESSOR"
      ],
      "checkPackagePrefix": "false",
      "checkstyleVersion" : "10.7.0",
      "javaCompliance" : "17+",
      "workingSets" : "API,Graal",
      "jacoco" : "include",
      "jacocoExcludePackages" : [
        "jdk.internal.vm.compiler.test",
        "org.graalvm.libgraal.jni",
        "org.graalvm.compiler.replacements",
        "org.graalvm.compiler.hotspot.test",
        "org.graalvm.compiler.replacements.test",
        "org.graalvm.compiler.api.directives.test",
        "org.graalvm.compiler.test",
        "org.graalvm.compiler.core.test",
        "org.graalvm.compiler.nodes.test",
        "org.graalvm.compiler.loop.test",
        "org.graalvm.compiler.core.aarch64.test",
        "org.graalvm.compiler.jtt",
        "org.graalvm.compiler.truffle.test",
      ],
    },

    "jdk.internal.vm.compiler.processor" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "requires" : [
        "java.compiler" # javax.annotation.processing.*
      ],
      "checkPackagePrefix": "false",
      "checkstyle" : "jdk.internal.vm.compiler",
      "javaCompliance" : "17+",
    },

    "jdk.internal.vm.compiler.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "jdk.internal.vm.compiler",
        "mx:JUNIT",
        "ASM_TREE_9.5",
        "ASM_UTIL_9.5",
        "JAVA_ALLOCATION_INSTRUMENTER",
        "truffle:TRUFFLE_SL_TEST",
        "truffle:TRUFFLE_TEST",
        "truffle:TRUFFLE_RUNTIME",
      ],
      "requires" : [
        "jdk.unsupported",
        "java.compiler",
        "java.logging",
        "java.instrument",
        "java.management",
        "jdk.jfr",
      ],
      "requiresConcealed" : {
        "java.base" : [
          "jdk.internal.module",
          "jdk.internal.misc",
          "jdk.internal.vm.annotation",
        ],
        "java.instrument" : [
          "sun.instrument",
        ],
        "jdk.internal.vm.ci" : [
          "jdk.vm.ci.meta",
          "jdk.vm.ci.code",
          "jdk.vm.ci.code.site",
          "jdk.vm.ci.code.stack",
          "jdk.vm.ci.common",
          "jdk.vm.ci.amd64",
          "jdk.vm.ci.aarch64",
          "jdk.vm.ci.services",
          "jdk.vm.ci.runtime",
          "jdk.vm.ci.hotspot",
          "jdk.vm.ci.hotspot.amd64",
          "jdk.vm.ci.hotspot.aarch64",
        ],
      },
      "annotationProcessors" : [
        "GRAAL_PROCESSOR",
        "truffle:TRUFFLE_DSL_PROCESSOR"
      ],
      "checkstyle" : "jdk.internal.vm.compiler",
      "javaCompliance" : "17+",
      "jacoco" : "exclude",
    },

    "org.graalvm.compiler.management" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "jdk.internal.vm.compiler",
      ],
      "requires" : [
        "jdk.internal.vm.ci",
        "jdk.management",
      ],
      "checkstyle" : "jdk.internal.vm.compiler",
      "annotationProcessors" : [
        "GRAAL_PROCESSOR",
      ],
      "javaCompliance" : "17+",
      "workingSets" : "Graal,HotSpot",
    },

    "org.graalvm.compiler.hotspot.jdk20.test" : {
      "testProject" : True,
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "jdk.internal.vm.compiler.test",
      ],
      "requires" : [
        "jdk.jfr",
      ],
      "requiresConcealed" : {
        "java.base" : [
          "jdk.internal.misc",
        ],
        "jdk.internal.vm.ci" : [
          "jdk.vm.ci.meta",
        ],
      },
      "checkstyle": "jdk.internal.vm.compiler",
      "javaCompliance" : "20+",
      "javaPreviewNeeded": "20+",
      "javac.lint.overrides": "none",
      "workingSets" : "Graal,HotSpot,Test",
    },

    "org.graalvm.compiler.hotspot.jdk21.test" : {
      "testProject" : True,
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "jdk.internal.vm.compiler.test",
      ],
      "requiresConcealed" : {
        "java.base" : [
          "jdk.internal.util",
        ],
      },
      "checkstyle": "jdk.internal.vm.compiler",
      "javaCompliance" : "21+",
      "javaPreviewNeeded": "21+",
      "workingSets" : "Graal,HotSpot,Test",
    },

    "org.graalvm.compiler.virtual.bench" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["mx:JMH_1_21", "org.graalvm.compiler.microbenchmarks"],
      "checkstyle" : "jdk.internal.vm.compiler",
      "javaCompliance" : "17+",
      "annotationProcessors" : ["mx:JMH_1_21"],
      "spotbugsIgnoresGenerated" : True,
      "workingSets" : "Graal,Bench",
      "testProject" : True,
    },

    "org.graalvm.compiler.microbenchmarks" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JMH_1_21",
        "jdk.internal.vm.compiler",
        "jdk.internal.vm.compiler.test",
      ],
      "requiresConcealed" : {
        "jdk.internal.vm.ci" : [
          "jdk.vm.ci.meta",
        ],
      },
      "checkstyle" : "jdk.internal.vm.compiler",
      "javaCompliance" : "17+",
      "checkPackagePrefix" : "false",
      "annotationProcessors" : ["mx:JMH_1_21"],
      "spotbugsIgnoresGenerated" : True,
      "workingSets" : "Graal,Bench",
      "testProject" : True,
    },

    # ------------- GraalTruffle -------------

    "org.graalvm.compiler.truffle.test.jdk21" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies": [
        "jdk.internal.vm.compiler.test",
      ],
      "annotationProcessors" : [
        "GRAAL_PROCESSOR",
      ],
      "overlayTarget" : "jdk.internal.vm.compiler.test",
      "multiReleaseJarVersion" : "21",
      "checkstyle" : "jdk.internal.vm.compiler",
      "javaCompliance" : "21+",
      "checkPackagePrefix" : "false",
      "jacoco": "exclude",
      "testProject" : True,
    },

    # ------------- blackbox micro benchmarks -------------

    "org.graalvm.micro.benchmarks" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JMH_1_21",
      ],
      "checkstyle" : "jdk.internal.vm.compiler",
      "javaCompliance" : "17+",
      "checkPackagePrefix" : "false",
      "annotationProcessors" : ["mx:JMH_1_21"],
      "spotbugsIgnoresGenerated" : True,
      "workingSets" : "Graal,Bench",
      "testProject" : True,
    },

    "org.graalvm.profdiff" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "jdk.internal.vm.compiler",
        "sdk:COLLECTIONS",
      ],
      "checkstyle" : "jdk.internal.vm.compiler",
      "javaCompliance" : "17+",
    },

    "org.graalvm.profdiff.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.profdiff",
        "mx:JUNIT",
      ],
      "checkstyle" : "jdk.internal.vm.compiler",
      "javaCompliance" : "17+",
      "workingSets" : "Graal,Test",
    },
  },

  "distributions" : {

    # ------------- Distributions -------------

    "GRAAL_TEST" : {
      "subDir" : "src",
      "dependencies" : [
        "jdk.internal.vm.compiler.test",
      ],
      "distDependencies" : [
        "GRAAL",
        "truffle:TRUFFLE_SL_TEST",
        "truffle:TRUFFLE_TEST",
        "truffle:TRUFFLE_COMPILER",
        "truffle:TRUFFLE_RUNTIME",
        "regex:TREGEX",
        "ASM_TREE_9.5",
        "ASM_UTIL_9.5",
      ],
      "exclude" : [
        "mx:JUNIT",
        "JAVA_ALLOCATION_INSTRUMENTER",
      ],
      "testDistribution" : True,
      "maven": False,
    },
    "GRAAL_TEST_PREVIEW_FEATURE" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.compiler.hotspot.jdk20.test",
        "org.graalvm.compiler.hotspot.jdk21.test",
      ],
      "distDependencies" : [
        "GRAAL_TEST",
      ],
      "exclude" : [
      ],
      "testDistribution" : True,
      "maven": False,
    },

    "GRAAL_PROCESSOR" : {
      "subDir": "src",
      "dependencies" : [
        "jdk.internal.vm.compiler.processor",
       ],
      "maven": False,
    },

    "GRAAL_VERSION": {
      "type": "dir",
      "platformDependent": False,
      "layout": {
        "META-INF/graalvm/jdk.internal.vm.compiler/version": "dependency:sdk:VERSION/version",
      },
      "description": "Compiler version.",
      "maven": False,
    },

    "GRAAL" : {
      # This distribution defines a module.
      "moduleInfo" : {
        "name" : "jdk.internal.vm.compiler",
        "requires" : [
          "jdk.unsupported" # sun.misc.Unsafe
        ],
        "exports" : [
          """* to com.oracle.graal.graal_enterprise,org.graalvm.nativeimage.pointsto,org.graalvm.nativeimage.builder,org.graalvm.nativeimage.foreign,org.graalvm.nativeimage.llvm,com.oracle.svm.svm_enterprise,com.oracle.svm_enterprise.ml_dataset,org.graalvm.nativeimage.base,
          org.graalvm.extraimage.builder,com.oracle.svm.extraimage_enterprise,org.graalvm.truffle.runtime.svm,com.oracle.truffle.enterprise.svm""",
          "org.graalvm.compiler.java                   to org.graalvm.nativeimage.agent.tracing,org.graalvm.nativeimage.configure",
          "org.graalvm.compiler.core.common            to org.graalvm.nativeimage.agent.tracing,org.graalvm.nativeimage.objectfile",
          "org.graalvm.compiler.debug                  to org.graalvm.nativeimage.objectfile",
          "org.graalvm.compiler.nodes.graphbuilderconf to org.graalvm.nativeimage.driver,org.graalvm.nativeimage.librarysupport",
          "org.graalvm.compiler.options                to org.graalvm.nativeimage.driver,org.graalvm.nativeimage.junitsupport",
          "org.graalvm.compiler.phases.common          to org.graalvm.nativeimage.agent.tracing,org.graalvm.nativeimage.configure",
          "org.graalvm.compiler.serviceprovider        to jdk.internal.vm.compiler.management,org.graalvm.nativeimage.driver,org.graalvm.nativeimage.agent.jvmtibase,org.graalvm.nativeimage.agent.diagnostics",
          "org.graalvm.util.json                       to org.graalvm.nativeimage.librarysupport,org.graalvm.nativeimage.agent.tracing,org.graalvm.nativeimage.configure,org.graalvm.nativeimage.driver",
        ],
        "uses" : [
          "org.graalvm.compiler.code.DisassemblerProvider",
          "org.graalvm.compiler.core.match.MatchStatementSet",
          "org.graalvm.compiler.debug.DebugHandlersFactory",
          "org.graalvm.compiler.debug.TTYStreamProvider",
          "org.graalvm.compiler.debug.PathUtilitiesProvider",
          "org.graalvm.compiler.hotspot.HotSpotCodeCacheListener",
          "org.graalvm.compiler.hotspot.HotSpotBackendFactory",
          "org.graalvm.compiler.hotspot.meta.HotSpotInvocationPluginProvider",
          "org.graalvm.compiler.nodes.graphbuilderconf.GeneratedPluginFactory",
          "org.graalvm.compiler.options.OptionDescriptors",
          "org.graalvm.compiler.serviceprovider.JMXService",
          "org.graalvm.compiler.truffle.compiler.hotspot.TruffleCallBoundaryInstrumentationFactory",
          "org.graalvm.compiler.truffle.compiler.substitutions.GraphBuilderInvocationPluginProvider",
        ],
      },
      "subDir" : "src",
      "dependencies" : [
        "jdk.internal.vm.compiler",
        "GRAAL_VERSION",
      ],
      "distDependencies" : [
        "sdk:COLLECTIONS",
        "sdk:WORD",
        "truffle:TRUFFLE_COMPILER",
      ],
      "allowsJavadocWarnings": True,
      "description":  "The GraalVM compiler and the Graal-truffle optimizer.",
      "maven" : {
        "artifactId" : "compiler",
        "tag": ["default", "public"],
      },
    },

    "GRAAL_MANAGEMENT" : {
      # This distribution defines a module.
      "moduleInfo" : {
        "name" : "jdk.internal.vm.compiler.management",
      },
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.compiler.management",
      ],
      "distDependencies" : [
        "GRAAL",
      ],
      "allowsJavadocWarnings": True,
      "description":  "The GraalVM compiler Management Bean.",
      "maven" : {
        "artifactId" : "compiler-management",
        "tag": ["default", "public"],
      },
    },

    "GRAAL_COMPILER_WHITEBOX_MICRO_BENCHMARKS" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.compiler.virtual.bench",
        "org.graalvm.compiler.microbenchmarks",
      ],
      "distDependencies" : [
        "GRAAL_TEST"
      ],
      "testDistribution" : True,
      "maven": False,
    },

    "GRAAL_COMPILER_MICRO_BENCHMARKS" : {
      "subDir" : "src",
      "dependencies" : ["org.graalvm.micro.benchmarks"],
      "testDistribution" : True,
      "maven": False,
    },

    "HSDIS_GRAALVM_SUPPORT" : {
      "native" : True,
      "description" : "Disassembler support distribution for the GraalVM",
      "os_arch" : {
        "linux" : {
          "riscv64" : {
            "optional" : True,
          },
          "<others>" : {
            "layout" : {
              "hsdis-<arch>.so" : "file:<path:HSDIS>/*",
            },
          },
        },
        "<others>" : {
          "amd64" : {
            "layout" : {
              "<libsuffix:hsdis-amd64>" : "file:<path:HSDIS>/*",
            },
          },
          "aarch64" : {
            "layout" : {
              "<libsuffix:hsdis-aarch64>" : "file:<path:HSDIS>/*",
            },
          },
        },
      },
    },

    "GRAAL_PROFDIFF": {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.profdiff",
      ],
      "distDependencies" : [
        "sdk:COLLECTIONS",
        "GRAAL",
      ],
      "maven" : False,
    },

    "GRAAL_PROFDIFF_TEST" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.profdiff.test",
      ],
      "distDependencies" : [
        "GRAAL_PROFDIFF",
      ],
      "exclude" : [
        "mx:JUNIT",
      ],
      "maven": False,
    },
  },
}
