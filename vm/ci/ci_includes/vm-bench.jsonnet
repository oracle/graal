local vm = import 'vm.jsonnet';
local vm_common = import '../ci_common/common.jsonnet';
local vm_common_bench = import '../ci_common/common-bench.jsonnet';

{
  local builds = [],

  builds: [{'defined_in': std.thisFile} + b for b in builds],
}