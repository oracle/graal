# pylint: disable=line-too-long
suite = {
    "mxversion": "5.308.1",
    "name": "substratevm",
    "version" : "21.3.0",
    "release" : False,
    "url" : "https://github.com/oracle/graal/tree/master/substratevm",

    "groupId" : "org.graalvm.nativeimage",
    "developer": {
        "name": "GraalVM Development",
        "email": "graalvm-dev@oss.oracle.com",
        "organization": "Oracle Corporation",
        "organizationUrl": "http://www.graalvm.org/",
    },
    "scm" : {
        "url" : "https://github.com/oracle/graal",
        "read" : "https://github.com/oracle/graal.git",
        "write" : "git@github.com:oracle/graal.git",
    },

    "defaultLicense" : "GPLv2-CPE",

    "versionConflictResolution": "latest",

    "javac.lint.overrides": "-path",

    "imports": {
        "suites": [
            {
                "name": "compiler",
                "subdir": True,
                "urls" : [
                    {"url" : "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind" : "binary"},
                ]
            },
        ]
    },

    "libraries" : {
        "RENAISSANCE_HARNESS_v0.9" : {
            "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/renaissance/renaissance-harness_v0.9.0.tar.gz"],
            "sha1" : "0bef46df4699d896034005d6f3f0422a7075482b",
            "packedResource": True,
        },
        "RENAISSANCE_HARNESS_v0.10" : {
            "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/renaissance/renaissance-harness_v0.10.0.tar.gz"],
            "sha1" : "842e60f56d9871a1fa5700dcc446acbd041e875b",
            "packedResource": True,
        },
        "RENAISSANCE_HARNESS_v0.11" : {
            "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/renaissance/renaissance-harness_v0.11.0.tar.gz"],
            "sha1" : "8d402c1e7c972badfcffdd6c64ed4e791b0dea02",
            "packedResource": True,
        },
        "DACAPO_SVM" : {
            "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/dacapo-9.12-native-image.jar"],
            "sha1" : "5d534f0b7aa9124d9797a180688468d2f126039a",
        },
        "SPARK_BREEZE_PATCHED" : {
            "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/breeze_2.11-0.11.2-patched.jar"],
            "sha1" : "e3327f5d890b5af0f7363a8b3cd95b6ce24bc1ea",
        },
        "XERCES_IMPL" : {
            "sha1" : "006898f2bdfeca5ac996cfff1b76ef98af5aa6f2",
            "maven" : {
                "groupId" : "xerces",
                "artifactId" : "xercesImpl",
                "version" : "2.6.2-jaxb-1.0.6",
           },
        },
        "LLVM_WRAPPER_SHADOWED": {
            "sha1" : "f2d365a8d432d6b2127acda19c5d3418126db9b0",
            "sourceSha1" : "0801daf22b189bbd9d515614a2b79c92af225d56",
            "dependencies" : ["JAVACPP_SHADOWED"],
            "urlbase": "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/native-image",
            "urls": ["{urlbase}/llvm-shadowed-9.0.0-1.5.2.jar"],
            "sourceUrls": ["{urlbase}/llvm-shadowed-9.0.0-1.5.2-sources.jar"],
            "license" : "GPLv2-CPE",
            "moduleName" : "com.oracle.svm.shadowed.org.bytedeco.llvm"
        },
        "JAVACPP_SHADOWED": {
            "sha1" : "212aaddcd73448c7b6da781fb6cde934c667dc2c",
            "sourceSha1" : "3e9cfc02750ba8ea3babc1b8546a50ec36b849a2",
            "urlbase": "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/native-image",
            "urls": ["{urlbase}/javacpp-shadowed-1.5.2.jar"],
            "sourceUrls": ["{urlbase}/javacpp-shadowed-1.5.2-sources.jar"],
            "license" : "GPLv2-CPE",
            "moduleName" : "com.oracle.svm.shadowed.org.bytedeco.javacpp"
        },
        "LLVM_PLATFORM_SPECIFIC_SHADOWED": {
            "urlbase": "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/native-image",
            "os_arch": {
                "linux": {
                    "amd64": {
                        "sha1": "53acc3692e0f67f3b4a4e5fa5b4a5a1de1aa7947",
                        "urls": ["{urlbase}/llvm-shadowed-9.0.0-1.5.2-linux-x86_64.jar"],
                        "moduleName" : "com.oracle.svm.shadowed.org.bytedeco.llvm.linux.x86_64"
                    },
                    "aarch64": {
                        "sha1": "49b2bff3ab0ecea436bd0f8ed64af28e5bdbd03a",
                        "urls": ["{urlbase}/llvm-shadowed-9.0.0-1.5.2-linux-arm64.jar"],
                        "moduleName" : "com.oracle.svm.shadowed.org.bytedeco.llvm.linux.arm64"
                    },
                    "<others>": {
                        "optional": True,
                    },
                },
                "darwin": {
                    "amd64": {
                        "sha1": "d1082bfd227b8f084682a2cd3b06e36f5d046e5e",
                        "urls": ["{urlbase}/llvm-shadowed-9.0.0-1.5.2-macosx-x86_64.jar"],
                        "moduleName" : "com.oracle.svm.shadowed.org.bytedeco.llvm.macosx.x86_64"
                    },
                },
                "<others>": {
                    "<others>": {
                        "optional": True,
                    }
                }
            },
        }
    },

    "projects": {
        "com.oracle.svm.util": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "sdk:GRAAL_SDK",
                "compiler:GRAAL",
            ],
            "javaCompliance": "8+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "checkstyle": "com.oracle.svm.core",
            "workingSets": "SVM",
        },
        "com.oracle.svm.util.jdk11": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.util",
            ],
            "requires" : ["java.instrument"],
            "requiresConcealed" : {
                "java.base" : ["jdk.internal.module"],
            },
            "javaCompliance": "11+",
            "multiReleaseJarVersion": "11",
            "overlayTarget" : "com.oracle.svm.util",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "checkstyle": "com.oracle.svm.core",
            "workingSets": "SVM",
        },

        "com.oracle.svm.core": {
            "subDir": "src",
            "sourceDirs": [
                "src",
                "headers",
            ],
            "dependencies": [
                "com.oracle.svm.util",
            ],
            "javaCompliance": "8+",
            "checkstyleVersion" : "8.36.1",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "workingSets": "SVM",
        },

        "com.oracle.svm.core.containers": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": ["com.oracle.svm.core"],
            "javaCompliance": "8+",
            "workingSets": "SVM",
            "spotbugs": "false",
        },

        "com.oracle.svm.core.jdk8": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": ["com.oracle.svm.core"],
            "javaCompliance": "8",
            "checkstyle": "com.oracle.svm.core",
            "workingSets": "SVM",
        },

        "com.oracle.svm.core.jdk11": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": ["com.oracle.svm.core"],
            "requires" : [
                "java.logging",
                "jdk.unsupported",
                "java.compiler",
            ],
            "requiresConcealed" : {
                "java.base" : [
                    "jdk.internal.module",
                    "jdk.internal.misc",
                    "jdk.internal.logger",
                    "jdk.internal.loader",
                    "sun.util.resources",
                    "sun.text.spi",
                    "jdk.internal.perf",
                    "sun.util.locale.provider",
                    "jdk.internal.loader"
                ],
            },
            "javaCompliance": "11+",
            "checkstyle": "com.oracle.svm.core",
            "workingSets": "SVM",
        },

        "com.oracle.svm.core.jdk14": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": ["com.oracle.svm.core"],
            "requiresConcealed" : {
                "java.base" : [
                    "jdk.internal.access.foreign",
                ],
            },
            "javaCompliance": "14+",
            "checkstyle": "com.oracle.svm.core",
            "workingSets": "SVM",
        },

        "com.oracle.svm.core.jdk15": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.core",
                "com.oracle.svm.core.jdk11"
            ],
            "requiresConcealed" : {
                "java.base" : [
                    "jdk.internal.loader",
                    "jdk.internal.misc",
                    "sun.invoke.util",
                ],
            },
            "javaCompliance": "15+",
            "checkstyle": "com.oracle.svm.core",
            "workingSets": "SVM",
        },

        "com.oracle.svm.core.jdk16": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": ["com.oracle.svm.core"],
            "requiresConcealed" : {
                "java.base" : [
                    "jdk.internal.loader",
                    "jdk.internal.misc",
                    "sun.invoke.util",
                ],
            },
            "javaCompliance": "16+",
            "checkstyle": "com.oracle.svm.core",
            "workingSets": "SVM",
        },

        "com.oracle.svm.core.genscavenge": {
            "subDir": "src",
            "sourceDirs": [
                "src",
            ],
            "dependencies": [
                "com.oracle.svm.core",
            ],
            "checkstyle": "com.oracle.svm.core",
            "javaCompliance": "8+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "workingSets": "SVM",
        },

        "com.oracle.svm.core.graal.amd64": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.core",
            ],
            "checkstyle": "com.oracle.svm.core",
            "javaCompliance": "8+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "workingSets": "SVM",
        },
        "com.oracle.svm.core.graal.aarch64": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.core",
            ],
            "checkstyle": "com.oracle.svm.core",
            "javaCompliance": "8+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "workingSets": "SVM",
        },
        "com.oracle.svm.core.graal.llvm": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.hosted",
                "LLVM_WRAPPER_SHADOWED",
                "LLVM_PLATFORM_SPECIFIC_SHADOWED",
            ],
            "checkstyle": "com.oracle.svm.core",
            "javaCompliance": "8+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "workingSets": "SVM",
        },

        "com.oracle.svm.core.posix": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.core.graal.amd64",
                "com.oracle.svm.core.graal.aarch64",
            ],
            "checkstyle": "com.oracle.svm.core",
            "javaCompliance": "8+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "workingSets": "SVM",
            "spotbugs": "false",
        },

        "com.oracle.svm.core.windows": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.core.graal.amd64",
            ],
            "checkstyle": "com.oracle.svm.core",
            "javaCompliance": "8+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "workingSets": "SVM",
            "spotbugs": "false",
        },

        "com.oracle.graal.pointsto": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.util",
            ],
            "checkstyle": "com.oracle.graal.pointsto",
            "javaCompliance": "8+",
            "checkstyleVersion" : "8.36.1",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "workingSets": "SVM",
        },
        "com.oracle.svm.hosted": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.objectfile",
                "com.oracle.svm.core",
                "com.oracle.graal.pointsto",
            ],
            "javaCompliance": "8+",
            "checkstyleVersion" : "8.36.1",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "workingSets": "SVM",
        },

        "com.oracle.svm.hosted.jdk8": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.hosted",
            ],
            "javaCompliance": "8",
            "checkstyle" : "com.oracle.svm.hosted",
            "checkPackagePrefix" : "false",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "workingSets": "SVM",
        },

        "com.oracle.svm.hosted.jdk11": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.hosted",
                "com.oracle.svm.core.jdk11"
            ],
            "requires" : ["java.instrument"],
            "requiresConcealed" : {
                "jdk.internal.vm.ci": ["jdk.vm.ci.meta"],
                "java.base" : ["jdk.internal.module"],
            },
            "javaCompliance": "11+",
            "checkstyle" : "com.oracle.svm.hosted",
            "multiReleaseJarVersion": "11",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "workingSets": "SVM",
        },
        "com.oracle.svm.hosted.jdk14": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.hosted",
            ],
            "requiresConcealed" : {
                "java.base" :
                    ["jdk.internal.loader"],
                "jdk.internal.vm.ci" :
                    ["jdk.vm.ci.meta"],
            },
            "javaCompliance": "14+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "workingSets": "SVM",
        },
        # Native libraries below explicitly set _FORTIFY_SOURCE to 0. This constant controls how glibc handles some
        # functions that can cause a stack overflow like snprintf. If set to 1 or 2, it causes glibc to use internal
        # functions with extra checking that are not available in all libc implementations. Different distros use
        # different defaults for this constant (e.g., gcc on Ubuntu 18.04 sets it to 2), so we set it to 0 here.
        "com.oracle.svm.native.libchelper": {
            "subDir": "src",
            "native": "static_lib",
            "os_arch": {
                "solaris": {
                    "<others>": {
                        "ignore": "solaris is not supported",
                    },
                },
                "windows": {
                    "<others>": {
                        "cflags": ["-Zi", "-O2", "-D_LITTLE_ENDIAN", "-DJDK_VER=<jdk_ver>"],
                    },
                },
                "<others>": {
                    "<others>": {
                        "cflags": ["-g", "-gdwarf-4", "-fPIC", "-O2", "-D_LITTLE_ENDIAN", "-ffunction-sections", "-fdata-sections", "-fvisibility=hidden", "-D_FORTIFY_SOURCE=0"],
                    },
                },
            },
        },

        "com.oracle.svm.native.darwin": {
            "subDir": "src",
            "native": "static_lib",
            "os_arch": {
                "darwin": {
                    "<others>": {
                        "cflags": ["-ObjC", "-fPIC", "-O1", "-D_LITTLE_ENDIAN", "-ffunction-sections", "-fdata-sections", "-fvisibility=hidden", "-D_FORTIFY_SOURCE=0"],
                    },
                },
                "<others>": {
                    "<others>": {
                        "ignore": "only needed on darwin",
                    },
                },
            },
        },

        "com.oracle.svm.native.jvm.posix": {
            "subDir": "src",
            "native": "static_lib",
            "deliverable" : "jvm",
            "use_jdk_headers" : True,
            "os_arch" : {
                "darwin": {
                    "<others>" : {
                        "cflags": ["-g", "-fPIC", "-O2", "-ffunction-sections", "-fdata-sections", "-fvisibility=hidden"],
                    },
                },
                "linux": {
                    "<others>" : {
                        "cflags": ["-g", "-gdwarf-4", "-fPIC", "-O2", "-ffunction-sections", "-fdata-sections", "-fvisibility=hidden", "-D_FORTIFY_SOURCE=0"],
                    },
                },
                "<others>": {
                    "<others>": {
                        "ignore": "only darwin and linux are supported",
                    },
                },
            },
            "dependencies": [
                "svm-jvmfuncs-fallback-builder",
            ],
        },

        "com.oracle.svm.native.jvm.windows": {
            "subDir": "src",
            "native": "static_lib",
            "deliverable" : "jvm",
            "use_jdk_headers" : True,
            "os_arch" : {
                "windows": {
                    "amd64" : {
                        "cflags": ["-MD", "-Zi", "-O2"],
                    },
                },
                "<others>": {
                    "<others>": {
                        "ignore": "only windows is supported",
                    },
                },
            },
            "dependencies": [
                "svm-jvmfuncs-fallback-builder",
            ],
        },

        "svm-jvmfuncs-fallback-builder": {
            "class" : "SubstrateJvmFuncsFallbacksBuilder",
        },

        "com.oracle.svm.jni": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.hosted",
            ],
            "checkstyle": "com.oracle.svm.core",
            "workingSets": "SVM",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "javaCompliance": "8+",
            "spotbugs": "false",
        },

        "com.oracle.svm.jfr": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.hosted",
            ],
            "checkstyle": "com.oracle.svm.core",
            "javaCompliance": "11+",
            "requires": [
                "jdk.jfr",
                "jdk.management",
                "jdk.unsupported"
            ],
            "requiresConcealed": {
                "jdk.jfr": [
                    "jdk.jfr.internal",
                    "jdk.jfr.internal.consumer",
                    "jdk.jfr.internal.jfc",
                    "jdk.jfr.internal.handlers",
                    "jdk.jfr.events"
                ],
                "jdk.internal.vm.ci": [
                    "jdk.vm.ci.meta",
                    "jdk.vm.ci.code",
                    "jdk.vm.ci.hotspot"
                ],
                "java.base": [
                    "jdk.internal.event",
                    "jdk.internal.misc",
                    "jdk.internal.util.xml",
                    "jdk.internal.util.xml.impl",
                    "jdk.internal.org.xml.sax.helpers"
                ]
            },
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "workingSets": "SVM",
        },

        "com.oracle.svm.driver": {
            "subDir": "src",
            "sourceDirs": [
                "src",
                "resources"
            ],
            "dependencies": [
                "com.oracle.svm.hosted",
            ],
            "checkstyle": "com.oracle.svm.driver",
            "checkstyleVersion" : "8.36.1",
            "workingSets": "SVM",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "javaCompliance": "8+",
            "spotbugs": "false",
        },

        "com.oracle.svm.driver.jdk11": {
            "subDir": "src",
            "sourceDirs": [
                "src",
            ],
            "checkstyle": "com.oracle.svm.driver",
            "workingSets": "SVM",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "javaCompliance" : "11+",
            "spotbugs": "false",
        },

        "svm-compiler-flags-builder": {
            "class" : "SubstrateCompilerFlagsBuilder",
            "dependencies" : [
                "SVM",
            ],
        },

        "com.oracle.svm.junit": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.reflect",
                "mx:JUNIT_TOOL",
            ],
            "checkstyle": "com.oracle.svm.core",
            "workingSets": "SVM",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "javaCompliance": "8+",
            "spotbugs": "false",
        },

        "com.oracle.svm.test": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "mx:JUNIT_TOOL",
                "sdk:GRAAL_SDK",
                "SVM",
            ],
            "checkstyle": "com.oracle.svm.core",
            "workingSets": "SVM",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "javaCompliance": "8+",
            "spotbugs": "false",
        },

        "com.oracle.svm.test.jdk11": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "mx:JUNIT_TOOL",
                "sdk:GRAAL_SDK",
            ],
            "requires" : [
                "java.compiler",
                "jdk.jfr",
            ],
            "checkstyle": "com.oracle.svm.core",
            "workingSets": "SVM",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "javaCompliance": "11+",
            "spotbugs": "false",
            "testProject": True,
        },

        "com.oracle.svm.configure.test": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "mx:JUNIT_TOOL",
                "sdk:GRAAL_SDK",
                "com.oracle.svm.configure",
            ],
            "checkstyle": "com.oracle.svm.core",
            "workingSets": "SVM",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "javaCompliance": "8+",
            "spotbugs": "false",
            "testProject": True,
        },

        "com.oracle.svm.reflect": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.hosted",
            ],
            "checkstyle": "com.oracle.svm.core",
            "workingSets": "SVM",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "javaCompliance": "8+",
            "spotbugs": "false",
        },

        "com.oracle.svm.methodhandles": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.reflect",
            ],
            "checkstyle": "com.oracle.svm.core",
            "workingSets": "SVM",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "javaCompliance": "8+",
            "spotbugs": "false",
        },

        "com.oracle.svm.tutorial" : {
            "subDir": "src",
            "sourceDirs" : ["src"],
            "dependencies" : ["com.oracle.svm.core"],
            "checkstyle" : "com.oracle.svm.truffle",
            "javaCompliance" : "8+",
            "annotationProcessors" : [
                "compiler:GRAAL_PROCESSOR",
            ],
            "workingSets" : "SVM",
            "spotbugs" : "false",
        },

        "com.oracle.objectfile" : {
            "subDir": "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "compiler:GRAAL"
            ],
            "checkstyle" : "com.oracle.svm.hosted",
            "javaCompliance" : "8+",
            "annotationProcessors" : ["compiler:GRAAL_PROCESSOR"],
            "workingSets" : "SVM",
            "spotbugs" : "false",
        },

        "com.oracle.svm.graal": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.hosted",
                "truffle:TRUFFLE_API",
            ],
            "checkstyle": "com.oracle.svm.hosted",
            "javaCompliance": "8+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
        },

        "com.oracle.svm.graal.test": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "mx:JUNIT_TOOL",
                "sdk:GRAAL_SDK",
                "com.oracle.svm.graal",
            ],
            "requiresConcealed" : {
                "jdk.internal.vm.ci": ["jdk.vm.ci.meta"],
            },
            "checkstyle": "com.oracle.svm.core",
            "workingSets": "SVM",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "javaCompliance": "8+",
            "spotbugs": "false",
            "testProject": True,
        },

        "com.oracle.svm.thirdparty": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.core",
            ],
            "checkstyle": "com.oracle.svm.truffle",
            "javaCompliance": "8+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "workingSets": "SVM",
        },

        "com.oracle.svm.bench": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.core",
            ],
            "checkstyle": "com.oracle.svm.truffle",
            "javaCompliance": "8+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "workingSets": "SVM",
        },

        "com.oracle.svm.truffle": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.graal",
            ],
            "checkstyle": "com.oracle.svm.truffle",
            "javaCompliance": "8+",
            "checkstyleVersion" : "8.36.1",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "workingSets": "SVM",
        },

        "com.oracle.svm.truffle.nfi": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.truffle",
            ],
            "checkstyle": "com.oracle.svm.truffle",
            "javaCompliance": "8+",
            "annotationProcessors": [
                "truffle:TRUFFLE_DSL_PROCESSOR",
            ],
            "workingSets": "SVM",
        },

        "com.oracle.svm.truffle.nfi.posix": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.truffle.nfi",
                "com.oracle.svm.core.posix",
            ],
            "checkstyle": "com.oracle.svm.truffle",
            "javaCompliance": "8+",
            "annotationProcessors": [
                "truffle:TRUFFLE_DSL_PROCESSOR",
            ],
            "workingSets": "SVM",
        },

        "com.oracle.svm.truffle.nfi.windows": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.truffle.nfi",
                "com.oracle.svm.core.windows",
            ],
            "checkstyle": "com.oracle.svm.truffle",
            "javaCompliance": "8+",
            "annotationProcessors": [
                "truffle:TRUFFLE_DSL_PROCESSOR",
            ],
            "workingSets": "SVM",
        },

        "com.oracle.svm.polyglot": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "generatedDependencies": [
                "com.oracle.svm.graal",
            ],
            "checkstyle": "com.oracle.svm.truffle",
            "javaCompliance": "8+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "workingSets": "SVM",
            "spotbugs": "false",
        },

        "org.graalvm.polyglot.nativeapi" : {
            "subDir": "src",
            "sourceDirs" : [
                "src",
                "resources",
            ],
            "dependencies" : [
                "sdk:GRAAL_SDK",
                "com.oracle.svm.hosted",
            ],
            "checkstyle": "org.graalvm.polyglot.nativeapi",
            "checkstyleVersion" : "8.36.1",
            "javaCompliance" : "8+",
            "annotationProcessors" : [
                "compiler:GRAAL_PROCESSOR",
            ],
            "workingSets" : "SVM",
            "spotbugs": "false",
        },

        "com.oracle.svm.graal.hotspot.libgraal" : {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.jni",
                "com.oracle.svm.reflect",
                "com.oracle.svm.graal",
                "compiler:GRAAL"
            ],
            "checkstyle" : "com.oracle.svm.hosted",
            "javaCompliance": "8,11+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
        },

        "com.oracle.svm.configure": {
            "subDir": "src",
            "sourceDirs": [
                "src",
                "resources",
            ],
            "dependencies": [
                "com.oracle.svm.core",
            ],
            "checkstyle": "com.oracle.svm.driver",
            "workingSets": "SVM",
            "annotationProcessors": [
            ],
            "javaCompliance": "8+",
            "spotbugs": "false",
        },

        "com.oracle.svm.configure.jdk11": {
            "subDir": "src",
            "sourceDirs": [
                "src",
            ],
            "dependencies": [
                "com.oracle.svm.configure",
            ],
            "checkstyle": "com.oracle.svm.driver",
            "workingSets": "SVM",
            "annotationProcessors": [
            ],
            "javaCompliance": "11+",
            "spotbugs": "false",
        },

        "com.oracle.svm.jvmtiagentbase": {
            "subDir": "src",
            "sourceDirs": [
                "src",
            ],
            "dependencies": [
                "com.oracle.svm.jni",
            ],
            "checkstyle": "com.oracle.svm.driver",
            "workingSets": "SVM",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "javaCompliance": "8+",
            "spotbugs": "false",
        },

        "com.oracle.svm.agent": {
            "subDir": "src",
            "sourceDirs": [
                "src",
                "resources"
            ],
            "dependencies": [
                "JVMTI_AGENT_BASE",
                "com.oracle.svm.configure",
                "com.oracle.svm.driver",
            ],
            "checkstyle": "com.oracle.svm.driver",
            "workingSets": "SVM",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "javaCompliance": "8+",
            "spotbugs": "false",
        },

        "com.oracle.svm.diagnosticsagent": {
            "subDir": "src",
            "sourceDirs": [
                "src",
                "resources"
            ],
            "dependencies": [
                "JVMTI_AGENT_BASE",
            ],
            "checkstyle": "com.oracle.svm.driver",
            "workingSets": "SVM",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "javaCompliance": "8+",
            "spotbugs": "false",
        },

        "com.oracle.svm.truffle.tck" : {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.hosted",
            ],
            "checkstyle" : "com.oracle.svm.truffle",
            "workingSets": "SVM",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "javaCompliance": "1.8",
        },
    },

    "distributions": {
        #
        # External Distributions
        #
        "SVM": {
            "subDir": "src",
            "description" : "SubstrateVM image builder components",
            "dependencies": [
                "com.oracle.svm.graal",
                "com.oracle.svm.truffle",
                "com.oracle.svm.hosted",
                "com.oracle.svm.hosted.jdk8",
                "com.oracle.svm.hosted.jdk11",
                "com.oracle.svm.hosted.jdk14",
                "com.oracle.svm.core",
                "com.oracle.svm.core.jdk8",
                "com.oracle.svm.core.jdk11",
                "com.oracle.svm.core.jdk14",
                "com.oracle.svm.core.jdk15",
                "com.oracle.svm.core.jdk16",
                "com.oracle.svm.core.graal.amd64",
                "com.oracle.svm.core.graal.aarch64",
                "com.oracle.svm.core.posix",
                "com.oracle.svm.core.windows",
                "com.oracle.svm.core.genscavenge",
                "com.oracle.svm.core.containers",
                "com.oracle.svm.jni",
                "com.oracle.svm.jfr",
                "com.oracle.svm.reflect",
                "com.oracle.svm.methodhandles"
            ],
            "manifestEntries" : {
                "Premain-Class": "com.oracle.svm.hosted.agent.NativeImageBytecodeInstrumentationAgent",
            },
            "distDependencies": [
                "sdk:GRAAL_SDK",
                "OBJECTFILE",
                "POINTSTO",
                "compiler:GRAAL",
            ],
            "moduleInfo" : {
                "name" : "org.graalvm.nativeimage.builder",
                "exports" : [
                    "com.oracle.svm.core.configure", # even Feature impls on class-path need access, thus unqualified
                    "com.oracle.svm.core.jdk", # Uses of com.oracle.svm.core.jdk.StackTraceUtils
                    "com.oracle.svm.core.snippets", # Uses of com.oracle.svm.core.snippets.KnownIntrinsics
                    "com.oracle.svm.core", # Uses of com.oracle.svm.core.TypeResult
                    "com.oracle.svm.core.util", # Uses of com.oracle.svm.core.util.VMError
                    "com.oracle.svm.hosted                        to java.base",
                    "com.oracle.svm.hosted.agent                  to java.instrument",
                    "com.oracle.svm.truffle.api                   to org.graalvm.truffle",
                    "* to jdk.internal.vm.compiler,org.graalvm.nativeimage.driver,org.graalvm.nativeimage.configure,org.graalvm.nativeimage.librarysupport,org.graalvm.nativeimage.llvm,org.graalvm.nativeimage.agent.jvmtibase,org.graalvm.nativeimage.agent.tracing,org.graalvm.nativeimage.agent.diagnostics,com.oracle.svm.svm_enterprise",
                ],
                "opens" : [
                    "com.oracle.svm.core.nodes                    to jdk.internal.vm.compiler",
                    "com.oracle.svm.core.graal.nodes              to jdk.internal.vm.compiler",
                    "com.oracle.svm.core.graal.snippets           to jdk.internal.vm.compiler",
                    "com.oracle.svm.hosted.fieldfolding           to jdk.internal.vm.compiler",
                ],
                "requires": [
                    "java.management",
                    "jdk.management",
                    "java.xml.crypto",
                    "java.desktop",
                    "java.security.sasl",
                    "java.smartcardio",
                    "java.net.http",
                    "jdk.sctp",
                    "jdk.scripting.nashorn@11..14",
                ],
                "uses" : [
                    "org.graalvm.nativeimage.Platform",
                    "com.oracle.truffle.api.TruffleLanguage.Provider",
                    "com.oracle.truffle.api.instrumentation.TruffleInstrument.Provider",
                    "com.oracle.svm.hosted.agent.NativeImageBytecodeInstrumentationAgentExtension",
                ],
                "requiresConcealed": {
                    "jdk.internal.vm.ci": [
                        "jdk.vm.ci.common",
                        "jdk.vm.ci.meta",
                        "jdk.vm.ci.code",
                        "jdk.vm.ci.services",
                        "jdk.vm.ci.runtime",
                        "jdk.vm.ci.amd64",
                        "jdk.vm.ci.aarch64",
                        "jdk.vm.ci.hotspot",
                    ],
                    "java.base": [
                        "sun.reflect.annotation",
                        "sun.reflect.generics.reflectiveObjects",
                        "sun.reflect.generics.repository",
                        "sun.reflect.generics.tree",
                        "sun.util.calendar",
                        "sun.security.jca",
                        "sun.security.util",
                        "sun.security.provider",
                        "com.sun.crypto.provider",
                        "sun.reflect.generics.repository",
                        "jdk.internal.org.objectweb.asm",
                        "sun.util.locale.provider",
                        "sun.util.resources",
                        "sun.invoke.util",
                    ],
                    "java.xml.crypto": [
                        "org.jcp.xml.dsig.internal.dom",
                    ],
                },
            },
        },

        "SVM_LIBFFI": {
            "subDir": "src",
            "description" : "SubstrateVM support for Truffle NFI LibFFI backend",
            "dependencies": [
                "com.oracle.svm.truffle.nfi",
                "com.oracle.svm.truffle.nfi.posix",
                "com.oracle.svm.truffle.nfi.windows",
            ],
            "distDependencies": [
                "SVM",
            ],
        },

        "JVMTI_AGENT_BASE": {
            "subDir": "src",
            "description": "Base framework for creating a JVMTI agent.",
            "dependencies": [
                "com.oracle.svm.jvmtiagentbase",
            ],
            "distDependencies": [
                "LIBRARY_SUPPORT",
                "SVM_DRIVER",
            ],
            "moduleInfo" : {
                "name" : "org.graalvm.nativeimage.agent.jvmtibase",
                "exports" : [
                    "com.oracle.svm.jvmtiagentbase",
                    "com.oracle.svm.jvmtiagentbase.jvmti",
                ],
                "requires" : [
                    "static com.oracle.mxtool.junit",
                    "static junit",
                    "static hamcrest",
                ],
            },
        },

        "LIBRARY_SUPPORT": {
            "subDir": "src",
            "description" : "SubstrateVM basic library-support components",
            "dependencies": [
                "com.oracle.svm.junit",
                "com.oracle.svm.polyglot",
                "com.oracle.svm.thirdparty",
            ],
            "distDependencies": [
                "sdk:GRAAL_SDK",
                "SVM",
                "OBJECTFILE",
                "compiler:GRAAL",
                "mx:JUNIT_TOOL",
            ],
            "moduleInfo" : {
                "name" : "org.graalvm.nativeimage.librarysupport",
                "requires" : [
                    "static com.oracle.mxtool.junit",
                    "static junit",
                    "static hamcrest",
                ],
                "exports" : [
                    "* to org.graalvm.nativeimage.builder",
                ],
            },
        },

        "OBJECTFILE": {
            "subDir": "src",
            "description" : "SubstrateVM object file writing library",
            "dependencies": [
                "com.oracle.objectfile"
            ],
            "distDependencies": [
                "compiler:GRAAL",
            ],
            "moduleInfo" : {
              "name" : "org.graalvm.nativeimage.objectfile",
              "exports" : [
                "com.oracle.objectfile",
                "com.oracle.objectfile.io",
                "com.oracle.objectfile.debuginfo",
              ],

              "requires" : ["jdk.unsupported"],
              "requiresConcealed" : {
                "java.base" : [
                  "sun.nio.ch",
                  "jdk.internal.ref",
                ],
              }
            },
        },

        "GRAAL_HOTSPOT_LIBRARY": {
            "subDir": "src",
            "description" : "SubstrateVM HotSpot Graal library support",
            "javaCompliance": "8,11+",
            "dependencies": [
                "com.oracle.svm.graal.hotspot.libgraal",
            ],
            "overlaps" : [
                "LIBRARY_SUPPORT"
            ],
            "distDependencies": [
                "SVM",
            ],
        },

        #
        # Native Projects
        #
        "SVM_HOSTED_NATIVE": {
            "native": True,
            "platformDependent" : True,
            "platforms" : [
                "linux-amd64",
                "darwin-amd64",
                "windows-amd64",
            ],
            "layout": {
                "<os>-<arch>/": [
                    "dependency:com.oracle.svm.native.libchelper/*",
                    "dependency:com.oracle.svm.native.darwin/*",
                    "dependency:com.oracle.svm.native.jvm.posix/*",
                    "dependency:com.oracle.svm.native.jvm.windows/*",
                ],
            },
            "description" : "SubstrateVM image builder native components",
            "maven": True
        },

        #
        # Internal Distributions
        #
        "SVM_DRIVER": {
            "subDir": "src",
            "description" : "SubstrateVM native-image building tool",
            "mainClass": "com.oracle.svm.driver.NativeImage",
            "dependencies": [
                "com.oracle.svm.driver",
                "com.oracle.svm.driver.jdk11",
                "svm-compiler-flags-builder",
            ],
            "distDependencies": [
                "LIBRARY_SUPPORT",
            ],
            "moduleInfo" : {
              "name" : "org.graalvm.nativeimage.driver",
              "exports" : [
                "com.oracle.svm.driver",
                "com.oracle.svm.driver.metainf",
              ],
              "uses" : [
                "org.graalvm.compiler.options.OptionDescriptors",
              ],
              "requires" : [
                "org.graalvm.nativeimage.builder",
                "java.management",
                "jdk.management",
                "static com.oracle.mxtool.junit",
                "static junit",
                "static hamcrest",
              ],
            },
        },

        "SVM_AGENT": {
            "subDir": "src",
            "description" : "SubstrateVM native-image-agent library",
            "dependencies": [
                "com.oracle.svm.agent",
                "com.oracle.svm.configure",
            ],
            "distDependencies": [
                "JVMTI_AGENT_BASE",
                "LIBRARY_SUPPORT",
                "SVM_DRIVER",
                "SVM_CONFIGURE"
            ],
            "moduleInfo" : {
                "name" : "org.graalvm.nativeimage.agent.tracing",
                "exports" : [
                    "com.oracle.svm.agent",
                ],
                "requires" : [
                    "static com.oracle.mxtool.junit",
                    "static junit",
                    "static hamcrest",
                ],
                "requiresConcealed" : {
                    "jdk.internal.vm.ci" : [
                        "jdk.vm.ci.meta",
                    ],
                }
            },
            # vm: included as binary, tool descriptor intentionally not copied
        },

        "SVM_DIAGNOSTICS_AGENT": {
            "subDir": "src",
            "description" : "Native-image diagnostics agent",
            "dependencies": [
                "com.oracle.svm.diagnosticsagent",
            ],
            "distDependencies": [
                "JVMTI_AGENT_BASE",
                "LIBRARY_SUPPORT",
            ],
            "moduleInfo" : {
                "name" : "org.graalvm.nativeimage.agent.diagnostics",
                "exports" : [
                    "com.oracle.svm.diagnosticsagent",
                ],
                "requires" : [
                    "static com.oracle.mxtool.junit",
                    "static junit",
                    "static hamcrest",
                ],
            },
        },

        "SVM_CONFIGURE": {
            "subDir": "src",
            "description" : "SubstrateVM native-image configuration tool",
            "mainClass": "com.oracle.svm.configure.ConfigurationTool",
            "dependencies": [
                "com.oracle.svm.configure",
                "com.oracle.svm.configure.jdk11",
            ],
            "distDependencies": [
                "LIBRARY_SUPPORT",
            ],
            "moduleInfo" : {
                "name" : "org.graalvm.nativeimage.configure",
                "exports" : [
                    "* to org.graalvm.nativeimage.agent.tracing",
                    "com.oracle.svm.configure",
                ],
                "requires" : [
                    "static com.oracle.mxtool.junit",
                    "static junit",
                    "static hamcrest",
                ],
            },
        },


        "POINTSTO": {
            "subDir": "src",
            "description" : "SubstrateVM static analysis to find ahead-of-time the code",
            "dependencies": [
                "com.oracle.svm.util",
                "com.oracle.graal.pointsto",
            ],
            "distDependencies": [
                "compiler:GRAAL",
            ],
            "exclude": [
            ],
            "moduleInfo" : {
              "name" : "org.graalvm.nativeimage.pointsto",
              "exports" : [
                "com.oracle.svm.util",
                "com.oracle.graal.pointsto",
                "com.oracle.graal.pointsto.api",
                "com.oracle.graal.pointsto.reports",
                "com.oracle.graal.pointsto.constraints",
                "com.oracle.graal.pointsto.util",
                "com.oracle.graal.pointsto.meta",
                "com.oracle.graal.pointsto.flow",
                "com.oracle.graal.pointsto.flow.builder",
                "com.oracle.graal.pointsto.nodes",
                "com.oracle.graal.pointsto.phases",
                "com.oracle.graal.pointsto.results",
                "com.oracle.graal.pointsto.typestate",
                "com.oracle.graal.pointsto.infrastructure",
                "com.oracle.graal.pointsto.flow.context.object",
              ],
              "requiresConcealed" : {
                "jdk.internal.vm.ci" : [
                  "jdk.vm.ci.meta",
                  "jdk.vm.ci.common",
                  "jdk.vm.ci.code",
                  "jdk.vm.ci.runtime",
                ],
              }
            },
        },

        "SVM_TESTS" : {
          "subDir": "src",
          "relpath" : True,
          "dependencies" : [
            "com.oracle.svm.test",
            "com.oracle.svm.configure.test",
            "com.oracle.svm.graal.test",
          ],
          "distDependencies": [
            "mx:JUNIT_TOOL",
            "sdk:GRAAL_SDK",
            "SVM",
            "SVM_CONFIGURE",
          ],
          "testDistribution" : True,
        },

        "SVM_TESTS_JDK11" : {
            "subDir": "src",
            "relpath" : True,
            "dependencies" : [
                "com.oracle.svm.test.jdk11",
            ],
            "distDependencies": [
                "mx:JUNIT_TOOL",
                "SVM",
            ],
            "javaCompliance": "11+",
            "testDistribution" : True,
        },

        "POLYGLOT_NATIVE_API" : {
            "subDir": "src",
            "dependencies": [
                "org.graalvm.polyglot.nativeapi",
            ],
            "distDependencies": [
                "sdk:GRAAL_SDK",
                "SVM",
            ],
            "maven": False
        },

        "POLYGLOT_NATIVE_API_HEADERS" : {
            "native" : True,
            "platformDependent" : True,
            "description" : "polyglot.nativeapi header files for the GraalVM build process",
            "layout" : {
                "./" : [
                    "extracted-dependency:POLYGLOT_NATIVE_API/*.h",
                ],
            },
        },

        "SVM_GRAALVM_SUPPORT" : {
            "native" : True,
            "platformDependent" : True,
            "description" : "SubstrateVM support distribution for the GraalVM",
            "layout" : {
                "clibraries/" : ["extracted-dependency:substratevm:SVM_HOSTED_NATIVE"],
                "builder/clibraries/" : ["extracted-dependency:substratevm:SVM_HOSTED_NATIVE"],
            },
        },

        "SVM_NFI_GRAALVM_SUPPORT" : {
            "native" : True,
            "platformDependent" : True,
            "description" : "Native libraries and headers for SubstrateVM NFI support",
            "layout" : {
                "native-image.properties": "file:mx.substratevm/language-nfi.properties",
                "builder/clibraries-libffi/" : [
                    "extracted-dependency:truffle:LIBFFI_DIST"
                ],
                "builder/clibraries-libffi/include/" : [
                    "file:src/com.oracle.svm.libffi/include/svm_libffi.h",
                    "extracted-dependency:truffle:TRUFFLE_NFI_GRAALVM_SUPPORT/include/trufflenfi.h",
                ],
                # The following files are intentionally left empty. The "none" backend is actually nothing, but we still
                # need some files so native-image doesn't complain about missing files on the classpath.
                "truffle-nfi-none.jar" : "string:",
                "builder/svm-none.jar" : "string:",
                "builder/clibraries-none/.empty.h" : "file:src/com.oracle.svm.libffi/include/empty.h",
            },
        },

        "NATIVE_IMAGE_GRAALVM_SUPPORT" : {
            "native" : True,
            "platformDependent" : True,
            "description" : "Native Image support distribution for the GraalVM",
            "os_arch" : {
                "windows": {
                    "<others>" : {
                        "layout" : {
                            "bin/" : "file:mx.substratevm/rebuild-images.cmd",
                        },
                    },
                },
                "<others>": {
                    "<others>": {
                        "layout" : {
                            "bin/rebuild-images" : "file:mx.substratevm/rebuild-images.sh",
                        },
                    },
                },
            },
        },

        "NATIVE_IMAGE_LICENSE_GRAALVM_SUPPORT" : {
            "native" : True,
            "platformDependent" : False,
            "description" : "Native Image support distribution for the GraalVM",
            "layout" : {
                "LICENSE_NATIVEIMAGE.txt" : "file:LICENSE",
            },
        },

        "NATIVE_IMAGE_JUNIT_SUPPORT" : {
            "native" : True,
            "description" : "Native-image based junit testing support",
            "layout" : {
                "native-image.properties" : "file:mx.substratevm/macro-junit.properties",
            },
        },

        "SVM_LLVM" : {
            "subDir" : "src",
            "description" : "LLVM backend for Native Image",
            "dependencies" : ["com.oracle.svm.core.graal.llvm"],
            "distDependencies" : [
                "SVM",
                "sdk:LLVM_TOOLCHAIN"
            ],
            "javaProperties": {
                "llvm.bin.dir": "<path:LLVM_TOOLCHAIN>/bin/",
            },
            "exclude": [
                "LLVM_WRAPPER_SHADOWED",
                "LLVM_PLATFORM_SPECIFIC_SHADOWED"
            ],
            "moduleInfo" : {
                "name" : "org.graalvm.nativeimage.llvm",
                "exports" : [
                    "* to org.graalvm.nativeimage.builder",
                ],
            },
            "maven" : False,
        },

        "SVM_TRUFFLE_TCK" : {
            "subDir" : "src",
            "description" : "Truffle TCK",
            "dependencies" : ["com.oracle.svm.truffle.tck"],
            "distDependencies" : ["SVM"],
            "maven" : True,
        },
    },
}
