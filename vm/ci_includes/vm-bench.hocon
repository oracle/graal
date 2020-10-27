builds += [
  ${bench_vm_linux} ${vm_bench_js_linux} {
    setup: ${common_vm.setup} [
      [set-export, VM_ENV, "${VM_ENV}-no_native"]
      ${mx_vm_common} [build]
    ]
    run: [
      ${mx_vm_common} [benchmark, --results-file, ${vm_bench_common.result_file}, "gu:*"]
      ${vm_bench_common.upload}
    ]
    name: bench-vm-ce-no-native-gu-linux
    timelimit: "1:00:00"
  }
]
