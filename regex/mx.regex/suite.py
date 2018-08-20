suite = {
  "mxversion" : "5.149.0",

  "name" : "regex",

  "version" : "1.0.0-rc6",
  "groupId" : "com.oracle.truffle",
  "url" : "http://www.graalvm.org/",
  "developer" : {
    "name" : "Truffle and Graal developers",
    "email" : "graalvm-users@oss.oracle.com",
    "organization" : "Graal",
    "organizationUrl" : "http://www.graalvm.org/",
  },

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
      "exports" : [
        "com.oracle.truffle.regex",
        "com.oracle.truffle.regex.chardata",
        "com.oracle.truffle.regex.result",
      ],
      "checkstyleVersion" : "8.8",
      "javaCompliance" : "8+",
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
      "javaCompliance" : "8+",
      "workingSets" : "Truffle,Regex",
    },
  },

  "distributions" : {
    "TREGEX" : {
      "moduleName" : "com.oracle.truffle.regex",
      "subDir" : "src",
      "dependencies" : ["com.oracle.truffle.regex"],
      "distDependencies" : [
        "truffle:TRUFFLE_API",
      ],
      "maven" : {
        "artifactId" : "truffle-regex",
      },
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

    "TREGEX_GRAALVM_SUPPORT" : {
      "native" : True,
      "description" : "TRegex support distribution for the GraalVM",
      "layout" : {
        "native-image.properties" : "file:mx.regex/native-image.properties",
      },
    },
  }
}
