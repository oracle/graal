#
# Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
#
suite = {
  "mxversion" : "5.223.0",
  "name" : "sdk",
  "version" : "19.3.0",
  "release" : False,
  "sourceinprojectwhitelist" : [],
  "url" : "https://github.com/oracle/graal",
  "groupId" : "org.graalvm.sdk",
  "developer" : {
    "name" : "Graal developers",
    "email" : "graal-dev@openjdk.java.net",
    "organization" : "Graal",
    "organizationUrl" : "https://github.com/oracle/graal",
  },
  "scm" : {
    "url" : "https://github.com/oracle/graal",
    "read" : "https://github.com/oracle/graal.git",
    "write" : "git@github.com:oracle/graal.git",
  },
  "repositories" : {
    "lafo-snapshots" : {
      "url" : "https://curio.ssw.jku.at/nexus/content/repositories/snapshots",
      "licenses" : ["GPLv2-CPE", "UPL", "BSD-new", "NCSA"]
    },
    "lafo" : {
      "snapshotsUrl" : "https://curio.ssw.jku.at/nexus/content/repositories/snapshots",
      "releasesUrl": "https://curio.ssw.jku.at/nexus/content/repositories/releases",
      "licenses" : ["GPLv2-CPE", "UPL", "BSD-new", "MIT", "NCSA"]
    },
  },
  "snippetsPattern" : ".*(Snippets|doc-files).*",
  "defaultLicense" : "UPL",
  "imports": {},
  "libraries" : {
    "JLINE" : {
      "sha1" : "c3aeac59c022bdc497c8c48ed86fa50450e4896a",
      "maven" : {
        "groupId" : "jline",
        "artifactId" : "jline",
        "version" : "2.14.6",
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
      "javaCompliance" : "8+",
      "workingSets" : "API,SDK",
    },
    "org.graalvm.polyglot" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.options",
        "org.graalvm.collections",
      ],
      "uses" : ["org.graalvm.polyglot.impl.AbstractPolyglotImpl"],
      "exports" : [
        "<package-info>",  # exports all packages containing package-info.java
        "org.graalvm.polyglot.impl to org.graalvm.truffle", # exported to truffle
        "org.graalvm.polyglot",
        "org.graalvm.polyglot.proxy",
      ],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "8+",
      "workingSets" : "API,SDK",
    },

    "org.graalvm.word" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "8+",
      "checkstyleVersion" : "8.8",
      "workingSets" : "API,SDK",
      "exports" : [
        "<package-info>",  # exports all packages containing package-info.java
        "org.graalvm.word.impl to jdk.internal.vm.compiler",
      ],
    },

    "org.graalvm.nativeimage" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.word",
        "org.graalvm.options",
      ],
      "exports" : [
        "<package-info>",  # exports all packages containing package-info.java
        "org.graalvm.nativeimage.impl.clinit",  # class initialization instrumentation
      ],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "8+",
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
      "javaCompliance" : "8+",
      "workingSets" : "Truffle,Tools",
      "checkstyle" : "org.graalvm.word",
    },
    "org.graalvm.launcher.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JUNIT",
        "org.graalvm.launcher"
      ],
      "javaCompliance" : "8+",
      "workingSets" : "Truffle,Tools,Test",
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
      "javaCompliance" : "8+",
      "workingSets" : "API,SDK,Test",
    },
    "org.graalvm.collections" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "exports" : [
        "<package-info>",  # exports all packages containing package-info.java
      ],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "8+",
      "workingSets" : "API,SDK",
    },
    "org.graalvm.collections.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JUNIT",
        "org.graalvm.collections",
      ],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "8+",
      "workingSets" : "API,SDK,Test",
    },
  },
  "licenses" : {
    "UPL" : {
      "name" : "Universal Permissive License, Version 1.0",
      "url" : "http://opensource.org/licenses/UPL",
    },
    "NCSA" : {
      "name" : "University of Illinois/NCSA Open Source License",
      "url" : "https://releases.llvm.org/8.0.0/LICENSE.TXT"
    },
  },

  # ------------- Distributions -------------
  "distributions" : {
    "GRAAL_SDK" : {
      "subDir" : "src",
      "moduleName" : "org.graalvm.sdk",
      "dependencies" : [
        "org.graalvm.polyglot",
        "org.graalvm.nativeimage",
        "org.graalvm.collections",
      ],
      "distDependencies" : [],
      "javadocType": "api",
      "description" : "GraalVM is an ecosystem for compiling and running applications written in multiple languages.\nGraalVM removes the isolation between programming languages and enables interoperability in a shared runtime.",
    },
    "SDK_TEST" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.collections.test",
        "org.graalvm.launcher.test",
      ],
      "distDependencies" : [
        "GRAAL_SDK",
        "LAUNCHER_COMMON"
      ],
      "maven" : False,
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
      "description" : "Common infrastructure to create language launchers using the Polyglot API.",
      "allowsJavadocWarnings": True,
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
      "javadocType": "api",
      "description" : """GraalVM TCK SPI""",
    },
  },
}
