local utils = import '../../../ci/ci_common/common-utils.libsonnet';
local vm = import 'vm.jsonnet';
local vm_common = import '../ci_common/common.jsonnet';
local vm_common_bench = import '../ci_common/common-bench.jsonnet';

{
  local builds = [],

  builds: utils.add_defined_in(builds, std.thisFile)
}
