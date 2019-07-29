suite = {
  "mxversion" : "5.223.0",
  "name" : "compiler",
  "sourceinprojectwhitelist" : [],

  "groupId" : "org.graalvm.compiler",
  "version" : "19.3.0",
  "release" : False,
  "url" : "http://www.graalvm.org/",
  "developer" : {
    "name" : "Truffle and Graal developers",
    "email" : "graalvm-users@oss.oracle.com",
    "organization" : "Graal",
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
        "urls" : [
          {"url" : "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind" : "binary"},
         ]
      },
    ]
  },

  "defaultLicense" : "GPLv2-CPE",
  "snippetsPattern" : ".*JavadocSnippets.*",
  "javac.lint.overrides": "-path",

  "jdklibraries" : {
    "JVMCI_SERVICES" : {
      "path" : "lib/jvmci-services.jar",
      "sourcePath" : "lib/jvmci-services.src.zip",
      "optional" : False,
      "jdkStandardizedSince" : "9",
      "module" : "jdk.internal.vm.ci"
    },
    "JVMCI_API" : {
      "path" : "lib/jvmci/jvmci-api.jar",
      "sourcePath" : "lib/jvmci/jvmci-api.src.zip",
      "dependencies" : [
        "JVMCI_SERVICES",
      ],
      "optional" : False,
      "jdkStandardizedSince" : "9",
      "module" : "jdk.internal.vm.ci"
    },
    "JVMCI_HOTSPOT" : {
      "path" : "lib/jvmci/jvmci-hotspot.jar",
      "sourcePath" : "lib/jvmci/jvmci-hotspot.src.zip",
      "dependencies" : [
        "JVMCI_API",
      ],
      "optional" : False,
      "jdkStandardizedSince" : "9",
      "module" : "jdk.internal.vm.ci"
    },
  },

  "libraries" : {

    # ------------- Libraries -------------

    "DACAPO" : {
      "urls" : [
        "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/dacapo-9.12-bach-patched.jar",
      ],
      "sha1" : "e39957904b7e79caf4fa54f30e8e4ee74d4e9e37",
    },

    "DACAPO_SCALA" : {
      "urls" : [
        "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/dacapo-scala-0.1.0-20120216.jar",
      ],
      "sha1" : "59b64c974662b5cf9dbd3cf9045d293853dd7a51",
    },

    "DACAPO_D3S" : {
      "urls" : [
        "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/dacapo-9.12-d3s.jar",
        "https://d3s.mff.cuni.cz/software/benchmarking/files/dacapo-9.12-d3s.jar",
      ],
      "sha1" : "b072de027141ac81ab5d48706949fda86de62468",
    },

    "RENAISSANCE" : {
      "urls" : [
        "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/renaissance/renaissance-gpl-0.10.0.jar"
      ],
      "sha1" : "5f58e281bb5aae161854b036c7e49e593a81186a",
    },

    "JAVA_ALLOCATION_INSTRUMENTER" : {
      "sha1" : "d0bdc21c5e6404726b102998e44c66a738897905",
      "maven" : {
        "groupId" : "com.google.code.java-allocation-instrumenter",
        "artifactId" : "java-allocation-instrumenter",
        "version" : "3.1.0",
      },
      "bootClassPathAgent" : "true",
    },

    "HCFDIS" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/hcfdis/hcfdis-3.jar"],
      "sha1" : "a71247c6ddb90aad4abf7c77e501acc60674ef57",
    },

    "C1VISUALIZER_DIST" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/c1visualizer/c1visualizer-1.7.zip"],
      "sha1" : "305a772ccbdc0e42dfa233b0ce6762d0dd1de6de",
      "packedResource": True,
    },

    "JOL_CLI" : {
      "sha1" : "45dd0cf195b16e70710a8d6d763cda614cf6f31e",
      "maven" : {
        "groupId" : "org.openjdk.jol",
        "artifactId" : "jol-cli",
        "version" : "0.9",
        "classifier" : "full",
      },
    },

    "BATIK" : {
      "sha1" : "122b87ca88e41a415cf8b523fd3d03b4325134a3",
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/batik-all-1.7.jar"],
    },

    "ASM5" : {
      "sha1" : "0da08b8cce7bbf903602a25a3a163ae252435795",
      "maven" : {
        "groupId" : "org.ow2.asm",
        "artifactId" : "asm",
        "version" : "5.0.4",
      },
    },

    "ASM_TREE5" : {
      "sha1" : "396ce0c07ba2b481f25a70195c7c94922f0d1b0b",
      "maven" : {
        "groupId" : "org.ow2.asm",
        "artifactId" : "asm-tree",
        "version" : "5.0.4",
      },
      "dependencies" : ["ASM5"],
    },

    "UBENCH_AGENT_DIST" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/java-ubench-agent-2e5becaf97afcf64fd8aef3ac84fc05a3157bff5.zip"],
      "sha1" : "19087a34b80be8845e9a3e7f927ceb592de83762",
    },

    # Required to run SPECJBB2015 on JDK >=11
    "ACTIVATION_1.1.1" : {
      "sha1" : "485de3a253e23f645037828c07f1d7f1af40763a",
      "maven" : {
        "groupId" : "javax.activation",
        "artifactId" : "activation",
        "version" : "1.1.1",
      },
    },
    "JAXB_API_2.1" : {
      "sha1" : "d68570e722cffe2000358ce9c661a0b0bf1ebe11",
      "maven" : {
        "groupId" : "javax.xml.bind",
        "artifactId" : "jaxb-api",
        "version" : "2.1",
      },
    },
    "JAXB_IMPL_2.1.17" : {
      "sha1" : "26efa071c07deb2b80cd72b6567f1260a68a0da5",
      "maven" : {
        "groupId" : "com.sun.xml.bind",
        "artifactId" : "jaxb-impl",
        "version" : "2.1.17",
      },
      "dependencies": ["JAXB_API_2.1", "ACTIVATION_1.1.1"]
    },
    "LLVM_WRAPPER": {
      "sha1" : "a990b2dba1c706f5c43c56fedfe70bad9a695852",
      "sourceSha1" : "decbd95d46092fa9afaf2523b5b23d07ad7ad6bc",
      "dependencies" : ["JAVACPP"],
      "maven" : {
        "groupId" : "org.bytedeco.javacpp-presets",
        "artifactId" : "llvm",
        "version" : "6.0.1-1.4.2",
      },
      "license" : "GPLv2-CPE"
    },
    "JAVACPP": {
      "sha1" : "cfa6a0259d98bff5aa8d41ba11b4d1dad648fbaa",
      "sourceSha1" : "fdb2d2c17f6b91cdd5421554396da8905f0dfed2",
      "maven" : {
        "groupId" : "org.bytedeco",
        "artifactId" : "javacpp",
        "version" : "1.4.2",
      },
      "license" : "GPLv2-CPE"
    },
    "LLVM_PLATFORM_SPECIFIC": {
      "os_arch": {
        "linux": {
          "amd64": {
            "sha1": "344483aefa15147c121a8fb6fb35a2406768cc5c",
            "maven": {
              "groupId" : "org.bytedeco.javacpp-presets",
              "artifactId" : "llvm",
              "version" : "6.0.1-1.4.2",
              "classifier": "linux-x86_64"
            },
          },
          "<others>": {
            "optional": True,
          },
        },
        "darwin": {
          "amd64": {
            "sha1": "57bc74574104a9e0a2dc4d7a71ffcc5731909e57",
            "maven": {
              "groupId" : "org.bytedeco.javacpp-presets",
              "artifactId" : "llvm",
              "version" : "6.0.1-1.4.2",
              "classifier": "macosx-x86_64"
            }
          },
        },
        "windows": {
          "amd64": {
            "sha1": "1fb48595e51b74c83886ec07b277ec914a757aaf",
            "maven": {
              "groupId" : "org.bytedeco.javacpp-presets",
              "artifactId" : "llvm",
              "version" : "6.0.1-1.4.2",
              "classifier": "windows-x86_64"
            },
          },
        },
        "<others>": {
          "<others>": {
            "optional": True,
          }
        }
      },
    }
  },

  "projects" : {

    # ------------- Graal -------------

    "org.graalvm.compiler.serviceprovider" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["JVMCI_SERVICES", "JVMCI_API"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "API,Graal",
    },

    "org.graalvm.compiler.serviceprovider.jdk8" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.serviceprovider",
        "JVMCI_SERVICES",
        "JVMCI_API"
      ],
      "overlayTarget" : "org.graalvm.compiler.serviceprovider",
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8",
      "checkPackagePrefix" : "false",
      "workingSets" : "API,Graal",
    },

    "org.graalvm.compiler.serviceprovider.jdk11" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["org.graalvm.compiler.serviceprovider"],
      "uses" : [
        "org.graalvm.compiler.serviceprovider.JMXService",
        "org.graalvm.compiler.phases.common.jmx.HotSpotMBeanOperationProvider",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "11+",
      "checkPackagePrefix" : "false",
      "overlayTarget" : "org.graalvm.compiler.serviceprovider",
      "multiReleaseJarVersion" : "11",
      "workingSets" : "API,Graal",
    },

    "org.graalvm.compiler.serviceprovider.jdk13" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["org.graalvm.compiler.serviceprovider"],
      "uses" : [
        "org.graalvm.compiler.serviceprovider.JMXService",
        "org.graalvm.compiler.phases.common.jmx.HotSpotMBeanOperationProvider",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "13+",
      "checkPackagePrefix" : "false",
      "overlayTarget" : "org.graalvm.compiler.serviceprovider",
      "multiReleaseJarVersion" : "13",
      "workingSets" : "API,Graal",
    },

    "org.graalvm.compiler.processor" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Codegen",
    },

    "org.graalvm.compiler.serviceprovider.processor" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["org.graalvm.compiler.processor"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Codegen",
    },

    "org.graalvm.compiler.options" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "JVMCI_SERVICES",
        "JVMCI_API",
        "sdk:GRAAL_SDK"
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "uses" : ["org.graalvm.compiler.options.OptionDescriptors"],
      "javaCompliance" : "8+",
      "workingSets" : "Graal",
    },

    "org.graalvm.compiler.options.processor" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["org.graalvm.compiler.processor"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Codegen",
    },

    "org.graalvm.compiler.options.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.options",
        "mx:JUNIT",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal",
    },

    "org.graalvm.compiler.debug" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "uses" : [
        "org.graalvm.compiler.debug.DebugHandlersFactory",
        "org.graalvm.compiler.debug.TTYStreamProvider",
      ],
      "dependencies" : [
        "JVMCI_API",
        "org.graalvm.compiler.serviceprovider",
        "org.graalvm.graphio",
        "org.graalvm.compiler.options"
      ],
      "annotationProcessors" : ["GRAAL_OPTIONS_PROCESSOR"],
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Debug",
    },

    "org.graalvm.compiler.debug.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JUNIT",
        "org.graalvm.compiler.debug",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Debug,Test",
    },

    "org.graalvm.compiler.code" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.graph",
      ],
      "annotationProcessors" : ["GRAAL_SERVICEPROVIDER_PROCESSOR"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal",
    },

    "org.graalvm.graphio" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "API,Graal",
    },

    "org.graalvm.libgraal" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
          "sdk:GRAAL_SDK",
          "JVMCI_HOTSPOT",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "API,Graal",
    },

    "org.graalvm.libgraal.jdk8" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
          "org.graalvm.libgraal",
          "sdk:GRAAL_SDK",
          "JVMCI_HOTSPOT",
      ],
      "checkPackagePrefix" : "false",
      "overlayTarget" : "org.graalvm.libgraal",
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8",
      "workingSets" : "API,Graal",
    },

    "org.graalvm.libgraal.jdk11" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
          "org.graalvm.libgraal",
          "sdk:GRAAL_SDK",
          "JVMCI_HOTSPOT",
      ],
      "overlayTarget" : "org.graalvm.libgraal",
      "multiReleaseJarVersion" : "11",
      "checkPackagePrefix" : "false",
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "11..12",
      "workingSets" : "API,Graal",
    },

    "org.graalvm.libgraal.jdk13" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
          "org.graalvm.libgraal",
          "sdk:GRAAL_SDK",
          "JVMCI_HOTSPOT",
      ],
      "overlayTarget" : "org.graalvm.libgraal",
      "multiReleaseJarVersion" : "13",
      "checkPackagePrefix" : "false",
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "13+",
      "workingSets" : "API,Graal",
    },

    "org.graalvm.util" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "sdk:GRAAL_SDK",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "API,Graal",
    },

    "org.graalvm.util.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.util",
        "org.graalvm.compiler.core.test",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "API,Graal",
    },

    "org.graalvm.compiler.api.directives" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "API,Graal",
    },

    "org.graalvm.compiler.api.directives.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "dependencies" : [
        "org.graalvm.compiler.core.test",
        "JVMCI_HOTSPOT",
      ],
      "javaCompliance" : "8+",
      "workingSets" : "API,Graal",
    },

    "org.graalvm.compiler.api.runtime" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "JVMCI_API",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "API,Graal",
    },

    "org.graalvm.compiler.api.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JUNIT",
        "JVMCI_SERVICES",
        "org.graalvm.compiler.api.runtime",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "API,Graal,Test",
    },

    "org.graalvm.compiler.api.replacements" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["JVMCI_API"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "API,Graal,Replacements",
    },

    "org.graalvm.compiler.hotspot" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "JVMCI_HOTSPOT",
        "org.graalvm.compiler.api.runtime",
        "org.graalvm.compiler.replacements",
        "org.graalvm.compiler.printer",
        "org.graalvm.compiler.runtime",
      ],
      "uses" : [
        "org.graalvm.compiler.hotspot.HotSpotGraalManagementRegistration",
      ],

      "checkstyle" : "org.graalvm.compiler.graph",
      "annotationProcessors" : [
        "GRAAL_NODEINFO_PROCESSOR",
        "GRAAL_COMPILER_MATCH_PROCESSOR",
        "GRAAL_REPLACEMENTS_PROCESSOR",
        "GRAAL_OPTIONS_PROCESSOR",
        "GRAAL_SERVICEPROVIDER_PROCESSOR",
      ],
      "javaCompliance" : "8+",
      "workingSets" : "Graal,HotSpot",
    },

    "org.graalvm.compiler.hotspot.jdk8" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies": [
        "org.graalvm.compiler.hotspot",
      ],
      "overlayTarget" : "org.graalvm.compiler.hotspot",
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8",
      "workingSets" : "Graal,HotSpot",
    },

    "org.graalvm.compiler.hotspot.jdk11" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies": ["org.graalvm.compiler.hotspot"],
      "overlayTarget" : "org.graalvm.compiler.hotspot",
      "multiReleaseJarVersion" : "11",
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "11+",
      "workingSets" : "Graal,HotSpot",
    },

    "org.graalvm.compiler.hotspot.jdk12" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies": ["org.graalvm.compiler.hotspot"],
      "overlayTarget" : "org.graalvm.compiler.hotspot",
      "multiReleaseJarVersion" : "12",
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "12+",
      "workingSets" : "Graal,HotSpot",
    },

    "org.graalvm.compiler.hotspot.jdk13" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies": ["org.graalvm.compiler.hotspot"],
      "overlayTarget" : "org.graalvm.compiler.hotspot",
      "multiReleaseJarVersion" : "13",
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "13+",
      "workingSets" : "Graal,HotSpot",
    },

    "org.graalvm.compiler.hotspot.management" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.hotspot",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "annotationProcessors" : [
        "GRAAL_SERVICEPROVIDER_PROCESSOR",
      ],
      "javaCompliance" : "8+",
      "workingSets" : "Graal,HotSpot",
    },

    "org.graalvm.compiler.hotspot.management.jdk11" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.serviceprovider.jdk11",
        "org.graalvm.compiler.hotspot.management",
      ],
      "imports" : [
        "java.management",
      ],
      "overlayTarget" : "org.graalvm.compiler.hotspot.management",
      "multiReleaseJarVersion" : "11",
      "checkstyle" : "org.graalvm.compiler.graph",
      "annotationProcessors" : [
        "GRAAL_SERVICEPROVIDER_PROCESSOR",
      ],
      "javaCompliance" : "11+",
      "workingSets" : "Graal,HotSpot",
    },

    "org.graalvm.compiler.hotspot.management.jdk13" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.serviceprovider.jdk13",
        "org.graalvm.compiler.hotspot.management",
      ],
      "imports" : [
        "java.management",
      ],
      "overlayTarget" : "org.graalvm.compiler.hotspot.management",
      "multiReleaseJarVersion" : "13",
      "checkstyle" : "org.graalvm.compiler.graph",
      "annotationProcessors" : [
        "GRAAL_SERVICEPROVIDER_PROCESSOR",
      ],
      "javaCompliance" : "13+",
      "workingSets" : "Graal,HotSpot",
    },

    "org.graalvm.compiler.hotspot.aarch64" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.core.aarch64",
        "org.graalvm.compiler.hotspot",
        "org.graalvm.compiler.replacements.aarch64",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "annotationProcessors" : [
        "GRAAL_SERVICEPROVIDER_PROCESSOR",
        "GRAAL_NODEINFO_PROCESSOR"
      ],
      "javaCompliance" : "8+",
      "workingSets" : "Graal,HotSpot,AArch64",
    },

    "org.graalvm.compiler.hotspot.amd64" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.core.amd64",
        "org.graalvm.compiler.hotspot",
        "org.graalvm.compiler.replacements.amd64",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "annotationProcessors" : [
        "GRAAL_SERVICEPROVIDER_PROCESSOR",
        "GRAAL_NODEINFO_PROCESSOR",
        "GRAAL_REPLACEMENTS_PROCESSOR",
      ],
      "javaCompliance" : "8+",
      "workingSets" : "Graal,HotSpot,AMD64",
    },

    "org.graalvm.compiler.hotspot.sparc" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.hotspot",
        "org.graalvm.compiler.core.sparc",
        "org.graalvm.compiler.replacements.sparc",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "annotationProcessors" : ["GRAAL_SERVICEPROVIDER_PROCESSOR"],
      "javaCompliance" : "8+",
      "workingSets" : "Graal,HotSpot,SPARC",
    },

    "org.graalvm.compiler.hotspot.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.libgraal",
        "org.graalvm.compiler.replacements.test",
        "org.graalvm.compiler.hotspot",
      ],
      "annotationProcessors" : [
        "GRAAL_NODEINFO_PROCESSOR",
        "GRAAL_REPLACEMENTS_PROCESSOR"
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,HotSpot,Test",
    },

    "org.graalvm.compiler.hotspot.jdk9.test" : {
      "testProject" : True,
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.hotspot.test"
      ],
      "checkstyle": "org.graalvm.compiler.graph",
      "javaCompliance" : "9+",
      "imports" : [
        "jdk.internal.misc",
      ],
      "workingSets" : "Graal,HotSpot,Test",
    },

    "org.graalvm.compiler.hotspot.lir.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.hotspot",
        "org.graalvm.compiler.lir.jtt",
        "org.graalvm.compiler.lir.test",
        "JVMCI_API",
        "JVMCI_HOTSPOT",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,HotSpot,Test",
    },

    "org.graalvm.compiler.hotspot.aarch64.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.asm.aarch64",
        "org.graalvm.compiler.hotspot.test",
        "org.graalvm.compiler.hotspot.aarch64",
      ],
      "annotationProcessors" : ["GRAAL_NODEINFO_PROCESSOR"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,HotSpot,AArch64,Test",
    },

    "org.graalvm.compiler.hotspot.amd64.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.asm.amd64",
        "org.graalvm.compiler.hotspot.test",
        "org.graalvm.compiler.lir.amd64",
        "org.graalvm.compiler.lir.jtt",
      ],
      "annotationProcessors" : ["GRAAL_NODEINFO_PROCESSOR"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,HotSpot,AMD64,Test",
    },

    "org.graalvm.compiler.nodeinfo" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Graph",
    },

    "org.graalvm.compiler.nodeinfo.processor" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["org.graalvm.compiler.processor"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Graph",
    },

    "org.graalvm.compiler.graph" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.nodeinfo",
        "org.graalvm.compiler.core.common",
        "org.graalvm.compiler.bytecode",
      ],
      "javaCompliance" : "8+",
      "checkstyleVersion" : "8.8",
      "annotationProcessors" : [
        "GRAAL_OPTIONS_PROCESSOR",
        "GRAAL_NODEINFO_PROCESSOR"
      ],
      "workingSets" : "Graal,Graph",
    },

    "org.graalvm.compiler.graph.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "dependencies" : [
        "mx:JUNIT",
        "org.graalvm.compiler.api.test",
        "org.graalvm.compiler.graph",
        "org.graalvm.graphio",
      ],
      "annotationProcessors" : ["GRAAL_NODEINFO_PROCESSOR"],
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Graph,Test",
    },

    "org.graalvm.compiler.asm" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "JVMCI_API",
        "org.graalvm.compiler.core.common"
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Assembler",
    },

    "org.graalvm.compiler.asm.aarch64" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.asm",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Assembler,AArch64",
    },

    "org.graalvm.compiler.asm.amd64" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.asm",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Assembler,AMD64",
    },

    "org.graalvm.compiler.asm.sparc" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.asm",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Assembler,SPARC",
    },

    "org.graalvm.compiler.asm.sparc.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.asm.test",
        "org.graalvm.compiler.asm.sparc",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Assembler,SPARC,Test",
    },

    "org.graalvm.compiler.bytecode" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["JVMCI_API"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Java",
    },

    "org.graalvm.compiler.asm.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.api.test",
        "org.graalvm.compiler.code",
        "org.graalvm.compiler.runtime",
        "org.graalvm.compiler.test",
        "org.graalvm.compiler.debug",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Assembler,Test",
    },

    "org.graalvm.compiler.asm.aarch64.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.asm.test",
        "org.graalvm.compiler.asm.aarch64",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Assembler,AArch64,Test",
    },

    "org.graalvm.compiler.asm.amd64.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.asm.test",
        "org.graalvm.compiler.asm.amd64",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Assembler,AMD64,Test",
    },

    "org.graalvm.compiler.lir" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.asm",
        "org.graalvm.compiler.code",
      ],
      "annotationProcessors" : ["GRAAL_OPTIONS_PROCESSOR"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,LIR",
    },

    "org.graalvm.compiler.lir.jtt" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.jtt",
      ],
      "annotationProcessors" : ["GRAAL_NODEINFO_PROCESSOR"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,LIR",
      "spotbugs" : "false",
      "testProject" : True,
    },

    "org.graalvm.compiler.lir.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JUNIT",
        "org.graalvm.compiler.lir",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,LIR",
    },

    "org.graalvm.compiler.lir.aarch64" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.lir",
        "org.graalvm.compiler.asm.aarch64",
      ],
      "annotationProcessors" : ["GRAAL_OPTIONS_PROCESSOR"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,LIR,AArch64",
    },

    "org.graalvm.compiler.lir.aarch64.jdk11" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.lir.aarch64",
      ],
      "annotationProcessors" : ["GRAAL_OPTIONS_PROCESSOR"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "11+",
      "workingSets" : "Graal,LIR,AArch64",
      "overlayTarget" : "org.graalvm.compiler.lir.aarch64",
      "multiReleaseJarVersion" : "11",
    },

    "org.graalvm.compiler.lir.amd64" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.lir",
        "org.graalvm.compiler.asm.amd64",
      ],
      "annotationProcessors" : ["GRAAL_OPTIONS_PROCESSOR"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,LIR,AMD64",
    },

    "org.graalvm.compiler.lir.sparc" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.asm.sparc",
        "org.graalvm.compiler.lir",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,LIR,SPARC",
    },

    "org.graalvm.compiler.word" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["org.graalvm.compiler.nodes"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "annotationProcessors" : ["GRAAL_NODEINFO_PROCESSOR"],
      "workingSets" : "API,Graal",
    },

    "org.graalvm.compiler.replacements" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.api.directives",
        "org.graalvm.compiler.java",
        "org.graalvm.compiler.loop.phases",
        "org.graalvm.compiler.word",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "annotationProcessors" : [
        "GRAAL_OPTIONS_PROCESSOR",
        "GRAAL_REPLACEMENTS_PROCESSOR",
        "GRAAL_NODEINFO_PROCESSOR",
      ],
      "workingSets" : "Graal,Replacements",
    },

    "org.graalvm.compiler.replacements.aarch64" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.replacements",
        "org.graalvm.compiler.lir.aarch64",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "annotationProcessors" : [
        "GRAAL_NODEINFO_PROCESSOR",
        "GRAAL_REPLACEMENTS_PROCESSOR",
      ],
      "workingSets" : "Graal,Replacements,AArch64",
    },

    "org.graalvm.compiler.replacements.amd64" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.replacements",
        "org.graalvm.compiler.lir.amd64",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "annotationProcessors" : [
        "GRAAL_NODEINFO_PROCESSOR",
        "GRAAL_REPLACEMENTS_PROCESSOR",
      ],
      "workingSets" : "Graal,Replacements,AMD64",
    },

    "org.graalvm.compiler.replacements.sparc" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.replacements",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Replacements,SPARC",
    },

    "org.graalvm.compiler.replacements.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.core.test",
        "org.graalvm.compiler.replacements.amd64",
      ],
      "annotationProcessors" : [
        "GRAAL_NODEINFO_PROCESSOR",
        "GRAAL_REPLACEMENTS_PROCESSOR"
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Replacements,Test",
      "jacoco" : "exclude",
    },

    "org.graalvm.compiler.replacements.jdk9.test" : {
      "testProject" : True,
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.replacements.test"
      ],
      "checkstyle": "org.graalvm.compiler.graph",
      "javaCompliance" : "9+",
      "imports" : [
        "jdk.internal.misc",
      ],
    },

    "org.graalvm.compiler.replacements.jdk9_11.test" : {
      "testProject" : True,
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.replacements.test"
      ],
      "checkstyle": "org.graalvm.compiler.graph",
      "javaCompliance" : "9..11",
      "imports" : [
        "jdk.internal.misc",
      ],
    },

    "org.graalvm.compiler.replacements.jdk12.test" : {
      "testProject" : True,
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.replacements.test"
      ],
      "checkstyle": "org.graalvm.compiler.graph",
      "javaCompliance" : "12+",
      "imports" : [
        "jdk.internal.misc",
      ],
    },

    "org.graalvm.compiler.replacements.processor" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["org.graalvm.compiler.processor"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Replacements",
    },

    "org.graalvm.compiler.nodes" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.api.replacements",
        "org.graalvm.compiler.lir",
        "sdk:GRAAL_SDK",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "annotationProcessors" : [
        "GRAAL_NODEINFO_PROCESSOR",
        "GRAAL_REPLACEMENTS_PROCESSOR",
        "GRAAL_OPTIONS_PROCESSOR"
      ],
      "workingSets" : "Graal,Graph",
    },

    "org.graalvm.compiler.nodes.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["org.graalvm.compiler.core.test"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Graph",
    },

    "org.graalvm.compiler.phases" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["org.graalvm.compiler.nodes"],
      "annotationProcessors" : ["GRAAL_OPTIONS_PROCESSOR"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Phases",
    },

    "org.graalvm.compiler.phases.common" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["org.graalvm.compiler.phases"],
      "annotationProcessors" : [
        "GRAAL_NODEINFO_PROCESSOR",
        "GRAAL_OPTIONS_PROCESSOR"
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Phases",
    },

    "org.graalvm.compiler.phases.common.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.api.test",
        "org.graalvm.compiler.runtime",
        "mx:JUNIT",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Test",
    },

    "org.graalvm.compiler.virtual" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["org.graalvm.compiler.phases.common"],
      "annotationProcessors" : [
        "GRAAL_OPTIONS_PROCESSOR",
        "GRAAL_NODEINFO_PROCESSOR"
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Phases",
    },

    "org.graalvm.compiler.virtual.bench" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["mx:JMH_1_21", "org.graalvm.compiler.microbenchmarks"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
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
        "org.graalvm.compiler.api.test",
        "org.graalvm.compiler.java",
        "org.graalvm.compiler.runtime",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "checkPackagePrefix" : "false",
      "annotationProcessors" : ["mx:JMH_1_21"],
      "spotbugsIgnoresGenerated" : True,
      "workingSets" : "Graal,Bench",
      "testProject" : True,
    },

    "org.graalvm.compiler.loop" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["org.graalvm.compiler.nodes"],
      "annotationProcessors" : ["GRAAL_OPTIONS_PROCESSOR"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal",
    },

    "org.graalvm.compiler.loop.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.loop",
        "org.graalvm.compiler.core.test"
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Test",
    },

    "org.graalvm.compiler.loop.phases" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
     "org.graalvm.compiler.loop",
     "org.graalvm.compiler.phases.common",
       ],
      "annotationProcessors" : ["GRAAL_OPTIONS_PROCESSOR"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Phases",
    },

    "org.graalvm.compiler.core" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.virtual",
        "org.graalvm.compiler.loop.phases",
      ],
      "uses" : [
        "org.graalvm.compiler.core.match.MatchStatementSet",
        "org.graalvm.compiler.hotspot.HotSpotCodeCacheListener",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "annotationProcessors" : [
        "GRAAL_SERVICEPROVIDER_PROCESSOR",
        "GRAAL_COMPILER_MATCH_PROCESSOR",
        "GRAAL_OPTIONS_PROCESSOR",
      ],
      "workingSets" : "Graal",
    },

    "org.graalvm.compiler.core.match.processor" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["org.graalvm.compiler.processor"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Codegen",
    },

    "org.graalvm.compiler.core.aarch64" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.core",
        "org.graalvm.compiler.lir.aarch64",
        "org.graalvm.compiler.java",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "annotationProcessors" : [
        "GRAAL_NODEINFO_PROCESSOR",
        "GRAAL_COMPILER_MATCH_PROCESSOR",
      ],
      "javaCompliance" : "8+",
      "workingSets" : "Graal,AArch64",
    },

    "org.graalvm.compiler.core.aarch64.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.lir.jtt",
        "org.graalvm.compiler.lir.aarch64",
        "JVMCI_HOTSPOT"
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,AArch64,Test",
    },

    "org.graalvm.compiler.core.amd64" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.core",
        "org.graalvm.compiler.lir.amd64",
        "org.graalvm.compiler.java",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "annotationProcessors" : [
        "GRAAL_NODEINFO_PROCESSOR",
        "GRAAL_COMPILER_MATCH_PROCESSOR",
        "GRAAL_OPTIONS_PROCESSOR",
      ],
      "javaCompliance" : "8+",
      "workingSets" : "Graal,AMD64",
    },

    "org.graalvm.compiler.core.amd64.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.lir.jtt",
        "org.graalvm.compiler.lir.amd64",
        "org.graalvm.compiler.core.amd64",
        "JVMCI_HOTSPOT"
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,AMD64,Test",
    },

    "org.graalvm.compiler.core.sparc" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.core",
        "org.graalvm.compiler.lir.sparc",
        "org.graalvm.compiler.java"
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "annotationProcessors" : [
        "GRAAL_NODEINFO_PROCESSOR",
        "GRAAL_COMPILER_MATCH_PROCESSOR",
      ],
      "javaCompliance" : "8+",
      "workingSets" : "Graal,SPARC",
    },

    "org.graalvm.compiler.hotspot.sparc.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.hotspot",
        "org.graalvm.compiler.lir.jtt",
        "JVMCI_HOTSPOT"
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,SPARC,Test",
    },

    "org.graalvm.compiler.core.llvm" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.core",
        "org.graalvm.compiler.lir.aarch64",
        "LLVM_WRAPPER",
        "LLVM_PLATFORM_SPECIFIC",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "annotationProcessors" : [
        "GRAAL_NODEINFO_PROCESSOR",
        "GRAAL_COMPILER_MATCH_PROCESSOR",
        "GRAAL_OPTIONS_PROCESSOR",
      ],
      "javaCompliance" : "8+",
      "workingSets" : "Graal,LLVM",
    },

    "org.graalvm.compiler.runtime" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["org.graalvm.compiler.core"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal",
    },

    "org.graalvm.compiler.java" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.phases",
      ],
      "annotationProcessors" : ["GRAAL_OPTIONS_PROCESSOR"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Java",
    },

    "org.graalvm.compiler.core.common" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.util",
        "org.graalvm.compiler.debug",
        "sdk:GRAAL_SDK",
      ],
      "annotationProcessors" : ["GRAAL_OPTIONS_PROCESSOR"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Java",
    },

    "org.graalvm.compiler.printer" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.core",
        "org.graalvm.compiler.java",
      ],
      "uses" : ["org.graalvm.compiler.code.DisassemblerProvider"],
      "annotationProcessors" : [
        "GRAAL_OPTIONS_PROCESSOR",
        "GRAAL_SERVICEPROVIDER_PROCESSOR"
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Graph",
    },

    "org.graalvm.compiler.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.debug",
        "org.graalvm.util",
        "mx:JUNIT",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Test",
    },

    "org.graalvm.compiler.core.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.api.directives",
        "org.graalvm.compiler.java",
        "org.graalvm.compiler.test",
        "org.graalvm.compiler.runtime",
        "org.graalvm.compiler.graph.test",
        "org.graalvm.compiler.printer",
        "JAVA_ALLOCATION_INSTRUMENTER",
        "ASM_TREE5",
      ],
      "uses" : ["org.graalvm.compiler.options.OptionDescriptors"],
      "annotationProcessors" : ["GRAAL_NODEINFO_PROCESSOR"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Test",
      "jacoco" : "exclude",
    },

    "org.graalvm.compiler.core.jdk9.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.core.test",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "9+",
      "workingSets" : "Graal,Test",
      "jacoco" : "exclude",
      "testProject" : True,
    },

    "org.graalvm.compiler.jtt" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.core.test",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Test",
      "jacoco" : "exclude",
      "spotbugs" : "false",
      "testProject" : True,
    },

    # ------------- JDK AOT -------------

    "jdk.tools.jaotc" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "JVMCI_HOTSPOT",
        "jdk.tools.jaotc.binformat",
        "org.graalvm.compiler.asm.amd64",
        "org.graalvm.compiler.asm.aarch64",
      ],

      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "11+",
      "workingSets" : "Graal,HotSpot",
    },

    "jdk.tools.jaotc.binformat" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "JVMCI_HOTSPOT",
        "org.graalvm.compiler.hotspot",
      ],

      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "11+",
      "workingSets" : "Graal,HotSpot",
    },

    "jdk.tools.jaotc.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "jdk.tools.jaotc",
        "mx:JUNIT",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "11+",
      "workingSets" : "Graal,Test",
    },

    # ------------- GraalTruffle -------------

    "org.graalvm.compiler.truffle.common" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "JVMCI_API",
        "org.graalvm.graphio",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "annotationProcessors" : [
      ],
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Truffle",
      "jacoco" : "exclude",
    },

    "org.graalvm.compiler.truffle.common.processor" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.options.processor",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Truffle",
    },

    "org.graalvm.compiler.truffle.compiler" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.truffle.common",
        "org.graalvm.compiler.core",
        "org.graalvm.compiler.replacements",
      ],
      "uses" : [
        "org.graalvm.compiler.truffle.compiler.substitutions.TruffleInvocationPluginProvider",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "annotationProcessors" : [
        "TRUFFLE_COMMON_PROCESSOR",
        "GRAAL_NODEINFO_PROCESSOR",
        "GRAAL_REPLACEMENTS_PROCESSOR",
        "GRAAL_OPTIONS_PROCESSOR",
        "GRAAL_SERVICEPROVIDER_PROCESSOR",
      ],
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Truffle",
      "jacoco" : "exclude",
    },

    "org.graalvm.compiler.truffle.compiler.amd64" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.replacements.amd64",
        "org.graalvm.compiler.truffle.compiler",
      ],
      "uses" : [
        "org.graalvm.compiler.truffle.compiler.substitutions.TruffleInvocationPluginProvider",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "annotationProcessors" : [
        "GRAAL_NODEINFO_PROCESSOR",
        "GRAAL_REPLACEMENTS_PROCESSOR",
        "GRAAL_OPTIONS_PROCESSOR",
        "GRAAL_SERVICEPROVIDER_PROCESSOR",
      ],
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Truffle",
      "jacoco" : "exclude",
    },

    "org.graalvm.compiler.truffle.runtime.serviceprovider" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["JVMCI_SERVICES"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Truffle",
    },

    "org.graalvm.compiler.truffle.runtime.serviceprovider.jdk8" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.truffle.runtime.serviceprovider",
      ],
      "overlayTarget" : "org.graalvm.compiler.truffle.runtime.serviceprovider",
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8",
      "checkPackagePrefix" : "false",
      "workingSets" : "Graal,Truffle",
    },

    "org.graalvm.compiler.truffle.runtime.serviceprovider.jdk9" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.truffle.runtime.serviceprovider",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "9+",
      "checkPackagePrefix" : "false",
      "overlayTarget" : "org.graalvm.compiler.truffle.runtime.serviceprovider",
      "multiReleaseJarVersion" : "9",
      "workingSets" : "Graal,Truffle",
    },

    "org.graalvm.compiler.truffle.runtime" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.truffle.common",
        "org.graalvm.compiler.truffle.runtime.serviceprovider",
        "truffle:TRUFFLE_API",
      ],
      "uses" : [
        "com.oracle.truffle.api.impl.TruffleLocator",
        "com.oracle.truffle.api.object.LayoutFactory",
        "org.graalvm.compiler.truffle.runtime.TruffleTypes",
        "org.graalvm.compiler.truffle.runtime.LoopNodeFactory",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "annotationProcessors" : [
        "TRUFFLE_COMMON_PROCESSOR",
        "truffle:TRUFFLE_DSL_PROCESSOR",
      ],
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Truffle",
      "jacoco" : "exclude",
    },

    "org.graalvm.compiler.truffle.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.truffle.compiler",
        "org.graalvm.compiler.truffle.compiler.amd64",
        "org.graalvm.compiler.truffle.runtime",
        "org.graalvm.compiler.core.test",
        "truffle:TRUFFLE_SL_TEST",
        "truffle:TRUFFLE_INSTRUMENT_TEST",
      ],
      "uses": [
        "org.graalvm.compiler.truffle.compiler.substitutions.TruffleInvocationPluginProvider",
      ],
      "annotationProcessors" : [
        "GRAAL_NODEINFO_PROCESSOR",
        "truffle:TRUFFLE_DSL_PROCESSOR"
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Truffle,Test",
      "jacoco" : "exclude",
    },

    "org.graalvm.compiler.truffle.common.hotspot" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.truffle.common",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "annotationProcessors" : [
        "GRAAL_OPTIONS_PROCESSOR",
      ],
      "workingSets" : "Graal,Truffle",
    },

    "org.graalvm.compiler.truffle.common.hotspot.libgraal" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.truffle.common.hotspot",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Truffle",
    },

    "org.graalvm.compiler.truffle.compiler.hotspot" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.truffle.compiler",
        "org.graalvm.compiler.truffle.common.hotspot",
        "org.graalvm.compiler.hotspot",
      ],
      "uses" : [
        "org.graalvm.compiler.hotspot.HotSpotBackendFactory",
        "org.graalvm.compiler.nodes.graphbuilderconf.NodeIntrinsicPluginFactory",
        "org.graalvm.compiler.truffle.compiler.hotspot.TruffleCallBoundaryInstrumentationFactory",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "annotationProcessors" : [
        "GRAAL_OPTIONS_PROCESSOR",
        "GRAAL_SERVICEPROVIDER_PROCESSOR"
      ],
      "workingSets" : "Graal,Truffle",
    },

    "org.graalvm.compiler.truffle.runtime.hotspot.java" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.truffle.compiler.hotspot",
        "org.graalvm.compiler.truffle.runtime.hotspot",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "annotationProcessors" : [
          "GRAAL_SERVICEPROVIDER_PROCESSOR",
      ],
      "workingSets" : "Graal,Truffle",
    },

    "org.graalvm.compiler.truffle.runtime.hotspot.libgraal" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.libgraal",
        "org.graalvm.compiler.truffle.runtime.hotspot",
        "org.graalvm.compiler.truffle.common.hotspot.libgraal",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8,13+",
      "annotationProcessors" : [
        "truffle:TRUFFLE_DSL_PROCESSOR",
      ],
      "workingSets" : "Graal,Truffle",
    },

    "org.graalvm.compiler.truffle.compiler.hotspot.libgraal" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.libgraal",
        "org.graalvm.compiler.truffle.compiler.hotspot",
        "org.graalvm.compiler.truffle.common.hotspot.libgraal",
      ],
      "uses" : [
        "org.graalvm.compiler.hotspot.HotSpotBackendFactory",
        "org.graalvm.compiler.nodes.graphbuilderconf.NodeIntrinsicPluginFactory",
        "org.graalvm.compiler.truffle.compiler.hotspot.TruffleCallBoundaryInstrumentationFactory",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8,13+",
      "annotationProcessors" : [
        "GRAAL_OPTIONS_PROCESSOR",
        "TRUFFLE_LIBGRAAL_PROCESSOR",
        "GRAAL_SERVICEPROVIDER_PROCESSOR"
      ],
      "workingSets" : "Graal,Truffle",
    },

    "org.graalvm.compiler.truffle.compiler.hotspot.libgraal.processor" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.processor",
        "org.graalvm.compiler.truffle.common.hotspot.libgraal",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8,13+",
      "workingSets" : "Graal,Truffle",
    },

    "org.graalvm.compiler.truffle.runtime.hotspot" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.truffle.runtime",
        "org.graalvm.compiler.truffle.common.hotspot",
        "JVMCI_HOTSPOT",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Truffle",
    },

    "org.graalvm.compiler.truffle.runtime.hotspot.jdk8+13" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.truffle.runtime",
        "JVMCI_HOTSPOT",
      ],
      "checkPackagePrefix" : "false",
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8,13+",
      "overlayTarget" : "org.graalvm.compiler.truffle.runtime.hotspot",
      "workingSets" : "Graal,Truffle",
    },

    "org.graalvm.compiler.truffle.runtime.hotspot.jdk9" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.truffle.runtime.hotspot",
        "JVMCI_HOTSPOT",
      ],
      "checkPackagePrefix" : "false",
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "9+",
      "overlayTarget" : "org.graalvm.compiler.truffle.runtime.hotspot",
      "multiReleaseJarVersion" : "9",
      "workingSets" : "Graal,Truffle",
    },

    "org.graalvm.compiler.truffle.compiler.hotspot.amd64" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.truffle.compiler.hotspot",
        "org.graalvm.compiler.hotspot.amd64",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "annotationProcessors" : [
        "GRAAL_SERVICEPROVIDER_PROCESSOR",
      ],
      "workingSets" : "Graal,Truffle",
    },

    "org.graalvm.compiler.truffle.compiler.hotspot.sparc" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.truffle.compiler.hotspot",
        "org.graalvm.compiler.hotspot.sparc",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "annotationProcessors" : ["GRAAL_SERVICEPROVIDER_PROCESSOR"],
      "workingSets" : "Graal,Truffle,SPARC",
    },

    "org.graalvm.compiler.truffle.compiler.hotspot.aarch64" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.hotspot.aarch64",
        "org.graalvm.compiler.truffle.compiler.hotspot",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "annotationProcessors" : ["GRAAL_SERVICEPROVIDER_PROCESSOR"],
      "workingSets" : "Graal,Truffle,AArch64",
    },

    # ------------- blackbox micro benchmarks -------------

    "org.graalvm.micro.benchmarks" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JMH_1_21",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "checkPackagePrefix" : "false",
      "annotationProcessors" : ["mx:JMH_1_21"],
      "spotbugsIgnoresGenerated" : True,
      "workingSets" : "Graal,Bench",
      "testProject" : True,
    },


  },

  "distributions" : {

    # ------------- Distributions -------------

    "GRAAL_OPTIONS" : {
      "subDir" : "src",
      "dependencies" : ["org.graalvm.compiler.options"],
      "distDependencies" : [
        "sdk:GRAAL_SDK",
        "JVMCI_API",
      ],
      "maven": False,
    },

    "GRAAL_GRAPHIO" : {
      "subDir" : "src",
      "dependencies" : ["org.graalvm.graphio"],
      "distDependencies" : [
      ],
      "maven": False,
    },

    "GRAAL_NODEINFO" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.compiler.nodeinfo",
      ],
      "maven": False,
    },

    "GRAAL_SERVICEPROVIDER" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.compiler.serviceprovider",
      ],
      "distDependencies" : [
        "JVMCI_SERVICES"
      ],
      "maven": False,
    },

    "GRAAL_API" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.compiler.api.replacements",
        "org.graalvm.compiler.api.runtime",
        "org.graalvm.compiler.graph",
      ],
      "distDependencies" : [
        "sdk:GRAAL_SDK",
        "JVMCI_API",
        "GRAAL_GRAPHIO",
        "GRAAL_NODEINFO",
        "GRAAL_OPTIONS",
        "GRAAL_SERVICEPROVIDER",
      ],
      "maven": False,
    },

    "GRAAL_COMPILER" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.compiler.core",
      ],
      "distDependencies" : [
        "GRAAL_API",
        "GRAAL_SERVICEPROVIDER",
      ],
      "maven": False,
    },

    "GRAAL_RUNTIME" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.compiler.replacements",
        "org.graalvm.compiler.runtime",
        "org.graalvm.compiler.code",
        "org.graalvm.compiler.printer",
        "org.graalvm.compiler.core.aarch64",
        "org.graalvm.compiler.replacements.aarch64",
        "org.graalvm.compiler.core.amd64",
        "org.graalvm.compiler.lir.aarch64",
        "org.graalvm.compiler.replacements.amd64",
        "org.graalvm.compiler.core.sparc",
        "org.graalvm.compiler.replacements.sparc",
      ],
      "distDependencies" : [
        "GRAAL_API",
        "GRAAL_COMPILER",
        "GRAAL_GRAPHIO",
      ],
      "maven": False,
    },

    "GRAAL_HOTSPOT" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.compiler.hotspot.aarch64",
        "org.graalvm.compiler.hotspot.amd64",
        "org.graalvm.compiler.hotspot.sparc",
        "org.graalvm.compiler.hotspot",
      ],
      "distDependencies" : [
        "JVMCI_HOTSPOT",
        "GRAAL_LIBGRAAL",
        "GRAAL_COMPILER",
        "GRAAL_RUNTIME",
      ],
      "maven": False,
    },

    "GRAAL_TEST" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.compiler.api.test",
        "org.graalvm.compiler.api.directives.test",
        "org.graalvm.compiler.asm.sparc.test",
        "org.graalvm.compiler.asm.aarch64.test",
        "org.graalvm.compiler.asm.amd64.test",
        "org.graalvm.compiler.core.aarch64.test",
        "org.graalvm.compiler.core.amd64.test",
        "org.graalvm.compiler.debug.test",
        "org.graalvm.compiler.hotspot.aarch64.test",
        "org.graalvm.compiler.hotspot.amd64.test",
        "org.graalvm.compiler.hotspot.lir.test",
        "org.graalvm.compiler.hotspot.sparc.test",
        "org.graalvm.compiler.options.test",
        "org.graalvm.compiler.jtt",
        "org.graalvm.compiler.lir.jtt",
        "org.graalvm.compiler.lir.test",
        "org.graalvm.compiler.nodes.test",
        "org.graalvm.compiler.phases.common.test",
        "org.graalvm.compiler.truffle.test",
        "org.graalvm.util.test",
        "org.graalvm.compiler.loop.test",
        "org.graalvm.compiler.replacements.jdk9.test",
        "org.graalvm.compiler.replacements.jdk9_11.test",
        "org.graalvm.compiler.replacements.jdk12.test",
        "org.graalvm.compiler.core.jdk9.test",
        "org.graalvm.compiler.hotspot.jdk9.test",
      ],
      "distDependencies" : [
        "JVMCI_HOTSPOT",
        "GRAAL",
        "truffle:TRUFFLE_SL_TEST",
        "truffle:TRUFFLE_INSTRUMENT_TEST",
      ],
      "exclude" : [
        "mx:JUNIT",
        "JAVA_ALLOCATION_INSTRUMENTER",
      ],
      "maven": False,
    },

    "GRAAL_TRUFFLE_COMMON" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.compiler.truffle.common",
      ],
      "distDependencies" : [
        "GRAAL_GRAPHIO",
      ],
      "maven": False,
    },

    "GRAAL_TRUFFLE_COMMON_HOTSPOT" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.compiler.truffle.common.hotspot",
      ],
      "distDependencies" : [
        "GRAAL_RUNTIME",
        "GRAAL_TRUFFLE_COMMON",
        "truffle:TRUFFLE_API",
      ],
      "maven": False,
    },

    "GRAAL_LIBGRAAL" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.libgraal",
      ],
      "distDependencies" : [
        "sdk:GRAAL_SDK",
        "JVMCI_HOTSPOT",
      ],
      "maven": False,
    },

    "GRAAL_TRUFFLE_COMMON_HOTSPOT_LIBGRAAL" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.compiler.truffle.common.hotspot.libgraal",
      ],
      "distDependencies" : [
        "GRAAL_TRUFFLE_COMMON_HOTSPOT",
      ],
      "maven": False,
    },

    "GRAAL_TRUFFLE_COMPILER" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.compiler.truffle.compiler",
      ],
      "distDependencies" : [
        "GRAAL_RUNTIME",
        "GRAAL_TRUFFLE_COMMON",
      ],
      "maven": False,
    },
    "GRAAL_TRUFFLE_RUNTIME" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.compiler.truffle.runtime.serviceprovider",
        "org.graalvm.compiler.truffle.runtime",
      ],
      "distDependencies" : [
        "GRAAL_TRUFFLE_COMMON",
        "truffle:TRUFFLE_API",
      ],
      "maven": False,
    },

    "GRAAL_TRUFFLE_COMPILER_HOTSPOT" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.compiler.truffle.compiler.hotspot.amd64",
        "org.graalvm.compiler.truffle.compiler.hotspot.sparc",
        "org.graalvm.compiler.truffle.compiler.hotspot.aarch64",
      ],
      "distDependencies" : [
        "GRAAL_TRUFFLE_COMMON_HOTSPOT",
        "GRAAL_RUNTIME",
        "GRAAL_HOTSPOT",
        "GRAAL_TRUFFLE_COMPILER",
      ],
      "maven": False,
    },
    "GRAAL_TRUFFLE_RUNTIME_HOTSPOT" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.libgraal",
        "org.graalvm.compiler.truffle.runtime.hotspot",
        "org.graalvm.compiler.truffle.runtime.hotspot.java",
        "org.graalvm.compiler.truffle.runtime.hotspot.libgraal",
      ],
      "distDependencies" : [
        "GRAAL_LIBGRAAL",
        "GRAAL_TRUFFLE_COMMON",
        "GRAAL_TRUFFLE_COMMON_HOTSPOT",
        "GRAAL_TRUFFLE_COMMON_HOTSPOT_LIBGRAAL",
        "GRAAL_TRUFFLE_COMPILER_HOTSPOT",
        "GRAAL_TRUFFLE_RUNTIME",
        "truffle:TRUFFLE_API",
      ],
      "maven": False,
    },

    "GRAAL_TRUFFLE_COMPILER_LIBGRAAL": {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.compiler.truffle.compiler.hotspot.amd64",
        "org.graalvm.compiler.truffle.compiler.hotspot.sparc",
        "org.graalvm.compiler.truffle.compiler.hotspot.aarch64",
        "org.graalvm.compiler.truffle.compiler.hotspot.libgraal",
      ],

      "distDependencies" : [
        "GRAAL_TRUFFLE_COMPILER_HOTSPOT",
        "GRAAL_RUNTIME",
        "GRAAL_HOTSPOT",
        "GRAAL_TRUFFLE_COMPILER",
        "GRAAL_TRUFFLE_COMMON_HOTSPOT_LIBGRAAL",
      ],
      "maven": False,
      "javaCompliance" : "8+",
    },

    "GRAAL_PROCESSOR_COMMON" : {
      "subDir": "src",
      "dependencies" : ["org.graalvm.compiler.processor"],
      "maven": False,
    },

    "GRAAL_OPTIONS_PROCESSOR" : {
      "subDir" : "src",
      "dependencies" : ["org.graalvm.compiler.options.processor"],
      "distDependencies" : ["GRAAL_PROCESSOR_COMMON"],
      "maven": False,
    },

    "TRUFFLE_COMMON_PROCESSOR" : {
      "subDir": "src",
      "dependencies" : ["org.graalvm.compiler.truffle.common.processor"],
      "distDependencies" : ["GRAAL_PROCESSOR_COMMON", "GRAAL_OPTIONS_PROCESSOR", "GRAAL_TRUFFLE_COMMON"],
      "maven": False,
    },

    "TRUFFLE_LIBGRAAL_PROCESSOR" : {
      "subDir" : "src",
      "dependencies" : ["org.graalvm.compiler.truffle.compiler.hotspot.libgraal.processor"],
      "distDependencies" : [
        "GRAAL_PROCESSOR_COMMON", "GRAAL_TRUFFLE_COMMON_HOTSPOT_LIBGRAAL"],
      "maven": False,
    },

    "GRAAL_SERVICEPROVIDER_PROCESSOR" : {
      "subDir" : "src",
      "dependencies" : ["org.graalvm.compiler.serviceprovider.processor"],
      "distDependencies" : ["GRAAL_PROCESSOR_COMMON"],
      "maven": False,
    },

    "GRAAL_NODEINFO_PROCESSOR" : {
      "subDir" : "src",
      "dependencies" : ["org.graalvm.compiler.nodeinfo.processor"],
      "distDependencies" : ["GRAAL_PROCESSOR_COMMON"],
      "maven": False,
    },

    "GRAAL_REPLACEMENTS_PROCESSOR" : {
      "subDir" : "src",
      "dependencies" : ["org.graalvm.compiler.replacements.processor"],
      "distDependencies" : ["GRAAL_PROCESSOR_COMMON"],
      "maven": False,
    },

    "GRAAL_COMPILER_MATCH_PROCESSOR" : {
      "subDir" : "src",
      "dependencies" : ["org.graalvm.compiler.core.match.processor"],
      "distDependencies" : ["GRAAL_PROCESSOR_COMMON"],
      "maven": False,
    },

    "GRAAL" : {
      # This distribution defines a module.
      "moduleName" : "jdk.internal.vm.compiler",
      "subDir" : "src",
      "overlaps" : [
        "GRAAL_GRAPHIO",
        "GRAAL_OPTIONS",
        "GRAAL_NODEINFO",
        "GRAAL_API",
        "GRAAL_COMPILER",
        "GRAAL_RUNTIME",
        "GRAAL_HOTSPOT",
        "GRAAL_LIBGRAAL",
        "GRAAL_SERVICEPROVIDER",
        "GRAAL_TRUFFLE_COMMON",
        "GRAAL_TRUFFLE_COMMON_HOTSPOT",
        "GRAAL_TRUFFLE_COMMON_HOTSPOT_LIBGRAAL",
        "GRAAL_TRUFFLE_RUNTIME",
        "GRAAL_TRUFFLE_COMPILER",
        "GRAAL_TRUFFLE_RUNTIME_HOTSPOT",
        "GRAAL_TRUFFLE_COMPILER_HOTSPOT",
      ],
      "dependencies" : [
        "org.graalvm.libgraal",
        "org.graalvm.compiler.options",
        "org.graalvm.compiler.nodeinfo",
        "org.graalvm.compiler.serviceprovider",
        "org.graalvm.compiler.api.replacements",
        "org.graalvm.compiler.api.runtime",
        "org.graalvm.compiler.graph",
        "org.graalvm.compiler.core",
        "org.graalvm.compiler.replacements",
        "org.graalvm.compiler.runtime",
        "org.graalvm.compiler.code",
        "org.graalvm.compiler.printer",
        "org.graalvm.compiler.core.aarch64",
        "org.graalvm.compiler.replacements.aarch64",
        "org.graalvm.compiler.core.amd64",
        "org.graalvm.compiler.replacements.amd64",
        "org.graalvm.compiler.core.sparc",
        "org.graalvm.compiler.replacements.sparc",
        "org.graalvm.compiler.hotspot.aarch64",
        "org.graalvm.compiler.hotspot.amd64",
        "org.graalvm.compiler.hotspot.sparc",
        "org.graalvm.compiler.hotspot",
        "org.graalvm.compiler.lir.aarch64",
        "org.graalvm.compiler.truffle.compiler.amd64",
        "org.graalvm.compiler.truffle.runtime.serviceprovider",
        "org.graalvm.compiler.truffle.runtime.hotspot",
        "org.graalvm.compiler.truffle.runtime.hotspot.java",
        "org.graalvm.compiler.truffle.runtime.hotspot.libgraal",
        "org.graalvm.compiler.truffle.compiler.hotspot.amd64",
        "org.graalvm.compiler.truffle.compiler.hotspot.sparc",
        "org.graalvm.compiler.truffle.compiler.hotspot.aarch64",
      ],
      "distDependencies" : [
        "sdk:GRAAL_SDK",
        "truffle:TRUFFLE_API",
      ],
      "exclude" : [
        "JVMCI_SERVICES",
        "JVMCI_API",
        "JVMCI_HOTSPOT",
      ],
      "allowsJavadocWarnings": True,
      "description":  "The GraalVM compiler and the Graal-truffle optimizer.",
      "maven" : {
        "artifactId" : "compiler",
      },
    },

    "GRAAL_MANAGEMENT" : {
      # This distribution defines a module.
      "moduleName" : "jdk.internal.vm.compiler.management",
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.compiler.hotspot.management",
      ],
      "distDependencies" : [
        "GRAAL",
      ],
      "exclude" : [
        "JVMCI_SERVICES",
        "JVMCI_API",
        "JVMCI_HOTSPOT",
      ],
      "maven": False,
    },

    "JAOTC" : {
      # This distribution defines a module.
      "moduleName" : "jdk.aot",
      "subDir" : "src",
      "dependencies" : [
        "jdk.tools.jaotc",
      ],
      "distDependencies" : [
        "GRAAL",
        "GRAAL_MANAGEMENT",
      ],
      "exclude" : [
        "JVMCI_SERVICES",
        "JVMCI_API",
        "JVMCI_HOTSPOT",
      ],
      "maven": False,
    },

    "JAOTC_TEST" : {
      "subDir" : "src",
      "dependencies" : [
        "jdk.tools.jaotc.test",
      ],
      "distDependencies" : [
        "JAOTC",
      ],
      "exclude" : [
        "mx:JUNIT",
      ],
      "testDistribution" : True,
      "maven": False,
    },

    "GRAAL_COMPILER_WHITEBOX_MICRO_BENCHMARKS" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.compiler.virtual.bench",
        "org.graalvm.compiler.microbenchmarks",
      ],
      "distDependencies" : [
        "GRAAL_TEST",
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

    "GRAAL_LLVM" : {
      "subDir" : "src",
      "description" : "LLVM compiler backend",
      "dependencies" : ["org.graalvm.compiler.core.llvm"],
      "distDependencies" : [
        "GRAAL",
      ],
      "exclude": [
        "LLVM_WRAPPER",
        "LLVM_PLATFORM_SPECIFIC",
      ],
      "maven" : False,
    }
  },
}
