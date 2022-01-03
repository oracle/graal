vm_bench_common: {
  result_file: results.json
  upload: [bench-uploader.py, ${vm_bench_common.result_file}]
  capabilities: [no_frequency_scaling, tmpfs25g, x52, linux, amd64]
  targets: [bench]
  timelimit: "1:30:00"
}

vm_bench_js_linux: ${vm_java_17} ${svm-common-linux-amd64} ${sulong_linux} ${custom_vm_linux} ${vm_bench_common} {
  cmd_base: ${mx_vm_common} [--dynamicimports, js-benchmarks, benchmark, --results-file, ${vm_bench_common.result_file}]
  config_base: ["--js-vm=graal-js", "--js-vm-config=default", "--jvm=graalvm-${VM_ENV}-java${BASE_JDK_SHORT_VERSION}"]
  cmd: ${vm_bench_js_linux.cmd_base} ["${BENCH_SUITE}:*", --] ${vm_bench_js_linux.config_base}
  setup: ${common_vm.setup} [
    ${mx_vm_common} [build]
    [git, clone, --depth, "1", [mx, urlrewrite, "https://github.com/graalvm/js-benchmarks.git"], ../../js-benchmarks]
  ]
  run: [
      ${vm_bench_js_linux.cmd} ["--jvm-config=jvm"]
      ${vm_bench_common.upload}
      ${vm_bench_js_linux.cmd} ["--jvm-config=native"]
      ${vm_bench_common.upload}
  ]
}

vm_polybench_linux_commands: {
  base_cmd: [mx, --env, "polybench-${VM_ENV}"]
  interpreter_bench_cmd: ${vm_polybench_linux_commands.base_cmd} [benchmark, "polybench:~r[(compiler/.*)|(warmup/.*)]", --results-file, ${vm_bench_polybench_linux_interpreter.result_file}, --, "--polybench-vm=graalvm-${VM_ENV}-java${BASE_JDK_SHORT_VERSION}"]
  compiler_bench_cmd: ${vm_polybench_linux_commands.base_cmd} [benchmark, "polybench:*[compiler/dispatch.js]", --results-file, ${vm_bench_polybench_linux_compiler.result_file}, --, "--polybench-vm=graalvm-${VM_ENV}-java${BASE_JDK_SHORT_VERSION}"]
  warmup_bench_cmd: ${vm_polybench_linux_commands.base_cmd} [benchmark, "--fork-count-file", "ci_common/benchmark-forks.json",  "polybench:r[warmup/.*]", --results-file, ${vm_bench_polybench_linux_warmup.result_file}, --, "--polybench-vm=graalvm-${VM_ENV}-java${BASE_JDK_SHORT_VERSION}"]
}

vm_bench_polybench_linux_common: ${svm-common-linux-amd64} ${truffleruby_linux} ${custom_vm_linux} ${vm_bench_common} {
  downloads: {
    WABT_DIR: {name: wabt, version: "1.0.12", platformspecific: true},
  }
  setup: ${common_vm.setup} [
    ${vm_polybench_linux_commands.base_cmd} [build]
    ${vm_polybench_linux_commands.base_cmd} [build, "--dependencies=POLYBENCH_BENCHMARKS"]
  ]
  notify_emails: [ "aleksandar.prokopec@oracle.com" ]
  targets: [bench, daily]
}

vm_bench_polybench_linux_interpreter: ${vm_bench_polybench_linux_common} ${vm_java_17} {
  run: [
    ${vm_polybench_linux_commands.interpreter_bench_cmd} ["--polybench-vm-config=jvm-interpreter"]
    ${vm_bench_common.upload}
    ${vm_polybench_linux_commands.interpreter_bench_cmd} ["--polybench-vm-config=native-interpreter"]
    ${vm_bench_common.upload}
  ]
  timelimit: "2:00:00"
}

vm_bench_polybench_linux_compiler: ${vm_bench_polybench_linux_common} ${vm_java_17} {
  compiler_bench_cmd: ${vm_polybench_linux_commands.compiler_bench_cmd} ["-w", "0", "-i", "10"]
  run: [
    ${vm_bench_polybench_linux_compiler.compiler_bench_cmd} ["--polybench-vm-config=jvm-standard", "--metric=compilation-time"]
    ${vm_bench_common.upload}
    ${vm_bench_polybench_linux_compiler.compiler_bench_cmd} ["--polybench-vm-config=native-standard", "--metric=compilation-time"]
    ${vm_bench_common.upload}
    ${vm_bench_polybench_linux_compiler.compiler_bench_cmd} ["--polybench-vm-config=jvm-standard", "--metric=partial-evaluation-time"]
    ${vm_bench_common.upload}
    ${vm_bench_polybench_linux_compiler.compiler_bench_cmd} ["--polybench-vm-config=native-standard", "--metric=partial-evaluation-time"]
    ${vm_bench_common.upload}
  ]
}

