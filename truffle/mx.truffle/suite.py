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
  "mxversion" : "6.11.4",
  "name" : "truffle",
  "version" : "23.0.0",
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
      "digest" : "sha512:27f6e2523e539383cede51d8eae7e97d49038c5a66cb4a94a9ce85165f16e7382b937a238cdb0c92e1136af56c5f57bcc6c04435a370c5d49f7e4bd32f0d9194",
      "maven" : {
        "groupId" : "jline",
        "artifactId" : "jline",
        "version" : "2.14.6",
      }
    },

    "LIBFFI_SOURCES" : {
      "resource" : True,
      "version" : "3.4.2",
      "urls" : [
        "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/libffi-{version}.tar.gz",
        "https://github.com/libffi/libffi/releases/download/v{version}/libffi-{version}.tar.gz",
      ],
      "digest" : "sha512:31bad35251bf5c0adb998c88ff065085ca6105cf22071b9bd4b5d5d69db4fadf16cadeec9baca944c4bb97b619b035bb8279de8794b922531fddeb0779eb7fb1",
    },

    "ANTLR4": {
      "digest" : "sha512:362994710ffebe81c200ffd6031e1158f8128da7e7f568c6d46bb2412c41d859b5d5cb162bf594d49faa1e895de3aceee66fa5a79e91e01117a028d800eb497c",
      "maven" : {
        "groupId" : "org.antlr",
        "artifactId" : "antlr4-runtime",
        "version" : "4.9.2",
      }
    },

    "ANTLR4_COMPLETE": {
      # original: https://www.antlr.org/download/antlr-4.9.2-complete.jar
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/antlr-4.9.2-complete.jar"],
      "digest": "sha512:a6040e66dc4b223228b83200149c41b66d7e9bee5d9580c36a3433437999487819c2fe85c2d5b72e1a9b24787f42a575603e23575fade8a5fb01f975c0bf76ea",
    },

    "TRUFFLE_JCODINGS": {
      "digest" : "sha512:49ab9ee3a2d2e7cdc4780252f6f796de1837e8b6dde3480b840faea711d16cbb9ea2c3d945e3cc8916b996a46164f866d3ed2cfdc6eeffb93087131b59e53ec0",
      "version" : "1.0.56.7",
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/graalvm-shadowed-jcodings-{version}.jar"],
      "exports" : [],
      "license": ["MIT"],
    },

    "TRUFFLE_ASM_9.1" : {
      "digest" : "sha512:5aa4dbb2886173e17b357c66bc926a75662df559091f007a64000e777fb2bf25f3ca08c40efb8b8120e1e8fd85ca542c76d777f80da6c530db19a3430e4a2cd1",
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/com.oracle.truffle.api.impl.asm-9.1.0.jar"],
    },

    "ICU4J" : {
      "moduleName" : "com.ibm.icu",
      "digest" : "sha512:48130eb346a5bb5bdaff867e5231808a3cd03d1876d8e6b7c501336df6992e912f7c53456dc72b673ad3272f3b54b414343eea8a1118d01d0e517403cab3e324",
      "sourceDigest" : "sha512:7afc98996ebf44046533abcca1125126ac4c05ad37ceeec35c29d2d29a9eeb06fc4d675d4378b9514238afdc7b73036947e196e350294b27520793a1111ddab3",
      "maven" : {
        "groupId" : "com.ibm.icu",
        "artifactId" : "icu4j",
        "version" : "72.1",
      },
    },
    "ICU4J-CHARSET" : {
      "moduleName" : "com.ibm.icu.charset",
      "digest" : "sha512:db3534ebaa54c956bd41f293bdffb691caf48500625286d0dbce2776da19bc1ad7d0108b192e8610db4c0a0ba459b36c4dc3af36b67b4c4c4d55a66315389cdd",
      "sourceDigest" : "sha512:07b228e82a4aeb246936468bb6e8c00ab1f9426aecc7c9dee78f97fc6ce9ad92492f5342112f8f815a8257ef644f6ffbb8859f2bdeaa8efeb830dbfae78a883f",
      "maven" : {
        "groupId" : "com.ibm.icu",
        "artifactId" : "icu4j-charset",
        "version" : "72.1",
      },
    },

    "TruffleJSON" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/trufflejson-20220320.jar"],
      "digest" : "sha512:b9f95acd4373acc5c86c2863e797313f7e97b39fa41566768a67f792f9cf3e642343fcbde2e6433069debc53babe766b09697eb775301f6f08773e75cfad9e83",
      "sourceUrls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/trufflejson-20220320-src.jar"],
      "sourceDigest" : "sha512:fe05059681fa01023a290a4a653e26007df0b76ecec81e2796828d34c7906986a502b0321f308578a15b695251b5a00b4b38cbc36a8165fbc3676cf4ac9b6ef9",
    },

    "VISUALVM-LIB-JFLUID-HEAP" : {
      "moduleName" : "org.graalvm.visualvm.lib.jfluid.heap",
      "digest" : "sha512:6d93cde728f5db242d2ab55090e5b2952e873625e542043d16d859e1486433c91efcf3c713d7255697951ee745cd5f66276b78f38c62c422cd7b1000291612ad",
      "sourceDigest" : "sha512:81377da5f52fae2e412ea084cb6e6e82d37beb96e8d624e47b6e7a2e70f61e237485239dd4b64934bf4758857d0582dc11acdeadc930bccb02a471df4b7e83b2",
      "maven" : {
        "groupId" : "org.graalvm.visualvm.modules",
        "artifactId" : "org-graalvm-visualvm-lib-jfluid-heap",
        "version" : "2.1.4",
      },
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

    "com.oracle.truffle.api.jdk19" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
      ],
      "overlayTarget" : "com.oracle.truffle.api",
      "checkPackagePrefix" : "false",
      "multiReleaseJarVersion" : "19",
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "19+",
      "javaPreviewNeeded": "19+",
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
        "VISUALVM-LIB-JFLUID-HEAP",
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

    "com.oracle.truffle.api.test.jdk17": {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.test",
      ],
      "checkstyle": "com.oracle.truffle.api",
      "javaCompliance" : "17+",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "API,Truffle,Test",
      "jacoco" : "exclude",
      "testProject" : True,
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
      "checkstyle" : "com.oracle.truffle.api",
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
    "com.oracle.truffle.tck.tests.language" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "TRUFFLE_API",
      ],
      "requires" : [
        "jdk.unsupported", # sun.misc.Unsafe
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "11+",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle,Test",
      "jacoco" : "exclude",
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
        "com.oracle.truffle.nfi.api",
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
        "native.test.path" : "<path:TRUFFLE_TEST_NATIVE>",
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
      "checkstyle" : "com.oracle.truffle.api",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle,SimpleLanguage",
    },

    "com.oracle.truffle.sl.launcher" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "sdk:GRAAL_SDK",
      ],
      "checkstyle" : "com.oracle.truffle.api",
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
      "checkstyle" : "com.oracle.truffle.api",
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
      "checkstyle" : "com.oracle.truffle.api",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle",
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
          "com.oracle.truffle.api* to com.oracle.truffle.regex, jdk.internal.vm.compiler, jdk.internal.vm.compiler.truffle.jfr, com.oracle.graal.graal_enterprise, org.graalvm.nativeimage.builder",
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
          "darwin-aarch64",
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

    "TRUFFLE_TCK_TESTS_LANGUAGE" : {
      "subDir" : "src",
      "javaCompliance" : "11+",
      "dependencies" : [
        "com.oracle.truffle.tck.tests.language"
      ],
      "distDependencies" : [
        "TRUFFLE_API",
      ],
      "description" : "A language for Truffle TCK testing.",
      "allowsJavadocWarnings": True,
      "testDistribution" : True,
      "maven": False,
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
       "exclude" : ["mx:HAMCREST", "mx:JUNIT", "mx:JMH_1_21", "VISUALVM-LIB-JFLUID-HEAP"],
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
        "com.oracle.truffle.api.test.jdk17",
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

    "TRUFFLE_ICU4J_GRAALVM_SUPPORT" : {
      "native" : True,
      "description" : "Truffle support distribution for ICU4J",
      "layout" : {
        "native-image.properties" : "file:mx.truffle/language-icu4j.properties",
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
