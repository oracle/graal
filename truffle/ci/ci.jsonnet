{
  local common = import '../../ci/ci_common/common.jsonnet',
  local bench_hw = (import '../../ci/ci_common/bench-common.libsonnet').bench_hw,
  local utils = import "../../ci/ci_common/common-utils.libsonnet",
  local top_level_ci = utils.top_level_ci,
  local devkits = common.devkits,

  local darwin_amd64 = common.darwin_amd64,
  local darwin_aarch64 = common.darwin_aarch64,
  local linux_amd64 = common.linux_amd64,
  local windows_amd64 = common.windows_amd64,

  local truffle_common = {
    setup+: [
      ["cd", "./truffle"],
    ],
    targets: ["gate"],
    timelimit: "30:00",
  },

  local guard = {
    guard: {
      includes: ["<graal>/sdk/**", "<graal>/truffle/**", "**.jsonnet"] + top_level_ci,
    }
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

  local gate_lite = truffle_common + {
    name: 'gate-truffle-lite-oraclejdk-' + self.jdk_name + '-' + self.os + '-' + self.arch,
    run: [
      ["mx", "build"],
      ["mx", "unittest", "--verbose"],
    ],
  },

  local sigtest = truffle_common + {
    name: 'gate-truffle-sigtest-' + self.jdk_name,
    run: [
      ["mx", "build"],
      ["mx", "sigtest", "--check", (if self.jdk_version == 21 then "all" else "bin")],
    ],
  },

  local simple_tool_maven_project_gate = truffle_common + {
    name: 'gate-external-mvn-simpletool-' + self.jdk_name,
    packages+: {
      maven: "==3.3.9"
    },
    mx_cmd: ["mx", "-p", "../vm", "--env", "ce", "--dynamicimports", "/graal-js", "--native-images=none"],
    run+: [
      ["set-export", "ROOT_DIR", ["pwd"]],
      self.mx_cmd + ["build"],
      ["mkdir", "mxbuild/tmp_mvn_repo"],
      self.mx_cmd + ["maven-deploy", "--tags=public", "--all-suites", "--all-distribution-types", "--validate=full", "--licenses=EPL-2.0,PSF-License,GPLv2-CPE,ICU,GPLv2,BSD-simplified,BSD-new,UPL,MIT", "--version-string", self.mx_cmd + ["graalvm-version"], "--suppress-javadoc", "local", "file://$ROOT_DIR/mxbuild/tmp_mvn_repo"],
      ["set-export", "JAVA_HOME", self.mx_cmd + ["--quiet", "--no-warning", "graalvm-home"]],
      ["cd", "external_repos/"],
      ["python", "populate.py"],
      ["cd", "simpletool"],
      ["mvn", "-Dmaven.repo.local=$ROOT_DIR/mxbuild/tmp_mvn_repo", "package"],
      ["./simpletool", "example.js"],
    ],
  },

  local simple_language_maven_project_gate = truffle_common + {
    name: 'gate-external-mvn-simplelanguage-' + self.jdk_name,
    packages+: {
      maven: "==3.3.9",
      ruby: ">=2.1.0",
    },
    mx_cmd: ["mx", "-p", "../vm", "--env", "ce", "--native-images=none"],
    run+: [
      ["set-export", "ROOT_DIR", ["pwd"]],
      self.mx_cmd + ["build"],
      ["mkdir", "mxbuild/tmp_mvn_repo"],
      self.mx_cmd + ["maven-deploy", "--tags=public", "--all-suites", "--all-distribution-types", "--validate=full", "--licenses=EPL-2.0,PSF-License,GPLv2-CPE,ICU,GPLv2,BSD-simplified,BSD-new,UPL,MIT", "--version-string", self.mx_cmd + ["graalvm-version"], "--suppress-javadoc", "local", "file://$ROOT_DIR/mxbuild/tmp_mvn_repo"],
      ["set-export", "JAVA_HOME", self.mx_cmd + ["--quiet", "--no-warning", "graalvm-home"]],
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

  local truffle_gate = truffle_common + common.deps.eclipse + common.deps.jdt + common.deps.spotbugs {
    name: 'gate-truffle-oraclejdk-' + self.jdk_name,
    run: [["mx", "--strict-compliance", "gate", "--strict-mode"]],
  },

  local truffle_weekly = common.weekly + {notify_groups:: ["truffle"]},

  local _builds = std.flattenArrays([
      [
        linux_amd64  + jdk + sigtest + guard,
        darwin_amd64 + jdk + truffle_weekly + gate_lite + guard,
        darwin_aarch64 + jdk + truffle_weekly + gate_lite + guard,
      ] for jdk in [common.oraclejdk21, common.oraclejdkLatest]
    ]) +
  [
    # The simple_language_maven_project_gate uses native-image, so we must run on labsjdk rather than oraclejdk
    linux_amd64  + common.labsjdk21 + simple_language_maven_project_gate,
    linux_amd64  + common.labsjdkLatest + simple_language_maven_project_gate,
    # The simple_tool_maven_project_gate builds compiler, so we must run on labsjdk rather than oraclejdk because of compiler module rename
    linux_amd64  + common.labsjdk21 + simple_tool_maven_project_gate,
    linux_amd64  + common.labsjdkLatest + simple_tool_maven_project_gate,

    linux_amd64 + common.oraclejdk21 + truffle_gate + guard + {timelimit: "45:00"},
    linux_amd64 + common.oraclejdkLatest + truffle_gate + guard + {environment+: {DISABLE_DSL_STATE_BITS_TESTS: "true"}},

    truffle_common + linux_amd64 + common.oraclejdk17 + guard {
      name: "gate-truffle-javadoc",
      run: [
        ["mx", "build"],
        ["mx", "javadoc"],
      ],
    },

    truffle_common + linux_amd64 + common.oraclejdk21 + guard {
      name: "gate-truffle-slow-path-unittests",
      run: [
        ["mx", "build", "-n", "-c", "-A-Atruffle.dsl.GenerateSlowPathOnly=true"],
        # only those tests exercise meaningfully implemented nodes
        # e.g. com.oracle.truffle.api.dsl.test uses nodes that intentionally produce
        # different results from fast/slow path specializations to test their activation
        ["mx", "unittest", "com.oracle.truffle.api.test.polyglot", "com.oracle.truffle.nfi.test"],
      ],
    },

    # TODO Run full gate on Windows GR-51441
    windows_amd64 + gate_lite + common.oraclejdk21 + devkits["windows-jdk21"] + guard,
    windows_amd64 + gate_lite + common.oraclejdkLatest + devkits["windows-jdkLatest"] + guard,

    truffle_common + linux_amd64 + common.oraclejdk21 + common.deps.eclipse + common.deps.jdt + guard + {
      name: "weekly-truffle-coverage-21-linux-amd64",
      run: [
        ["mx", "--strict-compliance", "gate", "--strict-mode", "--jacoco-relativize-paths", "--jacoco-omit-src-gen", "--jacocout", "coverage", "--jacoco-format", "lcov"],
      ],
      teardown+: [
        ["mx", "sversions", "--print-repositories", "--json", "|", "coverage-uploader.py", "--associated-repos", "-"],
      ],
      targets: ["weekly"],
      notify_groups:: ["truffle"],
      timelimit: "45:00",
    },

    # BENCHMARKS

    bench_hw.x52 + common.labsjdkLatestCE + bench_common + {
      name: "bench-truffle-jmh",
      notify_groups:: ["truffle_bench"],
      run: [
        ["mx", "--kill-with-sigquit", "benchmark", "--results-file", "${BENCH_RESULTS_FILE_PATH}", "truffle:*", "--", "--", "com.oracle.truffle"],
      ],
      targets+: ["weekly"],
      timelimit: "3:00:00",
      teardown: [
        ["bench-uploader.py", "${BENCH_RESULTS_FILE_PATH}"],
      ],
    },

    linux_amd64 + common.labsjdkLatestCE + bench_common + {
      name: "gate-truffle-test-benchmarks",
      run: [
        ["mx", "benchmark", "truffle:*", "--", "--jvm", "server", "--jvm-config", "graal-core", "--", "com.oracle.truffle", "-f", "1", "-wi", "1", "-w", "1", "-i", "1", "-r", "1"],
      ],
      targets: ["gate"],
    },
  ],

  builds: utils.add_defined_in(_builds, std.thisFile),
}
