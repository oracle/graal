suite = {
    "mxversion": "5.206.2",
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
                # Custom changes in Truffle (NFI) for Espresso (branch mokapot).
                "version": "3871baeedc128420c007a48bcdff5990a001075c",
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
            "dependencies": [
                "truffle:TRUFFLE_API",
                "truffle:TRUFFLE_NFI",
            ],
            "annotationProcessors": ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "javaCompliance": "1.8+",
            "checkstyle" : "com.oracle.truffle.espresso",
        },

        "com.oracle.truffle.espresso.launcher": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "sdk:GRAAL_SDK",
                "sdk:LAUNCHER_COMMON",
            ],
            "javaCompliance": "1.8+",
            "checkstyle" : "com.oracle.truffle.espresso.launcher",
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
                "CPPFLAGS": "-I<path:TRUFFLE_NFI_NATIVE>/include",
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
            "checkstyle": "com.oracle.truffle.espresso.test",
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
            "platformDependent": True,
            "results": [
                "bin/<lib:mokapot>",
            ],
            "buildDependencies": [
                "com.oracle.truffle.espresso",
            ],
            "buildEnv": {
                "TARGET": "bin/<lib:mokapot>",
                "LIBFFI_SRC": "<path:LIBFFI>",
                "CPPFLAGS": "-I<path:TRUFFLE_NFI_NATIVE>/include",
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
                "espresso.library.path": "<path:ESPRESSO_SUPPORT>/lib",
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
                "native.test.lib": "<path:ESPRESSO_TESTS_NATIVE>/bin/<lib:nativetest>",
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
                    "dependency:espresso:com.oracle.truffle.espresso.mokapot/bin/<lib:mokapot>",
                    "dependency:espresso:com.oracle.truffle.espresso.native/bin/<lib:nespresso>"
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
