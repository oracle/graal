local vm = import '../ci_includes/vm.jsonnet';
local vm_common = import '../ci_common/common.jsonnet';

{
  vm_bench_common: {
    result_file:: 'results.json',
    upload:: ['bench-uploader.py', self.result_file],
    capabilities+: ['tmpfs25g', 'x52'],
    timelimit: '1:30:00',
  },

  vm_bench_js_linux_amd64(bench_suite=null): vm.vm_java_17 + vm_common.svm_common_linux_amd64 + vm_common.sulong_linux + vm.custom_vm_linux + self.vm_bench_common + {
    cmd_base:: vm_common.mx_vm_common + ['--dynamicimports', 'js-benchmarks', 'benchmark', '--results-file', self.result_file],
    config_base:: ['--js-vm=graal-js', '--js-vm-config=default', '--jvm=graalvm-${VM_ENV}-java${BASE_JDK_SHORT_VERSION}'],
    setup+: [
      vm_common.mx_vm_common + ['build'],
      ['git', 'clone', '--depth', '1', ['mx', 'urlrewrite', 'https://github.com/graalvm/js-benchmarks.git'], '../../js-benchmarks'],
    ],
    run+:
      if (bench_suite != null) then [
        self.cmd_base + [bench_suite + ':*', '--'] + self.config_base + ['--jvm-config=jvm'],
        $.vm_bench_common.upload,
        self.cmd_base + [bench_suite + ':*', '--'] + self.config_base + ['--jvm-config=native'],
        $.vm_bench_common.upload,
      ] else [],
  },

  vm_polybench_linux_commands: {
    base_cmd:: ['mx', '--env', 'polybench-${VM_ENV}'],
    interpreter_bench_cmd:: self.base_cmd + ['benchmark', 'polybench:~r[(compiler/.*)|(warmup/.*)]', '--results-file', $.vm_bench_polybench_linux_interpreter.result_file, '--', '--polybench-vm=graalvm-${VM_ENV}-java${BASE_JDK_SHORT_VERSION}'],
    compiler_bench_cmd:: self.base_cmd + ['benchmark', 'polybench:*[compiler/dispatch.js]', '--results-file', $.vm_bench_polybench_linux_compiler.result_file, '--', '--polybench-vm=graalvm-${VM_ENV}-java${BASE_JDK_SHORT_VERSION}'],
    warmup_bench_cmd:: self.base_cmd + ['benchmark', '--fork-count-file', 'ci_common/benchmark-forks.json',  'polybench:r[warmup/.*]', '--results-file', $.vm_bench_polybench_linux_warmup.result_file, '--', '--polybench-vm=graalvm-${VM_ENV}-java${BASE_JDK_SHORT_VERSION}'],
  },

  vm_bench_polybench_linux_common: vm_common.svm_common_linux_amd64 + vm_common.truffleruby_linux + vm.custom_vm_linux + self.vm_bench_common + {
    downloads+: {
      WABT_DIR: {name: 'wabt', version: '1.0.12', platformspecific: true},
    },
    setup+: [
      $.vm_polybench_linux_commands.base_cmd + ['build'],
      $.vm_polybench_linux_commands.base_cmd + ['build', '--dependencies=POLYBENCH_BENCHMARKS'],
    ],
    notify_emails: [ 'aleksandar.prokopec@oracle.com' ],
  },

  vm_bench_polybench_linux_interpreter: self.vm_bench_polybench_linux_common + vm.vm_java_17 + {
    run+: [
      $.vm_polybench_linux_commands.interpreter_bench_cmd + ['--polybench-vm-config=jvm-interpreter'],
      $.vm_bench_common.upload,
      $.vm_polybench_linux_commands.interpreter_bench_cmd + ['--polybench-vm-config=native-interpreter'],
      $.vm_bench_common.upload,
    ],
    timelimit: '2:00:00',
  },

  vm_bench_polybench_linux_compiler: self.vm_bench_polybench_linux_common + vm.vm_java_17 + {
    compiler_bench_cmd:: $.vm_polybench_linux_commands.compiler_bench_cmd + ['-w', '0', '-i', '10'],
    run+: [
      $.vm_bench_polybench_linux_compiler.compiler_bench_cmd + ['--polybench-vm-config=jvm-standard', '--metric=compilation-time'],
      $.vm_bench_common.upload,
      $.vm_bench_polybench_linux_compiler.compiler_bench_cmd + ['--polybench-vm-config=native-standard', '--metric=compilation-time'],
      $.vm_bench_common.upload,
      $.vm_bench_polybench_linux_compiler.compiler_bench_cmd + ['--polybench-vm-config=jvm-standard', '--metric=partial-evaluation-time'],
      $.vm_bench_common.upload,
      $.vm_bench_polybench_linux_compiler.compiler_bench_cmd + ['--polybench-vm-config=native-standard', '--metric=partial-evaluation-time'],
      $.vm_bench_common.upload,
    ],
  },

  vm_bench_polybench_linux_context_init: self.vm_bench_polybench_linux_common + vm.vm_java_17 + {
    bench_cmd:: $.vm_polybench_linux_commands.base_cmd + ['benchmark', '--fork-count-file', 'ci_common/benchmark-forks.json', 'polybench:*[interpreter/pyinit.py,interpreter/jsinit.js,interpreter/rbinit.rb]', '--results-file', self.result_file, '--', '-w', '0', '-i', '0', '--polybench-vm=graalvm-${VM_ENV}-java${BASE_JDK_SHORT_VERSION}'],
    run+: [
      self.bench_cmd + ['--polybench-vm-config=jvm-standard', '--metric=none'],
      $.vm_bench_common.upload,
      self.bench_cmd + ['--polybench-vm-config=native-standard', '--metric=none'],
      $.vm_bench_common.upload,
    ],
  },

  vm_bench_polybench_linux_warmup: self.vm_bench_polybench_linux_common + vm.vm_java_17 + {
    run+: [
      $.vm_polybench_linux_commands.warmup_bench_cmd + ['--polybench-vm-config=native-standard', '--metric=one-shot'],
      $.vm_bench_common.upload,
    ],
  },

  vm_bench_polybench_linux_allocated_bytes: self.vm_bench_polybench_linux_common + vm.vm_java_17 + {
    run+: [
      # We run the interprer benchmarks in both interprer and standard mode to compare allocation with and without compilation.
      $.vm_polybench_linux_commands.interpreter_bench_cmd + ['-w', '40', '-i', '10', '--polybench-vm-config=jvm-interpreter', '--metric=allocated-bytes'],
      $.vm_bench_common.upload,
      $.vm_polybench_linux_commands.interpreter_bench_cmd + ['-w', '40', '-i', '10', '--polybench-vm-config=jvm-standard', '--metric=allocated-bytes'],
      $.vm_bench_common.upload,
      $.vm_polybench_linux_commands.interpreter_bench_cmd + ['-w', '40', '-i', '10', '--polybench-vm-config=native-interpreter', '--metric=allocated-bytes'],
      $.vm_bench_common.upload,
      $.vm_polybench_linux_commands.interpreter_bench_cmd + ['-w', '40', '-i', '10', '--polybench-vm-config=native-standard', '--metric=allocated-bytes'],
      $.vm_bench_common.upload,
    ],
    timelimit: '4:00:00',
  },

  vm_gate_polybench_linux: self.vm_bench_polybench_linux_common + vm.vm_java_17 + {
    interpreter_bench_cmd:: $.vm_polybench_linux_commands.interpreter_bench_cmd + ['-w', '1', '-i', '1'],
    compiler_bench_cmd:: $.vm_polybench_linux_commands.compiler_bench_cmd + ['-w', '0', '-i', '1'],
    warmup_bench_cmd:: $.vm_polybench_linux_commands.warmup_bench_cmd + ['-w', '1', '-i', '1'],
    run+: [
      self.interpreter_bench_cmd + ['--polybench-vm-config=jvm-interpreter'],
      self.interpreter_bench_cmd + ['--polybench-vm-config=native-interpreter'],
      self.compiler_bench_cmd + ['--polybench-vm-config=jvm-standard', '--metric=compilation-time'],
      self.compiler_bench_cmd + ['--polybench-vm-config=native-standard', '--metric=partial-evaluation-time'],
      self.warmup_bench_cmd + ['--polybench-vm-config=native-standard', '--metric=one-shot'],
    ],
    timelimit: '1:30:00',
    notify_emails: [],
  },

  vm_bench_polybench_nfi: {
    base_cmd:: ['mx', '--env', 'polybench-nfi-${VM_ENV}'],
    bench_cmd:: self.base_cmd + ['benchmark', 'polybench:r[nfi/.*]', '--results-file', $.vm_bench_common.result_file, '--', '--polybench-vm=graalvm-${VM_ENV}-java${BASE_JDK_SHORT_VERSION}'],
    setup+: [
      self.base_cmd + ['build'],
      self.base_cmd + ['build', '--dependencies=POLYBENCH_BENCHMARKS'],
    ],
    run+: [
      self.bench_cmd + ['--polybench-vm-config=jvm-standard'],
      $.vm_bench_common.upload,
      self.bench_cmd + ['--polybench-vm-config=native-standard'],
      $.vm_bench_common.upload,
    ],
    notify_groups:: ['sulong'],
    timelimit: '55:00',
  },

  vm_bench_polybench_nfi_linux_amd64: self.vm_bench_common + vm_common.svm_common_linux_amd64 + self.vm_bench_polybench_nfi,

  local builds = [
    # We used to expand `${common_vm_linux}` here to work around some limitations in the version of pyhocon that we use in the CI
    vm_common.bench_ondemand_vm_linux_amd64 + self.vm_bench_js_linux_amd64('octane')     + {name: 'ondemand-bench-vm-' + vm.vm_setup.short_name + '-js-octane-linux-amd64'},
    vm_common.bench_ondemand_vm_linux_amd64 + self.vm_bench_js_linux_amd64('jetstream')  + {name: 'ondemand-bench-vm-' + vm.vm_setup.short_name + '-js-jetstream-linux-amd64'},
    vm_common.bench_ondemand_vm_linux_amd64 + self.vm_bench_js_linux_amd64('jetstream2') + {name: 'ondemand-bench-vm-' + vm.vm_setup.short_name + '-js-jetstream2-linux-amd64'},
    vm_common.bench_ondemand_vm_linux_amd64 + self.vm_bench_js_linux_amd64('micro')      + {name: 'ondemand-bench-vm-' + vm.vm_setup.short_name + '-js-micro-linux-amd64'},
    vm_common.bench_ondemand_vm_linux_amd64 + self.vm_bench_js_linux_amd64('v8js')       + {name: 'ondemand-bench-vm-' + vm.vm_setup.short_name + '-js-v8js-linux-amd64'},
    vm_common.bench_ondemand_vm_linux_amd64 + self.vm_bench_js_linux_amd64('misc')       + {name: 'ondemand-bench-vm-' + vm.vm_setup.short_name + '-js-misc-linux-amd64'},
    vm_common.bench_ondemand_vm_linux_amd64 + self.vm_bench_js_linux_amd64('npm-regex')  + {name: 'ondemand-bench-vm-' + vm.vm_setup.short_name + '-js-npm-regex-linux-amd64'},

    vm_common.bench_daily_vm_linux_amd64 + self.vm_bench_polybench_linux_interpreter     + {name: 'daily-bench-vm-' + vm.vm_setup.short_name + '-polybench-linux-amd64'},
    vm_common.bench_daily_vm_linux_amd64 + self.vm_bench_polybench_linux_compiler        + {name: 'daily-bench-vm-' + vm.vm_setup.short_name + '-polybench-compiler-linux-amd64'},
    vm_common.bench_daily_vm_linux_amd64 + self.vm_bench_polybench_linux_context_init    + {name: 'daily-bench-vm-' + vm.vm_setup.short_name + '-polybench-context-init-linux-amd64'},
    vm_common.bench_daily_vm_linux_amd64 + self.vm_bench_polybench_linux_warmup          + {name: 'daily-bench-vm-' + vm.vm_setup.short_name + '-polybench-warmup-linux-amd64'},
    vm_common.bench_daily_vm_linux_amd64 + self.vm_bench_polybench_linux_allocated_bytes + {name: 'daily-bench-vm-' + vm.vm_setup.short_name + '-polybench-allocated-bytes-linux-amd64'},

    vm_common.bench_daily_vm_linux_amd64 + self.vm_bench_polybench_nfi_linux_amd64 + vm.vm_java_11 + {name: 'daily-bench-vm-' + vm.vm_setup.short_name + '-polybench-nfi-java11-linux-amd64'},
    vm_common.bench_daily_vm_linux_amd64 + self.vm_bench_polybench_nfi_linux_amd64 + vm.vm_java_17 + {name: 'daily-bench-vm-' + vm.vm_setup.short_name + '-polybench-nfi-java17-linux-amd64'},

    vm_common.bench_daily_vm_linux_amd64 + self.vm_bench_js_linux_amd64() + {
      # Override `self.vm_bench_js_linux_amd64.run`
      run: [
        vm_common.mx_vm_common + ['benchmark', '--results-file', self.result_file, 'agentscript-graal-js:*', '--', '--jvm=graalvm-${VM_ENV}-java17', '--jvm-config=jvm', '--js=graal-js', '--js-config=default'],
        $.vm_bench_common.upload,
        vm_common.mx_vm_common + ['benchmark', '--results-file', self.result_file, 'agentscript-graal-js:*', '--', '--jvm=graalvm-${VM_ENV}-java17', '--jvm-config=native', '--js=graal-js', '--js-config=default'],
        $.vm_bench_common.upload,
      ],
      timelimit: '45:00',
      name: 'daily-bench-vm-' + vm.vm_setup.short_name + '-agentscript-js-java17-linux-amd64',
    },

    vm_common.gate_vm_linux_amd64 + self.vm_gate_polybench_linux + {name: 'gate-vm-' + vm.vm_setup.short_name + '-polybench-linux-amd64'},
  ],

  builds: [{'defined_in': std.thisFile} + b for b in builds],
}
