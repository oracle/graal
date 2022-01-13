suite = {
  "mxversion" : "5.316.15",
  "name": "java-benchmarks",

  "javac.lint.overrides": "none",

  "libraries" : {
    "TIKA_1.0.6": {
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/tika-1.0.6.zip"],
      "sha1": "bf34a3b2ef72025125a4925b6327506076110537",
      "packedResource": True,
    },

    "PETCLINIC_0.1.6": {
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/petclinic-jpa-0.1.6.zip"],
      "sha1": "a27e41c3f4718b6d66ac0d39728f931dcfb2c613",
      "packedResource": True,
    },

    "SHOPCART_0.3.5": {
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/shopcart-0.3.5.zip"],
      "sha1": "da961b7b81c161fda51ac1939a983cbfc95a5b28",
      "packedResource": True,
    },

    "SPRING_HW_1.0.1": {
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/spring-hello-world-1.0.1.zip"],
      "sha1": "6c3b2d41dc0df793bd39150270b50e36578c27e0",
      "packedResource": True,
    },

    "QUARKUS_HW_1.0.1": {
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/quarkus-hello-world-1.0.1.zip"],
      "sha1": "3b647ae68654264745bb32d09422a0c3c45f850a",
      "packedResource": True,
    },

    "MICRONAUT_HW_1.0.2": {
      "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/micronaut-hello-world-1.0.2.zip"],
      "sha1": "daba8f34099dfd884b59fe73855e74d139efe18d",
      "packedResource": True,
    },

    "DACAPO" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/dacapo-9.12-bach-patched.jar"],
      "sha1" : "e39957904b7e79caf4fa54f30e8e4ee74d4e9e37",
    },

    "DACAPO_MR1_BACH" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/dacapo-9.12-MR1-bach.jar"],
      "sha1" : "9cf63ef9620032b47f1a4897fe910755596b371a",
    },

    "DACAPO_SCALA" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/dacapo-scala-0.1.0-20120216.jar"],
      "sha1" : "59b64c974662b5cf9dbd3cf9045d293853dd7a51",
    },

    "DACAPO_D3S" : {
      # original: https://d3s.mff.cuni.cz/software/benchmarking/files/dacapo-9.12-d3s.jar
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/dacapo-9.12-d3s.jar"],
      "sha1" : "b072de027141ac81ab5d48706949fda86de62468",
    },

    "RENAISSANCE_0.9.0" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/renaissance/renaissance-gpl-0.9.0.jar"],
      "sha1" : "925ca7d440078b0b30f5849695061262c226820e",
    },

    "RENAISSANCE_0.10.0" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/renaissance/renaissance-gpl-0.10.0.jar"],
      "sha1" : "5f58e281bb5aae161854b036c7e49e593a81186a",
    },

    "RENAISSANCE_0.11.0" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/renaissance/renaissance-gpl-0.11.0.jar"],
      "sha1" : "613f7615179ea364116cdd68aa41ad44a9cc49e4",
    },

    "RENAISSANCE_0.12.0" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/renaissance/renaissance-gpl-0.12.0.jar"],
      "sha1" : "5bf404f875622a714f9b5c772b52ad857b97658d",
    },

    "RENAISSANCE_0.13.0" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/renaissance/renaissance-gpl-0.13.0.jar"],
      "sha1" : "65eaca6ec6ba4c7293b82644bbdefd5cb2178825",
    },

    "UBENCH_AGENT_DIST" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/java-ubench-agent-2e5becaf97afcf64fd8aef3ac84fc05a3157bff5.zip"],
      "sha1" : "19087a34b80be8845e9a3e7f927ceb592de83762",
    },

    # Required to run SPECJBB2015 on JDK >=11
    "ACTIVATION_1.1.1" : {
      "sha1" : "485de3a253e23f645037828c07f1d7f1af40763a",
      "maven" : {
        "groupId" : "javax.activation",
        "artifactId" : "activation",
        "version" : "1.1.1",
      },
    },
    "JAXB_API_2.1" : {
      "sha1" : "d68570e722cffe2000358ce9c661a0b0bf1ebe11",
      "maven" : {
        "groupId" : "javax.xml.bind",
        "artifactId" : "jaxb-api",
        "version" : "2.1",
      },
    },
    "JAXB_IMPL_2.1.17" : {
      "sha1" : "26efa071c07deb2b80cd72b6567f1260a68a0da5",
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
      "sha1" : "f1bf1febd81ce7fbd83244682ddc79e74fec0076",
    },
    "SCALAFMT" : {
      "maven" : {
        "groupId" : "com.geirsson",
        "artifactId" : "scalafmt-core_2.12",
        "version" : "1.5.1",
      },
      "sha1" : "03c9c6ad4232101f130b4c8a804b6ab3af494b11",
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
      "sha1" : "fe9780170207ebdabd4ac5bb335c82bd0aead480",
    },
    "SCALA_REFLECT_12" : {
      "maven" : {
        "groupId" : "org.scala-lang",
        "artifactId" : "scala-reflect",
        "version" : "2.12.2",
      },
      "sha1" : "fa13c13351566738ff156ef8a56b869868f4b77e",
    },
    "METACONFIG_CORE" : {
      "maven" : {
        "groupId" : "com.geirsson",
        "artifactId" : "metaconfig-core_2.12",
        "version" : "0.4.0",
      },
      "sha1" : "d7d384877a1dac47797c093b463b2c2cb75b60e0",
    },
    "METACONFIG_TYPESAFE_CONFIG" : {
      "maven" : {
        "groupId" : "com.geirsson",
        "artifactId" : "metaconfig-typesafe-config_2.12",
        "version" : "0.4.0",
      },
      "sha1" : "7257969d24506df099b5a1fa9157fffa49435d00",
    },
    "SCALA_META" : {
      "maven" : {
        "groupId" : "org.scalameta",
        "artifactId" : "scalameta_2.12",
        "version" : "1.7.0",
      },
      "sha1" : "dfb33a0c8c549c3d3b992642a3df3e270816dad6",
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
      "sha1" : "c20a4a94841d53dd53c4eb1839f6826758291702",
    },
    "COMMON" : {
      "maven" : {
        "groupId" : "org.scalameta",
        "artifactId" : "common_2.12",
        "version" : "1.7.0",
      },
      "sha1" : "9d7ba225b6345974a98306178c7e030adb109f5d",
    },
    "SOURCECODE" : {
      "maven" : {
        "groupId" : "com.lihaoyi",
        "artifactId" : "sourcecode_2.12",
        "version" : "0.1.3",
      },
      "sha1" : "37504e0fab14f28cff977250e891a25c8bd6a762",
    },
    "SCALAPARSE" : {
      "maven" : {
        "groupId" : "com.lihaoyi",
        "artifactId" : "scalaparse_2.12",
        "version" : "0.4.2",
      },
      "sha1" : "80e70822eff9a0174e8f6621a7517f8153066073",
    },
    "DIALECTS" : {
      "maven" : {
        "groupId" : "org.scalameta",
        "artifactId" : "dialects_2.12",
        "version" : "1.7.0",
      },
      "sha1" : "f4981c13a754290c0e60eefdf5a44e1f5a1137d2",
    },
    "TREES" : {
      "maven" : {
        "groupId" : "org.scalameta",
        "artifactId" : "trees_2.12",
        "version" : "1.7.0",
      },
      "sha1" : "b3941b0398c1ca4073de73df96ee400590ee3e4a",
    },
    "PARSERS" : {
      "maven" : {
        "groupId" : "org.scalameta",
        "artifactId" : "parsers_2.12",
        "version" : "1.7.0",
      },
      "sha1" : "20352c0e076b6a8b9b8165dd13e942981dcceb03",
    },
    "TOKENIZERS" : {
      "maven" : {
        "groupId" : "org.scalameta",
        "artifactId" : "tokenizers_2.12",
        "version" : "1.7.0",
      },
      "sha1" : "0b718fef10a9453722827042b4afb894b69f81a1",
    },
    "TOKENS" : {
      "maven" : {
        "groupId" : "org.scalameta",
        "artifactId" : "tokens_2.12",
        "version" : "1.7.0",
      },
      "sha1" : "d4d906c124404d551a8cf4cb5dfb8a00a3f11fa8",
    },
    "QUASIQUOTES" : {
      "maven" : {
        "groupId" : "org.scalameta",
        "artifactId" : "quasiquotes_2.12",
        "version" : "1.7.0",
      },
      "sha1" : "ac3c9cee6a750c7f39dabe8ff705abecb02ec997",
    },
    "INLINE" : {
      "maven" : {
        "groupId" : "org.scalameta",
        "artifactId" : "inline_2.12",
        "version" : "1.7.0",
      },
      "sha1" : "571f990aa172f7f1a1fccce8521fb81ffe8321d2",
    },
    "IO" : {
      "maven" : {
        "groupId" : "org.scalameta",
        "artifactId" : "io_2.12",
        "version" : "1.7.0",
      },
      "sha1" : "c7a692840a0443cf25b3cdec0b18634163a8c3d7",
    },
    "SEMANTIC" : {
      "maven" : {
        "groupId" : "org.scalameta",
        "artifactId" : "semantic_2.12",
        "version" : "1.7.0",
      },
      "sha1" : "5304fa048b70bdba222c9b9401f6155be747518b",
    },
    "TRANSVERSERS" : {
      "maven" : {
        "groupId" : "org.scalameta",
        "artifactId" : "transversers_2.12",
        "version" : "1.7.0",
      },
      "sha1" : "cfd74b8b097f605093a2f104c72f8ff9850339d0",
    },
    "FASTPARSE" : {
      "maven" : {
        "groupId" : "com.lihaoyi",
        "artifactId" : "fastparse_2.12",
        "version" : "0.4.2",
      },
      "sha1" : "25d704ea5543084e3ec2676f22dbd39154a4324c",
    },
    "FASTPARSE_UTILS" : {
      "maven" : {
        "groupId" : "com.lihaoyi",
        "artifactId" : "fastparse-utils_2.12",
        "version" : "0.4.2",
      },
      "sha1" : "bb0d90c37e54e124dcb79440d33e3b32553463f6",
    },
    "TYPESAFE_CONFIG" : {
      "maven" : {
        "groupId" : "com.typesafe",
        "artifactId" : "config",
        "version" : "1.2.1",
      },
      "sha1" : "f771f71fdae3df231bcd54d5ca2d57f0bf93f467",
    },
  },

  "projects" : {
    "org.graalvm.bench.misc" : {
      "subDir" : "java",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JMH_1_21",
      ],
      "javaCompliance" : "8+",
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
      "javaCompliance" : "8+",
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
      "javaCompliance" : "8+",
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
