{
  local common = import "../../common.jsonnet",
  local tools      = import "ci_common/tools.libsonnet",
  local sg         = import "ci_common/svm-gate.libsonnet",
  local run_spec   = import "../../ci/ci_common/run-spec.libsonnet",

  local task_spec = run_spec.task_spec,
  local platform_spec = run_spec.platform_spec,

  local t(limit) = task_spec({timelimit: limit}),

  // mx gate build config
  local mxgate(tags) = os_arch_jdk_mixin + sg.mxgate(tags, suite="substratevm", suite_short="svm"),

  local eclipse = task_spec(common.eclipse),
  local jdt = task_spec(common.jdt),
  local gate = sg.gate,
  local gdb(version) = task_spec(sg.gdb(version)),
  local clone_js_benchmarks = sg.clone_js_benchmarks,

  local maven = task_spec({
    packages+: {
      maven: "==3.6.3",
    },
  }),

  local jsonschema = task_spec({
    packages+: {
      "pip:jsonschema": "==4.6.1",
    },
  }),

  local musl_toolchain = task_spec({
    downloads+: {
      "MUSL_TOOLCHAIN": {
        "name": "musl-toolchain",
        "version": "1.0",
        "platformspecific": true,
      },
    },
    environment+: {
      # Note that we must add the toolchain to the end of the PATH so that the system gcc still remains the first choice
      # for building the rest of GraalVM. The musl toolchain also provides a gcc executable that would shadow the system one
      # if it were added at the start of the PATH.
      PATH: "$PATH:$MUSL_TOOLCHAIN/bin",
    },
  }),

  local mx_build_exploded = task_spec({
    environment+: {
      MX_BUILD_EXPLODED: "true", # test native-image MX_BUILD_EXPLODED compatibility
    },
  }),

  local linux_amd64_jdk11 = common.linux_amd64   + common.labsjdk11,
  local linux_amd64_jdk17 = common.linux_amd64   + common.labsjdk17,
  local linux_amd64_jdk19 = common.linux_amd64   + common.labsjdk19,
  local darwin_jdk17      = common.darwin_amd64  + common.labsjdk17,
  local windows_jdk17     = common.windows_amd64 + common.labsjdk17 + common.devkits["windows-jdk17"],

  // JDKs
  local jdk_name_to_dict = {
    "jdk11"+: common.labsjdk11,
    "jdk17"+: common.labsjdk17,
    "jdk19"+: common.labsjdk19,
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

  local no_jobs = {
    "<all-os>"+: run_spec.exclude,
  },

  // START MAIN BUILD DEFINITION
  local task_dict = {
    "js": mxgate("build,js") + clone_js_benchmarks + platform_spec(no_jobs) + platform_spec({
      "linux:amd64:jdk17": gate + t("35:00"),
      "darwin:amd64:jdk17": gate + t("45:00"),
    }),
    "js-quickbuild": mxgate("build,js_quickbuild") + clone_js_benchmarks + platform_spec(no_jobs) + platform_spec({
      "darwin:amd64:jdk17": gate + t("45:00"),
    }),
    "build-ce": mxgate("build,checkstubs,helloworld,test,nativeimagehelp,muslcbuild,debuginfotest") + maven + musl_toolchain + gdb("10.2") + platform_spec(no_jobs) + platform_spec({
      "linux:amd64:jdk11": gate + t("35:00"),
    }),
    "modules-basic": mxgate("build,hellomodule,test") + maven + platform_spec(no_jobs) + platform_spec({
      "linux:amd64:jdk11": gate + t("30:00"),
    }),
    "style-fullbuild": mxgate("style,fullbuild,helloworld,test,svmjunit,debuginfotest") + eclipse + jdt + maven + jsonschema + mx_build_exploded + gdb("10.2") + platform_spec(no_jobs) + platform_spec({
      "linux:amd64:jdk17": gate + t("50:00"),
    }),
    "basics": mxgate("build,helloworld,test,svmjunit") + platform_spec(no_jobs) + platform_spec({
      "linux:amd64:jdk19": gate + t("55:00"),
      "windows:amd64:jdk17": gate + t("1:30:00"),
    }),
    "basics-quickbuild": mxgate("build,helloworld_quickbuild,test_quickbuild,svmjunit_quickbuild") + platform_spec(no_jobs) + platform_spec({
      "windows:amd64:jdk17": gate + t("1:30:00"),
    }),
  },
  // END MAIN BUILD DEFINITION
  processed_builds::run_spec.process(task_dict),
  builds: [{'defined_in': std.thisFile} + b for b in self.processed_builds.list],
  assert tools.check_names($.builds),
}
