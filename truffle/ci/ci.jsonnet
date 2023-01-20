{
  local common_json = import '../../common.json',
  local common = import '../../ci/ci_common/common.jsonnet',
  local bench_hw = (import '../../ci/ci_common/bench-common.libsonnet').bench_hw,
  local composable = (import '../../ci/ci_common/common-utils.libsonnet').composable,
  local top_level_ci = (import '../../ci/ci_common/common-utils.libsonnet').top_level_ci,
  local devkits = composable(common_json.devkits),

  local darwin_amd64 = common.darwin_amd64,
  local linux_amd64 = common.linux_amd64,
  local windows_amd64 = common.windows_amd64,

  local truffle_common = composable(common_json.deps.common) + common.mx + {
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

  local bench_common = composable(common_json.deps.common) + common.mx + {
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
    name: 'gate-truffle-mac-lite-oraclejdk-' + self.jdk_version,
    run: [
      ["mx", "build"],
      ["mx", "unittest", "--verbose"],
    ],
  },

  local sigtest = truffle_common + {
    name: 'gate-truffle-sigtest-' + self.jdk_version,
    run: [
      ["mx", "build"],
      ["mx", "sigtest", "--check", (if self.jdk_version == 17 then "all" else "bin")],
    ],
  },

  local simple_tool_maven_project_gate = truffle_common + {
    name: 'gate-external-mvn-simpletool-' + self.jdk_version,
    packages+: {
      maven: "==3.3.9"
    },
    mx_cmd: ["mx", "-p", "../vm", "--dynamicimports", "/graal-js"],
    run+: [
      ["set-export", "ROOT_DIR", ["pwd"]],
      self.mx_cmd + ["build"],
      ["mkdir", "tmp_mvn_repo"],
      ["mx", "maven-install", "--repo", "tmp_mvn_repo", "--version-string", self.mx_cmd + ["graalvm-version"]],
      ["set-export", "JAVA_HOME", self.mx_cmd + ["--quiet", "--no-warning", "graalvm-home"]],
      ["cd", "external_repos/"],
      ["python", "populate.py"],
      ["cd", "simpletool"],
      ["mvn", "-Dmaven.repo.local=$ROOT_DIR/tmp_mvn_repo", "package"],
      ["./simpletool", "js", "example.js"],
    ],
  },

  local simple_language_maven_project_gate = truffle_common + {
    name: 'gate-external-mvn-simplelanguage-' + self.jdk_version,
    packages+: {
      maven: "==3.3.9",
      "00:devtoolset": "==7", # GCC 7.3.1, make 4.2.1, binutils 2.28, valgrind 3.13.0
      "01:binutils": ">=2.34",
      ruby: ">=2.1.0",
    },
    mx_cmd: ["mx", "-p", "../vm", "--dynamicimports", "/substratevm", "--native-images=none"],
    run+: [
      ["set-export", "ROOT_DIR", ["pwd"]],
      self.mx_cmd + ["build"],
      ["mkdir", "tmp_mvn_repo"],
      ["mx", "maven-install", "--all-suites", "--repo", "tmp_mvn_repo", "--version-string", self.mx_cmd + ["graalvm-version"]],
      ["set-export", "JAVA_HOME", self.mx_cmd + ["--quiet", "--no-warning", "graalvm-home"]],
      ["cd", "external_repos"],
      ["python", "populate.py"],
      ["cd", "simplelanguage"],
      ["mvn", "-Dmaven.repo.local=$ROOT_DIR/tmp_mvn_repo", "package"],
      ["./sl", "language/tests/Add.sl"],
      ["./sl", "-dump", "language/tests/Add.sl"],
      ["./sl", "-disassemble", "language/tests/Add.sl"],
      ["./sl", "language/tests/Add.sl"],
      ["./native/slnative", "language/tests/Add.sl"],
      ["$JAVA_HOME/bin/gu", "install", "-L", "component/sl-component.jar"],
      ["$JAVA_HOME/bin/sl", "language/tests/Add.sl"],
      ["$JAVA_HOME/bin/slnative", "language/tests/Add.sl"],
      ["$JAVA_HOME/bin/polyglot", "--jvm", "--language", "sl", "--file", "language/tests/Add.sl"],
      ["$JAVA_HOME/bin/gu", "remove", "sl"],
    ],
  },

  local truffle_gate = truffle_common + common.eclipse + common.jdt {
    name: 'gate-truffle-oraclejdk-' + self.jdk_version,
    run: [["mx", "--strict-compliance", "gate", "--strict-mode"]],
  },

  local truffle_weekly = common.weekly + {notify_groups:: ["truffle"]},

  builds: [b for b in std.flattenArrays([
      [
        linux_amd64  + jdk + sigtest + guard,
        linux_amd64  + jdk + simple_tool_maven_project_gate + common.mach5_target,
        linux_amd64  + jdk + simple_language_maven_project_gate,
        darwin_amd64 + jdk + truffle_weekly + gate_lite + guard,
      ] for jdk in [common.oraclejdk20, common.oraclejdk17]
    ]) if b.name != "gate-external-mvn-simplelanguage-20" /* GR-42727	*/] +
  [
    linux_amd64 + common.oraclejdk17 + truffle_gate + guard + {timelimit: "45:00"},
    linux_amd64 + common.oraclejdk20 + truffle_gate + guard + {environment+: {DISABLE_DSL_STATE_BITS_TESTS: "true"}},

    truffle_common + linux_amd64 + common.oraclejdk17 + guard {
      name: "gate-truffle-javadoc",
      run: [
        ["mx", "build"],
        ["mx", "javadoc"],
      ],
    },

    truffle_common + linux_amd64 + common.oraclejdk17 + guard {
      name: "gate-truffle-slow-path-unittests",
      run: [
        ["mx", "build", "-n", "-c", "-A-Atruffle.dsl.GenerateSlowPathOnly=true"],
        # only those tests exercise meaningfully implemented nodes
        # e.g. com.oracle.truffle.api.dsl.test uses nodes that intentionally produce
        # different results from fast/slow path specializations to test their activation
        ["mx", "unittest", "com.oracle.truffle.api.test.polyglot", "com.oracle.truffle.nfi.test"],
      ],
    },

    truffle_common + windows_amd64 + common.oraclejdk17 + devkits["windows-jdk17"] + guard {
      name: "gate-truffle-nfi-windows-17",
      # TODO make that a full gate run
      # currently, some truffle unittests fail on windows
      run: [
        ["mx", "build" ],
        ["mx", "unittest", "--verbose" ],
      ],
    },

    truffle_common + linux_amd64 + common.oraclejdk17 + common.eclipse + common.jdt + guard + {
      name: "weekly-truffle-coverage-17-linux-amd64",
      run: [
        ["mx", "--strict-compliance", "gate", "--strict-mode", "--jacoco-relativize-paths", "--jacoco-omit-src-gen", "--jacocout", "coverage", "--jacoco-format", "lcov"],
      ],
      teardown+: [
        ["mx", "sversions", "--print-repositories", "--json", "|", "coverage-uploader.py", "--associated-repos", "-"],
      ],
      targets: ["weekly"],
      notify_groups:: ["truffle"],
    },

    # BENCHMARKS

    bench_hw.x52 + common.oraclejdk17 + bench_common + {
      name: "bench-truffle-jmh-17",
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

    linux_amd64 + common.oraclejdk17 + bench_common + {
      name: "gate-truffle-test-benchmarks-17",
      run: [
        ["mx", "benchmark", "truffle:*", "--", "--jvm", "server", "--jvm-config", "graal-core", "--", "com.oracle.truffle", "-f", "1", "-wi", "1", "-w", "1", "-i", "1", "-r", "1"],
      ],
      targets: ["gate"],
    },
  ]
}
