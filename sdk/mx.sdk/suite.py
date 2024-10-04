#
# Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
  "mxversion": "6.39.0",
  "name" : "sdk",
  "version" : "23.1.5",
  "release" : True,
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
      "licenses" : ["GPLv2-CPE", "GPLv2", "UPL", "BSD-new", "MIT", "NCSA", "ICU", "PSF-License", "BSD-simplified", "EPL-2.0"],
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
          },
          "<others>" : {
            "optional": True,
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
    "JLINE_READER": {
      "moduleName": "org.jline.reader",
      "digest": "sha512:777733fa5d19f34386e4ff4ac578fb8ef3bbe160db5755dc551a5ec085dd3d966d74525be0e2d0c7bab222f03e09e28190cb5b263a92c63c6683a09332accf2e",
      "sourceDigest": "sha512:a0f4c316c46f06ea30a6c6819d5c129dccc74b43fd8ba0380646feec89b61d0465a052645ca699211aab931daa1216b26de064ca2be9e16dfe842d2fd2a91404",
      "maven": {
        "groupId": "org.jline",
        "artifactId": "jline-reader",
        "version": "3.23.0",
      },
    },

    "JLINE_TERMINAL": {
      "moduleName": "org.jline.terminal",
      "digest": "sha512:d0d96389d750d6de82f4d8a29fc3756c0f28c19f520e761db69c6668b1e3fc9c2add30aee86ab7ac10426f2c075a63a0e5f7537096591fe585d8836f25c692ed",
      "sourceDigest": "sha512:931de13f023b9d15751c7e5b76ec9ad7811df19e7254146875ebd7e6d68e69764b916eef045ea743bd6d21515badeb1ddb795504d71ff1cad7e1b5889486c500",
      "maven": {
        "groupId": "org.jline",
        "artifactId": "jline-terminal",
        "version": "3.23.0",
      },
    },

    "JLINE_BUILTINS": {
      "moduleName": "org.jline.builtins",
      "digest": "sha512:166920f4252b4d6618a29aabc0e501930807c84df53cc727d238005aefc453b7c915345daa6653d281077e9dc25d3eb2f5a13ac9ceee1e230d9fd83b38113e32",
      "sourceDigest": "sha512:9b1c2cf976044810ea12e7a4c2b9462b33ce36c36716a2029b171dd3f4151d1852320d3b08f21bf5e86f694f85d77ccd71bbef679764dfa393516f6e0e0bfa32",
      "maven": {
        "groupId": "org.jline",
        "artifactId": "jline-builtins",
        "version": "3.23.0",
      },
    },
    "LLVM_ORG" : {
      "version" : "16.0.1-4-gad8c248269-bg39f02d0d6a",
      "host" : "https://lafo.ssw.uni-linz.ac.at/pub/llvm-org",
      "os_arch" : {
        "linux" : {
          "amd64" : {
            "urls" : ["{host}/llvm-llvmorg-{version}-linux-amd64.tar.gz"],
            "digest" : "sha512:fdef7aff621c533d30b89c15a3152dd2f6791c99317bb25295e4c9fc9e9b76a341174b55510c6a7e2df7e51be6aad473560927ee5e48605afa884a412147eb8c",
          },
          "aarch64" : {
            "urls" : ["{host}/llvm-llvmorg-{version}-linux-aarch64.tar.gz"],
            "digest" : "sha512:3c15573d19cb84aab1aea9ac5e1052b24002d9d46109b496cdd2f3d605177c1592e7fed5a7ba0ee7de1c4aed91e0fdc50c53d5018d364c61f5792d7e8f00bb2c",
          },
          "riscv64": {
            "urls" : ["{host}/llvm-llvmorg-16.0.1-4-gad8c248269-bge4d99266a2-linux-riscv64.tar.gz"],
            "digest" : "sha512:9186a20d4b657f8a4c86c6730d713c6f8f223a8e9ecceb88d8b5cd3c072e8e0159b810663e57076c0ddcdcd57a819b35b42b543e6633f012175b5f78a6d8de92",
          },
        },
        "darwin" : {
          "amd64" : {
            "urls" : ["{host}/llvm-llvmorg-{version}-darwin-amd64.tar.gz"],
            "digest" : "sha512:ae96a72ddeecd4bdc972adae01e6894d47d54024f50b996aa9d8df1a683c4a5faab435cd2d94d5fa4c0764f0b2902dbc30171ad254e0da94888ddc0bd018d4f0",
          },
          "aarch64" : {
            "urls" : ["{host}/llvm-llvmorg-{version}-darwin-aarch64.tar.gz"],
            "digest" : "sha512:9c57f30d5eed4162373ea9f342981843485dec5ebcefcf97f22e70e9af8a58167fc3eb3a383dfc9a00e061f58b8998fc063483b4f4e6ce5a74a0002f10a4e174",
          }
        },
        "windows" : {
          "amd64" : {
            "urls" : ["{host}/llvm-llvmorg-{version}-windows-amd64.tar.gz"],
            "digest" : "sha512:12e95f2b3ea64a059e8b73c67f5a7da6e6b30a068da65acb73da4e86ab2a376065105c84ecb90a7f7e2c31628642aba07f2b738fe18122524184c7b517e36e04",
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
      "version" : "16.0.1-4-gad8c248269-bg39f02d0d6a",
      "host" : "https://lafo.ssw.uni-linz.ac.at/pub/llvm-org",
      # we really want linux-amd64, also on non-linux and non-amd64 platforms for cross-compilation
      "urls" : ["{host}/compiler-rt-llvmorg-{version}-linux-amd64.tar.gz"],
      "digest" : "sha512:1520628266c4ca165fe299bcd7b7db087290f1a645bd41fc07779d771db0ba3308067f6caf9c39d7a76f3051023f481052035d78f598f40862bb91b462819afa",
      "license" : "Apache-2.0-LLVM",
    },
    "LLVM_ORG_SRC" : {
      "version" : "16.0.1-4-gad8c248269-bg39f02d0d6a",
      "host" : "https://lafo.ssw.uni-linz.ac.at/pub/llvm-org",
      "packedResource" : True,
      "urls" : ["{host}/llvm-src-llvmorg-{version}.tar.gz"],
      "digest" : "sha512:1bb2f66cc123bb9f0263cd186a8ab7948939f181001e57a7171466534bc89c0ebb17863e90c487f48083f202745ea3d90275a3fa26d793fd2b9f1b62d7e1eabd",
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
        "sdk:NATIVEIMAGE",
        "org.graalvm.options",
        "org.graalvm.collections",
        "org.graalvm.home",
      ],
      "requires" : [
        "java.logging",
      ],
      "annotationProcessors" : [
          "sdk:POLYGLOT_PROCESSOR"
      ],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "17+",
      "workingSets" : "API,SDK",
    },
    "org.graalvm.polyglot.processor" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
          "NATIVEBRIDGE_PROCESSOR"
      ],
      "requires" : [
        "java.compiler"
      ],
      "annotationProcessors" : [
      ],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "17+",
      "workingSets" : "API,Graal",
    },
    "org.graalvm.sdk" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
          "sdk:COLLECTIONS",
          "sdk:NATIVEIMAGE",
          "sdk:POLYGLOT",
          "sdk:WORD"],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "17+",
      "workingSets" : "API,SDK",
    },

    "org.graalvm.word" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "11+",
      "checkstyleVersion" : "10.7.0",
      "workingSets" : "API,SDK",
    },

    "org.graalvm.nativeimage" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "sdk:WORD",
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
      "javaCompliance" : "17+",
      "workingSets" : "SDK",
      "checkstyle" : "org.graalvm.word",
    },
    "org.graalvm.launcher" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "sdk:POLYGLOT",
        "JLINE3",
      ],
      "requires" : [
        "java.logging",
      ],
      "requiresConcealed" : {
        "java.base" : ["jdk.internal.module"],
      },
      "javaCompliance" : "17+",
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
      "javaCompliance" : "17+",
      "workingSets" : "Truffle,Tools,Test",
      "checkstyle" : "org.graalvm.word",
    },
    "org.graalvm.polyglot.tck" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "sdk:POLYGLOT",
      ],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "17+",
      "workingSets" : "API,SDK,Test",
    },
    "org.graalvm.collections" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "17+",
      "workingSets" : "API,SDK",
    },
    "org.graalvm.collections.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JUNIT",
        "COLLECTIONS",
      ],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "17+",
      "workingSets" : "API,SDK,Test",
    },
    "org.graalvm.home" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "NATIVEIMAGE",
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
        "sdk:POLYGLOT",
      ],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "17+",
      "workingSets" : "API,SDK",
    },
    "org.graalvm.jniutils" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
          "NATIVEIMAGE",
      ],
      "requires" : [
      ],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "17+",
    },
    "org.graalvm.nativebridge" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "JNIUTILS"
      ],
      "requires" : [
      ],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "17+",
    },
    "org.graalvm.nativebridge.processor" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
      ],
      "requires" : [
        "java.compiler"
      ],
      "annotationProcessors" : [
      ],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "17+",
      "workingSets" : "API,Graal",
    },
    "org.graalvm.nativebridge.processor.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JUNIT",
        "NATIVEBRIDGE",
      ],
      "annotationProcessors" : [
        "NATIVEBRIDGE_PROCESSOR",
      ],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "17+",
      "workingSets" : "Graal,Test",
      "jacoco" : "exclude",
      "testProject" : True,
    },
    "org.graalvm.toolchain.test" : {
      "class" : "ToolchainTestProject",
      "subDir" : "src",
      "buildDependencies" : ["LLVM_TOOLCHAIN"],
    },
    "org.graalvm.shadowed.org.jline": {
      # shaded JLINE_*
      "subDir": "src",
      "sourceDirs": ["src"],
      "javaCompliance": "17+",
      "spotbugs": "false",
      "requires": [
        "java.logging",
      ],
      "shadedDependencies": [
        "sdk:JLINE_READER",
        "sdk:JLINE_TERMINAL",
        "sdk:JLINE_BUILTINS",
      ],
      "class": "ShadedLibraryProject",
      "shade": {
        "packages": {
          "org.jline": "org.graalvm.shadowed.org.jline",
        },
        "include": [
          "org/jline/utils/*.caps",
          "org/jline/utils/*.txt",
          "org/jline/builtins/*.txt",
        ],
        "exclude": [
          "META-INF/MANIFEST.MF",
          # TTop.java would require java.lang.management (uses MXBean)
          "org/jline/builtins/TTop.java",
          # we patch the JLine's service loading mechanism with
          # hard-coded set of supported services, see one of the patches below
          "META-INF/services/**",
          "META-INF/maven/**",
          # We have our own native-image configuration
          "META-INF/native-image/**",
        ],
        "patch": {
          "org/jline/builtins/Nano.java": {
            # Remove dependency on UniversalDetector (doesn't work on native image)
            "import org.mozilla.universalchardet.UniversalDetector;": "",
            "\\Z":
              """
                // Stub for the removed class, we put it at the end of the file
                class UniversalDetector {
                    UniversalDetector(Object dummy) {}
                    void handleData(byte[] a, int b, int c) {}
                    void dataEnd() {}
                    String getDetectedCharset() { return null; }
                }""",
          },
          # Remove dependency on JLine's native library (would require shading and deployment of the library)
          # The native library is a fallback for functionality that is otherwise done via accessing
          # JDK internals via reflection.
          "org/jline/terminal/impl/AbstractPty.java": {
            "import org.graalvm.shadowed.org.jline.nativ.JLineLibrary;": "",
            "import org.graalvm.shadowed.org.jline.nativ.JLineNativeLoader;": "",
            "JLineNativeLoader.initialize\\(\\);": "",
            "return JLineLibrary.newFileDescriptor\\(fd\\);": "throw new RuntimeException(\"not implemented\");",
          },
          # Hard-coded list of terminal providers replaces a generic reflection based mechanism that
          # looks up the provider class names in the resources
          "org/jline/terminal/spi/TerminalProvider.java": {
            "import org.graalvm.shadowed.org.jline.terminal.Terminal;":
              """
                import org.graalvm.shadowed.org.jline.terminal.Terminal;
                import org.graalvm.shadowed.org.jline.terminal.impl.exec.ExecTerminalProvider;
              """,
            "static TerminalProvider load\\(String name\\) throws IOException \\x7b":
              """
              static TerminalProvider load(String name) throws IOException {
                  switch (name) {
                      case \"exec\":
                          return new ExecTerminalProvider();
                      default:
                        if (Boolean.TRUE) { // to avoid unreachable code
                            throw new IOException(\"Unable to find terminal provider \" + name);
                        }
                  }
                  // }
              """,
          },
        },
      },
      "description": "JLINE shaded library.",
      "allowsJavadocWarnings": True,
      "noMavenJavadoc": True,
      "javac.lint.overrides": 'none',
      "jacoco": "exclude",
    },
    "org.graalvm.maven.downloader" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "javaCompliance" : "17+",
      "license" : "UPL",
      "dependencies": [
        "sdk:NATIVEIMAGE",
      ],
      "requires": [
        "java.logging",
        "java.xml",
      ],
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
    "ICU" : {
      "name" : "Unicode/ICU License",
      "url" : "https://raw.githubusercontent.com/unicode-org/icu/main/LICENSE",
    },
    "GPLv2" : {
      "name" : "GNU General Public License, version 2",
      "url" : "http://www.gnu.org/licenses/old-licenses/gpl-2.0.html"
    },
    "PSF-License": {
      "name": "Python Software Foundation License",
      "url": "https://docs.python.org/3/license.html",
    },
    "BSD-simplified": {
      "name": "Simplified BSD License (2-clause BSD license)",
      "url": "http://opensource.org/licenses/BSD-2-Clause"
    },
    "EPL-2.0": {
      "name": "Eclipse Public License 2.0",
      "url": "https://opensource.org/licenses/EPL-2.0",
    },
},

  # ------------- Distributions -------------
  "distributions" : {
    "GRAAL_SDK" : {
      "subDir" : "src",
      "dependencies" : [
          "org.graalvm.sdk",
      ],
      "distDependencies" : [
          "sdk:COLLECTIONS",
          "sdk:NATIVEIMAGE",
          "sdk:POLYGLOT",
          "sdk:WORD",
      ],
      "javadocType": "api",
      "moduleInfo" : {
        "name" : "org.graalvm.sdk",
        "requires" : [
            "transitive java.logging",
            "transitive org.graalvm.word",
            "transitive org.graalvm.polyglot",
            "transitive org.graalvm.nativeimage",
            "transitive org.graalvm.collections",
        ],
        "exports" : [
            "org.graalvm.sdk"
        ],
        "uses" : [
        ],
        "opens" : [
        ],
      },
      "description" : "Shared library",
      "maven": {
          "tag": ["default", "public"],
      },
    },

    "NATIVEIMAGE" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.nativeimage",
        "com.oracle.svm.core.annotate",
      ],
      "distDependencies" : ["WORD"],
      "javadocType": "api",
      "moduleInfo" : {
        "name" : "org.graalvm.nativeimage",
        "requires" : [
            "transitive org.graalvm.word",
        ],
        "exports" : [
          "com.oracle.svm.core.annotate",
          "org.graalvm.nativeimage.hosted",
          "org.graalvm.nativeimage.c.function",
          "org.graalvm.nativeimage.c.struct",
          "org.graalvm.nativeimage.c.type",
          "org.graalvm.nativeimage.c.constant",
          "org.graalvm.nativeimage.c",
          "org.graalvm.nativeimage",
          """org.graalvm.nativeimage.impl to org.graalvm.nativeimage.pointsto,
                                             org.graalvm.nativeimage.base,
                                             org.graalvm.nativeimage.builder,
                                             org.graalvm.nativeimage.configure,
                                             com.oracle.svm.svm_enterprise,
                                             org.graalvm.extraimage.builder,
                                             org.graalvm.truffle.runtime.svm,
                                             com.oracle.svm.enterprise.truffle,
                                             org.graalvm.nativeimage.foreign""",
          "org.graalvm.nativeimage.impl.clinit to org.graalvm.nativeimage.builder",
        ],
        "uses" : [],
        "opens" : [],
      },
      "description" : "A framework that allows to customize native image generation.",
      "maven": {
          "tag": ["default", "public"],
      },
    },

    "POLYGLOT_PROCESSOR" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.polyglot.processor"
      ],
      "distDependencies" : ["sdk:NATIVEBRIDGE_PROCESSOR"],
      "maven": False,
    },

    "POLYGLOT_VERSION": {
      "type": "dir",
      "platformDependent": False,
      "layout": {
        "META-INF/graalvm/org.graalvm.polyglot/version": "dependency:VERSION/version",
      },
      "description": "Polyglot version.",
      "maven": False,
    },

    "POLYGLOT" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.polyglot",
        "org.graalvm.home",
        "POLYGLOT_VERSION",
      ],
      "distDependencies" : [
        "COLLECTIONS",
        "NATIVEIMAGE",
      ],
      "javadocType": "api",
      "moduleInfo" : {
        "name" : "org.graalvm.polyglot",
        "requires" : [
            "transitive java.logging",
            "org.graalvm.word",
            "org.graalvm.nativeimage",
            "org.graalvm.collections",
            # needed for dynamically loading Truffle
            "java.sql",
            "java.management",
            "jdk.unsupported",
            "jdk.management",
            "jdk.jfr",
        ],
        "exports" : [
          "org.graalvm.home",
          "org.graalvm.home.impl",
          "org.graalvm.polyglot.proxy",
          "org.graalvm.polyglot.io",
          "org.graalvm.polyglot.management",
          "org.graalvm.polyglot",
          "org.graalvm.options",
          "org.graalvm.polyglot.impl to org.graalvm.truffle, com.oracle.truffle.enterprise",
        ],
        "uses" : [
          "org.graalvm.polyglot.impl.AbstractPolyglotImpl"
        ],
        "opens" : [
          "org.graalvm.polyglot to org.graalvm.truffle"
        ],
      },
      "description" : "A framework that allows to embed polyglot language implementations in Java.",
      "maven" : {
        "groupId" : "org.graalvm.polyglot",
        "artifactId" : "polyglot",
        "tag": ["default", "public"],
      }
    },

    "COLLECTIONS" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.collections",
      ],
      "distDependencies" : [],
      "javadocType": "api",
      "moduleInfo" : {
        "name" : "org.graalvm.collections",
        "requires" : [],
        "exports" : [
           "org.graalvm.collections",
        ],
        "uses" : [],
        "opens" : [],
      },
      "description" : "A collections framework for GraalVM components.",
      "maven": {
          "tag": ["default", "public"],
      },
    },

    "WORD" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.word",
      ],
      "distDependencies" : [],
      "javadocType": "api",
      "moduleInfo" : {
        "name" : "org.graalvm.word",
        "requires" : [],
        "exports" : [
            "org.graalvm.word",
            "org.graalvm.word.impl to jdk.internal.vm.compiler",
        ],
        "uses" : [],
        "opens" : [],
      },
      "description" : "A low-level framework for machine-word-sized values in Java.",
      "maven": {
          "tag": ["default", "public"],
      },
    },

    "MAVEN_DOWNLOADER": {
      "moduleInfo" : {
        "name" : "org.graalvm.maven.downloader",
        "exports" : [
          "org.graalvm.maven.downloader",
        ],
      },
      "defaultBuild": False,
      "mainClass": "org.graalvm.maven.downloader.Main",
      "dependencies": [
        "org.graalvm.maven.downloader",
      ],
      "distDependencies": [
        "sdk:NATIVEIMAGE",
      ],
      "maven": False,
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
        "mx:JUNIT",
        "sdk:POLYGLOT",
        "sdk:NATIVEIMAGE",
        "sdk:COLLECTIONS",
        "sdk:LAUNCHER_COMMON"
      ],
      "maven" : False,
    },
    "JLINE3": {
      # shaded JLINE_*
      "moduleInfo": {
        "name": "org.graalvm.shadowed.jline",
        "requires": [
        ],
        "exports": [
          "org.graalvm.shadowed.org.jline.builtins",
          "org.graalvm.shadowed.org.jline.keymap",
          "org.graalvm.shadowed.org.jline.reader",
          "org.graalvm.shadowed.org.jline.reader.impl",
          "org.graalvm.shadowed.org.jline.reader.impl.completer",
          "org.graalvm.shadowed.org.jline.reader.impl.history",
          "org.graalvm.shadowed.org.jline.terminal",
          "org.graalvm.shadowed.org.jline.terminal.impl",
          "org.graalvm.shadowed.org.jline.terminal.spi",
          "org.graalvm.shadowed.org.jline.utils",
        ],
      },
      "subDir": "src",
      "sourceDirs": ["src"],
      "javaCompliance": "17+",
      "spotbugs": "false",
      "dependencies": [
        "org.graalvm.shadowed.org.jline",
      ],
      "description": "JLINE3 shaded module.",
      "allowsJavadocWarnings": True,
      "license": "BSD-new",
      "maven": {
        "groupId": "org.graalvm.shadowed",
        "artifactId": "jline",
        "tag": ["default", "public"],
      },
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
        "sdk:COLLECTIONS", "sdk:POLYGLOT",
        "JLINE3",
      ],
      "description" : "Common infrastructure to create language launchers using the Polyglot API.",
      "allowsJavadocWarnings": True,
      "maven": {
          "tag": ["default", "public"],
      },
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
        "sdk:COLLECTIONS", "sdk:POLYGLOT",
      ],
      "javadocType": "api",
      "description" : """GraalVM TCK SPI""",
      "maven": {
          "tag": ["default", "public"],
      },
    },
    "JNIUTILS" : {
      "moduleInfo" : {
        "name" : "org.graalvm.jniutils",
        "exports" : [
          "org.graalvm.jniutils",
        ],
      },
      "subDir" : "src",
      "dependencies" : ["org.graalvm.jniutils"],
      "distDependencies" : ["COLLECTIONS", "NATIVEIMAGE"],
      "description" : "Utilities for JNI calls from within native-image.",
      "allowsJavadocWarnings": True,
      "maven": {
          "tag": ["default", "public"],
      },
    },
    "NATIVEBRIDGE" : {
      "moduleInfo" : {
        "name" : "org.graalvm.nativebridge",
        "exports" : [
          "org.graalvm.nativebridge",
        ],
      },
      "subDir" : "src",
      "dependencies" : ["org.graalvm.nativebridge"],
      "distDependencies" : ["JNIUTILS"],
      "description" : "API and utility classes for nativebridge.",
      "allowsJavadocWarnings": True,
      "maven": {
          "tag": ["default", "public"],
      },
    },
    "NATIVEBRIDGE_PROCESSOR" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.nativebridge.processor"
      ],
      "distDependencies" : [],
      "maven": False,
    },
    "NATIVEBRIDGE_PROCESSOR_TEST" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.nativebridge.processor.test"
      ],
      "distDependencies" : [
        "mx:JUNIT",
        "NATIVEBRIDGE"
      ],
      "requiresConcealed": {
        "jdk.internal.vm.ci": [
          "jdk.vm.ci.services",
        ],
      },
      "maven": False,
      "testDistribution" : True,
    },
    "LLVM_TOOLCHAIN": {
      "native": True,
      "description": "LLVM with general purpose patches used by Sulong and Native Image",
      "os": {
        "windows": {
          # Starting with LLVM 13, the LLVM build tries to create symlinks if possible.
          # On Windows, symlinks are only supported when developer mode is enabled.
          # Get rid of the symlinks here, so our users don't need to enable developer mode.
          "defaultDereference": "always"
        },
        "<others>": {
        },
      },
      "layout": {
        "./": [
          {
            "source_type": "extracted-dependency",
            "dependency": "LLVM_ORG",
            "path": "*",
            "exclude": [
              # filter out some things that we don't want to redistribute
              "bin/bugpoint*",
              "bin/bbc",
              "bin/c-index-test*",
              "bin/clang-check*",
              "bin/clang-extdef-mapping*",
              "bin/clang-import-test*",
              "bin/clang-offload-*",
              "bin/clang-refactor*",
              "bin/clang-rename*",
              "bin/clang-scan-deps*",
              "bin/diagtool*",
              "bin/fir-opt",
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
              "bin/mlir-*",
              "bin/obj2yaml*",
              "bin/sancov*",
              "bin/sanstats*",
              "bin/scan-build*",
              "bin/scan-view*",
              "bin/tco",
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
              "lib/libMLIR*",
              "lib/libmlir*",
              "lib/lib*FIR*.a",
              "lib/libflang*.a",
              "lib/libFortranEvaluate.a",
              "lib/libFortranLower.a",
              "lib/libFortranParser.a",
              "lib/libFortranSemantics.a",
              "libexec",
              "lib/objects-Release",
              "include/mlir*",
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
    "LLVM_NINJA_TOOLCHAIN": {
      "native": True,
      "platformDependent": True,
      "os": {
        "linux": {
          "layout": {
            "toolchain.ninja": {
              "source_type": "string",
              "value": '''
include <ninja-toolchain:GCC_NINJA_TOOLCHAIN>
CC=<path:LLVM_TOOLCHAIN>/bin/clang
CXX=<path:LLVM_TOOLCHAIN>/bin/clang++
AR=<path:LLVM_TOOLCHAIN>/bin/llvm-ar
LDFLAGS=-fuse-ld=lld
'''
            },
          },
          "dependencies": [
            "mx:GCC_NINJA_TOOLCHAIN",
          ],
        },
        "darwin": {
          "layout": {
            "toolchain.ninja": {
              "source_type": "string",
              "value": '''
include <ninja-toolchain:GCC_NINJA_TOOLCHAIN>
CC=xcrun <path:LLVM_TOOLCHAIN>/bin/clang
CXX=xcrun <path:LLVM_TOOLCHAIN>/bin/clang++
AR=xcrun <path:LLVM_TOOLCHAIN>/bin/llvm-ar
'''
            },
          },
          "dependencies": [
            "mx:GCC_NINJA_TOOLCHAIN",
          ],
        },
        "windows": {
          "layout": {
            "toolchain.ninja": {
              "source_type": "string",
              "value": '''
include <ninja-toolchain:MSVC_NINJA_TOOLCHAIN>
CL=<path:LLVM_TOOLCHAIN>\\bin\\clang-cl
LINK=<path:LLVM_TOOLCHAIN>\\bin\\lld-link
LIB=<path:LLVM_TOOLCHAIN>\\bin\\llvm-lib
ML=<path:LLVM_TOOLCHAIN>\\bin\\llvm-ml
'''
            },
          },
          "dependencies": [
            "mx:MSVC_NINJA_TOOLCHAIN",
          ],
        },
      },
      "dependencies": [
        "LLVM_TOOLCHAIN",
        "org.graalvm.toolchain.test",
      ],
    },
  },
}
