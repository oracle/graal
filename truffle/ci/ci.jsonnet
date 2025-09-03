{
  local common = import '../../ci/ci_common/common.jsonnet',
  local bench_hw = (import '../../ci/ci_common/bench-common.libsonnet').bench_hw,
  local utils = import "../../ci/ci_common/common-utils.libsonnet",
  local top_level_ci = utils.top_level_ci,
  local devkits = common.devkits,

  local darwin_amd64 = common.darwin_amd64,
  local darwin_aarch64 = common.darwin_aarch64,
  local linux_amd64 = common.linux_amd64,
  local linux_aarch64 = common.linux_aarch64,
  local windows_amd64 = common.windows_amd64,

  local winDevKit(jdk) =
  devkits[
    if jdk.jdk_version == 21
    then "windows-jdk21"
    else "windows-jdkLatest"
  ],

  local truffle_common = {
    setup+: [
      ["cd", "./truffle"],
    ],
    notify_groups:: ["truffle"],
  },

  local bench_common = {
    environment+: {
      BENCH_RESULTS_FILE_PATH: "bench-results.json",
    },
    setup: [
      ["cd", "./compiler"],
      ["mx", "build" ],
      ["mx", "hsdis", "||", "true"],
    ]
  },

  local simple_tool_maven_project_gate = truffle_common + {
    name: self.name_prefix + 'truffle-simpletool-maven-' + self.jdk_name + '-' + self.os + '-' + self.arch,
    packages+: {
      maven: "==3.3.9"
    },
    mx_cmd: ["mx", "-p", "../vm", "--dynamicimports", "/graal-js"],
    run+: [
      ["set-export", "ROOT_DIR", ["pwd"]],
      self.mx_cmd + ["build"],
      ["mkdir", "mxbuild/tmp_mvn_repo"],
      self.mx_cmd + ["maven-deploy", "--tags=public", "--all-suites", "--all-distribution-types", "--validate=full", "--licenses=EPL-2.0,PSF-License,GPLv2-CPE,ICU,GPLv2,BSD-simplified,BSD-new,UPL,MIT", "--version-string", self.mx_cmd + ["graalvm-version"], "--suppress-javadoc", "local", "file://$ROOT_DIR/mxbuild/tmp_mvn_repo"],
      ["cd", "external_repos/"],
      ["python", "populate.py"],
      ["cd", "simpletool"],
      ["mvn", "-Dmaven.repo.local=$ROOT_DIR/mxbuild/tmp_mvn_repo", "package"],
      ["./simpletool", "example.js"],
    ],
  },

  local simple_language_maven_project_gate = truffle_common + {
    name: self.name_prefix + 'truffle-simplelanguage-maven-' + self.jdk_name + '-' + self.os + '-' + self.arch,
    packages+: {
      maven: "==3.3.9",
      ruby: "==3.0.2",
    },
    mx_cmd: ["mx"],
    run+: [
      ["set-export", "ROOT_DIR", ["pwd"]],
      self.mx_cmd + ["build"],
      ["mkdir", "mxbuild/tmp_mvn_repo"],
      self.mx_cmd + ["maven-deploy", "--tags=public", "--all-suites", "--all-distribution-types", "--validate=full", "--licenses=EPL-2.0,PSF-License,GPLv2-CPE,ICU,GPLv2,BSD-simplified,BSD-new,UPL,MIT", "--version-string", self.mx_cmd + ["graalvm-version"], "--suppress-javadoc", "local", "file://$ROOT_DIR/mxbuild/tmp_mvn_repo"],
      ["cd", "external_repos"],
      ["python", "populate.py"],
      ["cd", "simplelanguage"],
      ["mvn", "-Dmaven.repo.local=$ROOT_DIR/mxbuild/tmp_mvn_repo", "package", "-Pnative"],
      ["./sl", "language/tests/Add.sl"],
      ["./sl", "-dump", "language/tests/SumCall.sl"],
      ["./sl", "-disassemble", "language/tests/SumCall.sl"],
      ["./sl", "language/tests/SumCall.sl"],
      ["./standalone/target/slnative", "language/tests/SumCall.sl"],
    ],
  },

  local truffle_common_gate = truffle_common + common.deps.eclipse + common.deps.jdt + common.deps.spotbugs  {
    components+: ["truffle"],
  },

  # this is only valid for gates that depend only on truffle files
  # do not use for latest GraalVM builds
  local guard = {
    guard+: {
      includes+: ["<graal>/sdk/**", "<graal>/truffle/**", "**.jsonnet"] + top_level_ci,
    }
  },

  local truffle_style_gate = truffle_common_gate  + guard + {
    name: self.name_prefix + 'truffle-style-' + self.truffle_jdk_name + '-' + self.os + '-' + self.arch,
    run+: [
      ["mx", "--strict-compliance", "gate", "--strict-mode", "--tag", "style,fullbuild,sigtest"],
    ],
  },

  local truffle_test_full_gate = truffle_common_gate + guard + {
    name: self.name_prefix + 'truffle-full-test-' + self.truffle_jdk_name + '-' + self.os + '-' + self.arch,
    run+: [
      ["mx", "--strict-compliance", "gate", "--strict-mode", "--tag", "style,fullbuild,fulltest"]
    ],
  },
  local truffle_test_lite_gate= truffle_common_gate + guard + {
    name: self.name_prefix + 'truffle-lite-test-' + self.truffle_jdk_name + '-' + self.os + '-' + self.arch,
    run+: [
      ["mx", "--strict-compliance", "gate", "--strict-mode", "--tag", "build,test"],
    ],
  },

  local truffle_test_super_lite_gate = truffle_common_gate + guard + {
    name: self.name_prefix + 'truffle-super-lite-test-' + self.truffle_jdk_name + '-' + self.os + '-' + self.arch,
    run+: [
      ["mx", "build"],
      ["mx", "unittest", "--verbose"],
    ],
  },

  local truffle_coverage = truffle_common + common.deps.eclipse + common.deps.jdt + guard + {
    name: self.name_prefix + 'truffle-coverage-' + self.truffle_jdk_name + '-' + self.os + '-' + self.arch,
    run+: [
      ["mx", "--strict-compliance", "gate", "--strict-mode", "--jacoco-relativize-paths", "--jacoco-omit-src-gen", "--jacocout", "coverage", "--jacoco-format", "lcov", "--tags", "build,fulltest"],
    ],
    teardown+: [
      ["mx", "sversions", "--print-repositories", "--json", "|", "coverage-uploader.py", "--associated-repos", "-"],
    ],
    timelimit: "45:00",
  },

  local jmh_benchmark = bench_common + {
    name: self.name_prefix + 'truffle-jmh-' + self.truffle_jdk_name + '-' + self.os + '-' + self.arch,
    notify_groups:: ["truffle_bench"],
    run+: [
      ["mx", "--kill-with-sigquit", "benchmark", "--results-file", "${BENCH_RESULTS_FILE_PATH}", "truffle:*", "--", "--", "org.graalvm.truffle.benchmark"],
    ],
    timelimit: "3:00:00",
    teardown: [
      ["bench-uploader.py", "${BENCH_RESULTS_FILE_PATH}"],
    ],
  },

  local jmh_benchmark_test = bench_common + guard + {
    name:  self.name_prefix + 'truffle-test-benchmarks-' + self.truffle_jdk_name + '-' + self.os + '-' + self.arch,
    run+: [
      ["mx", "benchmark", "truffle:*", "--", "--jvm", "server", "--jvm-config", "graal-core", "--", "org.graalvm.truffle.benchmark", "-f", "1", "-wi", "1", "-w", "1", "-i", "1", "-r", "1"],
    ],
  },

  local tier1  = common.tier1 + {
    name_prefix: "gate-",
    timelimit: "0:30:00"
  },
  local tier2  = common.tier2 + {
    name_prefix: "gate-",
    timelimit: "01:00:00"
  },
  local tier3  = common.tier3 + {
    name_prefix: "gate-",
    timelimit: "01:15:00"
  },
  local daily  = common.daily + {
    name_prefix: "daily-",
    timelimit: "04:00:00"
  },
  local weekly  = common.weekly + {
    name_prefix: "weekly-",
    timelimit: "04:00:00"
  },
  local bench  = common.weekly + {
    name_prefix: "bench-",
    timelimit: "04:00:00"
  },

  local jdk_21_oracle = common.oraclejdk21 + {truffle_jdk_name: "oraclejdk-21"},
  local jdk_latest_oracle = common.oraclejdkLatest + {truffle_jdk_name: "oraclejdk-latest"},
  local jdk_latest_labs = common.labsjdkLatestCE+ {truffle_jdk_name: "labsjdk-latest"},

  local jdk_latest_graalvm_ce = jdk_latest_labs + common.deps.svm + {
    truffle_jdk_name: "graalvm-ce-latest",
    mx_build_graalvm_cmd: ["mx", "-p", "../vm", "--env", "ce", "--native-images=lib:jvmcicompiler"],
    run+: [
        self.mx_build_graalvm_cmd + ["build", "--force-javac"],
        ["set-export", "JAVA_HOME", self.mx_build_graalvm_cmd + ["--quiet", "--no-warning", "graalvm-home"]]
    ]
  },

  local test_jdks = [jdk_latest_oracle, jdk_21_oracle],
  local graalvm_jdks = [jdk_latest_graalvm_ce],

  local forEach(arr, fn) = std.flattenArrays([fn(x) for x in arr]),

  local _builds = std.flattenArrays([

    # Regular Truffle gates
    [linux_amd64 + tier1 + jdk_latest_oracle + truffle_style_gate],

    forEach(test_jdks, function(jdk)
      [
        linux_amd64      + tier2  + jdk + truffle_test_lite_gate,
        linux_amd64      + tier3  + jdk + truffle_test_full_gate,

        linux_aarch64    + tier3  + jdk + truffle_test_lite_gate,
        darwin_aarch64   + tier3  + jdk + truffle_test_lite_gate,
        windows_amd64    + tier3  + jdk + truffle_test_lite_gate + winDevKit(jdk),

        # we do have very few resources for Darwin AMD64 so only run weekly
        darwin_amd64     + weekly + jdk + truffle_test_lite_gate,
      ]
    ),

    # SimpleLanguage Maven Integration Test
    forEach(graalvm_jdks, function(jdk)
      [
        linux_amd64      + tier3  + jdk + simple_language_maven_project_gate,

        linux_aarch64    + weekly + jdk + simple_language_maven_project_gate,
        darwin_amd64     + weekly + jdk + simple_language_maven_project_gate,
        darwin_aarch64   + weekly + jdk + simple_language_maven_project_gate,

        # GR-68277 currently unsupported
        # windows_amd64  + weekly + jdk + simple_language_maven_project_gate + winDevKit(jdk),
      ]
    ),

    # SimpleTool Maven Integration Test
    forEach(graalvm_jdks, function(jdk)
      [
        linux_amd64      + tier3  + jdk + simple_tool_maven_project_gate,

        linux_aarch64    + weekly + jdk + simple_tool_maven_project_gate,
        darwin_amd64     + weekly + jdk + simple_tool_maven_project_gate,
        darwin_aarch64   + weekly + jdk + simple_tool_maven_project_gate,

        # GR-68277 currently unsupported
        # windows_amd64  + weekly + jdk + simple_tool_maven_project_gate + winDevKit(jdk),
      ]
    ),

    # Truffle Coverage
    [linux_amd64 + weekly + jdk_21_oracle + truffle_coverage],

    # Truffle Benchmarks
    [linux_amd64  + tier3  + jdk_latest_labs + jmh_benchmark_test],
    [bench_hw.x52 + bench  + jdk_latest_labs + jmh_benchmark]
  ]),
  builds: utils.add_defined_in(_builds, std.thisFile),
}
