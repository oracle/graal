#
# Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
  "mxversion": "6.39.0",
  "name" : "truffle",
  "version" : "23.1.5",
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
      },
    ]
  },
  "libraries" : {

    # ------------- Libraries -------------

    "LIBFFI_SOURCES" : {
      "resource" : True,
      "version" : "3.4.4",
      "urls" : [
        "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/libffi-{version}.tar.gz",
        "https://github.com/libffi/libffi/releases/download/v{version}/libffi-{version}.tar.gz",
      ],
      "digest" : "sha512:88680aeb0fa0dc0319e5cd2ba45b4b5a340bc9b4bcf20b1e0613b39cd898f177a3863aa94034d8e23a7f6f44d858a53dcd36d1bb8dee13b751ef814224061889",
    },

    "ANTLR4": {
      "moduleName": "org.antlr.antlr4.runtime",
      "digest" : "sha512:4abb69a3c6895edeec64c11d61886fbeb68eda5ebb21094f596e4f7add8afa9ff049c05fa916264b9185ac9013b16d8eabf44fb65da0b6871997c8f1473a3771",
      "sourceDigest" : "sha512:611840da04cf2768f234ef06d69d95ed6edb2c55a48c3cffdf96ab23e76fc3bdd03e900155e454d2dd23ce8b644d48882a980aa6f5a76905dbbad57320d1cce0",
      "maven" : {
        "groupId" : "org.antlr",
        "artifactId" : "antlr4-runtime",
        "version" : "4.12.0",
      }
    },

    "ANTLR4_COMPLETE": {
      # original: https://www.antlr.org/download/antlr-4.12.0-complete.jar
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/antlr-4.12.0-complete.jar"],
      "digest": "sha512:f92f976375421ef117a97cb4298b7478b849370334a1eaf2efb243bd510e79358f258f47327deb2b9441843e7061acc67add2d034259f3136d97da8a09e545a4",
    },

    "TRUFFLE_JCODINGS": {
      "digest" : "sha512:455f3dc287181c185ab87c03e88cc89615f3da262358f44ea01bb3cc9f04d8e3cee7911f8a14a6403d3285d9b54812aaa48ade093a0b3ec4e594adbbda1d5387",
      "version" : "1.0.58.1",
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/graalvm-shadowed-jcodings-{version}.jar"],
      "exports" : [],
      "license": ["MIT"],
    },

    "TRUFFLE_ASM_9.5" : {
      "digest" : "sha512:7a49aaa0c4b513ca54ce684a74a3848ba4caf486320125f08cb8872720dc1e789538729f45c46d6ccf1b1ea54f7c3770dc9682d13a3f1813a348168ee5c40b82",
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/com.oracle.truffle.api.impl.asm-9.5.0.jar"],
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

    # Deprecated: Do not use, replaced by TRUFFLE_JSON.
    "TruffleJSON" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/trufflejson-20231013.jar"],
      "digest" : "sha512:35a51c5deb0a3e3690b6d1d3d84682bc5e43b2fba1f1611d456526664667532103eb6efff6b2ea9eaedd099c9429f2b661ccbab44b681a031bdbb3e9758da949",
      "sourceUrls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/trufflejson-20231013-src.jar"],
      "sourceDigest" : "sha512:24e7276d1ac030297ff1c6a0ff2f2604ab5949af916f827efb677839d537aaf304bbf355dbb223f8a1ecebddb21e4b2a8e2dd87039e4816115a0e9568230fbf6",
    },

    "JSON" : {
      "moduleName" : "org.json",
      "digest" : "sha512:a5cdd1ed984448d6538746429f2d1a0ec8f64f93af0e84870ce898a9f07a81d11bf27d2ee081471975772efc8a0d3d5e05541197a532066e9edb09ad032d31a3",
      "sourceDigest" : "sha512:80b382663f1bfd31f668eeb083a0a5a1620153e195e5b030da8f4c320f6126d7183ecb11f4b1afc8408a385c0918caa9f77942376499f9723dd7134dadd57a89",
      "maven" : {
        "groupId" : "org.json",
        "artifactId" : "json",
        "version" : "20231013",
      },
    },

    "VISUALVM-LIB-JFLUID-HEAP" : {
      "digest" : "sha512:6d93cde728f5db242d2ab55090e5b2952e873625e542043d16d859e1486433c91efcf3c713d7255697951ee745cd5f66276b78f38c62c422cd7b1000291612ad",
      "sourceDigest" : "sha512:81377da5f52fae2e412ea084cb6e6e82d37beb96e8d624e47b6e7a2e70f61e237485239dd4b64934bf4758857d0582dc11acdeadc930bccb02a471df4b7e83b2",
      "maven" : {
        "groupId" : "org.graalvm.visualvm.modules",
        "artifactId" : "org-graalvm-visualvm-lib-jfluid-heap",
        "version" : "2.1.4",
      },
    },

    "JIMFS" : {
      "sha1": "48462eb319817c90c27d377341684b6b81372e08",
      "sourceSha1": "bc1cd4901ef5f6ed1f62fe2e876758ce081e2aba",
      "maven": {
        "groupId": "com.google.jimfs",
        "artifactId": "jimfs",
        "version": "1.2",
      },
    },

    "GUAVA": { # JIMFS dependency
      "moduleName": "com.google.common",
      "sha1": "119ea2b2bc205b138974d351777b20f02b92704b",
      "sourceSha1": "d34772c01bd6637982d1aafe895c4fcd8b42e139",
      "maven": {
        "groupId": "com.google.guava",
        "artifactId": "guava",
        "version": "31.0.1-jre",
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
        "sdk:POLYGLOT",
      ],
      "requires" : [
        "java.logging",
        "jdk.unsupported", # sun.misc.Unsafe
      ],
      # We need to force javac as JDT has a bug that JDT ignores SuppressWarnings
      # if warnings as errors is enabled. See GR-14683.
      "forceJavac" : "true",
      "javaCompliance" : "17+",
      "checkstyleVersion" : "10.7.0",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.jdk21" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
      ],
      "overlayTarget" : "com.oracle.truffle.api",
      "checkPackagePrefix" : "false",
      "multiReleaseJarVersion" : "21",
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "21+",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.utilities" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api"
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "17+",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.polyglot" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "sdk:POLYGLOT",
        "com.oracle.truffle.api.instrumentation",
        "com.oracle.truffle.api.exception",
      ],
      "requires" : [
        "java.logging",
        "jdk.management",
        "jdk.unsupported", # sun.misc.Unsafe
      ],
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "17+",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.runtime" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "TRUFFLE_API",
        "TRUFFLE_COMPILER",
      ],
      "requires" : [
        "java.logging",
        "jdk.management",
        "jdk.jfr",
        "jdk.unsupported", # sun.misc.Unsafe
      ],
      "requiresConcealed" : {
        "jdk.internal.vm.ci" : [
          "jdk.vm.ci.meta",
          "jdk.vm.ci.services",
          "jdk.vm.ci.code",
          "jdk.vm.ci.code.stack",
        ],
        "java.base" : [
          "jdk.internal.module",
        ],
      },
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR", "TRUFFLE_LIBGRAAL_PROCESSOR"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "17+",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.compiler" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
      ],
      "requires" : [
        "java.logging",
        "jdk.management",
        "jdk.unsupported", # sun.misc.Unsafe
      ],
      "requiresConcealed" : {
        "jdk.internal.vm.ci" : [
          "jdk.vm.ci.meta",
          "jdk.vm.ci.code",
        ],
      },
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "17+",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.host" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.exception",
        "truffle:TRUFFLE_ASM_9.5",
      ],
      "requires" : [
        "java.sql",
        "jdk.unsupported", # sun.misc.Unsafe
      ],
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "17+",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.modularized.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.modularized.test.separate.module.test",
        "TRUFFLE_API",
        "sdk:POLYGLOT",
        "mx:JUNIT",
      ],
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "17+",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "API,Truffle,Test",
      "jacoco" : "exclude",
    },

    "com.oracle.truffle.api.modularized.test.separate.module.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "javaCompliance" : "17+",
      "workingSets" : "API,Truffle,Test",
      "jacoco" : "exclude",
    },

    "com.oracle.truffle.api.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "TRUFFLE_TCK_TESTS",
        "TRUFFLE_API",
        "TRUFFLE_SL",
        "VISUALVM-LIB-JFLUID-HEAP",
        "GUAVA",
        "JIMFS",
        "mx:JUNIT",
      ],
      "requires" : [
        "java.desktop",
        "java.logging",
        "java.sql",
        "jdk.management",
        "jdk.unsupported", # sun.misc.Unsafe
      ],
      "requiresConcealed" : {
        "java.base" : [
          "jdk.internal.loader"
        ],
      },
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
      "workingSets" : "API,Truffle",
    },

    "com.oracle.truffle.api.dsl" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.api"],
      "requires" : [
        "java.logging",
        "jdk.unsupported", # sun.misc.Unsafe
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "17+",
      "workingSets" : "API,Truffle,Codegen",
    },

    "com.oracle.truffle.api.library.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.polyglot",
        "com.oracle.truffle.api.test",
        "com.oracle.truffle.api.dsl",
        "com.oracle.truffle.api.library",
        "mx:JUNIT",
      ],
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
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
        "jdk.management"
      ],
      "checkstyle" : "com.oracle.truffle.dsl.processor",
      "javaCompliance" : "17+",
      "checkstyleVersion" : "10.7.0",
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
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
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
        "truffle:TRUFFLE_ASM_9.5",
      ],
      "requires" : [
        "jdk.unsupported", # sun.misc.Unsafe
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "17+",
      "javadocType" : "api",
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
      "javaCompliance" : "17+",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "API,Truffle,Test",
      "testProject" : True,
    },

    "com.oracle.truffle.api.profiles" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.api.dsl"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
      "workingSets" : "Truffle,Tools",
    },
    "com.oracle.truffle.tck.common" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "sdk:POLYGLOT_TCK"
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
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

    "com.oracle.truffle.nfi.backend.panama" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "javaPreviewNeeded" : "21+",
      "dependencies" : [
        "com.oracle.truffle.nfi.backend.spi",
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "21+",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle",
    },

    "com.oracle.truffle.nfi.backend.spi" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "TRUFFLE_API",
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "17+",
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
      "toolchain" : "sdk:LLVM_NINJA_TOOLCHAIN",
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
        "linux-musl" : {
          "<others>" : {
            "cflags" : ["-g", "-Wall", "-Werror"],
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
      "javaCompliance" : "17+",
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
      "toolchain" : "sdk:LLVM_NINJA_TOOLCHAIN",
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
      "javaCompliance" : "17+",
      "checkstyle" : "com.oracle.truffle.api",
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle,SimpleLanguage",
    },

    "com.oracle.truffle.sl.launcher" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "sdk:POLYGLOT",
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
      "checkstyleVersion" : "10.7.0",
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
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
      "license": "GPLv2-CPE",
      "jacoco" : "exclude",
    },

    "org.graalvm.shadowed.com.ibm.icu" : {
      # shaded ICU4J + ICU4J-CHARSET
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "javaCompliance" : "17+",
      "spotbugs" : "false",
      "requires" : [
        "java.logging",
        "java.xml",
        "java.desktop",
      ],
      "dependencies" : [
      ],
      "shadedDependencies" : [
        "truffle:ICU4J",
        "truffle:ICU4J-CHARSET",
      ],
      "class" : "ShadedLibraryProject",
      "shade" : {
        "packages" : {
          "com.ibm.icu" : "org.graalvm.shadowed.com.ibm.icu",
        },
        "include" : [
          "com/ibm/icu/ICUConfig.properties",
          "com/ibm/icu/impl/data/**",
          "com/ibm/icu/impl/duration/impl/data/**",
          "LICENSE",
        ],
        "exclude" : [
          "META-INF/MANIFEST.MF",
          "META-INF/services/*", # deliberately excluding java.nio.charset.spi.CharsetProvider
          "**/*.html",
        ],
        "patch" : {
          "com/ibm/icu/ICUConfig.properties" : {
            "com\\.ibm\\.icu\\." : "org.graalvm.shadowed.com.ibm.icu.",
          },
          "com/ibm/icu/util/VTimeZone.java" : {
            # confuses the codesnippet doclet
            " (BEGIN|END):(\\w+)\\b" : " \'\\1:\\2\'",
          },
          "com/ibm/icu/impl/ICUBinary.java" : {
            # we want to make this code unreachable in native image builds
            "addDataFilesFromPath\\(dataPath, icuDataFiles\\);" : "// \\g<0>",
          },
          "com/ibm/icu/impl/ICUData.java" : {
            # [GR-47166] we load an absolute path from ICUData.class, to
            # workaround an issue we don't understand when this is on the
            # module path
            "ICU_DATA_PATH = \"(?!/)" : "\\g<0>/",
            "loader.getResourceAsStream\\(resourceName\\)": "(loader == ICUData.class.getClassLoader() ? ICUData.class.getResourceAsStream(resourceName) : \\g<0>)",
          },
          "com/ibm/icu/impl/URLHandler.java" : {
            # we want to make this code unreachable in native image builds
            "protected static URLHandler getDefault.*" : "\\g<0>\nif (Boolean.TRUE) {\nreturn null;\n}",
          },
        },
      },
      "description" : "ICU4J shaded library.",
      "allowsJavadocWarnings": True,
      "javac.lint.overrides" : 'none',
      "jacoco" : "exclude",
    },

    "com.oracle.truffle.runtime.attach" : {
      "subDir" : "src",
      "native" : "shared_lib",
      "deliverable" : "truffleattach",
      "use_jdk_headers" : True,
      "buildDependencies" : [
      ],
      "os_arch" : {
        "windows" : {
          "<others>" : {
            "cflags" : []
          }
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

    "com.oracle.truffle.libgraal.processor" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "truffle:ANTLR4"
      ],
      "requires" : [
        "java.compiler",
        "jdk.management"
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "17+",
    },

    "org.graalvm.shadowed.org.json" : {
      # shaded org.json/json
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "javaCompliance" : "17+",
      "forceJavac" : "true",
      "spotbugs" : "false",
      "requires" : [
      ],
      "dependencies" : [
      ],
      "shadedDependencies" : [
        "truffle:JSON",
      ],
      "class" : "ShadedLibraryProject",
      "shade" : {
        "packages" : {
          "org.json" : "org.graalvm.shadowed.org.json",
        },
        "exclude" : [
          "META-INF/MANIFEST.MF",
          "META-INF/maven/**",
        ],
        "patch" : {
        },
      },
      "description" : "JSON shaded library.",
      "allowsJavadocWarnings": True,
      "javac.lint.overrides" : 'none',
      "jacoco" : "exclude",
    },

    "org.graalvm.shadowed.org.antlr.v4.runtime" : {
      # shaded ANTLR4 (org.antlr:antlr4-runtime) library
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "javaCompliance" : "17+",
      "forceJavac" : "true",
      "spotbugs" : "false",
      "shadedDependencies" : [
        "truffle:ANTLR4",
      ],
      "class" : "ShadedLibraryProject",
      "shade" : {
        "packages" : {
          "org.antlr.v4.runtime" : "org.graalvm.shadowed.org.antlr.v4.runtime",
        },
        "exclude" : [
          "META-INF/MANIFEST.MF",
          "META-INF/maven/**",
          "main/dot/org/antlr/v4/runtime/atn/images/*"
        ],
      },
      "description" : "ANTLR4 shaded library.",
      "allowsJavadocWarnings": True,
      "javac.lint.overrides" : 'none',
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

    "TRUFFLE_MODULARIZED_TEST" : {
      # This distribution defines a module.
      "moduleInfo" : {
        "name" : "com.oracle.trufflemodtest",
        "exports" : [
          # Unqualified exports
          "com.oracle.truffle.api.modularized.test",
        ],
      },
      "subDir" : "src",
      "javaCompliance" : "17+",
      "dependencies" : [
        "com.oracle.truffle.api.modularized.test",
      ],
      "distDependencies" : [
        "sdk:POLYGLOT",
        "TRUFFLE_API",
        "TRUFFLE_RUNTIME",
        "TRUFFLE_SL",
        "TRUFFLE_MODULARIZED_TEST_SEPARATE_MODULE_TEST",
      ],
      "exclude" : [
        "mx:JUNIT",
      ],
      "useModulePath": True,
      "description" : "Module with JUnit tests for testing Truffle API in modular applications.",
      "maven": False,
    },

    "TRUFFLE_MODULARIZED_TEST_SEPARATE_MODULE_TEST" : {
      # This distribution defines a module.
      "moduleInfo" : {
        "name" : "com.oracle.trufflemodtestseparate",
        "exports" : [
          # Qualified exports
          "com.oracle.truffle.api.modularized.test.separate.module.test to com.oracle.trufflemodtest",
        ],
      },
      "subDir" : "src",
      "javaCompliance" : "17+",
      "dependencies" : [
        "com.oracle.truffle.api.modularized.test.separate.module.test",
      ],
      "useModulePath": True,
      "description" : "Separate test module for testing Truffle API in modular applications.",
      "maven": False,
    },

    "TRUFFLE_COMPILER" : {
      # This distribution defines a module.
      "moduleInfo" : {
        "name" : "org.graalvm.truffle.compiler",
        "requires" : [
          "jdk.unsupported", # sun.misc.Unsafe
          "java.logging",
          "java.management",
          "static jdk.internal.vm.ci",  # JVMCI module is not on the boot layer if not enabled
        ],
        "exports" : [
          # Qualified exports
          "com.oracle.truffle.compiler to org.graalvm.truffle.runtime, jdk.internal.vm.compiler, org.graalvm.nativeimage.builder, com.oracle.truffle.enterprise, com.oracle.graal.graal_enterprise, org.graalvm.truffle.runtime.svm, com.oracle.truffle.enterprise.svm",
          "com.oracle.truffle.compiler.hotspot to org.graalvm.truffle.runtime, jdk.internal.vm.compiler",
          "com.oracle.truffle.compiler.hotspot.libgraal to org.graalvm.truffle.runtime, jdk.internal.vm.compiler"
        ],
        "uses" : [
        ],
        "requiresConcealed" : {
          "jdk.internal.vm.ci" : [
            "jdk.vm.ci.meta",
            "jdk.vm.ci.code",
          ],
        },
      },

      "subDir" : "src",
      "javaCompliance" : "17+",
      "dependencies" : [
        "com.oracle.truffle.compiler",
      ],
      "distDependencies" : [],
      "description" : "Truffle compiler API.",
      "maven": {
          "tag": ["default", "public"],
      },
    },

    "TRUFFLE_RUNTIME" : {
      # This distribution defines a module.
      "moduleInfo" : {
        "name" : "org.graalvm.truffle.runtime",
        "requires" : [
          "jdk.unsupported", # sun.misc.Unsafe
          "java.logging",
          "java.management",
          "static jdk.internal.vm.ci",  # JVMCI module is not on the boot layer if not enabled
        ],
        "exports" : [
          # Qualified exports
          "* to org.graalvm.truffle, com.oracle.truffle.enterprise, org.graalvm.truffle.runtime.svm, com.oracle.truffle.enterprise.svm",
          # necessary to instantiate access truffle compiler from the runtime during host compilation
          "com.oracle.truffle.runtime.hotspot to jdk.internal.vm.compiler",
        ],
        "uses" : [
          "com.oracle.truffle.api.impl.TruffleLocator",
          "com.oracle.truffle.api.object.LayoutFactory",
          "com.oracle.truffle.runtime.LoopNodeFactory",
          "com.oracle.truffle.runtime.TruffleTypes",
          "com.oracle.truffle.runtime.EngineCacheSupport",
          "com.oracle.truffle.runtime.jfr.EventFactory.Provider",
          "com.oracle.truffle.runtime.FloodControlHandler",
          "org.graalvm.home.HomeFinder",
        ],
      },
      "requiresConcealed" : {
        "jdk.internal.vm.ci" : [
          "jdk.vm.ci.meta",
          "jdk.vm.ci.code",
          "jdk.vm.ci.code.stack",
          "jdk.vm.ci.services",
        ],
      },
      "subDir" : "src",
      "javaCompliance" : "17+",
      "dependencies" : [
        "com.oracle.truffle.runtime",
        "TRUFFLE_RUNTIME_ATTACH_RESOURCES",
      ],
      "distDependencies" : [
        "sdk:JNIUTILS",
        "TRUFFLE_API",
        "TRUFFLE_COMPILER",
      ],
      "description" : "Truffle runtime distribution.",
      "useModulePath": True,
      "maven": {
          "artifactId": "truffle-runtime",
          "tag": ["default", "public"],
      },
    },

    "TRUFFLE_API_VERSION": {
      "type": "dir",
      "platformDependent": False,
      "layout": {
        "META-INF/graalvm/org.graalvm.truffle/version": "dependency:sdk:VERSION/version",
      },
      "description": "Truffle API version.",
      "maven": False,
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
          "com.oracle.truffle.api.provider",
          "com.oracle.truffle.api.instrumentation.provider",
          "com.oracle.truffle.api.library.provider",

          # Qualified exports
          "com.oracle.truffle.api.impl to org.graalvm.locator, org.graalvm.truffle.runtime, com.oracle.truffle.enterprise, org.graalvm.truffle.runtime.svm, com.oracle.truffle.enterprise.svm",
          "com.oracle.truffle.object to com.oracle.truffle.enterprise, org.graalvm.truffle.runtime, com.oracle.truffle.enterprise, org.graalvm.truffle.runtime.svm, com.oracle.truffle.enterprise.svm",
        ],
        "opens" : [
          "com.oracle.truffle.polyglot to org.graalvm.truffle.runtime",
        ],
        "uses" : [
          "com.oracle.truffle.api.TruffleRuntimeAccess",
          "java.nio.file.spi.FileTypeDetector",
          "com.oracle.truffle.api.impl.TruffleLocator",
          "com.oracle.truffle.api.provider.TruffleLanguageProvider",
          "com.oracle.truffle.api.provider.InternalResourceProvider",
          "com.oracle.truffle.api.library.provider.DefaultExportProvider",
          "com.oracle.truffle.api.library.provider.EagerExportProvider",
          "com.oracle.truffle.api.instrumentation.provider.TruffleInstrumentProvider",
          "com.oracle.truffle.api.library.DefaultExportProvider", # Deprecated
          "com.oracle.truffle.api.library.EagerExportProvider", # Deprecated
          "com.oracle.truffle.api.TruffleLanguage.Provider", # Deprecated
          "com.oracle.truffle.api.instrumentation.TruffleInstrument.Provider", # Deprecated
        ],
      },

      "moduleInfo:closed" : {
        # This is the module descriptor for the Truffle API modular jar deployed via maven.
        # It exports all the Truffle API packages to the language that get loaded through Truffle at runtime.
        "exports" : [
          # Unqualified exports
          "com.oracle.truffle.api.provider",
          "com.oracle.truffle.api.instrumentation.provider",
          "com.oracle.truffle.api.library.provider",
          # Qualified exports
          "com.oracle.truffle.api* to org.graalvm.locator, com.oracle.truffle.enterprise, org.graalvm.truffle.runtime, org.graalvm.truffle.runtime.svm, com.oracle.truffle.enterprise.svm",
          "com.oracle.truffle.api.impl to org.graalvm.locator, org.graalvm.truffle.runtime, com.oracle.truffle.enterprise, org.graalvm.truffle.runtime.svm,com.oracle.truffle.enterprise.svm",
          "com.oracle.truffle.object to org.graalvm.truffle.runtime, com.oracle.truffle.enterprise, org.graalvm.truffle.runtime.svm, com.oracle.truffle.enterprise.svm",
        ],
      },
      "subDir" : "src",
      "javaCompliance" : "17+",
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
        "TRUFFLE_API_VERSION",
      ],
      "distDependencies" : [
        "sdk:POLYGLOT",
      ],
      "description" : "Truffle is a multi-language framework for executing dynamic languages\nthat achieves high performance when combined with Graal.",
      "javadocType": "api",
      "maven": {
          "tag": ["default", "public"],
      },
      "useModulePath": True,
      # We do no longer deploy a closed module to graalvm because there are known bugs
      # when a JDK boot module exports itself at runtime to a language at runtime.
      # with Truffle unchained we want to use truffle always from the module path
      # and deployement of Truffle the JDK is only there fore legacy support.
      # See GR-47669
      #"graalvm" : {
      # Deploy the modular jar specified by "moduleInfo.closed"
      #"moduleInfo" : "closed",
      #}
    },

    "TRUFFLE_RUNTIME_ATTACH_RESOURCES" : {
      "type" : "dir",
      "platformDependent" : True,
      "hashEntry" :  "META-INF/resources/engine/libtruffleattach/<os>/<arch>/sha256",
      "fileListEntry" : "META-INF/resources/engine/libtruffleattach/<os>/<arch>/files",
      "platforms" : [
          "linux-amd64",
          "linux-aarch64",
          "darwin-amd64",
          "darwin-aarch64",
          "windows-amd64",
          "windows-aarch64",
      ],
      "layout" : {
        "META-INF/resources/engine/libtruffleattach/<os>/<arch>/bin/" : "dependency:com.oracle.truffle.runtime.attach",
      },
      "description" : "Contains a library to provide access for the Truffle runtime to JVMCI.",
      "maven": False,
    },

    "TRUFFLE_NFI" : {
      # This distribution defines a module.
      "moduleInfo" : {
        "name" : "com.oracle.truffle.truffle_nfi",
        "exports" : [
          "com.oracle.truffle.nfi.api",
          "com.oracle.truffle.nfi.backend.spi",
          "com.oracle.truffle.nfi.backend.spi.types",
          "com.oracle.truffle.nfi.backend.spi.util",
        ],
      },
      "subDir" : "src",
      "javaCompliance" : "17+",
      "dependencies" : [
        "com.oracle.truffle.nfi",
      ],
      "distDependencies" : [
        "TRUFFLE_API",
      ],
      "description" : """Native function interface for the Truffle framework.""",
      "allowsJavadocWarnings": True,
      "maven": {
          "tag": ["default", "public"],
      },
    },

    "TRUFFLE_NFI_LIBFFI" : {
      # This distribution defines a module.
      "moduleInfo" : {
        "name" : "com.oracle.truffle.truffle_nfi_libffi",
        "opens" : [
          "com.oracle.truffle.nfi.backend.libffi to org.graalvm.truffle.runtime.svm",
        ],
      },
      "subDir" : "src",
      "javaCompliance" : "17+",
      "dependencies" : [
        "com.oracle.truffle.nfi.backend.libffi",
        "TRUFFLE_NFI_RESOURCES",
      ],
      "distDependencies" : [
        "TRUFFLE_NFI",
      ],
      "description" : """Implementation of the Truffle NFI using libffi.""",
      "allowsJavadocWarnings": True,
      "maven": {
          "tag": ["default", "public"],
      },
    },

    "TRUFFLE_NFI_PANAMA" : {
      # This distribution defines a module.
      "moduleInfo" : {
        "name" : "com.oracle.truffle.truffle_nfi_panama",
      },
      "subDir" : "src",
      "javaCompliance" : "21+",
      "dependencies" : [
        "com.oracle.truffle.nfi.backend.panama",
      ],
      "distDependencies" : [
        "TRUFFLE_NFI",
      ],
      "description" : """Implementation of the Truffle NFI using CLinker from project panama.""",
      "allowsJavadocWarnings": True,
      "maven": {
        "tag": ["default", "public"],
      },
      "noMavenJavadoc": True,  # the maven deploy job refuses to build javadoc if javaCompliance is higher than 17
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
      "maven": {
          "tag": ["default", "public"],
      },
    },

    "TRUFFLE_NFI_RESOURCES" : {
      "type" : "dir",
      "platformDependent" : True,
      "hashEntry" :  "META-INF/resources/nfi-native/libnfi/<os>/<arch>/sha256",
      "fileListEntry" : "META-INF/resources/nfi-native/libnfi/<os>/<arch>/files",
      "platforms" : [
          "linux-amd64",
          "linux-aarch64",
          "darwin-amd64",
          "darwin-aarch64",
          "windows-amd64",
          "windows-aarch64",
      ],
      "layout" : {
        "META-INF/resources/nfi-native/libnfi/<os>/<arch>/bin/" : "dependency:com.oracle.truffle.nfi.native",
      },
      "description" : "Contains the native library needed by the libffi NFI backend.",
      "maven": False,
    },

    "TRUFFLE_TCK" : {
      "subDir" : "src",
      "javaCompliance" : "17+",
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
      "maven": {
          "tag": ["default", "public"],
      },
    },

    "TRUFFLE_TCK_COMMON" : {
      "subDir" : "src",
      "javaCompliance" : "17+",
      "dependencies" : [
        "com.oracle.truffle.tck.common"
      ],
      "distDependencies" : [
        "sdk:POLYGLOT_TCK",
      ],
      "description" : "Common types for TCK Tests and Instruments.",
      "allowsJavadocWarnings": True,
      "maven": {
          "tag": ["default", "public"],
      },
    },

    "TRUFFLE_TCK_TESTS" : {
      "subDir" : "src",
      "javaCompliance" : "17+",
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
      "maven": {
          "tag": ["default", "public"],
      },
    },

    "TRUFFLE_TCK_TESTS_LANGUAGE" : {
      "subDir" : "src",
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
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
      "maven": {
          "tag": ["default", "public"],
      },
    },

    "TRUFFLE_DSL_PROCESSOR" : {
      "subDir" : "src",
      "dependencies" : [
        "com.oracle.truffle.dsl.processor",
      ],
      "distDependencies" : [],
      "description" : "The Truffle DSL Processor generates source code for nodes that are declared using the DSL.",
      "allowsJavadocWarnings": True,
      "maven": {
          "tag": ["default", "public"],
      },
    },

    "TRUFFLE_LIBGRAAL_PROCESSOR" : {
      "subDir" : "src",
      "dependencies" : ["com.oracle.truffle.libgraal.processor"],
      "distDependencies" : [],
      "description" : "The Truffle libgraal processor is shared across Truffle and the compiler to generate code for the compiler bridge.",
      "allowsJavadocWarnings": True,
      "maven": {
          "tag": ["default", "public"],
      },
    },

    "TRUFFLE_SL" : {
      "subDir" : "src",
      "moduleInfo" : {
        "name" : "org.graalvm.sl",
      },
      "javaCompliance" : "17+",
      "dependencies" : [
        "com.oracle.truffle.sl",
      ],
      "exclude" : [
        "truffle:ANTLR4",
      ],
      "distDependencies" : [
          "TRUFFLE_API",
      ],
      "useModulePath": True,
      "description" : "Truffle SL is an example language implemented using the Truffle API.",
      "allowsJavadocWarnings": True,
      "maven": {
          "tag": ["default", "public"],
      },
    },

    "TRUFFLE_SL_LAUNCHER" : {
      "subDir" : "src",
      "moduleInfo" : {
        "name" : "org.graalvm.sl_launcher",
      },
      "javaCompliance" : "17+",
      "dependencies" : [
        "com.oracle.truffle.sl.launcher",
      ],
      "distDependencies" : [
          "sdk:POLYGLOT",
      ],
      "useModulePath": True,
      "description" : "Truffle SL launchers using the polyglot API.",
      "allowsJavadocWarnings": True,
      "maven": {
          "tag": ["default", "public"],
      },
    },

    "TRUFFLE_SL_TEST" : {
      "subDir" : "src",
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
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
       "javaCompliance" : "17+",
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
       "exclude" : [
         "mx:HAMCREST",
         "mx:JUNIT",
         "mx:JMH_1_21",
         "VISUALVM-LIB-JFLUID-HEAP",
         "JIMFS",
         "GUAVA"
       ],
       "distDependencies" : [
         "TRUFFLE_API",
         "TRUFFLE_RUNTIME",
         "TRUFFLE_SL",
         "TRUFFLE_TCK_COMMON",
         "TRUFFLE_TCK_TESTS",
         "TRUFFLE_NFI",
         "TRUFFLE_NFI_LIBFFI",
         "TRUFFLE_DSL_PROCESSOR",
         "TRUFFLE_TEST_NATIVE",
         "TRUFFLE_TCK",
         "TRUFFLE_TCK_INSTRUMENTATION",
      ],
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

    "TRUFFLE_ANTLR4_GRAALVM_SUPPORT" : {
      "native" : True,
      "description" : "Truffle support distribution for ANTLR4",
      "layout" : {
        "native-image.properties" : "file:mx.truffle/language-antlr4.properties",
      },
      "maven" : False,
    },

    "TRUFFLE_JSON_GRAALVM_SUPPORT" : {
      "native" : True,
      "description" : "Truffle support distribution for Truffle JSON",
      "layout" : {
        "native-image.properties" : "file:mx.truffle/language-json.properties",
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

    "TRUFFLE_ICU4J" : {
      # shaded ICU4J + ICU4J-CHARSET
      # This distribution defines a module.
      "moduleInfo" : {
        "name" : "org.graalvm.shadowed.icu4j",
        "requires" : [
        ],
        "exports" : [
          # Qualified exports.
          "org.graalvm.shadowed.com.ibm.icu.lang to com.oracle.truffle.regex, org.graalvm.js, org.graalvm.py",
          "org.graalvm.shadowed.com.ibm.icu.math to org.graalvm.js, org.graalvm.py",
          "org.graalvm.shadowed.com.ibm.icu.number to org.graalvm.js, org.graalvm.py",
          "org.graalvm.shadowed.com.ibm.icu.text to org.graalvm.js, org.graalvm.py",
          "org.graalvm.shadowed.com.ibm.icu.util to org.graalvm.js, org.graalvm.py",
          "org.graalvm.shadowed.com.ibm.icu.charset to org.graalvm.js, org.graalvm.py",
          "org.graalvm.shadowed.com.ibm.icu.impl to org.graalvm.js, org.graalvm.py",
        ],
      },
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "javaCompliance" : "17+",
      "spotbugs" : "false",
      "dependencies" : [
        "org.graalvm.shadowed.com.ibm.icu",
      ],
      "distDependencies" : [
      ],
      "exclude" : [
      ],
      "description" : "ICU4J shaded module.",
      "allowsJavadocWarnings" : True,
      "license" : ["ICU"],
      "maven" : {
        "groupId" : "org.graalvm.shadowed",
        "artifactId" : "icu4j",
        "tag": ["default", "public"],
      },
    },

    "TRUFFLE_JSON" : {
      # shaded JSON
      # This distribution defines a module.
      "moduleInfo" : {
        "name" : "org.graalvm.shadowed.org.json",
        "requires" : [
        ],
        "exports" : [
          "org.graalvm.shadowed.org.json",
        ],
      },
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "javaCompliance" : "17+",
      "spotbugs" : "false",
      "dependencies" : [
        "org.graalvm.shadowed.org.json",
      ],
      "distDependencies" : [
      ],
      "exclude" : [
      ],
      "description" : "JSON shaded module.",
      "allowsJavadocWarnings" : True,
      "maven" : {
        "groupId" : "org.graalvm.shadowed",
        "artifactId" : "json",
        "tag": ["default", "public"],
      },
    },

    "TRUFFLE_ANTLR4" : {
      # shaded ANTLR4 (org.antlr:antlr4-runtime) library module
      "moduleInfo" : {
        "name" : "org.graalvm.shadowed.antlr4",
        "exports" : [
          "org.graalvm.shadowed.org.antlr.v4.runtime*",
        ],
      },
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "javaCompliance" : "17+",
      "spotbugs" : "false",
      "dependencies" : [
        "org.graalvm.shadowed.org.antlr.v4.runtime",
      ],
      "description" : "ANTLR4 shaded module.",
      "allowsJavadocWarnings" : True,
      "license" : "BSD-new",
      "maven" : {
        "groupId" : "org.graalvm.shadowed",
        "artifactId" : "antlr4",
        "tag": ["default", "public"],
      },
    },
  },
}
