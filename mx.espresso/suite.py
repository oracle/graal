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
    "mxversion": "5.254.3",
    "name": "espresso",

    # ------------- licenses

    "licenses": {
        "GPLv2": {
            "name": "GNU General Public License, version 2",
            "url": "http://www.gnu.org/licenses/old-licenses/gpl-2.0.html"
        },
    },
    "defaultLicense": "GPLv2",

    # ------------- imports

    "imports": {
        "suites": [
            {
                "name": "truffle",
                "subdir": True,
                # Custom changes in Truffle (NFI) for Espresso (branch slimbeans).
                "version": "57f429c2ed3950a807ab3d4a39a175f74b359c00",
                "urls": [
                    {"url": "https://github.com/graalvm/graal", "kind": "git"},
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },
        ],
    },
    "libraries": {
        # ------------- Libraries -------------

        "LIBFFI": {
            "urls": [
                "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/libffi-3.2.1.tar.gz",
                "https://sourceware.org/pub/libffi/libffi-3.2.1.tar.gz",
            ],
            "sha1": "280c265b789e041c02e5c97815793dfc283fb1e6",
        },
    },

    # ------------- projects

    "projects": {

        "com.oracle.truffle.espresso": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "truffle:TRUFFLE_API",
                "truffle:TRUFFLE_NFI",
                "com.oracle.truffle.espresso.jdwp"
            ],
            "annotationProcessors": ["truffle:TRUFFLE_DSL_PROCESSOR", "ESPRESSO_PROCESSOR"],
            "javaCompliance": "1.8+",
            "checkstyle": "com.oracle.truffle.espresso",
            "checkstyleVersion": "8.8",
            "checkPackagePrefix": False,  # java.lang.ref.PublicFinalReference
        },

        "com.oracle.truffle.espresso.processor": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
            ],
            "javaCompliance": "1.8+",
            "checkstyle": "com.oracle.truffle.espresso.processor",
        },

        "com.oracle.truffle.espresso.launcher": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "sdk:GRAAL_SDK",
                "sdk:LAUNCHER_COMMON",
            ],
            "javaCompliance": "1.8+",
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
            "javaCompliance": "1.8+",
            "checkstyle": "com.oracle.truffle.espresso.jdwp",
        },

        "com.oracle.truffle.espresso.playground": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "jniHeaders": True,
            "javaCompliance": "1.8+",
        },

        "com.oracle.truffle.espresso.playground.native": {
            "subDir": "src",
            "native": "shared_lib",
            "deliverable": "playground",
            "platformDependent": True,
            "use_jdk_headers": True,
            "buildDependencies": [
                "com.oracle.truffle.espresso.playground",
            ],
            "os": {
                "windows": {
                    "cflags": ["-Wall"],
                },
                "<others>": {
                    "cflags": ["-Wall", "-Werror"],
                },
            },
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
            ],            
            "os": {
                "windows": {
                    "cflags": ["-Wall"],
                },
                "<others>": {
                    "cflags": ["-Wall", "-Werror"],
                },
            },
        },

        "com.oracle.truffle.espresso.test": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "testProject": True,
            "jniHeaders": True,
            "dependencies": [
                "com.oracle.truffle.espresso",
                "truffle:TRUFFLE_INSTRUMENT_TEST",
                "mx:JUNIT",
            ],
            # JTT unit tests run both on the host JVM and on Espresso, so they must be compiled with a version compatible with Espresso (8).
            # Espresso itself can be compiled with Java 11 and the unit tests (compiled to 8) should run on a JVM 11.
            "javaCompliance": "8",
            "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "checkstyle": "com.oracle.truffle.espresso",
        },

        "com.oracle.truffle.espresso.test.jdk8": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "testProject": True,
            "dependencies": [
                "com.oracle.truffle.espresso.test"
            ],
            "overlayTarget": "com.oracle.truffle.espresso.test",
            "javaCompliance": "8",
        },

        # Native library for tests
        "com.oracle.truffle.espresso.test.native": {
            "testProject": True,
            "subDir": "src",
            "native": "shared_lib",
            "deliverable": "nativetest",
            "use_jdk_headers": True,
            "buildDependencies": [
                "com.oracle.truffle.espresso.test",
            ],
            "os": {
                "windows": {
                    "cflags": ["-Wall"],
                },
                "<others>": {
                    "cflags": ["-Wall", "-Werror"],
                },
            },
        },

        # libjvm Espresso implementation
        "com.oracle.truffle.espresso.mokapot": {
            "subDir": "src",
            "native": "shared_lib",
            "deliverable": "mokapot",
            "platformDependent": True,
            "use_jdk_headers": True,
            "buildDependencies": [
                "truffle:TRUFFLE_NFI_NATIVE",
            ],            
            "os_arch": {
                "darwin": {
                    "<others>": {
                        "cflags": ["-Wall", "-Werror"],
                        "ldflags": [
                            "-Wl,-install_name,@rpath/libjvm.dylib",
                            "-Wl,-rpath,@loader_path/.",
                            "-Wl,-rpath,@loader_path/..",
                            "-Wl,-current_version,1.0.0",
                            "-Wl,-compatibility_version,1.0.0"
                        ],
                    },
                },
                "linux": {
                    "<others>": {
                        "cflags": ["-Wall", "-Werror"],
                        "ldflags": [
                            "-Wl,-soname,libjvm.so",
                            "-Wl,--version-script,<path:espresso:com.oracle.truffle.espresso.mokapot>/mapfile-vers",
                        ],
                    },
                },
            },
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
            ],
            "javaProperties": {
                "org.graalvm.language.java.home": "<path:ESPRESSO_SUPPORT>",
            },
        },

        "ESPRESSO_TESTS": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.truffle.espresso.test"
            ],
            "distDependencies": [
                "espresso:ESPRESSO",
                "truffle:TRUFFLE_API",
                "truffle:TRUFFLE_INSTRUMENT_TEST",
                "mx:JUNIT",
            ],
            "javaProperties": {
                "native.test.lib": "<path:ESPRESSO_TESTS_NATIVE>/<lib:nativetest>",
                "espresso.test.SingletonContext": "true",
            },
            "testDistribution": True,
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
            "license": "UPL",
            "description": "Espresso launcher using the polyglot API.",
            "allowsJavadocWarnings": True,
        },

        "ESPRESSO_PROCESSOR": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.truffle.espresso.processor",
            ],
            "distDependencies": [],
            "license": "UPL",
            "description": "Espresso annotation processor.",
            "maven": False,
        },

        "ESPRESSO_SUPPORT": {
            "native": True,
            "description": "Espresso support distribution for the GraalVM",
            "platformDependent": True,
            "layout": {
                "./": [
                    "file:mx.espresso/native-image.properties",
                    "file:mx.espresso/reflectconfig.json",
                ],
                "lib/": [
                    "dependency:espresso:com.oracle.truffle.espresso.mokapot/<lib:mokapot>",
                    "dependency:espresso:com.oracle.truffle.espresso.native/<lib:nespresso>"
                ],
                # On MacOS -install_name (Linux's -soname counterpart) is not enough to fool the dynamic linker.
                "lib/<lib:jvm>": "link:<lib:mokapot>",
            },
        },

        "ESPRESSO_PLAYGROUND": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.truffle.espresso.playground"
            ],
            "description": "Espresso experiments",
            "javaProperties": {
                "playground.library": "<path:ESPRESSO_PLAYGROUND_NATIVE>/<lib:playground>"
            },
        },

        "ESPRESSO_PLAYGROUND_NATIVE": {
            "native": True,
            "relpath": True,
            "platformDependent": True,
            "platforms": [
                "linux-amd64",
                "darwin-amd64",
            ],
            "output": "<mxbuild>/playground-native",
            "dependencies": [
                "com.oracle.truffle.espresso.playground.native",
            ],
        },

        "ESPRESSO_TESTS_NATIVE": {
            "native": True,
            "relpath": True,
            "platformDependent": True,
            "output": "<mxbuild>/espresso-test-native",
            "dependencies": [
                "com.oracle.truffle.espresso.test.native",
            ],
            "testDistribution": True,
            "maven": False,
        },
    }
}
