local vm = import 'vm.jsonnet';
local vm_common = import '../ci_common/common.jsonnet';
local vm_common_bench = import '../ci_common/common-bench.jsonnet';

{
  local builds = [
    vm_common.bench_ondemand_vm_linux_amd64 +  vm_common_bench.vm_bench_js_linux_amd64() + {
     # Override `self.vm_bench_js_linux_amd64.setup`
     setup: vm.vm_setup.setup + [
       ['set-export', 'VM_ENV', '${VM_ENV}-no_native'],
       vm_common.mx_vm_common + ['build'],
     ],
     # Override `self.vm_bench_js_linux_amd64.run`
     run: [
       vm_common.mx_vm_common + ['benchmark', '--results-file', vm_common_bench.vm_bench_common.result_file, 'gu:*'],
       vm_common_bench.vm_bench_common.upload,
     ],
     name: 'ondemand-bench-vm-ce-no-native-gu-linux-amd64',
     timelimit: '1:00:00',
    },
  ],

  builds: [{'defined_in': std.thisFile} + b for b in builds],
}