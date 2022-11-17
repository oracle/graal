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
    "mxversion": "6.11.4",
    "name": "tools",
    "defaultLicense" : "GPLv2-CPE",

    "groupId" : "org.graalvm.tools",
    "version" : "23.0.0",
    "release" : False,
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
              "urls" : [
                {"url" : "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind" : "binary"},
              ]
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
                "truffle:TruffleJSON",
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
            "javaCompliance" : "11+",
            "checkstyleVersion" : "8.8",
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
            "javaCompliance" : "11+",
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
            "javaCompliance" : "11+",
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
            "javaCompliance" : "11+",
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
            "javaCompliance" : "11+",
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
            "javaCompliance" : "11+",
            "workingSets" : "Tools",
        },
        "com.oracle.truffle.tools.profiler" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "truffle:TRUFFLE_API",
                "truffle:TruffleJSON",
            ],
            "requires" : ["java.logging"],
            "exports" : [
              "<package-info>", # exports all packages containing package-info.java
              "com.oracle.truffle.tools.profiler.impl to org.graalvm.truffle",
            ],
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "checkstyle" : "com.oracle.truffle.tools.chromeinspector",
            "javaCompliance" : "11+",
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
            "javaCompliance" : "11+",
            "workingSets" : "Tools",
        },
        "com.oracle.truffle.tools.coverage" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "truffle:TRUFFLE_API",
                "truffle:TruffleJSON",
                ],
            "exports" : [
              "<package-info>", # exports all packages containing package-info.java
              "com.oracle.truffle.tools.coverage.impl to org.graalvm.truffle",
            ],
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "checkstyle" : "com.oracle.truffle.tools.chromeinspector",
            "javaCompliance" : "11+",
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
            "javaCompliance" : "11+",
            "workingSets" : "Tools",
        },
        "com.oracle.truffle.tools.dap" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "truffle:TRUFFLE_API",
                "truffle:TruffleJSON",
            ],
            "requires" : ["java.logging"],
            "exports" : [
              "<package-info>", # exports all packages containing package-info.java
              "com.oracle.truffle.tools.dap.instrument to org.graalvm.truffle"
            ],
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "checkstyle" : "com.oracle.truffle.tools.chromeinspector",
            "javaCompliance" : "11+",
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
            "javaCompliance" : "11+",
            "workingSets" : "Tools",
        },
        "com.oracle.truffle.tools.warmup" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "truffle:TRUFFLE_API",
                "truffle:TruffleJSON",
            ],
            "exports" : [
              "<package-info>", # exports all packages containing package-info.java
              # "com.oracle.truffle.tools.warmup.impl to org.graalvm.truffle",
            ],
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "checkstyle" : "com.oracle.truffle.tools.chromeinspector",
            "javaCompliance" : "11+",
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
            "javaCompliance" : "11+",
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
            "javaCompliance": "11+",
            "annotationProcessors": ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "workingSets": "Tools",
        },
        "org.graalvm.tools.lsp": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "org.graalvm.tools.api.lsp",
                "truffle:TruffleJSON"
            ],
            "requires" : [
                "java.logging",
            ],
            "checkstyle": "com.oracle.truffle.tools.chromeinspector",
            "javaCompliance": "11+",
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
            "javaCompliance": "11+",
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
            "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm/visualvm-1089.tar.gz"],
            "digest" : "sha512:404e526e244440f5d7ff470396eb39a011128c97cc8ebe0e750b4f818fd8dff3c1ed4f963417ab68f461a37a6e2f4457c127b516a45879a43887fe146b43827c",
        },
        "VISUALVM_PLATFORM_SPECIFIC" : {
            "os_arch" : {
                "linux" : {
                    "amd64" : {
                        "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm/visualvm-1089-linux-amd64.tar.gz"],
                        "digest" : "sha512:15f43b80ef82cdffeb08e6fce5305a49bc64c6ea4d303921a45d4b1fa5a4acd09dad69bf2323ea0861e189a43cfce4eaca41117e4e0a44c1fe2c4931acd0966d",
                    },
                    "aarch64" : {
                        "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm/visualvm-1089-linux-aarch64.tar.gz"],
                        "digest" : "sha512:8987f5c0f3922df71dbc33249a15c5840b3e2164a6ace1dd14730542e9536ab11dd332ceff41b6b16900f9bfca9bc779f83e06bec30ec28c1915d1b8830e00ea",
                    }
                },
                "darwin" : {
                    "amd64" : {
                        "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm/visualvm-1089-macos.tar.gz"],
                        "digest" : "sha512:e56d669d88cc580c724ce57b3edec87ec8ead744642e85277da365541e2d2f7b595cbff8a87d2c5cf97e53a35e12a1c26d1c8496021e25bcacdb2b8d8ab845ae",
                    },
                    "aarch64" : {
                        "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm/visualvm-1089-macos.tar.gz"],
                        "digest" : "sha512:e56d669d88cc580c724ce57b3edec87ec8ead744642e85277da365541e2d2f7b595cbff8a87d2c5cf97e53a35e12a1c26d1c8496021e25bcacdb2b8d8ab845ae",
                    }
                },
                "windows" : {
                    "amd64" : {
                        "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm/visualvm-1089-windows-amd64.tar.gz"],
                        "digest" : "sha512:1c3169ea3dd1acad39aea2a99c49c756945d2714d1fa5f414af62de0d359767117579b8033b9a1365e8fcb71f906c0b75cb87d51697675829941b0564bfd58dc",
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
                "requiresConcealed" : {
                    "org.graalvm.truffle" : [
                        "com.oracle.truffle.api.instrumentation"
                    ],
                },
            },
            "dependencies": ["com.oracle.truffle.tools.chromeinspector"],
            "distDependencies" : [
                "truffle:TRUFFLE_API",
                "TRUFFLE_PROFILER",
            ],
            "maven" : {
              "artifactId" : "chromeinspector",
            },
            "description" : "The bridge between truffle tools and the chrome inspector.",
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
              "truffle:JLINE",
            ],
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
                "requiresConcealed" : {
                    "org.graalvm.truffle" : [
                        "com.oracle.truffle.api.instrumentation"
                    ],
                },
            },
            "dependencies": [
                "org.graalvm.tools.insight",
                "com.oracle.truffle.tools.agentscript"
            ],
            "distDependencies" : [
                "truffle:TRUFFLE_API",
            ],
            "maven" : {
              "artifactId" : "insight",
            },
            "description" : "The Ultimate Insights Gathering Platform",
        },
        "INSIGHT_HEAP": {
            "subDir": "src",
            # This distribution defines a module.
            "moduleInfo" : {
                "name" : "org.graalvm.tools.insight.heap",
                "requiresConcealed" : {
                    "org.graalvm.truffle" : [
                        "com.oracle.truffle.api.instrumentation",
                    ],
                },
            },
            "dependencies": [
                "org.graalvm.tools.insight.heap"
            ],
            "distDependencies" : [
                "truffle:TRUFFLE_API",
                "INSIGHT",
            ],
            "maven" : {
              "artifactId" : "insight-heap",
            },
            "description" : "Heap Dump for GraalVM Insight",
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
                "requiresConcealed" : {
                    "org.graalvm.truffle" : [
                        "com.oracle.truffle.api.instrumentation"
                    ],
                },
            },
            "dependencies": [
                "com.oracle.truffle.tools.profiler",
            ],
            "distDependencies" : [
                "truffle:TRUFFLE_API",
            ],
            "maven" : {
              "artifactId" : "profiler",
            },
            "javadocType" : "api",
            "description" : "The truffle profiler, supporting CPU sampling and tracing. Memory tracing support is experimental"
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
                "requiresConcealed" : {
                    "org.graalvm.truffle" : [
                        "com.oracle.truffle.api.instrumentation"
                    ],
                },
            },
            "dependencies": [
                "com.oracle.truffle.tools.coverage",
            ],
            "distDependencies" : [
                "truffle:TRUFFLE_API",
            ],
            "maven" : {
              "artifactId" : "coverage",
            },
            "description" : "Truffle code coverage tool.",
            "javadocType" : "api",
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
                "requiresConcealed" : {
                    "org.graalvm.truffle" : [
                        "com.oracle.truffle.api.instrumentation"
                    ],
                },
            },
            "dependencies": [
                "com.oracle.truffle.tools.dap",
            ],
            "distDependencies" : [
                "truffle:TRUFFLE_API",
            ],
            "maven" : {
              "artifactId" : "dap",
            },
            "description" : "Truffle Debug Protocol Server implementation.",
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
            },
            "description" : "Truffle Language Server backend API.",
            "javadocType" : "api",
        },
        "LSP": {
            "subDir": "src",
            # This distribution defines a module.
            "moduleInfo" : {
                "name" : "org.graalvm.tools.lsp",
                "requiresConcealed" : {
                    "org.graalvm.truffle" : [
                        "com.oracle.truffle.api.instrumentation"
                    ],
                },
            },
            "dependencies": [
                "org.graalvm.tools.api.lsp",
                "org.graalvm.tools.lsp"
            ],
            "distDependencies" : [
                "LSP_API",
            ],
            "maven" : {
              "artifactId" : "lsp",
            },
            "description" : "Truffle Language Server backend implementation.",
        },
        "LSP_TEST": {
            "dependencies": ["org.graalvm.tools.lsp.test"],
            "distDependencies" : [
                "LSP",
                "truffle:TRUFFLE_SL",
            ],
            "description" : "Tests for the Truffle Language Server backend.",
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
