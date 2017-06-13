suite = {
  "mxversion" : "5.90.02",
  "name" : "sdk",
  "sourceinprojectwhitelist" : [],
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
  "repositories" : {
    "lafo-snapshots" : {
      "url" : "https://curio.ssw.jku.at/nexus/content/repositories/snapshots",
      "licenses" : ["GPLv2-CPE", "UPL", "BSD-new"]
    },
  },
  "snippetsPattern" : ".*(Snippets|doc-files).*",
  "defaultLicense" : "GPLv2-CPE",
  "imports": {},
  "libraries" : {},
  "projects" : {
    "org.graalvm.options" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [],
      "uses" : [],
      "exports" : [
        "<package-info>",  # exports all packages containing package-info.java
      ],
      "checkstyle" : "org.graalvm.api.word",
      "javaCompliance" : "1.8",
      "workingSets" : "API,SDK",
    },
    "org.graalvm.polyglot" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["org.graalvm.options"],
      "uses" : [],
      "exports" : [
        "<package-info>",  # exports all packages containing package-info.java
        "org.graalvm.polyglot.impl", # exported to truffle
        "org.graalvm.polyglot",
        "org.graalvm.polyglot.proxy",
      ],
      "checkstyle" : "org.graalvm.api.word",
      "javaCompliance" : "1.8",
      "workingSets" : "API,SDK",
    },
    "org.graalvm.api.word" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [],
      "checkstyle" : "org.graalvm.api.word",
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
    "GRAAL_SDK" : {
      "subDir" : "src",
      "moduleName" : "org.graalvm.sdk",
      "dependencies" : [
        "org.graalvm.polyglot",
      ],
      "distDependencies" : [],
    },
    "WORD_API" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.api.word",
      ],
      "distDependencies" : [
      ],
    },
  },
}
