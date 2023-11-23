#
# Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

suite = {
  "mxversion": "6.27.1",
  "name": "java-benchmarks",

  "ignore_suite_commit_info": True,

  "javac.lint.overrides": "none",

  "libraries" : {
    "TIKA_1.0.10": {
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/tika-1.0.10.zip"],
      "digest": "sha512:023e3c575c88fa2754538bf9f61a0642f12f34b6239d17df184f92c5708e1b2fdc3b4219c9d658ab88d5f9ed444d2883a500014df3c7942263050da256c6a5a3",
      "packedResource": True,
    },

    "MICRONAUT_MUSHOP_0.0.1" : {
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/mu-shop-0.0.1.zip"],
      "digest": "sha512:41f91679c2077a9c252cfc29b8cbf7884f2e9f3c411cfe6c88d807d346178b76299bc5c3f446290b84139ca3a9f6f18c91511fc8ee90b47549b4a8c19cdb337d" ,
      "packedResource": True
    },

    "QUARKUS_REGISTRY_0.0.1" : {
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/quarkus-registry-0.0.1.zip"],
      "digest": "sha512:3a48ce0c3c3dd66dd24fb82abfa52e666a32e73979ececd8cc30d28c653b1583218f46a162456318c8c5462d6a3f1ed87504ca961ef9400cd50953de922a7314" ,
      "packedResource": True
    },

    "PETCLINIC_3.0.1": {
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/spring-petclinic-3.0.1.zip"],
      "digest": "sha512:37d50049530ac23342a0e5adcf1c2a72b64312685ce407bc9dac577835d0df5e0548e72676e8a3a7bc670cf603d6b0c6e450f1ce8592f1f17681b6ddea602cda",
      "packedResource": True,
    },

    "SHOPCART_0.3.9": {
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/shopcart-0.3.9.zip"],
      "digest": "sha512:8a6095426c14fab7146c2ffd368c38069321983b38b2e8c75ab4b900ccda7c86723854a61d20f9c7779a49b41fc0ddfd806988458cc76088a8ab2be5acb4df48",
      "packedResource": True,
    },

    "SPRING_HW_3.0.6": {
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/spring-hello-world-3.0.6.zip"],
      "digest": "sha512:15a7fd76e47af62b25f66be0b12020f068202ced3b5cb96e6aed8b23cd60a037595ebb1b843a2e123051c08a18b5348133bc419c2b1f94364d263c13b3268ee1",
      "packedResource": True,
    },

    "QUARKUS_HW_1.0.5": {
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/quarkus-hello-world-1.0.5.zip"],
      "digest": "sha512:682a5dd39c27717d1e96a0c55f531a308f97266cc5abf4a9b7ecf3094a18b4a71cfb9630cb74be29b0580794190e25f538b5587856fa44cd35c34e1cab6ddcff",
      "packedResource": True,
    },

    "MICRONAUT_HW_1.0.6": {
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/micronaut-hello-world-1.0.6.zip"],
      "digest": "sha512:62f5c22c7217f42ffb0e53fffdbdf7add7ad4b6828c59967a2cae29e94120d0f88699ce14aca1b0cc39150119532d7c20c8e8b60fe097524b471094512b2ce58",
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
    # https://github.com/smarr/are-we-fast-yet
    "AWFY_1.1" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/awfy/awfy-770c6649.jar"],
      "digest" : "sha512:ecd9beb26b845fe9635399ed58a4f1098ca842525d2f89b0f1cf1c3580c0c163a342edb9d9685f43503792d450955dc7822c5d07d627baf1814b0e10695a6fa5",
    },
    "SCALAFMT" : {
      "maven" : {
        "groupId" : "com.geirsson",
        "artifactId" : "scalafmt-core_2.12",
        "version" : "1.5.1",
      },
      "digest" : "sha512:5065016fcfe18135d9e555f5880d44b014f6b7c884266e93f61861f256a0f4bfa19c4b9511c378d746fe79fb007b09f5728e53f6e3472c3bbbc8840e76ff70c2",
      "dependencies" : [
        "SCALA_LIBRARY_12",
        "SCALA_REFLECT_12",
        "METACONFIG_CORE",
        "SCALA_META",
        "INPUTS",
        "COMMON",
        "SOURCECODE",
        "DIALECTS",
        "PARSERS",
        "TOKENS",
        "TOKENIZERS",
        "QUASIQUOTES",
        "INLINE",
        "IO",
        "SEMANTIC",
        "TRANSVERSERS",
        "SCALAPARSE",
        "TREES",
        "FASTPARSE",
        "FASTPARSE_UTILS",
        "TYPESAFE_CONFIG",
        "METACONFIG_TYPESAFE_CONFIG",
      ],
    },
    "SCALA_LIBRARY_12" : {
      "maven" : {
        "groupId" : "org.scala-lang",
        "artifactId" : "scala-library",
        "version" : "2.12.2",
      },
      "digest" : "sha512:10bbf761be86b46dbd8596225cb3c9d7afe08b49b45b62f172319fd0c5b39d7c6bd518bdc24786c2d35000d19ff5d1e95f9bc7d6d73d3c18bb7227181d6288c5",
    },
    "SCALA_REFLECT_12" : {
      "maven" : {
        "groupId" : "org.scala-lang",
        "artifactId" : "scala-reflect",
        "version" : "2.12.2",
      },
      "digest" : "sha512:de67298d663cc3ffb062f8bba40585040a15d62ab5360ce114c612a9972393639c63ad0e61ef77f4125eaee53bda94cbd7528db3340b365124e122deb8b8115a",
    },
    "METACONFIG_CORE" : {
      "maven" : {
        "groupId" : "com.geirsson",
        "artifactId" : "metaconfig-core_2.12",
        "version" : "0.4.0",
      },
      "digest" : "sha512:75b1e6896c8a85f155b3505338c57ca05181b9517583e57696e6db8cbc416a5fcbec8c9a1646475b758d5c79eb3962ee9697d7ab2e780fe5a48abd444cc1633b",
    },
    "METACONFIG_TYPESAFE_CONFIG" : {
      "maven" : {
        "groupId" : "com.geirsson",
        "artifactId" : "metaconfig-typesafe-config_2.12",
        "version" : "0.4.0",
      },
      "digest" : "sha512:1c2ec751d7fbf5437d6c0d934e0b248ca193213a2d8e668e1a84eac06d145ee406854fc8bc7a833952b778412ee984990eac4bdcdeef629ec88f606e857e198c",
    },
    "SCALA_META" : {
      "maven" : {
        "groupId" : "org.scalameta",
        "artifactId" : "scalameta_2.12",
        "version" : "1.7.0",
      },
      "digest" : "sha512:0ae40e8e1741f67d30267a9f494e283c3bcf25a692b8dbf62b2233bb072d0e20424bb47c4a6a58fb5bca03eab7f38b3aabafd8365b5ccded9e8c7f4bfcf50d05",
      "dependencies" : [
        "SCALA_LIBRARY_12",
      ],
    },
    "INPUTS" : {
      "maven" : {
        "groupId" : "org.scalameta",
        "artifactId" : "inputs_2.12",
        "version" : "1.7.0",
      },
      "digest" : "sha512:f855d220fccf4ce09bac4d227f1b6d204dbd7018c380a791de27b8c2bbf4ded1b0197e85e72fa20c821a40f0130190e306be3f3d785724bcaab8facc3b1adc7c",
    },
    "COMMON" : {
      "maven" : {
        "groupId" : "org.scalameta",
        "artifactId" : "common_2.12",
        "version" : "1.7.0",
      },
      "digest" : "sha512:de7939cede61a457cf1b02cb67b0495867c6ca70bbd8a833d5dcf77e6cdb4fb3f39a69f8f73ee89563908d6665d0b71ba10abef6983e4a54d5e1b48fadad751a",
    },
    "SOURCECODE" : {
      "maven" : {
        "groupId" : "com.lihaoyi",
        "artifactId" : "sourcecode_2.12",
        "version" : "0.1.3",
      },
      "digest" : "sha512:3367cdaace100249e9a526efa9171dc2d322dafadad8e2b15a11beb0a4a89a25aa75752110250fe7db3155681a75012a2c8c7ca5d9cf346fefd548d0aa610a97",
    },
    "SCALAPARSE" : {
      "maven" : {
        "groupId" : "com.lihaoyi",
        "artifactId" : "scalaparse_2.12",
        "version" : "0.4.2",
      },
      "digest" : "sha512:85eecc7982ff6fb0cd262cee5ef6fcdd4ba1b18dbc5a9ed6b34ed2ba09bfbdc8a0e915bf9ded2e97ce6a404c413815d0e0bb63c9da8bff751ce4dee4d338bcac",
    },
    "DIALECTS" : {
      "maven" : {
        "groupId" : "org.scalameta",
        "artifactId" : "dialects_2.12",
        "version" : "1.7.0",
      },
      "digest" : "sha512:cd70d3b2447ce1cc9b937a111af8d67d7c019f04eb895ee022b831c190ef2416c1e53d7165722f225d353392d74de8f99ffb05a40072d40604c0fbf6bbad3dc6",
    },
    "TREES" : {
      "maven" : {
        "groupId" : "org.scalameta",
        "artifactId" : "trees_2.12",
        "version" : "1.7.0",
      },
      "digest" : "sha512:4ff5742854bfbfd02a7f45c7f1579846f3bd29a2c3943e1655ea8e671e31c39bf7304f4452599bf45fa8d067bf5844268f7fa3d30fd53d481e9f8df323561ea2",
    },
    "PARSERS" : {
      "maven" : {
        "groupId" : "org.scalameta",
        "artifactId" : "parsers_2.12",
        "version" : "1.7.0",
      },
      "digest" : "sha512:7d2cd8729281dd7f92d84b1aa89cf49f02ccadb81c77eaee17b3b7d1fb1fce9ed635c99d0eb5aa2894a6e853ea06a2050de481eee391f1c53a2bff530c44a7a8",
    },
    "TOKENIZERS" : {
      "maven" : {
        "groupId" : "org.scalameta",
        "artifactId" : "tokenizers_2.12",
        "version" : "1.7.0",
      },
      "digest" : "sha512:d26ef6df5a38d7cab6bc82dc680fa2b10d1efe89c59cf21d52cd9b007b5ec061e91a221e69b4a554646afb16ccf1804865fed2074a8a4e5db7b74a2690b61bf4",
    },
    "TOKENS" : {
      "maven" : {
        "groupId" : "org.scalameta",
        "artifactId" : "tokens_2.12",
        "version" : "1.7.0",
      },
      "digest" : "sha512:9bff1838a6dfedd05ab48c0ab4d40f89a8f22cfa51f9bce200ac192f7a66b2fa29e297eb1ce931f991eb8e50f047bff37ad15c368c197e85a7e04b77711ae33f",
    },
    "QUASIQUOTES" : {
      "maven" : {
        "groupId" : "org.scalameta",
        "artifactId" : "quasiquotes_2.12",
        "version" : "1.7.0",
      },
      "digest" : "sha512:495c0e451f7d383d1e72f6b229c5bea91979e31d3e9471f32c29d60a1260d38605e9c0904c61416d41f4462b6b19a985db36521ee5b33c306c28c56d160300c1",
    },
    "INLINE" : {
      "maven" : {
        "groupId" : "org.scalameta",
        "artifactId" : "inline_2.12",
        "version" : "1.7.0",
      },
      "digest" : "sha512:bc294face9cd8f2edcb8714c10c4a03e239b1c8550a437fdf43b74c18372eec5b316dcdcb5b68dff6a67d02dbec2fadcba3b5f3c504d78c71e5e251c4f82185e",
    },
    "IO" : {
      "maven" : {
        "groupId" : "org.scalameta",
        "artifactId" : "io_2.12",
        "version" : "1.7.0",
      },
      "digest" : "sha512:fdfb1b04852cfc15e44a18873df267b1c6484cee708fc9a162d1bee151fbdd10b5bc9d0b657abace3edba24c64b954742762029b92536676f042901b643231be",
    },
    "SEMANTIC" : {
      "maven" : {
        "groupId" : "org.scalameta",
        "artifactId" : "semantic_2.12",
        "version" : "1.7.0",
      },
      "digest" : "sha512:a74b58a430bfcf1158a82a9490b738ad3699bde12b79e7c170b9f8b1d53778b68bf5c27fc868d23eec60786877cf5c2b20c3f521eb2c2cf25b926dbff4e09133",
    },
    "TRANSVERSERS" : {
      "maven" : {
        "groupId" : "org.scalameta",
        "artifactId" : "transversers_2.12",
        "version" : "1.7.0",
      },
      "digest" : "sha512:4dd7739b94c62b29a4e76fe520684dc5078be35189ac2476ed6185e9aeefc8d986246629ccb82a4053aa9213a18df6aa3309acc38335a02cfcdcb74c54fa60cb",
    },
    "FASTPARSE" : {
      "maven" : {
        "groupId" : "com.lihaoyi",
        "artifactId" : "fastparse_2.12",
        "version" : "0.4.2",
      },
      "digest" : "sha512:10a63bde933dd15599d4fb3d8c8417b1708375675526fc652333a44506c21493f01e7b2d31cc9d1b072776e7acb0ce12f274d1a9509d049ffdf2175dd06cea9c",
    },
    "FASTPARSE_UTILS" : {
      "maven" : {
        "groupId" : "com.lihaoyi",
        "artifactId" : "fastparse-utils_2.12",
        "version" : "0.4.2",
      },
      "digest" : "sha512:2627fd1116b9ce2c4605a8822104ed21d4cd5bacf89110fc01eb77ed75cbb62d45c03915ef5f7758878903a5c8c6a4afd9bad686ef7a6ee667a760af9604abee",
    },
    "TYPESAFE_CONFIG" : {
      "maven" : {
        "groupId" : "com.typesafe",
        "artifactId" : "config",
        "version" : "1.2.1",
      },
      "digest" : "sha512:5ce11c5bdb14b6f4978940bd1728dd04021699fd1e234cd6c0c136866b99b691d6bf17a4a1996b0e94b91d196bbb18696807126bfee73c57792612e2e4f1c8ee",
    },
  },

  "projects" : {
    "org.graalvm.bench.misc" : {
      "subDir" : "java",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JMH_1_21",
      ],
      "javaCompliance" : "17+",
      "checkPackagePrefix" : "false",
      "annotationProcessors" : ["mx:JMH_1_21"],
      "spotbugsIgnoresGenerated" : True,
      "workingSets" : "Graal,Bench",
      "testProject" : True,
    },
    "org.graalvm.bench.shootouts" : {
      "subDir" : "java",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JMH_1_21",
      ],
      "javaCompliance" : "17+",
      "checkPackagePrefix" : "false",
      "annotationProcessors" : ["mx:JMH_1_21"],
      "spotbugsIgnoresGenerated" : True,
      "workingSets" : "Graal,Bench",
      "testProject" : True,
    },
    "org.graalvm.bench.console" : {
      "subDir" : "java",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "SCALAFMT"
      ],
      "javaCompliance" : "17+",
      "checkPackagePrefix" : "false",
      "workingSets" : "Graal,Bench",
      "testProject" : True,
    },
  },

  "imports" : {
    "suites": [
      {
        "name" : "sdk",
        "subdir": True
      }
    ]
  },

  "distributions" : {
    "GRAAL_BENCH_MISC" : {
      "subDir" : "java",
      "dependencies" : ["org.graalvm.bench.misc"],
      "testDistribution" : True,
      "maven": False,
    },
    "GRAAL_BENCH_SHOOTOUT" : {
      "subDir" : "java",
      "dependencies" : ["org.graalvm.bench.shootouts"],
      "testDistribution" : True,
      "maven": False,
    },
    "GRAAL_BENCH_CONSOLE" : {
      "subDir" : "java",
      "dependencies" : ["org.graalvm.bench.console"],
      "testDistribution" : True,
      "maven": False,
    }
  }
}
