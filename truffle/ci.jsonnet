{
  local common = import '../common.jsonnet',
  local bench_hw = (import '../bench-common.libsonnet').bench_hw,
  local devkits = (import "../common.json").devkits,

  local darwin_amd64 = common.darwin_amd64,
  local linux_amd64 = common.linux_amd64,
  local windows_amd64 = common.windows_amd64,

  local truffle_common = {
    setup+: [
      ["cd", "./truffle"],
    ],
    targets: ["gate"],
    timelimit: "30:00",
  },

  local bench_common = {
    environment: {
      BENCH_RESULTS_FILE_PATH: "bench-results.json",
      MX_PYTHON: "python3",
    },
    setup: [
      ["cd", "./compiler"],
      ["mx", "build" ],
      ["mx", "hsdis", "||", "true"],
    ]
  },

  local truffle_unittest = {
    environment+: {
      "MX_TEST_RESULTS_PATTERN": "es-XXX.json",
      "MX_TEST_RESULT_TAGS": "truffle"
    }
  },

  local gate_lite = truffle_common + truffle_unittest + {
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
      ["mx", "sigtest", "--check", (if self.jdk_version == 8 then "all" else "bin")],
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
      devtoolset: "==7", # GCC 7.3.1, make 4.2.1, binutils 2.28, valgrind 3.13.0
      binutils: ">=2.34",
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

  local truffle_gate = truffle_common + common.eclipse + common.jdt + truffle_unittest {
    name: 'gate-truffle-oraclejdk-' + self.jdk_version,
    run: [["mx", "--strict-compliance", "gate", "--strict-mode"]],
  },

  local truffle_weekly = common.weekly + {notify_groups:: ["truffle"]},

  builds: std.flattenArrays([
      [
        linux_amd64  + jdk + sigtest,
        linux_amd64  + jdk + simple_tool_maven_project_gate,
        linux_amd64  + jdk + simple_language_maven_project_gate,
        darwin_amd64 + jdk + truffle_weekly + gate_lite,
      ] for jdk in [common.oraclejdk11, common.oraclejdk17]
    ]) + [
    linux_amd64 + common.oraclejdk8  + truffle_gate + {timelimit: "45:00"},
    linux_amd64 + common.oraclejdk11 + truffle_gate + {environment+: {DISABLE_DSL_STATE_BITS_TESTS: "true"}},
    linux_amd64 + common.oraclejdk17 + truffle_gate + {environment+: {DISABLE_DSL_STATE_BITS_TESTS: "true"}},

    linux_amd64 + common.oraclejdk11 + truffle_common + {
      name: "gate-truffle-javadoc",
      run: [
        ["mx", "build"],
        ["mx", "javadoc"],
      ],
    },

    linux_amd64 + common.oraclejdk11 + truffle_common + truffle_unittest {
      name: "gate-truffle-slow-path-unittests",
      run: [
        ["mx", "build", "-n", "-c", "-A-Atruffle.dsl.GenerateSlowPathOnly=true"],
        # only those tests exercise meaningfully implemented nodes
        # e.g. com.oracle.truffle.api.dsl.test uses nodes that intentionally produce
        # different results from fast/slow path specializations to test their activation
        ["mx", "unittest", "com.oracle.truffle.api.test.polyglot", "com.oracle.truffle.nfi.test"],
      ],
    },

    windows_amd64 + common.oraclejdk11 + devkits["windows-jdk11"] + truffle_common + truffle_unittest {
      name: "gate-truffle-nfi-windows-11",
      # TODO make that a full gate run
      # currently, some truffle unittests fail on windows
      run: [
        ["mx", "build" ],
        ["mx", "unittest", "--verbose" ],
      ],
    },

    linux_amd64 + common.oraclejdk11 + truffle_common + common.eclipse + common.jdt + {
      name: "weekly-truffle-coverage-11-linux-amd64",
      run: [
        ["mx", "--strict-compliance", "gate", "--strict-mode", "--jacocout", "html"],
        ["mx", "coverage-upload"],
      ],
      targets: ["weekly"],
    },

    # BENCHMARKS

    bench_hw.x52 + common.oraclejdk11 + bench_common + {
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

    linux_amd64 + common.oraclejdk11 + bench_common + {
      name: "gate-truffle-test-benchmarks-11",
      run: [
        ["mx", "benchmark", "truffle:*", "--", "--jvm", "server", "--jvm-config", "graal-core", "--", "com.oracle.truffle", "-f", "1", "-wi", "1", "-w", "1", "-i", "1", "-r", "1"],
      ],
      targets: ["gate"],
    },
  ]
}
