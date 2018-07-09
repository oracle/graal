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
            "maven" : {
                "groupId" : "org.eclipse.lsp4j",
                "artifactId" : "org.eclipse.lsp4j",
                "version" : "0.4.1",
            },
            "dependencies" : ["LSP4J-JSONRPC", "LSP4J-GENERATOR"],
        },
        "LSP4J-JSONRPC" : {
            "sha1" : "f3f93f50bbeb7d58b50e6ffca615cbfc76491846",
            "maven" : {
                "groupId" : "org.eclipse.lsp4j",
                "artifactId" : "org.eclipse.lsp4j.jsonrpc",
                "version" : "0.4.1",
            },
            "dependencies" : ["GSON"],
        },
        "LSP4J-GENERATOR" : {
            "sha1" : "467f27e91fd694c05eb663532f2ede0404025afe",
            "maven" : {
                "groupId" : "org.eclipse.lsp4j",
                "artifactId" : "org.eclipse.lsp4j.generator",
                "version" : "0.4.1",
            },
            "dependencies" : ["XTEND-LIB"],
        },
        "XTEXT-XBASE-LIB" : {
            "sha1" : "ea0734bda785af01c6f02298d25ed3189dd5a2ac",
            "maven" : {
                "groupId" : "org.eclipse.xtext",
                "artifactId" : "org.eclipse.xtext.xbase.lib",
                "version" : "2.13.0",
            },
            "dependencies" : ["GUAVA"],
        },
        "XTEND-LIB" : {
            "sha1" : "accfb60dda659a31dddb5823d4fbcc7c0c1aa4ae",
            "maven" : {
                "groupId" : "org.eclipse.xtend",
                "artifactId" : "org.eclipse.xtend.lib",
                "version" : "2.13.0",
            },
            "dependencies" : ["XTEND-LIB-MACRO", "XTEXT-XBASE-LIB"],
        },
        "XTEND-LIB-MACRO" : {
            "sha1" : "04897a782f69cee9326ea1ae7e10078b4d738463",
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
            "maven" : {
                "groupId" : "com.google.code.gson",
                "artifactId" : "gson",
                "version" : "2.7",
            }
        },
        "GUAVA" : {
            "sha1" : "ffe5638f514e43ffb71163bbd453b1f9cc218426",
            "maven" : {
                "groupId" : "com.google.guava",
                "artifactId" : "guava",
                "version" : "19.0-rc3",
            }
        },
    },
    
    "projects" : {
        "de.hpi.swa.trufflelsp": {
            "subDir": "truffle-lsp",
            "sourceDirs": ["src"],
            "dependencies": [
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
        }
    },
}
