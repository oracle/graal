#
# Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
  "mxversion" : "5.300.4",
  "name" : "truffle",
  "version" : "22.1.0",
  "release" : False,
  "groupId" : "org.graalvm.truffle",
  "sourceinprojectwhitelist" : [],
  "url" : "http://openjdk.java.net/projects/graal",
  "developer" : {
    "name" : "GraalVM Development",
    "email" : "graalvm-dev@oss.oracle.com",
    "organization" : "Oracle Corporation",
    "organizationUrl" : "http://www.graalvm.org/",
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
      "version" : "3.3",
      # original: https://sourceware.org/pub/libffi/libffi-{version}.tar.gz
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/libffi-{version}.tar.gz"],
      "sha1" : "8df6cb570c8d6596a67d1c0773bf00650154f7aa",
    },

    "ANTLR4": {
      "sha1" : "ece33ec76e002dfde574cf7b57451a91a99185c5",
      "maven" : {
        "groupId" : "org.antlr",
        "artifactId" : "antlr4-runtime",
        "version" : "4.9.2",
      }
    },

    "ANTLR4_COMPLETE": {
      # original: https://www.antlr.org/download/antlr-4.9.2-complete.jar
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/antlr-4.9.2-complete.jar"],
      "sha1": "3ce2e9d96baa6e5de18eb370efc71cbb820021fb",
    },

    "TRUFFLE_JCODINGS": {
      "sha1" : "c88c640b82534bea6bdb0dfacf4035665ad91e26",
      "version" : "1.0.56.6",
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/graalvm-shadowed-jcodings-{version}.jar"],
      "exports" : [],
      "license": ["MIT"],
    },

    "TRUFFLE_ASM_9.1" : {
      "sha1" : "956cc657b3f1aa2c08ebbb3bbe4092aea1daf54a",
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/com.oracle.truffle.api.impl.asm-9.1.0.jar"],
    },

    "ICU4J" : {
      "moduleName" : "com.ibm.icu",
      "sha1" : "dfa3a1fbc55bf5db8c6e79fc0935ac7ab1202950",
      "sourceSha1" : "21551c094193ab59d284b434c1e33a3ddf4b5c0e",
      "maven" : {
        "groupId" : "com.ibm.icu",
        "artifactId" : "icu4j",
        "version" : "70.1",
      },
    },
    "ICU4J-CHARSET" : {
      "moduleName" : "com.ibm.icu.charset",
      "sha1" : "9d220e65da007d8ed60cd7970a74d14a8205dfe7",
      "sourceSha1" : "8f38201691b79d28e424daaa1c9a95039468e946",
      "maven" : {
        "groupId" : "com.ibm.icu",
        "artifactId" : "icu4j-charset",
        "version" : "70.1",
      },
    },

    "TruffleJSON" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/trufflejson-20201115.jar"],
      "sha1" : "7987931963f4fa995fa515273d70116725d0e37f",
      "sourceUrls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/trufflejson-20201115-src.jar"],
      "sourceSha1" : "1da7c590b0582fb4c2e4de9b581c70407fa9108b",
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
      "requires" : [
        "java.logging",
        "jdk.unsupported", # sun.misc.Unsafe
      ],
      # We need to force javac as JDT has a bug that JDT ignores SuppressWarnings
      # if warnings as errors is enabled. See GR-14683.
      "forceJavac" : "true",
      "javaCompliance" : "11+",
      "checkstyleVersion" : "8.36.1",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.utilities" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api"
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "11+",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.polyglot" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "sdk:GRAAL_SDK",
        "com.oracle.truffle.api.instrumentation",
        "com.oracle.truffle.api.exception",
      ],
      "requires" : [
        "java.logging",
        "jdk.management",
      ],
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "11+",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.host" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "sdk:GRAAL_SDK",
        "com.oracle.truffle.api.exception",
        "truffle:TRUFFLE_ASM_9.1",
      ],
      "requires" : [
        "java.sql",
        "jdk.unsupported", # sun.misc.Unsafe
      ],
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "11+",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "TRUFFLE_TCK_TESTS",
        "TRUFFLE_API",
        "TRUFFLE_SL",
        "mx:JUNIT",
      ],
      "requires" : [
        "java.desktop",
        "java.logging",
        "java.sql",
        "jdk.management",
      ],
      "requiresConcealed" : {
        "java.base" : [
          "jdk.internal.loader"
        ],
      },
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "11+",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "API,Truffle,Test",
      "jacoco" : "exclude",
    },

    "com.oracle.truffle.api.benchmark" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.instrumentation.test",
        "TRUFFLE_API",
        "mx:JMH_1_21",
      ],
      "requires" : [
        "jdk.unsupported", # sun.misc.Unsafe
      ],
      "requiresConcealed" : {
        "java.base" : ["jdk.internal.loader"],
      },
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "11+",
      "spotbugsIgnoresGenerated" : True,
      "testProject" : True,
      "annotationProcessors" : ["mx:JMH_1_21", "TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "API,Truffle,Test",
      "jacoco" : "exclude",
    },

    "com.oracle.truffle.api.library" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.api.dsl", "com.oracle.truffle.api.utilities"],
      "requires" : [
        "jdk.unsupported", # sun.misc.Unsafe
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "javaCompliance" : "11+",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.dsl" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.api"],
      "requires" : [
        "java.logging",
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "11+",
      "workingSets" : "API,Truffle,Codegen",
    },

    "com.oracle.truffle.api.library.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.polyglot",
        "com.oracle.truffle.api.test",
        "com.oracle.truffle.api.library",
        "mx:JUNIT",
      ],
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "11+",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "API,Truffle,Codegen,Test",
      "jacoco" : "exclude",
    },


    "com.oracle.truffle.api.dsl.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.polyglot",
        "com.oracle.truffle.api.test",
        "com.oracle.truffle.api.interop",
        "mx:JUNIT",
      ],
      "requires" : [
        "java.logging",
      ],
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "11+",
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
      "checkstyle" : "com.oracle.truffle.sl",
      "javaCompliance" : "11+",
      "workingSets" : "SimpleLanguage,Test",
      "jacoco" : "exclude",
    },

    "com.oracle.truffle.dsl.processor" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "truffle:ANTLR4"
      ],
      "requires" : [
        "java.compiler",
      ],
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "11+",
      "checkstyleVersion" : "8.36.1",
      "workingSets" : "Truffle,Codegen",
    },

    "com.oracle.truffle.dsl.processor.jdk9" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.dsl.processor",
      ],
      "requires" : [
        "java.compiler",
      ],
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "11+",
      "overlayTarget" : "com.oracle.truffle.dsl.processor",
      "multiReleaseJarVersion" : "11",
      "workingSets" : "Truffle,Codegen",
    },

    "com.oracle.truffle.api.interop" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.strings",
      ],
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "11+",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.exception" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.interop",
      ],
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "11+",
      "workingSets" : "API,Truffle",
    },

   "com.oracle.truffle.api.instrumentation" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.api.interop"],
      "requires" : [
        "java.logging",
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "javaCompliance" : "11+",
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
      "requires" : [
        "java.logging",
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "javaCompliance" : "11+",
      "workingSets" : "API,Truffle",
      "jacoco" : "exclude",
    },

    "com.oracle.truffle.api.debug" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.polyglot"],
      "generatedDependencies" : ["com.oracle.truffle.polyglot"],
      "checkstyle" : "com.oracle.truffle.api",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "javaCompliance" : "11+",
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
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "javaCompliance" : "11+",
      "workingSets" : "API,Truffle",
      "testProject" : True,
      "jacoco" : "exclude",
    },

    "com.oracle.truffle.api.object" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.interop",
      ],
      "requires" : [
        "jdk.unsupported", # sun.misc.Unsafe
      ],
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "11+",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.strings" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.profiles",
        "TRUFFLE_JCODINGS",
      ],
      "requires" : [
        "jdk.unsupported", # sun.misc.Unsafe
      ],
      "generatedDependencies": [
        "com.oracle.truffle.api.library",
      ],
      "requiresConcealed" : {
        "java.base" : ["jdk.internal.loader"],
      },
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "11+",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.strings.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "TRUFFLE_API",
        "mx:JUNIT",
        "mx:JMH_1_21",
      ],
      "requires" : [
        "jdk.unsupported", # sun.misc.Unsafe
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "11+",
      "annotationProcessors" : [
        "TRUFFLE_DSL_PROCESSOR",
        "mx:JMH_1_21",
      ],
      "requiresConcealed" : {
        "java.base" : ["jdk.internal.loader"],
      },
      "workingSets" : "API,Truffle,Codegen,Test",
      "jacoco" : "exclude",
      "testProject" : True,
    },


    "com.oracle.truffle.api.staticobject" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api",
        "truffle:TRUFFLE_ASM_9.1",
      ],
      "requires" : [
        "jdk.unsupported", # sun.misc.Unsafe
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "11+",
      "javadocType" : "api",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.staticobject.jdk11" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.staticobject"
      ],
      "overlayTarget" : "com.oracle.truffle.api.staticobject",
      "checkPackagePrefix" : "false",
      "multiReleaseJarVersion" : "11",
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "11+",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.staticobject.jdk17" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.staticobject"
      ],
      "overlayTarget" : "com.oracle.truffle.api.staticobject",
      "checkPackagePrefix" : "false",
      "multiReleaseJarVersion" : "17",
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "17+",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.staticobject.test": {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.staticobject",
        "TRUFFLE_API",
        "mx:JUNIT"
      ],
      "checkstyle": "com.oracle.truffle.api",
      "javaCompliance" : "11+",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "API,Truffle,Test",
      "testProject" : True,
    },

    "com.oracle.truffle.api.staticobject.test.jdk17": {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.staticobject.test",
      ],
      "checkstyle": "com.oracle.truffle.api",
      "javaCompliance" : "17+",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "API,Truffle,Test",
      "testProject" : True,
    },

    "com.oracle.truffle.api.profiles" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.api"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "11+",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.object" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.api.object"],
      "requires" : [
        "jdk.unsupported", # sun.misc.Unsafe
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "11+",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle",
    },

    "com.oracle.truffle.object.basic.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.object",
        "com.oracle.truffle.api.test",
        "mx:JUNIT"
      ],
      "requires" : [
        "jdk.unsupported", # GR-36880
      ],
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "11+",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle",
      "testProject" : True,
      "jacoco" : "exclude",
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
      "javaCompliance" : "11+",
      "workingSets" : "Truffle,Tools",
    },
    "com.oracle.truffle.tck.common" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "sdk:POLYGLOT_TCK"
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "11+",
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
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "11+",
      "workingSets" : "Truffle,Tools",
      "testProject" : False,
      "jacoco" : "exclude",
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
      "javaCompliance" : "11+",
      "workingSets" : "Truffle,Tools",
    },

    "com.oracle.truffle.nfi" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.nfi.api",
        "com.oracle.truffle.nfi.backend.spi",
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "spotbugsIgnoresGenerated" : True,
      "javaCompliance" : "11+",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle",
    },

    "com.oracle.truffle.nfi.api" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.interop",
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "11+",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle",
    },

    "com.oracle.truffle.nfi.backend.libffi" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "jniHeaders" : True,
      "dependencies" : [
        "com.oracle.truffle.nfi.backend.spi",
      ],
      "requires" : [
        "jdk.unsupported", # sun.misc.Unsafe
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "11+",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
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

    "com.oracle.truffle.nfi.backend.spi" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "TRUFFLE_API",
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "11+",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
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
      "use_jdk_headers" : True,
      "buildDependencies" : [
        "libffi",
        "com.oracle.truffle.nfi.backend.libffi",
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
        "linux" : {
          "<others>" : {
            "cflags" : ["-g", "-Wall", "-Werror", "-D_GNU_SOURCE"],
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
        "com.oracle.truffle.api.test",
        "mx:JUNIT",
        "TRUFFLE_NFI",
        "TRUFFLE_TCK",
        "TRUFFLE_TEST_NATIVE",
      ],
      "requires" : [
        "jdk.unsupported", # sun.misc.Unsafe
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "11+",
      "workingSets" : "Truffle",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "javaProperties" : {
        "native.test.lib" : "<path:TRUFFLE_TEST_NATIVE>/<lib:nativetest>",
        "native.isolation.test.lib" : "<path:TRUFFLE_TEST_NATIVE>/<lib:isolationtest>"
      },
      "testProject" : True,
      "jacoco" : "exclude",
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
            "cflags" : ["-g", "-Wall", "-Werror", "-m64", "-pthread"],
            "ldflags" : ["-m64", "-pthread"],
            "ldlibs" : ["-lm"],
          },
        },
        "<others>" : {
          "<others>" : {
            "cflags" : ["-g", "-Wall", "-Werror", "-pthread"],
            "ldflags" : ["-pthread"],
            "ldlibs" : ["-lm"],
          },
        },
      },
      "testProject" : True,
      "jacoco" : "exclude",
    },

    "com.oracle.truffle.nfi.test.native.isolation" : {
      "subDir" : "src",
      "native" : "shared_lib",
      "deliverable" : "isolationtest",
      "os_arch" : {
        "windows" : {
          "<others>" : {
            "cflags" : []
          }
        },
        "solaris" : {
          "<others>" : {
            "cflags" : ["-g", "-Wall", "-Werror", "-m64"],
          },
        },
        "<others>" : {
          "<others>" : {
            "cflags" : ["-g", "-Wall", "-Werror"],
          },
        },
      },
      "testProject" : True,
      "jacoco" : "exclude",
    },

    "com.oracle.truffle.sl" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "TRUFFLE_API",
        "truffle:ANTLR4"
      ],
      "requires" : [
        "java.logging",
        "jdk.unsupported", # GR-36880
      ],
      "javaCompliance" : "11+",
      "checkstyleVersion" : "8.36.1",
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
      "javaCompliance" : "11+",
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
      "requires" : [
        "java.logging",
      ],
      "checkstyle" : "com.oracle.truffle.sl",
      "javaCompliance" : "11+",
      "workingSets" : "Truffle,SimpleLanguage,Test",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR", "mx:JMH_1_21"],
      "testProject" : True,
      "jacoco" : "exclude",
    },

    "com.oracle.truffle.st" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "TRUFFLE_API",
      ],
      "javaCompliance" : "11+",
      "checkstyleVersion" : "8.36.1",
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
      "javaCompliance" : "11+",
      "checkstyleVersion" : "8.36.1",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle,SimpleLanguage",
      "testProject" : True,
      "jacoco" : "exclude",
    },

    "com.oracle.graalvm.locator": {
      "subDir": "src",
      "sourceDirs": ["src"],
      "dependencies": [
        "truffle:TRUFFLE_API",
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "11+",
      "license": "GPLv2-CPE",
      "jacoco" : "exclude",
    },

    "com.oracle.graalvm.locator.jdk11" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "overlayTarget" : "com.oracle.graalvm.locator",
      "checkstyle" : "com.oracle.truffle.api",
      "multiReleaseJarVersion" : "11",
      "javaCompliance" : "11+",
      "checkPackagePrefix" : "false",
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
      "moduleInfo" : {
        "name" : "org.graalvm.truffle",
        "requires" : [
          "static java.desktop",
          "jdk.unsupported", # sun.misc.Unsafe
          "java.logging",
          "java.management",
          "java.sql" # java.sql.date java.sql.Time
        ],
        "exports" : [
          # Qualified exports
          "com.oracle.truffle.api* to com.oracle.truffle.regex, jdk.internal.vm.compiler, com.oracle.graal.graal_enterprise, org.graalvm.nativeimage.builder",
          "com.oracle.truffle.api.impl to org.graalvm.locator",
          "com.oracle.truffle.api to org.graalvm.locator, com.oracle.truffle.truffle_nfi, org.graalvm.nativeimage.builder",
          "com.oracle.truffle.object to jdk.internal.vm.compiler, com.oracle.graal.graal_enterprise",
          "com.oracle.truffle.api.library to com.oracle.truffle.truffle_nfi_libffi, com.oracle.truffle.truffle_nfi",
        ],
        "uses" : [
          "com.oracle.truffle.api.TruffleRuntimeAccess",
          "java.nio.file.spi.FileTypeDetector",
          "com.oracle.truffle.api.impl.TruffleLocator",
          "com.oracle.truffle.api.TruffleLanguage.Provider",
          "com.oracle.truffle.api.instrumentation.TruffleInstrument.Provider",
          "com.oracle.truffle.api.library.DefaultExportProvider",
          "com.oracle.truffle.api.library.EagerExportProvider"
        ],
      },
      "moduleInfo:open" : {
        # This is the module descriptor for the Truffle API modular jar deployed via maven.
        # It exports all the Truffle API packages.
        "exports" : [
          # Unqualified exports
          "com.oracle.truffle.api.debug",
          "com.oracle.truffle.api.nodes",
          "com.oracle.truffle.api.source",
          "com.oracle.truffle.api.memory",
          "com.oracle.truffle.api.io",
          "com.oracle.truffle.api.frame",
          "com.oracle.truffle.api",
          "com.oracle.truffle.api.instrumentation",
          "com.oracle.truffle.api.dsl",
          "com.oracle.truffle.api.profiles",
          "com.oracle.truffle.api.interop",
          "com.oracle.truffle.api.exception",
          "com.oracle.truffle.api.object",
          "com.oracle.truffle.api.strings",
          "com.oracle.truffle.api.utilities",
          "com.oracle.truffle.api.library",
          "com.oracle.truffle.api.staticobject",

          # Qualified exports
          "com.oracle.truffle.api.impl to jdk.internal.vm.compiler, org.graalvm.locator",
          "com.oracle.truffle.object to jdk.internal.vm.compiler, com.oracle.graal.graal_enterprise",
        ],
      },
      "subDir" : "src",
      "javaCompliance" : "11+",
      "dependencies" : [
        "com.oracle.truffle.api",
        "com.oracle.truffle.api.exception",
        "com.oracle.truffle.api.dsl",
        "com.oracle.truffle.api.profiles",
        "com.oracle.truffle.api.debug",
        "com.oracle.truffle.api.utilities",
        "com.oracle.truffle.object",
        "com.oracle.truffle.api.strings",
        "com.oracle.truffle.polyglot",
        "com.oracle.truffle.host",
        "com.oracle.truffle.api.staticobject",
      ],
      "distDependencies" : [
        "sdk:GRAAL_SDK"
      ],
      "description" : "Truffle is a multi-language framework for executing dynamic languages\nthat achieves high performance when combined with Graal.",
      "javadocType": "api",
      "maven" : {
        # Deploy the modular jar specified by "moduleInfo.open"
        "moduleInfo" : "open",
      }
    },

    "TRUFFLE_NFI" : {
      # This distribution defines a module.
      "moduleInfo" : {
        "name" : "com.oracle.truffle.truffle_nfi",
        "requires" : [
          "org.graalvm.truffle"
        ]
      },
      "subDir" : "src",
      "javaCompliance" : "11+",
      "dependencies" : [
        "com.oracle.truffle.nfi",
      ],
      "distDependencies" : [
        "TRUFFLE_API",
        "TRUFFLE_NFI_NATIVE",
      ],
      "description" : """Native function interface for the Truffle framework.""",
      "allowsJavadocWarnings": True,
    },

    "TRUFFLE_NFI_LIBFFI" : {
      # This distribution defines a module.
      "moduleInfo" : {
        "name" : "com.oracle.truffle.truffle_nfi_libffi",
        "requiresConcealed" : {
          "org.graalvm.truffle" : [
            "com.oracle.truffle.api",
            "com.oracle.truffle.api.library"
          ],
        }
      },
      "subDir" : "src",
      "javaCompliance" : "11+",
      "dependencies" : [
        "com.oracle.truffle.nfi.backend.libffi",
      ],
      "distDependencies" : [
        "TRUFFLE_NFI",
        "TRUFFLE_NFI_NATIVE",
      ],
      "javaProperties" : {
          "truffle.nfi.library" : "<path:TRUFFLE_NFI_NATIVE>/bin/<lib:trufflenfi>"
      },
      "description" : """Implementation of the Truffle NFI using libffi.""",
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
      "description" : "Contains the NFI headers, and the native library needed by the libffi NFI backend.",
      "maven": True,
    },

    "TRUFFLE_TCK" : {
      "subDir" : "src",
      "javaCompliance" : "11+",
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
      "javaCompliance" : "11+",
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
      "javaCompliance" : "11+",
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
      "testDistribution" : False,
      "maven": True,
    },

    "TRUFFLE_TCK_INSTRUMENTATION" : {
      "subDir" : "src",
      "javaCompliance" : "11+",
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

    "TRUFFLE_DSL_PROCESSOR" : {
      "subDir" : "src",
      "dependencies" : ["truffle:ANTLR4",
                        "com.oracle.truffle.dsl.processor"],
      "distDependencies" : [],
      "description" : "The Truffle DSL Processor generates source code for nodes that are declared using the DSL.",
      "allowsJavadocWarnings": True,
    },

    "TRUFFLE_SL" : {
      "subDir" : "src",
      "javaCompliance" : "11+",
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
      "javaCompliance" : "11+",
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
      "javaCompliance" : "11+",
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
      "javaCompliance" : "11+",
      "dependencies" : [
        "com.oracle.truffle.sl.tck"
      ],
      "exclude" : [
        "mx:JUNIT",
      ],
      "distDependencies" : [
        "sdk:POLYGLOT_TCK",
        "TRUFFLE_SL"
      ],
      "maven" : False
    },

    "TRUFFLE_ST" : {
      "subDir" : "src",
      "javaCompliance" : "11+",
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
      "javaCompliance" : "11+",
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

     "TRUFFLE_TEST" : {
       "subDir" : "src",
       "javaCompliance" : "11+",
       "dependencies" : [
         "com.oracle.truffle.api.test",
         "com.oracle.truffle.api.benchmark",
         "com.oracle.truffle.api.dsl.test",
         "com.oracle.truffle.api.library.test",
         "com.oracle.truffle.api.instrumentation.test",
         "com.oracle.truffle.api.debug.test",
         "com.oracle.truffle.api.strings.test",
         "com.oracle.truffle.object.basic.test",
         "com.oracle.truffle.nfi.test",
         "com.oracle.truffle.api.staticobject.test",
       ],
       "exclude" : ["mx:HAMCREST", "mx:JUNIT", "mx:JMH_1_21"],
       "distDependencies" : [
         "TRUFFLE_API",
         "TRUFFLE_SL",
         "TRUFFLE_TCK_COMMON",
         "TRUFFLE_TCK_TESTS",
         "TRUFFLE_NFI",
         "TRUFFLE_NFI_LIBFFI",
         "TRUFFLE_DSL_PROCESSOR",
         "TRUFFLE_TEST_NATIVE",
         "TRUFFLE_TCK",
      ],
      "maven" : False,
     },

    "TRUFFLE_TEST_17" : {
      "subDir" : "src",
      "javaCompliance" : "17+",
      "dependencies" : [
        "com.oracle.truffle.api.staticobject.test.jdk17",
      ],
      "exclude" : ["mx:HAMCREST", "mx:JUNIT", "mx:JMH_1_21"],
      "distDependencies" : [
        "TRUFFLE_TEST",
      ],
      "testDistribution" : True,
      "maven" : False,
    },

     "TRUFFLE_TEST_NATIVE" : {
       "native" : True,
       "platformDependent" : True,
       "output" : "<mxbuild>/truffle-test-native",
       "dependencies" : [
         "com.oracle.truffle.nfi.test.native",
         "com.oracle.truffle.nfi.test.native.isolation",
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
      "maven" : False,
    },

    "TRUFFLE_NFI_GRAALVM_SUPPORT" : {
      "native" : True,
      "description" : "Truffle NFI support distribution for the GraalVM",
      "layout" : {
        "./include/" : ["dependency:com.oracle.truffle.nfi.native/include/*.h"],
      },
      "maven" : False,
    },

    "TRUFFLE_NFI_NATIVE_GRAALVM_SUPPORT" : {
      "native" : True,
      "platformDependent" : True,
      "description" : "Truffle NFI support distribution for the GraalVM",
      "layout" : {
        "./" : ["dependency:com.oracle.truffle.nfi.native"],
      },
      "maven" : False,
    },

    "LOCATOR": {
      "subDir": "src",
      "moduleInfo" : {
        "name" : "org.graalvm.locator",
        "exports" : [
          "com.oracle.graalvm.locator to jdk.internal.vm.compiler.management",
        ],
      },
      "dependencies": ["com.oracle.graalvm.locator"],
      "distDependencies": [
        "truffle:TRUFFLE_API",
      ],
      "maven" : False,
    },
  },
}
