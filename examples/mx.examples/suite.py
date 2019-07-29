suite = {
  "mxversion" : "5.223.0",
  "name" : "examples",
  "url" : "https://github.com/graalvm/graal",
  "developer" : {
    "name" : "Graal developers",
    "email" : "graal-dev@openjdk.java.net",
    "organization" : "Graal",
    "organizationUrl" : "https://github.com/graalvm/graal",
  },
  "scm" : {
    "url" : "https://github.com/graalvm/graal",
    "read" : "https://github.com/graalvm/graal.git",
    "write" : "git@github.com:graalvm/graal.git",
  },
  "snippetsPattern" : ".*(Snippets|doc-files).*",
  "defaultLicense" : "GPLv2-CPE",
  "imports" : {
    "suites": [
      {
        "name" : "compiler",
        "subdir": True,
        "urls" : [
          {"url" : "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind" : "binary"},
         ]
      },
    ]
  },
  "libraries" : {},
  "projects" : {
    "org.graalvm.polyglot.examples" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "sdk:GRAAL_SDK",
        "truffle:TRUFFLE_API",
        "mx:JUNIT"
      ],
      "uses" : [],
      "checkstyle" : "org.graalvm.polyglot",
      "javaCompliance" : "1.8",
      "workingSets" : "API,SDK",
    },
  },
  "licenses" : {
    "UPL" : {
      "name" : "Universal Permissive License, Version 1.0",
      "url" : "http://opensource.org/licenses/UPL",
    }
  },
    # ------------- Distributions -------------
  "distributions" : {
    "POLYGLOT_EXAMPLES" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.polyglot.examples",
      ],
      "distDependencies" : [
      ],
    },
  },
}
