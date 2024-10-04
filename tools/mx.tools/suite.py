#
# Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#
suite = {
    "mxversion": "6.41.0",
    "name": "tools",
    "defaultLicense" : "GPLv2-CPE",

    "groupId" : "org.graalvm.tools",
    "version" : "23.1.5",
    "release" : True,
    "url" : "http://openjdk.java.net/projects/graal",
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

    "imports": {
        "suites": [
            {
              "name" : "truffle",
              "subdir" : True,
            },
        ]
    },

    "ignore_suite_commit_info": True,

    "projects" : {
        "com.oracle.truffle.tools.chromeinspector" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "truffle:TRUFFLE_API",
                "TRUFFLE_PROFILER",
                "truffle:TRUFFLE_JSON",
                "TruffleJWS",
            ],
            "requires" : [
                "java.logging",
                "jdk.unsupported", # sun.misc.Unsafe
            ],
            "exports" : [
              "<package-info>", # exports all packages containing package-info.java
              "com.oracle.truffle.tools.chromeinspector.instrument to org.graalvm.truffle"
            ],
            "javaCompliance" : "17+",
            "checkstyleVersion" : "10.7.0",
            "checkstyle" : "com.oracle.truffle.tools.chromeinspector",
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "workingSets" : "Tools",
        },
        "com.oracle.truffle.tools.chromeinspector.test" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "com.oracle.truffle.tools.chromeinspector",
                "truffle:TRUFFLE_TEST",
                "truffle:TRUFFLE_SL",
                "mx:JUNIT",
            ],
            "javaCompliance" : "17+",
            "checkstyle" : "com.oracle.truffle.tools.chromeinspector",
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "workingSets" : "Tools",
        },
        "org.graalvm.tools.insight" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "truffle:TRUFFLE_API",
            ],
            "exports" : [
              "<package-info>", # exports all packages containing package-info.java
            ],
            "javaCompliance" : "17+",
            "checkstyle" : "com.oracle.truffle.tools.chromeinspector",
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "workingSets" : "Tools",
        },
        "com.oracle.truffle.tools.agentscript" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "org.graalvm.tools.insight",
            ],
            "requires" : [
                "jdk.unsupported", # sun.misc.Unsafe
            ],
            "exports" : [
              "<package-info>", # exports all packages containing package-info.java
            ],
            "javaCompliance" : "17+",
            "checkstyle" : "com.oracle.truffle.tools.chromeinspector",
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "workingSets" : "Tools",
        },
        "org.graalvm.tools.insight.heap" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "org.graalvm.tools.insight",
            ],
            "exports" : [
              "<package-info>", # exports all packages containing package-info.java
            ],
            "javaCompliance" : "17+",
            "checkstyle" : "com.oracle.truffle.tools.chromeinspector",
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "workingSets" : "Tools",
        },
        "org.graalvm.tools.insight.test" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "com.oracle.truffle.tools.agentscript",
                "org.graalvm.tools.insight.heap",
                "truffle:TRUFFLE_TEST",
                "mx:JUNIT"
            ],
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "checkstyle" : "com.oracle.truffle.tools.chromeinspector",
            "javaCompliance" : "17+",
            "workingSets" : "Tools",
        },
        "com.oracle.truffle.tools.profiler" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "truffle:TRUFFLE_API",
                "truffle:TRUFFLE_JSON",
            ],
            "requires" : ["java.logging"],
            "exports" : [
              "<package-info>", # exports all packages containing package-info.java
              "com.oracle.truffle.tools.profiler.impl to org.graalvm.truffle",
            ],
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "checkstyle" : "com.oracle.truffle.tools.chromeinspector",
            "javaCompliance" : "17+",
            "workingSets" : "Tools",
        },
        "com.oracle.truffle.tools.profiler.test" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "com.oracle.truffle.tools.profiler",
                "truffle:TRUFFLE_TEST",
                "mx:JUNIT"
            ],
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "checkstyle" : "com.oracle.truffle.tools.chromeinspector",
            "javaCompliance" : "17+",
            "workingSets" : "Tools",
        },
        "com.oracle.truffle.tools.coverage" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "truffle:TRUFFLE_API",
                "truffle:TRUFFLE_JSON",
                ],
            "exports" : [
              "<package-info>", # exports all packages containing package-info.java
              "com.oracle.truffle.tools.coverage.impl to org.graalvm.truffle",
            ],
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "checkstyle" : "com.oracle.truffle.tools.chromeinspector",
            "javaCompliance" : "17+",
            "workingSets" : "Tools",
        },
        "com.oracle.truffle.tools.coverage.test" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "com.oracle.truffle.tools.coverage",
                "truffle:TRUFFLE_TEST",
                "mx:JUNIT"
            ],
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "checkstyle" : "com.oracle.truffle.tools.chromeinspector",
            "javaCompliance" : "17+",
            "workingSets" : "Tools",
        },
        "com.oracle.truffle.tools.dap" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "truffle:TRUFFLE_API",
                "truffle:TRUFFLE_JSON",
            ],
            "requires" : ["java.logging"],
            "exports" : [
              "<package-info>", # exports all packages containing package-info.java
              "com.oracle.truffle.tools.dap.instrument to org.graalvm.truffle"
            ],
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "checkstyle" : "com.oracle.truffle.tools.chromeinspector",
            "javaCompliance" : "17+",
            "workingSets" : "Tools",
        },
        "com.oracle.truffle.tools.dap.test" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "com.oracle.truffle.tools.dap",
                "truffle:TRUFFLE_TEST",
                "mx:JUNIT"
            ],
            "requires" : ["java.logging"],
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "checkstyle" : "com.oracle.truffle.tools.chromeinspector",
            "javaCompliance" : "17+",
            "workingSets" : "Tools",
        },
        "com.oracle.truffle.tools.warmup" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "truffle:TRUFFLE_API",
                "truffle:TRUFFLE_JSON",
            ],
            "exports" : [
              "<package-info>", # exports all packages containing package-info.java
              # "com.oracle.truffle.tools.warmup.impl to org.graalvm.truffle",
            ],
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "checkstyle" : "com.oracle.truffle.tools.chromeinspector",
            "javaCompliance" : "17+",
            "workingSets" : "Tools",
        },
        "com.oracle.truffle.tools.warmup.test" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "com.oracle.truffle.tools.warmup",
                "truffle:TRUFFLE_TEST",
                "mx:JUNIT"
            ],
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "checkstyle" : "com.oracle.truffle.tools.chromeinspector",
            "javaCompliance" : "17+",
            "workingSets" : "Tools",
        },
        "org.graalvm.tools.api.lsp": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "truffle:TRUFFLE_API",
            ],
            "requires" : [
                "jdk.unsupported", # sun.misc.Unsafe
            ],
            "checkstyle": "com.oracle.truffle.tools.chromeinspector",
            "javaCompliance" : "17+",
            "annotationProcessors": ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "workingSets": "Tools",
        },
        "org.graalvm.tools.lsp": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "org.graalvm.tools.api.lsp",
                "truffle:TRUFFLE_JSON"
            ],
            "requires" : [
                "java.logging",
            ],
            "checkstyle": "com.oracle.truffle.tools.chromeinspector",
            "javaCompliance" : "17+",
            "annotationProcessors": ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "workingSets": "Tools",
        },
        "org.graalvm.tools.lsp.test": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "org.graalvm.tools.lsp",
                "truffle:TRUFFLE_SL",
                "mx:JUNIT"
            ],
            "checkstyle": "com.oracle.truffle.tools.chromeinspector",
            "javaCompliance" : "17+",
            "annotationProcessors": ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "workingSets": "Tools",
        },
    },

    "libraries": {
        "TruffleJWS" : {
          "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/trufflejws-1.5.2.jar"],
          "digest" : "sha512:6435a25bea1335553ce318be089f50ab56bbdd2f2e449b8d7f52dbfa69ee57e7aed4d2cf3225ba7dd63a7bc54ffafdc7ac497dfa64ac09f3552a1fec04016188",
          "sourceUrls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/trufflejws-1.5.2-src.jar"],
          "sourceDigest" : "sha512:a0d6c208a0bdb40a8b5960ba43569cb2b976a1387f0c85d97781704d5df642072b318826715191f6f49df0d981aecbd8a0b83b05dbc84018504554e2887f1a8c",
        },
        "VISUALVM_COMMON" : {
            "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm/visualvm-1090.tar.gz"],
            "digest" : "sha512:dcfa719f97c53c8693ac031f8ff3b17788296deeef94d2fbab3022cc8110d2a75dd439b8ff8f80e1812cbb8a8be2d48a27d2890521572bb6b87aeefa60e322e2",
        },
        "VISUALVM_PLATFORM_SPECIFIC" : {
            "os_arch" : {
                "linux" : {
                    "amd64" : {
                        "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm/visualvm-1090-linux-amd64.tar.gz"],
                        "digest" : "sha512:cd20f9ebc63d3ea5ae3c46f34075f02a178a6f4baca69093bbdda3597927d208339f6f7563ae8ea75b4b164eb49702d3a0ca08aa8d93b32dd8b967bdaf739f77",
                    },
                    "aarch64" : {
                        "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm/visualvm-1090-linux-aarch64.tar.gz"],
                        "digest" : "sha512:3a02402c92312c538ca6acf9f4e7cde77b85e0b37088350f772e582e60a6f53e67749c7cc09260445f260cf0a84098bc50be2342c6de20239454e8218526cd67",
                    },
                    "<others>": {
                        "optional": True,
                    },
                },
                "darwin" : {
                    "amd64" : {
                        "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm/visualvm-1090-macos.tar.gz"],
                        "digest" : "sha512:597e248b4ceab806ee47260b7a54b379aedb2ac877aa318660cae5f033d61590c0fdd4c3c070b4f058df50533483505dd84675b02f4cd1433a74af358f6b5a59",
                    },
                    "aarch64" : {
                        "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm/visualvm-1090-macos.tar.gz"],
                        "digest" : "sha512:597e248b4ceab806ee47260b7a54b379aedb2ac877aa318660cae5f033d61590c0fdd4c3c070b4f058df50533483505dd84675b02f4cd1433a74af358f6b5a59",
                    }
                },
                "windows" : {
                    "amd64" : {
                        "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm/visualvm-1090-windows-amd64.tar.gz"],
                        "digest" : "sha512:49e494a38e5775c07384096668df69106628b850696ac1ffa5a788351788a086d92175072ee2e50e3d169286e8b48f0b7aab1aa257afdaf57c845d482224bdec",
                    }
                },
            }
        },
    },

    "distributions": {
        "CHROMEINSPECTOR": {
            "subDir": "src",
            # This distribution defines a module.
            "moduleInfo" : {
                "name" : "com.oracle.truffle.tools.chromeinspector",
            },
            "dependencies": ["com.oracle.truffle.tools.chromeinspector"],
            "distDependencies" : [
                "truffle:TRUFFLE_API",
                "TRUFFLE_PROFILER",
                "truffle:TRUFFLE_JSON",
            ],
            "maven" : {
              "artifactId" : "chromeinspector-tool",
              "tag": ["default", "public"],
            },
            "description" : "The core module of the polyglot debugging backend for chrome inspector.",
        },
        "INSPECT_COMMUNITY": {
            "type": "pom",
            "runtimeDependencies": [
                "CHROMEINSPECTOR",
                "truffle:TRUFFLE_RUNTIME",
            ],
            "maven": {
              "groupId" : "org.graalvm.polyglot",
              "artifactId": "inspect-community",
              "tag": ["default", "public"],
            },
            "description": "The polyglot debugging backend for chrome inspector.",
        },
        "CHROMEINSPECTOR_TEST": {
            "subDir": "src",
            "dependencies": ["com.oracle.truffle.tools.chromeinspector.test"],
            "distDependencies" : [
                "truffle:TRUFFLE_API",
                "CHROMEINSPECTOR",
                "truffle:TRUFFLE_TEST",
                "truffle:TRUFFLE_SL",
            ],
            "exclude": [
              "mx:HAMCREST",
              "mx:JUNIT",
            ],
            "maven": False,
        },
        "CHROMEINSPECTOR_GRAALVM_SUPPORT" : {
            "native" : True,
            "description" : "Truffle Chrome Inspector support distribution for the GraalVM",
            "layout" : {
                "native-image.properties" : "file:mx.tools/tools-chromeinspector.properties",
            },
        },
        "INSIGHT": {
            "subDir": "src",
            # This distribution defines a module.
            "moduleInfo" : {
                "name" : "org.graalvm.tools.insight",
                "exports" : [
                  "org.graalvm.tools.insight"
                ],
            },
            "dependencies": [
                "org.graalvm.tools.insight",
                "com.oracle.truffle.tools.agentscript"
            ],
            "distDependencies" : [
                "truffle:TRUFFLE_API",
            ],
            "maven" : {
              "artifactId" : "insight-tool",
              "tag": ["default", "public"],
            },
            "description" : "The core module of the Insights Gathering Platform",
        },
        "INSIGHT_COMMUNITY": {
            "type": "pom",
            "runtimeDependencies": [
                "INSIGHT",
                "truffle:TRUFFLE_RUNTIME",
            ],
            "maven": {
              "groupId" : "org.graalvm.polyglot",
              "artifactId": "insight-community",
              "tag": ["default", "public"],
            },
            "description": "The Ultimate Insights Gathering Platform",
        },
        "INSIGHT_HEAP": {
            "subDir": "src",
            # This distribution defines a module.
            "moduleInfo" : {
                "name" : "org.graalvm.tools.insight.heap",
            },
            "dependencies": [
                "org.graalvm.tools.insight.heap"
            ],
            "distDependencies" : [
                "truffle:TRUFFLE_API",
                "INSIGHT",
            ],
            "maven" : {
              "artifactId" : "insight-heap-tool",
              "tag": ["default", "public"],
            },
            "description" : "The core module of Heap Dump for Insight",
        },
        "HEAP_COMMUNITY": {
            "type": "pom",
            "runtimeDependencies": [
                "INSIGHT_HEAP",
                "truffle:TRUFFLE_RUNTIME",
            ],
            "maven": {
              "groupId" : "org.graalvm.polyglot",
              "artifactId": "heap-community",
              "tag": ["default", "public"],
            },
            "description": "The Heap Dump for the Insights Gathering Platform",
        },
        "INSIGHT_TEST": {
            "subDir": "src",
            "dependencies": [
                "org.graalvm.tools.insight.test",
            ],
            "distDependencies" : [
                "truffle:TRUFFLE_TEST",
                "INSIGHT",
                "INSIGHT_HEAP",
            ],
            "exclude": [
              "mx:HAMCREST",
              "mx:JUNIT",
            ],
            "description" : "Tests for the Ultimate Insights Gathering Platform",
            "maven" : False,
        },
        "INSIGHT_GRAALVM_SUPPORT" : {
            "native" : True,
            "description" : "The Ultimate Insights Gathering Platform for the GraalVM",
            "layout" : {
                "native-image.properties" : "file:mx.tools/tools-insight.properties",
            },
        },
        "INSIGHT_HEAP_GRAALVM_SUPPORT" : {
            "native" : True,
            "description" : "Heap Dump for Insight for the GraalVM",
            "layout" : {
                "native-image.properties" : "file:mx.tools/tools-insight.properties",
            },
        },
        "TRUFFLE_PROFILER": {
            "subDir": "src",
            # This distribution defines a module.
            "moduleInfo" : {
                "name" : "com.oracle.truffle.tools.profiler",
                "exports" : [
                    # chromeinspector and smoke tests use CPUSampler
                    "com.oracle.truffle.tools.profiler",
                ],
            },
            "dependencies": [
                "com.oracle.truffle.tools.profiler",
            ],
            "distDependencies" : [
                "truffle:TRUFFLE_API",
                "truffle:TRUFFLE_JSON",
            ],
            "maven" : {
              "artifactId" : "profiler-tool",
              "tag": ["default", "public"],
            },
            "javadocType" : "api",
            "description" : "The core module of the Truffle profiler"
        },
        "PROFILER_COMMUNITY": {
            "type": "pom",
            "runtimeDependencies": [
                "TRUFFLE_PROFILER",
                "truffle:TRUFFLE_RUNTIME",
            ],
            "maven": {
              "groupId" : "org.graalvm.polyglot",
              "artifactId": "profiler-community",
              "tag": ["default", "public"],
            },
            "description": "The truffle profiler, supporting CPU sampling and tracing. Memory tracing support is experimental"
        },
        "TRUFFLE_PROFILER_TEST": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.truffle.tools.profiler.test",
            ],
            "distDependencies" : [
                "truffle:TRUFFLE_TEST",
                "TRUFFLE_PROFILER",
            ],
            "exclude": [
              "mx:HAMCREST",
              "mx:JUNIT",
            ],
            "description" : "Tests for the truffle profiler.",
            "maven" : False,
        },
        "TRUFFLE_PROFILER_GRAALVM_SUPPORT" : {
            "native" : True,
            "description" : "Truffle Profiler support distribution for the GraalVM",
            "layout" : {
                "native-image.properties" : "file:mx.tools/tools-profiler.properties",
            },
        },
        "TRUFFLE_COVERAGE": {
            "subDir": "src",
            # This distribution defines a module.
            "moduleInfo" : {
                "name" : "com.oracle.truffle.tools.coverage",
            },
            "dependencies": [
                "com.oracle.truffle.tools.coverage",
            ],
            "distDependencies" : [
                "truffle:TRUFFLE_API",
                "truffle:TRUFFLE_JSON",
            ],
            "maven" : {
              "artifactId" : "coverage-tool",
              "tag": ["default", "public"],
            },
            "description" : "Core module of the Truffle code coverage tool",
            "javadocType" : "api",
        },
        "COVERAGE_COMMUNITY": {
            "type": "pom",
            "runtimeDependencies": [
                "TRUFFLE_COVERAGE",
                "truffle:TRUFFLE_RUNTIME",
            ],
            "maven": {
              "groupId" : "org.graalvm.polyglot",
              "artifactId": "coverage-community",
              "tag": ["default", "public"],
            },
            "description": "The Truffle code coverage tool"
        },
        "TRUFFLE_COVERAGE_TEST": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.truffle.tools.coverage.test",
            ],
            "distDependencies" : [
                "truffle:TRUFFLE_TEST",
                "TRUFFLE_COVERAGE",
            ],
            "exclude": [
              "mx:HAMCREST",
              "mx:JUNIT",
            ],
            "description" : "Tests for the truffle coverage tool.",
            "maven" : False,
         },
        "TRUFFLE_COVERAGE_GRAALVM_SUPPORT" : {
            "native" : True,
            "description" : "Truffle Code coverage support distribution for the GraalVM",
            "layout" : {
                "native-image.properties" : "file:mx.tools/tools-coverage.properties",
            },
        },
        "DAP": {
            "subDir": "src",
            # This distribution defines a module.
            "moduleInfo" : {
                "name" : "com.oracle.truffle.tools.dap",
            },
            "dependencies": [
                "com.oracle.truffle.tools.dap",
            ],
            "distDependencies" : [
                "truffle:TRUFFLE_API",
                "truffle:TRUFFLE_JSON",
            ],
            "maven" : {
              "artifactId" : "dap-tool",
              "tag": ["default", "public"],
            },
            "description" : "Core module of the polyglot debugging backend for the Debug Adapter Protocol",
        },
        "DAP_COMMUNITY": {
            "type": "pom",
            "runtimeDependencies": [
                "DAP",
                "truffle:TRUFFLE_RUNTIME",
            ],
            "maven": {
              "groupId" : "org.graalvm.polyglot",
              "artifactId": "dap-community",
              "tag": ["default", "public"],
            },
            "description": "The polyglot debugging backend for the Debug Adapter Protocol"
        },
        "DAP_TEST": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.truffle.tools.dap.test",
            ],
            "distDependencies" : [
                "truffle:TRUFFLE_TEST",
                "DAP",
            ],
            "exclude": [
              "mx:HAMCREST",
              "mx:JUNIT",
            ],
            "description" : "Tests for the Truffle Debug Protocol Server.",
            "maven" : False,
         },
        "DAP_GRAALVM_SUPPORT" : {
            "native" : True,
            "description" : "Truffle Debug Protocol Server distribution for the GraalVM",
            "layout" : {
                "native-image.properties" : "file:mx.tools/tools-dap.properties",
            },
        },
        "VISUALVM_GRAALVM_SUPPORT": {
            "native": True,
            "platformDependent": True,
            "description": "VisualVM support distribution for the GraalVM",
            "layout": {
                "./": [
                    "extracted-dependency:VISUALVM_COMMON/lib/visualvm/*",
                    "extracted-dependency:VISUALVM_PLATFORM_SPECIFIC/./lib/visualvm/*",
                ],
                "LICENSE_VISUALVM.txt": "file:VISUALVM_LICENSE",
            },
        },
        "LSP_API": {
            "subDir": "src",
            # This distribution defines a module.
            "moduleName" : "org.graalvm.tools.api.lsp",
            "dependencies": ["org.graalvm.tools.api.lsp"],
            "distDependencies" : [
                "truffle:TRUFFLE_API",
            ],
            "maven" : {
              "artifactId" : "lsp_api",
              "tag": ["default", "public"],
            },
            "description" : "Truffle Language Server backend API.",
            "javadocType" : "api",
        },
        "LSP": {
            "subDir": "src",
            # This distribution defines a module.
            "moduleInfo" : {
                "name" : "org.graalvm.tools.lsp",
            },
            "dependencies": [
                "org.graalvm.tools.api.lsp",
                "org.graalvm.tools.lsp"
            ],
            "distDependencies" : [
                "LSP_API",
                "truffle:TRUFFLE_JSON",
            ],
            "maven" : {
              "artifactId" : "lsp-tool",
              "tag": ["default", "public"],
            },
            "description" : "Core module of the polyglot Language Server backend",
        },
        "LSP_COMMUNITY": {
            "type": "pom",
            "runtimeDependencies": [
                "LSP",
                "truffle:TRUFFLE_RUNTIME",
            ],
            "maven": {
              "groupId" : "org.graalvm.polyglot",
              "artifactId": "lsp-community",
              "tag": ["default", "public"],
            },
            "description": "The polyglot Language Server backend"
        },
        "LSP_TEST": {
            "dependencies": ["org.graalvm.tools.lsp.test"],
            "distDependencies" : [
                "LSP",
                "truffle:TRUFFLE_SL",
            ],
            "exclude": [
              "mx:HAMCREST",
              "mx:JUNIT",
            ],
            "description" : "Tests for the Truffle Language Server backend.",
            "maven": False,
        },
        "LSP_GRAALVM_SUPPORT" : {
            "native" : True,
            "description" : "Truffle Language Server backend for the GraalVM",
            "layout" : {
                "native-image.properties" : "file:mx.tools/tools-lsp.properties",
            },
        },
    },
}
