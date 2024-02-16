local common_json = import "../../common.json";
local labsjdk_ce = common_json.jdks["labsjdk-ce-latest"];
local labsjdk_ee = common_json.jdks["labsjdk-ee-latest"];
local galahad_jdk = common_json.jdks["galahad-jdk"];
local utils = import "common-utils.libsonnet";
{
  local GALAHAD_PROPERTY = "_galahad_include",
  local GALAHAD_SKIP_JDK_CHECK_PROPERTY = "_galahad_skip_jdk_check",
  local arrContains(arr, needle) =
    std.find(needle, arr) != []
  ,
  # Return true if this is a gate job.
  local is_gate(b) =
    std.find("gate", b.targets) != []
  ,
  local gate_or_postmerge_targets = ["gate", "post-merge", "deploy"],
  # Return true if this is a gate or post-merge/deployment job.
  local is_gate_or_postmerge(b) =
    std.setInter(gate_or_postmerge_targets, b.targets) != []
  ,
  local finalize(b) = std.parseJson(std.manifestJson(b)),
  # Converts a gate job into an ondemand job.
  local convert_gate_to_ondemand(b) =
    assert is_gate_or_postmerge(b) : "Not a gate or postmerge job: " + b.name;
    b + {
      name: "non-galahad-" + b.name,
      # replace gate or postmerge targets with ondemand
      targets: std.set(std.setDiff(b.targets, gate_or_postmerge_targets) + ["ondemand"]),
      # remove runAfter
      runAfter: [],
    }
  ,
  local has_labsjdk_latest(b) =
    std.objectHas(b, "downloads") && std.objectHas(b.downloads, "JAVA_HOME") &&
        (b.downloads.JAVA_HOME == labsjdk_ce || b.downloads.JAVA_HOME == labsjdk_ee),
  # Replaces labsjdk-ce-latest and labsjdk-ee-latest with galahad-jdk
  local replace_labsjdk(b) =
    if has_labsjdk_latest(b) then
      b + {
        downloads+: {
          JAVA_HOME: galahad_jdk,
        }
      }
    else
      b
  ,
  # Transforms a job if it is not relevant for galahad.
  # Only gate jobs are touched.
  # Gate jobs that are not relevant for galahad are turned into ondemand jobs.
  # This is preferred over removing irrelevant jobs because it does not introduce problems
  # with respect to dependent jobs (artifacts).
  local transform_galahad_job(original_build) =
    local b = replace_labsjdk(original_build);
    if !is_gate_or_postmerge(b) then
      b
    else
      local include = std.foldr(function(x, y) x && y, utils.std_get(b, GALAHAD_PROPERTY, [false]), true);
      assert std.type(include) == "boolean" : "Not a boolean: " + std.type(include);
      if include then
        b
      else
        # We finalize (manifest json string and parse it again) the build object before
        # modifying it because modification could have side effects, e.g., if a job
        # uses the "name" property to change other properties. At the current level,
        # everything should be ready to finalize. If we mess up, we don't care too much
        # because these ondemand jobs are not really used in the galahad CI.
        local finalized_build = finalize(b) +
          # readd skip_jdk_check
          if utils.std_get(b, GALAHAD_SKIP_JDK_CHECK_PROPERTY, false) then
            $.skip_jdk_check
          else
            {}
          ;
        convert_gate_to_ondemand(finalized_build)
  ,
  # Verify that a job really makes sense for galahad
  local verify_galahad_job(b) =
      assert utils.contains(b.name, "style") || utils.std_get(b, GALAHAD_SKIP_JDK_CHECK_PROPERTY, false) ||
        !has_labsjdk_latest(b) : "Job %s is not using a jpg-jdk: %s" % [b.name, b.downloads.JAVA_HOME];
      b
  ,
  local replace_mx(b) =
    # Use the `galahad` branch of mx to ensure that it is sufficiently up to date.
    if std.objectHas(b, "packages") then
      if std.objectHas(b.packages, "mx") then
        b + {
          packages+: {
            mx: "galahad"
          }
        }
      else
        b
    else
      b
  ,

  ####### Public API

  # Include a jobs in the galahad gate
  include:: {
    # There seems to be a problem with sjsonnet when merging boolean fields.
    # Working a round by using arrays.
    [GALAHAD_PROPERTY]:: [true]
  },
  # Exclude a job in the galahad gate
  exclude:: {
    [GALAHAD_PROPERTY]:: [false]
  },

  # Skip JDK check (e.g. for style gates)
  skip_jdk_check:: {
    [GALAHAD_SKIP_JDK_CHECK_PROPERTY]:: true
  },

  # only returns jobs that are relevant for galahad
  filter_builds(builds):: [verify_galahad_job(replace_mx(transform_galahad_job(b))) for b in builds],
}
