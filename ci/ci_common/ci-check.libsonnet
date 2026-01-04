local ci = import '../../ci.jsonnet';
local std_get = (import 'common-utils.libsonnet').std_get;

{
  local effective_targets(build) = std.map(function(target) if std.startsWith(target, "tier") then ci.tierConfig[target] else target, build.targets),
  local periodic_targets = ["daily", "weekly", "monthly", "post-merge", "opt-post-merge"],
  local has_periodic_target(build) = std.length(std.setInter(std.set(effective_targets(build)), std.set(periodic_targets))) != 0,

  # check that all non [gate, ondemand] entries have notify_emails or notify_groups defined
  local missing_notify(builds) = {
    [x.name]: {defined_in: std_get(x, "defined_in"), targets: std_get(x, "targets")}
    for x in builds if !std.objectHas(x, "notify_emails") && !std.objectHasAll(x, "notify_groups") && has_periodic_target(x)
  },

  # check that all entries have defined_in set
  local missing_defined_in(builds) = {
    [x.name]: std_get(x, "defined_in") for x in builds if !std.objectHas(x, "defined_in")
  },
  
  verify_ci(builds)::
    local missingNotify = missing_notify(builds);
    assert std.length(missingNotify) == 0 : "Missing notify_emails or notify_groups: " + missingNotify;

    local missingDefinedIn = missing_defined_in(builds);
    assert std.length(missingDefinedIn) == 0 : "Missing defined_in: " + missingDefinedIn;
    true
  ,
}
