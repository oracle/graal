suite = {
  "mxversion" : "5.70.0",
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
  "defaultLicense" : "GPLv2-CPE",
  "imports": {},
  "libraries" : {},
  "projects" : {
    "org.graalvm.polyglot" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [],
      "uses" : [],
      "exports" : [
        "<package-info>",  # exports all packages containing package-info.java
      ],
      "checkstyle" : "org.graalvm.polyglot",
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
