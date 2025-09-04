#
# Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
  "mxversion": "7.58.6",
  "name" : "sdk",
  "version" : "26.0.0",
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
      "digest": "sha512:dafd45af79a9874d7b41d146d37386da605e19ec2bf9c8989f121f9403902ce60ca9708fb016afed785186935d2dddd4b5b3304aca234c926b0e4368607b11ed",
      "sourceDigest": "sha512:f8ec62508f3e83278156b7bd14d5daded72976b10da63adac53be2f7b1c5e6c69340fac5b2f10ffe7e92d86b27425beee73e5d5b3c775e72595b47158bfbed1b",
      "maven": {
        "groupId": "org.jline",
        "artifactId": "jline-reader",
        "version": "3.28.0",
      },
    },
    "JLINE_TERMINAL": {
      "moduleName": "org.jline.terminal",
      "digest": "sha512:abe0ad0303e5eb81b549301dfdcf34aace14495240816f14302d193296c7a8be31488e468d18a215976b8e4e8fa29f72d830e492eed7d4a6f9f04c81a6e36c3c",
      "sourceDigest": "sha512:cb70ad2bee2f7713fa5358c16fc7c53974c862e33957d3ec809468abcbc0b20de8546ecb41955dcc2003e702e5469069fd856a1ce51b132e29d0286beec4fe7e",
      "maven": {
        "groupId": "org.jline",
        "artifactId": "jline-terminal",
        "version": "3.28.0",
      },
    },
    "JLINE_BUILTINS": {
      "moduleName": "org.jline.builtins",
      "digest": "sha512:189d893405170a3edc624a6b822a8a394a2f8b623c23aed9e015d4b018b232307408b6038322719155fc7da7e9c04a9bb0a76c8521f49dd86a5f84ea3880acb6",
      "sourceDigest": "sha512:33f06d7e2bb232ce413d8cf7234bdb416c3a6770dcf18a5c17e3889887b287378ff5fc531758143383c3f50b56613e5bf802b5c49a2a09b748c804568e0565b8",
      "maven": {
        "groupId": "org.jline",
        "artifactId": "jline-builtins",
        "version": "3.28.0",
      },
    },
    "JLINE_TERMINAL_FFM": {
      "moduleName": "org.jline.terminal.ffm",
      "digest": "sha512:e5839b04a2fd6119a11c6bc16e05203af88512039d85551b19d6e87c358a325ed5eb7051022a225e2641357c99d9c4121817a4795c50cf79a13b6b9d537cee96",
      "sourceDigest": "sha512:c651ae99fe1f453d9b3d22913e2fb003c11ff9c43621bedd7508fa322b49f15c3d93cf146c00f2e1f9dd939f3ca9009a52ee407b63fa3f3d4f5c997a1efba139",
      "maven": {
        "groupId": "org.jline",
        "artifactId": "jline-terminal-ffm",
        "version": "3.28.0",
      },
    },
    "LLVM_ORG" : {
      "version" : "20.1.4-1-ga7183f5a17-bg217527b869",
      "host" : "https://lafo.ssw.uni-linz.ac.at/pub/llvm-org",
      "os_arch" : {
        "linux" : {
          "amd64" : {
            "urls" : ["{host}/llvm-llvmorg-{version}-linux-amd64.tar.gz"],
            "digest" : "sha512:7b01495c3af3f5cd6ce8835e56ca3c99f24c1ae91425c0d090018bdf1639a27c527b76dc93e9e940beacfaeb31b63729ec35c54972891b0aedc04006ab498a15",
          },
          "aarch64" : {
            "urls" : ["{host}/llvm-llvmorg-{version}-linux-aarch64.tar.gz"],
            "digest" : "sha512:6c193bd952a3efbaa0b12875984d3898a2cce5d62b385e1b2adb78aac38d4a7df34b0bba55d9b705c970b7e1cc146c48c8cfdfdfc03c89c468a6ed4ecd522be4",
          },
          "riscv64": {
            "urls" : ["{host}/llvm-llvmorg-{version}-linux-riscv64.tar.gz"],
            "digest" : "sha512:28a83428a925a3ce41c99e107df96b570131f4ae6e14ece29be0a038bb521e020f785645dd931f06110c292d4ca688d9272c67f97b426f16f8ff89c57a36f868",
          },
        },
        "darwin" : {
          "amd64" : {
            "urls" : ["{host}/llvm-llvmorg-{version}-darwin-amd64.tar.gz"],
            "digest" : "sha512:88da0b36e7bdf03b7ec683b7f6c044e7a884b9e1575ae7a82c76eb073278df9fcfe7cac9f2e827cd7312468e9a337572fa93dfb5a16360a634e96154ba121dd4",
          },
          "aarch64" : {
            "urls" : ["{host}/llvm-llvmorg-{version}-darwin-aarch64.tar.gz"],
            "digest" : "sha512:27ca7c82608e5fb379563ed3d39393e1a6c5c7300d42abb2ad9bdac232c5708511f287f562fa90ef15e069073f533f65ba73c8a9e5c95e7c0f04fdd15e84f65e",
          }
        },
        "windows" : {
          "amd64" : {
            "urls" : ["{host}/llvm-llvmorg-{version}-windows-amd64.tar.gz"],
            "digest" : "sha512:87a0337c0e73bef91952295e5510f54bffbdc395877e612e7e14dc2ab0943b9d460e9dbaf47dd41f619362315fc2db80fa97927ec95230a90eb895e3c30fd12f",
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
      "version" : "20.1.4-1-ga7183f5a17-bg217527b869",
      "host" : "https://lafo.ssw.uni-linz.ac.at/pub/llvm-org",
      # we really want linux-amd64, also on non-linux and non-amd64 platforms for cross-compilation
      "urls" : ["{host}/compiler-rt-llvmorg-{version}-linux-amd64.tar.gz"],
      "digest" : "sha512:70fb32a94e35b44a170737aa5ad188214b4c6d72a7aaae141b348f18c0d7815d99dfb117b6f5b0bd1b9d2821aa47d374496d58209c9219f11f0ad090a04ef40a",
      "license" : "Apache-2.0-LLVM",
    },
    "LLVM_ORG_SRC" : {
      "version" : "20.1.4-1-ga7183f5a17-bg217527b869",
      "host" : "https://lafo.ssw.uni-linz.ac.at/pub/llvm-org",
      "packedResource" : True,
      "urls" : ["{host}/llvm-src-llvmorg-{version}.tar.gz"],
      "digest" : "sha512:9c83d01eb6745a3e20e3426d8334728a977da11528819cdc835bb2d62925b4d15cd167a549e08c4f263d15461771be2bc92d1ea36c1fd99ab7888740733c534c",
      "license" : "Apache-2.0-LLVM",
    },
    "MUSL_GCC_TOOLCHAIN" : {
      "packedResource": True,
      "os_arch": {
        "linux": {
          "amd64": {
            "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/toolchain-gcc-musl/toolchain-gcc-10.3.0-zlib-1.2.13-musl-1.2.5.1-linux-amd64.tar.gz"],
            "digest" : "sha512:a4be5d7f0a0857e30992079b39b0b7a00b80b4f255a0dddf8e4208d53c0a2a79ad26dbcb3e7b4018bfc1ef5751cf2bf7180c64d6b13dd4c80ed2dd0335945736",
          },
          "aarch64": {
            "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/toolchain-gcc-musl/toolchain-gcc-10.3.0-zlib-1.2.13-musl-1.2.5.1-linux-aarch64.tar.gz"],
            "digest" : "sha512:7f645bceaad864e6a0cf3289ca234e76819f7bfc06abf0c82b59e51e152191235b65c3b7f4e9baa2f2c2982459ffec2c284d99cc28bf7923eb9022eef03ad72d",
          },
          "<others>": {
            "optional": True,
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

    "DACAPO_23.11_MR2_chopin" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/dacapo-23.11-MR2-chopin.zip"],
      "digest" : "sha512:1399c9a743d4a52202372d7a5acef7e5d90181b79194484056cb716ba0284224c9bd7a7620b6db3a7a3c4ccdb8427ee4843fca4d23e37c0b13ada6ce9f041b6f",
      "packedResource": True,
    },

    "DACAPO_23.11_MR2_chopin_minimal" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/dacapo-23.11-MR2-chopin-minimal.zip"],
      "digest" : "sha512:51c04f81564f758e12c43dc44fc70fe2de15c5ea368e759b898a41bac3a458a421c1dbf557676533ba97c82283055fe73637cdc8c100b82384e5af9380113d40",
      "packedResource": True,
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

    "RENAISSANCE_0.16.0" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/renaissance/renaissance-gpl-0.16.0.jar"],
      "digest" : "sha512:82cc829636f3420622b9ce55fb0406230a2a90692f03f0e85bfe6d99f1bd58ee9ec173695bd1c597aeae149b19391231d0f7fe47ca290334b2dba7c7cd3ef64e",
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
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "17+",
      "workingSets" : "API,SDK",
      "graalCompilerSourceEdition": "ignore",
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
      "checkstyleVersion" : "10.21.0",
      "workingSets" : "API,SDK",
    },

    "org.graalvm.word.test" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JUNIT",
        "org.graalvm.word"
      ],
      "javaCompliance" : "21+",
      "workingSets" : "SDK",
      "checkstyle" : "org.graalvm.word",
      "graalCompilerSourceEdition": "ignore",
      "testProject" : True,
      "jacoco" : "exclude",
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

    # Native Image API extensions for libgraal.
    "org.graalvm.nativeimage.libgraal" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "sdk:NATIVEIMAGE"
      ],
      "checkstyle" : "org.graalvm.word",
      "javaCompliance" : "21+"
    },

    "org.graalvm.webimage.api": {
        "subDir": "src",
        "sourceDirs": ["src"],
        "dependencies": [],
        "javaCompliance": "21+",
        "spotbugs": "true",
        "workingSets": "SDK",
        "checkstyle": "org.graalvm.word",
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
      "testProject" : True,
      "jacoco" : "exclude",
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
      "testProject" : True,
      "jacoco" : "exclude",
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
      "testProject" : True,
      "jacoco" : "exclude",
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
      "testProject" : True,
      "jacoco" : "exclude",
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
    "org.graalvm.nativebridge.benchmark": {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "dependencies" : [
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
    "org.graalvm.nativebridge.launcher": {
      "subDir": "src",
      "native": "executable",
      "deliverable": "launcher",
      "use_jdk_headers": True,
      "buildDependencies": [
      ],
      "os_arch": {
        "windows": {
          "<others>": {
            "cflags": ["/std:c++17"]
          }
        },
        "linux": {
          "<others>": {
            "toolchain": "sdk:LLVM_NINJA_TOOLCHAIN",
            "cflags": ["-std=c++17", "-g", "-Wall", "-Werror", "-D_GNU_SOURCE", "-stdlib=libc++"],
            "ldlibs": ["-ldl", "-pthread", "-stdlib=libc++", "-static-libstdc++", "-l:libc++abi.a"],
          },
        },
        "darwin": {
          "<others>": {
            "cflags": ["-std=c++17", "-g", "-Wall", "-Werror", "-pthread", "-ObjC++"],
            "ldlibs": ["-ldl", "-pthread", "-framework", "Foundation"],
          },
        },
      },
      "graalCompilerSourceEdition": "ignore",
    },
    "org.graalvm.toolchain.test" : {
      "class" : "ToolchainTestProject",
      "subDir" : "src",
      "buildDependencies" : ["LLVM_TOOLCHAIN"],
      "graalCompilerSourceEdition": "ignore",
    },
    "org.graalvm.shadowed.org.jline": {
      # shaded custom JLine bundle
      "subDir": "src",
      "sourceDirs": ["src"],
      "javaCompliance": "17+",
      "spotbugs": "false",
      "requires": [
        "java.logging",
      ],
      "dependencies": [
        "sdk:NATIVEIMAGE",
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
          # Adds calls to initialize logging. This is a convenient way to enable JLine logging
          # in order to verify which terminal provider is used at runtime.
          "org/jline/terminal/TerminalBuilder.java": {
            "private TerminalBuilder\\(\\) {}": "private TerminalBuilder() { org.graalvm.shadowed.org.jline.terminal.JLineLoggingSupport.init(); }"
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
          "org/jline/terminal/impl/exec/ExecTerminalProvider.java": {
            "import org.graalvm.shadowed.org.jline.nativ.JLineLibrary;": "",
            "import org.graalvm.shadowed.org.jline.nativ.JLineNativeLoader;": "",
            "JLineNativeLoader.initialize\\(\\);": "",
            "return JLineLibrary.newRedirectPipe\\(fd\\);": "throw new RuntimeException(\"not implemented\");",
          },
          # Hard-coded list of terminal providers replaces a generic reflection based mechanism that
          # looks up the provider class names in the resources
          "org/jline/terminal/spi/TerminalProvider.java": {
            "import org.graalvm.shadowed.org.jline.terminal.Terminal;":
              """
                import org.graalvm.shadowed.org.jline.terminal.Terminal;
                import org.graalvm.shadowed.org.jline.terminal.impl.exec.ExecTerminalProvider;
              """,
            # \\x7b is to avoid the opening curly brace, which confuses the suite.py parser.
            # The commented out closing curly brace at the end is to match the opening
            # brace, again, to make the suite.py parser happy.
            "static TerminalProvider load\\(String name\\) throws IOException \\x7b":
              """
              static TerminalProvider load(String name) throws IOException {
                  switch (name) {
                      case \"exec\":
                          return new ExecTerminalProvider();
                      case \"ffm\":
                          TerminalProvider p = org.graalvm.shadowed.org.jline.terminal.impl.ffm.FFMTerminalProviderLoader.load();
                          if (p != null) {
                              return p;
                          }
                      default:
                          if (Boolean.TRUE) { // to avoid unreachable code below the switch
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
    "org.graalvm.shadowed.org.jline.jdk22": {
      # Shaded JLINE_TERMINAL_FFM as jdk22 overlay for the shaded JLine bundle
      # Needs --enable-native-access=org.graalvm.shadowed.jline
      "subDir": "src",
      "sourceDirs": ["src"],
      "javaCompliance": "22+",
      "spotbugs": "false",
      "requires": [
        "java.logging",
      ],
      "dependencies": [
        "org.graalvm.shadowed.org.jline",
        "sdk:NATIVEIMAGE",
      ],
      "shadedDependencies": [
        "sdk:JLINE_TERMINAL_FFM",
      ],
      "class": "ShadedLibraryProject",
      "shade": {
        "packages": {
          "org.jline": "org.graalvm.shadowed.org.jline",
        },
        "exclude": [
          "META-INF/MANIFEST.MF",
          # we patch the JLine's service loading mechanism with
          # hard-coded set of supported services, see one of the patches below
          "META-INF/services/**",
          "META-INF/maven/**",
          # We have our own native-image configuration (in the overlaid project)
          "META-INF/native-image/**",
        ],
      },
      "description": "JLINE FFM based Terminal service provider.",
      "overlayTarget" : "org.graalvm.shadowed.org.jline",
      "multiReleaseJarVersion" : "22",
      "ignoreSrcGenForOverlayMap": "true",
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
    "org.graalvm.resourcecopy" : {
      "subDir" : "src",
      "sourceDirs" : ["src"],
      "javaCompliance" : "17+",
      "license" : "UPL",
      "dependencies": [
        "sdk:POLYGLOT",
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
    "Oracle Proprietary": {
      "name": "ORACLE PROPRIETARY/CONFIDENTIAL",
      "url": "http://www.oracle.com/us/legal/copyright/index.html"
    },
    "GFTC": {
      "name": "GraalVM Free Terms and Conditions (GFTC) including License for Early Adopter Versions",
      "url": "https://www.oracle.com/downloads/licenses/graal-free-license.html"
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

    "NATIVEIMAGE_LIBGRAAL" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.nativeimage.libgraal",
      ],
      "distDependencies" : ["NATIVEIMAGE"],
      "javadocType": "api",
      "moduleInfo" : {
        "name" : "org.graalvm.nativeimage.libgraal",
        "requires" : [
          "transitive org.graalvm.nativeimage",
        ],
        "exports" : [
          "org.graalvm.nativeimage.libgraal",
          "org.graalvm.nativeimage.libgraal.hosted",
          "org.graalvm.nativeimage.libgraal.impl to org.graalvm.nativeimage.builder",
        ],
        "uses" : [],
        "opens" : [],
      },
      "description" : "Native Image API extensions for libgraal.",
      "maven": {
        # Explicitly set the artifactId here instead of relying on mx automatically
        # deriving it from the distribution name. This also makes the maven
        # coordinates stable in case of the (unlikely) event that the distribution
        # is renamed.
        "artifactId": "nativeimage-libgraal",
        "tag": ["default", "public"],
      },
    },

    "WEBIMAGE_PREVIEW": {
      "subDir": "src",
      "dependencies": [
        "org.graalvm.webimage.api",
      ],
      "distDependencies": [],
      "moduleInfo": {
        "name": "org.graalvm.webimage.api",
        "exports": [
          "org.graalvm.webimage.api",
        ],
      },
      "description": "The JavaScript interoperability API for GraalVM Web Image. This API is currently in preview and subject to change at any time.",
      "maven": {
        "artifactId": "webimage-preview",
        "tag": ["default", "public"],
      },
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

    "MAVEN_DOWNLOADER_VERSION": {
      "type": "dir",
      "platformDependent": False,
      "layout": {
        "META-INF/graalvm/org.graalvm.maven.downloader/version": "dependency:sdk:VERSION/version",
      },
      "description": "Maven downloader version.",
      "maven": False,
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
        "MAVEN_DOWNLOADER_VERSION",
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
        "org.graalvm.word.test",
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
        "sdk:WORD",
        "sdk:LAUNCHER_COMMON"
      ],
      "maven" : False,
      "graalCompilerSourceEdition": "ignore",
    },
    "JLINE3": {
      # Custom shaded JLine bundle (with FFM terminal provider on JDK22+)
      # One must pass --enable-native-access=org.graalvm.shadowed.jline, otherwise
      # JLine silently falls back to exec provider on POSIX, and with a warning
      # to "Dumb" provider on Windows
      # If desired, the FFM terminal on JDK22+ can be disabled at built time using system property:
      # org.graalvm.shadowed.org.jline.terminal.ffm.disable=true
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
          "org.graalvm.shadowed.org.jline.terminal.impl.exec",
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
      "distDependencies": [
         "sdk:NATIVEIMAGE",
         "sdk:POLYGLOT",
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
    "NATIVEBRIDGE_LAUNCHER_RESOURCES": {
      "type": "dir",
      "platformDependent": True,
      "platforms": [
          "linux-amd64",
          "linux-aarch64",
          "darwin-amd64",
          "darwin-aarch64",
          "windows-amd64",
          "windows-aarch64",
      ],
      "layout": {
        "<os>/<arch>/": "dependency:org.graalvm.nativebridge.launcher",
      },
      "description": "Contains a launcher for process isolated polyglot.",
      "maven": False,
      "graalCompilerSourceEdition": "ignore",
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
    "NATIVEBRIDGE_BENCHMARK": {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.nativebridge.benchmark"
      ],
      "distDependencies" : [
        "NATIVEBRIDGE"
      ],
      "maven": False,
      "testDistribution" : True,
      "graalCompilerSourceEdition": "ignore",
    },
    "RESOURCECOPY" : {
      "subDir" : "src",
      "dependencies" : [
        "org.graalvm.resourcecopy"
      ],
      "distDependencies" : [
        "POLYGLOT",
      ],
      "maven": False,
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
      "native_toolchain": {
        "kind": "ninja",
        "compiler": "llvm-toolchain",
        # empty, so it defaults everything to host properties
        "target": {},
      },
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
LDFLAGS=-fuse-ld=lld
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
        "compiler": "gcc",
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
CC=<path:MUSL_GCC_TOOLCHAIN>/musl-toolchain/bin/x86_64-linux-musl-gcc
CXX=<path:MUSL_GCC_TOOLCHAIN>/musl-toolchain/bin/x86_64-linux-musl-g++
AR=<path:MUSL_GCC_TOOLCHAIN>/musl-toolchain/bin/x86_64-linux-musl-ar
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
CC=<path:MUSL_GCC_TOOLCHAIN>/musl-toolchain/bin/aarch64-linux-musl-gcc
CXX=<path:MUSL_GCC_TOOLCHAIN>/musl-toolchain/bin/aarch64-linux-musl-g++
AR=<path:MUSL_GCC_TOOLCHAIN>/musl-toolchain/bin/aarch64-linux-musl-ar
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
        "compiler": "gcc",
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
set(CMAKE_C_COMPILER   <path:MUSL_GCC_TOOLCHAIN>/musl-toolchain/bin/x86_64-linux-musl-gcc)
set(CMAKE_CXX_COMPILER <path:MUSL_GCC_TOOLCHAIN>/musl-toolchain/bin/x86_64-linux-musl-g++)
set(CMAKE_AR           <path:MUSL_GCC_TOOLCHAIN>/musl-toolchain/bin/x86_64-linux-musl-ar)
'''
              },
            },
            "dependencies": [
              "MUSL_GCC_TOOLCHAIN",
            ],
          },
          "aarch64": {
            "layout" : {
              "toolchain.cmake" : {
                "source_type": "string",
                "value": '''
set(CMAKE_C_COMPILER   <path:MUSL_GCC_TOOLCHAIN>/musl-toolchain/bin/aarch64-linux-musl-gcc)
set(CMAKE_CXX_COMPILER <path:MUSL_GCC_TOOLCHAIN>/musl-toolchain/bin/aarch64-linux-musl-g++)
set(CMAKE_AR           <path:MUSL_GCC_TOOLCHAIN>/musl-toolchain/bin/aarch64-linux-musl-ar)
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
