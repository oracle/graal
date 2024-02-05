local ci = import "ci.jsonnet";
local galahad = import "ci/ci_common/galahad-common.libsonnet";
ci + {
  builds: galahad.filter_builds(ci.builds)
}