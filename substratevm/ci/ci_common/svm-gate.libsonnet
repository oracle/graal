{
  local common     = import "../../../ci/ci_common/common.jsonnet",
  local run_spec   = import "../../../ci/ci_common/run-spec.libsonnet",
  local galahad    = import "../../../ci/ci_common/galahad-common.libsonnet",

  local task_spec     = run_spec.task_spec,
  local evaluate_late = run_spec.evaluate_late,
  local make_visible  = (import "../../../common-utils.libsonnet").make_visible,
  local t(limit)      = task_spec({timelimit: limit}),
  local require_musl  = task_spec((import '../../../ci/ci_common/musl-common.libsonnet').musl_dependency),

  local std_get = (import "../../../common-utils.libsonnet").std_get,

  // mx gate build config
  mxgate(tags, suite, suite_short=suite, enable_proxies=false):: task_spec((if !enable_proxies then common.disable_proxies else {}) + {
      mxgate_batch:: null,
      mxgate_tags:: std.split(tags, ","),
      mxgate_config:: [],
      mxgate_extra_args:: [],
      mxgate_dy:: [],
      mxgate_target_arch:: null,
      setup+: [
        ["cd", "./" + suite],
        ["mx", "hsdis", "||", "true"],
      ],
    } + evaluate_late({
      mxgate: function(b) {
        local outer = b,
        local batch = outer.mxgate_batch,
        local config = outer.mxgate_config,
        local batch_suffix = if batch == null then "" else "_" + batch,
        local target_arch = outer.mxgate_target_arch,
        local target_arch_suffix = if target_arch == null then [] else [target_arch, "cross"],
        local dynamic_imports = if std.length(outer.mxgate_dy) == 0 then [] else ["--dy", std.join(",", outer.mxgate_dy)],
        // the outer assert just ensures that the expression is evaluated
        assert
          local _fields = ["target", "jdk_version", "os", "arch"];
          local _context = [outer[f] for f in ["build_name"] + _fields if std.objectHasAll(outer, f)];
          _fields == [
          // here is the actual check
          assert std.objectHasAll(outer, f): "build object is missing field '%s' (context: %s) object:\n%s" % [f, _context, std.manifestJsonEx(make_visible(outer), indent="  ")];
          f
          for f in _fields
        ],
        mxgate_name:: outer.task_name,
        name: std.join("-", [outer.target, suite_short, self.mxgate_name] + config + [outer.jdk_name] + target_arch_suffix + [outer.os, outer.arch]) + batch_suffix,
        run+: [["mx", "--kill-with-sigquit", "--strict-compliance"] + dynamic_imports + ["gate", "--strict-mode", "--tags", std.join(",", outer.mxgate_tags)] + outer.mxgate_extra_args],
      }
    })),

  dy(dynamic_import):: task_spec({
    mxgate_dy+: [dynamic_import]
  }),

  gdb(version):: {
    downloads+: {
      GDB: {name: "gdb", version: version, platformspecific: true},
    },
    environment+: {
      GDB_BIN: "$GDB/bin/gdb",
    },
  },

  target(t):: task_spec({
    target:: t,
    targets: [t],
  }),

  gate:: $.target("gate"),
  daily:: $.target("daily"),
  weekly:: $.target("weekly"),
  ondemand:: $.target("ondemand"),

  use_musl:: require_musl + task_spec({
      mxgate_config+::["musl"],
      mxgate_extra_args+: ["--extra-image-builder-arguments=--libc=musl --static"],
  } +
    # The galahad gates run with oracle JDK, which do not offer a musl build
    galahad.exclude
  ),

  add_quickbuild:: task_spec({
      mxgate_config+::["quickbuild"],
      mxgate_extra_args+: ["--extra-image-builder-arguments=-Ob"],
  }),

  add_o3:: task_spec({
      mxgate_config+::["O3"],
      mxgate_extra_args+: ["--extra-image-builder-arguments=-O3"],
  }),

  use_llvm:: task_spec({
      mxgate_config+::["llvm"],
      mxgate_extra_args+: ["--extra-image-builder-arguments=-H:+UnlockExperimentalVMOptions -H:CompilerBackend=llvm -H:-UnlockExperimentalVMOptions"],
  }),

  use_ecj:: task_spec({
      mxgate_config+::["ecj"],
  } + evaluate_late("05_ecj_gate", function(b) {
      mxgate_tags:: [if tag == "build" then "ecjbuild" else tag for tag in b.mxgate_tags],
  })),

  clone_js_benchmarks:: task_spec({
    setup+: [["git", "clone", "--depth", "1", ["mx", "urlrewrite", "https://github.com/graalvm/js-benchmarks.git"], "../../js-benchmarks"]],
  }),

  riscv64_cross_compile:: task_spec({
    mxgate_target_arch:: "riscv64",
    environment+: {CAPCACHE: "$HOME/capcache"},
    packages+: {
      "git": ">=1.8.3",
      "python": "==3.4.1",
      "make": ">=3.83",
      "zlib": ">=1.2.11",
      "riscv-gnu-toolchain": "==8.3.0",
      "qemu": ">=4.0.0",
      "glib": "==2.56.1",
      "pcre": "==8.43",
      "sshpass": "==1.05"
    },
  } + evaluate_late("riscv64-svmtest", function(b) {
    downloads+: {
      QEMU_HOME          : {name : "qemu-riscv64", version : "1.0"},
      C_LIBRARY_PATH     : {name : "riscv-static-libraries", version : std.toString(b.jdk_version)},
      JAVA_HOME_RISCV    : {name : "labsjdk", version : b.downloads.JAVA_HOME.version + "-linux-riscv64" }
    },
  })),
}
