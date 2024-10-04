# pylint: disable=line-too-long
suite = {
    "mxversion": "6.27.1",
    "name": "substratevm",
    "version" : "23.1.5",
    "release" : True,
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
            },
        ]
    },

    "libraries" : {
        "RENAISSANCE_HARNESS_v0.9" : {
            "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/renaissance/renaissance-harness_v0.9.0.tar.gz"],
            "digest" : "sha512:068207adf6bbd0a934429f7d6ddba8810e55992d06e131479658a7933bb352ea892d4304f745806dc342a6f7187a434ff2f106c6f8a6ee35ee696ea4fc998f7b",
            "packedResource": True,
        },
        "RENAISSANCE_HARNESS_v0.10" : {
            "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/renaissance/renaissance-harness_v0.10.0.tar.gz"],
            "digest" : "sha512:f19f858ee491b61f4537126336b80c901896ecba6fdb1ce052c4f61d3249cc84730f8f44b77c679faceeee92a3874343ae596f0d91017d4fd215f90f9f33f31b",
            "packedResource": True,
        },
        "RENAISSANCE_HARNESS_v0.11" : {
            "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/renaissance/renaissance-harness_v0.11.0.tar.gz"],
            "digest" : "sha512:23b40b3507f3af124a769d1f91b129ab3a3f4790eb615ad988578f2d21f78454f356c1289f3217cf569cb6d04d88fc37de1eb3dbc3122381328adbcd17de597a",
            "packedResource": True,
        },
        "RENAISSANCE_HARNESS_v0.12" : {
            "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/renaissance/renaissance-harness_v0.12.0.tar.gz"],
            "digest" : "sha512:4592b04fa10e8dc41a2423c62118b4740e4ae35ffc6a87b4b0de2764b41b5139cc04e9ea67144f5df09bd87262dcf24d693c2a296f94c83004e7513232ef3a1a",
            "packedResource": True,
        },
        "RENAISSANCE_HARNESS_v0.13" : {
            "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/renaissance/renaissance-harness_v0.13.0.tar.gz"],
            "digest" : "sha512:11b1c1effbca954948e8b51657c36aed60a806e4e5ded8a7eac8247cd217d05b6d0b761f6341438d137dfc18e16546e01e687f70a1a8cedbfbbc44e917dbf3aa",
            "packedResource": True,
        },
        "SPARK_BREEZE_PATCHED" : {
            "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/breeze_2.11-0.11.2-patched.jar"],
            "digest" : "sha512:754c06ddd1be0938d421ff332328e05ab754ea8fe95620c5d387fdf4bda6193455498ab82c6b449abadb7ca4aea4defc42ed00deb71fc40dfc619644b40990c3",
        },
        "XERCES_IMPL" : {
            "digest" : "sha512:abc3cc088fba82c3ad02e18f8664c1aebed018cf39b835824ec2ff2eb9bf9ca4521d805ddfcb112cdfbd0e2ac093a7be1261e7c3d99560d30f8888da4ab0cc85",
            "maven" : {
                "groupId" : "xerces",
                "artifactId" : "xercesImpl",
                "version" : "2.6.2-jaxb-1.0.6",
           },
        },
        "LLVM_WRAPPER_SHADOWED": {
            "digest" : "sha512:bcb73fed249ef14e61b7be4153cede889de7aba4344884a54a84fc2d78f59a0b56b5045f390c378ffb727f454f26e48f92d2f6d4bad8a94ff05ad3949335c7f6",
            "sourceDigest" : "sha512:31796d30a17df22e6624300f360cef39ce4a8ab06605d31fa4809f16ce347ebfa94e87e2ddedaf4d6e7b7682fa3192d8561bb056d797f09b27c101a9a0d98cde",
            "dependencies" : ["JAVACPP_SHADOWED"],
            "urlbase": "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/native-image",
            "urls": ["{urlbase}/llvm-shadowed-13.0.1-1.5.7.jar"],
            "sourceUrls": ["{urlbase}/llvm-shadowed-13.0.1-1.5.7-sources.jar"],
            "license" : "GPLv2-CPE",
            "moduleName" : "com.oracle.svm.shadowed.org.bytedeco.llvm"
        },
        "JAVACPP_SHADOWED": {
            "digest" : "sha512:2b1d23f4c00f1ee04546aee4467622f78b0cca8d231e9bcb77e507ad6a3afb8bdad31a956d7a8bed801eae44c6f70215bf8f891156521768d9ee9726b3fd860f",
            "sourceDigest" : "sha512:b84f22e2bd85407eb60fb6923d708d1928249064e2e405954558392f295fcb167738d095f4c926c4a470068409cb1db45d2da7729d1650769a68aa237492793f",
            "urlbase": "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/native-image",
            "urls": ["{urlbase}/javacpp-shadowed-1.5.7.jar"],
            "sourceUrls": ["{urlbase}/javacpp-shadowed-1.5.7-sources.jar"],
            "license" : "GPLv2-CPE",
            "moduleName" : "com.oracle.svm.shadowed.org.bytedeco.javacpp"
        },
        "LLVM_PLATFORM_SPECIFIC_SHADOWED": {
            "urlbase": "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/native-image",
            "os_arch": {
                "linux": {
                    "amd64": {
                        "digest": "sha512:5cb593fe98d73fa7e40f10d4b4fb3dcb0a86dc23248089660ea48bb2fab5bb905ca6826eac3b3a9d1abaa41567b55ca809e4466f2b03721ecc6110ab460d5ae3",
                        "urls": ["{urlbase}/llvm-shadowed-13.0.1-1.5.7_1-linux-x86_64.jar"],
                        "moduleName" : "com.oracle.svm.shadowed.org.bytedeco.llvm.linux.x86_64"
                    },
                    "aarch64": {
                        "digest": "sha512:ea31991151c21a2b74f88efa977cde4f3ff06a1501be9998b424e18a3e95d6f708fb95b1bc412050a7884a304ecb960663d5bd5265baee6a18b51d86d2d9940d",
                        "urls": ["{urlbase}/llvm-shadowed-13.0.1-1.5.7_2-linux-arm64.jar"],
                        "moduleName" : "com.oracle.svm.shadowed.org.bytedeco.llvm.linux.arm64"
                    },
                    "riscv64": {
                        "sha1": "51762767783b9997474397cfac1e5d1a0ad59e2f",
                        "urls": ["{urlbase}/llvm-shadowed-13.0.1-1.5.7-linux-riscv64.jar"],
                        "moduleName" : "com.oracle.svm.shadowed.org.bytedeco.llvm.linux.riscv64"
                    },
                    "<others>": {
                        "optional": True,
                    },
                },
                "darwin": {
                    "amd64": {
                        "digest": "sha512:e4ffea5a9f10a79b33b0981cc5e1ee84aa3200118ec4848b09c04ca882ad45fe8e30a495fcd57f829a2eb79a4e81930bfd137d12601df34b0d289d1883e471b8",
                        "urls": ["{urlbase}/llvm-shadowed-13.0.1-1.5.7_1-macosx-x86_64.jar"],
                        "moduleName" : "com.oracle.svm.shadowed.org.bytedeco.llvm.macosx.x86_64"
                    },
                    "aarch64": {
                        # GR-34811
                        "optional": True,
                    },
                },
                "<others>": {
                    "<others>": {
                        "optional": True,
                    }
                }
            },
        },
        "JAVACPP_PLATFORM_SPECIFIC_SHADOWED": {
            "urlbase": "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/native-image",
            "os_arch": {
                "linux": {
                    "amd64": {
                        "digest": "sha512:07b6e40e256ccd9905834c8cfad0fbd1bf351944332d15aaea309a830475c48f4ddb2133bbd4ce9c0322fd951ead097b9aac6ede4c6b798f4a71eee7e6983202",
                        "urls": ["{urlbase}/javacpp-shadowed-1.5.7_1-linux-x86_64.jar"],
                        "moduleName" : "com.oracle.svm.shadowed.org.bytedeco.javacpp.linux.x86_64"
                    },
                    "aarch64": {
                        "digest": "sha512:621799f844d327114a2a1e3aaf80ea2ac972914ae2caf53e8e9b47beb8712de3164fc3ec83b0a3e8352dcda4e2684d3253aa9bb8c1772bca6dba248cfbc2ab1e",
                        "urls": ["{urlbase}/javacpp-shadowed-1.5.7_2-linux-arm64.jar"],
                        "moduleName" : "com.oracle.svm.shadowed.org.bytedeco.javacpp.linux.arm64"
                    },
                    "riscv64": {
                        "sha1": "b00dee62b202898ec899cb7bc03604247d648ceb",
                        "urls": ["{urlbase}/javacpp-shadowed-1.5.7-linux-riscv64.jar"],
                        "moduleName" : "com.oracle.svm.shadowed.org.bytedeco.javacpp.linux.riscv64"
                    },
                    "<others>": {
                        "optional": True,
                    },
                },
                "darwin": {
                    "amd64": {
                        "digest": "sha512:5d10565ab3139fb25708a1791cdbcaa0ad24ef03cdce15902f4ad1e1c4dd7e326d7232a3a08332c60bd66c0e1c9e45261080ddf51dda49bbf4ebb2f36f0b1d87",
                        "urls": ["{urlbase}/javacpp-shadowed-1.5.7_1-macosx-x86_64.jar"],
                        "moduleName" : "com.oracle.svm.shadowed.org.bytedeco.javacpp.macosx.x86_64"
                    },
                    "aarch64": {
                        # GR-34811
                        "optional": True,
                    },
                },
                "<others>": {
                    "<others>": {
                        "optional": True,
                    }
                }
            },
        },
        "LLVM_LLD_STANDALONE": {
            "license" : "Apache-2.0-LLVM",
            "version" : "16.0.1-4-gad8c248269-bg7bf7e45f73",
            "host" : "https://lafo.ssw.uni-linz.ac.at/pub/llvm-org",
            "os_arch": {
                "darwin": {
                    "aarch64": {
                        "urls": ["{host}/llvm-lldonly-llvmorg-{version}-darwin-aarch64.tar.gz"],
                        "digest": "sha512:2a8d1853deb238fa4ee14df0ebb8224b7191eb5f955e9c0f51ff2c6993a9de243eb4721e8af3f785a1ce2ba7e908ec7100e4ba70df7cf61688d6d433892b60f8",
                    },
                    "<others>": {
                        "optional": True,
                    },
                },
                "<others>": {
                    "<others>": {
                        "optional": True,
                    }
                }
            }
        },
    },

    "projects": {
        "com.oracle.svm.util": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "sdk:NATIVEIMAGE",
                "compiler:GRAAL",
            ],
            "requiresConcealed" : {
                "java.base" : ["jdk.internal.module"],
            },
            "javaCompliance" : "17+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "checkstyle": "com.oracle.svm.core",
            "workingSets": "SVM",
            "jacoco" : "include",
        },

        "com.oracle.svm.common": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.util"
            ],
            "requiresConcealed" : {
                "jdk.internal.vm.ci" : [
                    "jdk.vm.ci.meta",
                ]
            },
            "javaCompliance" : "17+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "checkstyle": "com.oracle.svm.core",
            "workingSets": "SVM",
            "jacoco" : "include",
        },

        "com.oracle.svm.processor" : {
            "subDir" : "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "compiler:GRAAL_PROCESSOR"
            ],
            "requires" : [
                "java.compiler" # javax.annotation.processing.*
            ],
            "javaCompliance" : "17+",
            "checkstyle" : "com.oracle.svm.core",
            "workingSets" : "SVM",
            "jacoco" : "exclude",
        },

        "com.oracle.svm.core": {
            "subDir": "src",
            "sourceDirs": [
                "src",
                "headers",
            ],
            "dependencies": [
                "com.oracle.svm.common",
            ],
            "requires" : [
                "java.compiler",
                "jdk.jfr",
                "jdk.management",
                "jdk.zipfs",
            ],
            "requiresConcealed" : {
                "java.base" : [
                    "sun.invoke.util",
                    "sun.net",
                    "sun.net.www",
                    "sun.nio.ch",
                    "sun.reflect.annotation",
                    "sun.reflect.generics.factory",
                    "sun.reflect.generics.reflectiveObjects",
                    "sun.reflect.generics.repository",
                    "sun.reflect.generics.tree",
                    "sun.security.jca",
                    "sun.security.ssl",
                    "sun.security.util",
                    "sun.text.spi",
                    "sun.util",
                    "sun.util.calendar",
                    "sun.util.locale.provider",
                    "sun.util.resources",
                    "jdk.internal.access",
                    "jdk.internal.event",
                    "jdk.internal.loader",
                    "jdk.internal.logger",
                    "jdk.internal.misc",
                    "jdk.internal.module",
                    "jdk.internal.perf",
                    "jdk.internal.platform",
                    "jdk.internal.ref",
                    "jdk.internal.reflect",
                    "jdk.internal.vm",
                    "jdk.internal.util",
                ],
                "java.management": [
                    "com.sun.jmx.mbeanserver",
                    "sun.management",
                ],
                "jdk.management": [
                    "com.sun.management.internal"
                ],
                "jdk.jfr": [
                    "jdk.jfr.events",
                    "jdk.jfr.internal",
                    "jdk.jfr.internal.jfc",
                ],
                "jdk.internal.vm.ci": [
                    "jdk.vm.ci.meta",
                    "jdk.vm.ci.code",
                ],
            },
            "javaCompliance" : "17+",
            "checkstyleVersion" : "10.7.0",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
            ],
            "workingSets": "SVM",
            "jacoco" : "exclude",
        },

        "com.oracle.svm.core.genscavenge": {
            "subDir": "src",
            "sourceDirs": [
                "src",
            ],
            "dependencies": [
                "com.oracle.svm.core",
            ],
            "requires" : [
                "jdk.management",
            ],
            "requiresConcealed" : {
                "java.base": [
                    "sun.nio.ch",
                ],
                "java.management": [
                    "sun.management",
                ],
                "jdk.internal.vm.ci" : [
                    "jdk.vm.ci.code",
                ],
            },
            "checkstyle": "com.oracle.svm.core",
            "javaCompliance" : "17+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
            ],
            "workingSets": "SVM",
            "jacoco" : "exclude",
        },

        "com.oracle.svm.core.graal.amd64": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.core",
            ],
            "requiresConcealed" : {
                "jdk.internal.vm.ci" : [
                    "jdk.vm.ci.code.site",
                ],
            },
            "checkstyle": "com.oracle.svm.core",
            "javaCompliance" : "17+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
            ],
            "workingSets": "SVM",
            "jacoco" : "exclude",
        },
        "com.oracle.svm.core.graal.aarch64": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.core",
            ],
            "requiresConcealed" : {
                "jdk.internal.vm.ci" : [
                    "jdk.vm.ci.code.site",
                ],
            },
            "checkstyle": "com.oracle.svm.core",
            "javaCompliance" : "17+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
            ],
            "workingSets": "SVM",
            "jacoco" : "exclude",
        },
        "com.oracle.svm.core.graal.riscv64": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.core",
            ],
            "requiresConcealed" : {
                "jdk.internal.vm.ci" : [
                    "jdk.vm.ci.code.site",
                ],
            },
            "checkstyle": "com.oracle.svm.core",
            "javaCompliance" : "17+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
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
                "JAVACPP_PLATFORM_SPECIFIC_SHADOWED",
            ],
            "requiresConcealed" : {
                "java.base" : [
                    "jdk.internal.misc",
                ],
                "jdk.internal.vm.ci" : [
                    "jdk.vm.ci.meta",
                    "jdk.vm.ci.code",
                    "jdk.vm.ci.code.site",
                ],
            },
            "checkstyle": "com.oracle.svm.core",
            "javaCompliance" : "17+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
            ],
            "workingSets": "SVM",
            "jacoco" : "exclude",
        },

        "com.oracle.svm.core.posix": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.core.graal.amd64",
                "com.oracle.svm.core.graal.aarch64",
                "com.oracle.svm.core.graal.riscv64",
            ],
            "requiresConcealed" : {
                "java.base" : [
                    "jdk.internal.misc",
                ],
                "jdk.internal.vm.ci" : [
                    "jdk.vm.ci.code",
                    "jdk.vm.ci.meta",
                ],
            },
            "checkstyle": "com.oracle.svm.core",
            "javaCompliance" : "17+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
            ],
            "workingSets": "SVM",
            "spotbugs": "false",
            "jacoco" : "exclude",
        },

        "com.oracle.svm.core.windows": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.core.graal.amd64",
            ],
            "requiresConcealed" : {
                "jdk.internal.vm.ci" : [
                    "jdk.vm.ci.code",
                    "jdk.vm.ci.meta",
                ],
            },
            "checkstyle": "com.oracle.svm.core",
            "javaCompliance" : "17+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
            ],
            "workingSets": "SVM",
            "spotbugs": "false",
            "jacoco" : "exclude",
        },

        "com.oracle.graal.pointsto": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.common",
            ],
            "requires" : [
                "jdk.internal.vm.ci"
            ],
            "requiresConcealed" : {
                "java.base" : [
                    "jdk.internal.misc"
                ],
                "jdk.internal.vm.ci" : [
                    "jdk.vm.ci.meta",
                    "jdk.vm.ci.code",
                ]
            },
            "checkstyle": "com.oracle.svm.core",
            "javaCompliance" : "17+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "workingSets": "SVM",
            "jacoco" : "include",
        },

        "com.oracle.graal.pointsto.standalone": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.graal.pointsto",
            ],
            "requiresConcealed" : {
                "java.base" : [
                    "jdk.internal.misc"
                ],
                "jdk.internal.vm.ci" : [
                    "jdk.vm.ci.code",
                ]
            },
            "checkstyle": "com.oracle.svm.core",
            "javaCompliance" : "17+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "workingSets": "SVM",
            "jacoco" : "exclude",
        },

        "com.oracle.graal.pointsto.standalone.test": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "mx:JUNIT_TOOL",
                "sdk:NATIVEIMAGE",
                "STANDALONE_POINTSTO"
            ],
            "requires": [
                "jdk.unsupported",
                "java.compiler",
            ],
            "requiresConcealed": {
                "jdk.internal.vm.ci": [
                    "jdk.vm.ci.meta",
                ]
            },
            "checkstyle": "com.oracle.svm.test",
            "workingSets": "SVM",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "javaCompliance" : "17+",
            "spotbugs": "false",
            "jacoco" : "exclude",
        },

        "com.oracle.graal.reachability": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.graal.pointsto",
            ],
            "requiresConcealed": {
                "jdk.internal.vm.ci": [
                    "jdk.vm.ci.meta",
                    "jdk.vm.ci.code",
                ]
            },
            "checkstyle": "com.oracle.svm.core",
            "javaCompliance" : "17+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "workingSets": "SVM",
            "jacoco" : "exclude", # experimental code not used in production
        },

        "com.oracle.svm.hosted": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.objectfile",
                "com.oracle.svm.core",
                "com.oracle.graal.reachability"
            ],
            "requires" : [
                "jdk.jfr",
                "jdk.management",
            ],
            "requiresConcealed" : {
                "java.base" : [
                    "jdk.internal",
                    "jdk.internal.access",
                    "jdk.internal.event",
                    "jdk.internal.loader",
                    "jdk.internal.misc",
                    "jdk.internal.vm.annotation",
                    "jdk.internal.org.objectweb.asm",
                    "sun.reflect.annotation",
                    "sun.security.jca",
                    "sun.security.provider",
                    "sun.security.x509",
                    "sun.util.locale.provider",
                    "sun.util.resources",
                    "jdk.internal.module",
                    "sun.text.spi",
                    "jdk.internal.reflect",
                    "sun.util.cldr",
                    "sun.util.locale",
                    "sun.invoke.util",
                ],
                "java.management": [
                    "com.sun.jmx.mbeanserver", # Needed for javadoc links (MXBeanIntrospector,DefaultMXBeanMappingFactory, MXBeanProxy)
                    "sun.management", # Needed for javadoc links (MappedMXBeanType)
                ],
                "jdk.internal.vm.ci" : [
                    "jdk.vm.ci.meta",
                    "jdk.vm.ci.code",
                    "jdk.vm.ci.code.site",
                    "jdk.vm.ci.hotspot",
                    "jdk.vm.ci.runtime",
                ],
                "jdk.management": [
                    "com.sun.management.internal"
                ],
                "jdk.jfr": [
                    "jdk.jfr.internal",
                    "jdk.jfr.internal.jfc",
                ],
            },
            "javaCompliance" : "17+",
            "checkstyleVersion": "10.7.0",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
            ],
            "workingSets": "SVM",
            "jacoco" : "include",
            "jacocoExcludePackage": [
                "com.oracle.svm.hosted.dashboard",
            ],
        },

        "com.oracle.svm.core.foreign": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.core"
            ],
            "requiresConcealed": {
                "java.base": [
                    "jdk.internal.loader",
                    "jdk.internal.foreign",
                    "jdk.internal.foreign.abi",
                    "jdk.internal.foreign.abi.x64",
                    "jdk.internal.foreign.abi.x64.sysv",
                    "jdk.internal.foreign.abi.x64.windows",
                ],
                "jdk.internal.vm.ci" : [
                ],
            },
            "javaCompliance" : "21+",
            "javaPreviewNeeded": "21+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
            ],
            "javac.lint.overrides": "-preview",
            "checkstyle": "com.oracle.svm.hosted",
            "workingSets": "SVM",
            "jacoco" : "include",
        },

        "com.oracle.svm.hosted.foreign": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.hosted",
                "com.oracle.svm.core.foreign"
            ],
            "requiresConcealed": {
                "java.base": [
                    "jdk.internal.foreign.abi",
                ],
                "jdk.internal.vm.ci" : [
                    "jdk.vm.ci.code"
                ],
            },
            "javaCompliance" : "21+",
            "javaPreviewNeeded": "21+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
            ],
            "javac.lint.overrides": "-preview",
            "checkstyle": "com.oracle.svm.hosted",
            "workingSets": "SVM",
            "jacoco" : "include",
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
                        "cflags": ["-Zi", "-O2", "-D_LITTLE_ENDIAN"],
                    },
                },
                "<others>": {
                    "<others>": {
                        "cflags": ["-g", "-gdwarf-4", "-fPIC", "-O2", "-D_LITTLE_ENDIAN", "-ffunction-sections", "-fdata-sections", "-fvisibility=hidden", "-D_FORTIFY_SOURCE=0"],
                    },
                },
            },
            "jacoco" : "exclude",
        },

        "com.oracle.svm.native.reporterchelper": {
            "subDir": "src",
            "native": "shared_lib",
            "deliverable": "reporterchelper",
            "platformDependent": True,
            "use_jdk_headers": True,
            "os_arch": {
                "windows": {
                    "<others>": {
                        "cflags": ["-Wall"]
                    }
                },
                "<others>": {
                    "<others>": {
                        "cflags": ["-Wall", "-Werror"],
                    },
                },
            },
            "jacoco" : "exclude",
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
            "jacoco" : "exclude",
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
                        "cflags": ["-g", "-gdwarf-4", "-fPIC", "-O2", "-ffunction-sections", "-fdata-sections", "-fvisibility=hidden", "-D_FORTIFY_SOURCE=0", "-D_GNU_SOURCE"],
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
            "jacoco" : "exclude",
        },

        "com.oracle.svm.native.jvm.windows": {
            "subDir": "src",
            "native": "static_lib",
            "deliverable" : "jvm",
            "use_jdk_headers" : True,
            "os_arch" : {
                "windows": {
                    "amd64" : {
                        "cflags": ["-MD", "-Zi", "-O2", "-DJNIEXPORT="],
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
            "jacoco" : "exclude",
        },

        "svm-jvmfuncs-fallback-builder": {
            "class" : "SubstrateJvmFuncsFallbacksBuilder",
        },

        "com.oracle.svm.driver": {
            "subDir": "src",
            "sourceDirs": [
                "src",
                "resources"
            ],
            "dependencies": [
                "com.oracle.svm.hosted",
                "com.oracle.svm.driver.launcher",
            ],
            "requires" : [
                "jdk.management",
            ],
            "requiresConcealed" : {
                "java.base" : [
                    "jdk.internal.jimage",
                ],
            },
            "checkstyle": "com.oracle.svm.hosted",
            "workingSets": "SVM",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
            ],
            "javaCompliance" : "17+",
            "spotbugs": "false",
            "jacoco" : "exclude",
        },

        "com.oracle.svm.driver.launcher": {
            "subDir": "src",
            "sourceDirs": [
                "src",
                "resources"
            ],
            "checkstyle": "com.oracle.svm.hosted",
            "workingSets": "SVM",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
            ],
            "javaCompliance" : "17+",
            "spotbugs": "false",
            "jacoco" : "exclude",
        },

        "com.oracle.svm.junit": {
            "subDir": "src",
            "sourceDirs": [
                "src",
                "resources",
            ],
            "dependencies": [
                "com.oracle.svm.core",
                "mx:JUNIT_TOOL",
            ],
            "checkstyle": "com.oracle.svm.core",
            "workingSets": "SVM",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
            ],
            "javaCompliance" : "17+",
            "spotbugs": "false",
            "jacoco" : "exclude",
        },

        "com.oracle.svm.test": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "mx:JUNIT_TOOL",
                "sdk:NATIVEIMAGE",
                "SVM",
            ],
            "requires": [
                "java.compiler",
                "jdk.jfr",
                "java.management",
                "jdk.management.jfr",
                "java.rmi",
            ],
            "requiresConcealed" : {
                "java.base" : [
                    "jdk.internal.misc",
                    "sun.security.jca",
                ],
            },
            "checkstyle": "com.oracle.svm.test",
            "checkstyleVersion" : "10.7.0",
            "workingSets": "SVM",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
            ],
            "javaCompliance" : "17+",
            "spotbugs": "false",
            "jacoco" : "exclude",
        },

        "com.oracle.svm.configure.test": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "mx:JUNIT_TOOL",
                "sdk:NATIVEIMAGE",
                "com.oracle.svm.configure",
            ],
            "checkstyle": "com.oracle.svm.test",
            "workingSets": "SVM",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
            ],
            "javaCompliance" : "17+",
            "spotbugs": "false",
            "testProject": True,
            "jacoco" : "exclude",
        },

        "com.oracle.svm.tutorial" : {
            "subDir": "src",
            "sourceDirs" : ["src"],
            "dependencies" : ["sdk:NATIVEIMAGE"],
            "checkstyle" : "com.oracle.svm.hosted",
            "javaCompliance" : "17+",
            "workingSets" : "SVM",
            "spotbugs" : "false",
            "jacoco" : "exclude",
        },

        "com.oracle.objectfile" : {
            "subDir": "src",
            "sourceDirs" : ["src"],
            "dependencies" : [
                "compiler:GRAAL",
                "sdk:NATIVEIMAGE",
            ],
            "requiresConcealed" : {
                "java.base" : [
                    "jdk.internal.misc",
                    "jdk.internal.ref",
                    "sun.nio.ch",
                ],
                "jdk.internal.vm.ci" : [
                    "jdk.vm.ci.code",
                ],
            },
            "checkstyle" : "com.oracle.svm.hosted",
            "javaCompliance" : "17+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
            ],
            "workingSets" : "SVM",
            "spotbugs" : "false",
            "jacoco" : "exclude",
        },

        "com.oracle.svm.graal": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.hosted",
            ],
            "requiresConcealed" : {
                "java.base" : [
                    "jdk.internal.misc",
                ],
                "jdk.internal.vm.ci" : [
                    "jdk.vm.ci.aarch64",
                    "jdk.vm.ci.code.site",
                    "jdk.vm.ci.runtime",
                ],
            },
            "checkstyle": "com.oracle.svm.hosted",
            "javaCompliance" : "17+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
            ],
            "jacoco" : "include",
            "jacocoExcludePackages": [
                "com.oracle.svm.graal.meta",
                "com.oracle.svm.graal.substitutions",
            ],
        },

        "com.oracle.svm.graal.test": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "mx:JUNIT_TOOL",
                "sdk:NATIVEIMAGE",
                "com.oracle.svm.graal",
            ],
            "requiresConcealed" : {
                "jdk.internal.vm.ci" : [
                    "jdk.vm.ci.meta",
                ],
            },
            "checkstyle": "com.oracle.svm.test",
            "workingSets": "SVM",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
            ],
            "javaCompliance" : "17+",
            "spotbugs": "false",
            "testProject": True,
            "jacoco" : "exclude",
        },

        "com.oracle.svm.thirdparty": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.util",
            ],
            "checkstyle": "com.oracle.svm.core",
            "javaCompliance" : "17+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
            ],
            "workingSets": "SVM",
            "jacoco" : "exclude",
        },

        "com.oracle.svm.bench": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "sdk:NATIVEIMAGE",
            ],
            "checkstyle": "com.oracle.svm.core",
            "javaCompliance" : "17+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
            ],
            "workingSets": "SVM",
            "jacoco" : "exclude",
        },

        "com.oracle.svm.truffle": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.graal",
                "truffle:TRUFFLE_API",
                "truffle:TRUFFLE_RUNTIME",
            ],
            "requiresConcealed" : {
                "java.base" : [
                    "jdk.internal.misc",
                ],
                "jdk.internal.vm.ci": [
                    "jdk.vm.ci.aarch64",
                    "jdk.vm.ci.meta",
                ]
            },
            "checkstyle": "com.oracle.svm.hosted",
            "javaCompliance" : "17+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
            ],
            "workingSets": "SVM",
            "jacoco" : "exclude",
        },

        "com.oracle.svm.truffle.nfi.none": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "javaCompliance" : "17+",
            "workingSets": "SVM",
            "jacoco" : "exclude",
        },

        "com.oracle.svm.truffle.nfi": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.truffle",
            ],
            "checkstyle": "com.oracle.svm.hosted",
            "javaCompliance" : "17+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
                "truffle:TRUFFLE_DSL_PROCESSOR",
            ],
            "workingSets": "SVM",
            "jacoco" : "exclude",
        },

        "com.oracle.svm.truffle.nfi.posix": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.truffle.nfi",
                "com.oracle.svm.core.posix",
            ],
            "checkstyle": "com.oracle.svm.hosted",
            "javaCompliance" : "17+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
                "truffle:TRUFFLE_DSL_PROCESSOR",
            ],
            "workingSets": "SVM",
            "jacoco" : "exclude",
        },

        "com.oracle.svm.truffle.nfi.windows": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.truffle.nfi",
                "com.oracle.svm.core.windows",
            ],
            "checkstyle": "com.oracle.svm.hosted",
            "javaCompliance" : "17+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
                "truffle:TRUFFLE_DSL_PROCESSOR",
            ],
            "workingSets": "SVM",
            "jacoco" : "exclude",
        },

        "com.oracle.svm.polyglot": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "requiresConcealed" : {
                "jdk.internal.vm.ci" : [
                    "jdk.vm.ci.meta",
                ],
            },
            "generatedDependencies": [
                "com.oracle.svm.graal",
            ],
            "checkstyle": "com.oracle.svm.core",
            "javaCompliance" : "17+",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
            ],
            "workingSets": "SVM",
            "spotbugs": "false",
            "jacoco" : "exclude",
        },

        "org.graalvm.polyglot.nativeapi" : {
            "subDir": "src",
            "sourceDirs" : [
                "src",
                "resources",
            ],
            "dependencies" : [
                "sdk:POLYGLOT",
                "sdk:NATIVEIMAGE",
                "com.oracle.svm.hosted",
            ],
            "checkstyle": "com.oracle.svm.core",
            "javaCompliance" : "17+",
            "annotationProcessors" : [
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
            ],
            "workingSets" : "SVM",
            "spotbugs": "false",
            "jacoco" : "exclude",
        },

        "com.oracle.svm.graal.hotspot.libgraal" : {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.graal",
                "compiler:GRAAL",
                "sdk:JNIUTILS",
                "sdk:NATIVEBRIDGE",
            ],
            "requires" : [
            	"jdk.management"
            ],
            "requiresConcealed" : {
                "java.base" : [
                    "jdk.internal.misc",
                ],
                "jdk.internal.vm.ci": [
                    "jdk.vm.ci.meta",
                    "jdk.vm.ci.code",
                    "jdk.vm.ci.hotspot",
                ]
            },
            "checkstyle" : "com.oracle.svm.hosted",
            "javaCompliance" : "17+",
            "annotationProcessors": [
                "truffle:TRUFFLE_LIBGRAAL_PROCESSOR",
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
            ],
            "defaultBuild": False,
            "jacoco" : "exclude",
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
            "requiresConcealed": {
                "jdk.internal.vm.ci": [
                    "jdk.vm.ci.meta",
                ]
            },
            "checkstyle": "com.oracle.svm.hosted",
            "workingSets": "SVM",
            "annotationProcessors": [
            ],
            "javaCompliance" : "17+",
            "spotbugs": "false",
            "jacoco" : "exclude",
        },

        "com.oracle.svm.jvmtiagentbase": {
            "subDir": "src",
            "sourceDirs": [
                "src",
            ],
            "dependencies": [
                "com.oracle.svm.core",
            ],
            "checkstyle": "com.oracle.svm.hosted",
            "workingSets": "SVM",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
            ],
            "javaCompliance" : "17+",
            "spotbugs": "false",
            "jacoco" : "exclude",
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
            "requiresConcealed" : {
                "jdk.internal.vm.ci": [
                    "jdk.vm.ci.meta",
                ]
            },
            "checkstyle": "com.oracle.svm.hosted",
            "workingSets": "SVM",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
            ],
            "javaCompliance" : "17+",
            "spotbugs": "false",
            "jacoco" : "exclude",
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
            "requiresConcealed" : {
                "java.base" : [
                    "jdk.internal.loader",
                    "jdk.internal.org.objectweb.asm",
                ],
            },
            "checkstyle": "com.oracle.svm.hosted",
            "workingSets": "SVM",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
            ],
            "javaCompliance" : "17+",
            "spotbugs": "false",
            "jacoco" : "exclude",
        },

        "com.oracle.svm.truffle.tck" : {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.hosted",
                "truffle:TRUFFLE_RUNTIME",
            ],
            "requiresConcealed": {
                "jdk.internal.vm.ci": [
                    "jdk.vm.ci.meta",
                    "jdk.vm.ci.common",
                ]
            },
            "checkstyle" : "com.oracle.svm.hosted",
            "workingSets": "SVM",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "SVM_PROCESSOR",
            ],
            "javaCompliance" : "17+",
            "jacoco" : "exclude",
        },
    },

    "distributions": {
        #
        # External Distributions
        #
        "SVM_PROCESSOR" : {
            "subDir": "src",
            "dependencies" : [
                "com.oracle.svm.processor",
             ],
            "distDependencies": [
                "compiler:GRAAL_PROCESSOR",
            ],
            "maven": False,
        },

        "SVM": {
            "subDir": "src",
            "description" : "SubstrateVM image builder components",
            "dependencies": [
                "com.oracle.svm.graal",
                "com.oracle.svm.hosted",
                "com.oracle.svm.core",
                "com.oracle.svm.core.graal.amd64",
                "com.oracle.svm.core.graal.aarch64",
                "com.oracle.svm.core.graal.riscv64",
                "com.oracle.svm.core.posix",
                "com.oracle.svm.core.windows",
                "com.oracle.svm.core.genscavenge",
            ],
            "distDependencies": [
                "sdk:NATIVEIMAGE",
                "OBJECTFILE",
                "POINTSTO",
                "compiler:GRAAL",
                "NATIVE_IMAGE_BASE",
            ],
            "moduleInfo" : {
                "name" : "org.graalvm.nativeimage.builder",
                "exports" : [
                    "com.oracle.svm.hosted                        to java.base",
                    "* to org.graalvm.nativeimage.base,jdk.internal.vm.compiler,org.graalvm.nativeimage.driver,org.graalvm.nativeimage.configure,org.graalvm.nativeimage.librarysupport,org.graalvm.nativeimage.junitsupport,org.graalvm.nativeimage.llvm,org.graalvm.nativeimage.agent.jvmtibase,org.graalvm.nativeimage.agent.tracing,org.graalvm.nativeimage.agent.diagnostics,com.oracle.svm.svm_enterprise,com.oracle.svm.svm_enterprise.llvm,com.oracle.svm_enterprise.ml_dataset,org.graalvm.extraimage.builder,com.oracle.svm.extraimage_enterprise,org.graalvm.nativeimage.foreign,org.graalvm.truffle.runtime.svm,com.oracle.truffle.enterprise.svm",
                ],
                "opens" : [
                    "com.oracle.svm.core                          to jdk.internal.vm.compiler",
                    "com.oracle.svm.core.nodes                    to jdk.internal.vm.compiler",
                    "com.oracle.svm.core.graal.nodes              to jdk.internal.vm.compiler",
                    "com.oracle.svm.core.graal.snippets           to jdk.internal.vm.compiler",
                    "com.oracle.svm.hosted.fieldfolding           to jdk.internal.vm.compiler",
                    "com.oracle.svm.hosted.phases                 to jdk.internal.vm.compiler",
                    "com.oracle.svm.hosted.reflect                to jdk.internal.vm.compiler",
                ],
                "requires": [
                    "java.management",
                    "jdk.management",
                ],
                "uses" : [
                    "org.graalvm.nativeimage.Platform",
                    "org.graalvm.compiler.options.OptionDescriptors",
                    "com.oracle.svm.hosted.NativeImageClassLoaderPostProcessing",
                    "java.util.spi.ResourceBundleControlProvider",
                    "com.oracle.svm.core.feature.AutomaticallyRegisteredFeatureServiceRegistration",
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
                        "sun.reflect.generics.scope",
                        "sun.util.calendar",
                        "sun.util.locale",
                        "sun.security.jca",
                        "sun.security.util",
                        "sun.security.provider",
                        "sun.security.ssl",
                        "com.sun.crypto.provider",
                        "sun.reflect.generics.repository",
                        "jdk.internal.org.objectweb.asm",
                        "sun.util.locale.provider",
                        "sun.util.cldr",
                        "sun.util.resources",
                        "sun.invoke.util",
                        "sun.net",
                    ],
                    "java.management": [
                        "sun.management",
                    ],
                },
            },
            "noMavenJavadoc": True,
            "maven": {
                "tag": ["default", "public"],
            },
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
            },
            "maven": False,
        },

        "LIBRARY_SUPPORT": {
            "subDir": "src",
            "description" : "SubstrateVM basic library-support components",
            "dependencies": [
                "com.oracle.svm.polyglot",
                "com.oracle.svm.thirdparty",
            ],
            "distDependencies": [
                "sdk:NATIVEIMAGE",
                "SVM",
            ],
            "moduleInfo" : {
                "name" : "org.graalvm.nativeimage.librarysupport",
                "exports" : [
                    "* to org.graalvm.nativeimage.builder",
                ],
            },
            "maven": {
                "tag": ["default", "public"],
            },
        },

        "JUNIT_SUPPORT": {
            "subDir": "src",
            "description" : "SubstrateVM suppoprt for building JUnit test into image",
            "dependencies": [
                "com.oracle.svm.junit",
            ],
            "distDependencies": [
                "sdk:NATIVEIMAGE",
                "SVM",
                "compiler:GRAAL",
                "mx:JUNIT_TOOL",
            ],
            "moduleInfo" : {
                "name" : "org.graalvm.nativeimage.junitsupport",
                "exports" : [
                    "* to org.graalvm.nativeimage.builder,org.graalvm.nativeimage.base,org.graalvm.nativeimage.pointsto"
                ],
                "requires" : [
                    "static com.oracle.mxtool.junit",
                    "static junit",
                    "static hamcrest",
                ]
            },
            "maven": {
                "tag": ["default", "public"],
            },
        },

        "OBJECTFILE": {
            "subDir": "src",
            "description" : "SubstrateVM object file writing library",
            "dependencies": [
                "com.oracle.objectfile"
            ],
            "distDependencies": [
                "sdk:NATIVEIMAGE",
                "compiler:GRAAL",
            ],
            "moduleInfo" : {
              "name" : "org.graalvm.nativeimage.objectfile",
              "exports" : [
                "com.oracle.objectfile",
                "com.oracle.objectfile.io",
                "com.oracle.objectfile.debuginfo",
                "com.oracle.objectfile.macho",
              ],

              "requiresConcealed" : {
                "java.base" : [
                  "sun.nio.ch",
                  "jdk.internal.ref",
                ],
              }
            },
            "maven": {
                "tag": ["default", "public"],
            },
        },

        "TRUFFLE_RUNTIME_SVM_VERSION": {
            "type": "dir",
            "platformDependent": False,
            "layout": {
                "META-INF/graalvm/org.graalvm.truffle.runtime.svm/version": "dependency:sdk:VERSION/version",
            },
            "description": "SVM Runtime for Truffle version.",
            "maven": False,
        },

        "TRUFFLE_RUNTIME_SVM": {
            "subDir": "src",
            "description" : "SVM Runtime for Truffle languages.",
            "dependencies": [
                "com.oracle.svm.truffle",
                "com.oracle.svm.truffle.nfi",
                "com.oracle.svm.truffle.nfi.posix",
                "com.oracle.svm.truffle.nfi.windows",
                "TRUFFLE_RUNTIME_SVM_VERSION",
            ],
            "distDependencies": [
                "SVM",
                "OBJECTFILE",
                "POINTSTO",
                "truffle:TRUFFLE_RUNTIME",
            ],
            "moduleInfo" : {
                "name" : "org.graalvm.truffle.runtime.svm",
                "exports" : [
                    "com.oracle.svm.truffle.api to org.graalvm.truffle",
                    "* to org.graalvm.truffle.runtime.svm,com.oracle.truffle.enterprise.svm",
                ],
                "opens" : [
                    "com.oracle.svm.truffle to org.graalvm.nativeimage.builder,jdk.internal.vm.compiler",
                    "com.oracle.svm.truffle.nfi to org.graalvm.nativeimage.builder,jdk.internal.vm.compiler",
                    "com.oracle.svm.truffle.nfi.windows to org.graalvm.nativeimage.builder,jdk.internal.vm.compiler",
                    "com.oracle.svm.truffle.nfi.posix to org.graalvm.nativeimage.builder,jdk.internal.vm.compiler",
                    "com.oracle.svm.truffle.api to org.graalvm.nativeimage.builder,jdk.internal.vm.compiler",
                    "com.oracle.svm.truffle.isolated to org.graalvm.nativeimage.builder,jdk.internal.vm.compiler",
                ],
                "requires": [
                    "java.management",
                    "jdk.management",
                    # the runtime might not be available at runtime
                    # the module can still be used with the TruffleBaseFeature
                    "static org.graalvm.truffle.runtime",
                    "static org.graalvm.jniutils",
                ],
                "uses" : [
                    "com.oracle.truffle.api.TruffleLanguage.Provider",
                    "com.oracle.truffle.api.instrumentation.TruffleInstrument.Provider",
                    "com.oracle.truffle.api.provider.TruffleLanguageProvider",
                    "com.oracle.truffle.api.instrumentation.provider.TruffleInstrumentProvider",
                    "com.oracle.truffle.api.TruffleLanguage.Provider",                   # Deprecated
                    "com.oracle.truffle.api.instrumentation.TruffleInstrument.Provider", # Deprecated
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
                    "java.management": [
                        "sun.management",
                    ],
                },
            },
            "noMavenJavadoc": True,
            "maven": {
                "tag": ["default", "public"],
            },
        },

        "TRUFFLE_GRAALVM_SUPPORT" : {
          "native" : True,
          "description" : "Truffle support distribution for SVM",
          "layout" : {
            "native-image.properties" : "file:mx.substratevm/macro-truffle.properties",
          },
          "maven" : False,
        },

        "TRUFFLE_SVM_GRAALVM_SUPPORT" : {
          "native" : True,
          "description" : "Truffle support distribution for SVM",
          "layout" : {
            "native-image.properties" : "file:mx.substratevm/macro-truffle-svm.properties",
          },
          "maven" : False,
        },

        "GRAAL_HOTSPOT_LIBRARY": {
            "subDir": "src",
            "description" : "SubstrateVM HotSpot Graal library support",
            "javaCompliance" : "17+",
            "dependencies": [
                "com.oracle.svm.graal.hotspot.libgraal",
            ],
            "overlaps" : [
                "LIBRARY_SUPPORT"
            ],
            "distDependencies": [
                "SVM",
                "sdk:JNIUTILS",
                "sdk:NATIVEBRIDGE",
            ],
            "defaultBuild": False,
            "maven": False,
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
            "maven": {
                "tag": ["default", "public"],
            },
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
                "com.oracle.svm.driver.launcher",
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
              ],
            },
            "maven": False,
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
                "requiresConcealed" : {
                    "jdk.internal.vm.ci" : [
                        "jdk.vm.ci.meta",
                    ],
                }
            },
            # vm: included as binary, tool descriptor intentionally not copied
            "maven": False,
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
            },
            "maven": False,
        },

        "SVM_CONFIGURE": {
            "subDir": "src",
            "description" : "SubstrateVM native-image configuration tool",
            "mainClass": "com.oracle.svm.configure.ConfigurationTool",
            "dependencies": [
                "com.oracle.svm.configure",
            ],
            "distDependencies": [
                "LIBRARY_SUPPORT",
            ],
            "moduleInfo" : {
                "name" : "org.graalvm.nativeimage.configure",
                "exports" : [
                    "* to org.graalvm.nativeimage.agent.tracing",
                    "com.oracle.svm.configure",
                    "com.oracle.svm.configure.command",
                ],
            },
            "maven": False,
        },

        "NATIVE_IMAGE_BASE": {
            "subDir": "src",
            "description" : "Native Image base that can be shared by native image building and pointsto.",
            "dependencies": [
                "com.oracle.svm.common",
                "com.oracle.svm.util",
            ],
            "distDependencies": [
                "compiler:GRAAL",
                "sdk:NATIVEIMAGE",
            ],
            "exclude": [
            ],
            "moduleInfo" : {
                "name" : "org.graalvm.nativeimage.base",
                "requires" : ["java.sql", "java.xml"],# workaround for GR-47773 on the module-path which requires java.sql (like truffle) or java.xml
                "exports" : [
                    "com.oracle.svm.util                   to org.graalvm.nativeimage.pointsto,org.graalvm.nativeimage.builder,org.graalvm.nativeimage.librarysupport,org.graalvm.nativeimage.driver,org.graalvm.nativeimage.llvm,org.graalvm.nativeimage.agent.jvmtibase,org.graalvm.nativeimage.agent.tracing,org.graalvm.nativeimage.agent.diagnostics,org.graalvm.nativeimage.junitsupport,com.oracle.svm.svm_enterprise,com.oracle.svm_enterprise.ml_dataset,org.graalvm.extraimage.builder,com.oracle.svm.extraimage_enterprise,org.graalvm.extraimage.librarysupport,org.graalvm.nativeimage.foreign,org.graalvm.truffle.runtime.svm,com.oracle.truffle.enterprise.svm",
                    "com.oracle.svm.common.meta            to org.graalvm.nativeimage.pointsto,org.graalvm.nativeimage.builder,org.graalvm.nativeimage.llvm,org.graalvm.extraimage.builder,org.graalvm.nativeimage.foreign,org.graalvm.truffle.runtime.svm,com.oracle.truffle.enterprise.svm",
                    "com.oracle.svm.common.option          to org.graalvm.nativeimage.pointsto,org.graalvm.nativeimage.builder,org.graalvm.nativeimage.driver,org.graalvm.nativeimage.foreign,org.graalvm.truffle.runtime.svm,com.oracle.truffle.enterprise.svm",
                ],
            },
            "maven": {
                "tag": ["default", "public"],
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
                "NATIVE_IMAGE_BASE",
            ],
            "exclude": [
            ],
            "moduleInfo" : {
              "name" : "org.graalvm.nativeimage.pointsto",
              "exports" : [
                "com.oracle.graal.pointsto",
                "com.oracle.graal.pointsto.api",
                "com.oracle.graal.pointsto.heap",
                "com.oracle.graal.pointsto.heap.value",
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
                "com.oracle.graal.pointsto.flow.context.bytecode",
              ],
              "requires": [
                "java.management",
                "jdk.management",
              ],
              "requiresConcealed" : {
                "java.management": [
                  "sun.management",
                ],
                "jdk.internal.vm.ci" : [
                  "jdk.vm.ci.meta",
                  "jdk.vm.ci.common",
                  "jdk.vm.ci.code",
                  "jdk.vm.ci.runtime",
                ],
              }
            },
            "maven": {
                "tag": ["default", "public"],
            },
        },

        "STANDALONE_POINTSTO": {
            "subDir": "src",
            "description" : "A standalone version of SubstrateVM static analysis to use for general pointsto analysis",
            "dependencies": [
                "com.oracle.graal.pointsto.standalone",
            ],
            "distDependencies": [
                "compiler:GRAAL",
                "NATIVE_IMAGE_BASE",
                "POINTSTO"
            ],
            "exclude": [
            ],
            "moduleInfo" : {
                "name" : "org.graalvm.nativeimage.pointsto.standalone",
                "exports" : [
                    "com.oracle.graal.pointsto.standalone",
                ],
                "requires": [
                    "java.management",
                    "jdk.management",
                ],
                "requiresConcealed" : {
                    "java.management": [
                        "sun.management",
                    ],
                    "jdk.internal.vm.ci" : [
                        "jdk.vm.ci.meta",
                        "jdk.vm.ci.common",
                        "jdk.vm.ci.code",
                        "jdk.vm.ci.runtime",
                    ],
                    "jdk.internal.vm.compiler" : [
                        "org.graalvm.compiler.options"
                    ]
                }
            },
            "maven": {
                "tag": ["default", "public"],
            },
        },

        "STANDALONE_POINTSTO_TESTS" : {
            "subDir": "src",
            "relpath" : True,
            "dependencies" : [
                "com.oracle.graal.pointsto.standalone.test",
            ],
            "distDependencies": [
                "mx:JUNIT_TOOL",
                "sdk:NATIVEIMAGE",
                "STANDALONE_POINTSTO",
            ],
            "testDistribution" : True,
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
            "sdk:NATIVEIMAGE",
            "SVM",
            "SVM_CONFIGURE",
          ],
          "testDistribution" : True,
        },

        "POLYGLOT_NATIVE_API" : {
            "subDir": "src",
            "dependencies": [
                "org.graalvm.polyglot.nativeapi",
            ],
            "distDependencies": [
                "sdk:NATIVEIMAGE",
                "sdk:POLYGLOT",
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
                "builder/lib/" : ["dependency:com.oracle.svm.native.reporterchelper"],
                # Note: `ld64.lld` is a symlink to `lld`, but it is dereferenced here.
                "bin/" : ["extracted-dependency:LLVM_LLD_STANDALONE/bin/ld64.lld"],
            },
        },

        "SVM_NFI_NONE_JAR" : {
            "description" : "Almost empty jar for the none NFI backend",
            "subDir": "src",
            "dependencies": [
                "com.oracle.svm.truffle.nfi.none", # to avoid `MX_BUILD_EXPLODED=true mx build` failing
            ],
            "maven": False
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
                "truffle-nfi-none.jar" : "dependency:SVM_NFI_NONE_JAR",
                "builder/svm-none.jar" : "dependency:SVM_NFI_NONE_JAR",
                "builder/clibraries-none/.empty.h" : "file:src/com.oracle.svm.libffi/include/empty.h",
            },
        },

        "SVM_TRUFFLE_RUNTIME_GRAALVM_SUPPORT" : {
            "native" : True,
            "platformDependent" : True,
            "description" : "Native libraries and headers for SubstrateVM NFI support",
            "layout" : {
                "builder/include/" : [
                    "file:src/com.oracle.svm.libffi/include/svm_libffi.h",
                    "extracted-dependency:truffle:TRUFFLE_NFI_GRAALVM_SUPPORT/include/trufflenfi.h",
                ],
                "builder/" : [
                    "extracted-dependency:truffle:LIBFFI_DIST"
                ],
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
                "svm-junit.packages" : "file:mx.substratevm/svm-junit.packages",
            },
        },

        "NATIVE_IMAGE_JUNITCP_SUPPORT" : {
            "native" : True,
            "description" : "Native-image based junit testing support but with running image-builder on classpath",
            "layout" : {
                "native-image.properties" : "file:mx.substratevm/macro-junitcp.properties",
                "svm-junit.packages" : "file:mx.substratevm/svm-junit.packages",
            },
        },

        "SVM_FOREIGN": {
            "subDir": "src",
            "description" : "SubstrateVM support for the Foreign API",
            "dependencies": [
                "com.oracle.svm.hosted.foreign",
            ],
            "distDependencies": [
                "compiler:GRAAL",
                "SVM"
            ],
            "moduleInfo" : {
                "name" : "org.graalvm.nativeimage.foreign",
                "requires" : [
                    "org.graalvm.nativeimage.builder"
                ],
                "exports" : [
                    "* to org.graalvm.nativeimage.builder",
                ],
                "requiresConcealed": {
                    "jdk.internal.vm.ci" : [
                        "jdk.vm.ci.meta",
                        "jdk.vm.ci.code",
                    	"jdk.vm.ci.amd64",
                    ],
                    "java.base": [
                        "jdk.internal.foreign",
                        "jdk.internal.foreign.abi",
                        "jdk.internal.foreign.abi.x64",
                        "jdk.internal.foreign.abi.x64.sysv",
                        "jdk.internal.foreign.abi.x64.windows",
                    ],
                },
            },
            "maven" : False,
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
                "LLVM_PLATFORM_SPECIFIC_SHADOWED",
                "JAVACPP_PLATFORM_SPECIFIC_SHADOWED",
            ],
            "moduleInfo" : {
                "name" : "org.graalvm.nativeimage.llvm",
                "exports" : [
                    "* to org.graalvm.nativeimage.builder,org.graalvm.nativeimage.base",
                ],
            },
            "maven" : False,
        },

        "SVM_TRUFFLE_TCK" : {
            "subDir" : "src",
            "description" : "Truffle TCK",
            "dependencies" : ["com.oracle.svm.truffle.tck"],
            "distDependencies" : ["SVM", "truffle:TRUFFLE_RUNTIME"],
            "maven" : {
                "tag": ["default", "public"],
            },
        },
    },
}
