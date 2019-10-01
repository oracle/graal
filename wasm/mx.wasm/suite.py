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
  "mxversion" : "5.210.2",
  "name" : "wasm",
  "versionConflictResolution" : "latest",

  "imports" : {
    "suites" : [
      {
        "name" : "truffle",
        "subdir" : True,
        "urls": [
          {"url" : "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind" : "binary"},
        ],
      },
    ],
  },

  "projects" : {
    "com.oracle.truffle.wasm" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "truffle:TRUFFLE_API",
        "sdk:GRAAL_SDK",
        "truffle:ANTLR4",
      ],
      "checkstyle" : "com.oracle.truffle.wasm",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle, WebAssembly",
      "license" : "BSD-new",
    },

    "com.oracle.truffle.wasm.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.wasm",
        "truffle:TRUFFLE_TCK",
        "mx:JUNIT",
      ],
      "checkstyle" : "com.oracle.truffle.wasm",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle,WebAssembly",
      "license" : "BSD-new",
      "testProject" : True,
    },

    "com.oracle.truffle.wasm.source" : {
      "subDir" : "src",
      "dependencies" : [],
      "class" : "GraalWasmSourceFileProject",
      "checkstyle" : "com.oracle.truffle.wasm",
      "workingSets" : "Truffle,WebAssembly",
      "testProject" : False,
      "defaultBuild" : False,
    },

    "com.oracle.truffle.wasm.source.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JUNIT",
      ],
      "checkstyle" : "com.oracle.truffle.wasm",
      "javaCompliance" : "1.8",
      "workingSets" : "Truffle,WebAssembly",
      "testProject" : True,
      "defaultBuild" : False,
    },

    "com.oracle.truffle.wasm.benchmark" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.truffle.wasm",
        "mx:JMH_1_21",
      ],
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["mx:JMH_1_21"],
    },
  },

  "distributions" : {
    "WASM" : {
      "moduleName" : "com.oracle.truffle.wasm",
      "subDir" : "src",
      "dependencies" : ["com.oracle.truffle.wasm"],
      "distDependencies" : [
        "truffle:TRUFFLE_API",
        "truffle:TRUFFLE_TCK",
        "sdk:GRAAL_SDK",
      ],
      "maven" : {
        "artifactId" : "wasm",
      },
      "description" : "Truffle WebAssembly language.",
      "allowsJavadocWarnings": True,
    },

    "WASM_TESTS" : {
      "dependencies" : [
        "com.oracle.truffle.wasm.test",
        # "com.oracle.truffle.wasm.emcc.test",
        "truffle:TRUFFLE_TCK",
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

    "WASM_SOURCE_TESTS" : {
      "platformDependent" : True,
      "description" : "Tests compiled from source code.",
      "dependencies" : [
        "com.oracle.truffle.wasm.source",
        "com.oracle.truffle.wasm.source.test",
      ],
      "exclude" : [
        "mx:JUNIT",
      ],
      "distDependencies" : [
        "WASM_TESTS",
      ],
      "defaultBuild" : False,
      "maven" : False,
    },

    "WASM_BENCHMARKS" : {
      "subDir" : "src",
      "dependencies" : [
        "com.oracle.truffle.wasm.benchmark",
        "mx:JMH_1_21",
      ],
    },
  }
}
