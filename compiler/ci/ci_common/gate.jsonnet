{
  local c = import '../../../ci/ci_common/common.jsonnet',
  local config = import '../../../ci/repo-configuration.libsonnet',
  local jvm_config = config.compiler.default_jvm_config,
  local s = self,
  local t(limit) = {timelimit: limit},

  setup:: {
    setup+: [
      ["cd", "./" + config.compiler.compiler_suite],
      ["mx", "hsdis", "||", "true"]
    ]
  },

  base(tags="build,test", cmd_suffix=[], extra_vm_args="", extra_unittest_args="", jvm_config_suffix=null, no_warning_as_error=false):: s.setup + {
    run+: [
      ["mx", "--strict-compliance",
         "--kill-with-sigquit",
         "gate",
         "--strict-mode",
         "--extra-vm-argument=-Dgraal.DumpOnError=true -Dgraal.PrintGraphFile=true -Dgraal.PrintBackendCFG=true" +
           (if extra_vm_args == "" then "" else " " + extra_vm_args)
      ] + (if extra_unittest_args != "" then [
        "--extra-unittest-argument=" + extra_unittest_args,
      ] else []) + (if no_warning_as_error then [
        "--no-warning-as-error"
      ] else []) + [
        "--tags=" + tags
      ] + cmd_suffix
    ],
    environment+: if jvm_config_suffix != null then {
      JVM_CONFIG: jvm_config + jvm_config_suffix
    } else {},
    targets: ["gate"],
    python_version: "3"
  },

  daily:: {
    # Re-use existing mailing list for weeklies
    notify_groups: ["compiler_weekly"],
    targets: ["daily"],
    timelimit: "2:00:00",
  },

  weekly:: {
    notify_groups: ["compiler_weekly"],
    targets: ["weekly"],
    timelimit: "2:00:00",
  },

  monthly:: {
    # No need for a dedicated mailing list for monthlies yet
    notify_groups: ["compiler_weekly"],
    targets: ["monthly"],
    timelimit: "2:00:00",
  },

  test:: s.base(no_warning_as_error=true),

  jacoco_gate_args:: ["--jacoco-omit-excluded", "--jacoco-relativize-paths", "--jacoco-omit-src-gen", "--jacocout", "coverage", "--jacoco-format", "lcov"],
  upload_coverage:: ["mx", "sversions", "--print-repositories", "--json", "|", "coverage-uploader.py", "--associated-repos", "-"],

  coverage_base(ctw):: s.base(tags="build,%s" % if ctw then "ctw" else "coverage",
                              cmd_suffix=s.jacoco_gate_args,
                              extra_vm_args=if !ctw then "" else "-DCompileTheWorld.MaxClasses=5000" /*GR-23372*/) +
  {
    teardown+: [
      s.upload_coverage,
    ],
  },

  coverage::      s.coverage_base(ctw=false),
  coverage_avx3:: s.coverage_base(ctw=false) + s.avx3,
  coverage_ctw::  s.coverage_base(ctw=true),

  test_javabase:: s.base("build,javabasetest"),
  test_jtt_phaseplan_fuzzing:: s.base("build,phaseplan-fuzz-jtt-tests"),
  test_vec16:: s.base(extra_vm_args="-Dgraal.DetailedAsserts=true -XX:MaxVectorSize=16"),
  test_avx0:: s.base(extra_vm_args="-Dgraal.ForceAdversarialLayout=true", jvm_config_suffix="-avx0"),
  test_avx1:: s.base(extra_vm_args="-Dgraal.ForceAdversarialLayout=true", jvm_config_suffix="-avx1"),

  # Runs truffle tests in a mode similar to HotSpot's -Xcomp option
  # (i.e. compile immediately without background compilation).
  truffle_xcomp:: s.base("build,unittest",
    extra_vm_args="-Dpolyglot.engine.AllowExperimentalOptions=true " +
                  "-Dpolyglot.engine.CompileImmediately=true " +
                  "-Dpolyglot.engine.BackgroundCompilation=false " +
                  "-Dtck.inlineVerifierInstrument=false",
    extra_unittest_args="--very-verbose truffle") + {
      environment+: {"TRACE_COMPILATION": "true"},
      logs+: ["*/*_compilation.log"]
    },

  ctw:: s.base("build,ctw", no_warning_as_error=true),

  ctw_economy:: s.base("build,ctweconomy", extra_vm_args="-Dgraal.CompilerConfiguration=economy"),
  ctw_phaseplan_fuzzing:: s.base("build,ctwphaseplanfuzzing"),

  # Runs some benchmarks as tests
  benchmarktest:: s.base("build,benchmarktest") + {
    run+: [
      # blackbox jmh test
      ["mx", "benchmark", "jmh-dist:GRAAL_COMPILER_MICRO_BENCHMARKS",
             "--fail-fast",
             "--",
             "-Djmh.ignoreLock=true",
             "--jvm-config=" + jvm_config,
             "--jvm=server",
             "--",
             ".*TestJMH.*" ],
      # whitebox jmh test
      ["mx", "benchmark", "jmh-whitebox:*",
             "--fail-fast",
             "--",
             "-Djmh.ignoreLock=true",
             "--jvm-config=" + jvm_config,
             "--jvm=server",
             "--",
             ".*TestJMH.*" ]
    ]
  },

  bootstrap:: s.base("build,bootstrap", no_warning_as_error=true),
  bootstrap_lite:: s.base("build,bootstraplite", no_warning_as_error=true),
  bootstrap_full:: s.base("build,bootstrapfullverify", no_warning_as_error=true),
  bootstrap_economy:: s.base("build,bootstrapeconomy", no_warning_as_error=true, extra_vm_args="-Dgraal.CompilerConfiguration=economy"),

  style:: c.eclipse + c.jdt + s.base("style,fullbuild,javadoc"),

  avx3:: {
    capabilities+: ["avx512"],
    environment+: {
      JVM_CONFIG: jvm_config + "-avx3"
    }
  },

  many_cores:: {
    capabilities+: ["manycores"]
  },

  # Returns true if `str` contains `needle` as a substring.
  contains(str, needle):: std.findSubstr(needle, str) != [],

  # Returns the value of the `name` field if it exists in `obj` otherwise `default`.
  get(obj, name, default=null)::
      if obj == null then default else
      if std.objectHas(obj, name) then obj[name] else default,

  # Returns true if `key` == `value` or key ends with "*" and
  # is a prefix of `value` (without the trailing "*").
  local key_matches_value(key, value) =
    if std.endsWith(key, "*") then
      local prefix = std.substr(key, 0, std.length(key) - 1);
      std.startsWith(value, prefix)
    else
      key == value,

  manifest_match(manifest, name):: [key for key in std.objectFields(manifest) if key_matches_value(key, name)] != [],

  # This map defines the builders that run as gates. Each key in this map
  # must correspond to the name of a build created by `make_build`.
  # Each value in this map is an object that overrides or extends the
  # fields of the denoted build.
  local gates = {
    "gate-compiler-test-labsjdk-20-linux-amd64": t("1:00:00") + c.mach5_target,
    "gate-compiler-test-labsjdk-20-linux-aarch64": t("1:50:00"),
    "gate-compiler-test-labsjdk-20-darwin-amd64": t("1:00:00") + c.mach5_target,
    "gate-compiler-test-labsjdk-20-darwin-aarch64": t("1:00:00"),
    "gate-compiler-test-labsjdk-20-windows-amd64": t("55:00") + c.mach5_target,

    "gate-compiler-style-labsjdk-20-linux-amd64": t("45:00"),

    "gate-compiler-ctw-labsjdk-20-linux-amd64": c.mach5_target,
    "gate-compiler-ctw-labsjdk-20-windows-amd64": t("1:50:00"),

    "gate-compiler-ctw_economy-labsjdk-20-linux-amd64": {},
    "gate-compiler-ctw_economy-labsjdk-20-windows-amd64": t("1:50:00"),

    "gate-compiler-benchmarktest-labsjdk-20-linux-amd64": {},

    "gate-compiler-truffle_xcomp-labsjdk-20-linux-amd64": t("1:30:00"),

    "gate-compiler-bootstrap_lite-labsjdk-20-darwin-amd64": t("1:00:00") + c.mach5_target,

    "gate-compiler-bootstrap_full-labsjdk-20-linux-amd64": s.many_cores + c.mach5_target
  },

  # This map defines the builders that run daily. Each key in this map
  # must be the name of a build created by `make_build` (or be the prefix
  # of a build name if the key ends with "*").
  # Each value in this map is an object that overrides or extends the
  # fields of the denoted build.
  local dailies = {
    "daily-compiler-ctw-labsjdk-20-linux-aarch64": {},
    "daily-compiler-ctw-labsjdk-20-darwin-amd64": {},
    "daily-compiler-ctw-labsjdk-20-darwin-aarch64": {},

    "daily-compiler-ctw_economy-labsjdk-20-linux-aarch64": {},
    "daily-compiler-ctw_economy-labsjdk-20-darwin-amd64": {},
    "daily-compiler-ctw_economy-labsjdk-20-darwin-aarch64": {},
  },

  # This map defines the builders that run weekly. Each key in this map
  # must be the name of a build created by `make_build` (or be the prefix
  # of a build name if the key ends with "*").
  # Each value in this map is an object that overrides or extends the
  # fields of the denoted build.
  local weeklies = {
    "weekly-compiler-ctw_phaseplan_fuzzing-labsjdk-20-linux-amd64": {
      notify_groups: [],
      notify_emails: ["gergo.barany@oracle.com"],
    },

    "weekly-compiler-test-labsjdk-17-linux-amd64": {},
    "weekly-compiler-test-labsjdk-17-linux-aarch64": {},
    "weekly-compiler-test-labsjdk-17-windows-amd64": {},
    "weekly-compiler-test-labsjdk-17-darwin-amd64": {},
    "weekly-compiler-test-labsjdk-17-darwin-aarch64": {},

    "weekly-compiler-test_vec16-labsjdk-20-linux-amd64": {},
    "weekly-compiler-test_avx0-labsjdk-20-linux-amd64": {},
    "weekly-compiler-test_avx1-labsjdk-20-linux-amd64": {},
    "weekly-compiler-test_jtt_phaseplan_fuzzing-labsjdk-20-linux-amd64": {
      notify_groups: [],
      notify_emails: ["gergo.barany@oracle.com"],
    },

    "weekly-compiler-bootstrap_lite-labsjdk-17-darwin-amd64": t("1:00:00") + c.mach5_target,

    "weekly-compiler-benchmarktest-labsjdk-20Debug-linux-amd64": {},

    "weekly-compiler-coverage*": {},

    "weekly-compiler-test-labsjdk-20Debug-linux-amd64": {}
  },

  # This map defines overrides and field extensions for monthly builds.
  local monthlies = {},

  # Creates a CI build object.
  #
  # jdk: JDK version (e.g. "20", "20Debug")
  # os_arch: OS and architecture (e.g., "linux-amd64", "darwin-aarch64")
  # task: name of an object field in self defining the JDK and platform agnostic build details (e.g. "test")
  # extra_tasks: object whose fields define additional tasks to those defined in self
  # gates_manifest: specification of gate builds (e.g. see `gates` local variable)
  # dailies_manifest: specification of daily builds (e.g. see `dailies` local variable)
  # weeklies_manifest: specification of weekly builds (e.g. see `weeklies` local variable)
  # monthlies_manifest: specification of monthly builds (e.g. see `monthlies` local variable)
  # returns: an object with a single "build" field
  make_build(jdk, os_arch, task, suite="compiler", extra_tasks={},
             include_common_os_arch=true,
             gates_manifest=gates,
             dailies_manifest=dailies,
             weeklies_manifest=weeklies,
             monthlies_manifest=monthlies):: {
    local base_name = "%s-%s-labsjdk-%s-%s" % [suite, task, jdk, os_arch],
    local gate_name = "gate-" + base_name,
    local daily_name = "daily-" + base_name,
    local weekly_name = "weekly-" + base_name,
    local monthly_name = "monthly-" + base_name,

    local is_gate = $.manifest_match(gates_manifest, gate_name),
    local is_daily = $.manifest_match(dailies_manifest, daily_name),
    local is_monthly = $.manifest_match(monthlies_manifest, monthly_name),
    local is_weekly = !is_gate && !is_daily && !is_monthly, # Default to weekly
    local is_windows = $.contains(os_arch, "windows"),
    local extra = if is_gate then
        $.get(gates_manifest, gate_name, {})
      else if is_daily then
        $.get(dailies_manifest, daily_name, {})
      else if is_weekly then
        $.get(weeklies_manifest, weekly_name, {})
      else if is_monthly then
        $.get(monthlies_manifest, monthly_name, {}),

    build: {
      name: if is_gate   then gate_name
       else if is_daily  then daily_name
       else if is_weekly then weekly_name
       else                   monthly_name
    } +
      (s + extra_tasks)[task] +
      c["labsjdk%s" % jdk] +
      (if include_common_os_arch then c[std.strReplace(os_arch, "-", "_")] else {}) +
      (if is_daily then s.daily else {}) +
      (if is_weekly then s.weekly else {}) +
      (if is_monthly then s.monthly else {}) +
      (if is_windows then c.devkits["windows-jdk%s" % jdk] else {}) +
      extra,
  },

  # Checks that each key in `manifest` corresponds to the name of a build in `builds`.
  #
  # manifest: a map whose keys must be a subset of the build names in `builds`
  # manifest_file: file in which the value of `manifest` originates
  # manifest_name: name of the field providing the value of `manifest`
  check_manifest(manifest, builds, manifest_file, manifest_name): {
    local manifest_keys = std.set([key for key in std.objectFields(manifest) if !std.endsWith(key, "*")]),
    local manifest_prefixes = std.set([std.substr(key, 0, std.length(key) - 1) for key in std.objectFields(manifest) if std.endsWith(key, "*")]),
    #    local manifest_keys = std.set(std.objectFields(manifest)),
    local build_names = std.set([b.name for b in builds]),
    local unknown_keys = std.setDiff(manifest_keys, build_names),
    local unknown_prefixes = [p for p in manifest_prefixes if [n for n in build_names if std.startsWith(n, p)] == []],

    result: if unknown_keys != [] then
        error "%s: name(s) in %s manifest that do not match a defined builder:\n  %s\nDefined builders:\n  %s" % [
          manifest_file,
          manifest_name,
          std.join("\n  ", std.sort(unknown_keys)),
          std.join("\n  ", std.sort(build_names))]
      else if unknown_prefixes != [] then
        error "%s: prefix(es) in %s manifest that do not match a defined builder:\n  %s\nDefined builders:\n  %s" % [
          manifest_file,
          manifest_name,
          std.join("\n  ", std.sort(unknown_prefixes)),
          std.join("\n  ", std.sort(build_names))]
      else
        true
  },

  # Builds run on all platforms (platform = JDK + OS + ARCH)
  local all_platforms_builds = [self.make_build(jdk, os_arch, task).build
    for jdk in [
      "17",
      "20"
    ]
    for os_arch in [
      "linux-amd64",
      "linux-aarch64",
      "darwin-amd64",
      "darwin-aarch64",
      "windows-amd64"
    ]
    for task in [
      "test",
      "truffle_xcomp",
      "ctw",
      "ctw_economy",
      "coverage",
      "coverage_ctw",
      "benchmarktest",
      "bootstrap_lite",
      "bootstrap_full"
    ]
  ],

  # Builds run on only on linux-amd64-jdk20
  local linux_amd64_jdk20_builds = [self.make_build("20", "linux-amd64", task).build
    for task in [
      "ctw_phaseplan_fuzzing",
      "coverage_avx3",
      "test_vec16",
      "test_avx0",
      "test_avx1",
      "test_javabase",
      "test_jtt_phaseplan_fuzzing",
      "style"
    ]
  ],

  # Builds run on only on linux-amd64-jdk20Debug
  local linux_amd64_jdk20Debug_builds = [self.make_build("20Debug", "linux-amd64", task).build
    for task in [
      "benchmarktest",
      "test"
    ]
  ],

  # Complete set of builds defined in this file
  local all_builds =
    all_platforms_builds +
    linux_amd64_jdk20_builds +
    linux_amd64_jdk20Debug_builds,

  builds: if
      self.check_manifest(gates,     all_builds, std.thisFile, "gates").result &&
      self.check_manifest(dailies,   all_builds, std.thisFile, "dailies").result &&
      self.check_manifest(weeklies,  all_builds, std.thisFile, "weeklies").result &&
      self.check_manifest(monthlies, all_builds, std.thisFile, "monthlies").result
    then
      all_builds + (import '../ci_includes/bootstrap_extra.libsonnet').builds
}
