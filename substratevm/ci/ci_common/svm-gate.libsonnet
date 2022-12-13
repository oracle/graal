{
  local common     = import "../../../ci/ci_common/common.jsonnet",
  local tools      = import "tools.libsonnet",
  local run_spec   = import "../../../ci/ci_common/run-spec.libsonnet",

  local task_spec     = run_spec.task_spec,
  local evaluate_late = run_spec.evaluate_late,
  local _make_visible = tools._make_visible,
  local t(limit)      = task_spec({timelimit: limit}),
  local require_musl = task_spec((import 'include.libsonnet').musl_dependency),

  local std_get = tools.std_get,

  // mx gate build config
  mxgate(tags, suite, suite_short=suite):: task_spec(common.disable_proxies + {
      mxgate_batch:: null,
      mxgate_tags:: std.split(tags, ","),
      mxgate_config:: [],
      mxgate_extra_args:: [],
      mxgate_dy:: [],
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
        local dynamic_imports = if std.length(outer.mxgate_dy) == 0 then [] else ["--dy", std.join(",", outer.mxgate_dy)],
        // the outer assert just ensures that the expression is evaluated
        assert
          local _fields = ["target", "jdk_version", "os", "arch"];
          local _context = [outer[f] for f in ["build_name"] + _fields if std.objectHasAll(outer, f)];
          _fields == [
          // here is the actual check
          assert std.objectHasAll(outer, f): "build object is missing field '%s' (context: %s) object:\n%s" % [f, _context, std.manifestJsonEx(_make_visible(outer), indent="  ")];
          f
          for f in _fields
        ],
        mxgate_name:: outer.task_name,
        name: std.join("-", [outer.target, suite_short, self.mxgate_name] + config + ["jdk" + outer.jdk_version, outer.os, outer.arch]) + batch_suffix,
        environment+: if batch == null then {} else {MX_SVMTEST_BATCH: batch},
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

  use_musl:: require_musl + task_spec({
      mxgate_config+::["musl"],
      mxgate_extra_args+: ["--extra-image-builder-arguments=--libc=musl --static"],
  }),

  add_quickbuild:: task_spec({
      mxgate_config+::["quickbuild"],
      mxgate_extra_args+: ["--extra-image-builder-arguments=-Ob"],
  }),

  use_llvm:: task_spec({
      mxgate_config+::["llvm"],
      mxgate_extra_args+: ["--extra-image-builder-arguments=-H:CompilerBackend=llvm"],
  }),

  use_ecj:: task_spec({
      mxgate_config+::["ecj"],
  } + evaluate_late("05_ecj_gate", function(b) {
      mxgate_tags:: [if tag == "build" then "ecjbuild" else tag for tag in b.mxgate_tags],
  })),

  clone_js_benchmarks:: task_spec({
    setup+: [["git", "clone", "--depth", "1", ["mx", "urlrewrite", "https://github.com/graalvm/js-benchmarks.git"], "../../js-benchmarks"]],
  }),
}
