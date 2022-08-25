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
    "mxversion": "6.4.0",
    "name": "tools",
    "defaultLicense" : "GPLv2-CPE",

    "groupId" : "org.graalvm.tools",
    "version" : "22.3.0",
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
          "sha1" : "9a0b23fb70ec828405ade9252c2e1e946a2eb455",
          "sourceUrls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/trufflejws-1.5.2-src.jar"],
          "sourceSha1" : "4b6f04d25ae52ace16f8ff9e7561f965e04f5848",
        },
        "VISUALVM_COMMON" : {
            "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm/visualvm-1083.tar.gz"],
            "sha1" : "b9f08440543fa4773ae42ae29eaac243d43cd50f",
        },
        "VISUALVM_PLATFORM_SPECIFIC" : {
            "os_arch" : {
                "linux" : {
                    "amd64" : {
                        "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm/visualvm-1083-linux-amd64.tar.gz"],
                        "sha1" : "086dc63630295019bca9771ffa9e73a9d5ae946f",
                    },
                    "aarch64" : {
                        "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm/visualvm-1083-linux-aarch64.tar.gz"],
                        "sha1" : "0a28f1d5b025bab8b16a44193093289f0a0cc3f1",
                    }
                },
                "darwin" : {
                    "amd64" : {
                        "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm/visualvm-1083-macos.tar.gz"],
                        "sha1" : "da1790d286e5d486925b3577fc29262fb208d74a",
                    },
                    "aarch64" : {
                        "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm/visualvm-1083-macos.tar.gz"],
                        "sha1" : "da1790d286e5d486925b3577fc29262fb208d74a",
                    }
                },
                "windows" : {
                    "amd64" : {
                        "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/visualvm/visualvm-1083-windows-amd64.tar.gz"],
                        "sha1" : "5767ceff72bf300f0364dab9e5c29437462383e0",
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
