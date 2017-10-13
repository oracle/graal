suite = {
  "mxversion" : "5.128.4",
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
  "libraries" : {
    "JLINE" : {
      "sha1" : "fdedd5f2522122102f0b3db85fe7aa563a009926",
      "maven" : {
        "groupId" : "jline",
        "artifactId" : "jline",
        "version" : "2.14.5",
      },
      "license" : "BSD-new"
    },
  },
  "projects" : {
    "org.graalvm.options" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [],
      "uses" : [],
      "exports" : [
        "<package-info>",  # exports all packages containing package-info.java
      ],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "1.8",
      "workingSets" : "API,SDK",
    },
    "org.graalvm.polyglot" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : ["org.graalvm.options"],
      "uses" : ["org.graalvm.polyglot.impl.AbstractPolyglotImpl"],
      "exports" : [
        "<package-info>",  # exports all packages containing package-info.java
        "org.graalvm.polyglot.impl", # exported to truffle
        "org.graalvm.polyglot",
        "org.graalvm.polyglot.proxy",
      ],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "1.8",
      "workingSets" : "API,SDK",
    },

    "org.graalvm.word" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "1.8",
      "workingSets" : "API,SDK",
    },

    "org.graalvm.nativeimage" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.word",
        "org.graalvm.options",
      ],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "1.8",
      "workingSets" : "API,SDK",
    },
    "org.graalvm.launcher" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.polyglot",
        "org.graalvm.nativeimage",
        "JLINE",
      ],
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle,Tools",
      "checkstyle" : "org.graalvm.word",
    },
    "org.graalvm.polyglot.tck" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.polyglot",
      ],
      "exports" : [
        "<package-info>",  # exports all packages containing package-info.java
      ],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "1.8",
      "workingSets" : "API,SDK,Test",
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
      "moduleName" : "org.graalvm.graal_sdk",
      "dependencies" : [
        "org.graalvm.polyglot",
        "org.graalvm.nativeimage",
      ],
      "distDependencies" : [],
      "maven" : {
        "groupId" : "org.graalvm",
        "artifactId" : "graal-sdk"
      },
      "javadocType": "api",
      "description" : """GraalVM is an ecosystem for compiling and running applications written in multiple languages.
GraalVM removes the isolation between programming languages and enables interoperability in a shared runtime.""",
    },
    "WORD_API" : {
      "subDir" : "src",
      "moduleName" : "org.graalvm.word",
      "dependencies" : [
        "org.graalvm.word",
      ],
      "distDependencies" : [
      ],
      "overlaps" : [
        "GRAAL_SDK",
      ],
      "maven" : False,
    },
    "LAUNCHER_COMMON" : {
      "subDir" : "src",
      "moduleName" : "org.graalvm.launcher",
      "dependencies" : [
        "org.graalvm.launcher",
      ],
      "distDependencies" : [
        "GRAAL_SDK",
      ],
      "maven": False,
    },
    "POLYGLOT_TCK" : {
      "subDir" : "src",
      "moduleName" : "org.graalvm.polyglot_tck",
      "dependencies" : [
        "org.graalvm.polyglot.tck",
      ],
      "distDependencies" : [
        "GRAAL_SDK",
      ],
      "maven" : {
        "groupId" : "org.graalvm",
        "artifactId" : "polyglot-tck"
      },
      "javadocType": "api",
      "description" : """GraalVM TCK SPI""",
    },
  },
}
