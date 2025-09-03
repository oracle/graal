suite = {
  "mxversion": "7.58.9",
  "name" : "compiler",
  "sourceinprojectwhitelist" : [],

  "groupId" : "org.graalvm.compiler",
  "version" : "26.0.0",
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
        "name" : "sdk",
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
    "IDEALGRAPHVISUALIZER_DIST" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/idealgraphvisualizer/idealgraphvisualizer-1.22-6cb0d3acbb1.zip"],
      "digest" : "sha512:8c4795fae203bfa84c40b041fe6d0f46a89bd8b975120d28aea9483eef1c1b63ab685716c1258387c12a255560904284fd0bf9aa947f2efabc4a629148000b5d",
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

    "jdk.graal.compiler" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "sdk:WORD",
        "sdk:COLLECTIONS",
        "truffle:TRUFFLE_COMPILER",
      ],
      "requires" : [
        "jdk.internal.vm.ci",
        "java.logging",
      ],
      "requiresConcealed" : {
        "java.base" : [
          "jdk.internal.misc"
        ],
        "jdk.internal.vm.ci" : [
          "jdk.vm.ci.meta",
          "jdk.vm.ci.code",
          "jdk.vm.ci.code.site",
          "jdk.vm.ci.code.stack",
          "jdk.vm.ci.common",
          "jdk.vm.ci.amd64",
          "jdk.vm.ci.aarch64",
          "jdk.vm.ci.riscv64",
          "jdk.vm.ci.services",
          "jdk.vm.ci.runtime",
          "jdk.vm.ci.hotspot",
          "jdk.vm.ci.hotspot.amd64",
          "jdk.vm.ci.hotspot.aarch64",
          "jdk.vm.ci.hotspot.riscv64",
        ],
      },
      "uses" : [
        "jdk.graal.compiler.hotspot.meta.DefaultHotSpotLoweringProvider.Extensions",
        "jdk.graal.compiler.hotspot.meta.HotSpotInvocationPluginProvider",
        "jdk.graal.compiler.lir.LIRInstructionVerifier",
        "jdk.graal.compiler.core.common.CompilerProfiler",
        "jdk.graal.compiler.truffle.substitutions.GraphBuilderInvocationPluginProvider",
        "jdk.graal.compiler.truffle.phases.inlining.InliningPolicyProvider",
        "jdk.graal.compiler.truffle.host.TruffleHostEnvironment.Lookup",
        "jdk.graal.compiler.truffle.substitutions.GraphDecoderInvocationPluginProvider"
      ],
      "annotationProcessors" : [
        "GRAAL_PROCESSOR"
      ],
      "checkPackagePrefix": "false",
      "checkstyleVersion" : "10.21.0",
      "javaCompliance" : "21+",
      "workingSets" : "API,Graal",
      "jacoco" : "include",
      "jacocoExcludePackages" : [
        "jdk.graal.compiler.test",
        "jdk.graal.compiler.replacements",
        "jdk.graal.compiler.hotspot.test",
        "jdk.graal.compiler.replacements.test",
        "jdk.graal.compiler.api.directives.test",
        "jdk.graal.compiler.test",
        "jdk.graal.compiler.core.test",
        "jdk.graal.compiler.nodes.test",
        "jdk.graal.compiler.loop.test",
        "jdk.graal.compiler.core.aarch64.test",
        "jdk.graal.compiler.jtt",
        "jdk.graal.compiler.truffle.test",
      ],
    },

    "jdk.graal.compiler.processor" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "requires" : [
        "java.compiler" # javax.annotation.processing.*
      ],
      "checkPackagePrefix": "false",
      "jacoco" : "exclude",
      "checkstyle" : "jdk.graal.compiler",
      "javaCompliance" : "21+",
    },

    "jdk.graal.compiler.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "jdk.graal.compiler",
        "mx:JUNIT",
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
        "jdk.incubator.vector",
      ],
      "requiresConcealed" : {
        "java.base" : [
          "jdk.internal.module",
          "jdk.internal.misc",
          "jdk.internal.util",
          "jdk.internal.vm.annotation",
          "sun.security.util.math",
          "sun.security.util.math.intpoly",
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
      "checkstyle" : "jdk.graal.compiler",
      "javaCompliance" : "24+",
      "forceJavac": True,
      "jacoco" : "exclude",
      # warning: [incubating] using incubating module(s): jdk.incubator.vector
      "javac.lint.overrides" : "-incubating",
      "graalCompilerSourceEdition": "ignore",
    },

    "jdk.graal.compiler.management" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "jdk.graal.compiler",
      ],
      "requires" : [
        "jdk.internal.vm.ci",
        "jdk.management",
      ],
      "checkPackagePrefix": "false",
      "checkstyle" : "jdk.graal.compiler",
      "annotationProcessors" : [
        "GRAAL_PROCESSOR",
      ],
      "javaCompliance" : "21+",
      "workingSets" : "Graal,HotSpot",
    },

    "jdk.graal.compiler.microbenchmarks" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JMH_1_21",
        "jdk.graal.compiler",
        "jdk.graal.compiler.test",
      ],
      "requiresConcealed" : {
        "jdk.internal.vm.ci" : [
          "jdk.vm.ci.meta",
          "jdk.vm.ci.code"
        ],
      },
      "jacoco" : "exclude",
      "checkstyle" : "jdk.graal.compiler",
      "javaCompliance" : "24+",
      "forceJavac": True,
      "checkPackagePrefix" : "false",
      "annotationProcessors" : ["mx:JMH_1_21"],
      "spotbugsIgnoresGenerated" : True,
      "workingSets" : "Graal,Bench",
      "testProject" : True,
      "graalCompilerSourceEdition": "ignore",
    },

    # ------------- blackbox micro benchmarks -------------

    "org.graalvm.micro.benchmarks" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JMH_1_21",
      ],
      "checkstyle" : "jdk.graal.compiler",
      "javaCompliance" : "21+",
      "checkPackagePrefix" : "false",
      "annotationProcessors" : ["mx:JMH_1_21"],
      "spotbugsIgnoresGenerated" : True,
      "workingSets" : "Graal,Bench",
      "jacoco" : "exclude",
      "testProject" : True,
      "graalCompilerSourceEdition": "ignore",
    },

    "org.graalvm.profdiff" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "jdk.graal.compiler",
        "sdk:COLLECTIONS",
      ],
      "checkstyle" : "jdk.graal.compiler",
      "javaCompliance" : "21+",
      "graalCompilerSourceEdition": "ignore",
    },

    "org.graalvm.profdiff.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.profdiff",
        "mx:JUNIT",
      ],
      "checkstyle" : "jdk.graal.compiler",
      "javaCompliance" : "21+",
      "jacoco" : "exclude",
      "workingSets" : "Graal,Test",
      "graalCompilerSourceEdition": "ignore",
    },

    "org.graalvm.igvutil" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "jdk.graal.compiler",
        "sdk:COLLECTIONS",
      ],
      "checkstyle" : "jdk.graal.compiler",
      "javaCompliance" : "21+",
      "jacoco" : "exclude",
      "graalCompilerSourceEdition": "ignore",
    },

    "org.graalvm.igvutil.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.igvutil",
        "mx:JUNIT",
      ],
      "checkstyle" : "jdk.graal.compiler",
      "javaCompliance" : "21+",
      "jacoco" : "exclude",
      "workingSets" : "Graal,Test",
      "graalCompilerSourceEdition": "ignore",
    },

    # ------------- libgraal -------------

    # See jdk.graal.compiler.core.common.LibGraalSupport for the SPI
    # used by core compiler classes to access libgraal specific
    # functionality without requiring the compiler classes to directly
    # depend on libgraal specific modules.
    "jdk.graal.compiler.libgraal" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "workingSets" : "Graal",
      "javaCompliance" : "24+",
      "forceJavac": True,
      "checkstyle" : "jdk.graal.compiler",
      "dependencies" : [
        "GRAAL",
        "GRAAL_MANAGEMENT",
        "sdk:NATIVEIMAGE_LIBGRAAL",
        "sdk:JNIUTILS"
      ],
      "requiresConcealed" : {
        "java.base" : [
          "jdk.internal.module",
          "jdk.internal.misc"
        ],
        "jdk.internal.vm.ci" : [
          "jdk.vm.ci.code",
          "jdk.vm.ci.meta",
          "jdk.vm.ci.runtime",
          "jdk.vm.ci.services",
          "jdk.vm.ci.hotspot",
        ],
      },
      # Code coverage is not done when building libgraal
      "jacoco" : "exclude",
      "annotationProcessors" : [
        "GRAAL_PROCESSOR",
      ],

      # Direct reference to jdk.vm.ci.hotspot.CompilerThreadCanCallJavaScope
      # causing spotbugs analysis to fail with "missing class" error.
      "spotbugs": "false",
    },

    "jdk.graal.compiler.libgraal.loader" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "workingSets" : "Graal",
      "javaCompliance" : "24+",
      "forceJavac": True,
      "checkstyle" : "jdk.graal.compiler",
      "dependencies" : [
        "sdk:NATIVEIMAGE_LIBGRAAL",
      ],
      "requiresConcealed" : {
        "java.base" : [
          "jdk.internal.module",
          "jdk.internal.jimage",
        ],
      },
      "spotbugs": "false",
      "jacoco" : "exclude",
    },
  },

  "distributions" : {

    # ------------- Distributions -------------

    "GRAAL_TEST" : {
      "subDir" : "src",
      "dependencies" : [
        "jdk.graal.compiler.test",
      ],
      "distDependencies" : [
        "GRAAL",
        "truffle:TRUFFLE_SL_TEST",
        "truffle:TRUFFLE_TEST",
        "truffle:TRUFFLE_COMPILER",
        "truffle:TRUFFLE_RUNTIME",
      ],
      "exclude" : [
        "mx:JUNIT",
        "JAVA_ALLOCATION_INSTRUMENTER",
      ],
      "testDistribution" : True,
      "unittestConfig": "graal",
      "maven": False,
      "graalCompilerSourceEdition": "ignore",
    },

    "GRAAL_PROCESSOR" : {
      "subDir": "src",
      "dependencies" : [
        "jdk.graal.compiler.processor",
       ],
      "maven": False,
    },

    "GRAAL_VERSION": {
      "type": "dir",
      "platformDependent": False,
      "layout": {
        "META-INF/graalvm/jdk.graal.compiler/version": "dependency:sdk:VERSION/version",
      },
      "description": "Compiler version.",
      "maven": False,
    },

    "GRAAL" : {
      # This distribution defines a module.
      "moduleInfo" : {
        "name" : "jdk.graal.compiler",
        "exports" : [
          """* to jdk.graal.compiler.libgraal,
                  jdk.graal.compiler.management,
                  com.oracle.graal.graal_enterprise,
                  org.graalvm.nativeimage.pointsto,
                  org.graalvm.nativeimage.builder,
                  org.graalvm.nativeimage.foreign,
                  org.graalvm.nativeimage.llvm,
                  com.oracle.svm.svm_enterprise,
                  com.oracle.svm_enterprise.ml_dataset,
                  org.graalvm.nativeimage.base,
                  org.graalvm.extraimage.builder,
                  org.graalvm.extraimage.librarysupport,
                  com.oracle.svm.extraimage_enterprise,
                  org.graalvm.truffle.runtime.svm,
                  com.oracle.truffle.enterprise.svm""",
          "jdk.graal.compiler.java                   to org.graalvm.nativeimage.agent.tracing,org.graalvm.nativeimage.configure",
          "jdk.graal.compiler.util                   to org.graalvm.nativeimage.agent.tracing,org.graalvm.nativeimage.configure",
          "jdk.graal.compiler.core.common            to org.graalvm.nativeimage.agent.tracing,org.graalvm.nativeimage.objectfile",
          "jdk.graal.compiler.debug                  to org.graalvm.nativeimage.objectfile",
          "jdk.graal.compiler.nodes.graphbuilderconf to org.graalvm.nativeimage.driver,org.graalvm.nativeimage.librarysupport",
          "jdk.graal.compiler.options                to org.graalvm.nativeimage.driver,org.graalvm.nativeimage.junitsupport",
          "jdk.graal.compiler.phases.common          to org.graalvm.nativeimage.agent.tracing,org.graalvm.nativeimage.configure",
          "jdk.graal.compiler.serviceprovider        to org.graalvm.nativeimage.driver,org.graalvm.nativeimage.agent.jvmtibase,org.graalvm.nativeimage.agent.diagnostics",
          "jdk.graal.compiler.util.json              to org.graalvm.nativeimage.librarysupport,org.graalvm.nativeimage.agent.tracing,org.graalvm.nativeimage.configure,org.graalvm.nativeimage.driver",
        ],
        "uses" : [
          "jdk.graal.compiler.code.DisassemblerProvider",
          "jdk.graal.compiler.core.match.MatchStatementSet",
          "jdk.graal.compiler.core.common.LibGraalSupport",
          "jdk.graal.compiler.debug.DebugDumpHandlersFactory",
          "jdk.graal.compiler.debug.TTYStreamProvider",
          "jdk.graal.compiler.debug.PathUtilitiesProvider",
          "jdk.graal.compiler.hotspot.HotSpotBackendFactory",
          "jdk.graal.compiler.hotspot.meta.HotSpotInvocationPluginProvider",
          "jdk.graal.compiler.nodes.graphbuilderconf.GeneratedPluginFactory",
          "jdk.graal.compiler.options.OptionDescriptors",
          "jdk.graal.compiler.serviceprovider.JMXService",
          "jdk.graal.compiler.truffle.hotspot.TruffleCallBoundaryInstrumentationFactory",
          "jdk.graal.compiler.truffle.substitutions.GraphBuilderInvocationPluginProvider",
        ],
      },
      "subDir" : "src",
      "dependencies" : [
        "jdk.graal.compiler",
        "GRAAL_VERSION",
      ],
      "distDependencies" : [
        "sdk:COLLECTIONS",
        "sdk:WORD",
        "truffle:TRUFFLE_COMPILER",
      ],
      "allowsJavadocWarnings": True,
      "description": "The JAR build of the Graal compiler. This is intended to be used to speed up Graal Languages on OpenJDK distributions that do not ship the Graal compiler. To enable it, \'-XX:+EnableJVMCI\' is required and this Graal compiler JAR and its dependencies need to be on the \'--upgrade-module-path\'.", # pylint: disable=line-too-long
      "maven" : {
        "artifactId" : "compiler",
        "tag": ["default", "public"],
      },
    },

    "GRAAL_MANAGEMENT" : {
      # This distribution defines a module.
      "moduleInfo" : {
        "name" : "jdk.graal.compiler.management",
      },
      "subDir" : "src",
      "dependencies" : [
        "jdk.graal.compiler.management",
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

    "LIBGRAAL_LOADER" : {
      "subDir": "src",
      "dependencies" : [
        "jdk.graal.compiler.libgraal.loader"
      ],
      "distDependencies" : [
        "sdk:NATIVEIMAGE",
        "sdk:NATIVEIMAGE_LIBGRAAL",
        "GRAAL",
      ],
      "maven": False,
    },

    "LIBGRAAL": {
      "moduleInfo" : {
        "name" : "jdk.graal.compiler.libgraal",
      },
      "subDir": "src",
      "description" : "Module that builds libgraal",
      "javaCompliance" : "24+",
      "dependencies": [
        "jdk.graal.compiler.libgraal",
      ],
      "distDependencies": [
        "GRAAL",
        "GRAAL_MANAGEMENT",
        "sdk:NATIVEIMAGE",
        "sdk:NATIVEIMAGE_LIBGRAAL",
        "sdk:COLLECTIONS",
        "sdk:JNIUTILS",
        "truffle:TRUFFLE_COMPILER"
      ],
      "maven": False,
    },

    "GRAAL_COMPILER_WHITEBOX_MICRO_BENCHMARKS" : {
      "subDir" : "src",
      "dependencies" : [
        "jdk.graal.compiler.microbenchmarks",
      ],
      "distDependencies" : [
        "GRAAL_TEST"
      ],
      "testDistribution" : True,
      "maven": False,
      "graalWhiteboxDistribution": True,
      "graalCompilerSourceEdition": "ignore",
    },

    "GRAAL_COMPILER_MICRO_BENCHMARKS" : {
      "subDir" : "src",
      "dependencies" : ["org.graalvm.micro.benchmarks"],
      "testDistribution" : True,
      "maven": False,
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
    },

    "GRAAL_IGVUTIL": {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.igvutil",
      ],
      "distDependencies" : [
        "GRAAL",
      ],
      "maven" : False,
      "graalCompilerSourceEdition": "ignore",
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
      "unittestConfig": "graal",
      "maven": False,
      "graalCompilerSourceEdition": "ignore",
    },

    "GRAAL_IGVUTIL_TEST" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.igvutil.test",
      ],
      "distDependencies" : [
        "GRAAL_IGVUTIL",
      ],
      "exclude" : [
        "mx:JUNIT",
      ],
      "unittestConfig": "graal",
      "maven": False,
      "graalCompilerSourceEdition": "ignore",
    },
  },
}
