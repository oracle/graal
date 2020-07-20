#
# Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

  "name" : "regex",

  "version" : "20.3.0",
  "release" : False,
  "groupId" : "org.graalvm.regex",
  "url" : "http://www.graalvm.org/",
  "developer" : {
    "name" : "GraalVM Development",
    "email" : "graalvm-dev@oss.oracle.com",
    "organization" : "Oracle Corporation",
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
        "mx:JMH_1_21",
      ],
      "annotationProcessors" : [
        "truffle:TRUFFLE_DSL_PROCESSOR",
        "mx:JMH_1_21",
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
