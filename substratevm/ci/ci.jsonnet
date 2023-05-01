{
  local gate_triggering_suites = ["sdk", "substratevm", "compiler", "truffle"],

  local common     = import "../../ci/ci_common/common.jsonnet",
  local util       = import "../../ci/ci_common/common-utils.libsonnet",
  local tools      = import "ci_common/tools.libsonnet",
  local sg         = import "ci_common/svm-gate.libsonnet",
  local run_spec   = import "../../ci/ci_common/run-spec.libsonnet",
  local exclude    = run_spec.exclude,

  local task_spec = run_spec.task_spec,
  local platform_spec = run_spec.platform_spec,
  local evaluate_late = run_spec.evaluate_late,

  local t(limit) = task_spec({timelimit: limit}),

  // mx gate build config
  local mxgate(tags) = os_arch_jdk_mixin + sg.mxgate(tags, suite="substratevm", suite_short="svm"),

  local eclipse = task_spec(common.deps.eclipse),
  local jdt = task_spec(common.deps.jdt),
  local gate = sg.gate,
  local gdb(version) = task_spec(sg.gdb(version)),
  local use_musl = sg.use_musl,
  local add_quickbuild = sg.add_quickbuild,

  local maven = task_spec(evaluate_late('05_add_maven', function(b)
  if b.os == 'windows' then {
    downloads+: {
      MAVEN_HOME: {name: 'maven', version: '3.3.9', platformspecific: false},
    },
    environment+: {
      PATH: '$MAVEN_HOME\\bin;$JAVA_HOME\\bin;$PATH',
    },
  } else {
    packages+: {
      maven: "==3.6.3",
    },
  })),

  local jsonschema = task_spec({
    packages+: {
      "pip:jsonschema": "==4.6.1",
    },
  }),

  local mx_build_exploded = task_spec({
    environment+: {
      MX_BUILD_EXPLODED: "true", # test native-image MX_BUILD_EXPLODED compatibility
    },
  }),

  // JDKs
  local jdk_name_to_dict = {
    "jdk17"+: common.labsjdk17,
    "jdk20"+: common.labsjdk20,
    "jdk21"+: common.oraclejdk21,
  },

  local default_os_arch = {
    "linux"+: {
      "amd64"+: common.linux_amd64,
      "aarch64"+: common.linux_aarch64,
    },
    "darwin"+: {
      "amd64"+: common.darwin_amd64,
      "aarch64"+: common.darwin_aarch64,
    },
    "windows"+:{
      "amd64"+: common.windows_amd64 + {
        packages+: common.devkits["windows-jdk" + self.jdk_version].packages
      }
    },
  },
  local os_arch_jdk_mixin = task_spec(run_spec.evaluate_late({
    // this starts with _ on purpose so that it will be evaluated first
    "_os_arch_jdk": function(b)
      tools.delete_timelimit(jdk_name_to_dict[b.jdk] + default_os_arch[b.os][b.arch])
  })),

  local all_jobs = {
    "windows:aarch64"+: exclude,
    "*:*:jdk19"+: exclude,
  },
  local no_jobs = all_jobs {
    "*"+: exclude,
  },

  local feature_map = {
    libc: {
      musl: no_jobs {
        "*"+: use_musl,
      },
    },
    optlevel: {
      quickbuild: no_jobs {
        "*"+: add_quickbuild,
      },
    },
    "java-compiler": {
      ecj: no_jobs {
        "*"+: sg.use_ecj,
      },
    },
  },
  local feature_order = ["libc", "optlevel", "java-compiler"],

  local variants(s) = run_spec.generate_variants(s, feature_map, order=feature_order),

  // START MAIN BUILD DEFINITION
  local task_dict = {
    "style-fullbuild": mxgate("fullbuild,style,nativeimagehelp") + eclipse + jdt + maven + mx_build_exploded + gdb("10.2") + platform_spec(no_jobs) + platform_spec({
      "linux:amd64:jdk20": gate + t("30:00"),
    }),
    "basics": mxgate("build,helloworld,native_unittests,truffle_unittests,debuginfotest,hellomodule") + maven + jsonschema + platform_spec(no_jobs) + platform_spec({
      "linux:amd64:jdk21": gate + gdb("10.2") + t("55:00"),
      "windows:amd64:jdk17": gate + t("1:30:00"),
    }) + variants({
      "optlevel:quickbuild": {
        "windows:amd64:jdk17": gate + t("1:30:00"),
      },
      "libc:musl": {
        "linux:amd64:jdk17": gate + gdb("10.2") + t("55:00"),
      },
      "java-compiler:ecj": {
        "linux:amd64:jdk17": gate + gdb("10.2") + t("55:00"),
      },
    }),
  },
  // END MAIN BUILD DEFINITION
  processed_builds::run_spec.process(task_dict),
  builds: [{'defined_in': std.thisFile} + util.add_gate_predicate(b, gate_triggering_suites) for b in self.processed_builds.list],
  assert tools.check_names($.builds),
}
