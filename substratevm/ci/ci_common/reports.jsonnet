local run_spec_tools = import "../../../ci/ci_common/run-spec-tools.libsonnet";
local ci = import "../ci.jsonnet";
{
  // return a table of the target, convert to CSV using `jq -r ".[] | @csv"`
  //
  // convert to CSV:
  //   jsonnet -e '(import "reports.jsonnet").targets' | jq -r ".[] | @csv"
  targets::run_spec_tools.target_table(ci.processed_builds),
  targets_with_variants::run_spec_tools.target_table(ci.processed_builds, with_variants=true),
}