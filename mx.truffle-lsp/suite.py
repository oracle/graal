suite = {
    "name" : "truffle-lsp",
    "mxversion" : "5.140.0",

    "imports": {
        "suites": [{
            "name": "truffle",
            "subdir": True,
            "version": "8c629b91f1882a6d7e482832f87f99a9071a3a23",
            "urls": [{
                "url": "https://github.com/graalvm/graal",
                "kind": "git"
            }],
        }],
    },

    "libraries": {
        "LSP4J" : {
            "sha1" : "cc1df3852ded574cf35a6cb81f1629695bab35eb",
            "maven" : {
                "groupId" : "org.eclipse.lsp4j",
                "artifactId" : "org.eclipse.lsp4j",
                "version" : "0.3.0",
            },
            "dependencies" : ["LSP4J-JSONRPC", "LSP4J-GENERATOR"],
        },
        "LSP4J-JSONRPC" : {
            "sha1" : "16296abd1cb9fa647ef0fd6c5615e11c69878bc6",
            "maven" : {
                "groupId" : "org.eclipse.lsp4j",
                "artifactId" : "org.eclipse.lsp4j.jsonrpc",
                "version" : "0.3.0",
            },
            "dependencies" : ["GSON"],
        },
        "LSP4J-GENERATOR" : {
            "sha1" : "be95c0dcef23d74e9d8418a215aa5584c507f7cb",
            "maven" : {
                "groupId" : "org.eclipse.lsp4j",
                "artifactId" : "org.eclipse.lsp4j.generator",
                "version" : "0.3.0",
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
                "sdk:GRAAL_SDK",
                "LSP4J",
                "mx:JUNIT"
                #"sdk:LAUNCHER_COMMON",
                # "tools:CHROMEINSPECTOR",
            ],
            "checkstyle": "de.hpi.swa.trufflelsp",
            "javaCompliance": "1.8",
            "annotationProcessors": ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "workingSets": "TruffleLSP",
        }
    },
}
