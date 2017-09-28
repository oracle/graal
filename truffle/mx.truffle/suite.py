suite = {
  "mxversion" : "5.123.0",
  "name" : "truffle",
  "sourceinprojectwhitelist" : [],
  "url" : "http://openjdk.java.net/projects/graal",
  "developer" : {
    "name" : "Truffle and Graal developers",
    "email" : "graal-dev@openjdk.java.net",
    "organization" : "Graal",
    "organizationUrl" : "http://openjdk.java.net/projects/graal",
  },
  "scm" : {
    "url" : "https://github.com/graalvm/truffle",
    "read" : "https://github.com/graalvm/truffle.git",
    "write" : "git@github.com:graalvm/truffle.git",
  },
  "repositories" : {
    "lafo-snapshots" : {
      "url" : "https://curio.ssw.jku.at/nexus/content/repositories/snapshots",
      "licenses" : ["GPLv2-CPE", "UPL", "BSD-new"]
    },
  },
  "defaultLicense" : "GPLv2-CPE",
  "imports" : {
    "suites": [
      {
        "name" : "sdk",
        "subdir": True,
        "urls" : [
          {"url" : "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind" : "binary"},
         ]
      },
    ]
  },
  "libraries" : {

    # ------------- Libraries -------------

    "JLINE" : {
      "sha1" : "fdedd5f2522122102f0b3db85fe7aa563a009926",
      "maven" : {
        "groupId" : "jline",
        "artifactId" : "jline",
        "version" : "2.14.5",
      }
    },

    "LIBFFI" : {
      "urls" : [
        "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/libffi-3.2.1.tar.gz",
        "ftp://sourceware.org/pub/libffi/libffi-3.2.1.tar.gz",
      ],
      "sha1" : "280c265b789e041c02e5c97815793dfc283fb1e6",
    },
  },
  "snippetsPattern" : ".*(Snippets|doc-files).*",
  "projects" : {

    # ------------- Truffle -------------

    "com.oracle.truffle.api.source" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
      ],
      "uses" : [
        "com.oracle.truffle.api.TruffleRuntimeAccess",
        "java.nio.file.spi.FileTypeDetector"
      ],
      "exports" : [
        "<package-info>", # exports all packages containing package-info.java
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.8",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.source",
        "sdk:GRAAL_SDK",
      ],
      "uses" : [
        "com.oracle.truffle.api.TruffleRuntimeAccess",
      ],
      "exports" : [
        "<package-info>", # exports all packages containing package-info.java
        "com.oracle.truffle.api.impl", # exported to graal-core
      ],
      "javaCompliance" : "1.8",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.utilities" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api"
      ],
      "exports" : [
        "<package-info>", # exports all packages containing package-info.java
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.8",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.vm" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "uses" : ["com.oracle.truffle.api.impl.TruffleLocator", "org.graalvm.polyglot.impl.AbstractPolyglotImpl",],
      "dependencies" : [
        "sdk:GRAAL_SDK",
        "com.oracle.truffle.api.interop.java",
        "com.oracle.truffle.api.instrumentation",
      ],
      "exports" : [
        "<package-info>", # exports all packages containing package-info.java
      ],
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR_INTEROP_INTERNAL"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.8",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.profiles",
        "com.oracle.truffle.api.interop",
        "com.oracle.truffle.api.debug",
        "com.oracle.truffle.api.utilities",
        "com.oracle.truffle.api.vm",
        "mx:JUNIT",
      ],
      "imports" : ["jdk.internal.loader"],
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "API,Truffle,Test",
      "jacoco" : "exclude",
    },

    "com.oracle.truffle.api.benchmark" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "TRUFFLE_API",
        "mx:JMH_1_18",
      ],
      "imports" : ["jdk.internal.loader"],
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "1.8",
      "findbugsIgnoresGenerated" : True,
      "isTestProject" : True,
      "annotationProcessors" : ["mx:JMH_1_18", "TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "API,Truffle,Test",
      "jacoco" : "exclude",
    },

    "com.oracle.truffle.api.dsl" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.api"],
      "exports" : [
        "<package-info>", # exports all packages containing package-info.java
        "com.oracle.truffle.api.dsl.internal", # Exported for com.oracle.truffle.sl.nodes.SLTypes
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.8",
      "workingSets" : "API,Truffle,Codegen",
    },

    "com.oracle.truffle.api.dsl.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.dsl.processor",
        "com.oracle.truffle.api.vm",
        "com.oracle.truffle.api.test",
        "mx:JUNIT",
      ],
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "API,Truffle,Codegen,Test",
      "jacoco" : "exclude",
    },

    "com.oracle.truffle.dsl.processor" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.dsl",
        "com.oracle.truffle.api.instrumentation",
      ],
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "1.8",
      "imports" : [
        "com.sun.tools.javac.processing",
        "com.sun.tools.javac.model",
        "com.sun.tools.javac.util",
        "com.sun.tools.javac.tree",
        "com.sun.tools.javac.file",
      ],
      "workingSets" : "Truffle,Codegen",
    },

    "com.oracle.truffle.dsl.processor.interop" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.interop",
        "com.oracle.truffle.dsl.processor"
      ],
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "1.8",
      "imports" : [
        "com.sun.tools.javac.processing",
        "com.sun.tools.javac.model",
        "com.sun.tools.javac.util",
        "com.sun.tools.javac.tree",
        "com.sun.tools.javac.file",
      ],
      "workingSets" : "Truffle,Codegen",
    },

    "com.oracle.truffle.api.interop" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.dsl",
        "com.oracle.truffle.api.profiles",
      ],
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR_INTERNAL"],
      "exports" : [
        "<package-info>", # exports all packages containing package-info.java
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.8",
      "workingSets" : "API,Truffle",
    },

   "com.oracle.truffle.api.instrumentation" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.api"],
      "exports" : [
        "<package-info>", # exports all packages containing package-info.java
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.8",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.instrumentation.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.tools",
        "com.oracle.truffle.api.debug",
        "com.oracle.truffle.api.dsl.test",
        "mx:JUNIT"
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "javaCompliance" : "1.8",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.debug" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.api.vm"],
      "runtimeDeps" : [
        "java.desktop"
      ],
      "exports" : [
        "<package-info>", # exports all packages containing package-info.java
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR_INTERNAL"],
      "javaCompliance" : "1.8",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.debug.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.debug",
        "com.oracle.truffle.api.instrumentation.test",
        "com.oracle.truffle.api.dsl.test",
        "com.oracle.truffle.tck",
        "mx:JUNIT"
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR_INTEROP_INTERNAL"],
      "javaCompliance" : "1.8",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.interop.java" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.interop",
      ],
      "exports" : [
        "<package-info>", # exports all packages containing package-info.java
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR_INTEROP_INTERNAL"],
      "javaCompliance" : "1.8",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.interop.java.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.interop.java",
        "com.oracle.truffle.api.vm",
        "com.oracle.truffle.object.basic",
        "com.oracle.truffle.api.test",
        "mx:JUNIT"
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "javaCompliance" : "1.8",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.object" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.interop",
        "com.oracle.truffle.api.utilities"
      ],
      "exports" : [
        "<package-info>", # exports all packages containing package-info.java
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.8",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.object.dsl" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.api.object"],
      "exports" : [
        "<package-info>", # exports all packages containing package-info.java
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.8",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.object.dsl.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.object",
        "com.oracle.truffle.object.basic",
        "com.oracle.truffle.object.dsl.processor",
        "mx:JUNIT",
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "API,Truffle,Codegen,Test",
      "jacoco" : "exclude",
    },

    "com.oracle.truffle.object.dsl.processor" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.object.dsl",
        "com.oracle.truffle.dsl.processor"
      ],
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle,Codegen",
    },

    "com.oracle.truffle.api.profiles" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.api"],
      "exports" : [
        "<package-info>", # exports all packages containing package-info.java
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.8",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.metadata" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.interop",
        "com.oracle.truffle.api.instrumentation"
      ],
      "exports" : [
        "<package-info>", # exports all packages containing package-info.java
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR_INTEROP_INTERNAL"],
      "javaCompliance" : "1.8",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.metadata.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.metadata",
        "com.oracle.truffle.api.interop.java",
        "com.oracle.truffle.api.instrumentation.test",
        "mx:JUNIT"
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR_INTEROP_INTERNAL"],
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle,Tools",
    },

    "com.oracle.truffle.object" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.api.object"],
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle",
    },

    "com.oracle.truffle.object.basic" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.object"],
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle",
    },

    "com.oracle.truffle.object.basic.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.object.basic",
        "mx:JUNIT"
      ],
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle",
    },

    "com.oracle.truffle.tck" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "TRUFFLE_API",
        "mx:JUNIT",
      ],
      "generatedDependencies" : [
        "com.oracle.truffle.tutorial",
      ],
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle,Tools",
    },

    "com.oracle.truffle.tools" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
                        "com.oracle.truffle.api.vm"],
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle,Tools",
    },

    "com.oracle.truffle.tools.profiler" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["TRUFFLE_API"],
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle,Tools",
    },

    "com.oracle.truffle.tools.profiler.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
          "com.oracle.truffle.tools.profiler",
          "com.oracle.truffle.api.instrumentation.test",
          "mx:JUNIT"
          ],
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle,Tools",
    },

    "com.oracle.truffle.tools.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
          "com.oracle.truffle.tools",
          "com.oracle.truffle.api.instrumentation.test",
          "mx:JUNIT"
          ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle,Tools",
    },

    "com.oracle.truffle.tools.debug.shell" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.vm",
        "JLINE"
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle,Tools",
    },

    "com.oracle.truffle.nfi" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.interop.java",
        "com.oracle.truffle.nfi.types",
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR_INTEROP_INTERNAL"],
      "workingSets" : "Truffle",
    },

    "com.oracle.truffle.nfi.types" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle",
    },

    "com.oracle.truffle.nfi.native" : {
      "subDir" : "src",
      "native" : True,
      "vpath" : True,
      "results" : [
        "bin/<lib:trufflenfi>",
      ],
      "headers" : [
        "include/trufflenfi.h"
      ],
      "buildDependencies" : [
        "com.oracle.truffle.nfi",
      ],
      "buildEnv" : {
        "LIBFFI_SRC" : "<path:LIBFFI>",
        "LIBTRUFFLENFI" : "<lib:trufflenfi>",
        "OS" : "<os>",
      },
    },

    "com.oracle.truffle.nfi.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JUNIT",
        "TRUFFLE_NFI",
        "TRUFFLE_TCK",
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "javaProperties" : {
        "native.test.lib" : "<path:TRUFFLE_TEST_NATIVE>/<lib:nativetest>"
      },
    },

    "com.oracle.truffle.nfi.test.native" : {
      "subDir" : "src",
      "native" : True,
      "vpath" : True,
      "results" : [
        "bin/<lib:nativetest>",
      ],
      "buildDependencies" : [
        "TRUFFLE_NFI_NATIVE",
      ],
      "buildEnv" : {
        "TARGET" : "bin/<lib:nativetest>",
        "CPPFLAGS" : "-I<path:TRUFFLE_NFI_NATIVE>/include",
        "OS" : "<os>",
      },
    },

    "com.oracle.truffle.sl" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "TRUFFLE_API",
      ],
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle,SimpleLanguage",
      "license" : "UPL",
    },

    "com.oracle.truffle.sl.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.tck",
        "com.oracle.truffle.sl",
        "mx:JMH_1_18",
      ],
      "checkstyle" : "com.oracle.truffle.sl",
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle,SimpleLanguage,Test",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR", "mx:JMH_1_18"],
      "license" : "UPL",
    },

    "com.oracle.truffle.tutorial" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "TRUFFLE_API",
      ],
      "checkstyle" : "com.oracle.truffle.sl",
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle,SimpleLanguage,Test",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "license" : "UPL",
    },
   },

  "licenses" : {
    "UPL" : {
      "name" : "Universal Permissive License, Version 1.0",
      "url" : "http://opensource.org/licenses/UPL",
    }
  },

  "distributions" : {

    # ------------- Distributions -------------

    "TRUFFLE_API" : {
      # This distribution defines a module.
      "moduleName" : "com.oracle.truffle.truffle_api",
      "subDir" : "src",
      "javaCompliance" : "1.8",
      "dependencies" : [
        "com.oracle.truffle.api.interop.java",
        "com.oracle.truffle.api.dsl",
        "com.oracle.truffle.api.profiles",
        "com.oracle.truffle.api.metadata",
        "com.oracle.truffle.api.debug",
        "com.oracle.truffle.api.utilities",
        "com.oracle.truffle.api.vm",
        "com.oracle.truffle.object.basic",
        "com.oracle.truffle.api.object.dsl",
      ],
      "distDependencies" : [
        "sdk:GRAAL_SDK"
      ],
      "description" : """Truffle is a multi-language framework for executing dynamic languages
        that achieves high performance when combined with Graal.""",
      "javadocType": "api",
    },

    "TRUFFLE_NFI" : {
      "subDir" : "src",
      "javaCompliance" : "1.8",
      "dependencies" : [
        "com.oracle.truffle.nfi",
      ],
      "distDependencies" : [
        "TRUFFLE_API",
        "TRUFFLE_NFI_NATIVE",
      ],
      "javaProperties" : {
          "truffle.nfi.library" : "<path:TRUFFLE_NFI_NATIVE>/bin/<lib:trufflenfi>"
      },
      "description" : """Native function interface for the Truffle framework.""",
      "allowsJavadocWarnings": True,
      "maven" : False,
    },

    "TRUFFLE_NFI_NATIVE" : {
      "native" : True,
      "relpath" : True,
      "platformDependent" : True,
      "output" : "mxbuild/truffle-nfi-native",
      "dependencies" : [
        "com.oracle.truffle.nfi.native",
      ],
      "maven" : False,
    },

    "TRUFFLE_TCK" : {
      "subDir" : "src",
      "javaCompliance" : "1.8",
      "dependencies" : [
        "com.oracle.truffle.tck"
      ],
      "distDependencies" : ["TRUFFLE_API"],
      "exclude" : ["mx:JUNIT"],
      "description" : """A collection of tests that can certify language implementation to be compliant
        with most recent requirements of the Truffle infrastructure and tooling.""",
      "allowsJavadocWarnings": True,
    },

    "TRUFFLE_DSL_PROCESSOR_INTERNAL" : {
      "internal" : True,
      "subDir" : "src",
      "javaCompliance" : "1.8",
      "dependencies" : ["com.oracle.truffle.dsl.processor"],
      "distDependencies" : ["sdk:GRAAL_SDK"],
      "maven" : False,
    },

    "TRUFFLE_DSL_PROCESSOR_INTEROP_INTERNAL" : {
      "internal" : True,
      "subDir" : "src",
      "javaCompliance" : "1.8",
      "dependencies" : ["com.oracle.truffle.dsl.processor", "com.oracle.truffle.dsl.processor.interop"],
      "distDependencies" : ["sdk:GRAAL_SDK"],
      "maven" : False,
    },

    "TRUFFLE_DSL_PROCESSOR" : {
      "subDir" : "src",
      "javaCompliance" : "1.8",
      "dependencies" : ["com.oracle.truffle.dsl.processor", "com.oracle.truffle.dsl.processor.interop", "com.oracle.truffle.object.dsl.processor"],
      "distDependencies" : ["TRUFFLE_API"],
      "description" : "The Truffle DSL Processor generates source code for nodes that are declared using the DSL.",
      "allowsJavadocWarnings": True,
    },

    "TRUFFLE_SL" : {
      "subDir" : "src",
      "javaCompliance" : "1.8",
      "dependencies" : [
        "com.oracle.truffle.sl",
      ],
      "exclude" : [
        "mx:JUNIT",
      ],
      "distDependencies" : [
          "TRUFFLE_API",
          "TRUFFLE_TCK",
          "TRUFFLE_DSL_PROCESSOR"
      ],
      "license" : "UPL",
      "description" : "Truffle SL is an example language implemented using the Truffle API.",
      "allowsJavadocWarnings": True,
    },

    "TRUFFLE_SL_TEST" : {
      "subDir" : "src",
      "javaCompliance" : "1.8",
      "dependencies" : [
        "com.oracle.truffle.sl.test"
      ],
      "exclude" : [
        "mx:JUNIT",
        "mx:JMH_1_18"
      ],
      "distDependencies" : [
          "TRUFFLE_API",
          "TRUFFLE_TCK",
          "TRUFFLE_DSL_PROCESSOR",
          "TRUFFLE_SL"
      ],
      "license" : "UPL",
      "maven" : False
    },

     "TRUFFLE_DEBUG" : {
      "subDir" : "src",
      "javaCompliance" : "1.8",
      "dependencies" : [
        "com.oracle.truffle.tools.debug.shell",
        "com.oracle.truffle.tools"
      ],
      "exclude" : ["JLINE"],
      "distDependencies" : [
          "TRUFFLE_API",
      ],
      "description" : "Experimental REPL server to build your debugger console for your language.",
      "allowsJavadocWarnings": True,
     },

    "TRUFFLE_INSTRUMENT_TEST" : {
      "subDir" : "src",
      "javaCompliance" : "1.8",
      "dependencies" : [
        "com.oracle.truffle.api.instrumentation.test",
      ],
      "exclude" : ["mx:HAMCREST", "mx:JUNIT", "mx:JMH_1_18"],
      "distDependencies" : [
        "TRUFFLE_API",
        "TRUFFLE_DSL_PROCESSOR",
        "TRUFFLE_DEBUG",
      ],
      "description" : "Instrumentation tests including InstrumentationTestLanguage.",
      "allowsJavadocWarnings": True,
    },

     "TRUFFLE_TEST" : {
       "subDir" : "src",
       "javaCompliance" : "1.8",
       "dependencies" : [
         "com.oracle.truffle.api.test",
         "com.oracle.truffle.api.benchmark",
         "com.oracle.truffle.api.dsl.test",
         "com.oracle.truffle.api.instrumentation.test",
         "com.oracle.truffle.api.metadata.test",
         "com.oracle.truffle.api.debug.test",
         "com.oracle.truffle.api.interop.java.test",
         "com.oracle.truffle.api.object.dsl.test",
         "com.oracle.truffle.object.basic.test",
         "com.oracle.truffle.tools.test",
         "com.oracle.truffle.nfi.test",
       ],
       "exclude" : ["mx:HAMCREST", "mx:JUNIT", "mx:JMH_1_18"],
       "distDependencies" : [
         "TRUFFLE_API",
         "TRUFFLE_NFI",
         "TRUFFLE_DSL_PROCESSOR",
         "TRUFFLE_DEBUG",
         "TRUFFLE_INSTRUMENT_TEST",
         "TRUFFLE_TEST_NATIVE",
         "TRUFFLE_TCK",
      ],
      "maven" : False,
     },

     "TRUFFLE_TEST_NATIVE" : {
       "native" : True,
       "platformDependent" : True,
       "output" : "mxbuild/truffle-test-native",
       "dependencies" : [
         "com.oracle.truffle.nfi.test.native",
       ],
      "maven" : False,
     },

     "TRUFFLE_PROFILER": {
       "dependencies": [
         "com.oracle.truffle.tools.profiler",
       ],
       "distDependencies" : [
         "TRUFFLE_API",
       ],
       "description" : "The truffle profiler, supporting CPU sampling and tracing. Memory tracing support is experimental"
     },

     "TRUFFLE_PROFILER_TEST": {
       "dependencies": [
         "com.oracle.truffle.tools.profiler.test",
       ],
       "distDependencies" : [
         "TRUFFLE_INSTRUMENT_TEST",
         "TRUFFLE_PROFILER",
       ],
       "description" : "Tests for the truffle profiler.",
       "maven" : False,
     },
  },
}
