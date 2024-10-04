#
# Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
  "mxversion": "6.27.1",

  "name" : "regex",

  "version" : "23.1.5",
  "release" : True,
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
      },
    ]
  },
  "ignore_suite_commit_info": True,
  "licenses" : {
    "upl" : {
      "name" : "Universal Permissive License, Version 1.0",
      "url" : "http://opensource.org/licenses/upl",
    },
  },

  "defaultLicense" : "UPL",

  "javac.lint.overrides" : "none",

  "projects" : {
    "com.oracle.truffle.regex" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "truffle:TRUFFLE_API",
        "truffle:TRUFFLE_ICU4J",
      ],
      "requires" : [
        "java.logging",
        "jdk.unsupported", # sun.misc.Unsafe
      ],
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "exports" : [
        "com.oracle.truffle.regex.chardata",
      ],
      "checkstyleVersion" : "10.7.0",
      "javaCompliance" : "17+",
      "workingSets" : "Truffle,Regex",
      "spotbugsIgnoresGenerated" : True,
    },

    "com.oracle.truffle.regex.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "regex:TREGEX_TEST_DUMMY_LANG",
        "mx:JUNIT",
        "mx:JMH_1_21",
      ],
      "annotationProcessors" : [
        "truffle:TRUFFLE_DSL_PROCESSOR",
        "mx:JMH_1_21",
      ],
      "checkstyle" : "com.oracle.truffle.regex",
      "javaCompliance" : "17+",
      "workingSets" : "Truffle,Regex",
    },

    "com.oracle.truffle.regex.test.dummylang" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.regex",
      ],
      "annotationProcessors" : [
        "truffle:TRUFFLE_DSL_PROCESSOR",
      ],
      "checkstyle" : "com.oracle.truffle.regex",
      "javaCompliance" : "17+",
      "workingSets" : "Truffle,Regex",
    },
  },

  "distributions" : {
    "TREGEX" : {
      "moduleInfo" : {
        "name" : "com.oracle.truffle.regex",
        "requires" : [
          "java.logging",
          "jdk.unsupported", # sun.misc.Unsafe
        ],
      },
      "subDir" : "src",
      "dependencies" : ["com.oracle.truffle.regex"],
      "distDependencies" : [
        "truffle:TRUFFLE_API",
        "truffle:TRUFFLE_ICU4J",
      ],
      "exclude" : [
      ],
      "maven" : {
        "artifactId" : "regex",
        "tag": ["default", "public"],
      },
      "description" : "Truffle regular expressions language.",
      "allowsJavadocWarnings": True,
    },

    "TREGEX_TEST_DUMMY_LANG" : {
      "moduleInfo" : {
        "name" : "com.oracle.truffle.regex.test.dummylang",
      },
      "subDir" : "src",
      "dependencies" : ["com.oracle.truffle.regex.test.dummylang"],
      "distDependencies" : [
        "regex:TREGEX"
      ],
      "exclude" : [
      ],
      "description" : "Truffle regular expressions testing dummy language.",
      "allowsJavadocWarnings": True,
      "maven" : False,
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
        "regex:TREGEX",
        "regex:TREGEX_TEST_DUMMY_LANG",
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

    "TREGEX_TEST_DUMMY_LANG_GRAALVM_SUPPORT" : {
      "native" : True,
      "description" : "TRegex support distribution for the GraalVM",
      "layout" : {
        "native-image.properties" : "file:mx.regex/dummylang-native-image.properties",
      },
    },
  }
}
