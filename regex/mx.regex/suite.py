# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#
# ----------------------------------------------------------------------------------------------------
suite = {
  "mxversion" : "5.223.0",

  "name" : "regex",

  "version" : "19.3.0",
  "release" : False,
  "groupId" : "org.graalvm.regex",
  "url" : "http://www.graalvm.org/",
  "developer" : {
    "name" : "Truffle and Graal developers",
    "email" : "graalvm-users@oss.oracle.com",
    "organization" : "Graal",
    "organizationUrl" : "http://www.graalvm.org/",
  },
  "scm" : {
    "url" : "https://github.com/oracle/graal",
    "read" : "https://github.com/oracle/graal.git",
    "write" : "git@github.com:oracle/graal.git",
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
        "com.oracle.truffle.regex.chardata",
      ],
      "checkstyleVersion" : "8.8",
      "javaCompliance" : "8+",
      "workingSets" : "Truffle,Regex",
      "spotbugsIgnoresGenerated" : True,
    },

    "com.oracle.truffle.regex.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.regex",
        "mx:JUNIT",
      ],
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
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
        "artifactId" : "regex",
      },
      "description" : "Truffle regular expressions language.",
      "allowsJavadocWarnings": True,
    },

    "TREGEX_UNIT_TESTS" : {
      "subDir": "src",
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
