{
  local common = import '../../ci/ci_common/common.jsonnet',
  local utils = import '../../ci/ci_common/common-utils.libsonnet',

  Gate:: {
    timelimit : "30:00",
    targets: [ "tier2" ],
    run: [
      ["cd", "./visualizer"],
      ["mx", "pylint" ],
      ["mx", "verify-graal-graphio" ],
      ["mx", "build" ],
      ["mx", "clean" ],
      ["mx", "unittest" ],
      ["mx", "igv", "-J-Dnetbeans.close=true", "--nosplash"],
    ]
  },

  Integration:: {
    timelimit : "30:00",
    targets: [ "tier2" ],
    # reset catch files to avoid capturing (non-existent) dump files, which can take very long
    catch_files: [],
    downloads+: {
      "TOOLS_JAVA_HOME": common.jdks_data["oraclejdk21"]
    },
    run: [
      ["cd", "./compiler"],
      ["mx", "build" ],
      ["mx", "benchmark", "dacapo:fop", "--", "-Djdk.graal.Dump=:1", "-Djdk.graal.PrintGraph=File", "-Djdk.graal.DumpPath=../IGV_Dumps"],
      ["cd", "../visualizer"],
      ["mx", "--java-home=$TOOLS_JAVA_HOME", "build" ],
      ["mx", "--java-home=$TOOLS_JAVA_HOME", "igv", "-J-Digv.openfile.onstartup.and.close=../compiler/IGV_Dumps", "--nosplash"],
    ]
  },

  linux_maven: {
    packages+: {
      maven: '==3.9.10',
    },
  },

  local _builds = [
    common.linux_amd64 + self.linux_maven + common.oraclejdk17 + self.Gate + { name: "gate-visualizer-linux-amd64-oraclejdk-17" },
    common.linux_amd64 + self.linux_maven + common.labsjdkLatestCE + self.Integration + { name: "gate-visualizer-integration-linux-amd64-labsjdk-latest" },
  ],

  builds: utils.add_defined_in(_builds, std.thisFile),
}
