suite = {
    "name" : "truffle-lsp",
    "mxversion" : "5.140.0",

    "imports": {
        "suites": [{
            "name": "truffle",
            "subdir": True,
            "version": "f731a8c22f486419208f072b8c05e98f049ca511",
            "urls": [{
                "url": "https://github.com/graalvm/graal",
                "kind": "git"
            }],
        }],
    },

    "libraries": {
        "LSP4J" : {
            "sha1" : "90e34b7c7e0257e3993ca5a939ae94f889d31340",
            "sourceSha1": "6dd33739fe6dc7f306b819d88a6f9a8f9279da51",
            "maven" : {
                "groupId" : "org.eclipse.lsp4j",
                "artifactId" : "org.eclipse.lsp4j",
                "version" : "0.4.1",
            },
            "dependencies" : ["LSP4J-JSONRPC", "LSP4J-GENERATOR"],
        },
        "LSP4J-JSONRPC" : {
            "sha1" : "f3f93f50bbeb7d58b50e6ffca615cbfc76491846",
            "sourceSha1": "2cb08b2bcc262bb984822c274987a87664a75fed",
            "maven" : {
                "groupId" : "org.eclipse.lsp4j",
                "artifactId" : "org.eclipse.lsp4j.jsonrpc",
                "version" : "0.4.1",
            },
            "dependencies" : ["GSON"],
        },
        "LSP4J-GENERATOR" : {
            "sha1" : "467f27e91fd694c05eb663532f2ede0404025afe",
            "sourceSha1": "80fc1d3f970fd3e666ecd7f70781e22d4e7f70ee",
            "maven" : {
                "groupId" : "org.eclipse.lsp4j",
                "artifactId" : "org.eclipse.lsp4j.generator",
                "version" : "0.4.1",
            },
            "dependencies" : ["XTEND-LIB"],
        },
        "XTEXT-XBASE-LIB" : {
            "sha1" : "ea0734bda785af01c6f02298d25ed3189dd5a2ac",
            "sourceSha1": "d2ed94bab5bae700d752a6f638edf08c19298464",
            "maven" : {
                "groupId" : "org.eclipse.xtext",
                "artifactId" : "org.eclipse.xtext.xbase.lib",
                "version" : "2.13.0",
            },
            "dependencies" : ["GUAVA"],
        },
        "XTEND-LIB" : {
            "sha1" : "accfb60dda659a31dddb5823d4fbcc7c0c1aa4ae",
            "sourceSha1": "c8841f7735714cc794a980094178a9fd31b50754",
            "maven" : {
                "groupId" : "org.eclipse.xtend",
                "artifactId" : "org.eclipse.xtend.lib",
                "version" : "2.13.0",
            },
            "dependencies" : ["XTEND-LIB-MACRO", "XTEXT-XBASE-LIB"],
        },
        "XTEND-LIB-MACRO" : {
            "sha1" : "04897a782f69cee9326ea1ae7e10078b4d738463",
            "sourceSha1": "67abbc9540e78a8aba1c6e4fad3ba1b2183f7be7",
            "maven" : {
                "groupId" : "org.eclipse.xtend",
                "artifactId" : "org.eclipse.xtend.lib.macro",
                "version" : "2.13.0",
            }
        },
        # "HAMCREST-CORE" : {
        #     "sha1" : "42a25dc3219429f0e5d060061f71acb49bf010a0",
        #     "maven" : {
        #         "groupId" : "org.hamcrest",
        #         "artifactId" : "hamcrest-core",
        #         "version" : "1.3",
        #     }
        # },
        "GSON" : {
            "sha1" : "751f548c85fa49f330cecbb1875893f971b33c4e",
            "sourceSha1": "bbb63ca253b483da8ee53a50374593923e3de2e2",
            "maven" : {
                "groupId" : "com.google.code.gson",
                "artifactId" : "gson",
                "version" : "2.7",
            }
        },
        "GUAVA" : {
            "sha1" : "6ce200f6b23222af3d8abb6b6459e6c44f4bb0e9",
            "sourceSha1": "91a4d115400e904f22b03a78deb355e9ea803cd4",
            "maven" : {
                "groupId" : "com.google.guava",
                "artifactId" : "guava",
                "version" : "19.0",
            }
        },
    },
    
    "projects" : {
        "de.hpi.swa.trufflelsp.api": {
            "subDir": "truffle-lsp",
            "sourceDirs": ["src"],
            "dependencies": [
                "sdk:GRAAL_SDK",
            ],
            "checkstyle": "de.hpi.swa.trufflelsp",
            "javaCompliance": "1.8",
            "workingSets": "TruffleLSP"
        },

        "de.hpi.swa.trufflelsp": {
            "subDir": "truffle-lsp",
            "sourceDirs": ["src"],
            "dependencies": [
                "de.hpi.swa.trufflelsp.api",
                "truffle:TRUFFLE_API",
                "truffle:TRUFFLE_DSL_PROCESSOR",
                "truffle:TRUFFLE_NFI",
                "sdk:GRAAL_SDK",
                "sdk:LAUNCHER_COMMON",
                "LSP4J"
            ],
            "checkstyle": "de.hpi.swa.trufflelsp",
            "javaCompliance": "1.8",
            "annotationProcessors": ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "workingSets": "TruffleLSP",
        },

        "de.hpi.swa.trufflelsp.launcher": {
            "subDir": "truffle-lsp",
            "sourceDirs": ["src"],
            "dependencies": [
                "de.hpi.swa.trufflelsp.api",
                "sdk:GRAAL_SDK",
                "sdk:LAUNCHER_COMMON",
            ],
            "checkstyle": "de.hpi.swa.trufflelsp",
            "javaCompliance": "1.8",
            "workingSets": "TruffleLSP"
        },

        "de.hpi.swa.trufflelsp.test": {
            "subDir": "truffle-lsp",
            "sourceDirs": ["src"],
            "dependencies": [
                "de.hpi.swa.trufflelsp.api",
                "de.hpi.swa.trufflelsp",
                "de.hpi.swa.trufflelsp.launcher",
                "truffle:TRUFFLE_SL",
                "mx:JUNIT"
            ],
            "checkstyle": "de.hpi.swa.trufflelsp",
            "javaCompliance": "1.8",
            "annotationProcessors": ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "workingSets": "TruffleLSP",
        },
    },
}
