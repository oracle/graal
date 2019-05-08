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
        "com.oracle.truffle.wasm.parser",
        "mx:JUNIT",
      ],
      "checkstyle" : "com.oracle.truffle.wasm",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle, WebAssembly",
      "license" : "BSD-new",
      "testProject" : True,
    },
    "com.oracle.truffle.wasm" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [],
      "checkstyle" : "com.oracle.truffle.wasm",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle, WebAssembly",
      "license" : "BSD-new",
    },
    "com.oracle.truffle.wasm.parser" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [],
      "checkstyle" : "com.oracle.truffle.wasm",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle, WebAssembly",
      "license" : "BSD-new",
    }
  }
}
