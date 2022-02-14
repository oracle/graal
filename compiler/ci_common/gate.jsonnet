{
  local c = import '../../common.jsonnet',
  local config = import '../../repo-configuration.libsonnet',
  local jvm_config = config.compiler.default_jvm_config,
  local s = self,
  local t(limit) = {timelimit: limit},

  setup:: {
    setup+: [
      ["cd", "./" + config.compiler.compiler_suite],
      ["mx", "hsdis", "||", "true"]
    ]
  },

  base(tags="build,test", cmd_suffix=[], extra_vm_args="", extra_unittest_args="", jvm_config_suffix=null):: s.setup + {
    run+: [
      ["mx", "--strict-compliance",
         "--kill-with-sigquit",
         "gate",
         "--strict-mode",
         "--extra-vm-argument=-Dgraal.DumpOnError=true -Dgraal.PrintGraphFile=true -Dgraal.PrintBackendCFG=true" +
           (if extra_vm_args == "" then "" else " " + extra_vm_args)
      ] + (if extra_unittest_args != "" then [
        "--extra-unittest-argument=" + extra_unittest_args,
      ] else []) + [
        "--tags=" + tags
      ] + cmd_suffix
    ],
    environment+: if jvm_config_suffix != null then {
      JVM_CONFIG: jvm_config + jvm_config_suffix
    } else {},
    targets: ["gate"]
  },

  weekly:: {
    notify_groups: ["compiler_weekly"],
    targets: ["weekly"],
    timelimit: "1:30:00",
  },

  # Configures env vars such that `mx unittest` persists results in a json file
  save_as_json:: {
    environment+: {
      "MX_TEST_RESULTS_PATTERN" : "./es-XXX.json",
      "MX_TEST_RESULT_TAGS": "compiler"
    }
  },


  test:: s.base(),

  coverage:: s.base("build,coverage", ["--jacoco-omit-excluded", "--jacocout", "html"]) + {
    run+: [
      ["mx", "coverage-upload"],
      # GR-18258 ["mx", "sonarqube-upload", "-Dsonar.host.url=$SONAR_HOST_URL", "-Dsonar.projectKey=com.oracle.graal.compiler."jvm-config.default, "-Dsonar.projectName=GraalVM - Compiler ("jvm-config.default")", "--exclude-generated", "--skip-coverage"]
    ]
  },

  test_javabase:: s.base("build,javabasetest"),

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
    extra_unittest_args="truffle"),

  ctw:: s.base("build,ctw"),

  ctw_economy:: s.base("build,ctweconomy", extra_vm_args="-Dgraal.CompilerConfiguration=economy"),

  coverage_ctw:: s.base("build,ctw", ["--jacoco-omit-excluded", "--jacocout", "html"], extra_vm_args="-DCompileTheWorld.MaxClasses=5000" /*GR-23372*/) + {
    run+: [
      ["mx", "coverage-upload"]
    ],
    timelimit : "1:30:00"
  },

  # Runs some benchmarks as tests
  benchmark:: s.base("build,benchmarktest") + {
    run+: [
      # blackbox jmh test
      ["mx", "benchmark", "jmh-dist:GRAAL_COMPILER_MICRO_BENCHMARKS",
             "--",
             "-Djmh.ignoreLock=true",
             "--jvm-config=" + jvm_config,
             "--jvm=server",
             "--",
             ".*TestJMH.*" ],
      # whitebox jmh test
      ["mx", "benchmark", "jmh-whitebox:*",
             "--",
             "-Djmh.ignoreLock=true",
             "--jvm-config=" + jvm_config,
             "--jvm=server",
             "--",
             ".*TestJMH.*" ]
    ]
  },

  bootstrap:: s.base("build,bootstrap"),
  bootstrap_lite:: s.base("build,bootstraplite"),
  bootstrap_full:: s.base("build,bootstrapfullverify"),
  bootstrap_economy:: s.base("build,bootstrapeconomy", extra_vm_args="-Dgraal.CompilerConfiguration=economy"),
  
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

  builds: [
    # Darwin AMD64
    {name: "gate-compiler-test-labsjdk-17-darwin-amd64"} +              s.test +           c.labsjdk17 +      c.darwin_amd64 + t("1:00:00") + s.save_as_json,
    {name: "weekly-compiler-test-labsjdk-11-darwin-amd64"} +            s.test +           c.labsjdk11 +      c.darwin_amd64 + s.weekly,

    # Windows AMD64
    {name: "gate-compiler-test-labsjdk-11-windows-amd64"} +             s.test +           c.labsjdk11 +      c.windows_amd64  + t("55:00") + c.devkits["windows-jdk11"] + s.save_as_json,
    {name: "gate-compiler-test-labsjdk-17-windows-amd64"} +             s.test +           c.labsjdk17 +      c.windows_amd64  + t("55:00") + c.devkits["windows-jdk17"] + s.save_as_json,

    # Linux AMD64
    {name: "gate-compiler-test-labsjdk-11-linux-amd64"} +               s.test +           c.labsjdk11 +      c.linux_amd64 + t("50:00") + s.save_as_json,
    {name: "gate-compiler-test-labsjdk-17-linux-amd64"} +               s.test +           c.labsjdk17 +      c.linux_amd64 + t("55:00") + s.save_as_json,
    {name: "gate-compiler-ctw-labsjdk-11-linux-amd64"} +                s.ctw +            c.labsjdk11 +      c.linux_amd64,
    {name: "gate-compiler-ctw-labsjdk-17-linux-amd64"} +                s.ctw +            c.labsjdk17 +      c.linux_amd64,
    {name: "gate-compiler-ctw-economy-labsjdk-11-linux-amd64"} +        s.ctw_economy +    c.labsjdk11 +      c.linux_amd64,
    {name: "gate-compiler-ctw-economy-labsjdk-17-linux-amd64"} +        s.ctw_economy +    c.labsjdk17 +      c.linux_amd64,
    {name: "gate-compiler-benchmarktest-labsjdk-11-linux-amd64"} +      s.benchmark +      c.labsjdk11 +      c.linux_amd64,
    {name: "gate-compiler-benchmarktest-labsjdk-17-linux-amd64"} +      s.benchmark +      c.labsjdk17 +      c.linux_amd64,
    {name: "gate-compiler-style-linux-amd64"} +                         s.style +          c.labsjdk17 +      c.linux_amd64 + t("45:00"),
    {name: "gate-compiler-test-truffle-xcomp-labsjdk-17-linux-amd64"} + s.truffle_xcomp +  c.labsjdk17 +      c.linux_amd64 + t("1:00:00") + s.save_as_json,
    {name: "weekly-compiler-test-labsjdk-17-linux-amd64-vector16"} +    s.test_vec16 +     c.labsjdk17 +      c.linux_amd64 + s.weekly,
    {name: "weekly-compiler-test-labsjdk-17-linux-amd64-avx0"} +        s.test_avx0 +      c.labsjdk17 +      c.linux_amd64 + s.weekly,
    {name: "weekly-compiler-test-labsjdk-17-linux-amd64-avx1"} +        s.test_avx1 +      c.labsjdk17 +      c.linux_amd64 + s.weekly,
    {name: "weekly-compiler-test-javabasetest-labsjdk-11-linux-amd64"} +s.test_javabase +  c.labsjdk11 +      c.linux_amd64 + s.weekly,
    {name: "weekly-compiler-coverage-labsjdk-17-linux-amd64"} +         s.coverage +       c.labsjdk17Debug + c.linux_amd64 + s.weekly + t("1:50:00"),
    {name: "weekly-compiler-test-benchmarktest-labsjdk-17Debug-linux-amd64-fastdebug"} +   c.labsjdk17Debug + c.linux_amd64 + s.benchmark + s.weekly + t("1:00:00"),
    {name: "weekly-compiler-test-ctw-labsjdk-11-linux-amd64"} +         s.coverage_ctw +   c.labsjdk11 +      c.linux_amd64 + s.weekly + t("2:00:00"),
    {name: "weekly-compiler-test-ctw-labsjdk-17-linux-amd64"} +         s.coverage_ctw +   c.labsjdk17 +      c.linux_amd64 + s.weekly,
    {name: "weekly-compiler-test-labsjdk-17-linux-amd64-fastdebug"} +   s.test +           c.labsjdk17Debug + c.linux_amd64 + s.weekly + t("3:00:00"),

    # Linux AArch64
    {name: "gate-compiler-test-labsjdk-11-linux-aarch64"} +             s.test +           c.labsjdk11 +      c.linux_aarch64 + t("1:50:00") + s.save_as_json,
    {name: "gate-compiler-ctw-labsjdk-11-linux-aarch64"} +              s.ctw +            c.labsjdk11 +      c.linux_aarch64 + t("1:50:00"),
    {name: "gate-compiler-ctw-economy-labsjdk-11-linux-aarch64"} +      s.ctw_economy +    c.labsjdk11 +      c.linux_aarch64 + t("1:50:00"),
    {name: "weekly-compiler-coverage-labsjdk-11-linux-aarch64"} +       s.coverage +       c.labsjdk11 +      c.linux_aarch64 + s.weekly + t("1:50:00"),
    {name: "weekly-compiler-test-ctw-labsjdk-11-linux-aarch64"} +       s.coverage_ctw +   c.labsjdk11 +      c.linux_aarch64 + s.weekly,
    
    # Bootstrap testing
    {name: "gate-compiler-bootstraplite-labsjdk-11-darwin-amd64"} +     s.bootstrap_lite + c.labsjdk11 +      c.darwin_amd64 + t("1:00:00"),
    {name: "gate-compiler-bootstraplite-labsjdk-17-darwin-amd64"} +     s.bootstrap_lite + c.labsjdk17 +      c.darwin_amd64 + t("1:00:00"),
    {name: "gate-compiler-bootstrapfullverify-labsjdk-17-linux-amd64"} +s.bootstrap_full + c.labsjdk17 +      c.linux_amd64  + s.many_cores,

  ] + (import '../ci_includes/bootstrap_extra.libsonnet').builds
}
