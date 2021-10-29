{
  local common = import '../common.jsonnet',
  local darwin_amd64 = common["darwin-amd64"],
  local linux_amd64 = common["linux-amd64"],
  local windows_amd64 = common["windows-amd64"],
  local devkits = (import "../common.json").devkits,

  local truffle_common = {
    packages+: {
      'pip:ninja_syntax': '==1.7.2',
      'pip:pylint': '==1.9.3',
      "pip:isort": "==4.3.19",
      "pip:logilab-common": "==1.4.4",
      'mx': 'HEAD',
    },
    setup+: [
      ["cd", "./truffle"],
    ],
    targets: ["gate"],
    timelimit: "30:00",
  },

  local bench_common = linux_amd64 + common.oraclejdk8 + {
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

  local bench_weekly = bench_common + {
    targets: ["bench", "weekly"],
    timelimit: "3:00:00",
    teardown: [
      ["bench-uploader.py", "${BENCH_RESULTS_FILE_PATH}"],
    ],
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

  local truffle_gate = truffle_common + common.eclipse + common.jdt + {
    name: 'gate-truffle-oraclejdk-' + self.jdk_version,
    run: [["mx", "--strict-compliance", "gate", "--strict-mode"]],
  },

  local truffle_weekly = common.weekly + {notify_groups:: ["truffle_weekly"]},

  builds: [
    sigtest + linux_amd64 + common.oraclejdk8,
    sigtest + linux_amd64 + common.oraclejdk11,
    sigtest + linux_amd64 + common.oraclejdk17,

    simple_tool_maven_project_gate + linux_amd64 + common.oraclejdk8,
    simple_tool_maven_project_gate + linux_amd64 + common["labsjdk-ee-11"],
    simple_tool_maven_project_gate + linux_amd64 + common["labsjdk-ee-17"],
    simple_language_maven_project_gate + linux_amd64 + common.oraclejdk8,
    simple_language_maven_project_gate + linux_amd64 + common["labsjdk-ee-11"],
    simple_language_maven_project_gate + linux_amd64 + common["labsjdk-ee-17"],

    truffle_gate + linux_amd64 + common.oraclejdk8  + {timelimit: "45:00"},
    truffle_gate + linux_amd64 + common.oraclejdk11 + {environment+: {DISABLE_DSL_STATE_BITS_TESTS: "true"}},
    truffle_gate + linux_amd64 + common.oraclejdk17 + {environment+: {DISABLE_DSL_STATE_BITS_TESTS: "true"}},

    truffle_common + linux_amd64 + common.oraclejdk8 + {
      name: "gate-truffle-javadoc",
      run: [
        ["mx", "build"],
        ["mx", "javadoc"],
      ],
    },

    truffle_common + linux_amd64 + common.oraclejdk8 + {
      name: "gate-truffle-slow-path-unittests",
      run: [
        ["mx", "build", "-n", "-c", "-A-Atruffle.dsl.GenerateSlowPathOnly=true"],
        # only those tests exercise meaningfully implemented nodes
        # e.g. com.oracle.truffle.api.dsl.test uses nodes that intentionally produce
        # different results from fast/slow path specializations to test their activation
        ["mx", "unittest", "com.oracle.truffle.api.test.polyglot", "com.oracle.truffle.nfi.test"],
      ],
    },

    gate_lite + darwin_amd64 + common.oraclejdk8  + truffle_weekly,
    gate_lite + darwin_amd64 + common.oraclejdk11 + truffle_weekly,
    gate_lite + darwin_amd64 + common.oraclejdk17 + truffle_weekly,

    windows_amd64 + common.oraclejdk8 + devkits["windows-oraclejdk8"] + truffle_common + {
      name: "gate-truffle-nfi-windows-8",
      # TODO make that a full gate run
      # currently, some truffle unittests fail on windows
      run: [
        ["mx", "build" ],
        ["mx", "unittest", "--verbose" ]
      ],
    },

    # BENCHMARKS

    bench_weekly + {
      name: "bench-truffle-jmh",
      notify_groups:: ["truffle_bench"],
      run: [
        ["mx", "--kill-with-sigquit", "benchmark", "--results-file", "${BENCH_RESULTS_FILE_PATH}", "truffle:*", "--", "--", "com.oracle.truffle"],
      ],
    },

    bench_common + {
      name: "gate-truffle-test-benchmarks-8",
      run: [
        ["mx", "benchmark", "truffle:*", "--", "--jvm", "server", "--jvm-config", "graal-core", "--", "com.oracle.truffle", "-f", "1", "-wi", "1", "-w", "1", "-i", "1", "-r", "1"],
      ],
      targets: ["gate"],
    },

    truffle_common + linux_amd64 + common.oraclejdk8 + common.eclipse + common.jdt + {
      name: "weekly-truffle-coverage-8-linux-amd64",
      run: [
        ["mx", "--strict-compliance", "gate", "--strict-mode", "--jacocout", "html"],
        ["mx", "coverage-upload"]
      ],
      targets: ["weekly"],
    },
  ]
}
