suite = {
  "mxversion" : "5.267.0",
  "name" : "compiler",
  "sourceinprojectwhitelist" : [],

  "groupId" : "org.graalvm.compiler",
  "version" : "20.3.0",
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
        "urls" : [
          {"url" : "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind" : "binary"},
         ]
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
    "JFR" : {
      "path" : "lib/jfr.jar",
      "sourcePath" : "lib/jfr.jar",
      "jdkStandardizedSince" : "9",
      "optional" : True,
      "module" : "jdk.jfr"
    },
  },

  "libraries" : {

    # ------------- Libraries -------------

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
      "requiresConcealed" : {
        "jdk.internal.vm.ci" : [
          "jdk.vm.ci.meta",
          "jdk.vm.ci.code",
          "jdk.vm.ci.services",
          "jdk.vm.ci.runtime",
        ],
      },
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
      "requiresConcealed" : {
        "jdk.internal.vm.ci" : [
          "jdk.vm.ci.meta",
          "jdk.vm.ci.code",
          "jdk.vm.ci.services",
          "jdk.vm.ci.runtime",
        ],
      },
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
      "javaCompliance" : "8+",
      "workingSets" : "Graal",
    },

    "org.graalvm.compiler.options.jdk11" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.options"
      ],
      "overlayTarget" : "org.graalvm.compiler.options",
      "multiReleaseJarVersion" : "11",
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "11+",
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
      "dependencies" : [
        "JVMCI_API",
        "org.graalvm.compiler.serviceprovider",
        "org.graalvm.graphio",
        "org.graalvm.compiler.options"
      ],
      "annotationProcessors" : ["GRAAL_PROCESSOR"],
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
      "annotationProcessors" : ["GRAAL_PROCESSOR"],
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
      "requiresConcealed" : {
        "jdk.internal.vm.ci" : [
          "jdk.vm.ci.services",
          "jdk.vm.ci.hotspot"
        ],
      },
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
      "requiresConcealed" : {
        "jdk.internal.vm.ci" : [
          "jdk.vm.ci.services",
          "jdk.vm.ci.hotspot"
        ],
      },
      "overlayTarget" : "org.graalvm.libgraal",
      "multiReleaseJarVersion" : "13",
      "checkPackagePrefix" : "false",
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "13+",
      "workingSets" : "API,Graal",
    },

    "org.graalvm.libgraal.jni" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.debug",
        "org.graalvm.libgraal.jni.annotation",
        "sdk:GRAAL_SDK",
      ],
      "annotationProcessors" : [
        "GRAAL_LIBGRAAL_PROCESSOR",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8,11+",
      "workingSets" : "API,Graal",
    },

    "org.graalvm.libgraal.jni.annotation" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8,11+",
      "workingSets" : "Graal",
    },

    "org.graalvm.libgraal.jni.processor" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.processor",
        "org.graalvm.libgraal.jni.annotation",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8,11+",
      "workingSets" : "Graal",
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

      "checkstyle" : "org.graalvm.compiler.graph",
      "annotationProcessors" : [
        "GRAAL_PROCESSOR",
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
      "requiresConcealed" : {
        "jdk.internal.vm.ci" : [
          "jdk.vm.ci.hotspot",
          "jdk.vm.ci.meta",
          "jdk.vm.ci.services",
        ],
      },
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
      "requiresConcealed" : {
        "jdk.internal.vm.ci" : [
          "jdk.vm.ci.hotspot",
          "jdk.vm.ci.services",
          "jdk.vm.ci.meta",
        ],
      },
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
      "requiresConcealed" : {
        "jdk.internal.vm.ci" : [
          "jdk.vm.ci.hotspot",
          "jdk.vm.ci.meta"
        ],
      },
      "overlayTarget" : "org.graalvm.compiler.hotspot",
      "multiReleaseJarVersion" : "13",
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "13+",
      "workingSets" : "Graal,HotSpot",
    },

    "org.graalvm.compiler.hotspot.jdk15" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies": [
        "org.graalvm.compiler.hotspot",
        "JVMCI_HOTSPOT",
        "JFR",
      ],
      "requiresConcealed" : {
        "jdk.internal.vm.ci" : [
          "jdk.vm.ci.meta",
          "jdk.vm.ci.hotspot",
        ],
      },
      "annotationProcessors" : [
        "GRAAL_PROCESSOR",
      ],
      "overlayTarget" : "org.graalvm.compiler.hotspot",
      "multiReleaseJarVersion" : "15",
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "15+",
      "checkPackagePrefix" : "false",
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
        "GRAAL_PROCESSOR",
      ],
      "javaCompliance" : "8+",
      "workingSets" : "Graal,HotSpot",
    },

    "org.graalvm.compiler.management" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.serviceprovider",
      ],
      "requires" : [
        "jdk.management",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "annotationProcessors" : [
        "GRAAL_PROCESSOR",
      ],
      "javaCompliance" : "11+",
      "workingSets" : "Graal,HotSpot",
    },

    "org.graalvm.compiler.hotspot.management.libgraal.annotation" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.libgraal.jni.annotation",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8,11+",
      "workingSets" : "Graal",
    },

    "org.graalvm.compiler.hotspot.management.libgraal.processor" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.hotspot.management.libgraal.annotation",
        "org.graalvm.libgraal.jni.processor",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8,11+",
      "workingSets" : "Graal",
    },

    "org.graalvm.compiler.hotspot.management.libgraal" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.libgraal",
        "org.graalvm.libgraal.jni",
        "org.graalvm.compiler.hotspot.management",
        "org.graalvm.compiler.hotspot.management.libgraal.annotation",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "checkPackagePrefix" : "false",
      "annotationProcessors" : [
        "GRAAL_PROCESSOR",
        "GRAAL_LIBGRAAL_PROCESSOR",
      ],
      "javaCompliance" : "8,11+",
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
        "GRAAL_PROCESSOR"
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
        "GRAAL_PROCESSOR"
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
      "annotationProcessors" : ["GRAAL_PROCESSOR"],
      "javaCompliance" : "8..14",
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
        "GRAAL_PROCESSOR"
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
      "requiresConcealed" : {
        "jdk.internal.vm.ci" : [
          "jdk.vm.ci.meta",
          "jdk.vm.ci.code",
        ],
      },
      "checkstyle": "org.graalvm.compiler.graph",
      "javaCompliance" : "9+",
      "workingSets" : "Graal,HotSpot,Test",
    },

    "org.graalvm.compiler.hotspot.jdk15.test" : {
      "testProject" : True,
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.replacements.test",
      ],
      "requiresConcealed" : {
        "jdk.internal.vm.ci" : [
          "jdk.vm.ci.meta",
        ],
      },
      "checkstyle": "org.graalvm.compiler.graph",
      "javaCompliance" : "15+",
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
      "annotationProcessors" : ["GRAAL_PROCESSOR"],
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
      "annotationProcessors" : ["GRAAL_PROCESSOR"],
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
        "GRAAL_PROCESSOR"
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
      "annotationProcessors" : ["GRAAL_PROCESSOR"],
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
      "annotationProcessors" : ["GRAAL_PROCESSOR"],
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
      "javaCompliance" : "8..14",
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
      "javaCompliance" : "8..14",
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
      "annotationProcessors" : ["GRAAL_PROCESSOR"],
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
      "annotationProcessors" : ["GRAAL_PROCESSOR"],
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
      "annotationProcessors" : ["GRAAL_PROCESSOR"],
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
      "requiresConcealed" : {
        "jdk.internal.vm.ci" : [
          "jdk.vm.ci.code",
          "jdk.vm.ci.aarch64",
        ],
      },
      "annotationProcessors" : ["GRAAL_PROCESSOR"],
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
      "annotationProcessors" : ["GRAAL_PROCESSOR"],
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
      "javaCompliance" : "8..14",
      "workingSets" : "Graal,LIR,SPARC",
    },

    "org.graalvm.compiler.word" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["org.graalvm.compiler.nodes"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "annotationProcessors" : ["GRAAL_PROCESSOR"],
      "workingSets" : "API,Graal",
    },

    "org.graalvm.compiler.replacements" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.api.directives",
        "org.graalvm.compiler.java",
        "org.graalvm.compiler.loop.phases",
        "org.graalvm.compiler.virtual"
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "annotationProcessors" : [
        "GRAAL_PROCESSOR",
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
        "GRAAL_PROCESSOR",
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
        "GRAAL_PROCESSOR",
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
      "javaCompliance" : "8..14",
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
        "GRAAL_PROCESSOR",
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
      "requiresConcealed" : {
        "java.base" : [
          "jdk.internal.misc",
        ],
        "jdk.internal.vm.ci" : [
          "jdk.vm.ci.meta",
          "jdk.vm.ci.code",
          "jdk.vm.ci.amd64",
          "jdk.vm.ci.aarch64",
        ],
      },
    },

    "org.graalvm.compiler.replacements.jdk9_11.test" : {
      "testProject" : True,
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.replacements.test"
      ],
      "requiresConcealed" : {
        "java.base" : [
          "jdk.internal.misc",
        ],
        "jdk.internal.vm.ci" : [
          "jdk.vm.ci.meta",
          "jdk.vm.ci.code",
          "jdk.vm.ci.amd64",
          "jdk.vm.ci.aarch64",
        ],
      },
      "checkstyle": "org.graalvm.compiler.graph",
      "javaCompliance" : "9..11",
    },

    "org.graalvm.compiler.replacements.jdk10.test" : {
      "testProject" : True,
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.replacements.test"
      ],
      "requiresConcealed" : {
        "jdk.internal.vm.ci" : [
          "jdk.vm.ci.meta",
          "jdk.vm.ci.code",
          "jdk.vm.ci.aarch64",
        ],
      },
      "checkstyle": "org.graalvm.compiler.graph",
      "javaCompliance" : "10+",
    },

    "org.graalvm.compiler.replacements.jdk12.test" : {
      "testProject" : True,
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.replacements.test"
      ],
      "requiresConcealed" : {
        "java.base" : [
          "jdk.internal.misc",
        ],
        "jdk.internal.vm.ci" : [
          "jdk.vm.ci.meta",
          "jdk.vm.ci.code",
          "jdk.vm.ci.amd64",
          "jdk.vm.ci.aarch64",
        ],
      },
      "checkstyle": "org.graalvm.compiler.graph",
      "javaCompliance" : "12+",
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
        "GRAAL_PROCESSOR",
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
      "dependencies" : [
        "org.graalvm.compiler.word"
      ],
      "annotationProcessors" : ["GRAAL_PROCESSOR"],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Phases",
    },

    "org.graalvm.compiler.phases.common" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["org.graalvm.compiler.phases"],
      "annotationProcessors" : [
        "GRAAL_PROCESSOR",
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
        "GRAAL_PROCESSOR",
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
      "annotationProcessors" : ["GRAAL_PROCESSOR"],
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
      "annotationProcessors" : ["GRAAL_PROCESSOR"],
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
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "annotationProcessors" : [
        "GRAAL_PROCESSOR",
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
        "GRAAL_PROCESSOR",
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
        "GRAAL_PROCESSOR",
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
        "GRAAL_PROCESSOR",
      ],
      "javaCompliance" : "8..14",
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
      "javaCompliance" : "8..14",
      "workingSets" : "Graal,SPARC,Test",
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
      "annotationProcessors" : ["GRAAL_PROCESSOR"],
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
      "uses" : [
        "org.graalvm.compiler.core.common.CompilerProfiler",
      ],
      "annotationProcessors" : ["GRAAL_PROCESSOR"],
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
      "annotationProcessors" : [
        "GRAAL_PROCESSOR",
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

    "org.graalvm.compiler.api.test.jdk11" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.debug",
      ],
      "requiresConcealed" : {
        "java.base" : [
          "jdk.internal.module",
        ]
      },
      "checkPackagePrefix" : "false",
      "javaCompliance" : "11+",
      "overlayTarget" : "org.graalvm.compiler.api.test",
      "multiReleaseJarVersion" : "11",
      "checkstyle" : "org.graalvm.compiler.graph",
      "workingSets" : "Graal,Test",
      "testProject" : True,
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
      "annotationProcessors" : ["GRAAL_PROCESSOR"],
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
      "requiresConcealed" : {
        "jdk.internal.vm.ci" : [
          "jdk.vm.ci.meta",
        ],
      },
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
      "requiresConcealed" : {
        "jdk.internal.vm.ci" : '*',
      },
      "requires" : [
        "java.management"
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
      "requiresConcealed" : {
        "jdk.internal.vm.ci" : [
          "jdk.vm.ci.hotspot",
          "jdk.vm.ci.services"
        ]
      },
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

    "org.graalvm.compiler.truffle.options" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "sdk:GRAAL_SDK",
        "truffle:TRUFFLE_API"
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "annotationProcessors" : [
        "truffle:TRUFFLE_DSL_PROCESSOR"
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
        "org.graalvm.compiler.truffle.options",
        "org.graalvm.compiler.core",
        "org.graalvm.compiler.replacements",
      ],
      "uses" : [
        "org.graalvm.compiler.truffle.compiler.substitutions.TruffleInvocationPluginProvider",
        "org.graalvm.compiler.truffle.compiler.phases.inlining.InliningPolicyProvider"
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "annotationProcessors" : [
        "GRAAL_PROCESSOR",
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
      "checkstyle" : "org.graalvm.compiler.graph",
      "annotationProcessors" : [
        "GRAAL_PROCESSOR",
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

    "org.graalvm.compiler.truffle.runtime.serviceprovider.jdk11" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.truffle.runtime.serviceprovider",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "11+",
      "checkPackagePrefix" : "false",
      "overlayTarget" : "org.graalvm.compiler.truffle.runtime.serviceprovider",
      "multiReleaseJarVersion" : "9",
      "workingSets" : "Graal,Truffle",
    },

    "org.graalvm.compiler.truffle.jfr" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "truffle:TRUFFLE_API",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "annotationProcessors" : [
      ],
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Truffle",
      "jacoco" : "exclude",
    },

    "org.graalvm.compiler.truffle.jfr.impl" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.truffle.jfr",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "annotationProcessors" : [
      ],
      "javaCompliance" : "8+",
      "workingSets" : "Graal,Truffle",
      "jacoco" : "exclude",
    },

    "org.graalvm.compiler.truffle.jfr.impl.jdk8" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.truffle.jfr",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "annotationProcessors" : [
      ],
      "overlayTarget" : "org.graalvm.compiler.truffle.jfr.impl",
      "checkPackagePrefix" : "false",
      "javaCompliance" : "8",
      "workingSets" : "Graal,Truffle",
      "jacoco" : "exclude",
    },

    "org.graalvm.compiler.truffle.jfr.impl.jdk11" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.truffle.jfr",
      ],
      "requires" : [
        "jdk.jfr"
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "annotationProcessors" : [
      ],
      "overlayTarget" : "org.graalvm.compiler.truffle.jfr.impl",
      "checkPackagePrefix" : "false",
      "javaCompliance" : "11+",
      "multiReleaseJarVersion" : "11",
      "workingSets" : "Graal,Truffle",
      "jacoco" : "exclude",
    },

    "org.graalvm.compiler.truffle.runtime" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.truffle.common",
        "org.graalvm.compiler.truffle.options",
        "org.graalvm.compiler.truffle.runtime.serviceprovider",
        "org.graalvm.compiler.truffle.jfr",
        "truffle:TRUFFLE_API",
      ],
      "requires" : [
        "java.logging",
      ],
      "uses" : [
        "org.graalvm.compiler.truffle.jfr.EventFactory.Provider"
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "annotationProcessors" : [
        "GRAAL_PROCESSOR",
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
        "truffle:TRUFFLE_TEST",
      ],
      "annotationProcessors" : [
        "GRAAL_PROCESSOR",
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
        "GRAAL_PROCESSOR",
      ],
      "workingSets" : "Graal,Truffle",
    },

    "org.graalvm.compiler.truffle.common.hotspot.libgraal" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.libgraal.jni.annotation"
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8,11,13+",
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
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8+",
      "annotationProcessors" : [
        "GRAAL_PROCESSOR",
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
          "GRAAL_PROCESSOR",
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
        "org.graalvm.util",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8,11+",
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
        "org.graalvm.libgraal.jni",
        "org.graalvm.compiler.truffle.compiler.hotspot",
        "org.graalvm.compiler.truffle.common.hotspot.libgraal",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8,11+",
      "annotationProcessors" : [
        "GRAAL_LIBGRAAL_PROCESSOR",
      ],
      "workingSets" : "Graal,Truffle",
    },

    "org.graalvm.compiler.truffle.compiler.hotspot.libgraal.processor" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.libgraal.jni.processor",
        "org.graalvm.compiler.truffle.common.hotspot.libgraal",
      ],
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "8,11+",
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

    "org.graalvm.compiler.truffle.runtime.hotspot.jdk11" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.compiler.truffle.runtime.hotspot",
        "JVMCI_HOTSPOT",
      ],
      "requiresConcealed" : {
        "jdk.internal.vm.ci" : [
          "jdk.vm.ci.meta",
          "jdk.vm.ci.hotspot",
        ],
      },
      "checkPackagePrefix" : "false",
      "checkstyle" : "org.graalvm.compiler.graph",
      "javaCompliance" : "11+",
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
        "GRAAL_PROCESSOR",
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
      "javaCompliance" : "8..14",
      "annotationProcessors" : ["GRAAL_PROCESSOR"],
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
      "annotationProcessors" : ["GRAAL_PROCESSOR"],
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

    "GRAAL_GRAPHIO" : {
      "subDir" : "src",
      "dependencies" : ["org.graalvm.graphio"],
      "distDependencies" : [],
      "maven": False,
    },

    "GRAAL_ONLY_TEST" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.compiler.api.test"
      ],
      "distDependencies" : [
        "JVMCI_HOTSPOT",
        "GRAAL"
      ],
      "exclude" : [
        "mx:JUNIT",
        "JAVA_ALLOCATION_INSTRUMENTER",
      ],
      "maven": False,
    },

    "GRAAL_TEST" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.compiler.api.directives.test",
        "org.graalvm.compiler.asm.sparc.test",
        "org.graalvm.compiler.asm.aarch64.test",
        "org.graalvm.compiler.asm.amd64.test",
        "org.graalvm.compiler.test",
        "org.graalvm.compiler.core.aarch64.test",
        "org.graalvm.compiler.core.amd64.test",
        "org.graalvm.compiler.debug.test",
        "org.graalvm.compiler.hotspot.aarch64.test",
        "org.graalvm.compiler.hotspot.amd64.test",
        "org.graalvm.compiler.hotspot.lir.test",
        "org.graalvm.compiler.hotspot.sparc.test",
        "org.graalvm.compiler.hotspot.jdk15.test",
        "org.graalvm.compiler.hotspot.jdk9.test",
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
        "org.graalvm.compiler.replacements.jdk10.test",
        "org.graalvm.compiler.replacements.jdk12.test",
        "org.graalvm.compiler.core.jdk9.test",
        "org.graalvm.compiler.hotspot.jdk9.test",
      ],
      "distDependencies" : [
        "GRAAL_ONLY_TEST",
        "truffle:TRUFFLE_SL_TEST",
        "truffle:TRUFFLE_TEST",
      ],
      "exclude" : [
        "mx:JUNIT",
        "JAVA_ALLOCATION_INSTRUMENTER",
      ],
      "maven": False,
    },

    "GRAAL_TRUFFLE_JFR_IMPL" : {
      # This distribution defines a module.
      "moduleInfo" : {
        "name" : "jdk.internal.vm.compiler.truffle.jfr",
      },
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.compiler.truffle.jfr.impl",
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

    "GRAAL_TRUFFLE_COMPILER_LIBGRAAL": {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.compiler.truffle.compiler.hotspot.amd64",
        "org.graalvm.compiler.truffle.compiler.hotspot.sparc",
        "org.graalvm.compiler.truffle.compiler.hotspot.aarch64",
        "org.graalvm.compiler.truffle.compiler.hotspot.libgraal",
      ],

      "distDependencies" : [
        "GRAAL",
      ],
      "maven": False,
      "javaCompliance" : "8+",
    },

    "GRAAL_PROCESSOR" : {
      "subDir": "src",
      "dependencies" : [
        "org.graalvm.compiler.processor",
        "org.graalvm.compiler.options.processor",
        "org.graalvm.compiler.truffle.common.processor",
        "org.graalvm.compiler.serviceprovider.processor",
        "org.graalvm.compiler.nodeinfo.processor",
        "org.graalvm.compiler.replacements.processor",
        "org.graalvm.compiler.core.match.processor"
       ],
      "maven": False,
    },

    "GRAAL_LIBGRAAL_PROCESSOR" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.libgraal.jni.processor",
        "org.graalvm.compiler.truffle.compiler.hotspot.libgraal.processor",
        "org.graalvm.compiler.hotspot.management.libgraal.processor"
      ],
      "distDependencies" : ["GRAAL_PROCESSOR"],
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
          "* to com.oracle.graal.graal_enterprise",
          "org.graalvm.compiler.api.directives         to jdk.aot",
          "org.graalvm.compiler.api.runtime            to jdk.aot",
          "org.graalvm.compiler.api.replacements       to jdk.aot",
          "org.graalvm.compiler.asm.amd64              to jdk.aot",
          "org.graalvm.compiler.asm.aarch64            to jdk.aot",
          "org.graalvm.compiler.bytecode               to jdk.aot",
          "org.graalvm.compiler.code                   to jdk.aot",
          "org.graalvm.compiler.core                   to jdk.aot",
          "org.graalvm.compiler.core.common            to jdk.aot,jdk.internal.vm.compiler.management",
          "org.graalvm.compiler.core.target            to jdk.aot",
          "org.graalvm.compiler.debug                  to jdk.aot,jdk.internal.vm.compiler.management",
          "org.graalvm.compiler.graph                  to jdk.aot",
          "org.graalvm.compiler.hotspot                to jdk.aot,jdk.internal.vm.compiler.management",
          "org.graalvm.compiler.hotspot.meta           to jdk.aot",
          "org.graalvm.compiler.hotspot.replacements   to jdk.aot",
          "org.graalvm.compiler.hotspot.stubs          to jdk.aot",
          "org.graalvm.compiler.hotspot.word           to jdk.aot",
          "org.graalvm.compiler.java                   to jdk.aot",
          "org.graalvm.compiler.lir.asm                to jdk.aot",
          "org.graalvm.compiler.lir.phases             to jdk.aot",
          "org.graalvm.compiler.nodes                  to jdk.aot",
          "org.graalvm.compiler.nodes.graphbuilderconf to jdk.aot",
          "org.graalvm.compiler.options                to jdk.aot,jdk.internal.vm.compiler.management",
          "org.graalvm.compiler.phases                 to jdk.aot",
          "org.graalvm.compiler.phases.common.jmx      to jdk.internal.vm.compiler.management",
          "org.graalvm.compiler.phases.tiers           to jdk.aot",
          "org.graalvm.compiler.printer                to jdk.aot",
          "org.graalvm.compiler.runtime                to jdk.aot",
          "org.graalvm.compiler.replacements           to jdk.aot",
          "org.graalvm.compiler.serviceprovider        to jdk.aot,jdk.internal.vm.compiler.management",
          "org.graalvm.compiler.truffle.jfr            to jdk.internal.vm.compiler.truffle.jfr",
          "org.graalvm.libgraal                        to jdk.internal.vm.compiler.management",
          "org.graalvm.util                            to jdk.internal.vm.compiler.management",
        ],
        "uses" : [
          "com.oracle.truffle.api.impl.TruffleLocator",
          "com.oracle.truffle.api.object.LayoutFactory",
          "org.graalvm.compiler.code.DisassemblerProvider",
          "org.graalvm.compiler.core.match.MatchStatementSet",
          "org.graalvm.compiler.debug.DebugHandlersFactory",
          "org.graalvm.compiler.debug.TTYStreamProvider",
          "org.graalvm.compiler.hotspot.HotSpotGraalManagementRegistration",
          "org.graalvm.compiler.hotspot.HotSpotCodeCacheListener",
          "org.graalvm.compiler.hotspot.HotSpotBackendFactory",
          "org.graalvm.compiler.nodes.graphbuilderconf.GeneratedPluginFactory",
          "org.graalvm.compiler.options.OptionDescriptors",
          "org.graalvm.compiler.phases.common.jmx.HotSpotMBeanOperationProvider",
          "org.graalvm.compiler.serviceprovider.JMXService",
          "org.graalvm.compiler.truffle.compiler.hotspot.TruffleCallBoundaryInstrumentationFactory",
          "org.graalvm.compiler.truffle.compiler.substitutions.TruffleInvocationPluginProvider",
          "org.graalvm.compiler.truffle.runtime.LoopNodeFactory",
          "org.graalvm.compiler.truffle.runtime.TruffleTypes",
          "org.graalvm.home.HomeFinder",
        ],
        "requiresConcealed" : {
          "jdk.internal.vm.ci" : "*"
        }
      },
      "subDir" : "src",
      "overlaps" : [
        "GRAAL_GRAPHIO",
        "GRAAL_LIBGRAAL_PROCESSOR"],
      "dependencies" : [
        "org.graalvm.libgraal",
        "org.graalvm.libgraal.jni",
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
        "org.graalvm.compiler.truffle.jfr",
      ],
      "distDependencies" : [
        "sdk:GRAAL_SDK",
        "truffle:TRUFFLE_API"
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
      "moduleInfo" : {
        "name" : "jdk.internal.vm.compiler.management",
      },
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.compiler.management",
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
      "allowsJavadocWarnings": True,
      "description":  "The GraalVM compiler Management Bean.",
      "maven" : {
        "artifactId" : "compiler-management",
      },
    },

    "GRAAL_MANAGEMENT_LIBGRAAL": {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.compiler.hotspot.management.libgraal",
      ],
      "overlaps" : [
        "GRAAL_LIBGRAAL_PROCESSOR"
      ],
      "distDependencies" : [
        "GRAAL_MANAGEMENT",
        "GRAAL",
      ],
      "maven": False,
      "javaCompliance" : "8+",
    },

    "JAOTC" : {
      # This distribution defines a module.
      "moduleInfo" : {
        "name":"jdk.aot",
      },
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
        "GRAAL_ONLY_TEST"
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
  },
}
