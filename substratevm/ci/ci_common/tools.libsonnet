{
  // Verifies that the CI job names match the single mx gate tag name.
  //
  // It only checks single tag jobs for name (build tags are ignored).
  // Returns a list of error messages.
  check_single_tag_names(builds)::
    local _errors = std.filter(function(x) x != false, [
      // ignore build tags
      local _tags_without_build = if std.length(b.mxgate_tags) == 1 then
          b.mxgate_tags
        else
          std.filter(function(x) x != "build" && x != "ecjbuild", b.mxgate_tags);
      // convert mx gate tags to names
      local _tag_to_name(tag) =
        // replace "_" in tags with "-" in CI job names
        local _canonical_tag = std.strReplace(tag, "_", "-");
        _canonical_tag;
      // only check single tag jobs
      if std.length(_tags_without_build) > 1 || b.mxgate_name == _tag_to_name(_tags_without_build[0]) then
        false
      else
        "Job name '%s' does not match tag '%s' (%s)" % [b.mxgate_name, _tag_to_name(_tags_without_build[0]), b.name]
      for b in builds
    ]);
    _errors
  ,
  // Verifies that gates with the same tags are named the same.
  //
  // Returns a list of error messages.
  check_tags_canonical(builds)::
    local _tags_id(b) = std.join(",", std.set(b.mxgate_tags));
    local _canonical_names = {
      [_tags_id(b)]: b.mxgate_name
      for b in std.set(builds, keyF=_tags_id)
    };
    local _not_canonical_name(b) = _canonical_names[_tags_id(b)] != b.mxgate_name;
    [
      "Job name '%s' with tags '%s' does not match other job with the same tags and name '%s'" % [b.mxgate_name, _tags_id(b), _canonical_names[_tags_id(b)]]
      for b in std.filter(_not_canonical_name, builds)
    ]
  ,
  // Verifies that the CI job names are sane.
  //
  // This means
  // * All jobs with the same tags should have the same name
  // * All jobs with single tag should be names after that tag
  //
  // Returns `true` if all names are Ok, or raises an `error` if there are issues.
  check_names(builds)::
    local _errors = self.check_single_tag_names(builds) + self.check_tags_canonical(builds);
    if std.length(_errors) == 0 then
      true
    else
      error "Name Errors:\n  " + std.join("\n  ", _errors)
  ,
  // std.get is not available in all versions
  std_get::(import '../../common-utils.libsonnet').std_get,
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
