{
  local gate_triggering_suites = ["sdk", "substratevm", "compiler", "truffle"],

  local common     = import "../../ci/ci_common/common.jsonnet",
  local util       = import "../../ci/ci_common/common-utils.libsonnet",
  local tools      = import "ci_common/tools.libsonnet",
  local sg         = import "ci_common/svm-gate.libsonnet",
  local run_spec   = import "../../ci/ci_common/run-spec.libsonnet",
  local check_no_timelimit = (import '../../ci/ci_common/run-spec-tools.libsonnet').check_no_timelimit,
  local galahad    = import "../../ci/ci_common/galahad-common.libsonnet",
  local exclude    = run_spec.exclude,

  local task_spec = run_spec.task_spec,
  local platform_spec = run_spec.platform_spec,
  local evaluate_late = run_spec.evaluate_late,

  local t(limit) = task_spec({timelimit: limit}),

  // mx gate build config
  local mxgate(tags) = os_arch_jdk_mixin + sg.mxgate(tags, suite="substratevm", suite_short="svm") + task_spec(common.deps.svm),

  local eclipse = task_spec(common.deps.eclipse),
  local spotbugs = task_spec(common.deps.spotbugs),
  local jdt = task_spec(common.deps.jdt),
  local gate = sg.gate,
  local tier1 = sg.tier1,
  local tier2 = sg.tier2,
  local tier3 = sg.tier3,
  local gdb(version) = task_spec(sg.gdb(version)),
  local use_musl_static = sg.use_musl_static,
  local use_musl_dynamic = sg.use_musl_dynamic,
  local add_quickbuild = sg.add_quickbuild,
  local partial = sg.partial,

  local use_oraclejdk_latest = task_spec(run_spec.evaluate_late({
    "use_oraclejdk_latest": common.oraclejdkLatest + galahad.exclude
  })),

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
    "jdk21"+: common.labsjdk21,
    "jdk-latest"+: common.labsjdkLatest + galahad.exclude,
  },

  local default_os_arch(b) = {
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
        packages+: common.devkits["windows-" + if b.jdk == "jdk-latest" then "jdkLatest" else b.jdk].packages
      }
    },
  },
  local os_arch_jdk_mixin = task_spec(run_spec.evaluate_late({
    // this starts with _ on purpose so that it will be evaluated first
    "_os_arch_jdk": function(b)
      check_no_timelimit(jdk_name_to_dict[b.jdk] + default_os_arch(b)[b.os][b.arch])
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
      musl_static: no_jobs {
        "*"+: use_musl_static,
      },
      musl_dynamic: no_jobs {
        "*"+: use_musl_dynamic,
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
    "style-fullbuild": mxgate("fullbuild,style,nativeimagehelp,check_libcontainer_annotations,check_libcontainer_namespace") + eclipse + jdt + spotbugs + maven + mx_build_exploded + gdb("14.2") + platform_spec(no_jobs) + platform_spec({
      "linux:amd64:jdk-latest": tier1 + t("30:00"),
    }),
    "basics": mxgate("build,helloworld,native_unittests,truffle_unittests,debuginfotest,hellomodule,java_agent,condconfig") + maven + jsonschema + platform_spec(no_jobs) + platform_spec({
      "linux:amd64:jdk-latest": tier2 + partial(2) + gdb("14.2") + t("40:00"),
      "windows:amd64:jdk-latest": tier3 + t("1:30:00"),
    }) + variants({
      "optlevel:quickbuild": {
        "windows:amd64:jdk-latest": tier3 + t("1:30:00"),
      },
      "libc:musl_static": {
        "linux:amd64:jdk-latest": tier3 + gdb("14.2") + t("55:00"),
      },
      "java-compiler:ecj": {
        "linux:amd64:jdk-latest": tier2 + partial(2) + gdb("14.2") + t("40:00"),
      },
    }),
    "oraclejdk-helloworld": mxgate("build,helloworld,hellomodule") + maven + jsonschema + platform_spec(no_jobs) + platform_spec({
      "linux:amd64:jdk-latest": tier1 + use_oraclejdk_latest + t("30:00"),
    }),
  },
  // END MAIN BUILD DEFINITION
  processed_builds::run_spec.process(task_dict),
  builds: util.add_defined_in([util.add_gate_predicate(sg.validate_tiers(b), gate_triggering_suites) for b in self.processed_builds.list], std.thisFile),
  assert tools.check_names($.builds),
}
