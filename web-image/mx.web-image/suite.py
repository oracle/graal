suite = {
    "mxversion": "7.33.0",
    "name": "web-image",
    "versionConflictResolution": "latest",
    "version": "1.0",
    "release": False,
    "groupId": "org.graalvm.webimage",
    "imports": {
        "suites": [
            {
                "name": "substratevm",
                "subdir": "true",
            },
            # Dynamic imports
            {
                "name": "graal-nodejs",
                "subdir": True,
                "dynamic": True,
                "version": "20cf920b900b643c6e8ab05cd0a5e66e2ba4e687",
                "urls": [
                    {"url": "https://github.com/graalvm/graaljs.git", "kind": "git"},
                ],
            },
            {
                "name": "graal-js",
                "subdir": True,
                "dynamic": True,
                "version": "20cf920b900b643c6e8ab05cd0a5e66e2ba4e687",
                "urls": [
                    {"url": "https://github.com/graalvm/graaljs.git", "kind": "git"},
                ],
            },
        ]
    },
    "libraries": {
        # ------------- Libraries -------------
        "GOOGLE_CLOSURE": {
            "digest": "sha512:b7051704edccbf221054471ed3690b617d9e32b3d4ba25d702f03b664b5f4a5e2dbec05741831e1027dda593fa4a8c28ddc82cea926c946f659db77b3f327973",
            "sourceDigest": "sha512:5b0c537e8c7c26f80e4ee9aa0c10e4cfa29076947120e6bd2a7a1f9789ee809600c73aa97bf2b30a6d60b5130f367cf19bfcda6dcafdaa8f8a1529943a0f2227",
            "maven": {
                "groupId": "com.google.javascript",
                "artifactId": "closure-compiler",
                "version": "v20210907",
            },
        },
        # https://github.com/apache/netbeans-html4j
        "NET_JAVA_HTML": {
            "digest": "sha512:93f272ac3ccb89d40b95866f3dfa7856c4900d7fa7f4efe7ed08f2787d96f8a977cea2ed750e315679b8f3f73520229dbdb18f04b88f84cabaa0bba4970312b5",
            "sourceDigest": "sha512:be09bcface1b5ebb8abb27ba95450cbaca56df5c76f459ec96f0a61cdc5ffbefab7873b08cae9d9d2e0e38929bfffabe38592260127cd4a235e29463a91aa068",
            "maven": {
                "groupId": "org.netbeans.html",
                "artifactId": "net.java.html",
                "version": "1.7",
            },
        },
        "NET_JAVA_HTML_BOOT": {
            "digest": "sha512:6d6cc01ec56ce85e728e56296ae154a7fe4a38c3a7be85ae1a04671b5ac0c600b0d67940e0c4d850b9d53e987a7222598cd0f33cff2172773194da76bd176b50",
            "sourceDigest": "sha512:620a343e7641716b93ed9cd846998098a3c1bae633024900e3224868adc07df9261f2ebdcd5bf796bfd52e27f0e594bdbda274fea3df3c616e4db2b689824535",
            "maven": {
                "groupId": "org.netbeans.html",
                "artifactId": "net.java.html.boot",
                "version": "1.7",
            },
        },
        "NET_JAVA_HTML_JSON": {
            "digest": "sha512:0b3a4ecd885fffe4695758bf7712655ecb076aeebc3bb5888be031b033ae133a3e854030854f8821e6344ae0d291b872d51e740e7b0b0d150fe14800b16af356",
            "sourceDigest": "sha512:1404c5d17ed45755d0e7ca393994d430f29501d7da9b02bfe1e22841b5d43b15fac8c578cba4fdb8647f0fac1d75916e4bf870b4eed59f98fe6a33bff6132bd2",
            "maven": {
                "groupId": "org.netbeans.html",
                "artifactId": "net.java.html.json",
                "version": "1.7",
            },
        },
        "NET_JAVA_HTML_JSON_TCK": {
            "digest": "sha512:e420247f14f934d1b0abdeb50ab7ea1b8837e3e4aa5c469daf6ee1996475aaee883ef47156576d0badb44323e4a2801463aaaf0853b7be220836233d6d0a43f8",
            "sourceDigest": "sha512:7e57b8bb39c30a1931c1d6d43f2b4e48aa05e1fdca4e7c96acc4a29b7725bde565f06cf6870e6aaaf608f3a6ed286a8b7c861f533d55f225a8c87c5ce6c8fe70",
            "maven": {
                "groupId": "org.netbeans.html",
                "artifactId": "net.java.html.json.tck",
                "version": "1.7",
            },
        },
        # virtual file system that substitutes NIO and IO
        "JIMFS_BFS": {
            "digest": "sha512:dac2d913bcae2ed216c6fd0658a9b3d080741d5d512133834d727ba41e1a1a013574885c9d21d27594246faf361a45c5af8b7883e6f1ce34843e59b9e181a565",
            "sourceDigest": "sha512:409570c18b01ea29f8c3ac656cf9f1f3540c71d64d26f7a02ff906227eec9d2797643904b003f13bbe86d2e75ed954a5a816984db915e987724bed2b4ce56e68",
            "urlbase": "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/web-image",
            "urls": ["{urlbase}/jimfs-1.2-oracle-00001.jar"],
            "sourceUrls": ["{urlbase}/jimfs-1.2-oracle-00001-sources.jar"],
            "dependencies": ["GUAVA_BFS"],
        },
        # Dependency of JIMFS
        "GUAVA_BFS": {
            "digest": "sha512:02afa0937235a5dab09957a33bf6ce0461419e7e3e11f76a1fe5f0a3d44a54dabdfc2c3097a2970becec889bb30bd3b27f26fe7c83d38a365ba792dea495ea40",
            "sourceDigest": "sha512:1b2090bf9a5943cbe1c76759042146cec4290ae7e71a24122dfa0a81da59072195d6610adf4514b54b047bb6d4394254017babf06a8cd351f57375366b320c04",
            "urlbase": "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/web-image",
            "urls": ["{urlbase}/guava-31.0.1-jre-oracle-00001.jar"],
            "sourceUrls": ["{urlbase}/guava-31.0.1-jre-oracle-00001-sources.jar"],
        },
    },
    # -------------    Projects  -------------
    "projects": {
        # core projects
        "com.oracle.svm.webimage": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "compiler:GRAAL",
                "substratevm:SVM",
                "substratevm:SVM_CONFIGURE",
                "org.graalvm.webimage.api",
                "SVM_WASM_JIMFS",
            ],
            "requires": [
                "jdk.internal.vm.ci",
                "java.logging",
            ],
            "requiresConcealed": {
                "java.base": [
                    "sun.nio.ch",
                    "sun.security.provider",
                    "jdk.internal.misc",
                    "jdk.internal.util",
                ],
                "jdk.internal.vm.ci": ["jdk.vm.ci.code.site", "jdk.vm.ci.code", "jdk.vm.ci.common", "jdk.vm.ci.meta"],
            },
            "javaCompliance": "21+",
            "spotbugs": "true",
            "workingSets": "web-image",
            "checkstyleVersion": "10.21.0",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "substratevm:SVM_PROCESSOR",
            ],
            "spotbugsIgnoresGenerated": True,
            "checkPackagePrefix": False,
        },
        "org.graalvm.webimage.api": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [],
            "javaCompliance": "21+",
            "spotbugs": "true",
            "workingSets": "web-image",
            "annotationProcessors": ["compiler:GRAAL_PROCESSOR"],
            "checkstyle": "com.oracle.svm.webimage",
            "spotbugsIgnoresGenerated": True,
        },
        "com.oracle.svm.webimage.tools": {
            "subDir": "src",
            "sourceDirs": [
                "src",
                "resources",
            ],
            "dependencies": [],
            "requires": ["jdk.httpserver"],
            "javaCompliance": "21+",
            "spotbugs": "true",
            "workingSets": "web-image",
            "annotationProcessors": ["compiler:GRAAL_PROCESSOR"],
            "checkstyle": "com.oracle.svm.webimage",
            "spotbugsIgnoresGenerated": True,
        },
        "com.oracle.svm.webimage.driver": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "substratevm:SVM_DRIVER",
            ],
            "javaCompliance": "21+",
            "spotbugs": "true",
            "workingSets": "web-image",
            "annotationProcessors": ["compiler:GRAAL_PROCESSOR"],
            "checkstyle": "com.oracle.svm.webimage",
            "spotbugsIgnoresGenerated": True,
        },
        "com.oracle.svm.webimage.jtt": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "substratevm:SVM",
                "SVM_WASM_API",
                "WEBIMAGE_LIBRARY_SUPPORT",
                "mx:JUNIT",
                "NET_JAVA_HTML",
                "NET_JAVA_HTML_BOOT",
                "NET_JAVA_HTML_JSON",
                "NET_JAVA_HTML_JSON_TCK",
            ],
            "requiresConcealed": {
                "jdk.internal.vm.ci": [
                    "jdk.vm.ci.common",
                ],
            },
            "javaCompliance": "21+",
            "spotbugs": "false",
            "workingSets": "web-image",
            "testProject": True,
            "checkstyle": "com.oracle.svm.webimage",
            "annotationProcessors": [
                "substratevm:SVM_PROCESSOR",
            ],
        },
        "com.oracle.svm.hosted.webimage": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "compiler:GRAAL",
                "com.oracle.svm.webimage",
                "substratevm:SVM",
                "substratevm:POINTSTO",
                "WEBIMAGE_GOOGLE_CLOSURE",
            ],
            "requires": [
                "jdk.internal.vm.ci",
                "java.logging",
                "java.compiler",
            ],
            "requiresConcealed": {
                "java.base": ["sun.nio.ch", "sun.security.provider", "jdk.internal.reflect"],
                "jdk.internal.vm.ci": ["jdk.vm.ci.code.site", "jdk.vm.ci.code", "jdk.vm.ci.common", "jdk.vm.ci.meta"],
            },
            "javaCompliance": "21+",
            "spotbugs": "true",
            "annotationProcessors": [
                "compiler:GRAAL_PROCESSOR",
                "substratevm:SVM_PROCESSOR",
            ],
            "workingSets": "web-image",
            "checkstyle": "com.oracle.svm.hosted",
            "spotbugsIgnoresGenerated": True,
        },
        "com.oracle.svm.hosted.webimage.test": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "mx:JUNIT",
                "compiler:GRAAL_TEST",
                "com.oracle.svm.webimage.jtt",
                "com.oracle.svm.hosted.webimage",
                "com.oracle.svm.webimage.driver",
            ],
            "requiresConcealed": {
                "jdk.internal.vm.ci": [
                    "jdk.vm.ci.common",
                ],
            },
            "javaCompliance": "21+",
            "workingSets": "web-image",
            "spotbugs": "false",
            "checkstyle": "com.oracle.svm.hosted",
            "testProject": True,
        },
        "com.oracle.svm.webimage.thirdparty": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.svm.hosted.webimage",
            ],
            "requiresConcealed": {
                "jdk.internal.vm.ci": [
                    "jdk.vm.ci.meta",
                ],
            },
            "javaCompliance": "21+",
            "workingSets": "web-image",
            "spotbugs": "true",
            "checkstyle": "com.oracle.svm.hosted",
            "checkPackagePrefix": False,
        },
    },
    # ------------- Distributions -------------
    "distributions": {
        "SVM_WASM": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.svm.hosted.webimage",
                "compiler:GRAAL",
                "substratevm:POINTSTO",
                "substratevm:SVM",
            ],
            "distDependencies": [
                "substratevm:SVM",
                "SVM_WASM_API",
                "SVM_WASM_JIMFS",
                "WEBIMAGE_GOOGLE_CLOSURE",
            ],
            "moduleInfo": {
                "name": "org.graalvm.extraimage.builder",
                "requires": [
                    "org.graalvm.nativeimage.pointsto",
                    "org.graalvm.nativeimage.builder",
                    "org.graalvm.webimage.api",
                    "org.graalvm.collections",
                    "static org.graalvm.wrapped.google.closure",
                ],
                "opens": [
                    "com.oracle.svm.webimage.substitute.system to org.graalvm.nativeimage.builder",
                ],
                "exports": [
                    """* to org.graalvm.extraimage.driver,
                            com.oracle.svm.extraimage_enterprise,
                            org.graalvm.nativeimage.builder,
                            jdk.graal.compiler,
                            org.graalvm.extraimage.librarysupport""",
                ],
            },
            "maven": False,
        },
        "SVM_WASM_API": {
            "subDir": "src",
            "dependencies": [
                "org.graalvm.webimage.api",
            ],
            "distDependencies": [],
            "moduleInfo": {
                "name": "org.graalvm.webimage.api",
                "exports": [
                    "org.graalvm.webimage.api",
                ],
            },
            "maven": False,
        },
        "NATIVE_IMAGE_WASM_SUPPORT": {
            "native": True,
            "description": "Macro for the Native Image Wasm Backend",
            "dependencies": ["svm-wasm-macro-builder"],
            "layout": {
                "native-image.properties": "dependency:svm-wasm-macro-builder/native-image.properties",
            },
        },
        "WEBIMAGE_LIBRARY_SUPPORT": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.svm.webimage.thirdparty",
            ],
            "distDependencies": [
                "SVM_WASM",
            ],
            "moduleInfo": {
                "name": "org.graalvm.extraimage.librarysupport",
                "exports": [
                    "* to org.graalvm.extraimage.builder",
                    "com.oracle.svm.webimage.thirdparty",
                ],
                "opens": [
                    "com.oracle.svm.webimage.thirdparty to org.graalvm.extraimage.builder",
                ],
                "requires": [
                    "jdk.graal.compiler",
                    "org.graalvm.nativeimage.builder",
                    "org.graalvm.webimage.api",
                ],
            },
        },
        "WEBIMAGE_DRIVER": {
            "subDir": "src",
            "description": "Web image building tool",
            "mainClass": "com.oracle.svm.webimage.driver.WebImage",
            "dependencies": [
                "com.oracle.svm.webimage.driver",
            ],
            "distDependencies": [
                "substratevm:SVM_DRIVER",
            ],
            "moduleInfo": {
                "name": "org.graalvm.extraimage.driver",
            },
            "maven": False,
        },
        "WEBIMAGE_DRIVER_SUPPORT": {
            "native": True,
            "description": "Macro for the Web Image driver",
            "dependencies": ["web-image-macro-builder"],
            "layout": {
                "native-image.properties": "dependency:web-image-macro-builder/native-image.properties",
            },
        },
        "WEBIMAGE_TOOLS": {
            "subDir": "src",
            "description": "Web Image Debugging Tools",
            "mainClass": "com.oracle.svm.webimage.tools.ToolServer",
            "dependencies": [
                "com.oracle.svm.webimage.tools",
            ],
            "distDependencies": [
                "SVM_WASM",
            ],
            "moduleInfo": {"name": "org.graalvm.webimage.tools"},
            "maven": False,
        },
        "WEBIMAGE_TESTS": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.svm.hosted.webimage.test",
            ],
            "distDependencies": [
                "SVM_WASM",
                "WEBIMAGE_TESTCASES",
                "WEBIMAGE_DRIVER",
                "compiler:GRAAL_TEST",
            ],
            "exclude": [
                "mx:JUNIT",
            ],
            "maven": False,
            "testDistribution": True,
            "unittestConfig": "web-image",
        },
        "WEBIMAGE_TESTCASES": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.svm.webimage.jtt",
            ],
            "distDependencies": [
                "SVM_WASM_API",
                "WEBIMAGE_LIBRARY_SUPPORT",
            ],
            "exclude": [
                "mx:JUNIT",
            ],
            "maven": False,
            "testDistribution": True,
        },
        "SVM_WASM_JIMFS": {
            "moduleInfo": {
                "name": "org.graalvm.wrapped.google.jimfs",
                "requires": [
                    "java.logging",
                    "org.graalvm.wrapped.google.guava",
                ],
                "exports": [
                    "com.google.common.jimfs to org.graalvm.extraimage.builder, com.oracle.svm.extraimage_enterprise",
                ],
            },
            "javaCompliance": "17+",
            "dependencies": [
                "JIMFS_BFS",
                "SVM_WASM_GUAVA",
            ],
            "maven": False,
        },
        "SVM_WASM_GUAVA": {
            "moduleInfo": {
                "name": "org.graalvm.wrapped.google.guava",
                "requires": [
                    "java.logging",
                ],
                "exports": [
                    "com.google.common.* to org.graalvm.wrapped.google.jimfs",
                ],
            },
            "javaCompliance": "17+",
            "dependencies": [
                "GUAVA_BFS",
            ],
            "maven": False,
        },
        "WEBIMAGE_GOOGLE_CLOSURE": {
            # Converts the GOOGLE_CLOSURE library to a proper module with a custom module name
            "moduleInfo": {
                "name": "org.graalvm.wrapped.google.closure",
                "requires": [
                    "java.xml",
                    "java.sql",
                ],
                "exports": [
                    "com.google.javascript.* to org.graalvm.extraimage.builder, com.oracle.svm.extraimage_enterprise"
                ],
            },
            "javaCompliance": "17+",
            "dependencies": [
                "GOOGLE_CLOSURE",
            ],
            "maven": False,
        },
    },
}
