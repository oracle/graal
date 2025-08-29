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
  "mxversion": "7.55.2",
  "name" : "wasm",
  "groupId" : "org.graalvm.wasm",
  "version" : "26.0.0",
  "release" : False,
  "versionConflictResolution" : "latest",
  "url" : "http://graalvm.org/webassembly",
  "developer" : {
    "name": "GraalVM Development",
    "email": "graalvm-dev@oss.oracle.com",
    "organization": "Oracle Corporation",
    "organizationUrl": "http://www.graalvm.org/",
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
      "digest" : "sha512:8adfb561c82f9b198d1d8b7bea605fc8f4418d3e199d0d6262014dc75cee5b1a2ff59ec838b6322f5ee981e7094dbc3c9fa61ee5e8bfe7793aa927e2a900c6ec",
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
      "checkstyleVersion" : "10.21.0",
      "javaCompliance" : "17+",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "WebAssembly",
      "license" : "UPL",
    },

    "org.graalvm.wasm.jdk25" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.wasm",
      ],
      "requires": [
        "jdk.incubator.vector", # Vector API
      ],
      "overlayTarget" : "org.graalvm.wasm",
      "multiReleaseJarVersion" : "25",
      "checkstyle" : "org.graalvm.wasm",
      "javaCompliance" : "25+",
      "forceJavac": True,
      "workingSets" : "WebAssembly",
      "license" : "UPL",
      "javac.lint.overrides" : "-incubating",
      "spotbugs" : "false",
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
        "mx:JUNIT",
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

    "org.graalvm.wasm.polybench": {
      "subDir": "benchmarks",
      "class": "GraalVmWatProject",
      "defaultBuild": False,
      "testProject": True,
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
    },

    "graalwasm_licenses": {
      "class": "StandaloneLicenses",
      "community_license_file": "LICENSE",
      "community_3rd_party_license_file": "THIRD_PARTY_LICENSE.txt",
    },

    "graalwasm_thin_launcher": {
      "class": "ThinLauncherProject",
      "mainClass": "org.graalvm.wasm.launcher.WasmLauncher",
      "jar_distributions": ["wasm:WASM_LAUNCHER"],
      "relative_home_paths": {
        "wasm": "..",
      },
      "relative_jre_path": "../jvm",
      "relative_module_path": "../modules",
      "relative_extracted_lib_paths": {
        "truffle.attach.library": "../jvmlibs/<lib:truffleattach>",
      },
      "liblang_relpath": "../lib/<lib:wasmvm>",
    },

    "libwasmvm": {
      "class": "LanguageLibraryProject",
      "dependencies": [
        "GRAALWASM_STANDALONE_DEPENDENCIES",
      ],
      "build_args": [
        # From mx.wasm/native-image.properties
        # Configure launcher
        "-Dorg.graalvm.launcher.class=org.graalvm.wasm.launcher.WasmLauncher",
      ],
      "dynamicBuildArgs": "libwasmvm_build_args",
    },
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
        "requires": [
          "org.graalvm.collections",
          "static jdk.incubator.vector", # Vector API
        ],
      },
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.wasm",
      ],
      "distDependencies" : [
        "truffle:TRUFFLE_API",
        "sdk:POLYGLOT",
      ],
      "description" : "GraalWasm, a high-performance embeddable WebAssembly runtime for Java. This artifact includes the core language runtime. It is not recommended to depend on the artifact directly. Instead, use `org.graalvm.polyglot:wasm` to ensure all dependencies are pulled in correctly.", # pylint: disable=line-too-long
      "allowsJavadocWarnings": True,
      "license" : "UPL",
      "maven" : {
        "artifactId" : "wasm-language",
        "tag": ["default", "public"],
      },
      "noMavenJavadoc": True,
      "useModulePath": True,
    },

    "WASM_POM": {
      "type": "pom",
      "runtimeDependencies": [
        "WASM",
        "truffle:TRUFFLE_RUNTIME",
      ],
      "maven": {
        "artifactId": "wasm",
        "tag": ["default", "public"],
      },
      "description": "GraalWasm, a high-performance embeddable WebAssembly runtime for Java. This POM dependency includes GraalWasm dependencies and Truffle.",
      "license": "UPL",
    },

    "WASM_LAUNCHER" : {
      "moduleInfo" : {
        "name" : "org.graalvm.wasm.launcher",
        "exports" : [
          "org.graalvm.wasm.launcher to org.graalvm.launcher",
        ],
        "requires": [
          "org.graalvm.polyglot",
        ],
      },
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.wasm.launcher",
      ],
      "distDependencies" : [
        "sdk:LAUNCHER_COMMON",
      ],
      "mainClass" : "org.graalvm.wasm.launcher.WasmLauncher",
      "license" : "UPL",
      "maven" : False,
      "useModulePath": True,
    },

    "WASM_TESTS" : {
      "moduleInfo" : {
        "name" : "org.graalvm.wasm.test",
        "exports" : [
          # Export everything to junit and dependent test distributions.
          "org.graalvm.wasm.test*",
          # Export utils to JMH benchmarks
          "org.graalvm.wasm.utils*",
        ],
        "requires" : [
          "org.graalvm.polyglot",
          "org.graalvm.collections",
          "org.graalvm.truffle",
        ],
      },
      "dependencies" : [
        "org.graalvm.wasm.test",
        "org.graalvm.wasm.utils",
      ],
      "exclude" : [
        "mx:JUNIT",
        "mx:HAMCREST",
      ],
      "distDependencies" : [
        "truffle:TRUFFLE_API",
        "truffle:TRUFFLE_TCK",
        "WASM",
      ],
      "maven" : False,
      "useModulePath": True,
      "unittestConfig": "wasm",
    },

    "WASM_TESTCASES" : {
      "moduleInfo" : {
        "name" : "org.graalvm.wasm.testcases",
        "exports" : [
          # Export everything to junit
          "org.graalvm.wasm.testcases* to junit",
        ],
        "opens" : [
          "test.c",
          "test.wat",
        ],
      },
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
      "useModulePath" : True,
      "testDistribution" : True,
      "unittestConfig": "wasm",
    },

    "WASM_BENCHMARKS" : {
      "moduleInfo" : {
        "name" : "org.graalvm.wasm.benchmark",
        "requires" : [
          "java.compiler",
          "org.graalvm.polyglot",
        ],
      },
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
      "useModulePath": True,
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

    "WASM_POLYBENCH_BENCHMARKS": {
      "description": "Distribution for Wasm polybench benchmarks",
      "layout": {
        "./": [
          "dependency:org.graalvm.wasm.polybench/*",
        ],
      },
      "defaultBuild": False,
      "testDistribution": True,
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

    "GRAALWASM_STANDALONE_DEPENDENCIES": {
      "description": "GraalWasm standalone dependencies",
      "class": "DynamicPOMDistribution",
      "distDependencies": [
        "wasm:WASM_LAUNCHER",
        "wasm:WASM",
        "sdk:TOOLS_FOR_STANDALONE",
      ],
      "dynamicDistDependencies": "graalwasm_standalone_deps",
      "maven": False,
    },

    "GRAALWASM_STANDALONE_COMMON": {
      "description": "Common layout for Native and JVM standalones",
      "type": "dir",
      "platformDependent": True,
      "platforms": "local",
      "layout": {
        "./": [
          "extracted-dependency:WASM_GRAALVM_SUPPORT",
          "dependency:graalwasm_licenses/*",
        ],
        "bin/<exe:wasm>": "dependency:graalwasm_thin_launcher",
        "release": "dependency:sdk:STANDALONE_JAVA_HOME/release",
      },
    },

    "GRAALWASM_NATIVE_STANDALONE": {
      "description": "GraalWasm Native standalone",
      "type": "dir",
      "platformDependent": True,
      "platforms": "local",
      "layout": {
        "./": [
          "dependency:GRAALWASM_STANDALONE_COMMON/*",
        ],
        "lib/": "dependency:libwasmvm",
      },
    },

    "GRAALWASM_JVM_STANDALONE": {
      "description": "GraalWasm JVM standalone",
      "type": "dir",
      "platformDependent": True,
      "platforms": "local",
      "layout": {
        "./": [
          "dependency:GRAALWASM_STANDALONE_COMMON/*",
        ],
        "jvm/": {
          "source_type": "dependency",
          "dependency": "sdk:STANDALONE_JAVA_HOME",
          "path": "*",
          "exclude": [
            # Native Image-related
            "bin/native-image*",
            "lib/static",
            "lib/svm",
            "lib/<lib:native-image-agent>",
            "lib/<lib:native-image-diagnostics-agent>",
            # Unnecessary and big
            "lib/src.zip",
            "jmods",
          ],
        },
        "jvmlibs/": [
          "extracted-dependency:truffle:TRUFFLE_ATTACH_GRAALVM_SUPPORT",
        ],
        "modules/": [
          "classpath-dependencies:GRAALWASM_STANDALONE_DEPENDENCIES",
        ],
      },
    },

    "GRAALWASM_NATIVE_STANDALONE_RELEASE_ARCHIVE": {
        "class": "DeliverableStandaloneArchive",
        "platformDependent": True,
        "standalone_dist": "GRAALWASM_NATIVE_STANDALONE",
        "language_id": "wasm",
        "community_archive_name": "graalwasm-community",
        "enterprise_archive_name": "graalwasm",
    },

    "GRAALWASM_JVM_STANDALONE_RELEASE_ARCHIVE": {
        "class": "DeliverableStandaloneArchive",
        "platformDependent": True,
        "standalone_dist": "GRAALWASM_JVM_STANDALONE",
        "language_id": "wasm",
        "community_archive_name": "graalwasm-community-jvm",
        "enterprise_archive_name": "graalwasm-jvm",
    },
  }
}