vm_bench_polybench_linux_context_init: ${vm_bench_polybench_linux_common} ${vm_java_17} {
  bench_cmd: ${vm_polybench_linux_commands.base_cmd} [benchmark, "--fork-count-file", "ci_common/benchmark-forks.json", "polybench:*[interpreter/pyinit.py,interpreter/jsinit.js,interpreter/rbinit.rb]", --results-file, ${vm_bench_polybench_linux_context_init.result_file}, --, "-w", "0", "-i", "0", "--polybench-vm=graalvm-${VM_ENV}-java${BASE_JDK_SHORT_VERSION}"]
  run: [
    ${vm_bench_polybench_linux_context_init.bench_cmd} ["--polybench-vm-config=jvm-standard", "--metric=none"]
    ${vm_bench_common.upload}
    ${vm_bench_polybench_linux_context_init.bench_cmd} ["--polybench-vm-config=native-standard", "--metric=none"]
    ${vm_bench_common.upload}
  ]
}

vm_bench_polybench_linux_warmup: ${vm_bench_polybench_linux_common} ${vm_java_17} {
  run: [
    ${vm_polybench_linux_commands.warmup_bench_cmd} ["--polybench-vm-config=native-standard", "--metric=one-shot"]
    ${vm_bench_common.upload}
  ]
}


vm_bench_polybench_linux_allocated_bytes: ${vm_bench_polybench_linux_common} ${vm_java_17} {
  run: [
    # The polybench allocated bytes metric does not run on native.
    # We run the interprer benchmarks in both interprer and standard mode to compare allocation with and without compilation.
    ${vm_polybench_linux_commands.interpreter_bench_cmd} ["-w", "40", "-i", "10", "--polybench-vm-config=jvm-interpreter", "--metric=allocated-bytes"]
    ${vm_bench_common.upload}
    ${vm_polybench_linux_commands.interpreter_bench_cmd} ["-w", "40", "-i", "10", "--polybench-vm-config=jvm-standard", "--metric=allocated-bytes"]
    ${vm_bench_common.upload}
  ]
  timelimit: "2:00:00"
}


vm_gate_polybench_linux: ${vm_bench_polybench_linux_common} ${vm_java_17} {
  interpreter_bench_cmd: ${vm_polybench_linux_commands.interpreter_bench_cmd} ["-w", "1", "-i", "1"]
  compiler_bench_cmd: ${vm_polybench_linux_commands.compiler_bench_cmd} ["-w", "0", "-i", "1"]
  warmup_bench_cmd: ${vm_polybench_linux_commands.warmup_bench_cmd} ["-w", "1", "-i", "1"]
  run: [
    ${vm_gate_polybench_linux.interpreter_bench_cmd} ["--polybench-vm-config=jvm-interpreter"]
    ${vm_gate_polybench_linux.interpreter_bench_cmd} ["--polybench-vm-config=native-interpreter"]
    ${vm_gate_polybench_linux.compiler_bench_cmd} ["--polybench-vm-config=jvm-standard", "--metric=compilation-time"]
    ${vm_gate_polybench_linux.compiler_bench_cmd} ["--polybench-vm-config=native-standard", "--metric=partial-evaluation-time"]
    ${vm_gate_polybench_linux.warmup_bench_cmd} ["--polybench-vm-config=native-standard", "--metric=one-shot"]
  ]
  timelimit: "1:00:00"
  targets: [gate]
  capabilities: ${vm_linux.capabilities}
  notify_emails: []
}

vm_bench_polybench_nfi: ${sulong-weekly-notifications} {
  base_cmd: [mx, --env, "polybench-nfi-${VM_ENV}"]
  bench_cmd: ${vm_bench_polybench_nfi.base_cmd} [benchmark, "polybench:r[nfi/.*]", --results-file, ${vm_bench_common.result_file}, --, "--polybench-vm=graalvm-${VM_ENV}-java${BASE_JDK_SHORT_VERSION}"]
  setup: ${common_vm.setup} [
    ${vm_bench_polybench_nfi.base_cmd} [build]
    ${vm_bench_polybench_nfi.base_cmd} [build, "--dependencies=POLYBENCH_BENCHMARKS"]
  ]
  run: [
    ${vm_bench_polybench_nfi.bench_cmd} ["--polybench-vm-config=jvm-standard"]
    ${vm_bench_common.upload}
    ${vm_bench_polybench_nfi.bench_cmd} ["--polybench-vm-config=native-standard"]
    ${vm_bench_common.upload}
  ]
  targets: [bench, daily]
}

