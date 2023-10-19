local r = import "run-spec.libsonnet";
local supported_oss_names = r.supported_oss_names;
local supported_archs_names = r.supported_archs_names;
local supported_jdks_names = r.supported_jdks_names;

local std_get = (import "../../ci/ci_common/common-utils.libsonnet").std_get;
{
  //
  local CONFIG_KEY = "<build-config>",
  local task_spec(b) = {[CONFIG_KEY]+: b},
  local get_task_spec(b) = std_get(b, CONFIG_KEY, default={}),
  task_spec:: task_spec,
  get_task_spec:: get_task_spec,
  //
  local MULTIPLIER_KEY = "<multiplier>",
  local add_multiply(fn_or_arr) = {
    local fn =
      if std.type(fn_or_arr) == "function" then
        fn_or_arr
      else if std.type(fn_or_arr) == "array" then
        function(b) fn_or_arr
      else
        error "expected parameter type 'function', got '%s'" % [std.type(fn_or_arr)],
    [MULTIPLIER_KEY]: fn,
  },
  add_multiply:: add_multiply,
  //
  local IGNORE_KEY = "<exclude>",
  local exclude = {[IGNORE_KEY]:true},
  local include = {[IGNORE_KEY]:false},
  local is_excluded(s) = std_get(s, IGNORE_KEY, default=false),
  exclude::exclude,
  include::include,
  //
  local PLATFORM_SPEC_KEY = "platform_spec",
  local context_variant(context, variant) = "%s[var:%s]" % [context, variant],
  local desugar_platform_spec(platform_spec, context=null) = {
    [PLATFORM_SPEC_KEY]+:: desugar_spec(platform_spec, context=context) +
      if std.objectHasAll(platform_spec, "variants") then
        {
          variants+:: std.foldl(
            function(acc, k) acc + {
              local v = platform_spec.variants[k],
              [k]+: desugar_spec(v, context=context_variant(context, k))
            },
            std.objectFieldsAll(platform_spec.variants),
            {}
          )
        }
      else
        {}
  },
  local platform_spec_fn_name = "_platfrom_spec_fn",
  platform_spec(platform_spec):: {
    // applying this is delayed so that we can annotate the desugaring with a context
    [platform_spec_fn_name]+: [function(context=null) desugar_platform_spec(platform_spec, context=context)]
  },
  get_platform_spec(platform_spec):: std_get(platform_spec, PLATFORM_SPEC_KEY, default={}),
  add_platform_spec(platform_spec):: { [PLATFORM_SPEC_KEY]+: platform_spec},
  //
  local platform_spec_fields = [
      "<all-os>",
      "<all-arch>",
      "<all-jdk>",
    ] + supported_oss_names + supported_archs_names + supported_jdks_names
  ,

  // generate variants
  generate_variants(s, feature_map, order=null)::
    local feature_order = if order != null then
      order
    else
      std.objectFieldsAll(feature_map);
    assert std.assertEqual(std.set(feature_order), std.set(std.objectFieldsAll(feature_map)));
    assert std.assertEqual(std.length(std.set(feature_order)), std.length(feature_order));
    assert std.length([x for x in feature_order if std.startsWith(x, "!")]) == 0 : "feature names must not start with '!': " + feature_order;
    local mergeArray(arr) = std.foldl( function(a, b) a + b, arr, {});
    local get_feature_value_pair(key) =
       local split = std.split(key, ":");
       {
         feature: split[0],
         values:
           // gather specified feature values
           local _values =
             assert std.length(split) > 1 : "feature map keys must have the form 'feature_name:feature_value', got '%s'" % [key];
             if std.length(split) == 2 && split[1] == "*" then
               std.objectFieldsAll(feature_map[self.feature])
             else
               split[1:std.length(split)]
             ;
           local excluded = [x[1:] for x in _values if std.startsWith(x, "!")];
           if std.length(excluded) == 0 then
             // inclusion only
             _values
           else
             // exclusion
             assert std.length(excluded) == std.length(_values) : "Cannot mix inclusion with exclusion (either all entries start with '!' or none): " + _values;
             std.setDiff(std.objectFieldsAll(feature_map[self.feature]), excluded)
         }
       ;
    local is_feature_desc(key) = std.member(feature_order, get_feature_value_pair(key).feature);
    // return a list of objects containing features and a spec
    local expand_features(obj, features={}) = std.flattenArrays([
      if is_feature_desc(field) then
        // feature description -> recurse
        local inner_obj = obj[field];
        local p = get_feature_value_pair(field);
        std.flattenArrays([expand_features(inner_obj, features + {[p.feature]: feature_value}) for feature_value in p.values])
      else
        // no feature description -> create features+spec object
        [{
          features: features,
          spec: { [field]+: obj[field] }
        }]
      for field in std.objectFieldsAll(obj)
    ]);
    // create a stable name from features
    local variant_name_from_features(features) = std.join("_", ["%s:%s" % [f, features[f]] for f in feature_order if std.member(std.objectFieldsAll(features), f)]);
    // turn the features list into a variants dictionary
    local features_to_variant(arr) = [
      {
        [variant_name_from_features(s.features)]+: s.spec
      }
      for s in arr
    ];
    // get feature spec
    local feature_spec(features) = mergeArray([feature_map[f][features[f]] for f in feature_order if std.member(std.objectFieldsAll(features), f)]);
    local get_feature_spec(arr) = mergeArray([
      {
        [variant_name_from_features(s.features)]: feature_spec(s.features)
      }
      for s in arr
    ]);
    $.platform_spec({
      local feature_list = expand_features(s),
      local variants_dict = mergeArray(features_to_variant(feature_list)),
      local feature_spec_dict = get_feature_spec(feature_list),
      variants+: {[variant]: feature_spec_dict[variant] + variants_dict[variant] for variant in std.objectFieldsAll(variants_dict)}
    })
  ,

  //
  local desugar_spec_impl(spec) =
    local _star_to_long(parts) = std.mapWithIndex(function(idx, part)
      if part == "*" then
        if idx == 0 then "<all-os>"
        else if idx == 1 then "<all-arch>"
        else if idx == 2 then "<all-jdk>"
        else part
      else
        part
      , parts);
    local _impl(parts, value) = {
      assert std.type(value) == "object" : "not an object %s (%s)" % [value, parts],
      [parts[0]]+:
      if std.length(parts) > 1 then
        _impl(parts[1:std.length(parts)], value)
      else
          value
    };
    std.foldl(
      function(acc, k) acc + _impl(_star_to_long(std.split(k, ":")), spec[k]),
      std.objectFieldsAll(spec),
      {}
    ),
  local add_includes(spec) =
    local includes_matrix =
      std.foldl(function(acc, e) acc + e,
        [
          local os_spec = std_get(spec, os, {});
          local arch_spec = std_get(os_spec, arch, {});
          local jdk_spec = std_get(arch_spec, jdk, {});
          (
          if !std.objectHasAll(os_spec, CONFIG_KEY) then {} else
            {
              [os]+: include
            }
          ) + (
          if !std.objectHasAll(arch_spec, CONFIG_KEY) then {} else
            {
              [os]+: {
                [arch]+: include
              }
            }
          ) + (
          if !std.objectHasAll(jdk_spec, CONFIG_KEY) then {} else
            {
              [os]+: {
                [arch]+: {
                  [jdk]+: include
                }
              }
            }
          )
        for os in supported_oss_names + ["<all-os>"]
        for arch in supported_archs_names + ["<all-arch>"]
        for jdk in supported_jdks_names + ["<all-jdk>"]
      ]
      ,
      {}
      )
    ;
     includes_matrix + spec
  ,
  local make_platform_spec_composable(spec, context=null) =
    local special_fields = [IGNORE_KEY, MULTIPLIER_KEY];
    local known_fields = std.set(platform_spec_fields + [CONFIG_KEY, "variants"] + special_fields);
    local _impl(key, obj, stack=[]) = if key == CONFIG_KEY then obj else
      assert std.type(obj) == "object" : "not an object '%s' (%s) (context: %s, stack: %s)" % [obj, std.type(obj), context, stack];
      local obj_fields = std.set(std.objectFieldsAll(obj));
      local union = std.setUnion(obj_fields, known_fields);
      local inter = std.setInter(obj_fields, known_fields);
      local diff = std.setDiff(obj_fields, known_fields);
      local isLeaf = key == CONFIG_KEY;
      local onlyMatrixKeys = union == known_fields;
      local noMatrixKeys = inter == [];
      if isLeaf then
        assert noMatrixKeys: "unexected platform spec keys in build definition %s (context: %s, stack: %s)" % [inter, context, stack];
        obj
      else
        assert onlyMatrixKeys : "unexpected keys in platform spec %s, expected %s (context: %s, stack: %s)" % [diff, known_fields, context, stack];
        std.foldl(function(acc, k) acc +
          if k == "variants" then
            // delete variants (to be added separately)
            {}
          else if std.member(special_fields, k) then
            {
              [k]: obj[k]
            }
          else
            {
              [k]+: _impl(k, obj[k], stack = stack + [k])
            }
          ,
          obj_fields,
          {}
        )
      ;
     _impl(null, spec),
  local _verify_desugared_task_dict_entry(obj, context=null) =
    local obj_fields = std.set(std.objectFieldsAll(obj));
    local known_fields = std.set(supported_oss_names + ["<all-os>", "<other-os>", "variants"]);
    local diff = std.setDiff(obj_fields, known_fields);
    assert std.length(diff) == 0 : "unexpected top-level fields in platform spec %s, expected %s (context: %s)" % [diff, known_fields, context];
    true
  ,
  local desugar_spec(spec, context=null) =
    local _desugared = desugar_spec_impl(spec);
    assert _verify_desugared_task_dict_entry(_desugared, context=context);
    local _composable = make_platform_spec_composable(_desugared, context=context);
    add_includes(_composable),
  assert std.assertEqual(
    desugar_spec({
      "linux:amd64:jdk19" : task_spec({ "bar": "pub" }),
      "linux:amd64" : task_spec({
        "foo": "baz",
      }),
      "linux": {
        "aarch64": task_spec("exclude"),
      }
    }, context="<assertion1>"),
    {
      "linux": {
        "amd64": {
          "<exclude>": false,
          "jdk19": {
            "<build-config>": {
              "bar" : "pub",
            },
            "<exclude>": false,
          },
          "<build-config>": {
            "foo": "baz",
          },
        },
        "aarch64": {
          "<build-config>": "exclude",
          "<exclude>": false,
        }
      }
    }
  ),
  local _verify_desugar_task_dict_entry(obj, context=null) =
    local obj_fields = std.set(std.objectFieldsAll(obj));
    local known_fields = std.set([CONFIG_KEY, MULTIPLIER_KEY, platform_spec_fn_name]);
    local diff = std.setDiff(obj_fields, known_fields);
    assert std.length(diff) == 0 : "unexpected fields %s (context: %s)" % [diff, context];
    true
  ,
  desugar_task_dict(task_dict)::
    assert std.type(task_dict) == "object" : "expected parameter type 'object', got '%s'" % [std.type(task_dict)];
  {
    [build]:
      local d = task_dict[build];
      assert _verify_desugar_task_dict_entry(d, context=build);
      local _platform_spec_fn = std_get(d, platform_spec_fn_name);
      local _desugared_spec = d +
        if _platform_spec_fn != null then
          // apply all functions
          std.foldl(
            function(acc, fn) acc + fn(context=build),
            _platform_spec_fn,
            {}
          )+ {[platform_spec_fn_name]: null}
        else
          {}
        ;
      std.foldl(
        function(acc, k) acc +
          if k == platform_spec_fn_name then
            {}
          else
            {[k]+: _desugared_spec[k]},
        std.objectFieldsAll(_desugared_spec),
        {}
      )
    for build in std.objectFieldsAll(task_dict)
  },
  //
  push_down_task_dict(task_dict)::
    local add_spec(os_arch_jdk_spec, os, arch, jdk) =
      local _get_helper(spec, field, field_name) =
        local _all = std_get(spec, "<all-%s>" % field_name, default={});
        local _other = std_get(spec, "<other-%s>" % field_name, default={});
        local _field = std_get(spec, field, default=_other);
        _all + _field
      ;
      local arch_jdk_spec = _get_helper(os_arch_jdk_spec, os, "os");
      local jdk_spec = _get_helper(arch_jdk_spec, arch, "arch");
      local _spec = _get_helper(jdk_spec, jdk, "jdk");
      local x = arch_jdk_spec + jdk_spec + _spec;
      local _removed_fields = platform_spec_fields;
      x + {
        [f]:{}
        for f in _removed_fields
      }
    ;
    local filter_fields(s) =
      local special_fields = [CONFIG_KEY, MULTIPLIER_KEY];
      std.foldl(function(acc, k) acc +
        if std.member(special_fields, k) then
          { [k]+: s[k]}
        else
          {}
        ,
        std.objectFieldsAll(s),
        {}
      )
    ;
    local push_down_platform_spec(platform_spec) =
      std.foldl(function(acc, e) acc + e,
        [
          local s = add_spec(platform_spec, os, arch, jdk);
          if is_excluded(s) then {} else
          {
            [os]+: {
              [arch]+: {
                [jdk]+: filter_fields(s)
              }
            }
          }
        for os in supported_oss_names
        for arch in supported_archs_names
        for jdk in supported_jdks_names
      ]
      ,
      {}
      )
    ;
  {
    [build]: task_dict[build] + {
      [PLATFORM_SPEC_KEY]:
        local _spec = std_get(task_dict[build], PLATFORM_SPEC_KEY);
        assert _spec != null : "build has no platform spec: %s" % [build];
        push_down_platform_spec(_spec) +
        if std.objectHasAll(_spec, "variants") then
          {
            variants:: {
              local v = _spec.variants[k],
              [k]: push_down_platform_spec(v)
              for k in std.objectFieldsAll(_spec.variants)
            }
          }
        else
          {}
    }
    for build in std.objectFieldsAll(task_dict)
  },
  //
  expand_variants_task_dict(task_dict)::
    local make_array_arch(platform_spec_arch, extra_spec) = std.foldl(
      function(acc, jdk) acc + {
        [jdk]+: [platform_spec_arch[jdk] + extra_spec]
      },
      [jdk for jdk in supported_jdks_names if std.objectHasAll(platform_spec_arch, jdk)],
      {}
    );
    local make_array_os(platform_spec_os, extra_spec) = std.foldl(
      function(acc, arch) acc + {
        [arch]+: make_array_arch(platform_spec_os[arch], extra_spec)
      },
      [arch for arch in supported_archs_names if std.objectHasAll(platform_spec_os, arch)],
      {}
    );
    local make_array(platform_spec, extra_spec={}) = std.foldl(
      function(acc, os) acc + {
        [os]+: make_array_os(platform_spec[os], extra_spec)
      },
      [os for os in supported_oss_names if std.objectHasAll(platform_spec, os)],
      {}
    );
  {
    [build]: task_dict[build] + {
      local _spec = task_dict[build][PLATFORM_SPEC_KEY],
      local _variants = std_get(_spec, "variants", {}),
      local _base_spec = make_array(_spec + { variants::{} }),
      [PLATFORM_SPEC_KEY]:
        std.foldl(
          function(acc, k)
            // add the task_variant property
            acc + make_array(_variants[k], task_spec({task_variant::k})),
          std.objectFieldsAll(_variants),
          _base_spec
          ),
    }
    for build in std.objectFieldsAll(task_dict)
  },
  //
  generate_builds_dict(task_dict)::
    local expand_build(b) =
      local _multiplier = std_get(b, MULTIPLIER_KEY);
      if _multiplier != null then
        [b + {[MULTIPLIER_KEY]::null} + x for x in _multiplier(b)]
      else
        [b]
    ;
  {
    local platform_spec = task_dict[build][PLATFORM_SPEC_KEY],
    [build]: [x[CONFIG_KEY] for x in std.flattenArrays(
    [
      expand_build(
        // add the common build spec
        task_dict[build] +
        // add the os/arch/jdk specific spec
        _spec +
        // provide the task_name/os/arch/jdk definitons to the spec
        task_spec({
          task_name:: build,
          os:: os,
          arch:: arch,
          jdk:: jdk,
        })
      )
      for os in supported_oss_names if std.objectHasAll(platform_spec, os)
      for arch in supported_archs_names if std.objectHasAll(platform_spec[os], arch)
      for jdk in supported_jdks_names if std.objectHasAll(platform_spec[os][arch], jdk)
      for _spec in platform_spec[os][arch][jdk]
    ])]
    for build in std.objectFieldsAll(task_dict)
  },
  //
  evaluate_late(first, second=null)::
    local evaluate_late_dict(d) = {
      assert std.type(d) == "object" : "expected parameter type 'object', got '%s'" % [std.type(d)],
      evaluate_late+:: {
        local v = d[k],
        [k]:
          if std.type(v) == "function" then
            v
          else if std.type(v) == "object" then
            function(_) v
          else
            error "expected 'function' or 'object' for entry %s, got %s" % [k, std.type(v)],
        for k in std.objectFieldsAll(d)
      },
    };
    if second != null then
      assert std.type(first) == "string" : "expected parameter type 'string', got '%s'" % [std.type(first)];
      evaluate_late_dict({[first]: second})
    else
      evaluate_late_dict(first)
  ,
  // Add properties that need to be evaluated late
  //
  // This works around ordering issues. For example, a platform needs to add a download, which depdends
  // on the JDK version. However, the JDK definition might come after the platform definition. To avoid this,
  // the definition can be added to the `evaluate_late` field. The content of the `evaluate_late` field
  // (if it exists) will be added late when all other properties have been set.
  apply_evaluate_late(task_dict):: {
    local evaluate_late_impl(b) =
      if !std.objectHasAll(b, "evaluate_late") then b else
        std.foldl(
          function(acc, eval_late_name)
            acc + b.evaluate_late[eval_late_name](acc),
            std.objectFieldsAll(b.evaluate_late),
            b
        )
    ,
    [build]: [
      evaluate_late_impl(x)
      for x in task_dict[build]
     ]
    for build in std.objectFieldsAll(task_dict)
  },
  //
  local _make_visible(o, inc_hidden=true) =
    local objectFields = if inc_hidden then std.objectFieldsAll else std.objectFields;
    if std.type(o) == "array" then
      [_make_visible(e) for e in o]
    else if std.type(o) == "object" then
      {
        [key] : _make_visible(o[key])
        for key in objectFields(o)
      }
    else if std.type(o) == "function" then
      "<function>"
    else
      o
    ,
  _make_visible::_make_visible,
}
