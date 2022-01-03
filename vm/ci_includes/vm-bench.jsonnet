local vm_common = import '../ci_common/common.jsonnet';
local vm_common_bench = import '../ci_common/common-bench.jsonnet';

{
  builds: [
    vm_common.bench_vm_linux +  vm_common_bench.vm_bench_js_linux + {
      setup+: [
        ['set-export', 'VM_ENV', '${VM_ENV}-no_native'],
        vm_common.mx_vm_common + ['build'],
      ],
      run+: [
        vm_common.mx_vm_common + ['benchmark', '--results-file', vm_common_bench.vm_bench_common.result_file, 'gu:*'],
        vm_common_bench.vm_bench_common.upload,
      ],
      name: 'bench-vm-ce-no-native-gu-linux',
      timelimit: '1:00:00',
    },
  ],
}