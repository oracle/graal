{
  local c = import '../../common.jsonnet',
  local config = import '../../repo-configuration.libsonnet',
  local jvm_config = config.compiler.default_jvm_config,
  local s = self,
  local t(limit) = {timelimit: limit},

  Setup:: {
    setup+: [
      ["cd", "./" + config.compiler.compiler_suite],
      ["mx", "hsdis", "||", "true"]
    ]
  },

  Base(tags="build,test", cmd_suffix=[], extra_vm_args="", extra_unittest_args="", jvm_config_suffix=null):: s.Setup + {
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

  Weekly:: {
    notify_groups: ["compiler_weekly"],
    targets: ["weekly"],
    timelimit: "1:30:00",
  },

  # Configures env vars such that `mx unittest` persists results in a json file
  SaveAsJson:: {
    environment+: {
      "MX_TEST_RESULTS_PATTERN" : "./es-XXX.json",
      "MX_TEST_RESULT_TAGS": "compiler"
    }
  },


  Test:: s.Base(),

  Coverage:: s.Base("build,coverage", ["--jacoco-omit-excluded", "--jacocout", "html"]) + {
    run: [
      ["mx", "coverage-upload"],
      # GR-18258 ["mx", "sonarqube-upload", "-Dsonar.host.url=$SONAR_HOST_URL", "-Dsonar.projectKey=com.oracle.graal.compiler."jvm-config.default, "-Dsonar.projectName=GraalVM - Compiler ("jvm-config.default")", "--exclude-generated", "--skip-coverage"]
    ]
  },

  TestJavaBase:: s.Base("build,javabasetest"),

  TestMaxVS:: s.Base(extra_vm_args="-Dgraal.DetailedAsserts=true -XX:MaxVectorSize=16"),
  TestAVX0:: s.Base(extra_vm_args="-Dgraal.ForceAdversarialLayout=true", jvm_config_suffix="-avx0"),
  TestAVX1:: s.Base(extra_vm_args="-Dgraal.ForceAdversarialLayout=true", jvm_config_suffix="-avx1"),

  TruffleCompImm:: s.Base("build,unittest",
    extra_vm_args="-Dpolyglot.engine.AllowExperimentalOptions=true " +
                  "-Dpolyglot.engine.CompileImmediately=true " +
                  "-Dpolyglot.engine.BackgroundCompilation=false " +
                  "-Dtck.inlineVerifierInstrument=false",
    extra_unittest_args="truffle"),

  CTW:: s.Base("build,ctw"),

  CTWEconomy:: s.Base("build,ctweconomy", extra_vm_args="-Dgraal.CompilerConfiguration=economy"),

  CTWWeekly:: s.Base("build,ctw", ["--jacoco-omit-excluded", "--jacocout", "html"], extra_vm_args="-DCompileTheWorld.MaxClasses=5000" /*GR-23372*/) + {
    run+: [
      ["mx", "coverage-upload"]
    ],
    timelimit : "1:30:00"
  },

  TestBenchmark:: s.Base("build,benchmarktest") + {
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

  Bootstrap:: s.Base("build,bootstrap"),
  BootstrapLite:: s.Base("build,bootstraplite"),
  BootstrapFull:: s.Base("build,bootstrapfullverify"),
  BootstrapEconomy:: s.Base("build,bootstrapeconomy", extra_vm_args="-Dgraal.CompilerConfiguration=economy"),
  
  Style:: c.eclipse + c.jdt + s.Base("style,fullbuild,javadoc"),

  X82_AVX3:: {
    capabilities+: ["x82"],
    environment+: {
      JVM_CONFIG: jvm_config + "-avx3"
    }
  },

  ManyCores:: {
    capabilities+: ["manycores"]
  },

  local edition = config.graalvm_edition,
  LabsJDK11:: c["labsjdk-" + edition + "-11"],
  LabsJDK17:: c["labsjdk-" + edition + "-17"],
  LabsJDK11Debug:: c["labsjdk-" + edition + "-11Debug"],
  LabsJDK17Debug:: c["labsjdk-" + edition + "-17Debug"],

  builds: [
    # Darwin AMD64
    {name: "gate-compiler-test-labsjdk-17-darwin-amd64"} +              s.Test +            s.LabsJDK17 +      c.DarwinAMD64 + t("1:00:00") + s.SaveAsJson,
    {name: "weekly-compiler-test-test-labsjdk-11-darwin-amd64"} +       s.Test +            s.LabsJDK11 +      c.DarwinAMD64 + s.Weekly,

    # Windows AMD64
    {name: "gate-compiler-test-labsjdk-11-windows-amd64"} +             s.Test +            s.LabsJDK11 +      c.WindowsAMD64  + t("55:00") + c.devkits["windows-jdk11"] + s.SaveAsJson,
    {name: "gate-compiler-test-labsjdk-17-windows-amd64"} +             s.Test +            s.LabsJDK17 +      c.WindowsAMD64  + t("55:00") + c.devkits["windows-jdk17"] + s.SaveAsJson,

    # Linux AMD64
    {name: "gate-compiler-test-labsjdk-11-linux-amd64"} +               s.Test +            s.LabsJDK11 +      c.LinuxAMD64 + t("50:00") + s.SaveAsJson,
    {name: "gate-compiler-test-labsjdk-17-linux-amd64"} +               s.Test +            s.LabsJDK17 +      c.LinuxAMD64 + t("55:00") + s.SaveAsJson,
    {name: "gate-compiler-ctw-labsjdk-11-linux-amd64"} +                s.CTW +             s.LabsJDK11 +      c.LinuxAMD64,
    {name: "gate-compiler-ctw-labsjdk-17-linux-amd64"} +                s.CTW +             s.LabsJDK17 +      c.LinuxAMD64,
    {name: "gate-compiler-ctw-economy-labsjdk-11-linux-amd64"} +        s.CTWEconomy +      s.LabsJDK11 +      c.LinuxAMD64,
    {name: "gate-compiler-ctw-economy-labsjdk-17-linux-amd64"} +        s.CTWEconomy +      s.LabsJDK17 +      c.LinuxAMD64,
    {name: "gate-compiler-benchmarktest-labsjdk-11-linux-amd64"} +      s.TestBenchmark +   s.LabsJDK11 +      c.LinuxAMD64,
    {name: "gate-compiler-benchmarktest-labsjdk-17-linux-amd64"} +      s.TestBenchmark +   s.LabsJDK17 +      c.LinuxAMD64,
    {name: "gate-compiler-style-linux-amd64"} +                         s.Style +           s.LabsJDK17 +      c.LinuxAMD64 + t("45:00"),
    {name: "gate-compiler-test-labsjdk-11-linux-amd64-avx3"} +          s.Test +            s.LabsJDK11 +      c.LinuxAMD64 + t("45:00") + s.X82_AVX3 + s.SaveAsJson,
    {name: "gate-compiler-test-truffle-compile-immediately-labsjdk-17-linux-amd64"} + s.TruffleCompImm +  s.LabsJDK17 + c.LinuxAMD64 + t("1:00:00") + s.SaveAsJson,
    {name: "weekly-compiler-test-labsjdk-17-linux-amd64-maxvectorsize"}+s.TestMaxVS +       s.LabsJDK17 +      c.LinuxAMD64 + s.Weekly,
    {name: "weekly-compiler-test-labsjdk-17-linux-amd64-avx0"} +        s.TestAVX0 +        s.LabsJDK17 +      c.LinuxAMD64 + s.Weekly,
    {name: "weekly-compiler-test-labsjdk-17-linux-amd64-avx1"} +        s.TestAVX1 +        s.LabsJDK17 +      c.LinuxAMD64 + s.Weekly,
    {name: "weekly-compiler-test-javabasetest-labsjdk-11-linux-amd64"} +s.TestJavaBase +    s.LabsJDK11 +      c.LinuxAMD64 + s.Weekly,
    {name: "weekly-compiler-coverage-labsjdk-17-linux-amd64"} +         s.Coverage +        s.LabsJDK17Debug + c.LinuxAMD64 + s.Weekly + t("1:50:00"),
    {name: "weekly-compiler-test-benchmarktest-labsjdk-17Debug-linux-amd64-fastdebug"} + s.TestBenchmark + s.LabsJDK17Debug + c.LinuxAMD64 + s.Weekly + t("1:00:00"),
    {name: "weekly-compiler-test-ctw-labsjdk-11-linux-amd64"} +         s.CTWWeekly +       s.LabsJDK11 +      c.LinuxAMD64 + s.Weekly + t("2:00:00"),
    {name: "weekly-compiler-test-ctw-labsjdk-17-linux-amd64"} +         s.CTWWeekly +       s.LabsJDK17 +      c.LinuxAMD64 + s.Weekly,
    {name: "weekly-compiler-test-labsjdk-17-linux-amd64-fastdebug"} +   s.Test +            s.LabsJDK17Debug + c.LinuxAMD64 + s.Weekly + t("3:00:00"),

    # Linux AArch64
    {name: "gate-compiler-test-labsjdk-11-linux-aarch64"} +             s.Test +            s.LabsJDK11 +     c.LinuxAArch64   + t("1:50:00") + s.SaveAsJson,
    {name: "gate-compiler-ctw-labsjdk-11-linux-aarch64"} +              s.CTW +             s.LabsJDK11 +     c.LinuxAArch64   + t("1:50:00"),
    {name: "gate-compiler-ctw-economy-labsjdk-11-linux-aarch64"} +      s.CTWEconomy +      s.LabsJDK11 +     c.LinuxAArch64   + t("1:50:00"),
    {name: "weekly-compiler-coverage-labsjdk-11-linux-aarch64"} +       s.Coverage +        s.LabsJDK11 +     c.LinuxAArch64   + s.Weekly + t("1:50:00"),
    {name: "weekly-compiler-test-ctw-labsjdk-11-linux-aarch64"} +       s.CTWWeekly +       s.LabsJDK11 +     c.LinuxAArch64   + s.Weekly,
    
    # Bootsrap testing
    {name: "gate-compiler-bootstraplite-labsjdk-11-darwin-amd64"} +     s.BootstrapLite +   s.LabsJDK11 +     c.DarwinAMD64    + t("1:00:00"),
    {name: "gate-compiler-bootstraplite-labsjdk-17-darwin-amd64"} +     s.BootstrapLite +   s.LabsJDK17 +     c.DarwinAMD64    + t("1:00:00"),
    {name: "gate-compiler-bootstrapfullverify-labsjdk-17-linux-amd64"} +s.BootstrapFull +   s.LabsJDK17 +     c.LinuxAMD64     + s.ManyCores,

  ] + (import '../ci_includes/bootstrap_extra.libsonnet').builds
}
