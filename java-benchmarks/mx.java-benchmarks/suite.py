suite = {
  "mxversion" : "5.265.6",
  "name": "java-benchmarks",

  "libraries" : {
    "DACAPO" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/dacapo-9.12-bach-patched.jar"],
      "sha1" : "e39957904b7e79caf4fa54f30e8e4ee74d4e9e37",
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
    "AWFY" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/awfy/awfy-770c6649.jar"],
      "sha1" : "f1bf1febd81ce7fbd83244682ddc79e74fec0076",
    },
  }
}
