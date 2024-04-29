# note: this file needs to be in sync between CE and EE

local vm = import '../ci_includes/vm.jsonnet';
local common = import '../../../ci/ci_common/common.jsonnet';
local vm_common = import '../ci_common/common.jsonnet';

local repo_config = import '../../../ci/repo-configuration.libsonnet';

{
  vm_bench_common: {
    result_file:: 'results.json',
    upload:: ['bench-uploader.py', self.result_file],
    upload_and_wait_for_indexing:: self.upload + ['--wait-for-indexing'],
    capabilities+: ['tmpfs25g', 'e3'],
    timelimit: '1:30:00',
  },

  vm_bench_js_linux_amd64(bench_suite=null): vm.vm_java_21 + vm_common.svm_common_linux_amd64 + vm_common.sulong_linux + vm.custom_vm_linux + self.vm_bench_common + {
    cmd_base:: vm_common.mx_vm_common + ['--dynamicimports', 'js-benchmarks', 'benchmark', '--results-file', self.result_file],
    config_base:: ['--js-vm=graal-js', '--js-vm-config=default', '--jvm=graalvm-${VM_ENV}'],
    setup+: [
      ['set-export', 'VM_ENV', '$VM_ENV-js'],
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

  polybench_hpc_linux_common: {
    packages+: {
      'papi': '==5.5.1',
    },
    environment+: {
      ENABLE_POLYBENCH_HPC: 'yes',
      POLYBENCH_HPC_EXTRA_HEADERS: '/cm/shared/apps/papi/papi-5.5.1/include',
      POLYBENCH_HPC_PAPI_LIB_DIR: '/cm/shared/apps/papi/papi-5.5.1/lib',
      LIBPFM_FORCE_PMU: 'amd64'
    },
  },

  vm_bench_polybenchmarks_base(env): {
    base_cmd:: ['mx', '--env', env, '--dy', 'polybenchmarks'],
  },

  vm_bench_polybenchmarks_linux_build: vm_common.svm_common_linux_amd64 + vm_common.truffleruby_linux_amd64 + vm.custom_vm_linux + self.vm_bench_common + vm.vm_java_21 + self.polybench_hpc_linux_common + self.vm_bench_polybenchmarks_base(env='polybench-${VM_ENV}') {
    setup+: [
      self.base_cmd + ['sforceimports'],
    ],
    run+: [
      self.base_cmd + ['build'],
    ],
    publishArtifacts+: [
      {
        name: "graalvm-with-polybench",
        dir: "..",
        patterns: [
          "*/*/mxbuild/*",
          "*/mxbuild/*",
        ]
      }
    ],
    targets+: ['ondemand'],
  },

  vm_bench_polybenchmarks_linux_common(vm_config='jvm', is_gate=false, suite='default:*'): vm_common.svm_common_linux_amd64 + vm_common.truffleruby_linux_amd64 + vm.custom_vm_linux + self.vm_bench_common + vm.vm_java_21 + self.polybench_hpc_linux_common + self.vm_bench_polybenchmarks_base(env='polybench-${VM_ENV}') {
    bench_cmd:: self.base_cmd + ['benchmark', '--results-file', self.result_file],
    setup+: [
      self.base_cmd + ['sforceimports'],
      ['unpack-artifact', 'graalvm-with-polybench'],
      ['mx', '-p', '../../polybenchmarks/', 'build_benchmarks'],
    ],
    requireArtifacts: [{
      name: 'graalvm-with-polybench',
      autoExtract: false,
      dir: '..',
    }],
    run+: if (is_gate) then  [
      self.bench_cmd + ['polybenchmarks-awfy:*',    '--', '--polybenchmark-vm=graalvm-${VM_ENV}', '--polybenchmark-vm-config=native', '--gate'],
      self.bench_cmd + ['polybenchmarks-awfy:*',    '--', '--polybenchmark-vm=graalvm-${VM_ENV}', '--polybenchmark-vm-config=jvm',    '--gate'],
      self.bench_cmd + ['polybenchmarks-default:*', '--', '--polybenchmark-vm=graalvm-${VM_ENV}', '--polybenchmark-vm-config=native', '--gate'],
      self.bench_cmd + ['polybenchmarks-default:*', '--', '--polybenchmark-vm=graalvm-${VM_ENV}', '--polybenchmark-vm-config=jvm',    '--gate'],
    ] else [
      self.bench_cmd + ['polybenchmarks-' + suite, '--', '--polybenchmark-vm=graalvm-${VM_ENV}', '--polybenchmark-vm-config=' + vm_config],
    ],
    notify_emails+: if (is_gate) then [] else [ 'boris.spasojevic@oracle.com' ],
    teardown+:      if (is_gate) then [] else [ $.vm_bench_common.upload ],
    timelimit:      if (is_gate) then '1:00:00' else '1:30:00',
  },

  local wabt = {
    packages+: {
      gcc: '==8.3.0',
    },
    downloads+: {
      WABT_DIR: {name: 'wabt', version: '1.0.32', platformspecific: true},
    },
    environment+: {
      WABT_DIR: '$WABT_DIR/bin',
    },
  },

  vm_bench_polybench_linux_common(env='polybench-${VM_ENV}', is_gate=false): vm_common.svm_common_linux_amd64 + vm_common.truffleruby_linux_amd64 + vm.custom_vm_linux + self.vm_bench_common + wabt {
    base_cmd:: ['mx', '--env', env],
    bench_cmd:: self.base_cmd + ['benchmark'] + (if (is_gate) then ['--fail-fast'] else []),
    interpreter_bench_cmd(vmConfig):: self.bench_cmd +
        (if std.startsWith(vmConfig, 'jvm-') then
            ['polybench:~r[(compiler/.*)|(warmup/.*)]']
        else
            ['polybench:~r[(compiler/.*)|(warmup/.*)|(.*panama.*)]'] # panama NFI backend only supported in JVM mode
        ) + ['--results-file', self.result_file, '--', '--polybench-vm=graalvm-${VM_ENV}', '--polybench-vm-config=' + vmConfig],
    compiler_bench_cmd(vmConfig):: self.bench_cmd + ['polybench:*[compiler/dispatch.js]', '--results-file', self.result_file, '--', '--polybench-vm=graalvm-${VM_ENV}', '--polybench-vm-config=' + vmConfig],
    warmup_bench_cmd(vmConfig):: self.bench_cmd + ['--fork-count-file', 'ci/ci_common/benchmark-forks.json',  'polybench:r[warmup/.*]', '--results-file', self.result_file, '--', '--polybench-vm=graalvm-${VM_ENV}', '--polybench-vm-config=' + vmConfig],

    setup+: [
      self.base_cmd + ['build'],
      self.base_cmd + ['build', '--dependencies=POLYBENCH_BENCHMARKS'],
    ],
    notify_groups:: ['polybench'],
  },

  vm_bench_polybench_hpc_linux_common(env, metric, benchmarks='*', polybench_vm_config='native-interpreter'): self.vm_bench_polybench_linux_common(env=env) + self.polybench_hpc_linux_common + {
    local machine_name = "e3",     // restricting ourselves to specific hardware to ensure performance counters work there
    machine_name_prefix:: "gate-",
    capabilities+: [machine_name],
    run+: [
      self.base_cmd + ['benchmark', 'polybench:'+benchmarks,
                       '--fork-count-file', 'ci/ci_includes/polybench-hpc.json',
                       '--results-file', self.result_file,
                       '--machine-name', self.machine_name_prefix + machine_name,
                       '--',
                       '--metric=' + metric,
                       '--polybench-vm=graalvm-${VM_ENV}',
                       '--polybench-vm-config=' + polybench_vm_config],
      self.upload_and_wait_for_indexing + ['||', 'echo', 'Result upload failed!'],
    ],
  },

  vm_bench_polybench_linux_interpreter: self.vm_bench_polybench_linux_common() + vm.vm_java_21 + {
    run+: [
      self.interpreter_bench_cmd(vmConfig='jvm-interpreter'),
      self.upload,
      self.interpreter_bench_cmd(vmConfig='native-interpreter'),
      self.upload,
    ],
    timelimit: '2:00:00',
  },

  vm_bench_polybench_linux_compiler: self.vm_bench_polybench_linux_common() + vm.vm_java_21 + {
    compiler_bench_cmd(vmConfig):: super.compiler_bench_cmd(vmConfig) + ['-w', '0', '-i', '10'],
    run+: [
      self.compiler_bench_cmd(vmConfig='jvm-standard') + ['--metric=compilation-time'],
      self.upload,
      self.compiler_bench_cmd(vmConfig='native-standard') + ['--metric=compilation-time'],
      self.upload,
      self.compiler_bench_cmd(vmConfig='jvm-standard') + ['--metric=partial-evaluation-time'],
      self.upload,
      self.compiler_bench_cmd(vmConfig='native-standard') + ['--metric=partial-evaluation-time'],
      self.upload,
    ],
  },

  vm_bench_polybench_linux_context_init: self.vm_bench_polybench_linux_common() + vm.vm_java_21 + {
    bench_cmd:: super.base_cmd + ['benchmark', '--fork-count-file', 'ci/ci_common/benchmark-forks.json', 'polybench:*[interpreter/pyinit.py,interpreter/jsinit.js,interpreter/rbinit.rb]', '--results-file', self.result_file, '--', '-w', '0', '-i', '0', '--polybench-vm=graalvm-${VM_ENV}'],
    run+: [
      self.bench_cmd + ['--polybench-vm-config=jvm-standard', '--metric=none'],
      self.upload,
      self.bench_cmd + ['--polybench-vm-config=native-standard', '--metric=none'],
      self.upload,
    ],
  },

  vm_bench_polybench_linux_warmup: self.vm_bench_polybench_linux_common() + vm.vm_java_21 + {
    run+: [
      self.warmup_bench_cmd(vmConfig='native-standard') + ['--metric=one-shot'],
      self.upload,
    ],
  },

  vm_bench_polybench_linux_memory: self.vm_bench_polybench_linux_common() + vm.vm_java_21 + {
    run+: [
      self.interpreter_bench_cmd(vmConfig='jvm-standard') + ['--metric=metaspace-memory'],
      self.upload,
      self.interpreter_bench_cmd(vmConfig='jvm-standard') + ['--metric=application-memory'],
      self.upload,
      # We run the interprer benchmarks in both interprer and standard mode to compare allocation with and without compilation.
      self.interpreter_bench_cmd(vmConfig='jvm-interpreter') + ['-w', '40', '-i', '10', '--metric=allocated-bytes'],
      self.upload,
      self.interpreter_bench_cmd(vmConfig='jvm-standard') + ['-w', '40', '-i', '10', '--metric=allocated-bytes'],
      self.upload,
      self.interpreter_bench_cmd(vmConfig='native-interpreter') + ['-w', '40', '-i', '10', '--metric=allocated-bytes'],
      self.upload,
      self.interpreter_bench_cmd(vmConfig='native-standard') + ['-w', '40', '-i', '10', '--metric=allocated-bytes'],
      self.upload,
    ],
    timelimit: '4:00:00',
  },

  vm_gate_polybench_linux: self.vm_bench_polybench_linux_common(is_gate=true) + vm.vm_java_21 + {
    interpreter_bench_cmd(vmConfig):: super.interpreter_bench_cmd(vmConfig) + ['-w', '1', '-i', '1'],
    compiler_bench_cmd(vmConfig):: super.compiler_bench_cmd(vmConfig) + ['-w', '0', '-i', '1'],
    warmup_bench_cmd(vmConfig):: super.warmup_bench_cmd(vmConfig) + ['-w', '1', '-i', '1'],
    run+: [
      self.interpreter_bench_cmd(vmConfig='jvm-interpreter'),
      self.interpreter_bench_cmd(vmConfig='native-interpreter'),
      self.compiler_bench_cmd(vmConfig='jvm-standard') + ['--metric=compilation-time'],
      self.compiler_bench_cmd(vmConfig='native-standard') + ['--metric=partial-evaluation-time'],
      self.warmup_bench_cmd(vmConfig='native-standard') + ['--metric=one-shot'],
    ],
    timelimit: '1:30:00',
    notify_groups: ['polybench'],
  },

  vm_bench_polybench_nfi: {
    base_cmd:: ['mx', '--env', 'polybench-nfi-${VM_ENV}'],
    bench_cmd:: self.base_cmd + ['benchmark', 'polybench:r[nfi/.*]', '--results-file', self.result_file, '--', '--polybench-vm=graalvm-${VM_ENV}'],
    setup+: [
      self.base_cmd + ['build'],
      self.base_cmd + ['build', '--dependencies=POLYBENCH_BENCHMARKS'],
    ],
    run+: [
      self.bench_cmd + ['--polybench-vm-config=jvm-standard'],
      self.upload,
      self.bench_cmd + ['--polybench-vm-config=native-standard'],
      self.upload,
    ],
    notify_groups:: ['sulong'],
    timelimit: '55:00',
  },

  js_bench_compilation_throughput(pgo): self.vm_bench_common + common.heap.default + {
    local mx_libgraal = ["mx", "--env", repo_config.vm.mx_env.libgraal],

    setup+: [
      mx_libgraal + ["--dynamicimports", "/graal-js", "sforceimports"],  # clone the revision of /graal-js imported by /vm
      ["git", "clone", "--depth", "1", ['mx', 'urlrewrite', "https://github.com/graalvm/js-benchmarks.git"], "../../js-benchmarks"],
      mx_libgraal + ["--dynamicimports", "/graal-js,js-benchmarks", "sversions"]
    ] + (if pgo then repo_config.compiler.collect_libgraal_profile(mx_libgraal) else []) + [
      mx_libgraal + (if pgo then repo_config.compiler.use_libgraal_profile else []) + ["--dynamicimports", "/graal-js,js-benchmarks", "build", "--force-javac"]
    ],
    local xms = if std.objectHasAll(self.environment, 'XMS') then ["-Xms${XMS}"] else [],
    local xmx = if std.objectHasAll(self.environment, 'XMX') then ["-Xmx${XMX}"] else [],
    run: [
      mx_libgraal + ["--dynamicimports", "js-benchmarks,/graal-js",
        "benchmark", "octane:typescript",
        "--results-file", self.result_file, "--"
      ] + xms + xmx + [
        "--experimental-options",
        "--engine.CompilationFailureAction=ExitVM",
        "-Dgraal.DumpOnError=true",
        "-Dgraal.PrintGraph=File",
        "--js-vm=graal-js",
        "--js-vm-config=default",
        "--jvm=server",
        "--jvm-config=" + repo_config.compiler.libgraal_jvm_config(pgo) + "-no-truffle-bg-comp",
        "-XX:+CITime"],
      self.upload
    ],
    logs+: [
      "runtime-graphs-*.bgv"
    ],
    timelimit: "2:30:00",
    notify_groups:: ['compiler_bench']
  },

  vm_bench_polybench_nfi_linux_amd64: self.vm_bench_common + vm_common.svm_common_linux_amd64 + self.vm_bench_polybench_nfi,

  local builds = [
    # We used to expand `${common_vm_linux}` here to work around some limitations in the version of pyhocon that we use in the CI
    vm_common.bench_ondemand_vm_linux_amd64 + self.vm_bench_js_linux_amd64('octane')     + {name: 'ondemand-bench-vm-' + vm.vm_setup.short_name + '-js-octane-java' + self.jdk_version + '-linux-amd64'},
    vm_common.bench_ondemand_vm_linux_amd64 + self.vm_bench_js_linux_amd64('jetstream')  + {name: 'ondemand-bench-vm-' + vm.vm_setup.short_name + '-js-jetstream-java' + self.jdk_version + '-linux-amd64'},
    vm_common.bench_ondemand_vm_linux_amd64 + self.vm_bench_js_linux_amd64('jetstream2') + {name: 'ondemand-bench-vm-' + vm.vm_setup.short_name + '-js-jetstream2-java' + self.jdk_version + '-linux-amd64'},
    vm_common.bench_ondemand_vm_linux_amd64 + self.vm_bench_js_linux_amd64('micro')      + {name: 'ondemand-bench-vm-' + vm.vm_setup.short_name + '-js-micro-java' + self.jdk_version + '-linux-amd64'},
    vm_common.bench_ondemand_vm_linux_amd64 + self.vm_bench_js_linux_amd64('v8js')       + {name: 'ondemand-bench-vm-' + vm.vm_setup.short_name + '-js-v8js-java' + self.jdk_version + '-linux-amd64'},
    vm_common.bench_ondemand_vm_linux_amd64 + self.vm_bench_js_linux_amd64('misc')       + {name: 'ondemand-bench-vm-' + vm.vm_setup.short_name + '-js-misc-java' + self.jdk_version + '-linux-amd64'},
    vm_common.bench_ondemand_vm_linux_amd64 + self.vm_bench_js_linux_amd64('npm-regex')  + {name: 'ondemand-bench-vm-' + vm.vm_setup.short_name + '-js-npm-regex-java' + self.jdk_version + '-linux-amd64'},

    vm_common.bench_daily_vm_linux_amd64 + self.vm_bench_polybench_linux_interpreter     + {name: 'daily-bench-vm-' + vm.vm_setup.short_name + '-polybench-linux-amd64', notify_groups:: ['polybench']},
    vm_common.bench_daily_vm_linux_amd64 + self.vm_bench_polybench_linux_compiler        + {name: 'daily-bench-vm-' + vm.vm_setup.short_name + '-polybench-compiler-linux-amd64', notify_groups:: ['polybench']},
    vm_common.bench_daily_vm_linux_amd64 + self.vm_bench_polybench_linux_context_init    + {name: 'daily-bench-vm-' + vm.vm_setup.short_name + '-polybench-context-init-linux-amd64', notify_groups:: ['polybench']},
    vm_common.bench_daily_vm_linux_amd64 + self.vm_bench_polybench_linux_warmup          + {name: 'daily-bench-vm-' + vm.vm_setup.short_name + '-polybench-warmup-linux-amd64', notify_groups:: ['polybench']},
    vm_common.bench_daily_vm_linux_amd64 + self.vm_bench_polybench_linux_memory          + {name: 'daily-bench-vm-' + vm.vm_setup.short_name + '-polybench-memory-linux-amd64', notify_groups:: ['polybench'] },

    # Produces the graalvm-with-polybench artifact
    vm_common.vm_linux_amd64 + self.vm_bench_polybenchmarks_linux_build + {name: 'ondemand-vm-build-' + vm.vm_setup.short_name + '-with-polybench-linux-amd64', notify_groups:: ['polybench']},

    # Consume the graalvm-with-polybench artifact
    vm_common.bench_daily_vm_linux_amd64 + self.vm_bench_polybenchmarks_linux_common(vm_config='native')                        + {name: 'daily-bench-vm-' + vm.vm_setup.short_name + '-polybenchmarks-default-native-linux-amd64', notify_groups:: ['polybench']},
    vm_common.bench_daily_vm_linux_amd64 + self.vm_bench_polybenchmarks_linux_common(vm_config='jvm')                           + {name: 'daily-bench-vm-' + vm.vm_setup.short_name + '-polybenchmarks-default-jvm-linux-amd64', notify_groups:: ['polybench']},
    vm_common.bench_daily_vm_linux_amd64 + self.vm_bench_polybenchmarks_linux_common(vm_config='native', suite='awfy:r[.*py]')  + {name: 'daily-bench-vm-' + vm.vm_setup.short_name + '-polybenchmarks-awfy-py-native-linux-amd64', notify_groups:: ['polybench']},
    vm_common.bench_daily_vm_linux_amd64 + self.vm_bench_polybenchmarks_linux_common(vm_config='jvm',    suite='awfy:r[.*py]')  + {name: 'daily-bench-vm-' + vm.vm_setup.short_name + '-polybenchmarks-awfy-py-jvm-linux-amd64', notify_groups:: ['polybench']},
    vm_common.bench_daily_vm_linux_amd64 + self.vm_bench_polybenchmarks_linux_common(vm_config='native', suite='awfy:r[.*rb]')  + {name: 'daily-bench-vm-' + vm.vm_setup.short_name + '-polybenchmarks-awfy-rb-native-linux-amd64', notify_groups:: ['polybench']},
    vm_common.bench_daily_vm_linux_amd64 + self.vm_bench_polybenchmarks_linux_common(vm_config='jvm',    suite='awfy:r[.*rb]')  + {name: 'daily-bench-vm-' + vm.vm_setup.short_name + '-polybenchmarks-awfy-rb-jvm-linux-amd64', notify_groups:: ['polybench']},
    vm_common.bench_daily_vm_linux_amd64 + self.vm_bench_polybenchmarks_linux_common(vm_config='native', suite='awfy:r[.*jar]') + {name: 'daily-bench-vm-' + vm.vm_setup.short_name + '-polybenchmarks-awfy-jar-native-linux-amd64', notify_groups:: ['polybench']},
    vm_common.bench_daily_vm_linux_amd64 + self.vm_bench_polybenchmarks_linux_common(vm_config='jvm',    suite='awfy:r[.*jar]') + {name: 'daily-bench-vm-' + vm.vm_setup.short_name + '-polybenchmarks-awfy-jar-jvm-linux-amd64', notify_groups:: ['polybench']},

    vm_common.bench_daily_vm_linux_amd64 + self.vm_bench_polybench_nfi_linux_amd64 + vm.vm_java_21 + {name: 'daily-bench-vm-' + vm.vm_setup.short_name + '-polybench-nfi-java21-linux-amd64', notify_groups:: ['polybench']},

    vm_common.bench_daily_vm_linux_amd64 + self.js_bench_compilation_throughput(true) + vm.vm_java_21 + { name: 'daily-bench-vm-' + vm.vm_setup.short_name + '-libgraal-pgo-throughput-js-typescript-java' + self.jdk_version + '-linux-amd64' },
    vm_common.bench_daily_vm_linux_amd64 + self.js_bench_compilation_throughput(false) + vm.vm_java_21 + { name: 'daily-bench-vm-' + vm.vm_setup.short_name + '-libgraal-no-pgo-throughput-js-typescript-java' + self.jdk_version + '-linux-amd64' },

    vm_common.bench_daily_vm_linux_amd64 + self.vm_bench_js_linux_amd64() + {
      # Override `self.vm_bench_js_linux_amd64.run`
      run: [
        vm_common.mx_vm_common + ['benchmark', '--results-file', self.result_file, 'agentscript-graal-js:*', '--', '--jvm=graalvm-${VM_ENV}', '--jvm-config=jvm', '--js=graal-js', '--js-config=default'],
        $.vm_bench_common.upload,
        vm_common.mx_vm_common + ['benchmark', '--results-file', self.result_file, 'agentscript-graal-js:*', '--', '--jvm=graalvm-${VM_ENV}', '--jvm-config=native', '--js=graal-js', '--js-config=default'],
        $.vm_bench_common.upload,
      ],
      timelimit: '45:00',
      name: 'daily-bench-vm-' + vm.vm_setup.short_name + '-agentscript-js-java' + self.jdk_version + '-linux-amd64',
      notify_groups:: ['javascript'],
    },

    vm_common.gate_vm_linux_amd64 + self.vm_bench_polybenchmarks_linux_common(is_gate=true)    + {name: 'gate-vm-' + vm.vm_setup.short_name + '-polybenchmarks-linux-amd64'},
    vm_common.gate_vm_linux_amd64 + self.vm_gate_polybench_linux + {name: 'gate-vm-' + vm.vm_setup.short_name + '-polybench-linux-amd64'},
  ],

  builds: [{'defined_in': std.thisFile} + b for b in builds],
}
