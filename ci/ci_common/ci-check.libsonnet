local std_get = (import 'common-utils.libsonnet').std_get;

{
  # check that all non [gate, ondemand] entries have notify_emails or notify_groups defined
  local missing_notify(builds) = {
    [x.name]: std_get(x, "defined_in") for x in builds if !std.objectHas(x, "notify_emails") && !std.objectHasAll(x, "notify_groups") && (std.member(x.targets, "daily") || std.member(x.targets, "weekly") || std.member(x.targets, "monthly"))
  },
  
  verify_ci(builds)::
    local missingNotify = missing_notify(builds);
    assert std.length(missingNotify) == 0 : "Missing notify_emails or notify_groups: " + missingNotify;
    true
  ,
}
