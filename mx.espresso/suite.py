suite = {
    "mxversion": "5.190.6",
    "name": "espresso",

    # ------------- licenses

    "licenses": {
        "GPLv2": {
            "name": "GNU General Public License, version 2",
            "url": "http://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html"
        },
    },
    "defaultLicense": "GPLv2",

    # ------------- imports

    "imports": {
        "suites": [
            {
                "name": "truffle",
                "subdir": True,
                "version": "d763ec481c0d69f0dd5d1617c5f926ed1d4708b3",
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
                "ftp://sourceware.org/pub/libffi/libffi-3.2.1.tar.gz",
            ],
            "sha1": "280c265b789e041c02e5c97815793dfc283fb1e6",
        },
    },

    # ------------- projects

    "projects": {
        "com.oracle.truffle.espresso": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "jniHeaders": True,
            "dependencies": [
                "truffle:TRUFFLE_API",
                "truffle:TRUFFLE_NFI",
            ],
            "annotationProcessors": ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "javaCompliance": "1.8+",
            "checkstyleVersion": "8.8",
        },

        "com.oracle.truffle.espresso.overlay": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
            ],
            "javaCompliance": "1.8+",
            "checkstyleVersion": "8.8",
            "checkPackagePrefix": "false",
        },

        "com.oracle.truffle.espresso.launcher": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "sdk:GRAAL_SDK",
                "sdk:LAUNCHER_COMMON",
            ],
            "javaCompliance": "1.8+",
            "checkstyleVersion": "8.8",
        },

        "com.oracle.truffle.espresso.playground": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "jniHeaders": True,
            "dependencies": [
            ],
            "javaCompliance": "1.8+",
        },

        "com.oracle.truffle.espresso.playground.native": {
            "subDir": "src",
            "native": True,
            "vpath": True,
            "results": [
                "bin/<lib:playground>",
            ],
            "buildDependencies": [
                "com.oracle.truffle.espresso.playground",
            ],
            "buildEnv": {
                "TARGET": "bin/<lib:playground>",
                "CPPFLAGS": "-I<jnigen:com.oracle.truffle.espresso.playground>",
                "OS": "<os>",
            },
        },

        # Native library for Espresso native interface
        "com.oracle.truffle.espresso.native": {
            "subDir": "src",
            "native": True,
            "vpath": True,
            "results": [
                "bin/<lib:nespresso>",
            ],
            "buildDependencies": [
                "com.oracle.truffle.espresso",
            ],
            "buildEnv": {
                "TARGET": "bin/<lib:nespresso>",
                "LIBFFI_SRC": "<path:LIBFFI>",
                "CPPFLAGS": "-I<jnigen:com.oracle.truffle.espresso> -I<path:TRUFFLE_NFI_NATIVE>/include",
                "OS": "<os>",
            },
        },

        "com.oracle.truffle.espresso.test": {
            "subDir": "src",
            "jniHeaders": True,
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.truffle.espresso",
                "truffle:TRUFFLE_INSTRUMENT_TEST",
                "mx:JUNIT",
            ],
            "javaCompliance": "1.8+",
            "checkstyle": "com.oracle.truffle.espresso",
        },

        "com.oracle.truffle.espresso.bench": {
            "subDir": "src",            
            "sourceDirs": ["src"],
            "dependencies": [
            ],
            "javaCompliance": "1.8+",
            "checkstyle": "com.oracle.truffle.espresso",
        },

        # Native library for tests
        "com.oracle.truffle.espresso.test.native": {
            "testProject" : True,
            "subDir": "src",
            "native": True,
            "vpath": True,
            "results": [
                "bin/<lib:nativetest>",
            ],
            "buildDependencies": [
                "com.oracle.truffle.espresso.test",
            ],
            "buildEnv": {
                "TARGET": "bin/<lib:nativetest>",
                "CPPFLAGS": "-I<jnigen:com.oracle.truffle.espresso.test>",
                "OS": "<os>",
            },
        },

        # libjvm Espresso implementation
        "com.oracle.truffle.espresso.mokapot": {
            "subDir": "src",
            "native": True,
            "vpath": True,
            "results": [
                "bin/<lib:mokapot>",
            ],
            "buildDependencies": [
                "com.oracle.truffle.espresso",
            ],
            "buildEnv": {
                "TARGET": "bin/<lib:mokapot>",
                "LIBFFI_SRC": "<path:LIBFFI>",
                "CPPFLAGS": "-I<jnigen:com.oracle.truffle.espresso> -I<path:TRUFFLE_NFI_NATIVE>/include",
                "OS": "<os>",
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
                "nespresso.library": "<path:ESPRESSO_NATIVE>/bin/<lib:nespresso>",
                "mokapot.library": "<path:ESPRESSO_MOKAPOT_NATIVE>/bin/<lib:mokapot>"
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
                "native.test.lib": "<path:ESPRESSO_TESTS_NATIVE>/bin/<lib:nativetest>"
            },
            "testDistribution": True,
        },


        "ESPRESSO_LAUNCHER": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.truffle.espresso.launcher",
            ],
            "distDependencies": [
                "sdk:GRAAL_SDK",
                "sdk:LAUNCHER_COMMON",
            ],
            "license": "UPL",
            "description": "Espresso launcher using the polyglot API.",
            "allowsJavadocWarnings": True,
        },

        "ESPRESSO_OVERLAY": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.truffle.espresso.overlay"
            ],
            "distDependencies": [
            ],
            "description": "Espresso overlay distribution for the GraalVM",
        },

        "ESPRESSO_SUPPORT": {
            "native": True,
            "description": "Espresso support distribution for the GraalVM",
            "layout": {
                "./": [
                    "file:mx.espresso/native-image.properties",
                    "file:mx.espresso/reflectconfig.json",
                ],
            },
        },

        "ESPRESSO_PLAYGROUND": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.truffle.espresso.playground"
            ],
            "distDependencies": [
            ],
            "description": "Espresso experiments",
            "javaProperties": {
                "playground.library": "<path:ESPRESSO_PLAYGROUND_NATIVE>/bin/<lib:playground>"
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

        "ESPRESSO_NATIVE": {
            "native": True,
            "relpath": True,
            "platformDependent": True,
            "platforms": [
                "linux-amd64",
                "darwin-amd64",
            ],
            "output": "<mxbuild>/espresso-native",
            "dependencies": [
                "com.oracle.truffle.espresso.native",
            ],
        },

        "ESPRESSO_MOKAPOT_NATIVE": {
            "native": True,
            "relpath": True,
            "platformDependent": True,
            "platforms": [
                "linux-amd64",
                "darwin-amd64",
            ],
            "output": "<mxbuild>/espresso-mokapot-native",
            "dependencies": [
                "com.oracle.truffle.espresso.mokapot",
            ],
            "description": "Espresso libjvm surrogate",
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


        "ESPRESSO_BENCH": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.truffle.espresso.bench"
            ],
            "distDependencies": [
            ],
            "description": "Espresso benchmarks",
        },

    }
}
