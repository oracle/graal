// run-spec: a library for declaratively describing CI jobs
//
// See `docs/ci/run-spec.md`
//
local _impl      = import "run-spec-impl.libsonnet";
local examples   = (import "run-spec-examples.libsonnet").examples;
{
  // Supported platforms
  supported_oss_names:: ["linux", "darwin", "windows"],
  supported_archs_names:: ["amd64", "aarch64"],
  supported_jdks_names:: ["jdk17", "jdk19", "jdk20", "jdk21", "jdk-latest"],

  // This will turn a task dictionary into a list of build objects.
  process(task_dict)::
    local DESCRIPTION = {
      // This function will process the task dictionary and transform
      // it into a build list as expected by the CI system.
      // Every entry in the dictionary is composed of the top-level $.task_spec,
      // extended by $.platform_spec to add platform specific $.task_spec entries.
      //
      // A task dictionary has the following form:
      local EXAMPLE_TASK_DICT = {
        "task-name": $.task_spec({ /*...*/ }) + $.platform_spec(EXAMPLE_PLATFORM_SPEC_0)
      },
      // The (top-level) $.task_spec part describes what a task should do (e.g., set "run" or "setup").
      local SEE_ALSO_0 = [$.task_spec],
      // The $.platform_spec part describes on which platform to run a task (e.g., which os/arch/jdk and
      // targets such as gate, daily, etc. Both, $.task_spec and $.platform_spec, are composable
      // and can be specified multiple times.
      local EXAMPLE_PLATFORM_SPEC_0 = {
        linux: {
          amd64: {
            jdk20: $.task_spec({ /*...*/ })
          }
        }
      },
      // The "leafs" of the platform specification are again $.task_spec entries, which are
      // used to extend the $.task_spec specified on the top level.
      //
      // There is also a short-hand variant for nested entries:
      local EXAMPLE_PLATFORM_SPEC_1 = {
        linux_amd64_jdk20: $.task_spec({ /*...*/ })
      },
      // See the following for more details.
      local SEE_ALSO_1 = [examples.desugaring, $.platform_spec],
      //
      // See the following definitions for the details about each processing
      // stage. They are applied in the order of definition.
    };

    // Desugars short-hand notation and makes platform specification composable.
    // Short-hand notation allows using colon (":") separated keys
    // to simplify writing deeply nested specifications.
    // Also adds $.include to all leafs (i.e., objects with $.task_spec).
    //
    // Examples:
    assert examples.desugaring;
    assert examples.desugaring_includes;
    assert examples.desugaring_star;
    local after_desugar = _impl.desugar_task_dict(task_dict);

    // Pushes down partial platform specification to full os.arch.jdk level.
    // Also removes excluded entries.
    //
    // Examples:
    assert examples.pushdown;
    assert examples.pushdown_all_os_exclude;
    assert examples.pushdown_all_jdk_exclude;
    local after_pushdown = _impl.push_down_task_dict(after_desugar);

    // Expands the os.arch.jdk leafs with the variants (if specified).
    //
    // In addition, all variants get the `task_variant` property.
    //
    // Example:
    assert examples.expand_variants;
    local after_expand_variants = _impl.expand_variants_task_dict(after_pushdown);

    // Turns the platform specification into full job objects by adding the common build specification to the platform
    // specific ones.
    //
    // Also adds the task_name/os/arch/jdk properties to the build objects and applies multipliers.
    // See $.add_multiply
    //
    // Example:
    assert examples.multiply;
    local after_multiply = _impl.generate_builds_dict(after_expand_variants);

    // Applies the evaluate_late functions.
    //
    // The functions are applied in lexical order of their key.
    // See $.evaluate_late()
    //
    // Example:
    assert examples.evaluate_late;
    local after_evaluate_late = _impl.apply_evaluate_late(after_multiply);

    {
      // Functions to access the result.
      after_desugar(inc_hidden=false)::_impl._make_visible(after_desugar, inc_hidden=inc_hidden),
      after_pushdown(inc_hidden=false)::_impl._make_visible(after_pushdown, inc_hidden=inc_hidden),
      after_expand_variants(inc_hidden=false)::_impl._make_visible(after_expand_variants, inc_hidden=inc_hidden),
      after_multiply(inc_hidden=false)::_impl._make_visible(after_multiply, inc_hidden=inc_hidden),
      after_evaluate_late(inc_hidden=false)::_impl._make_visible(after_evaluate_late, inc_hidden=inc_hidden),

      // Shortcut for the final stage
      final::self.after_evaluate_late,

      // Array to simplify comparing results between stages
      stage:: [
        self.after_desugar,
        self.after_pushdown,
        self.after_expand_variants,
        self.after_multiply,
        self.after_evaluate_late,
      ],
      // The final job list
      list:: std.flattenArrays([
        after_evaluate_late[build]
        for build in std.objectFieldsAll(after_evaluate_late)
      ]),
    }
  ,

  // Configures a task. This is what will eventually make up the CI configuration.
  // The configuration will be stored in a predefined property. This simplifies the
  // processing and composition of jobs.
  task_spec(b):: _impl.task_spec(b),

  // Gets the build configuration for an object.
  get_task_spec(b):: _impl.get_task_spec(b),

  // Adds a run specification to a task.
  platform_spec(run_spec):: {
    // A run specification consists of $.task_spec entries in an <os>.<arch>.<jdk> hierarchy:
    local EXAMPLE_0 = {
      "linux": {
        "amd64": {
          "jdk20": $.task_spec({ /*...*/ })
        }
      }
    },
    // Not all levels need to be specified. In the example below, the $.task_spec is applied
    // to all supported jdk version:
    local EXAMPLE_1 = {
      "linux": {
        "amd64": $.task_spec({ /*...*/ })
      }
    },
    // More specific properties extend the less specific ones.
    //
    // There is also a short-hand notation of nested properties where the keys are separated by
    // colon (":"). The next example is equivalent to the one before:
    local EXAMPLE_2 = {
      linux_amd64: $.task_spec({ /*...*/ })
    },
    local SEE_ALSO_0 = [examples.desugaring],
    // Mixing short-hand notation and the long one is allowed, but the order of composition is not
    // obvious and therefore mixing is not recommended.
    //
    // "<all-os>", "<all-arch>", and "<all-jdk>" can be used as wildcard entries. Those are added
    // to all os/arch/jdks, even if there is a specific property defined. In that case, the "<all-*>"
    // is extended (+) by the specific one.
    //
    // The run specification may include a "variants" property on the top level. The "variants" entry is an
    // object where all properties describe an alternative os/arch/jdk structure. The main idea is to
    // run the task in an alternative configuration, e.g., selecting a different GC or running a benchmark
    // with different optimizations. The name of the variant (the keys in the "variants" object) will be
    // supplied to the $.task_spec objects when the variants are being resolved.
    local EXAMPLE_3 = {
      linux_amd64: $.task_spec({ /*...*/ }),
      variants: {
        my_variant: {
          linux_amd64: $.task_spec({ /*...*/ })
        }
      }
    },
    local SEE_ALSO_1 = [examples.expand_variants],
  } + _impl.platform_spec(run_spec),

  // Generates a variants entry using a feature map.
  // See the example for a detailed explanation.
  assert examples.generate_variants,
  assert examples.generate_variants_exclude,
  generate_variants(variant_spec, feature_map, order=null):: {
    local SEE_ALSO = [$.run_job],
  } + _impl.generate_variants(variant_spec, feature_map, order=order),

  // Registers a multiplier for the given task. This allows generating multiple jobs out of a single
  // task description. This is useful for splitting up long running jobs into several batches.
  // The parameter is an array of $.task_spec objects.
  // Every $.task_spec object in the array will yield a build object consisting of the common
  // $.task_spec extended by the config from the array.
  // This function should be added next to $.task_spec.
  add_multiply(arr)::
    local SEE_ALSO = [examples.multiply];
   _impl.add_multiply(arr),

  // Excludes a certain platform combination (os/arch/jdk) from being executed.
  // By default all os/arch/jdk combinations are *included*.
  // This property should be added next to $.task_spec and
  // used within a $.platform_spec specification. Using it next to the top-level $.task_spec
  // has no effect.
  exclude:: _impl.exclude,

  // Includes a certain platform combination that was excluded on a upper level.
  // This property should be added next to $.task_spec and
  // used within a $.platform_spec specification. Using it next to the top-level $.task_spec
  // has no effect.
  include:: _impl.include,

  // Add properties that need to be evaluated late
  evaluate_late(first, second=null):: {
    // This works around ordering issues. For example, an architecture needs to add a download, which depends
    // on the JDK version. However, the JDK definition might come after the platform definition. To avoid this,
    // the definition can be added to the `evaluate_late` field. The content of the `evaluate_late` field
    // (if it exists) will be added late when all other properties have been set.
    //
    // This function should be applied within a $.task_spec specification.
    //
    // The function accepts either one or two parameters. The canonical form is using a single parameter
    // which is an object of unary functions. The function will receive the current build object at
    // the time of evaluation. For example:
    local EVAL_LATE_EX1 = $.evaluate_late({"myname": function(b) { name: b.name + "myname" } }),
    // When using the two parameter form, the first parameter is the name of the evaluate late
    // function, and the second is the function itself. The follow example is equivalent
    // to the one before:
    local EVAL_LATE_EX2 = $.evaluate_late("myname", function(b) { name: b.name + "myname" }),
    // If the current build object is not needed, an object can be passed instead of a function.
    // This works for the one and two parameter form. So the following two examples are identical:
    local EVAL_LATE_EX3 = $.evaluate_late("myname", { myname: true }),
    local EVAL_LATE_EX4 = $.evaluate_late({"myname": { myname: true }}),
    //
    // The evaluate function are processed in lexical order of their name. The order
    // they are specified does not matter.
    local SEE_ALSO = [examples.evaluate_late],
  } + _impl.evaluate_late(first, second=second),
}
