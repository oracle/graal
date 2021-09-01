#
# Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
  "mxversion" : "5.309.1",
  "name" : "sdk",
  "version" : "21.3.0",
  "release" : False,
  "sourceinprojectwhitelist" : [],
  "url" : "https://github.com/oracle/graal",
  "groupId" : "org.graalvm.sdk",
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
  "repositories" : {
    "lafo-snapshots" : {
      "url" : "https://curio.ssw.jku.at/nexus/content/repositories/snapshots",
      "licenses" : ["GPLv2-CPE", "UPL", "BSD-new", "NCSA"]
    },
    "lafo" : {
      "snapshotsUrl" : "https://curio.ssw.jku.at/nexus/content/repositories/snapshots",
      "releasesUrl": "https://curio.ssw.jku.at/nexus/content/repositories/releases",
      "licenses" : ["GPLv2-CPE", "UPL", "BSD-new", "MIT", "NCSA"],
    },
    "lafo-maven" : {
      "snapshotsUrl" : "https://curio.ssw.jku.at/nexus/content/repositories/maven-snapshots",
      "releasesUrl": "https://curio.ssw.jku.at/nexus/content/repositories/maven-releases",
      "licenses" : ["GPLv2-CPE", "UPL", "BSD-new", "MIT", "NCSA"],
      "mavenId" : "lafo",
    },
  },
  "snippetsPattern" : ".*(Snippets|doc-files).*",
  "defaultLicense" : "UPL",
  "imports": {},
  "libraries" : {
    "WRK": {
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/wrk-a211dd5.tar.gz"],
      "sha1": "6052d4452e190e707309c575fa2852a0035c5f34",
      "packedResource": True,
      "license": "Apache-2.0-wrk-a211dd5",
    },
    "WRK2": {
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/wrk2.tar.gz"],
      "sha1": "a5c417b414f72951de4eb95641808a0a5aa31445",
      "packedResource": True,
      "license": "Apache-2.0",
    },
    "APACHE_JMETER_5.3": {
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/apache-jmeter-5.3.zip"],
      "sha1": "17480a0905d9d485bc8ce8e7be9daec2de98c251",
      "packedResource": True,
      "license": "Apache-2.0",
    },
    "JLINE" : {
      "sha1" : "c3aeac59c022bdc497c8c48ed86fa50450e4896a",
      "maven" : {
        "groupId" : "jline",
        "artifactId" : "jline",
        "version" : "2.14.6",
      }
    },
    "JLINE3" : {
      "sha1" : "4cb5f6c20b10912ef3c12f4c4c3ebcdbcbe3a555",
      "version" : "3.16.0.1",
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/jline3-shadowed-{version}.jar"],
      "license" : "BSD-new",
      "requires" : ["java.logging"],
      "exports" : [
        "org.graalvm.shadowed.org.fusesource.hawtjni.runtime",
        "org.graalvm.shadowed.org.fusesource.jansi",
        "org.graalvm.shadowed.org.fusesource.jansi.internal",
        "org.graalvm.shadowed.org.jline.builtins",
        "org.graalvm.shadowed.org.jline.builtins.ssh",
        "org.graalvm.shadowed.org.jline.builtins.telnet",
        "org.graalvm.shadowed.org.jline.console",
        "org.graalvm.shadowed.org.jline.console.impl",
        "org.graalvm.shadowed.org.jline.keymap",
        "org.graalvm.shadowed.org.jline.reader",
        "org.graalvm.shadowed.org.jline.reader.impl",
        "org.graalvm.shadowed.org.jline.reader.impl.completer",
        "org.graalvm.shadowed.org.jline.reader.impl.history",
        "org.graalvm.shadowed.org.jline.style",
        "org.graalvm.shadowed.org.jline.terminal",
        "org.graalvm.shadowed.org.jline.terminal.impl",
        "org.graalvm.shadowed.org.jline.terminal.impl.jansi",
        "org.graalvm.shadowed.org.jline.terminal.impl.jansi.freebsd",
        "org.graalvm.shadowed.org.jline.terminal.impl.jansi.linux",
        "org.graalvm.shadowed.org.jline.terminal.impl.jansi.osx",
        "org.graalvm.shadowed.org.jline.terminal.impl.jansi.solaris",
        "org.graalvm.shadowed.org.jline.terminal.impl.jansi.win",
        "org.graalvm.shadowed.org.jline.terminal.impl.jna",
        "org.graalvm.shadowed.org.jline.terminal.impl.jna.freebsd",
        "org.graalvm.shadowed.org.jline.terminal.impl.jna.linux",
        "org.graalvm.shadowed.org.jline.terminal.impl.jna.osx",
        "org.graalvm.shadowed.org.jline.terminal.impl.jna.solaris",
        "org.graalvm.shadowed.org.jline.terminal.impl.jna.win",
        "org.graalvm.shadowed.org.jline.terminal.spi",
        "org.graalvm.shadowed.org.jline.utils",
        "org.graalvm.shadowed.org.jline.widget",
      ],
    },
    "LLVM_ORG" : {
      "version" : "10.0.0-6-gad4288df64-bg263fb7a415",
      "host" : "https://lafo.ssw.uni-linz.ac.at/pub/llvm-org",
      "os_arch" : {
        "linux" : {
          "amd64" : {
            "urls" : ["{host}/llvm-llvmorg-{version}-linux-amd64.tar.gz"],
            "sha1" : "89521da049c9275f63cc11c9f60a40050deec61c",
          },
          "aarch64" : {
            "urls" : ["{host}/llvm-llvmorg-{version}-linux-aarch64.tar.gz"],
            "sha1" : "f0bdf96d621e0de31fb0e2036d77ff18d2fe87e2",
          }
        },
        "darwin" : {
          "amd64" : {
            "urls" : ["{host}/llvm-llvmorg-{version}-darwin-amd64.tar.gz"],
            "sha1" : "e25ea07786bcb55c186b9bf9a20d5fcefd5587b1",
          }
        },
        "windows" : {
          "amd64" : {
            "urls" : ["{host}/llvm-llvmorg-{version}-windows-amd64.tar.gz"],
            "sha1" : "cbff9162b4a67dbaaa31efdb1002fd01912de5e5",
          }
        },
        "<others>": {
            "<others>": {
                "optional": True,
            }
        },
      },
      "license" : "Apache-2.0-LLVM",
    },
    "LLVM_ORG_COMPILER_RT_LINUX" : {
      "version" : "10.0.0-6-gad4288df64-bg263fb7a415",
      "host" : "https://lafo.ssw.uni-linz.ac.at/pub/llvm-org",
      # we really want linux-amd64, also on non-linux and non-amd64 platforms for cross-compilation
      "urls" : ["{host}/compiler-rt-llvmorg-{version}-linux-amd64.tar.gz"],
      "sha1" : "687e1a131efb2b43b57c9b22849e642151845bfe",
      "license" : "Apache-2.0-LLVM",
    },
    "LLVM_ORG_SRC" : {
      "version" : "10.0.0-6-gad4288df64-bg263fb7a415",
      "host" : "https://lafo.ssw.uni-linz.ac.at/pub/llvm-org",
      "packedResource" : True,
      "urls" : ["{host}/llvm-src-llvmorg-{version}.tar.gz"],
      "sha1" : "c5340ed83c3ff6040ed2dc49ab070a4cd0ccceea",
      "license" : "Apache-2.0-LLVM",
      },
  },
  "projects" : {
    "org.graalvm.options" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "8+",
      "workingSets" : "API,SDK",
    },
    "org.graalvm.polyglot" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.collections",
        "org.graalvm.home",
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
      "checkstyleVersion" : "8.36.1",
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
      "javaCompliance" : "8+",
      "workingSets" : "API,SDK",
    },
    "org.graalvm.nativeimage.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JUNIT",
        "org.graalvm.nativeimage"
      ],
      "javaCompliance" : "8+",
      "workingSets" : "SDK",
      "checkstyle" : "org.graalvm.word",
    },
    "org.graalvm.launcher" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.polyglot",
        "JLINE",
        "JLINE3",
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
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "8+",
      "workingSets" : "API,SDK,Test",
    },
    "org.graalvm.collections" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
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
    "org.graalvm.home" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.nativeimage",
      ],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "8+",
      "workingSets" : "API,SDK",
    },
    "org.graalvm.home.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JUNIT",
        "org.graalvm.home",
      ],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "8+",
      "workingSets" : "API,SDK",
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
    "Apache-2.0-LLVM" : {
      "name" : "Apache License 2.0 with LLVM Exceptions",
      "url" : "http://releases.llvm.org/9.0.0/LICENSE.TXT"
    },
    "Apache-2.0-wrk-a211dd5" : {
      "name" : "Modified Apache 2.0 License",
      "url" : "https://raw.githubusercontent.com/wg/wrk/a211dd5a7050b1f9e8a9870b95513060e72ac4a0/LICENSE"
    },
},

  # ------------- Distributions -------------
  "distributions" : {
    "GRAAL_SDK" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.polyglot",
        "org.graalvm.nativeimage",
        "org.graalvm.collections",
        "org.graalvm.home",
      ],
      "distDependencies" : [],
      "javadocType": "api",
      "moduleInfo" : {
        "name" : "org.graalvm.sdk",
        "requires" : ["java.logging"],
        "exports" : [
          "org.graalvm.collections",
          "org.graalvm.home",
          "org.graalvm.home.impl",
          "org.graalvm.nativeimage.hosted",
          "org.graalvm.nativeimage.c.function",
          "org.graalvm.nativeimage.c.struct",
          "org.graalvm.nativeimage.c.type",
          "org.graalvm.nativeimage.c.constant",
          "org.graalvm.nativeimage.c",
          "org.graalvm.nativeimage",
          "org.graalvm.nativeimage.impl.clinit", # class initialization instrumentation
          "org.graalvm.polyglot.proxy",
          "org.graalvm.polyglot.io",
          "org.graalvm.polyglot.management",
          "org.graalvm.polyglot",
          "org.graalvm.options",
          "org.graalvm.word",
          "org.graalvm.polyglot.impl to org.graalvm.truffle",
          "org.graalvm.word.impl to jdk.internal.vm.compiler",
          "org.graalvm.nativeimage.impl to org.graalvm.nativeimage.builder,org.graalvm.nativeimage.configure,com.oracle.svm.svm_enterprise",
        ],
        "uses" : [
          "org.graalvm.polyglot.impl.AbstractPolyglotImpl"
        ],
        "opens" : [
          "org.graalvm.polyglot to org.graalvm.truffle"
        ],
      },
      "description" : "GraalVM is an ecosystem for compiling and running applications written in multiple languages.\nGraalVM removes the isolation between programming languages and enables interoperability in a shared runtime.",
    },
    "SDK_TEST" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.collections.test",
        "org.graalvm.nativeimage.test",
        "org.graalvm.launcher.test",
        "org.graalvm.home.test",
      ],
      "distDependencies" : [
        "GRAAL_SDK",
        "LAUNCHER_COMMON"
      ],
      "maven" : False,
    },
    "LAUNCHER_COMMON" : {
      "subDir" : "src",
      "moduleInfo" : {
        "name" : "org.graalvm.launcher",
        "exports" : [
          "org.graalvm.launcher",
        ],
      },
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
      "moduleInfo" : {
        "name" : "org.graalvm.polyglot_tck",
        "exports" : [
          "org.graalvm.polyglot.tck"
        ],
      },
      "dependencies" : [
        "org.graalvm.polyglot.tck",
      ],
      "distDependencies" : [
        "GRAAL_SDK",
      ],
      "javadocType": "api",
      "description" : """GraalVM TCK SPI""",
    },
    "LLVM_TOOLCHAIN": {
      "native": True,
      "description": "LLVM with general purpose patches used by Sulong and Native Image",
      "layout": {
        "./": [
          {
            "source_type": "extracted-dependency",
            "dependency": "LLVM_ORG",
            "path": "*",
            "dereference": "never",
            "exclude": [
              "bin/bugpoint*",
              "bin/c-index-test*",
              "bin/clang-check*",
              "bin/clang-extdef-mapping*",
              "bin/clang-import-test*",
              "bin/clang-offload-*",
              "bin/clang-refactor*",
              "bin/clang-rename*",
              "bin/clang-scan-deps*",
              "bin/diagtool*",
              "bin/dsymutil*",
              "bin/git-clang-format",
              "bin/hmaptool",
              "bin/llvm-addr2line*",
              "bin/llvm-bcanalyzer*",
              "bin/llvm-cat*",
              "bin/llvm-cfi-verify*",
              "bin/llvm-cov*",
              "bin/llvm-c-test*",
              "bin/llvm-cvtres*",
              "bin/llvm-cxxdump*",
              "bin/llvm-cxxfilt*",
              "bin/llvm-cxxmap*",
              "bin/llvm-dwp*",
              "bin/llvm-elfabi*",
              "bin/llvm-exegesis*",
              "bin/llvm-jitlink*",
              "bin/llvm-lipo*",
              "bin/llvm-lto*",
              "bin/llvm-lto2*",
              "bin/llvm-mc*",
              "bin/llvm-mca*",
              "bin/llvm-modextract*",
              "bin/llvm-mt*",
              "bin/llvm-opt-report*",
              "bin/llvm-pdbutil*",
              "bin/llvm-profdata*",
              "bin/llvm-rc*",
              "bin/llvm-rtdyld*",
              "bin/llvm-size*",
              "bin/llvm-split*",
              "bin/llvm-stress*",
              "bin/llvm-strings*",
              "bin/llvm-symbolizer*",
              "bin/llvm-tblgen*",
              "bin/llvm-undname*",
              "bin/llvm-xray*",
              "bin/obj2yaml*",
              "bin/sancov*",
              "bin/sanstats*",
              "bin/scan-build*",
              "bin/scan-view*",
              "bin/verify-uselistorder*",
              "bin/yaml2obj*",
              "bin/set-xcode-analyzer",
              "share",
              "include/clang",
              "include/clang-c",
              "include/lld",
              "include/llvm",
              "include/llvm-c",
              "lib/cmake",
              "lib/Checker*",
              "lib/Sample*",
              "lib/libRemarks*",
              "lib/libLLVM*.a",
              "lib/libclang.so*",
              "lib/libclang.dylib*",
              "lib/libclang*.a",
              "lib/liblld*.a",
              "libexec",
              # the following is added by COMPILER_RT
              "lib/clang/*/lib/linux/*clang_rt*",
              # Windows libarary excludes
              "lib/*.lib",
            ]
          },
          "extracted-dependency:LLVM_ORG_COMPILER_RT_LINUX",
          "file:3rd_party_license_llvm-toolchain.txt",
        ],
        "./patches/" : "file:llvm-patches/*",
      },
      "platformDependent" : True,
      "maven": False,
      "license" : "Apache-2.0-LLVM",
    },
    "LLVM_TOOLCHAIN_FULL": {
      "description": "Distribution including all of LLVM. Use only for building/testing. Only the content of LLVM_TOOLCHAIN will be included in the llvm-toolchain installable.",
      "native": True,
      "layout": {
        "./": [
          {
            "source_type": "extracted-dependency",
            "dependency": "LLVM_ORG",
            "path": "*",
            "dereference": "never",
          },
        ],
      },
      "platformDependent" : True,
      "maven": False,
      "license" : "Apache-2.0-LLVM",
      "defaultBuild" : False,
    },
  },
}