vm_bench_polybench_nfi_linux_amd64: ${vm_bench_common} ${svm-common-linux-amd64} ${vm_bench_polybench_nfi}


builds += [
  # We need to expand `${common_vm_linux}` here to work around some limitations in the version of pyhocon that we use in the CI
  ${common_vm_linux} ${vm_bench_js_linux} { environment: { BENCH_SUITE: octane },    name: bench-vm-${vm_setup.short_name}-js-octane-linux},
  ${common_vm_linux} ${vm_bench_js_linux} { environment: { BENCH_SUITE: jetstream }, name: bench-vm-${vm_setup.short_name}-js-jetstream-linux}
  ${common_vm_linux} ${vm_bench_js_linux} { environment: { BENCH_SUITE: jetstream2}, name: bench-vm-${vm_setup.short_name}-js-jetstream2-linux}
  ${common_vm_linux} ${vm_bench_js_linux} { environment: { BENCH_SUITE: micro },     name: bench-vm-${vm_setup.short_name}-js-micro-linux}
  ${common_vm_linux} ${vm_bench_js_linux} { environment: { BENCH_SUITE: v8js },      name: bench-vm-${vm_setup.short_name}-js-v8js-linux}
  ${common_vm_linux} ${vm_bench_js_linux} { environment: { BENCH_SUITE: misc },      name: bench-vm-${vm_setup.short_name}-js-misc-linux}
  ${common_vm_linux} ${vm_bench_js_linux} { environment: { BENCH_SUITE: npm-regex }, name: bench-vm-${vm_setup.short_name}-js-npm-regex-linux}

  ${common_vm_linux} ${vm_bench_polybench_linux_interpreter} {name: bench-vm-${vm_setup.short_name}-polybench-linux}
  ${common_vm_linux} ${vm_bench_polybench_linux_compiler} {name: bench-vm-${vm_setup.short_name}-polybench-compiler-linux}
  ${common_vm_linux} ${vm_bench_polybench_linux_context_init} {name: bench-vm-${vm_setup.short_name}-polybench-context-init-linux}
  ${common_vm_linux} ${vm_bench_polybench_linux_warmup} {name: bench-vm-${vm_setup.short_name}-polybench-warmup-linux}
  ${common_vm_linux} ${vm_bench_polybench_linux_allocated_bytes} {name: bench-vm-${vm_setup.short_name}-polybench-allocated-bytes-linux}

  ${common_vm_linux} ${vm_bench_polybench_nfi_linux_amd64} ${vm_java_8} {name: bench-vm-${vm_setup.short_name}-polybench-nfi-linux-amd64-java8 }
  ${common_vm_linux} ${vm_bench_polybench_nfi_linux_amd64} ${vm_java_11} {name: bench-vm-${vm_setup.short_name}-polybench-nfi-linux-amd64-java11 }
  ${common_vm_linux} ${vm_bench_polybench_nfi_linux_amd64} ${vm_java_17} {name: bench-vm-${vm_setup.short_name}-polybench-nfi-linux-amd64-java17 }

  ${bench_daily_vm_linux} ${vm_bench_js_linux} {
    run: [
      ${mx_vm_common} [benchmark, --results-file, ${vm_bench_js_linux.result_file}, "agentscript-graal-js:*", --, "--jvm=graalvm-${VM_ENV}", "--jvm-config=jvm", "--js=graal-js", "--js-config=default"]
      ${vm_bench_common.upload}
      ${mx_vm_common} [benchmark, --results-file, ${vm_bench_js_linux.result_file}, "agentscript-graal-js:*", --, "--jvm=graalvm-${VM_ENV}", "--jvm-config=native", "--js=graal-js", "--js-config=default"]
      ${vm_bench_common.upload}
    ]
    targets: [bench, daily]
    timelimit: "45:00"
    name: bench-vm-${vm_setup.short_name}-agentscript-js-java17-linux-amd64
  }

  ${common_vm_linux} ${vm_gate_polybench_linux} {name: gate-vm-${vm_setup.short_name}-polybench-linux}
]
