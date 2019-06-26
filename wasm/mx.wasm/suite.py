suite = {
  "mxversion" : "5.210.2",
  "name" : "wasm",
  "versionConflictResolution" : "latest",

  "imports" : {
    "suites" : [
      {
        "name" : "truffle",
        "subdir" : True,
        "urls": [
          {"url" : "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind" : "binary"},
        ],
      },
    ],
  },

  "projects" : {
    "com.oracle.truffle.wasm.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.wasm",
        "truffle:TRUFFLE_TCK",
        "mx:JUNIT",
      ],
      "checkstyle" : "com.oracle.truffle.wasm",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle,WebAssembly",
      "license" : "BSD-new",
      "testProject" : True,
    },
    "com.oracle.truffle.wasm" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "truffle:TRUFFLE_API",
        "sdk:GRAAL_SDK",
        "truffle:ANTLR4",
      ],
      "checkstyle" : "com.oracle.truffle.wasm",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle, WebAssembly",
      "license" : "BSD-new",
    },
  },

  "distributions" : {
    "WASM" : {
      "moduleName" : "com.oracle.truffle.wasm",
      "subDir" : "src",
      "dependencies" : ["com.oracle.truffle.wasm"],
      "distDependencies" : [
        "truffle:TRUFFLE_API",
        "truffle:TRUFFLE_TCK",
        "sdk:GRAAL_SDK",
      ],
      "maven" : {
        "artifactId" : "wasm",
      },
      "description" : "Truffle WebAssembly language.",
      "allowsJavadocWarnings": True,
    },

    "WASM_UNIT_TESTS" : {
      "dependencies" : [
        "com.oracle.truffle.wasm.test",
        "truffle:TRUFFLE_TCK",
      ],
      "exclude" : [
        "mx:JUNIT",
      ],
      "distDependencies" : [
        "truffle:TRUFFLE_API",
        "truffle:TRUFFLE_TCK",
        "WASM",
      ],
      "maven" : False,
    },
  }
}
