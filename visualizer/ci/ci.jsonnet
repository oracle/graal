{
  local common = import '../../ci/ci_common/common.jsonnet',
  local utils = import '../../ci/ci_common/common-utils.libsonnet',

  Gate:: {
    timelimit : "30:00",
    targets: [ "gate" ],
    run: [
      ["cd", "./visualizer"],
      ["mx", "pylint" ],
      ["mx", "verify-graal-graphio" ],
      ["mx", "build" ],
      ["mx", "clean" ],
      ["mx", "build-release" ],
      ["mx", "spotbugs" ],
      ["mx", "unittest" ],
      ["mx", "igv", "-J-Dnetbeans.close=true", "--nosplash"],
    ]
  },

  Integration:: {
    timelimit : "30:00",
    targets: [ "gate" ],
    downloads+: {
      "VISUALIZER_JAVA_HOME": common.jdks_data["oraclejdk11"]
    },
    run: [
      ["cd", "./visualizer"],
      ["mx", "--java-home=$VISUALIZER_JAVA_HOME", "build" ],
      ["cd", "../compiler"],
      ["mx", "build" ],
      ["mx", "benchmark", "dacapo:fop", "--", "-Djdk.graal.Dump=:1", "-Djdk.graal.PrintGraph=File", "-Djdk.graal.DumpPath=../IGV_Dumps"],
      ["cd", "../visualizer"],
      ["mx", "igv", "-J-Digv.openfile.onstartup.and.close=../compiler/IGV_Dumps", "--nosplash"],
    ]
  },

  local _builds = [
    common.linux_amd64 + common.oraclejdk11 + self.Gate + { name: "gate-visualizer-linux-amd64-oraclejdk-11" },
    common.linux_amd64 + common.labsjdkLatestCE + self.Integration + { name: "gate-visualizer-integration-linux-amd64-labsjdk-latest" },
  ],

  builds: utils.add_defined_in(_builds, std.thisFile),
}
