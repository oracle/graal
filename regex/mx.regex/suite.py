suite = {
  "mxversion" : "5.149.0",

  "name" : "regex",

  "imports" : {
    "suites": [
      {
        "name" : "truffle",
        "subdir": True,
        "urls" : [
          {"url" : "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind" : "binary"},
         ]
      },
    ]
  },

  "repositories" : {
    "lafo-snapshots" : {
      "url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots",
      "licenses" : ["GPLv2-CPE", "UPL", "BSD-new"]
    },
  },

  "defaultLicense" : "GPLv2-CPE",

  "javac.lint.overrides" : "none",

  "projects" : {
    "com.oracle.truffle.regex" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "truffle:TRUFFLE_API",
      ],
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle,Regex",
    },

    "com.oracle.truffle.regex.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.regex",
        "mx:JUNIT",
      ],
      "checkstyle" : "com.oracle.truffle.regex",
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle,Regex",
    },
  },

  "distributions" : {
    "TREGEX" : {
      "subDir" : "src",
      "dependencies" : ["com.oracle.truffle.regex"],
      "distDependencies" : [
        "truffle:TRUFFLE_API",
      ],
    },

    "TREGEX_UNIT_TESTS" : {
      "dependencies" : [
        "com.oracle.truffle.regex.test",
      ],
      "exclude" : [
        "mx:JUNIT",
      ],
      "distDependencies" : [
        "TREGEX",
      ],
      "maven" : False,
    },
  }
}
