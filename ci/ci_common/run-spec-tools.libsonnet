local r = import "run-spec.libsonnet";
local _impl = import "run-spec-impl.libsonnet";
local supported_oss_names = r.supported_oss_names;
local supported_archs_names = r.supported_archs_names;
local supported_jdks_names = r.supported_jdks_names;
local std_get = (import "../../ci/ci_common/common-utils.libsonnet").std_get;
{
  local map_os_arch_jdk(fn) =
    [fn(os, arch, jdk)
    for os in supported_oss_names
    for arch in supported_archs_names
    for jdk in supported_jdks_names
    ]
  ,
  local map_jdk_os_arch(fn) =
    [fn(os, arch, jdk)
    for jdk in supported_jdks_names
    for os in supported_oss_names
    for arch in supported_archs_names
    ]
  ,
  // return a table of the target, convert to CSV using `jq -r ".[] | @csv"`
  target_table(processed_spec, with_variants=false, with_empty=false, task_details_title="task details", task_details_factory=function(spec) "")::
    local map_matrix = map_jdk_os_arch;
    local task_dict = processed_spec.after_pushdown(inc_hidden=true);
    local platform_titles = map_matrix(function(os, arch, jdk) std.join("-", [os, arch, jdk]));
    local get_target(default_config, platform_spec, os, arch, jdk) =
      local os_spec = std_get(platform_spec, os);
      local arch_spec = std_get(os_spec, arch);
      local jdk_spec = std_get(arch_spec, jdk);
      if os_spec != null && arch_spec != null && jdk_spec != null then
        std_get(r.get_task_spec(jdk_spec), "target", default="<unavailable>")
      else
        ""
    ;
    local cols = std.flattenArrays([
      local default_config = r.get_task_spec(task_dict[build]);
      local as_variants = {
        "": _impl.get_platform_spec(task_dict[build])
      } + if with_variants then std.prune(std_get(_impl.get_platform_spec(task_dict[build]), "variants", default={})) else {};
      std.foldl(function(acc, variant) acc +
        local run_spec = as_variants[variant];
        local matrix = map_matrix(function(os, arch, jdk) get_target(default_config, run_spec, os, arch, jdk));
        local is_empty = std.set(matrix) == [""];
        if with_empty || !is_empty then
          [[build, variant, task_details_factory(default_config)] + matrix]
        else
          []
        ,
        std.objectFieldsAll(as_variants),
        [])
      for build in std.objectFieldsAll(task_dict)
    ]);
    local table = [ ["name", "variant", task_details_title] + platform_titles] + cols;
    table
  ,
  // Removes the 'timelimit' property from an object.
  // Usually, this is used to remove hard-coded (default) timelimits defined in `common.json`.
  // These definitions assume that the os/arch definition comes first and will be refined later.
  // With run-spec, however, this is not true in general because the os/arch is only fixed later
  // in the pipeline. Thus, hard-coded timelimits would override any previous settings. To resolve
  // this, we delete the default value altogether and explicitly set the timelimits for all jobs.
  //
  // Implementation note: we cannot set the value to `null` and use `std.prune` because that deletes hidden fields.
  delete_timelimit(b)::
    local public_fields = std.objectFields(b);
    std.foldl(function(acc, k) acc +
      local value = b[k];
      if std.member(public_fields, k) then
        if std.type(value) == "string" then
          { [k]: value }
        else
          { [k]+: value }
      else
        if std.type(value) == "string" then
          { [k]:: value }
        else
          { [k]+:: value }
      ,
      [k for k in std.objectFieldsAll(b) if k != "timelimit"],
      {}
    ),
}