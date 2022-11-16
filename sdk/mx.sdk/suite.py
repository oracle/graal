#
# Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
  "mxversion" : "6.11.4",
  "name" : "sdk",
  "version" : "23.0.0",
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
  "ignore_suite_commit_info": True,
  "libraries" : {
    "WRK_MULTIARCH": {
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/wrk-a211dd5-multiarch.tar.gz"],
      "digest": "sha512:a7f7a7fd9bf8b87423a682ff1390a6ba87cc8dec43d41a3dcabb9a4fa5516b3d2e71f2384661a4248440c0ba4f2e27b8ef50d5dc123c5ae118866fa38254e23c",
      "packedResource": True,
      "license": "Apache-2.0-wrk-a211dd5",
    },
    "WRK2_MULTIARCH": {
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/wrk2-multiarch.tar.gz"],
      "digest": "sha512:597d64086e4d8126bea480ae5edc15b3b9ed649a4ad38c99c42968f25e260da351780921c5013200eddefcc5a4b715676df194d52ff04a5bfcec024cc6140530",
      "packedResource": True,
      "license": "Apache-2.0",
    },
    "APACHE_JMETER_5.3": {
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/apache-jmeter-5.3.zip"],
      "digest": "sha512:e84dfe57397ca5bd9ed5d38c85a1081373b047ff9d41658a64a09dcf8329c25aaa7c23b5bba1b492c3d12edce7f141504baba8071b05df760303c1873ee46ddb",
      "packedResource": True,
      "license": "Apache-2.0",
    },
    "UPX": {
      "packedResource": True,
      "os_arch" : {
        "linux" : {
          "amd64" : {
            "digest": "sha512:c005f55b7935f09302a37cda478101ed540f065de7a61e095f92d4fbc9bbfd9d1ffc8c342a1738466799af1213ac8e61f68efd6936c6a061c15529fca8414418",
            "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/upx/upx-3.96-amd64_linux.tar.gz"],
          },
          "aarch64" : {
            "digest" : "sha512:75d9c41cc3861a021a38f78992c018713c3a06adaa2b343a62fb048596f080d26e4583cafc95cbf3747f9637b22c8156353a6557c6738cf0e68f671b549f31e3",
            "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/upx/upx-3.96-arm64_linux.tar.gz"],
          }
        },
        "windows" : {
          "amd64" : {
            "digest": "sha512:228c1a8ce0a2a4d1b3b3cc1cf216c4c1f9d5ab53f351eb7e9f1a46c7c0b940002e8954b120275d50ef8728077274caba08d54d6f48668ff71978604d00d6ddc2",
            "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/upx/upx-3.96-win64.zip"],
          }
        },
        "<others>" : {
          "<others>" : {
            "optional": True,
          }
        }
      }
    },
    "JLINE" : {
      "digest" : "sha512:27f6e2523e539383cede51d8eae7e97d49038c5a66cb4a94a9ce85165f16e7382b937a238cdb0c92e1136af56c5f57bcc6c04435a370c5d49f7e4bd32f0d9194",
      "maven" : {
        "groupId" : "jline",
        "artifactId" : "jline",
        "version" : "2.14.6",
      }
    },
    "JLINE3" : {
      "digest" : "sha512:d9518c40e206950b3815593de83b1e3632896096407937f15646c81c69f167900c67bd88f3ff2b86258960bbd108d3c0cf09a1ad7cfbf1be489b2af4feccbb58",
      "version" : "3.16.0.3",
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
      "version" : "14.0.6-3-gc7a4a53c32-bgc5e298fd27",
      "host" : "https://lafo.ssw.uni-linz.ac.at/pub/llvm-org",
      "os_arch" : {
        "linux" : {
          "amd64" : {
            "urls" : ["{host}/llvm-llvmorg-{version}-linux-amd64.tar.gz"],
            "digest" : "sha512:7cdc72501a03c148b3f36aff06363cea786b207507b04af3973130f01e60870e6677d8efa788be80086f033908997d664331da47e220f8cdba98e78d1892f1c1",
          },
          "aarch64" : {
            "urls" : ["{host}/llvm-llvmorg-{version}-linux-aarch64.tar.gz"],
            "digest" : "sha512:758cac0af84c3aed95e1e24c9102eb0b487de0d17d1d8c0c479de56b8ef6eb8321a4d94c94d7cb71fb369bddd2633d981a1071af7e028fb06957be5cde4f6ca4",
          },
          "riscv64": {
            "urls" : ["{host}/llvm-llvmorg-{version}-linux-riscv64.tar.gz"],
            "digest" : "sha512:70b4c5b95dc3e2e311e94df94dc032587a766429c554871a7ec13402edb1f6de1657d6b4e95c5fbb24c7957d75e4f9421158f15d08d3811e0f6d9820367ecf30",
          },
        },
        "darwin" : {
          "amd64" : {
            "urls" : ["{host}/llvm-llvmorg-{version}-darwin-amd64.tar.gz"],
            "digest" : "sha512:e97360157167d1edead2afaa2728cf5470b4c56f42d35e2bbf8cab9f5510801532681bd0cf96df43d37d69c72573b44f1788c1931f538275833d05b21b886c73",
          },
          "aarch64" : {
            "urls" : ["{host}/llvm-llvmorg-{version}-darwin-aarch64.tar.gz"],
            "digest" : "sha512:6ab8d86a4d9f0df32921d44c8b4e627a848df552b0bec9a278c42a578013b49b1bb1f43bb236c89965ae10f458760b8e5cf0106f1fbf7a137550a5d981df6c47",
          }
        },
        "windows" : {
          "amd64" : {
            "urls" : ["{host}/llvm-llvmorg-{version}-windows-amd64.tar.gz"],
            "digest" : "sha512:53554500d1dac665118f91ad7243b4dfc4bca2c8e2ae41543dba6c57f6964c273c40e6d132c1fe42612bcdd4ce8957bf1884d8ad4db68925ebe6e4b0473652e6",
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
      "version" : "14.0.6-3-gc7a4a53c32-bgc5e298fd27",
      "host" : "https://lafo.ssw.uni-linz.ac.at/pub/llvm-org",
      # we really want linux-amd64, also on non-linux and non-amd64 platforms for cross-compilation
      "urls" : ["{host}/compiler-rt-llvmorg-{version}-linux-amd64.tar.gz"],
      "digest" : "sha512:62821277a43e558b1c0bd6da494e64407871af87fada9017ade5a48567e418cc83ad297a729aea3d606c3290d8909eeeae9d374cd439a5254ffcfb44a83d3935",
      "license" : "Apache-2.0-LLVM",
    },
    "LLVM_ORG_SRC" : {
      "version" : "14.0.6-3-gc7a4a53c32-bgc5e298fd27",
      "host" : "https://lafo.ssw.uni-linz.ac.at/pub/llvm-org",
      "packedResource" : True,
      "urls" : ["{host}/llvm-src-llvmorg-{version}.tar.gz"],
      "digest" : "sha512:6f99f1f7950521a8a5f261b536ae7066cece753ed1dde458c7ffb4e38652f5bfe15b03cf6e0e67ec5c7c402cf57c2898f445f329bc115195f29cdb5b424089b8",
      "license" : "Apache-2.0-LLVM",
    },
  },
  "projects" : {
    "org.graalvm.options" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "11+",
      "workingSets" : "API,SDK",
    },
    "org.graalvm.polyglot" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.collections",
        "org.graalvm.home",
      ],
      "requires" : [
        "java.logging",
      ],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "11+",
      "workingSets" : "API,SDK",
    },

    "org.graalvm.word" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "11+",
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
      "javaCompliance" : "11+",
      "workingSets" : "API,SDK",
    },
    "com.oracle.svm.core.annotate" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
         "org.graalvm.nativeimage",
      ],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "11+",
      "workingSets" : "API,SDK",
    },
    "org.graalvm.nativeimage.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JUNIT",
        "org.graalvm.nativeimage"
      ],
      "javaCompliance" : "11+",
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
      "requires" : [
        "java.logging",
      ],
      "requiresConcealed" : {
        "java.base" : ["jdk.internal.module"],
      },
      "javaCompliance" : "11+",
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
      "javaCompliance" : "11+",
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
      "javaCompliance" : "11+",
      "workingSets" : "API,SDK,Test",
    },
    "org.graalvm.collections" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "11+",
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
      "javaCompliance" : "11+",
      "workingSets" : "API,SDK,Test",
    },
    "org.graalvm.home" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "org.graalvm.nativeimage",
      ],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "11+",
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
      "javaCompliance" : "11+",
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
        "com.oracle.svm.core.annotate",
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
          "com.oracle.svm.core.annotate",
          "org.graalvm.nativeimage.hosted",
          "org.graalvm.nativeimage.c.function",
          "org.graalvm.nativeimage.c.struct",
          "org.graalvm.nativeimage.c.type",
          "org.graalvm.nativeimage.c.constant",
          "org.graalvm.nativeimage.c",
          "org.graalvm.nativeimage",
          "org.graalvm.polyglot.proxy",
          "org.graalvm.polyglot.io",
          "org.graalvm.polyglot.management",
          "org.graalvm.polyglot",
          "org.graalvm.options",
          "org.graalvm.word",
          "org.graalvm.polyglot.impl to org.graalvm.truffle, com.oracle.graal.graal_enterprise",
          "org.graalvm.word.impl to jdk.internal.vm.compiler",
          "org.graalvm.nativeimage.impl to org.graalvm.nativeimage.base,org.graalvm.nativeimage.builder,org.graalvm.nativeimage.configure,com.oracle.svm.svm_enterprise",
          "org.graalvm.nativeimage.impl.clinit to org.graalvm.nativeimage.builder",
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
    "LLVM_ORG_FILTERED": {
      "native": True,
      "description": "LLVM_ORG build with some things removed that we don't want to redistribute",
      "os_arch": {
        "windows": {
          "<others>": {
            "layout": {
              "./": {
                # Starting with LLVM 13, the LLVM build tries to create symlinks if possible.
                # On Windows, symlinks are only supported when developer mode is enabled.
                # Get rid of the symlinks here, so our users don't need to enable developer mode.
                "dereference": "always"
              },
            },
          },
        },
        "<others>": {
          "<others>": {
            "layout": {
              "./": {
                "dereference": "never"
              },
            },
          },
        },
      },
      "layout": {
        "./": {
          "source_type": "extracted-dependency",
          "dependency": "LLVM_ORG",
          "path": "*",
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
            "bin/llvm-windres*", # symlink to llvm-rc
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
      },
    },
    "LLVM_TOOLCHAIN": {
      "native": True,
      "description": "LLVM with general purpose patches used by Sulong and Native Image",
      "layout": {
        "./": [
          "extracted-dependency:LLVM_ORG_FILTERED",
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
