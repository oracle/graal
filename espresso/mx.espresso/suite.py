#
# Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
    "mxversion": "6.44.0",
    "name": "espresso",
    "version" : "24.1.0",
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
        "Oracle Proprietary": {
            "name": "ORACLE PROPRIETARY/CONFIDENTIAL",
            "url": "http://www.oracle.com/us/legal/copyright/index.html"
        },
    },
    "defaultLicense": "GPLv2",

    # ------------- imports

    "imports": {
        "suites": [
            {
                "name": "truffle",
                "subdir": True,
            },
            {
                "name": "tools",
                "subdir": True,
            },
            {
                "name": "sulong",
                "subdir": True,
            },
            {
                "name" : "sdk",
                "subdir": True,
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
            "javaCompliance" : "8+",
            "checkstyle": "com.oracle.truffle.espresso.polyglot",
            "checkstyleVersion": "10.7.0",
            "license": "UPL",
        },

        "com.oracle.truffle.espresso.hotswap": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
            ],
            "javaCompliance" : "8+",
            "checkstyle": "com.oracle.truffle.espresso.polyglot",
            "license": "UPL",
        },

        "com.oracle.truffle.espresso": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "truffle:TRUFFLE_API",
                "truffle:TRUFFLE_NFI",
                "com.oracle.truffle.espresso.jdwp",
                "com.oracle.truffle.espresso.shadowed.asm",
            ],
            "requires": [
                "java.logging",
                "jdk.unsupported", # sun.misc.Signal
                "java.management",
            ],
            "uses": [
                "com.oracle.truffle.espresso.ffi.NativeAccess.Provider",
            ],
            "annotationProcessors": ["truffle:TRUFFLE_DSL_PROCESSOR", "ESPRESSO_PROCESSOR"],
            "javaCompliance" : "17+",
            "checkstyle": "com.oracle.truffle.espresso",
            "checkstyleVersion": "10.7.0",
        },

        "com.oracle.truffle.espresso.resources.libs": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "truffle:TRUFFLE_API",
            ],
            "annotationProcessors": ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "javaCompliance": "17+",
            "checkstyle": "com.oracle.truffle.espresso",
        },

        "com.oracle.truffle.espresso.processor": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "requires": [
                "java.compiler"
            ],
            "javaCompliance" : "17+",
            "checkstyle": "com.oracle.truffle.espresso",
        },

        "com.oracle.truffle.espresso.launcher": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "sdk:POLYGLOT",
                "sdk:LAUNCHER_COMMON",
            ],
            "javaCompliance" : "17+",
            "checkstyle": "com.oracle.truffle.espresso",
        },

        "com.oracle.truffle.espresso.libjavavm": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "sdk:POLYGLOT",
                "sdk:LAUNCHER_COMMON",
            ],
            "requires": [
                "java.logging",
            ],
            "javaCompliance" : "17+",
            "checkstyle": "com.oracle.truffle.espresso",
        },

        "com.oracle.truffle.espresso.jdwp": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "truffle:TRUFFLE_API",
                "truffle:TRUFFLE_NFI",
            ],
            "requires": [
                "java.logging",
            ],
            "annotationProcessors": ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "javaCompliance" : "17+",
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
                "com.oracle.truffle.espresso.mokapot",
            ],
            "os_arch": {
                "windows": {
                    "<others>": {
                        "cflags": ["-Wall"],
                    },
                },
                "linux-musl": {
                    "<others>": {
                        "cflags": ["-Wall", "-Werror", "-Wno-error=cpp"],
                        "toolchain": "sulong:SULONG_BOOTSTRAP_TOOLCHAIN",
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
                "linux-musl": {
                    "<others>": {
                        "ignore": "GNU Linux-only",
                    },
                },
                "<others>": {
                    "<others>": {
                        "ignore": "GNU Linux-only",
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
                            # newer LLVM versions default to --no-undefined-version
                            "-Wl,--undefined-version",
                        ],
                        "toolchain": "sulong:SULONG_BOOTSTRAP_TOOLCHAIN",
                    },
                },
                "linux-musl": {
                    "<others>": {
                        "cflags": ["-Wall", "-Werror", "-Wno-error=cpp", "-g"],
                        "ldflags": [
                            "-Wl,-soname,libjvm.so",
                            "-Wl,--version-script,<path:espresso:com.oracle.truffle.espresso.mokapot>/mapfile-vers",
                            # newer LLVM versions default to --no-undefined-version
                            "-Wl,--undefined-version",
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
                "sdk:DACAPO_SCALA",
            ],
            "javaCompliance": "8+",
            "checkstyle": "com.oracle.truffle.espresso",
            "testProject" : True,
        },

        "com.oracle.truffle.espresso.shadowed.asm" : {
            # Shadowed ASM library (org.ow2.asm:asm)
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "javaCompliance" : "17+",
            "spotbugs" : "false",
            "shadedDependencies" : [
                "truffle:ASM_9.5",
            ],
            "class" : "ShadedLibraryProject",
            "shade" : {
                "packages" : {
                    "org.objectweb.asm" : "com.oracle.truffle.espresso.shadowed.asm",
                },
                "exclude" : [
                    "META-INF/MANIFEST.MF",
                    "**/package.html",
                ],
            },
            "description" : "ASM library shadowed for Espresso.",
            "allowsJavadocWarnings": True,
            # We need to force javac because the generated sources in this project produce warnings in JDT.
            "forceJavac" : "true",
            "javac.lint.overrides" : "none",
            "jacoco" : "exclude",
            "graalCompilerSourceEdition": "ignore",
        },

        "espresso-legacy-nativeimage-properties": {
            "class": "EspressoLegacyNativeImageProperties",
        },
    },

    # ------------- distributions

    "distributions": {
        "ESPRESSO": {
            "moduleInfo" : {
                "name" : "org.graalvm.espresso",
                "exports": [
                    "com.oracle.truffle.espresso.runtime.staticobject",  # Workaround GR-48132
                ],
                "requires": [
                  "org.graalvm.collections",
                  "org.graalvm.nativeimage",
                  "org.graalvm.polyglot",
                ],
            },
            "description" : "Core module of the Java on Truffle (aka Espresso): a Java bytecode interpreter",
            "subDir": "src",
            "dependencies": [
                "com.oracle.truffle.espresso",
            ],
            "distDependencies": [
                "truffle:TRUFFLE_API",
                "truffle:TRUFFLE_NFI",
            ],
            "maven" : {
                "artifactId" : "espresso-language",
                "tag": ["default", "public"],
            },
            "useModulePath": True,
            "noMavenJavadoc": True,
        },

        "ESPRESSO_LAUNCHER": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.truffle.espresso.launcher",
            ],
            "mainClass": "com.oracle.truffle.espresso.launcher.EspressoLauncher",
            "distDependencies": [
                "sdk:POLYGLOT",
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
                "sdk:POLYGLOT",
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

        "ESPRESSO_LIBS_RESOURCES": {
            "platformDependent": True,
            "moduleInfo": {
                "name": "org.graalvm.espresso.resources.libs",
            },
            "distDependencies": [
                "truffle:TRUFFLE_API",
            ],
            "dependencies": [
                "com.oracle.truffle.espresso.resources.libs",
                "ESPRESSO_LIBS_DIR",
            ],
            "compress": True,
            "useModulePath": True,
            "description": "Libraries used by the Java on Truffle (aka Espresso) implementation",
            "maven" : {
                "artifactId": "espresso-libs-resources",
                "tag": ["default", "public"],
            },
        },

        "ESPRESSO_LIBS_DIR": {
            "platformDependent": True,
            "type": "dir",
            "hashEntry": "META-INF/resources/java/espresso-libs/<os>/<arch>/sha256",
            "fileListEntry": "META-INF/resources/java/espresso-libs/<os>/<arch>/files",
            "platforms": [
                "linux-amd64",
                "linux-aarch64",
                "darwin-amd64",
                "darwin-aarch64",
                "windows-amd64",
            ],
            "os_arch": {
                "linux": {
                    "<others>": {
                        "layout": {
                            "META-INF/resources/java/espresso-libs/<os>/<arch>/lib/": [
                                "dependency:espresso:com.oracle.truffle.espresso.eden/<lib:eden>",
                                "dependency:espresso:com.oracle.truffle.espresso.native/<lib:nespresso>",
                                # Copy of libjvm.so, accessible by Sulong via the default Truffle file system.
                                "dependency:espresso:com.oracle.truffle.espresso.mokapot/<lib:jvm>",
                                "dependency:espresso:ESPRESSO_POLYGLOT",
                                "dependency:espresso:HOTSWAP",
                            ],
                        },
                    },
                },
                "linux-musl": {
                    "<others>": {
                        "layout": {
                            "META-INF/resources/java/espresso-libs/<os>/<arch>/lib/": [
                                "dependency:espresso:com.oracle.truffle.espresso.native/<lib:nespresso>",
                                # Copy of libjvm.so, accessible by Sulong via the default Truffle file system.
                                "dependency:espresso:com.oracle.truffle.espresso.mokapot/<lib:jvm>",
                                "dependency:espresso:ESPRESSO_POLYGLOT",
                                "dependency:espresso:HOTSWAP",
                            ],
                        },
                    },
                },
                "<others>": {
                    "<others>": {
                        "layout": {
                            "META-INF/resources/java/espresso-libs/<os>/<arch>/lib/": [
                                "dependency:espresso:com.oracle.truffle.espresso.native/<lib:nespresso>",
                                # Copy of libjvm.so, accessible by Sulong via the default Truffle file system.
                                "dependency:espresso:com.oracle.truffle.espresso.mokapot/<lib:jvm>",
                                "dependency:espresso:ESPRESSO_POLYGLOT",
                                "dependency:espresso:HOTSWAP",
                            ],
                        },
                    },
                },
            },
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
                            "./native-image.properties": "dependency:espresso:espresso-legacy-nativeimage-properties",
                            "LICENSE_JAVAONTRUFFLE": "file:LICENSE",
                            "lib/": [
                                "dependency:espresso:com.oracle.truffle.espresso.eden/<lib:eden>",
                                "dependency:espresso:com.oracle.truffle.espresso.native/<lib:nespresso>",
                                # Copy of libjvm.so, accessible by Sulong via the default Truffle file system.
                                "dependency:espresso:com.oracle.truffle.espresso.mokapot/<lib:jvm>",
                                "dependency:espresso:ESPRESSO_POLYGLOT/*",
                                "dependency:espresso:HOTSWAP/*",
                            ],
                        },
                    },
                },
                "linux-musl": {
                    "<others>": {
                        "layout": {
                            "./native-image.properties": "dependency:espresso:espresso-legacy-nativeimage-properties",
                            "LICENSE_JAVAONTRUFFLE": "file:LICENSE",
                            "lib/": [
                                "dependency:espresso:com.oracle.truffle.espresso.native/<lib:nespresso>",
                                # Copy of libjvm.so, accessible by Sulong via the default Truffle file system.
                                "dependency:espresso:com.oracle.truffle.espresso.mokapot/<lib:jvm>",
                                "dependency:espresso:ESPRESSO_POLYGLOT/*",
                                "dependency:espresso:HOTSWAP/*",
                            ],
                        },
                    },
                },
                "<others>": {
                    "<others>": {
                        "layout": {
                            "./native-image.properties": "dependency:espresso:espresso-legacy-nativeimage-properties",
                            "LICENSE_JAVAONTRUFFLE": "file:LICENSE",
                            "lib/": [
                                "dependency:espresso:com.oracle.truffle.espresso.native/<lib:nespresso>",
                                # Copy of libjvm.so, accessible by Sulong via the default Truffle file system.
                                "dependency:espresso:com.oracle.truffle.espresso.mokapot/<lib:jvm>",
                                "dependency:espresso:ESPRESSO_POLYGLOT/*",
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

        "ESPRESSO_POLYGLOT": {
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
            },
            "maven": {
                "artifactId": "polyglot",
                "tag": ["default", "public"],
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
            },
            "maven": {
                "tag": ["default", "public"],
            },
        },

        "DACAPO_SCALA_WARMUP": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.truffle.espresso.dacapo",
                "sdk:DACAPO_SCALA",
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
