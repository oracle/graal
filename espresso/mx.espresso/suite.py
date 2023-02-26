#
# Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
    "mxversion": "6.9.1",
    "name": "espresso",
    "version" : "23.0.0",
    "release" : False,
    "groupId" : "org.graalvm.espresso",
    "url" : "https://www.graalvm.org/reference-manual/java-on-truffle/",
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

    # ------------- licenses

    "licenses": {
        "GPLv2": {
            "name": "GNU General Public License, version 2",
            "url": "http://www.gnu.org/licenses/old-licenses/gpl-2.0.html"
        },
        "UPL": {
            "name": "Universal Permissive License, Version 1.0",
            "url": "http://opensource.org/licenses/UPL",
        },
    },
    "defaultLicense": "GPLv2",

    # ------------- imports

    "imports": {
        "suites": [
            {
                "name": "truffle",
                "subdir": True,
                "urls": [
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
            {
                "name": "tools",
                "subdir": True,
                "urls": [
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
            {
                "name": "sulong",
                "subdir": True,
                "urls": [
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ],
                "os_arch": {
                    "windows": {
                        "<others>": {
                            "ignore": True,
                        },
                    },
                    "<others>": {
                        "<others>": {
                            "ignore": False,
                        }
                    }
                }
            },
            {
                "name" : "java-benchmarks",
                "subdir": True,
                "urls": [
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
        ],
    },

    # ------------- projects

    "projects": {

        "com.oracle.truffle.espresso.polyglot": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
            ],
            "javaCompliance": "11+",
            "checkstyle": "com.oracle.truffle.espresso.polyglot",
            "checkstyleVersion": "8.8",
            "license": "UPL",
        },

        "com.oracle.truffle.espresso.hotswap": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
            ],
            "javaCompliance": "11+",
            "checkstyle": "com.oracle.truffle.espresso.polyglot",
            "license": "UPL",
        },

        "com.oracle.truffle.espresso": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "truffle:TRUFFLE_API",
                "truffle:TRUFFLE_NFI",
                "truffle:TRUFFLE_ASM_9.1",
                "com.oracle.truffle.espresso.jdwp",
            ],
            "requires": [
                "java.logging",
                "jdk.unsupported", # sun.misc.Signal
            ],
            "annotationProcessors": ["truffle:TRUFFLE_DSL_PROCESSOR", "ESPRESSO_PROCESSOR"],
            "javaCompliance": "11+",
            "checkstyle": "com.oracle.truffle.espresso",
            "checkstyleVersion": "8.8",
        },

        "com.oracle.truffle.espresso.jdk17": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "overlayTarget": "com.oracle.truffle.espresso",
            "dependencies": [
                "com.oracle.truffle.espresso",
            ],
            "checkPackagePrefix": "false",
            "multiReleaseJarVersion": "17",
            "checkstyle": "com.oracle.truffle.espresso",
            "javaCompliance": "17+",
        },


        "com.oracle.truffle.espresso.processor": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "requires": [
                "java.compiler"
            ],
            "javaCompliance": "11+",
            "checkstyle": "com.oracle.truffle.espresso",
        },

        "com.oracle.truffle.espresso.launcher": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "sdk:GRAAL_SDK",
                "sdk:LAUNCHER_COMMON",
            ],
            "javaCompliance": "11+",
            "checkstyle": "com.oracle.truffle.espresso",
        },

        "com.oracle.truffle.espresso.libjavavm": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "sdk:GRAAL_SDK",
                "sdk:LAUNCHER_COMMON",
            ],
            "requires": [
                "java.logging",
            ],
            "javaCompliance": "11+",
            "checkstyle": "com.oracle.truffle.espresso",
        },

        "com.oracle.truffle.espresso.jdwp": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "truffle:TRUFFLE_API",
                "truffle:TRUFFLE_NFI",
            ],
            "annotationProcessors": ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "javaCompliance": "11+",
            "checkstyle": "com.oracle.truffle.espresso.jdwp",
        },

        # Native library for Espresso native interface
        "com.oracle.truffle.espresso.native": {
            "subDir": "src",
            "native": "shared_lib",
            "deliverable": "nespresso",
            "platformDependent": True,
            "use_jdk_headers": True,
            "buildDependencies": [
                "truffle:TRUFFLE_NFI_NATIVE",
                "com.oracle.truffle.espresso.mokapot",
            ],
            "os_arch": {
                "windows": {
                    "<others>": {
                        "cflags": ["-Wall"],
                    },
                },
                "<others>": {
                    "<others>": {
                        "cflags": ["-Wall", "-Werror"],
                        "toolchain": "sulong:SULONG_BOOTSTRAP_TOOLCHAIN",
                    },
                },
            },
        },

        # Shared library to overcome certain, but not all, dlmopen limitations/bugs,
        # allowing native isolated namespaces to be rather usable.
        "com.oracle.truffle.espresso.eden": {
            "subDir": "src",
            "native": "shared_lib",
            "deliverable": "eden",
            "platformDependent": True,
            "os_arch": {
                "linux": {
                    "<others>": {
                        "cflags" : ["-g", "-fPIC", "-Wall", "-Werror", "-D_GNU_SOURCE"],
                        "ldflags": [
                            "-Wl,-soname,libeden.so",
                        ],
                        "ldlibs" : ["-ldl"],
                    },
                },
                "<others>": {
                    "<others>": {
                        "ignore": "Linux-only",
                    },
                },
            },
        },

        # libjvm Espresso implementation
        "com.oracle.truffle.espresso.mokapot": {
            "subDir": "src",
            "native": "shared_lib",
            "deliverable": "jvm",
            "platformDependent": True,
            "use_jdk_headers": True,
            "buildDependencies": [
                "truffle:TRUFFLE_NFI_NATIVE",
            ],
            "os_arch": {
                "darwin": {
                    "<others>": {
                        "cflags": ["-Wall", "-Werror", "-std=c11"],
                        "ldflags": [
                            "-Wl,-install_name,@rpath/libjvm.dylib",
                            "-Wl,-rpath,@loader_path/.",
                            "-Wl,-rpath,@loader_path/..",
                            "-Wl,-current_version,1.0.0",
                            "-Wl,-compatibility_version,1.0.0"
                        ],
                        "toolchain": "sulong:SULONG_BOOTSTRAP_TOOLCHAIN",
                    },
                },
                "linux": {
                    "<others>": {
                        "cflags": ["-Wall", "-Werror", "-g", "-std=c11", "-D_GNU_SOURCE"],
                        "ldflags": [
                            "-Wl,-soname,libjvm.so",
                            "-Wl,--version-script,<path:espresso:com.oracle.truffle.espresso.mokapot>/mapfile-vers",
                        ],
                        "toolchain": "sulong:SULONG_BOOTSTRAP_TOOLCHAIN",
                    },
                },
                "windows": {
                    "<others>": {
                        "cflags": ["-Wall"],
                    },
                }
            },
        },

        "com.oracle.truffle.espresso.dacapo": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "java-benchmarks:DACAPO_SCALA",
            ],
            "javaCompliance": "8+",
            "checkstyle": "com.oracle.truffle.espresso",
            "testProject" : True,
        },
    },

    # ------------- distributions

    "distributions": {
        "ESPRESSO": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.truffle.espresso",
            ],
            "distDependencies": [
                "truffle:TRUFFLE_API",
                "truffle:TRUFFLE_NFI",
                "truffle:TRUFFLE_NFI_LIBFFI",
                "tools:TRUFFLE_PROFILER",
            ],
            "exclude": [
                "truffle:TRUFFLE_ASM_9.1",
            ],
            "javaProperties": {
                "org.graalvm.language.java.home": "<path:ESPRESSO_SUPPORT>",
            },
            "maven": False,
        },

        "ESPRESSO_LAUNCHER": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.truffle.espresso.launcher",
            ],
            "mainClass": "com.oracle.truffle.espresso.launcher.EspressoLauncher",
            "distDependencies": [
                "sdk:GRAAL_SDK",
                "sdk:LAUNCHER_COMMON",
            ],
            "description": "Espresso launcher using the polyglot API.",
            "allowsJavadocWarnings": True,
            "maven": False,
        },

        "LIB_JAVAVM": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.truffle.espresso.libjavavm",
            ],
            "distDependencies": [
                "sdk:GRAAL_SDK",
                "sdk:LAUNCHER_COMMON",
            ],
            "description": "provides native espresso entry points",
            "allowsJavadocWarnings": True,
            "maven": False,
        },

        "ESPRESSO_PROCESSOR": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.truffle.espresso.processor",
            ],
            "description": "Espresso annotation processor.",
            "maven": False,
        },

        "ESPRESSO_SUPPORT": {
            "native": True,
            "description": "Espresso support distribution for the GraalVM (in espresso home)",
            "platformDependent": True,
            "os_arch": {
                "linux": {
                    "<others>": {
                        "layout": {
                            "./": ["file:mx.espresso/reflectconfig.json"],
                            "./native-image.properties": ["file:mx.espresso/native-image-preinit.properties"],
                            "LICENSE_JAVAONTRUFFLE": "file:LICENSE",
                            "lib/": [
                                "dependency:espresso:com.oracle.truffle.espresso.eden/<lib:eden>",
                                "dependency:espresso:com.oracle.truffle.espresso.native/<lib:nespresso>",
                                # Copy of libjvm.so, accessible by Sulong via the default Truffle file system.
                                "dependency:espresso:com.oracle.truffle.espresso.mokapot/<lib:jvm>",
                                "dependency:espresso:POLYGLOT/*",
                                "dependency:espresso:HOTSWAP/*",
                            ],
                        },
                    },
                },
                "<others>": {
                    "<others>": {
                        "layout": {
                            "./": [
                                "file:mx.espresso/native-image.properties",
                                "file:mx.espresso/reflectconfig.json",
                            ],
                            "LICENSE_JAVAONTRUFFLE": "file:LICENSE",
                            "lib/": [
                                "dependency:espresso:com.oracle.truffle.espresso.eden/<lib:eden>",
                                "dependency:espresso:com.oracle.truffle.espresso.native/<lib:nespresso>",
                                # Copy of libjvm.so, accessible by Sulong via the default Truffle file system.
                                "dependency:espresso:com.oracle.truffle.espresso.mokapot/<lib:jvm>",
                                "dependency:espresso:POLYGLOT/*",
                                "dependency:espresso:HOTSWAP/*",
                            ],
                        },
                    },
                },
            },
            "maven": False,
        },

        "ESPRESSO_JVM_SUPPORT": {
            "native": True,
            "description": "Espresso support distribution for the GraalVM (in JRE)",
            "platformDependent": True,
            "layout": {
                "truffle/": [
                    "dependency:espresso:com.oracle.truffle.espresso.mokapot/<lib:jvm>",
                ],
            },
            "maven": False,
        },

        "POLYGLOT": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.truffle.espresso.polyglot"
            ],
            "description": "Espresso polyglot API",
            "license": "UPL",
            "javadocType": "api",
            "moduleInfo" : {
                "name" : "espresso.polyglot",
                "exports" : [
                    "com.oracle.truffle.espresso.polyglot",
                ]
            }
        },

        "HOTSWAP": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.truffle.espresso.hotswap"
            ],
            "description": "Espresso HotSwap API",
            "license": "UPL",
            "javadocType": "api",
            "moduleInfo" : {
                "name" : "espresso.hotswap",
                "exports" : [
                    "com.oracle.truffle.espresso.hotswap",
                ]
            }
        },

        "DACAPO_SCALA_WARMUP": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.truffle.espresso.dacapo",
                "java-benchmarks:DACAPO_SCALA",
            ],
            "testDistribution": True,
            "manifestEntries" : {
                    "Manifest-Version": "1.0",
                    "Build-Timestamp": "2012-02-16T11:12:52",
                    "Implementation-Title": "Scala Benchmark Suite",
                    "Implementation-Version": "0.1.0-SNAPSHOT",
                    "Built-By": "sewe",
                    "Specification-Vendor": "Technische Universitat Darmstadt",
                    "Created-By": "Apache Maven 3.0.4",
                    "Implementation-Vendor": "Technische Universitat Darmstadt",
                    "Build-Number": "02fbc0d55f60",
                    "Implementation-Vendor-Id": "org.scalabench.benchmarks",
                    "Build-Jdk": "1.6.0_26",
                    "Specification-Title": "Scala Benchmark Suite",
                    "Specification-Version": "0.1.0-SNAPSHOT",
                    "Main-Class": "Harness",
                    "Archiver-Version": "Plexus Archiver",
            },
            "description": "Scala DaCapo with WallTime callback",
            "maven": False,
        },
    }
}
