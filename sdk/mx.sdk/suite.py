#
# Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
  "mxversion": "7.22.0",
  "name" : "sdk",
  "version" : "24.1.0",
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
      "licenses" : ["GPLv2-CPE", "GPLv2", "UPL", "BSD-new", "MIT", "NCSA", "ICU", "PSF-License", "BSD-simplified", "EPL-2.0"],
      "mavenId" : "lafo",
    },
  },
  "snippetsPattern" : ".*(Snippets|doc-files).*",
  "defaultLicense" : "UPL",
  "ignore_suite_commit_info": True,
  "libraries" : {
    "WRK_MULTIARCH": {
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/wrk-a211dd5-multiarch-2.0.tar.gz"],
      "digest": "sha512:b25a315ce50b446cb7f715ad93e718b937d2ed5c2be9ad0308935a7ccfca92f8d74aff403b5aff53fdedd0f71fd7beb08c6060b904f23fbd27ff6a40a1848770",
      "packedResource": True,
      "license": "Apache-2.0-wrk-a211dd5",
    },
    "WRK2_MULTIARCH": {
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/wrk2-multiarch-2.1.tar.gz"],
      "digest": "sha512:ed237a2d899782198adbc6cad70e971590379526a01465917fc6fbcbbac6a7f2cf015a5b441fcc617d72526aaf9be8db478e201bb390f7090c32b03a9e5f8614",
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
      "version" : "18.1.3-4-gd3f23e9e73-bg3b8289d0a4",
      "host" : "https://lafo.ssw.uni-linz.ac.at/pub/llvm-org",
      "os_arch" : {
        "linux" : {
          "amd64" : {
            "urls" : ["{host}/llvm-llvmorg-{version}-linux-amd64.tar.gz"],
            "digest" : "sha512:6ed66161ae39849d92ba13b16759e5c97a6a499bd61fce0a7516e9813e201a8142e8e98e13baac74150916b72a284850e0fff878ddc97017f63dc6c4f83f6004",
          },
          "aarch64" : {
            "urls" : ["{host}/llvm-llvmorg-{version}-linux-aarch64.tar.gz"],
            "digest" : "sha512:609ff4ccfb1249413a18656543d149bc3ed04d753ba2873ba82b75d50c4db9cbe47a60a33e63f479b20ec472957533b97bb279d7c9185fcdf6537aae85ac7ad7",
          },
          "riscv64": {
            "urls" : ["{host}/llvm-llvmorg-{version}-linux-riscv64.tar.gz"],
            "digest" : "sha512:e4ad54bef601c1ff3f75e63816590bf578180285316440ec6ed4a6a454323ac1f62e14630aee64b2d44654dbc7e7a2c1a868ca3a2d15c40a977d3f9b8a94d036",
          },
        },
        "darwin" : {
          "amd64" : {
            "urls" : ["{host}/llvm-llvmorg-{version}-darwin-amd64.tar.gz"],
            "digest" : "sha512:26d827536104e06f4baf964dd8d999266d3a89442c658d4490bef412fd12dfaa3bc06d9f748531b280b121d88e1fd9ed53aba8d217bae7f8a6e60d0f10e842be",
          },
          "aarch64" : {
            "urls" : ["{host}/llvm-llvmorg-{version}-darwin-aarch64.tar.gz"],
            "digest" : "sha512:741a2d007b9ce34d8f46e785d528cd116f3937bc2b403f68db405cfc272025d4b382ae234e2c0b3bba414e61abfc7a85aa8dfc6306cd274289bd1f10fb033e41",
          }
        },
        "windows" : {
          "amd64" : {
            "urls" : ["{host}/llvm-llvmorg-{version}-windows-amd64.tar.gz"],
            "digest" : "sha512:7e1e1eae6ea1218486c752a04356a516bc1fbba52b09235f64c8bec39d3287a7157ea5ffd9f09bf6172e1af976e691b373dbae6e4cadc512ce7e4d054d282848",
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
      "version" : "18.1.3-4-gd3f23e9e73-bg3b8289d0a4",
      "host" : "https://lafo.ssw.uni-linz.ac.at/pub/llvm-org",
      # we really want linux-amd64, also on non-linux and non-amd64 platforms for cross-compilation
      "urls" : ["{host}/compiler-rt-llvmorg-{version}-linux-amd64.tar.gz"],
      "digest" : "sha512:15334c645908d9195893c3fada3c291a5c2dc93d02de797b117ee801df59bd0087835dc2862d97b2862136961e474cedc9d3f90bb94d54d27af66021372e5172",
      "license" : "Apache-2.0-LLVM",
    },
    "LLVM_ORG_SRC" : {
      "version" : "18.1.3-4-gd3f23e9e73-bg3b8289d0a4",
      "host" : "https://lafo.ssw.uni-linz.ac.at/pub/llvm-org",
      "packedResource" : True,
      "urls" : ["{host}/llvm-src-llvmorg-{version}.tar.gz"],
      "digest" : "sha512:7eb738373300c6c86acc081b9cd29e82c4f3ef4ebcf7ecd1b37e60346edbf0eb4b8edbc14d440a2f896966ed1f8824f940aca467f22630c81ee1403acefff2bd",
      "license" : "Apache-2.0-LLVM",
    },
    "MUSL_GCC_TOOLCHAIN" : {
      "packedResource": True,
      "os_arch": {
        "linux": {
          "amd64": {
            "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/toolchain-gcc-musl/toolchain-gcc-10.2.1-musl-1.2.2-linux-amd64.tar.gz"],
            "digest" : "sha512:8f49b04d4826c560c791e5223f504046fa0daa6b79e581ea1781a2d01f4efe2de4a0fb6771dc1b07318ab0109a61ea3b04255eadf36191a76687f873931eb283",
          },
          "aarch64": {
            "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/toolchain-gcc-musl/toolchain-gcc-10.2.1-musl-1.2.2-linux-aarch64.tar.gz"],
            "digest" : "sha512:f5545f6b36c2306861c026895d437a57357515e8dfefb0e8419413f61b146f42dc072f8a8a7a9f4885d6448396d656f59264e61e3f5eedd278486228aa58904e",
          },
        },
        "<others>": {
          "<others>": {
            "optional": True,
          }
        }
      },
    },
    "TIKA_1.0.11": {
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/tika-1.0.11.zip"],
      "digest": "sha512:0d45935c428bb3226b0d22b8ea8a8f2eb9e145a72225c6320eeb7c7d1cebc644921f6b77d379a9356bfcbdb66e9daf351d2aef18ff47fa67dad2d5cb15def875",
      "packedResource": True,
    },

    "MICRONAUT_MUSHOP_0.0.2" : {
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/mu-shop-0.0.2.zip"],
      "digest": "sha512:e0a503423c1cf94611e9dc14c4d8f06ae25081745b3ebe47b91225c188713537a55c9281a865d1ada364fddaac7d17b90b7da728ccf3975e4d2f584d21bee550" ,
      "packedResource": True
    },

    "QUARKUS_REGISTRY_0.0.2" : {
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/quarkus-registry-0.0.2.zip"],
      "digest": "sha512:0b35bc8090514cdcb7471966858c8850bdc9132432fd392593a084c95af1d5b57ed8cd31969cf7bc94583c1973103fc3b7f5364fd5c7450335783f76cc9242aa" ,
      "packedResource": True
    },

    "PETCLINIC_3.0.1": {
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/spring-petclinic-3.0.1.zip"],
      "digest": "sha512:37d50049530ac23342a0e5adcf1c2a72b64312685ce407bc9dac577835d0df5e0548e72676e8a3a7bc670cf603d6b0c6e450f1ce8592f1f17681b6ddea602cda",
      "packedResource": True,
    },

    "SHOPCART_0.3.10": {
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/shopcart-0.3.10.zip"],
      "digest": "sha512:99099b4c013527c769b0c72637e5c450e1dfad7a847eddca453377260d3146cc091fe72f8ac6e93eb2a76290e85d9721ea7c964d0185fe82fca86f7175662bde",
      "packedResource": True,
    },

    "SPRING_HW_3.0.6": {
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/spring-hello-world-3.0.6.zip"],
      "digest": "sha512:15a7fd76e47af62b25f66be0b12020f068202ced3b5cb96e6aed8b23cd60a037595ebb1b843a2e123051c08a18b5348133bc419c2b1f94364d263c13b3268ee1",
      "packedResource": True,
    },

    "QUARKUS_HW_1.0.6": {
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/quarkus-hello-world-1.0.6.zip"],
      "digest": "sha512:c7678c9169dc6ba445fff8dbef7bc61579084c74eee4233d3deb3d56e73429b9cd894a3e98f7d8d7fe39b4f181b77ba916afa559a2f91de30a309fb9bdd5c56b",
      "packedResource": True,
    },

    "MICRONAUT_HW_1.0.7": {
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/micronaut-hello-world-1.0.7.zip"],
      "digest": "sha512:f4fdba567c055ed0190c73a96c746947091751bad6ccea75a5826d72acbbedfd66736259b7e3fb2fadbb645e88c92f6ac17948cb1b3a435407f20dc77e16a61e",
      "packedResource": True,
    },

    "DACAPO" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/dacapo-9.12-bach-patched.jar"],
      "digest" : "sha512:cd25bcc7d72d5ce3025413db6f256f4e37327b04c94fce823b25396421c5a77a6e2671ad2354b693bad908daa8cbc26c150eea98c0479a1e5a00b65ef7e57c2a",
    },

    "DACAPO_MR1_BACH" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/dacapo-9.12-MR1-bach.jar"],
      "digest" : "sha512:09cab128f15ed13d49edb0f598be7e4426fefb87565bb10d522e6b1995543bf2c0810d8ce71f6847ebff072e35b0dbfd3137fc1a43d3c4db6cc9ae8852ec79cd",
    },

    "DACAPO_MR1_2baec49" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/dacapo-9.12-MR1-2baec49.jar"],
      "digest" : "sha512:3707b0aee8b1f53828832a85d118981c9b0919c7c1f26dc86fd368d8092857a0b5b75cb5427fee791b0ea89bb7b60a66e2130f55a037c8b082235d3035d9e9cf",
    },

    "DACAPO_SCALA" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/dacapo-scala-0.1.0-20120216.jar"],
      "digest" : "sha512:9a3908f3a0f2937bbc7edcd78f7e7e229bea8dee566d5f2d922bd4dc3c5b02cf97de294e479200372ff90bfbdf80e88dc7fc9fda9cb294088346e4a5ff28893e",
    },

    "DACAPO_D3S" : {
      # original: https://d3s.mff.cuni.cz/software/benchmarking/files/dacapo-9.12-d3s.jar
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/dacapo-9.12-d3s.jar"],
      "digest" : "sha512:8a96e7ca560ba221169872006f61c4662464e13c2bd50f3a86d307b65de6a984dd72f2f7e316d188a2758121902be8a54e52b8e54d7d04f3ea46eefb6898b94a",
    },

    "RENAISSANCE_0.14.1" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/renaissance/renaissance-gpl-0.14.1.jar"],
      "digest" : "sha512:75dcb0e7891eca287c4ee575ee02e14a97e281bc680499ac313928a13f95ee3638f17d591c3c67b89f12b49b4fe5efb6a64d3fa0bb1670bf5d982c7e7081894f",
    },

    "RENAISSANCE_0.15.0" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/renaissance/renaissance-gpl-0.15.0.jar"],
      "digest" : "sha512:2682ac2dd133efb143352fc571671859980044565470188ea26a95926132f43477686fe321f012f7af9ba5f4022b797e4b7591f63bb12450a80f820456a54ac2",
    },

    "UBENCH_AGENT_DIST" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/java-ubench-agent-2e5becaf97afcf64fd8aef3ac84fc05a3157bff5.zip"],
      "digest" : "sha512:5ec1781aaceb3c1c6123e6db52a49967399f42ac9c81ef87e2abdf7b8d4a8cd0dac6b8e31e9a57ee0e890b15e1a9326a4a3e44e742f9aa1cba7836361c50b921",
    },

    # Required to run SPECJBB2015 on JDK >=11
    "ACTIVATION_1.1.1" : {
      "digest" : "sha512:49119b0cc3af02700685a55c6f15e6d40643f81640e642b9ea39a59e18d542f8837d30b43b5be006ce1a98c8ec9729bb2165c0442978168f64caa2fc6e3cb93d",
      "maven" : {
        "groupId" : "javax.activation",
        "artifactId" : "activation",
        "version" : "1.1.1",
      },
    },
    "JAXB_API_2.1" : {
      "digest" : "sha512:8cf015e64a33b6f6076259f09da1262efebc772d9d94764027a02d57a891a0f5fb9c2dcaab748f6cb6a29ab107ba0f3ad106d815bdb69f72786d30e25951a15f",
      "maven" : {
        "groupId" : "javax.xml.bind",
        "artifactId" : "jaxb-api",
        "version" : "2.1",
      },
    },
    "JAXB_IMPL_2.1.17" : {
      "digest" : "sha512:a60c4750b56acd60d0ea78c5d0be7abf6618abe44ab846f2388594fedb78a96222d1e998d42adfbd6ade666f2c8128373af88f73967ff46965f855a31a27b588",
      "maven" : {
        "groupId" : "com.sun.xml.bind",
        "artifactId" : "jaxb-impl",
        "version" : "2.1.17",
      },
      "dependencies": ["JAXB_API_2.1", "ACTIVATION_1.1.1"]
    },
    "AWFY_1.1" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/awfy/awfy-770c6649.jar"],
      "digest" : "sha512:ecd9beb26b845fe9635399ed58a4f1098ca842525d2f89b0f1cf1c3580c0c163a342edb9d9685f43503792d450955dc7822c5d07d627baf1814b0e10695a6fa5",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
    },
    "org.graalvm.toolchain.test" : {
      "class" : "ToolchainTestProject",
      "subDir" : "src",
      "buildDependencies" : ["LLVM_TOOLCHAIN"],
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      },
      "graalCompilerSourceEdition": "ignore",
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
            "org.graalvm.word.impl to jdk.graal.compiler",
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
      "mainClass": "org.graalvm.maven.downloader.Main",
      "dependencies": [
        "org.graalvm.maven.downloader",
      ],
      "distDependencies": [
        "sdk:NATIVEIMAGE",
      ],
      "allowsJavadocWarnings": True,
      "noMavenJavadoc": True,
      "description" : "Helpers to download maven artifacts without maven.",
      "maven": {
        "tag": ["default", "public"],
      },
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
    },
    "LAUNCHER_COMMON" : {
      "subDir" : "src",
      "moduleInfo" : {
        "name" : "org.graalvm.launcher",
        "exports" : [
          "org.graalvm.launcher",
        ],
        "requires" : [
          "org.graalvm.collections",
          "org.graalvm.nativeimage",
        ],
      },
      "dependencies" : [
        "org.graalvm.launcher",
      ],
      "distDependencies" : [
        "sdk:COLLECTIONS",
        "sdk:NATIVEIMAGE",
        "sdk:POLYGLOT",
        "JLINE3",
      ],
      "description" : "Common infrastructure to create language launchers using the Polyglot API.",
      "allowsJavadocWarnings": True,
      "maven": {
          "tag": ["default", "public"],
      },
      "graalCompilerSourceEdition": "ignore",
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
        "sdk:COLLECTIONS",
        "sdk:POLYGLOT",
      ],
      "javadocType": "api",
      "description" : """GraalVM TCK SPI""",
      "useModulePath": True,
      "maven": {
          "tag": ["default", "public"],
      },
      "graalCompilerSourceEdition": "ignore",
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
        "requires": [
          "org.graalvm.collections",
          "org.graalvm.nativeimage",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
      "graalCompilerSourceEdition": "ignore",
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
CFLAGS=
CXXFLAGS=
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
CFLAGS=
CXXFLAGS=
LDFLAGS=
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
CFLAGS=
CXXFLAGS=
LDFLAGS=
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
      "graalCompilerSourceEdition": "ignore",
    },
    "MUSL_NINJA_TOOLCHAIN" : {
      "native" : True,
      "platformDependent" : True,
      "native_toolchain" : {
        "kind": "ninja",
        "target": {
          # host os/arch
          "libc": "musl",
        },
      },
      "os_arch": {
        "linux": {
          "amd64": {
            "layout" : {
              "toolchain.ninja" : {
                "source_type": "string",
                "value": '''
include <ninja-toolchain:GCC_NINJA_TOOLCHAIN>
CC=<path:MUSL_GCC_TOOLCHAIN>/x86_64-linux-musl-native/bin/gcc
CXX=<path:MUSL_GCC_TOOLCHAIN>/x86_64-linux-musl-native/bin/g++
AR=<path:MUSL_GCC_TOOLCHAIN>/x86_64-linux-musl-native/bin/ar
CFLAGS=
CXXFLAGS=
LDFLAGS=
'''
              },
            },
            "dependencies": [
              "MUSL_GCC_TOOLCHAIN",
              "mx:GCC_NINJA_TOOLCHAIN",
            ],
          },
          "aarch64": {
            "layout" : {
              "toolchain.ninja" : {
                "source_type": "string",
                "value": '''
include <ninja-toolchain:GCC_NINJA_TOOLCHAIN>
CC=<path:MUSL_GCC_TOOLCHAIN>/aarch64-linux-musl-native/bin/gcc
CXX=<path:MUSL_GCC_TOOLCHAIN>/aarch64-linux-musl-native/bin/g++
AR=<path:MUSL_GCC_TOOLCHAIN>/aarch64-linux-musl-native/bin/ar
CFLAGS=
CXXFLAGS=
LDFLAGS=
'''
              },
            },
            "dependencies": [
              "MUSL_GCC_TOOLCHAIN",
              "mx:GCC_NINJA_TOOLCHAIN",
            ],
          },
        },
        "<others>": {
          "<others>": {
            "optional": True,
          }
        },
      },
      "maven" : False,
      "graalCompilerSourceEdition": "ignore",
    },
    "MUSL_CMAKE_TOOLCHAIN" : {
      "native" : True,
      "platformDependent" : True,
      "native_toolchain" : {
        "kind": "cmake",
        "target": {
          # host os/arch
          "libc": "musl",
        },
      },
      "os_arch": {
        "linux": {
          "amd64": {
            "layout" : {
              "toolchain.cmake" : {
                "source_type": "string",
                "value": '''
set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_C_COMPILER   <path:MUSL_GCC_TOOLCHAIN>/x86_64-linux-musl-native/bin/gcc)
set(CMAKE_CXX_COMPILER <path:MUSL_GCC_TOOLCHAIN>/x86_64-linux-musl-native/bin/g++)
set(CMAKE_AR           <path:MUSL_GCC_TOOLCHAIN>/x86_64-linux-musl-native/bin/ar)
'''
              },
            },
            "dependencies": [
              "MUSL_GCC_TOOLCHAIN",
            ],
          },
        },
        "<others>": {
          "<others>": {
            "optional": True,
          }
        },
      },
      "maven" : False,
      "graalCompilerSourceEdition": "ignore",
    },
  },
}
