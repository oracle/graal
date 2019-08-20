#
# Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
#
suite = {
  "mxversion" : "5.223.0",
  "name" : "truffle",
  "version" : "19.3.0",
  "release" : False,
  "groupId" : "org.graalvm.truffle",
  "sourceinprojectwhitelist" : [],
  "url" : "http://openjdk.java.net/projects/graal",
  "developer" : {
    "name" : "Truffle and Graal developers",
    "email" : "graal-dev@openjdk.java.net",
    "organization" : "Graal",
    "organizationUrl" : "https://github.com/oracle/graal",
  },
  "scm" : {
    "url" : "https://github.com/oracle/graal/tree/master/truffle",
    "read" : "https://github.com/oracle/graal.git",
    "write" : "git@github.com:oracle/graal.git",
  },
  "defaultLicense" : "UPL",
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
      "sha1" : "c3aeac59c022bdc497c8c48ed86fa50450e4896a",
      "maven" : {
        "groupId" : "jline",
        "artifactId" : "jline",
        "version" : "2.14.6",
      }
    },

    "LIBFFI_SOURCES" : {
      "resource" : True,
      "version" : "3.2.1",
      "urls" : [
        "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/libffi-{version}.tar.gz",
        "https://sourceware.org/pub/libffi/libffi-{version}.tar.gz",
      ],
      "sha1" : "280c265b789e041c02e5c97815793dfc283fb1e6",
    },

    "ANTLR4": {
      "sha1" : "946f8aa9daa917dd81a8b818111bec7e288f821a",
      "maven" : {
        "groupId" : "org.antlr",
        "artifactId" : "antlr4-runtime",
        "version" : "4.7.1",
      }
    },

    "ANTLR4_COMPLETE": {
      "urls": [
        "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/antlr-4.7.1-complete.jar",
        "https://www.antlr.org/download/antlr-4.7.1-complete.jar"
      ],
      "sha1": "90aa8308da72ae610207d8f6ca27736921be692a",
    },
  },
  "snippetsPattern" : ".*(Snippets|doc-files).*",
  "projects" : {

    # ------------- Truffle -------------

    "com.oracle.truffle.api" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "sdk:GRAAL_SDK",
      ],
      "uses" : [
        "com.oracle.truffle.api.TruffleRuntimeAccess",
        "java.nio.file.spi.FileTypeDetector",
      ],
      "exports" : [
        "<package-info>", # exports all packages containing package-info.java
        "com.oracle.truffle.api.impl", # exported to the GraalVM compiler
      ],
      # We need to force javac as JDT has a bug that JDT ignores SuppressWarnings
      # if warnings as errors is enabled. See GR-14683.
      "forceJavac" : "true",
      "javaCompliance" : "8+",
      "checkstyleVersion" : "8.8",
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
      "javaCompliance" : "8+",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.polyglot" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "uses" : ["com.oracle.truffle.api.impl.TruffleLocator",],
      "dependencies" : [
        "sdk:GRAAL_SDK",
        "com.oracle.truffle.api.interop",
        "com.oracle.truffle.api.instrumentation",
        "com.oracle.truffle.api.utilities",
      ],
      "exports" : [
        "<package-info>", # exports all packages containing package-info.java
      ],
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR_INTEROP_INTERNAL"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "8+",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "TRUFFLE_API",
        "TRUFFLE_SL",
        "mx:JUNIT",
      ],
      "imports" : ["jdk.internal.loader"],
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "8+",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "API,Truffle,Test",
      "jacoco" : "exclude",
    },

    "com.oracle.truffle.api.benchmark" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "TRUFFLE_API",
        "TRUFFLE_INSTRUMENT_TEST",
        "mx:JMH_1_21",
      ],
      "imports" : ["jdk.internal.loader"],
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "8+",
      "spotbugsIgnoresGenerated" : True,
      "testProject" : True,
      "annotationProcessors" : ["mx:JMH_1_21", "TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "API,Truffle,Test",
      "jacoco" : "exclude",
    },

    "com.oracle.truffle.api.library" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.api.dsl"],
      "exports" : [
        "<package-info>", # exports all packages containing package-info.java
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "8+",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.dsl" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.api"],
      "exports" : [
        "<package-info>", # exports all packages containing package-info.java
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "8+",
      "workingSets" : "API,Truffle,Codegen",
    },

    "com.oracle.truffle.api.library.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.dsl.processor",
        "com.oracle.truffle.polyglot",
        "com.oracle.truffle.api.test",
        "com.oracle.truffle.api.library",
        "mx:JUNIT",
      ],
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "8+",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "API,Truffle,Codegen,Test",
      "jacoco" : "exclude",
    },


    "com.oracle.truffle.api.dsl.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.dsl.processor",
        "com.oracle.truffle.polyglot",
        "com.oracle.truffle.api.test",
        "com.oracle.truffle.api.interop",
        "mx:JUNIT",
      ],
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "8+",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "API,Truffle,Codegen,Test",
      "jacoco" : "exclude",
    },

    "com.oracle.truffle.sl.tck" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JUNIT",
        "sdk:POLYGLOT_TCK"
      ],
      "exports" : [
        "com.oracle.truffle.sl.tck",
      ],
      "checkstyle" : "com.oracle.truffle.sl",
      "javaCompliance" : "8+",
      "workingSets" : "SimpleLanguage,Test",
    },

    "com.oracle.truffle.dsl.processor" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.library",
        "com.oracle.truffle.api.instrumentation",
        "truffle:ANTLR4"
      ],
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "8+",
      "checkstyleVersion" : "8.8",
      "imports" : [
        "com.sun.tools.javac.processing",
        "com.sun.tools.javac.model",
        "com.sun.tools.javac.util",
        "com.sun.tools.javac.tree",
        "com.sun.tools.javac.file",
      ],
      "workingSets" : "Truffle,Codegen",
    },

    "com.oracle.truffle.dsl.processor.jdk9" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.dsl.processor",
      ],
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "9+",
      "overlayTarget" : "com.oracle.truffle.dsl.processor",
      "multiReleaseJarVersion" : "9",
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
      "javaCompliance" : "8+",
      # We need to force javac as JDT cannot deal with the amount of deprecations
      # in this package, together with using it in an annotation processor.
      # Can be removed when deprecated API was removed.
      "forceJavac" : "true",
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
        "com.oracle.truffle.api.profiles",
        "com.oracle.truffle.api.library",
      ],
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR_INTERNAL"],
      "exports" : [
        "<package-info>", # exports all packages containing package-info.java
      ],
      # We need to force javac as JDT cannot deal with the amount of deprecations
      # in this package, together with using it in an annotation processor.
      # Can be removed when deprecated API was removed.
      "forceJavac" : "true",
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "8+",
      "workingSets" : "API,Truffle",
    },

   "com.oracle.truffle.api.instrumentation" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.api.profiles"],
      "exports" : [
        "<package-info>", # exports all packages containing package-info.java
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "8+",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.instrumentation.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.debug",
        "com.oracle.truffle.api.dsl.test",
        "mx:JUNIT"
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "javaCompliance" : "8+",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.debug" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.polyglot"],
      "generatedDependencies" : ["com.oracle.truffle.polyglot"],
      "runtimeDeps" : [
        "java.desktop"
      ],
      "exports" : [
        "<package-info>", # exports all packages containing package-info.java
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR_INTERNAL"],
      "javaCompliance" : "8+",
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
      "javaCompliance" : "8+",
      "workingSets" : "API,Truffle",
      "testProject" : True,
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
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR_INTEROP_INTERNAL"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "8+",
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
      "javaCompliance" : "8+",
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
      "javaCompliance" : "8+",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "API,Truffle,Codegen,Test",
      "jacoco" : "exclude",
      "testProject" : True,
    },

    "com.oracle.truffle.object.dsl.processor" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.object.dsl",
        "com.oracle.truffle.dsl.processor"
      ],
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "8+",
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
      "javaCompliance" : "8+",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.object" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.api.object"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "8+",
      "workingSets" : "Truffle",
    },

    "com.oracle.truffle.object.basic" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.object"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "8+",
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
      "javaCompliance" : "8+",
      "workingSets" : "Truffle",
      "testProject" : True,
    },

    "com.oracle.truffle.tck" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "TRUFFLE_API",
        "mx:JUNIT",
        "sdk:POLYGLOT_TCK"
      ],
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "8+",
      "workingSets" : "Truffle,Tools",
    },
    "com.oracle.truffle.tck.common" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "sdk:POLYGLOT_TCK"
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "8+",
      "workingSets" : "Truffle,Tools",
    },
    "com.oracle.truffle.tck.tests" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JUNIT",
        "sdk:POLYGLOT_TCK",
        "com.oracle.truffle.tck.common",
      ],
      "uses":[
        "org.graalvm.polyglot.tck.LanguageProvider"
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "8+",
      "workingSets" : "Truffle,Tools",
      "testProject" : True,
    },
    "com.oracle.truffle.tck.instrumentation" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JUNIT",
        "TRUFFLE_API",
        "com.oracle.truffle.tck.common",
      ],
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "8+",
      "workingSets" : "Truffle,Tools",
    },

    "com.oracle.truffle.nfi" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "jniHeaders" : True,
      "dependencies" : [
        "com.oracle.truffle.api.interop",
        "com.oracle.truffle.nfi.spi",
      ],
      "exports" : [
        "<package-info>", # exports all packages containing package-info.java
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "8+",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR_INTEROP_INTERNAL"],
      "workingSets" : "Truffle",
      "os_arch" : {
        "solaris" : {
          "<others>" : {
            "ignore" : "temporarily disabled",  # necessary until GR-13214 is resolved
          },
        },
        "<others>" : {
          "<others>" : {
            "ignore" : False,
          },
        },
      },
    },

    "com.oracle.truffle.nfi.spi" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "TRUFFLE_API",
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "8+",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR_INTEROP_INTERNAL"],
      "workingSets" : "Truffle",
    },

    "libffi" : {
      "class" : "LibffiBuilderProject",
      "dependencies" : [
        "LIBFFI_SOURCES",
      ],
    },

    "com.oracle.truffle.nfi.native" : {
      "subDir" : "src",
      "native" : "shared_lib",
      "deliverable" : "trufflenfi",
      "buildDependencies" : [
        "libffi",
        "com.oracle.truffle.nfi",
      ],
      "os_arch" : {
        "windows" : {
          "<others>" : {
            "cflags" : []
          }
        },
        "solaris" : {
          "<others>" : {
            "cflags" : ["-g", "-Wall", "-Werror", "-m64", "-pthread"],
            "ldflags" : ["-m64", "-pthread"],
            "ldlibs" : ["-ldl"],
          },
        },
        "<others>" : {
          "<others>" : {
            "cflags" : ["-g", "-Wall", "-Werror"],
            "ldlibs" : ["-ldl"],
          },
        },
      },
    },

    "com.oracle.truffle.nfi.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JUNIT",
        "TRUFFLE_NFI",
        "TRUFFLE_TCK",
        "TRUFFLE_TEST_NATIVE",
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "8+",
      "workingSets" : "Truffle",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "javaProperties" : {
        "native.test.lib" : "<path:TRUFFLE_TEST_NATIVE>/<lib:nativetest>"
      },
      "testProject" : True,
    },

    "com.oracle.truffle.nfi.test.native" : {
      "subDir" : "src",
      "native" : "shared_lib",
      "deliverable" : "nativetest",
      "buildDependencies" : [
        "com.oracle.truffle.nfi.native",
      ],
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
            "ldlibs" : ["-lm"],
          },
        },
        "<others>" : {
          "<others>" : {
            "cflags" : ["-g", "-Wall", "-Werror"],
            "ldlibs" : ["-lm"],
          },
        },
      },
      "testProject" : True,
    },

    "com.oracle.truffle.sl" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "TRUFFLE_API",
        "truffle:ANTLR4"
      ],
      "javaCompliance" : "8+",
      "checkstyleVersion" : "8.8",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle,SimpleLanguage",
    },

    "com.oracle.truffle.sl.launcher" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "sdk:GRAAL_SDK",
      ],
      "checkstyle" : "com.oracle.truffle.sl",
      "javaCompliance" : "8+",
      "workingSets" : "Truffle,SimpleLanguage",
    },

    "com.oracle.truffle.sl.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.tck",
        "com.oracle.truffle.sl",
        "mx:JMH_1_21",
      ],
      "checkstyle" : "com.oracle.truffle.sl",
      "javaCompliance" : "8+",
      "workingSets" : "Truffle,SimpleLanguage,Test",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR", "mx:JMH_1_21"],
      "testProject" : True,
    },

    "com.oracle.truffle.st" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "TRUFFLE_API",
      ],
      "javaCompliance" : "8+",
      "checkstyleVersion" : "8.8",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle,SimpleLanguage",
    },

    "com.oracle.truffle.st.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JUNIT",
        "com.oracle.truffle.st"
      ],
      "javaCompliance" : "8+",
      "checkstyleVersion" : "8.8",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle,SimpleLanguage",
      "testProject" : True,
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

    "LIBFFI_DIST" : {
      "native" : True,
      "platformDependent" : True,
      "layout" : {
        "./" : "dependency:libffi/*"
      }
    },

    "TRUFFLE_API" : {
      # This distribution defines a module.
      "moduleName" : "org.graalvm.truffle",
      "subDir" : "src",
      "javaCompliance" : "8+",
      "dependencies" : [
        "com.oracle.truffle.api.dsl",
        "com.oracle.truffle.api.profiles",
        "com.oracle.truffle.api.debug",
        "com.oracle.truffle.api.utilities",
        "com.oracle.truffle.object.basic",
        "com.oracle.truffle.api.object.dsl",
        "com.oracle.truffle.polyglot",
      ],
      "distDependencies" : [
        "sdk:GRAAL_SDK"
      ],
      "description" : "Truffle is a multi-language framework for executing dynamic languages\nthat achieves high performance when combined with Graal.",
      "javadocType": "api",
    },

    "TRUFFLE_NFI" : {
      # This distribution defines a module.
      "moduleName" : "com.oracle.truffle.truffle_nfi",
      "subDir" : "src",
      "javaCompliance" : "8+",
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
    },

    "TRUFFLE_NFI_NATIVE" : {
      "native" : True,
      "platformDependent" : True,
      "platforms" : [
          "linux-amd64",
          "linux-aarch64",
          "darwin-amd64",
      ],
      "layout" : {
        "bin/" : "dependency:com.oracle.truffle.nfi.native",
        "include/" : "dependency:com.oracle.truffle.nfi.native/include/*.h",
      },
      "include_dirs" : ["include"],
      "description" : "Contains the native library needed by truffle-nfi.",
      "maven": True,
    },

    "TRUFFLE_TCK" : {
      "subDir" : "src",
      "javaCompliance" : "8+",
      "dependencies" : [
        "com.oracle.truffle.tck"
      ],
      "distDependencies" : [
        "TRUFFLE_API",
        "sdk:POLYGLOT_TCK",
      ],
      "exclude" : ["mx:JUNIT"],
      "description" : "A collection of tests that can certify language implementation to be compliant\nwith most recent requirements of the Truffle infrastructure and tooling.",
      "allowsJavadocWarnings": True,
    },

    "TRUFFLE_TCK_COMMON" : {
      "subDir" : "src",
      "javaCompliance" : "8+",
      "dependencies" : [
        "com.oracle.truffle.tck.common"
      ],
      "distDependencies" : [
        "sdk:POLYGLOT_TCK",
      ],
      "description" : "Common types for TCK Tests and Instruments.",
      "allowsJavadocWarnings": True,
    },

    "TRUFFLE_TCK_TESTS" : {
      "subDir" : "src",
      "javaCompliance" : "8+",
      "dependencies" : [
        "com.oracle.truffle.tck.tests"
      ],
      "distDependencies" : [
        "sdk:POLYGLOT_TCK",
        "TRUFFLE_TCK_COMMON"
      ],
      "exclude" : ["mx:JUNIT"],
      "description" : "A collection of tests that can certify language implementation to be compliant\nwith most recent requirements of the Truffle infrastructure and tooling.",
      "allowsJavadocWarnings": True,
      "testProject" : True,
      "maven": True,
    },

    "TRUFFLE_TCK_INSTRUMENTATION" : {
      "subDir" : "src",
      "javaCompliance" : "8+",
      "dependencies" : [
        "com.oracle.truffle.tck.instrumentation"
      ],
      "distDependencies" : [
        "TRUFFLE_API",
        "TRUFFLE_TCK_COMMON"
      ],
      "exclude" : ["mx:JUNIT"],
      "description" : "Instruments used by the Truffle TCK.",
      "allowsJavadocWarnings": True,
    },

    "TRUFFLE_DSL_PROCESSOR_INTERNAL" : {
      "internal" : True,
      "subDir" : "src",
      "dependencies" : ["com.oracle.truffle.dsl.processor", "com.oracle.truffle.api.library"],
      "distDependencies" : ["sdk:GRAAL_SDK"],
      "maven" : False,
    },

    "TRUFFLE_DSL_PROCESSOR_INTEROP_INTERNAL" : {
      "internal" : True,
      "subDir" : "src",
      "dependencies" : ["com.oracle.truffle.dsl.processor", "com.oracle.truffle.dsl.processor.interop"],
      "distDependencies" : ["sdk:GRAAL_SDK"],
      "maven" : False,
    },

    "TRUFFLE_DSL_PROCESSOR" : {
      "subDir" : "src",
      "dependencies" : ["com.oracle.truffle.dsl.processor", "com.oracle.truffle.dsl.processor.interop", "com.oracle.truffle.object.dsl.processor", "truffle:ANTLR4"],
      "distDependencies" : ["TRUFFLE_API"],
      "description" : "The Truffle DSL Processor generates source code for nodes that are declared using the DSL.",
      "allowsJavadocWarnings": True,
    },

    "TRUFFLE_SL" : {
      "subDir" : "src",
      "javaCompliance" : "8+",
      "dependencies" : [
        "com.oracle.truffle.sl",
      ],
      "exclude" : [
        "mx:JUNIT",
        "truffle:ANTLR4",
      ],
      "distDependencies" : [
          "TRUFFLE_API",
          "TRUFFLE_TCK",
      ],
      "description" : "Truffle SL is an example language implemented using the Truffle API.",
      "allowsJavadocWarnings": True,
    },

    "TRUFFLE_SL_LAUNCHER" : {
      "subDir" : "src",
      "javaCompliance" : "8+",
      "dependencies" : [
        "com.oracle.truffle.sl.launcher",
      ],
      "distDependencies" : [
          "sdk:GRAAL_SDK",
      ],
      "description" : "Truffle SL launchers using the polyglot API.",
      "allowsJavadocWarnings": True,
    },

    "TRUFFLE_SL_TEST" : {
      "subDir" : "src",
      "javaCompliance" : "8+",
      "dependencies" : [
        "com.oracle.truffle.sl.test"
      ],
      "exclude" : [
        "mx:JUNIT",
        "mx:JMH_1_21"
      ],
      "distDependencies" : [
          "TRUFFLE_API",
          "TRUFFLE_TCK",
          "TRUFFLE_SL"
      ],
      "maven" : False
    },

    "TRUFFLE_SL_TCK" : {
      "subDir" : "src",
      "javaCompliance" : "8+",
      "dependencies" : [
        "com.oracle.truffle.sl.tck"
      ],
      "exclude" : [
        "mx:JUNIT",
      ],
      "distDependencies" : [
        "sdk:POLYGLOT_TCK"
      ],
      "maven" : False
    },

    "TRUFFLE_ST" : {
      "subDir" : "src",
      "javaCompliance" : "8+",
      "dependencies" : [
        "com.oracle.truffle.st",
      ],
      "exclude" : [
        "mx:JUNIT",
      ],
      "distDependencies" : [
          "TRUFFLE_API",
      ],
      "description" : "Truffle ST is an example tool implemented using the Truffle API.",
      "allowsJavadocWarnings": True,
      "maven" : False
    },

    "TRUFFLE_ST_TEST" : {
      "subDir" : "src",
      "javaCompliance" : "8+",
      "dependencies" : [
        "com.oracle.truffle.st.test"
      ],
      "exclude" : [
        "mx:JUNIT",
      ],
      "distDependencies" : [
        "TRUFFLE_API",
        "TRUFFLE_ST"
      ],
      "maven" : False
    },

    "TRUFFLE_INSTRUMENT_TEST" : {
      "subDir" : "src",
      "javaCompliance" : "8+",
      "dependencies" : [
        "com.oracle.truffle.api.instrumentation.test",
      ],
      "exclude" : ["mx:HAMCREST", "mx:JUNIT", "mx:JMH_1_21"],
      "distDependencies" : [
        "TRUFFLE_API",
        "TRUFFLE_SL",
        "TRUFFLE_TCK",
        "sdk:POLYGLOT_TCK",
        "TRUFFLE_DSL_PROCESSOR",
      ],
      "description" : "Instrumentation tests including InstrumentationTestLanguage.",
      "allowsJavadocWarnings": True,
    },

     "TRUFFLE_TEST" : {
       "subDir" : "src",
       "javaCompliance" : "8+",
       "dependencies" : [
         "com.oracle.truffle.api.test",
         "com.oracle.truffle.api.benchmark",
         "com.oracle.truffle.api.dsl.test",
         "com.oracle.truffle.api.library.test",
         "com.oracle.truffle.api.instrumentation.test",
         "com.oracle.truffle.api.debug.test",
         "com.oracle.truffle.api.object.dsl.test",
         "com.oracle.truffle.object.basic.test",
         "com.oracle.truffle.nfi.test",
       ],
       "exclude" : ["mx:HAMCREST", "mx:JUNIT", "mx:JMH_1_21"],
       "distDependencies" : [
         "TRUFFLE_API",
         "TRUFFLE_NFI",
         "TRUFFLE_DSL_PROCESSOR",
         "TRUFFLE_INSTRUMENT_TEST",
         "TRUFFLE_TEST_NATIVE",
         "TRUFFLE_TCK",
      ],
      "maven" : False,
     },

     "TRUFFLE_TEST_NATIVE" : {
       "native" : True,
       "platformDependent" : True,
       "output" : "<mxbuild>/truffle-test-native",
       "dependencies" : [
         "com.oracle.truffle.nfi.test.native",
       ],
       "testDistribution" : True,
      "maven" : False,
     },

    "TRUFFLE_GRAALVM_SUPPORT" : {
      "native" : True,
      "description" : "Truffle support distribution for SVM",
      "layout" : {
        "native-image.properties" : "file:mx.truffle/macro-truffle.properties",
      },
    },

    "TRUFFLE_NFI_GRAALVM_SUPPORT" : {
      "native" : True,
      "description" : "Truffle NFI support distribution for the GraalVM",
      "layout" : {
        "native-image.properties" : "file:mx.truffle/language-nfi.properties",
      },
    },
  },
}
