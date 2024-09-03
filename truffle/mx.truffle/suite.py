#
# Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
  "mxversion": "7.28.0",
  "name" : "truffle",
  "version" : "24.2.0",
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
      "version" : "3.4.6",
      "urls" : [
        "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/libffi-{version}.tar.gz",
        "https://github.com/libffi/libffi/releases/download/v{version}/libffi-{version}.tar.gz",
      ],
      "digest" : "sha512:033d2600e879b83c6bce0eb80f69c5f32aa775bf2e962c9d39fbd21226fa19d1e79173d8eaa0d0157014d54509ea73315ad86842356fc3a303c0831c94c6ab39",
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

    "JCODINGS_1.0.58": {
      "digest" : "sha512:625210aa07d1e08bf2f5fdc9da6c491a4e5a56e7db297cba1aa73636670ac1d62f3fd763716ef6ede862456b17169272ed9c8461d07100f95262163dc9c18ef8",
      "sourceDigest" : "sha512:d0f883f658310f7ad091aea08df28f1f5fe12080d6cb266cd91aec7e34cda1d57736d32618e8632b329854367d6e4d5fc91b5eb8ac9b823b26113fae3f75f50c",
      "maven": {
        "groupId": "org.jruby.jcodings",
        "artifactId": "jcodings",
        "version": "1.0.58",
      },
      "license": ["MIT"],
    },

    "ASM_9.5": {
      "digest": "sha512:9e65f2983783725bae196ca939b45246958731246df1c495089c8ea5ce646de77c4e01a5a9ba10642016bb3258e1727e9ebcece6e74d9e3c32f528025d76b955",
      "sourceDigest": "sha512:64262b68c1acd473a49be3c8a89190200be66808034c55fb23ed08d8a977111c234b6dc77b6ca95310e1f1cbc43437cdc863aadb6217976cc1720d63ef01e937",
      "maven": {
        "groupId": "org.ow2.asm",
        "artifactId": "asm",
        "version": "9.5",
      },
      "license": "BSD-new",
    },

    "ASM_TREE_9.5": {
      "digest": "sha512:816de8f84c216a7bd97b2458bde64a4b99a039b7b59fbd1ef52edf8bf869edabb93967736fe0c61e8eb3e1520e0cefe69ba59cda12df30f9f85db75fb6c064f3",
      "sourceDigest": "sha512:a107043c05398091e3f3c614270c626be8ea5a1a547e30dc5709ef92069c8c8baa315c385a68f244c3a37bc148c3aeeec26adc8c00afc2a03a1d21a40e076a4c",
      "maven": {
        "groupId": "org.ow2.asm",
        "artifactId": "asm-tree",
        "version": "9.5",
      },
      "dependencies" : ["ASM_9.5"],
      "license": "BSD-new",
    },

    "ASM_COMMONS_9.5": {
      "digest": "sha512:6121a9d033627a33839d9bd264fce4a531b6a3f974720adc6150816f0316d1522c3d8caf7df039fdd46cb66bedd788e50f709d4a1f75d3525912ad7a4d87f7da",
      "sourceDigest": "sha512:688d56a1b4fb6f7d86b79b7493a848851892910d00067a0c5effdaf7132266ec7a1ba57a8248c2fd6c0ebdef18a4918908a36e85f5927b9acb55448047a1e333",
      "maven": {
        "groupId": "org.ow2.asm",
        "artifactId": "asm-commons",
        "version": "9.5",
      },
      "dependencies" : ["ASM_9.5", "ASM_TREE_9.5"],
      "license": "BSD-new",
    },

    "ICU4J" : {
      "moduleName" : "com.ibm.icu",
      "digest" : "sha512:08720318b2d175ad8379306caacea7da181e51ce9e7410318dae11008ab4a8d6d7b1723136a1fd17b02b11efd24ce87cbe24413c1d5f8d7cb0d00c9d32c1b267",
      "sourceDigest" : "sha512:0cc7bda1e4396249ff33cb692fe102a20c499fce53c79035a26ed3815760554246e5fd9bd5bf6f154cf2c823c0502fc573ce2987924c263cd371aaf3dadc7a8b",
      "maven" : {
        "groupId" : "com.ibm.icu",
        "artifactId" : "icu4j",
        "version" : "74.1",
      },
    },
    "ICU4J-CHARSET" : {
      "moduleName" : "com.ibm.icu.charset",
      "digest" : "sha512:15457b170448676d299b20d6bb26909735645f6e3fe28efb6036ad4b94a91ee58bfbeb427adc9264fe6bfa23972b3dbd2df9c85f8285290ed2b035da6e51326b",
      "sourceDigest" : "sha512:0f90efa00cb7c98af87f9fbe6a63f5d465d4cd9da9666d2cd04d7b3e588b1b9c3835403f5c9f2863d5cd93122ad004e888fb06da3bdd2b5f37e830ae699d2576",
      "maven" : {
        "groupId" : "com.ibm.icu",
        "artifactId" : "icu4j-charset",
        "version" : "74.1",
      },
    },

    "XZ-1.9" : {
      "digest" : "sha512:a4362db234d4e83683e90f5baf90c82107450cc4404acab96e3fab14b8a3d4588a19722171d32f27d18463682a6994cad9af0b1065c954e3a77ea7bdcf586bac",
      "sourceDigest" : "sha512:5625f7392d1958466507f0530585c2c40c5b301981ab64115f489dc4599e3364acd7f00bfdc69aa3124d4641a562923dc3ee038faee5a52dd3cbe29a1a35e5ab",
      "maven" : {
        "groupId" : "org.tukaani",
        "artifactId" : "xz",
        "version" : "1.9",
      },
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
      "digest": "sha512:0a9df2cd8e0073545582cec28932ee91c75dcd61b06cd0a3bc9ab46527d149379d929e866a54758e6c110569359bec2b45b50a97b046356014e8302cd84544eb",
      "sourceDigest": "sha512:407414fac32f435bdb0f3ac8fdd367f87044867d62a17818a4d98070a4d292525470366a68348bf99b596a38460cd1ced6fe38b97b1aae67642e6c231747bb8f",
      "maven": {
        "groupId": "com.google.jimfs",
        "artifactId": "jimfs",
        "version": "1.2",
      },
    },

    "GUAVA": { # JIMFS dependency
      "moduleName": "com.google.common",
      "digest": "sha512:c8d8aa38e6fb04c409c37922efcbbe182f65156a853f691d8381d56eea208adf22f7a28873bb7895210e41857dd4411aaf952682a2692051220e281910d0798f",
      "sourceDigest": "sha512:52ab5b63592f6137f98cf4a1b754f34550cc3636913c07407775b52e7249386c7710ffb9b4f26f173b2f8ad67d6575b3d07327492f7687032261815b0a4f4b7b",
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
      "javaCompliance" : "17+",
      "checkstyleVersion" : "10.7.0",
      "workingSets" : "API,Truffle",
      "graalCompilerSourceEdition": "ignore",
    },

    "com.oracle.truffle.api.jdk22" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
      ],
      "requires" : [
      ],
      "overlayTarget" : "com.oracle.truffle.api",
      "checkPackagePrefix" : "false",
      "multiReleaseJarVersion" : "22",
      "javaCompliance" : "22+",
      "forceJavac": True,
      "checkstyle" : "com.oracle.truffle.api",
      "workingSets" : "API,Truffle",
      "graalCompilerSourceEdition": "ignore",
      # disable SpotBugs as long JDK 22 is unsupported [GR-49566]
      "spotbugs" : "false",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
          "jdk.internal.access",
        ],
      },
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR", "TRUFFLE_LIBGRAAL_PROCESSOR"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "17+",
      "workingSets" : "API,Truffle",
      "graalCompilerSourceEdition": "ignore",
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
        "com.oracle.truffle.api.impl.asm",
      ],
      "requires" : [
        "java.sql",
        "jdk.unsupported", # sun.misc.Unsafe
      ],
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "17+",
      "workingSets" : "API,Truffle",
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
    },

    "com.oracle.truffle.api.modularized.test.separate.module.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "javaCompliance" : "17+",
      "workingSets" : "API,Truffle,Test",
      "jacoco" : "exclude",
      "graalCompilerSourceEdition": "ignore",
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
        "jdk.attach", # required by SubprocessTestUtils
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
    },

    "com.oracle.truffle.api.strings" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api.profiles",
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
      "graalCompilerSourceEdition": "ignore",
    },

    "com.oracle.truffle.api.strings.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "TRUFFLE_API",
        "mx:JUNIT",
        "mx:JMH_1_21",
        "TRUFFLE_JCODINGS",
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
      "graalCompilerSourceEdition": "ignore",
    },


    "com.oracle.truffle.api.staticobject" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.api",
        "com.oracle.truffle.api.impl.asm",
      ],
      "requires" : [
        "jdk.unsupported", # sun.misc.Unsafe
      ],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "17+",
      "javadocType" : "api",
      "workingSets" : "API,Truffle",
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
    },

    "com.oracle.truffle.api.profiles" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.truffle.api.dsl"],
      "checkstyle" : "com.oracle.truffle.api",
      "javaCompliance" : "17+",
      "workingSets" : "API,Truffle",
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
    },

    "com.oracle.truffle.nfi.backend.panama" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.nfi.backend.spi",
      ],
      "checkstyle" : "com.oracle.truffle.api",
      # GR-51699
      "javaCompliance" : "22+",
      "forceJavac": True,
      "annotationProcessors" : ["TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle",
      # disable SpotBugs and Jacoco as long as JDK 22 is unsupported [GR-49566]
      "spotbugs" : "false",
      "jacoco" : "exclude",
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
    },

    "libffi" : {
      "class" : "LibffiBuilderProject",
      "dependencies" : [
        "LIBFFI_SOURCES",
      ],
      "graalCompilerSourceEdition": "ignore",
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
            "cflags" : ["-g", "-Wall", "-Werror", "-D_GNU_SOURCE", "-fvisibility=hidden"],
            "ldlibs" : ["-ldl"],
          },
        },
        "linux-musl" : {
          "<others>" : {
            "cflags" : ["-g", "-Wall", "-Werror", "-fvisibility=hidden"],
            "ldlibs" : ["-ldl"],
          },
        },
        "<others>" : {
          "<others>" : {
            "cflags" : ["-g", "-Wall", "-Werror", "-fvisibility=hidden"],
            "ldlibs" : ["-ldl"],
          },
        },
      },
      "graalCompilerSourceEdition": "ignore",
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
        "sdk:POLYGLOT",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
    },

    "org.graalvm.shadowed.com.ibm.icu" : {
      # shaded ICU4J + ICU4J-CHARSET
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "javaCompliance" : "17+",
      "spotbugsIgnoresGenerated" : True,
      "requires" : [
        "java.logging",
        "java.xml",
        "java.desktop",
      ],
      "dependencies" : [
        "sdk:NATIVEIMAGE",
        "truffle:TRUFFLE_XZ",
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
          "**/pom.xml",
          "**/pom.properties",
        ],
        "patch" : {
          "com/ibm/icu/ICUConfig.properties" : {
            "com\\.ibm\\.icu\\." : "org.graalvm.shadowed.com.ibm.icu.",
          },
          "com/ibm/icu/impl/ICUBinary.java" : {
            # we want to make this code unreachable in native image builds
            "addDataFilesFromPath\\(dataPath, icuDataFiles\\);" : "// \\g<0>",
          },
          "com/ibm/icu/impl/ICUData.java" : {
            # [GR-47166] we load an absolute path from ICUData.class, to
            # workaround an issue we don't understand when on the module path
            "ICU_DATA_PATH = \"(?!/)" : "\\g<0>/",
            "root.getResourceAsStream\\(resourceName\\)": "getResourceStream(null, root, resourceName)",
            "loader.getResourceAsStream\\(resourceName\\)": "getResourceStream(loader, null, resourceName)",
            "private static InputStream getStream": """
    private static InputStream getResourceStream(ClassLoader loader, Class<?> root, String resourceName) {
        Class<?> refClass = root;
        if (refClass == null && loader == ICUData.class.getClassLoader()) {
            refClass = ICUData.class;
        }
        InputStream inputStream;
        if (refClass != null) {
            inputStream = refClass.getResourceAsStream(resourceName);
        } else {
            inputStream = loader.getResourceAsStream(resourceName);
        }
        return inputStream;
    }
    \\g<0>""",
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
      "graalCompilerSourceEdition": "ignore",
    },

    "org.graalvm.shadowed.org.tukaani.xz" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "javaCompliance" : "17+",
      "spotbugsIgnoresGenerated" : True,
      "shadedDependencies" : [
        "truffle:XZ-1.9",
      ],
      "class" : "ShadedLibraryProject",
      "shade" : {
        "packages" : {
          "org.tukaani.xz" : "org.graalvm.shadowed.org.tukaani.xz",
        },
        "exclude" : [
          "META-INF/MANIFEST.MF",
          "META-INF/versions/9/module-info.java"
        ],
      },
      "description" : "shaded XZ Java library.",
      "allowsJavadocWarnings": True,
      "javac.lint.overrides" : 'none',
      "jacoco" : "exclude",
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
    },

    "org.graalvm.shadowed.org.json" : {
      # shaded org.json/json
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "javaCompliance" : "17+",
      "spotbugsIgnoresGenerated" : True,
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
      },
      "description" : "JSON shaded library.",
      # We need to force javac because the generated sources in this project produce warnings in JDT.
      "forceJavac": True,
      "javac.lint.overrides" : 'none',
      "jacoco" : "exclude",
      "graalCompilerSourceEdition": "ignore",
    },

    "org.graalvm.shadowed.org.antlr.v4.runtime" : {
      # shaded ANTLR4 (org.antlr:antlr4-runtime) library
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "javaCompliance" : "17+",
      "spotbugsIgnoresGenerated" : True,
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
      "forceJavac" : "true",
      "javac.lint.overrides" : 'none',
      "jacoco" : "exclude",
      "graalCompilerSourceEdition": "ignore",
    },

    "com.oracle.truffle.api.impl.asm" : {
      # Shadowed ASM libraries (org.ow2.asm:asm,asm-tree,asm-commons)
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "javaCompliance" : "17+",
      "spotbugsIgnoresGenerated" : True,
      "shadedDependencies" : [
        "truffle:ASM_9.5",
        "truffle:ASM_TREE_9.5",
        "truffle:ASM_COMMONS_9.5",
      ],
      "class" : "ShadedLibraryProject",
      "shade" : {
        "packages" : {
          "org.objectweb.asm" : "com.oracle.truffle.api.impl.asm",
        },
        "exclude" : [
          "META-INF/MANIFEST.MF",
          "**/package.html",
        ],
      },
      "description" : "ASM library shadowed for Truffle.",
      # We need to force javac because the generated sources in this project produce warnings in JDT.
      "forceJavac" : "true",
      "javac.lint.overrides" : "none",
      "jacoco" : "exclude",
      "graalCompilerSourceEdition": "ignore",
    },

    "org.graalvm.shadowed.org.jcodings" : {
      # Shadowed JCODINGS library (org.jruby.jcodings:jcodings)
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "javaCompliance" : "17+",
      "spotbugsIgnoresGenerated" : True,
      "dependencies" : [
          "TRUFFLE_API"
      ],
      "shadedDependencies" : [
        "truffle:JCODINGS_1.0.58",
      ],
      "class" : "ShadedLibraryProject",
      "shade" : {
        "packages" : {
          "org.jcodings" : "org.graalvm.shadowed.org.jcodings",
        },
        "exclude" : [
          "module-info.java",
          "META-INF/MANIFEST.MF",
          "META-INF/services/java.nio.charset.spi.CharsetProvider",
          "META-INF/maven/org.jruby.jcodings/jcodings/*", # pom.xml, pom.properties
          "org/jcodings/spi/*.java", # CharsetProvider implementation classes (Charsets.java, ISO_8859_16.java)
        ],
        "include" : {
          "tables/*.bin" : {
            "tables/" : "org/graalvm/shadowed/org/jcodings/tables/",
          },
        },
        "patch" : {
          "org/jcodings/util/ArrayReader.java" : {
            "\"/tables/\"" : "\"/org/graalvm/shadowed/org/jcodings/tables/\"",
          },
          # Fix CESU8Encoding.leftAdjustCharHead by applying a stripped down version of:
          # https://github.com/jruby/jcodings/pull/61/ (not in 1.0.58; remove when updating to a newer version).
          "org/jcodings/specific/CESU8Encoding.java" : {
"""
  (public int leftAdjustCharHead\\(byte\\[\\] bytes, int p, int s, int end\\) {
    if \\(s <= p\\)
      return s;
    int p_ = s;
    while \\(!utf8IsLead\\(bytes\\[p_\\] & 0xff\\) && p_ > p\\)
      p_--;)(
    return p_;
  })
""" : """
  \\1
    if (p_ > p && s - p_ == 2 && Character.isLowSurrogate((char) utf8Decode3ByteSequence(bytes, p_))) {
      int pSurrogatePair = p_ - 1;
      while (!utf8IsLead(bytes[pSurrogatePair] & 0xff) && pSurrogatePair > p)
        pSurrogatePair--;
      if (p_ - pSurrogatePair == 3 && Character.isHighSurrogate((char) utf8Decode3ByteSequence(bytes, pSurrogatePair))) {
        return pSurrogatePair;
      }
    }\\2

  private static int utf8Decode3ByteSequence(byte[] bytes, int p) {
    return ((bytes[p] & 0xF) << 12) | ((bytes[p + 1] & 0xff & 0x3f) << 6) | (bytes[p + 2] & 0xff & 0x3f);
  }
""",
          },
          "org/jcodings/Encoding.java" : {
            "(public static Encoding load\\([^()]*\\) \\{\\s*)[^{}]*(?:\\{[^{}]*\\}[^{}]*)*(\\s*\\})" : "\\1throw new InternalException(ErrorMessages.ERR_ENCODING_CLASS_DEF_NOT_FOUND, name);\\2"
          },
          "org/jcodings/transcode/Transcoder.java" : {
            "(public static Transcoder load\\([^()]*\\) \\{\\s*)[^{}]*(?:\\{[^{}]*\\}[^{}]*)*(\\s*\\})" : "\\1throw new InternalException(ErrorMessages.ERR_TRANSCODER_CLASS_DEF_NOT_FOUND, name);\\2"
          },
        },
      },
      "description" : "JCodings library shadowed for Truffle.",
      # We need to force javac because the generated sources in this project produce warnings in JDT.
      "forceJavac" : "true",
      "javac.lint.overrides" : "none",
      "jacoco" : "exclude",
      "graalCompilerSourceEdition": "ignore",
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
      },
      "graalCompilerSourceEdition": "ignore",
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
        "TRUFFLE_SL",
        "TRUFFLE_MODULARIZED_TEST_SEPARATE_MODULE_TEST",
      ],
      "exclude" : [
        "mx:JUNIT",
      ],
      "useModulePath": True,
      "description" : "Module with JUnit tests for testing Truffle API in modular applications.",
      "unittestConfig": "truffle",
      "maven": False,
      "graalCompilerSourceEdition": "ignore",
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
      "unittestConfig": "truffle",
      "maven": False,
      "graalCompilerSourceEdition": "ignore",
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
          "com.oracle.truffle.compiler to org.graalvm.truffle.runtime, jdk.graal.compiler, org.graalvm.nativeimage.builder, com.oracle.truffle.enterprise, com.oracle.graal.graal_enterprise, org.graalvm.truffle.runtime.svm, com.oracle.truffle.enterprise.svm",
          "com.oracle.truffle.compiler.hotspot to org.graalvm.truffle.runtime, jdk.graal.compiler",
          "com.oracle.truffle.compiler.hotspot.libgraal to org.graalvm.truffle.runtime, jdk.graal.compiler"
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
          "static jdk.jfr", # JFR is not included in the J9 JVM
          "org.graalvm.collections",
          "org.graalvm.nativeimage",
          "org.graalvm.polyglot",
        ],
        "exports" : [
          # Qualified exports
          "* to org.graalvm.truffle, com.oracle.truffle.enterprise, org.graalvm.truffle.runtime.svm, com.oracle.truffle.enterprise.svm",
          # necessary to instantiate access truffle compiler from the runtime during host compilation
          "com.oracle.truffle.runtime.hotspot to jdk.graal.compiler",
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
      "graalCompilerSourceEdition": "ignore",
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
          "java.sql", # java.sql.date java.sql.Time
          "org.graalvm.collections",
          "org.graalvm.nativeimage",
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
          "com.oracle.truffle.api.strings.provider",

          # Qualified exports
          "com.oracle.truffle.api.impl to org.graalvm.locator, org.graalvm.truffle.runtime, com.oracle.truffle.enterprise, org.graalvm.truffle.runtime.svm, com.oracle.truffle.enterprise.svm, com.oracle.truffle.truffle_nfi_panama",
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
          "com.oracle.truffle.api.strings.provider.JCodingsProvider",
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
          "com.oracle.truffle.api.impl to org.graalvm.locator, org.graalvm.truffle.runtime, com.oracle.truffle.enterprise, org.graalvm.truffle.runtime.svm,com.oracle.truffle.enterprise.svm, com.oracle.truffle.truffle_nfi_panama",
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
        "sdk:COLLECTIONS",
        "sdk:NATIVEIMAGE",
        "sdk:POLYGLOT",
      ],
      "description" : "Truffle is a multi-language framework for executing dynamic languages\nthat achieves high performance when combined with Graal.",
      "javadocType": "api",
      "maven": {
          "tag": ["default", "public"],
      },
      "useModulePath": True,
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
        "requires": [
          "org.graalvm.collections",
          "org.graalvm.polyglot",
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
      "graalCompilerSourceEdition": "ignore",
    },

    "TRUFFLE_NFI_LIBFFI" : {
      # This distribution defines a module.
      "moduleInfo" : {
        "name" : "com.oracle.truffle.truffle_nfi_libffi",
        "opens" : [
          "com.oracle.truffle.nfi.backend.libffi to org.graalvm.truffle.runtime.svm",
        ],
        "requires": [
          "org.graalvm.truffle",
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
      "graalCompilerSourceEdition": "ignore",
    },

    "TRUFFLE_NFI_PANAMA" : {
      # This distribution defines a module.
      "moduleInfo" : {
        "name" : "com.oracle.truffle.truffle_nfi_panama",
        "requires": [
          "org.graalvm.truffle",
        ],
      },
      "subDir" : "src",
      "javaCompliance" : "22+",
      # GR-51699
      "forceJavac": True,
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
    },

    "TRUFFLE_TCK_TESTS" : {
      "subDir" : "src",
      "javaCompliance" : "17+",
      "dependencies" : [
        "com.oracle.truffle.tck.tests"
      ],
      "distDependencies" : [
        "sdk:POLYGLOT_TCK",
        "TRUFFLE_TCK_COMMON",
        "TRUFFLE_TCK_INSTRUMENTATION",
      ],
      "exclude" : ["mx:JUNIT"],
      "description" : "A collection of tests that can certify language implementation to be compliant\nwith most recent requirements of the Truffle infrastructure and tooling.",
      "allowsJavadocWarnings": True,
      "testDistribution" : False,
      "unittestConfig": "truffle-tck",
      "maven": {
          "tag": ["default", "public"],
      },
      "noMavenJavadoc": True,
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
    },

    "TRUFFLE_SL" : {
      "subDir" : "src",
      "moduleInfo" : {
        "name" : "org.graalvm.sl",
        "requires": [
          "org.graalvm.polyglot",
        ],
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
          "TRUFFLE_SL",
          "TRUFFLE_TCK_TESTS",
      ],
      "unittestConfig": "truffle",
      "maven" : False,
      "graalCompilerSourceEdition": "ignore",
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
        "TRUFFLE_SL",
      ],
      "description" : "Truffle TCK provider for SL language.",
      "maven" : {
        "artifactId": "sl-truffle-tck",
        "tag": ["default", "public"],
      },
      "noMavenJavadoc": True,
      "graalCompilerSourceEdition": "ignore",
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
      "maven" : False,
      "graalCompilerSourceEdition": "ignore",
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
      "unittestConfig": "truffle",
      "maven" : False,
      "graalCompilerSourceEdition": "ignore",
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
         "TRUFFLE_SL",
         "TRUFFLE_TCK_COMMON",
         "TRUFFLE_TCK_TESTS",
         "TRUFFLE_DSL_PROCESSOR",
         "TRUFFLE_TCK",
         "TRUFFLE_TCK_INSTRUMENTATION",
         "TRUFFLE_JCODINGS",
      ],
      "unittestConfig": "truffle",
      "maven" : False,
      "graalCompilerSourceEdition": "ignore",
     },

    "TRUFFLE_NFI_TEST": {
       "subDir": "src",
       "javaCompliance": "17+",
       "dependencies": [
         "com.oracle.truffle.nfi.test",
       ],
       "exclude": [
         "mx:HAMCREST",
         "mx:JUNIT",
       ],
       "distDependencies": [
        "TRUFFLE_TEST",
        "TRUFFLE_NFI",
        "TRUFFLE_NFI_LIBFFI",
        "TRUFFLE_TEST_NATIVE",
      ],
      "unittestConfig": "truffle-nfi",
      "maven": False,
      "graalCompilerSourceEdition": "ignore",
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
       "unittestConfig": "truffle",
       "maven" : False,
     },

    "TRUFFLE_NFI_GRAALVM_SUPPORT" : {
      "native" : True,
      "description" : "Truffle NFI support distribution for the GraalVM",
      "layout" : {
        "./include/" : ["dependency:com.oracle.truffle.nfi.native/include/*.h"],
      },
      "maven" : False,
      "graalCompilerSourceEdition": "ignore",
    },

    "TRUFFLE_NFI_NATIVE_GRAALVM_SUPPORT" : {
      "native" : True,
      "platformDependent" : True,
      "description" : "Truffle NFI support distribution for the GraalVM",
      "layout" : {
        "./" : ["dependency:com.oracle.truffle.nfi.native"],
      },
      "maven" : False,
      "graalCompilerSourceEdition": "ignore",
    },

    "TRUFFLE_ICU4J_GRAALVM_SUPPORT" : {
      "native" : True,
      "description" : "Truffle support distribution for ICU4J",
      "layout" : {
        "native-image.properties" : "file:mx.truffle/language-icu4j.properties",
      },
      "maven" : False,
      "graalCompilerSourceEdition": "ignore",
    },

    "TRUFFLE_ANTLR4_GRAALVM_SUPPORT" : {
      "native" : True,
      "description" : "Truffle support distribution for ANTLR4",
      "layout" : {
        "native-image.properties" : "file:mx.truffle/language-antlr4.properties",
      },
      "maven" : False,
      "graalCompilerSourceEdition": "ignore",
    },

    "TRUFFLE_JSON_GRAALVM_SUPPORT" : {
      "native" : True,
      "description" : "Truffle support distribution for Truffle JSON",
      "layout" : {
        "native-image.properties" : "file:mx.truffle/language-json.properties",
      },
      "maven" : False,
      "graalCompilerSourceEdition": "ignore",
    },

    "TRUFFLE_XZ_GRAALVM_SUPPORT" : {
      "native" : True,
      "description" : "Truffle support distribution for XZ Java library",
      "layout" : {
        "native-image.properties" : "file:mx.truffle/language-xz.properties",
      },
      "maven" : False,
      "graalCompilerSourceEdition": "ignore",
    },

    "LOCATOR": {
      "subDir": "src",
      "moduleInfo" : {
        "name" : "org.graalvm.locator",
        "exports" : [
          "com.oracle.graalvm.locator to jdk.graal.compiler.management",
        ],
        "requires": [
          "org.graalvm.polyglot",
        ],
      },
      "dependencies": ["com.oracle.graalvm.locator"],
      "distDependencies": [
        "truffle:TRUFFLE_API",
      ],
      "maven" : False,
      "graalCompilerSourceEdition": "ignore",
    },

    "TRUFFLE_ICU4J" : {
      # shaded ICU4J + ICU4J-CHARSET
      # This distribution defines a module.
      "moduleInfo" : {
        "name" : "org.graalvm.shadowed.icu4j",
        "requires" : [
          "static java.xml",
          "static java.desktop",
          "static org.graalvm.shadowed.xz",
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
      "dependencies" : [
        "org.graalvm.shadowed.com.ibm.icu",
      ],
      "distDependencies" : [
        "sdk:NATIVEIMAGE",
        "truffle:TRUFFLE_XZ",
      ],
      "description" : "ICU4J shaded module.",
      "allowsJavadocWarnings" : True,
      "license" : ["ICU"],
      "maven" : {
        "groupId" : "org.graalvm.shadowed",
        "artifactId" : "icu4j",
        "tag": ["default", "public"],
      },
      "compress" : True,
      "graalCompilerSourceEdition": "ignore",
    },

    "TRUFFLE_XZ" : {
      # This distribution defines a module.
      "moduleInfo" : {
        "name" : "org.graalvm.shadowed.xz",
        "requires" : [
        ],
        "exports" : [
          # Qualified exports.
          "org.graalvm.shadowed.org.tukaani.xz to org.graalvm.py, org.graalvm.r, org.graalvm.shadowed.icu4j",
          "org.graalvm.shadowed.org.tukaani.xz.* to org.graalvm.py, org.graalvm.r, org.graalvm.shadowed.icu4j",
        ],
      },
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "javaCompliance" : "17+",
      "dependencies" : [
        "org.graalvm.shadowed.org.tukaani.xz",
      ],
      "description" : "shaded XZ Java module.",
      "allowsJavadocWarnings" : True,
      "maven" : {
        "groupId" : "org.graalvm.shadowed",
        "artifactId" : "xz",
        "tag": ["default", "public"],
      },
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
    },

    "TRUFFLE_JCODINGS" : {
      # JCODINGS library shadowed for Truffle.
      # This distribution defines a module.
      "moduleInfo" : {
        "name" : "org.graalvm.shadowed.jcodings",
        "exports" : [
          # Unqualified exports
          "org.graalvm.shadowed.org.jcodings",
          "org.graalvm.shadowed.org.jcodings.ascii",
          "org.graalvm.shadowed.org.jcodings.constants",
          "org.graalvm.shadowed.org.jcodings.exception",
          "org.graalvm.shadowed.org.jcodings.specific",
          "org.graalvm.shadowed.org.jcodings.transcode",
          "org.graalvm.shadowed.org.jcodings.transcode.specific",
          "org.graalvm.shadowed.org.jcodings.unicode",
          "org.graalvm.shadowed.org.jcodings.util",
        ],
      },
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "javaCompliance" : "17+",
      "dependencies" : [
        "org.graalvm.shadowed.org.jcodings",
      ],
      "distDependencies" : [
        "TRUFFLE_API",
      ],
      "description" : "JCodings module shadowed for Truffle.",
      "allowsJavadocWarnings" : True,
      "license" : ["MIT"],
      "useModulePath": True,
      "maven" : {
        "groupId" : "org.graalvm.shadowed",
        "artifactId" : "jcodings",
        "tag": ["default", "public"],
      },
      "compress" : True,
      "graalCompilerSourceEdition": "ignore",
    },
  },
}
