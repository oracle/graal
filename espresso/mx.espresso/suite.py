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
    "mxversion": "7.58.0",
    "name": "espresso",
    "version" : "26.0.0",
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
            {
                "name" : "espresso-shared",
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
            "checkstyleVersion": "10.21.0",
            "license": "UPL",
        },

        "com.oracle.truffle.espresso.io": {
            "subDir": "src",
            "sourceDirs": ["src"],
            # Contains classes in sun.nio.* that only compile with javac.
            "forceJavac": "true",
            "javaCompliance": "21+",
            "patchModule": "java.base",
            "checkPackagePrefix": False,  # Contains classes in java.io and sun.nio.
            "checkstyle": "com.oracle.truffle.espresso",
        },

        "com.oracle.truffle.espresso.io.jdk21": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.truffle.espresso.io",
            ],
            "overlayTarget": "com.oracle.truffle.espresso.io",
            # Contains classes in sun.nio.* that only compile with javac.
            "forceJavac": "true",
            "multiReleaseJarVersion": "21",
            "patchModule": "java.base",
            "javaCompliance": "21",
            "checkPackagePrefix": False,  # Contains classes in java.io and sun.nio.
            "checkstyle": "com.oracle.truffle.espresso",
        },

        "com.oracle.truffle.espresso.io.jdk25": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.truffle.espresso.io",
            ],
            # GR-47124 spotbugs does not support jdk25
            "spotbugs": "false",
            "overlayTarget": "com.oracle.truffle.espresso.io",
            # Contains classes in sun.nio.* that only compile with javac.
            "forceJavac": "true",
            "multiReleaseJarVersion": "25",
            "patchModule": "java.base",
            "javaCompliance": "25",
            "checkPackagePrefix": False,  # Contains classes in java.io and sun.nio.
            "checkstyle": "com.oracle.truffle.espresso",
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

        "org.graalvm.continuations": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
            ],
            "javaCompliance" : "21+",
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
                "espresso-shared:ESPRESSO_SHARED",
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
            "jacoco" : "include",
            "javaCompliance" : "17+",
            "checkstyle": "com.oracle.truffle.espresso",
            "checkstyleVersion": "10.21.0",
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
            "javaCompliance" : "21+",
            "checkstyle": "com.oracle.truffle.espresso",
        },

        "com.oracle.truffle.espresso.launcher": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "sdk:POLYGLOT",
                "sdk:LAUNCHER_COMMON",
            ],
            "jacoco" : "include",
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
                "espresso-shared:ESPRESSO_SHARED",
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

        "com.oracle.truffle.espresso.jvmci": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "requires": [
                "jdk.internal.vm.ci",
            ],
            "requiresConcealed": {
                "jdk.internal.vm.ci": [
                    "jdk.vm.ci.amd64",
                    "jdk.vm.ci.aarch64",
                    "jdk.vm.ci.code",
                    "jdk.vm.ci.code.stack",
                    "jdk.vm.ci.common",
                    "jdk.vm.ci.meta",
                    "jdk.vm.ci.runtime",
                ],
            },
            "javaCompliance": "8+",
            "checkstyle": "com.oracle.truffle.espresso",
        },

        # Native library for Espresso native interface
        "com.oracle.truffle.espresso.native": {
            "subDir": "src",
            "native": "shared_lib",
            "deliverable": "nespresso",
            "platformDependent": True,
            "buildDependencies": [
                "com.oracle.truffle.espresso.mokapot",
            ],
            "os_arch": {
                "windows": {
                    "<others>": {
                        "cflags": ["-g", "-O3", "-Wall"],
                        "multitarget": {
                            "libc": ["default"],
                        },
                    },
                },
                "linux-musl": {
                    "<others>": {
                        "cflags": ["-Wall", "-Werror", "-Wno-error=cpp"],
                        "multitarget": {
                            "libc": ["musl", "default"],
                            "compiler": ["sulong-bitcode", "host", "*"]
                        },
                    },
                },
                "<others>": {
                    "<others>": {
                        "cflags": ["-Wall", "-Werror"],
                        "multitarget": {
                            "libc": ["glibc", "musl", "default"],
                            "compiler": ["sulong-bitcode", "host", "*"]
                        },
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
                        "cflags" : ["-g", "-O3", "-fPIC", "-Wall", "-Werror", "-D_GNU_SOURCE"],
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
                        "multitarget": {
                            "compiler": ["sulong-bitcode", "host", "*"]
                        },
                    },
                },
                "linux": {
                    "<others>": {
                        "cflags": ["-Wall", "-Werror", "-g", "-std=c11", "-D_GNU_SOURCE"],
                        "ldflags": [
                            "-Wl,-soname,libjvm.so",
                            "-Wl,--version-script,<path:espresso:com.oracle.truffle.espresso.mokapot>/mapfile-vers",
                        ],
                        "multitarget": {
                            "libc": ["glibc", "musl", "default"],
                            "compiler": ["sulong-bitcode", "host", "*"]
                        },
                    },
                },
                "linux-musl": {
                    "<others>": {
                        "cflags": ["-Wall", "-Werror", "-Wno-error=cpp", "-g"],
                        "ldflags": [
                            "-Wl,-soname,libjvm.so",
                            "-Wl,--version-script,<path:espresso:com.oracle.truffle.espresso.mokapot>/mapfile-vers",
                        ],
                        "multitarget": {
                            "libc": ["musl", "default"],
                            "compiler": ["sulong-bitcode", "host", "*"]
                        },
                    },
                },
                "windows": {
                    "<others>": {
                        "cflags": ["-g", "-O3", "-Wall"],
                        "multitarget": {
                            "libc": ["default"],
                        },
                    },
                }
            },
        },

        "com.oracle.truffle.espresso.shadowed.asm" : {
            # Shadowed ASM library (org.ow2.asm:asm)
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "javaCompliance" : "17+",
            "spotbugs" : "false",
            "shadedDependencies" : [
                "truffle:ASM_9.7.1",
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

        "javavm": {
            "class": "NativeImageLibraryProject",
            "dependencies": [
                # no need for sulong in the native standalone
                "LIB_JAVAVM",
                "ESPRESSO",
                "ESPRESSO_LIBS_RESOURCES",
                "truffle:TRUFFLE_NFI_LIBFFI",
                "truffle:TRUFFLE_RUNTIME",
                "sdk:TOOLS_FOR_STANDALONE",
            ],
            # optionally provides:
            # - truffle-enterprise:TRUFFLE_ENTERPRISE
            # - regex:TREGEX
            # - espresso:ESPRESSO_RUNTIME_RESOURCES or espresso-tests:ESPRESSO_RUNTIME_RESOURCES
            "dynamicDependencies": "javavm_deps",
            "build_args": [
                '-Dpolyglot.java.GuestFieldOffsetStrategy=graal',
                '-R:+EnableSignalHandling',
                '-R:+InstallSegfaultHandler',
                '-H:+UnlockExperimentalVMOptions', '-H:-JNIExportSymbols', '-H:-UnlockExperimentalVMOptions',
                '-Dorg.graalvm.launcher.relative.java.home=..',
                '-Dorg.graalvm.launcher.relative.home=languages/java/lib/<lib:javavm>',
                '-H:-DetectUserDirectoriesInImageHeap',  # GR-63314
            ],
            # optionally provides:
            # --enable-monitoring=threaddump or -H:+DumpThreadStacksOnSignal
            # -H:+CopyLanguageResources
            # -H:-IncludeLanguageResources
            # -Dpolyglot.image-build-time.PreinitializeContexts=java
            # -Dpolyglot.image-build-time.PreinitializeContextsWithNative=true
            "dynamicBuildArgs": "javavm_build_args",
        },

        "espresso": {
            "class": "ThinLauncherProject",
            "relative_jre_path": "../languages/java/jvm",
            "mainClass":'com.oracle.truffle.espresso.launcher.EspressoLauncher',
            "jar_distributions": ['espresso:ESPRESSO_LAUNCHER'],
            "relative_home_paths": {
                "java": "../languages/java",
                # optionally injected:
                # "llvm": "../languages/llvm",
            },
            "relative_module_path": "../languages/java/espresso",
            "relative_extracted_lib_paths": {
                "truffle.attach.library": "../languages/java/jvmlibs/<lib:truffleattach>",
                "truffle.nfi.library": "../languages/java/jvmlibs/<lib:trufflenfi>",
            },
        },

        "espresso-release-file": {
            "class": "EspressoReleaseFileProject",
        },

        "espresso-license-files": {
            "class": "StandaloneLicenses",
            "community_license_file": "LICENSE",
            "community_3rd_party_license_file": "LICENSE",  # TODO GR-64780
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
                  "org.graalvm.espresso.shared",
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
                "espresso-shared:ESPRESSO_SHARED",
            ],
            "maven" : {
                "artifactId" : "espresso-language",
                "tag": ["default", "public"],
            },
            "useModulePath": True,
            "noMavenJavadoc": True,
        },

        "ESPRESSO_LAUNCHER": {
            "moduleInfo" : {
                "name" : "org.graalvm.espresso.launcher",
                "exports": [
                    "com.oracle.truffle.espresso.launcher to org.graalvm.launcher",
                ],
                "requires": [
                    "org.graalvm.launcher",
                    "org.graalvm.polyglot",
                ],
            },
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
            "useModulePath": True,
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

        "ESPRESSO_STANDALONE_COMMON": {
            "type": "dir",
            "platformDependent": True,
            "platforms": "local",
            "layout": {
                "./": [
                    "dependency:espresso:espresso-release-file",
                    "dependency:espresso:espresso-license-files/*",
                ],
                "languages/java/": [
                    "dependency:espresso:ESPRESSO_SUPPORT/*",
                ],
            },
            "maven": False,
        },

        "ESPRESSO_NATIVE_STANDALONE": {
            "type": "dir",
            "description": "Espresso standalone distribution",
            "platformDependent": True,
            "platforms": "local",
            "layout": {
                "./": [{
                        "source_type": "dependency",
                        "dependency": "espresso:ESPRESSO_JAVA_HOME",
                        "path": "*",
                        "exclude": [
                            "lib/jfr",
                            "lib/jvm.cfg",
                            "lib/static",
                            "<jdk_lib_dir>/server",
                            "README",
                            "LICENSE",
                        ],
                    },
                    "dependency:espresso:ESPRESSO_STANDALONE_COMMON/*",
                ],
                "<jdk_lib_dir>/truffle/": [
                    "dependency:espresso:com.oracle.truffle.espresso.mokapot/*/<multitarget_libc_selection>/<lib:jvm>",
                ],
                "lib/jvm.cfg": {
                    "source_type": "string",
                    "value": "-truffle KNOWN\n",
                },
                "languages/java/lib/": [
                    "dependency:espresso:javavm/standard-deliverables/*",
                ],
            },
            "maven": False,
        },

        "ESPRESSO_JVM_STANDALONE_JAVA_LINKS": {
            "type": "dir",
            "platformDependent": True,
            "platforms": "local",
            "os": {
                "windows": {
                    "layout": {
                        "bin/java.cmd": "file:mx.espresso/launchers/java.cmd",
                        "bin/javac.cmd": "file:mx.espresso/launchers/javac.cmd",
                    },
                },
                "<others>": {
                    "layout": {
                        "bin/java": "file:mx.espresso/launchers/java.sh",
                        "bin/javac": "file:mx.espresso/launchers/javac.sh",
                    },
                },
            },
            "maven": False,
        },

        "ESPRESSO_JVM_STANDALONE": {
            "type": "dir",
            "pruning_mode": "optional",
            "description": "Espresso JVM standalone distribution for testing",
            "platformDependent": True,
            "platforms": "local",
            "defaultDereference": "never",
            "layout": {
                "bin/": [
                    "dependency:espresso:espresso",
                    "dependency:espresso:ESPRESSO_JVM_STANDALONE_JAVA_LINKS/bin/*",
                ],
                "./": [{
                        "source_type": "dependency",
                        "dependency": "espresso:ESPRESSO_JAVA_HOME",
                        "path": "*",
                        "exclude": [
                            "bin",  # those can't run without <jdk_lib_dir>/server
                            "lib/jfr",
                            "lib/static",
                            "<jdk_lib_dir>/server",
                            "README",
                            "LICENSE",
                        ],
                    },
                    "dependency:espresso:ESPRESSO_STANDALONE_COMMON/*",
                ],
                "languages/java/lib/": [
                    # Copy of libjvm.so, accessible by Sulong via the default Truffle file system.
                    "dependency:espresso:com.oracle.truffle.espresso.mokapot/*/<multitarget_libc_selection>/<lib:jvm>",
                ],
                "languages/java/": [
                    {
                        'source_type': 'extracted-dependency',
                        'dependency': 'espresso:ESPRESSO_LLVM_SUPPORT',
                        'path': '*',
                        'optional': True,
                    },
                ],
                "languages/java/espresso/": [
                    {
                        "source_type": "classpath-dependencies",
                        "dependencies": [
                            "espresso:ESPRESSO_LAUNCHER",
                            "espresso:JVM_STANDALONE_JARS",
                        ],
                    },
                ],
                "languages/java/jvm/": {
                    "source_type": "dependency",
                    "dependency": "sdk:STANDALONE_JAVA_HOME",
                    "path": "*",
                    "exclude": [
                        # Native Image-related
                        "bin/native-image*",
                        "lib/static",
                        "lib/svm",
                        "lib/<lib:native-image-agent>",
                        "lib/<lib:native-image-diagnostics-agent>",
                        # Unnecessary and big
                        "lib/src.zip",
                        "jmods",
                    ],
                },
                "languages/java/jvmlibs/": [
                    "extracted-dependency:truffle:TRUFFLE_ATTACH_GRAALVM_SUPPORT",
                    "extracted-dependency:truffle:TRUFFLE_NFI_NATIVE_GRAALVM_SUPPORT",
                ],
                "languages/llvm/": {
                    'source_type': 'dependency',
                    'dependency': 'espresso:ESPRESSO_STANDALONE_LLVM_HOME',
                    'path': '*',
                    'optional': True,
                },
            },
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
            "layout": {
                "META-INF/resources/java/espresso-libs/<os>/<arch>/lib/": [
                    # Copy of libjvm.so, accessible by Sulong via the default Truffle file system.
                    "dependency:espresso:com.oracle.truffle.espresso.mokapot/*/<multitarget_libc_selection>/<lib:jvm>",
                ],
                "META-INF/resources/java/espresso-libs/<os>/<arch>/": "dependency:espresso:ESPRESSO_SUPPORT/*",
            },
            "maven": False,
        },

        "ESPRESSO_PD_SUPPORT": {
            "type": "dir",
            "description": "Platform dependent part of the espresso support distribution",
            "platformDependent": True,
            "platforms": "local",
            "os_arch": {
                "linux": {
                    "<others>": {
                        "layout": {
                            "lib/": [
                                "dependency:espresso:com.oracle.truffle.espresso.eden/<lib:eden>",
                            ],
                        },
                    },
                },
                "linux-musl": {
                    "<others>": {
                        "layout": {
                        },
                    },
                },
                "<others>": {
                    "<others>": {
                        "layout": {
                        },
                    },
                },
            },
            "maven": False,
        },

        "ESPRESSO_SUPPORT": {
            "type": "dir",
            "description": "Espresso support distribution (in espresso home)",
            "platformDependent": True,
            "platforms": "local",
            "layout": {
                "lib/": [
                    "dependency:espresso:com.oracle.truffle.espresso.native/*/<multitarget_libc_selection>/<lib:nespresso>",
                    "dependency:espresso:ESPRESSO_POLYGLOT/*",
                    "dependency:espresso:HOTSWAP/*",
                    "dependency:espresso:CONTINUATIONS/*",
                    "dependency:espresso:ESPRESSO_JVMCI/*",
                    "dependency:espresso:ESPRESSO_IO/*",
                ],
                "./": {
                    "source_type": "dependency",
                    "dependency": "espresso:ESPRESSO_PD_SUPPORT",
                    "path": "*",
                    "optional": True
                },
            },
            "maven": False,
        },

        "ESPRESSO_GRAALVM_SUPPORT": {
            "native": True,
            "description": "Espresso support distribution for the GraalVM (in espresso home)",
            "platformDependent": True,
            "layout": {
                "./native-image.properties": "dependency:espresso:espresso-legacy-nativeimage-properties",
                "LICENSE_JAVAONTRUFFLE": "file:LICENSE",
                "./": "dependency:espresso:ESPRESSO_SUPPORT/*",
                "lib/": [
                    # Copy of libjvm.so, accessible by Sulong via the default Truffle file system.
                    "dependency:espresso:com.oracle.truffle.espresso.mokapot/*/<multitarget_libc_selection>/<lib:jvm>",
                ]
            },
            "maven": False,
        },

        "ESPRESSO_JVM_SUPPORT": {
            "native": True,
            "description": "Espresso support distribution for the GraalVM (in JRE)",
            "platformDependent": True,
            "layout": {
                "truffle/": [
                    "dependency:espresso:com.oracle.truffle.espresso.mokapot/*/<multitarget_libc_selection>/<lib:jvm>",
                ],
            },
            "maven": False,
        },

        "ESPRESSO_IO": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.truffle.espresso.io"
            ],
            "description": "Injection of Truffle file system to guest java.base",
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

        "CONTINUATIONS": {
            "subDir": "src",
            "dependencies": [
                "org.graalvm.continuations"
            ],
            "description": "Espresso Continuations API",
            "license": "UPL",
            "javadocType": "api",
            "moduleInfo" : {
                "name" : "org.graalvm.continuations",
                "exports" : [
                    "org.graalvm.continuations",
                ]
            },
            "maven": {
                "tag": ["default", "public"],
            },
        },

        "ESPRESSO_JVMCI": {
            "subDir": "src",
            "moduleInfo": {
                "name": "jdk.internal.vm.ci.espresso",
                "exports": [
                    "com.oracle.truffle.espresso.jvmci,com.oracle.truffle.espresso.jvmci.meta to jdk.graal.compiler.espresso",
                ]
            },
            "dependencies": [
                "com.oracle.truffle.espresso.jvmci",
            ],
            "description": "JVMCI implementation for Espresso",
            "maven": False,
        },

        "JAVA_POM": {
            "class": "DynamicPOMDistribution",
            "description": "Java on Truffle (aka Espresso): a Java bytecode interpreter",
            "distDependencies": [
                "ESPRESSO",
                "ESPRESSO_LIBS_RESOURCES",
                "truffle:TRUFFLE_NFI_LIBFFI",
                "truffle:TRUFFLE_RUNTIME",
                # sulong is not strictly required, but it'll work out of the box in more cases if it's there
                "sulong:LLVM_NATIVE_POM",
            ],
            # optionally provides:
            # - ESPRESSO_RUNTIME_RESOURCES
            "dynamicDistDependencies": "java_community_deps",
            "maven": {
                "artifactId": "java-community",
                "tag": ["default", "public"],
            },
        },

        "JVM_STANDALONE_JARS": {
            "class": "DynamicPOMDistribution",
            "distDependencies": [
                "ESPRESSO",
                "truffle:TRUFFLE_NFI_LIBFFI",
                "truffle:TRUFFLE_RUNTIME",
                "sdk:TOOLS_FOR_STANDALONE",
            ],
            # optionally provides:
            # - regex:TREGEX
            # - sulong:SULONG_CORE
            # - sulong:SULONG_NATIVE
            # - sulong:SULONG_NFI
            # - truffle-enterprise:TRUFFLE_ENTERPRISE
            "dynamicDistDependencies": "jvm_standalone_deps",
            "maven": False,
        },

        "ESPRESSO_POLYBENCH_BENCHMARKS": {
            "description": "Distribution for Espresso polybench benchmarks",
            "layout": {
                # Layout is dynamically populated in mx_register_dynamic_suite_constituents
            },
        },
    }
}
