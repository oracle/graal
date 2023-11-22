local run_spec_tools = import "../../../ci/ci_common/run-spec-tools.libsonnet";
local ci = import "../ci.jsonnet";
local std_get = (import "../../../ci/ci_common/common-utils.libsonnet").std_get;
{
  local get_gate_tags(spec) =
    std.join(" ", std_get(spec, "mxgate_tags", ["<unavailable>"]))
  ,
  // filter JDK 19 and windows-aarch64 jobs
  local filter(matrix) =
    local mask = [!std.endsWith(x, 'jdk19') && !std.startsWith(x, 'windows-aarch64') for x in matrix[0]];
    [
      [
        row[col_idx]
      for col_idx in std.range(0, std.length(row) - 1) if mask[col_idx]]
    for row in matrix]
  ,
  // return a table of the target, convert to CSV using `jq -r ".[] | @csv"`
  //
  // convert to CSV:
  //   jsonnet -e '(import "reports.jsonnet").targets' | jq -r ".[] | @csv"
  targets::filter(run_spec_tools.target_table(ci.processed_builds, task_details_title="gate tags", task_details_factory=get_gate_tags)),
  targets_with_variants::filter(run_spec_tools.target_table(ci.processed_builds, with_variants=true, task_details_title="gate tags", task_details_factory=get_gate_tags)),
}
