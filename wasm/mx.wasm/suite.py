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
  "mxversion": "6.41.0",
  "name" : "wasm",
  "groupId" : "org.graalvm.wasm",
  "version" : "23.1.5",
  "versionConflictResolution" : "latest",
  "url" : "http://graalvm.org/",
  "developer" : {
    "name" : "Truffle and Graal developers",
    "email" : "graalvm-dev@oss.oracle.com",
    "organization" : "Oracle Corporation",
    "organizationUrl" : "http://www.graalvm.org/",
  },
  "scm" : {
    "url" : "https://github.com/oracle/graal",
    "read" : "https://github.com/oracle/graal.git",
    "write" : "git@github.com:oracle/graal.git",
  },
  "defaultLicense" : "UPL",

  "imports" : {
    "suites" : [
      {
        "name" : "truffle",
        "subdir" : True,
      },
    ],
  },
  "libraries": {
    "JOL": {
      "sha1" : "553a2ba27f58b71e7efb545d7d3c657761f5b596",
      "maven" : {
        "groupId" : "org.openjdk.jol",
        "artifactId" : "jol-core",
        "version" : "0.16",
      },
    },
  },
  "projects" : {
    "org.graalvm.wasm" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "truffle:TRUFFLE_API",
        "sdk:POLYGLOT",
      ],
      "requires": [
        "jdk.unsupported", # sun.misc.Unsafe
      ],
      "checkstyleVersion" : "10.7.0",
      "javaCompliance" : "17+",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "WebAssembly",
      "license" : "UPL",
    },

    "org.graalvm.wasm.launcher" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "sdk:LAUNCHER_COMMON",
      ],
      "checkstyle" : "org.graalvm.wasm",
      "javaCompliance" : "17+",
      "license" : "UPL",
    },

    "org.graalvm.wasm.utils" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.wasm",
        "truffle:TRUFFLE_API",
      ],
      "checkstyle" : "org.graalvm.wasm",
      "javaCompliance" : "17+",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "WebAssembly",
      "license" : "BSD-new",
      "testProject" : True,
    },

    "org.graalvm.wasm.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.wasm",
        "org.graalvm.wasm.utils",
        "truffle:TRUFFLE_TCK",
        "mx:JUNIT",
      ],
      "checkstyle" : "org.graalvm.wasm",
      "javaCompliance" : "17+",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "WebAssembly",
      "license" : "BSD-new",
      "testProject" : True,
    },

    "org.graalvm.wasm.testcases" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [],
      "class" : "EmscriptenProject",
      "checkstyle" : "org.graalvm.wasm",
      "workingSets" : "WebAssembly",
      "testProject" : True,
      "defaultBuild" : False,
    },

    "org.graalvm.wasm.testcases.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.wasm.test",
        "mx:JUNIT",
      ],
      "checkstyle" : "org.graalvm.wasm",
      "javaCompliance" : "17+",
      "workingSets" : "WebAssembly",
      "testProject" : True,
      "defaultBuild" : False,
    },

    "org.graalvm.wasm.benchcases" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [],
      "class" : "EmscriptenProject",
      "checkstyle" : "org.graalvm.wasm",
      "includeset" : "bench",
      "workingSets" : "WebAssembly",
      "testProject" : True,
      "defaultBuild" : False,
    },

    "org.graalvm.wasm.benchcases.bench" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.wasm.benchmark",
        "mx:JMH_1_21",
      ],
      "checkstyle" : "org.graalvm.wasm",
      "javaCompliance" : "17+",
      "annotationProcessors" : ["mx:JMH_1_21"],
      "workingSets" : "WebAssembly",
      "testProject" : True,
      "defaultBuild" : False,
    },

    "org.graalvm.wasm.benchmark" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.wasm",
        "org.graalvm.wasm.utils",
        "mx:JMH_1_21",
      ],
      "javaCompliance" : "17+",
      "annotationProcessors" : ["mx:JMH_1_21"],
      "testProject" : True,
    },

    "org.graalvm.wasm.memory" : {
      "subDir": "src",
      "sourceDirs" : ["src"],
      "dependencies": [
        "org.graalvm.wasm",
        "JOL",
      ],
      "workingSets": "WebAssembly",
      "javaCompliance" : "17+",
      "defaultBuild": False,
    }
  },

  "externalProjects": {
    "resource.org.graalvm.wasm.testcases": {
      "type": "web",
      "path": "src/org.graalvm.wasm.testcases",
      "source": [
        "src",
      ],
    },
    "resource.org.graalvm.wasm.benchcases": {
      "type": "web",
      "path": "src/org.graalvm.wasm.benchcases",
      "source": [
        "src",
      ],
    },
  },

  "distributions" : {
    "WASM" : {
      "moduleInfo" : {
        "name" : "org.graalvm.wasm",
      },
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.wasm",
      ],
      "distDependencies" : [
        "truffle:TRUFFLE_API",
        "sdk:POLYGLOT",
      ],
      "description" : "GraalWasm, an engine for the WebAssembly language in GraalVM.",
      "allowsJavadocWarnings": True,
      "license" : "UPL",
      "maven" : {
        "artifactId" : "wasm-language",
        "tag": ["default", "public"],
      },
      "noMavenJavadoc": True,
    },

    "WASM_COMMUNITY": {
      "type": "pom",
      "runtimeDependencies": [
        "WASM",
        "truffle:TRUFFLE_RUNTIME",
      ],
      "maven": {
        "groupId": "org.graalvm.polyglot",
        "artifactId": "wasm-community",
        "tag": ["default", "public"],
      },
      "description": "Graal WASM engine.",
      "license": "UPL",
    },

    "WASM_LAUNCHER" : {
      "moduleInfo" : {
        "name" : "org.graalvm.wasm.launcher",
        "exports" : [
          "org.graalvm.wasm.launcher to org.graalvm.launcher",
        ],
      },
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.wasm.launcher",
      ],
      "distDependencies" : [
        "sdk:LAUNCHER_COMMON",
      ],
      "mainClass" : "org.graalvm.wasm.WasmLauncher",
      "license" : "UPL",
      "maven" : False,
    },

    "WASM_TESTS" : {
      "dependencies" : [
        "org.graalvm.wasm.test",
        "org.graalvm.wasm.utils",
      ],
      "exclude" : [
        "mx:JUNIT",
      ],
      "distDependencies" : [
        "truffle:TRUFFLE_API",
        "truffle:TRUFFLE_TCK",
        "WASM",
      ],
      "maven" : False,
    },

    "WASM_TESTCASES" : {
      "description" : "Tests compiled from the source code.",
      "dependencies" : [
        "org.graalvm.wasm.testcases",
        "org.graalvm.wasm.testcases.test",
      ],
      "exclude" : [
        "mx:JUNIT",
      ],
      "distDependencies" : [
        "WASM_TESTS",
      ],
      "defaultBuild" : False,
      "maven" : False,
      "testDistribution" : True,
    },

    "WASM_BENCHMARKS" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.wasm.benchmark",
        "mx:JMH_1_21",
      ],
      "distDependencies" : [
        "truffle:TRUFFLE_API",
        "truffle:TRUFFLE_TCK",
        "WASM",
        "WASM_TESTS",
      ],
      "maven" : False,
      "testDistribution" : True,
    },

    "WASM_BENCHMARKCASES" : {
      "description" : "Benchmarks compiled from the source code.",
      "dependencies" : [
        "org.graalvm.wasm.benchcases",
        "org.graalvm.wasm.benchcases.bench",
        "mx:JMH_1_21",
      ],
      "distDependencies" : [
        "truffle:TRUFFLE_API",
        "truffle:TRUFFLE_TCK",
        "WASM",
        "WASM_TESTS",
      ],
      "overlaps" : [
        "WASM_BENCHMARKS",
      ],
      "defaultBuild" : False,
      "platformDependent" : True,
      "maven" : False,
      "testDistribution" : True,
    },

    "WASM_GRAALVM_SUPPORT": {
      "native": True,
      "platformDependent": False,
      "description": "Wasm support distribution",
      "layout": {
        "./": "file:mx.wasm/native-image.properties",
      },
      "maven": False,
    },

    "WASM_GRAALVM_LICENSES": {
      "native": True,
      "platformDependent": False,
      "description": "Wasm support distribution for the GraalVM license files",
      "layout": {
        "LICENSE_WASM.txt": "file:LICENSE",
      },
      "maven": False,
    },
  }
}
