# note: this file needs to be in sync between CE and EE

local utils = import '../../../ci/ci_common/common-utils.libsonnet';
local vm = import '../ci_includes/vm.jsonnet';
local common = import '../../../ci/ci_common/common.jsonnet';
local vm_common = import '../ci_common/common.jsonnet';

local repo_config = import '../../../ci/repo-configuration.libsonnet';

{
  vm_bench_base(machine_name=null)::
    {
      result_file:: 'results.json',
      upload:: ['bench-uploader.py', self.result_file],
      upload_and_wait_for_indexing:: self.upload + ['--wait-for-indexing'],
      timelimit: '1:30:00',
      capabilities+: if std.objectHasAll(self, 'machine_name') then [self.machine_name] else [],
    } +
    (if machine_name != null then {
      machine_name:: machine_name,
      capabilities+: [machine_name]
    } else {}),

  vm_bench_common: self.vm_bench_base(machine_name='x52') + { capabilities+: ['tmpfs25g'] },

  vm_bench_js_linux_amd64(bench_suite=null): vm.vm_java_Latest + common.deps.svm + common.deps.sulong + vm.custom_vm + self.vm_bench_common + {
    cmd_base:: vm_common.mx_vm_common + ['--dynamicimports', 'js-benchmarks', 'benchmark', '--results-file', self.result_file],
    config_base:: ['--js-vm=graal-js', '--js-vm-config=default', '--jvm=graalvm-${VM_ENV}'],
    setup+: [
      ['set-export', 'VM_ENV', '$VM_ENV-js'],
      vm_common.mx_vm_common + ['build'],
      ['git', 'clone', '--depth', '1', ['mx', 'urlrewrite', 'https://github.com/graalvm/js-benchmarks.git'], '../../js-benchmarks'],
    ],
    run+:
      if (bench_suite != null) then [
        self.cmd_base + [bench_suite + ':*', '--'] + self.config_base + ['--jvm-config=jvm', '--vm.Xss24m'],
        $.vm_bench_common.upload,
        self.cmd_base + [bench_suite + ':*', '--'] + self.config_base + ['--jvm-config=native'],
        $.vm_bench_common.upload,
      ] else [],
  },

  polybench_hpc_linux_common(shape=null):
    (if shape != null then {
      machine_name:: shape,
      capabilities+: [shape],
    } else {}) + {
    packages+: {
      'papi': '==5.5.1',
    },
    local PAPI_DIR='/cm/shared/apps/papi/papi-5.5.1',
    environment+: {
      ENABLE_POLYBENCH_HPC: 'yes',
      POLYBENCH_HPC_EXTRA_HEADERS: PAPI_DIR + '/include',
      POLYBENCH_HPC_PAPI_LIB_DIR: PAPI_DIR + '/lib',
    } + if !std.objectHasAll(self, 'machine_name') then {} else if std.count(['e3', 'e4_36_256', 'e4_8_64'], self.machine_name) > 0 then {LIBPFM_FORCE_PMU: 'amd64'} else if self.machine_name == 'x52' then {} else {},
    setup+: [
        # Debug commands to help diagnose future problems.
        ['sysctl', 'kernel.perf_event_paranoid'], # print paranoid level (-1 expected)
        [PAPI_DIR + '/bin/papi_avail'], # print available events (non-zero number of events expected)
    ],
  },


  polybench_vm_common(env='${VM_ENV}', fail_fast=false, skip_machine=false): (if skip_machine then self.vm_bench_base(machine_name=null) else self.vm_bench_common) + common.deps.svm + vm.custom_vm {
    local is_enterprise = (vm.edition == 'ee'),
    setup+: [
      ['mx', '--env', env, 'sforceimports'],
      ['mx', '--env', env, 'build'],
      ['set-export', 'POLYBENCH_JVM', ['mx', '--env', env, 'graalvm-home']],
    ] + if is_enterprise then [['mx', '--dy', '/truffle-enterprise', 'build']] else [],

    # Extends the provided polybench command with common arguments used in CI. We want the command at the call site
    # to be simple (i.e., a flat array of string literals) so it can be easily copied and run locally; using this
    # wrapper allows us to inject CI-specific fields without specifying them in the command.
    polybench_wrap(command)::
      assert command[0] == 'mx' : "polybench command should start with 'mx'";
      // Dynamically import /truffle-enterprise when running on enterprise.
      local extra_imports = if is_enterprise then ['--dy', '/truffle-enterprise'] else [];
      ['mx'] + extra_imports + command[1:] + ['--mx-benchmark-args', '--results-file', self.result_file] +
      (if (fail_fast) then ['--fail-fast'] else []),
    notify_groups:: ['polybench'],
  },

  polybench_vm_hpc_common: self.polybench_vm_common(skip_machine=true) + self.polybench_hpc_linux_common(shape='e4_8_64') + {
    polybench_wrap(command)::
      super.polybench_wrap(command) + [
        '--mx-benchmark-args',
        '--machine-name', 'gate-' + self.machine_name
      ],
    teardown: [self.upload_and_wait_for_indexing + ['||', 'echo', 'Result upload failed!']],
  },

  polybench_vm_gate(os, arch, language, name = null): vm_common.vm_base(os, arch, 'tier3') + self.polybench_vm_common(fail_fast=true, skip_machine=true) + vm.vm_java_Latest + {
    name: utils.hyphenize(['gate-vm', vm.vm_setup.short_name, 'polybench', language, name, utils.jdk_and_hardware(self)]),
    timelimit: '1:00:00',
  },

  polybench_vm_daily(os, arch, language, name = null): vm_common.vm_base(os, arch, 'daily', bench=true) + self.polybench_vm_common() + vm.vm_java_Latest + {
    name: utils.hyphenize(['daily-bench-vm', vm.vm_setup.short_name, 'polybench', language, name, utils.jdk_and_hardware(self)]),
    teardown: [self.upload],
    timelimit: '4:00:00',
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
        "--engine.MaximumCompilations=200", # GR-61670
        "-Djdk.graal.DumpOnError=true",
        "-Djdk.graal.PrintGraph=File",
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

  local builds = [
    # We used to expand `${common_vm_linux}` here to work around some limitations in the version of pyhocon that we use in the CI
    vm_common.vm_base('linux', 'amd64', 'ondemand', bench=true) + self.vm_bench_js_linux_amd64('octane')     + {name: 'ondemand-bench-vm-' + vm.vm_setup.short_name + '-js-octane-' + utils.jdk_and_hardware(self)},
    vm_common.vm_base('linux', 'amd64', 'ondemand', bench=true) + self.vm_bench_js_linux_amd64('jetstream')  + {name: 'ondemand-bench-vm-' + vm.vm_setup.short_name + '-js-jetstream-' + utils.jdk_and_hardware(self)},
    vm_common.vm_base('linux', 'amd64', 'ondemand', bench=true) + self.vm_bench_js_linux_amd64('jetstream2') + {name: 'ondemand-bench-vm-' + vm.vm_setup.short_name + '-js-jetstream2-' + utils.jdk_and_hardware(self)},
    vm_common.vm_base('linux', 'amd64', 'ondemand', bench=true) + self.vm_bench_js_linux_amd64('micro')      + {name: 'ondemand-bench-vm-' + vm.vm_setup.short_name + '-js-micro-' + utils.jdk_and_hardware(self)},
    vm_common.vm_base('linux', 'amd64', 'ondemand', bench=true) + self.vm_bench_js_linux_amd64('v8js')       + {name: 'ondemand-bench-vm-' + vm.vm_setup.short_name + '-js-v8js-' + utils.jdk_and_hardware(self)},
    vm_common.vm_base('linux', 'amd64', 'ondemand', bench=true) + self.vm_bench_js_linux_amd64('misc')       + {name: 'ondemand-bench-vm-' + vm.vm_setup.short_name + '-js-misc-' + utils.jdk_and_hardware(self)},
    vm_common.vm_base('linux', 'amd64', 'ondemand', bench=true) + self.vm_bench_js_linux_amd64('npm-regex')  + {name: 'ondemand-bench-vm-' + vm.vm_setup.short_name + '-js-npm-regex-' + utils.jdk_and_hardware(self)},

    vm_common.vm_base('linux', 'amd64', 'daily', bench=true) + self.js_bench_compilation_throughput(true) + vm.vm_java_Latest + { name: 'daily-bench-vm-' + vm.vm_setup.short_name + '-libgraal-pgo-throughput-js-typescript-' + utils.jdk_and_hardware(self) },
    vm_common.vm_base('linux', 'amd64', 'daily', bench=true) + self.js_bench_compilation_throughput(false) + vm.vm_java_Latest + { name: 'daily-bench-vm-' + vm.vm_setup.short_name + '-libgraal-no-pgo-throughput-js-typescript-' + utils.jdk_and_hardware(self) },

    vm_common.vm_base('linux', 'amd64', 'daily', bench=true) + self.vm_bench_js_linux_amd64() + {
      # Override `self.vm_bench_js_linux_amd64.run`
      run: [
        vm_common.mx_vm_common + ['benchmark', '--results-file', self.result_file, 'agentscript-graal-js:*', '--', '--jvm=graalvm-${VM_ENV}', '--jvm-config=jvm', '--js=graal-js', '--js-config=default'],
        $.vm_bench_common.upload,
        vm_common.mx_vm_common + ['benchmark', '--results-file', self.result_file, 'agentscript-graal-js:*', '--', '--jvm=graalvm-${VM_ENV}', '--jvm-config=native', '--js=graal-js', '--js-config=default'],
        $.vm_bench_common.upload,
      ],
      timelimit: '45:00',
      name: 'daily-bench-vm-' + vm.vm_setup.short_name + '-agentscript-js-' + utils.jdk_and_hardware(self),
      notify_groups:: ['javascript'],
    },
  ] + [
    # Sulong polybench jobs
    self.polybench_vm_gate('linux', 'amd64', 'sulong') + {
      setup+: [
        ['mx', '--dy', '/sulong', 'build'],
        ['mx', '--dy', '/sulong', 'build', '--dependencies', 'SULONG_POLYBENCH_BENCHMARKS'],
      ],
      run+: [
        self.polybench_wrap(['mx', '--dy', '/sulong', '--java-home', '${POLYBENCH_JVM}', 'polybench', '--suite', 'sulong:gate']),
      ],
      notify_groups +: ['sulong'],
    },
    self.polybench_vm_daily('linux', 'amd64', 'sulong') + {
      setup+: [
        ['mx', '--dy', '/sulong', 'build'],
        ['mx', '--dy', '/sulong', 'build', '--dependencies', 'SULONG_POLYBENCH_BENCHMARKS'],
      ],
      run+: [
        self.polybench_wrap(['mx', '--dy', '/sulong', '--java-home', '${POLYBENCH_JVM}', 'polybench', '--suite', 'sulong:benchmark']),
      ],
      notify_groups +: ['sulong'],
    }
  ] + [
    # Wasm polybench jobs
    self.polybench_vm_gate('linux', 'amd64', 'wasm') + common.deps.wasm + {
      setup+: [
        ['mx', '--dy', '/wasm', 'build'],
        ['mx', '--dy', '/wasm', 'build', '--dependencies', 'WASM_POLYBENCH_BENCHMARKS']
      ],
      run+: [
        self.polybench_wrap(['mx', '--dy', '/wasm', '--java-home', '${POLYBENCH_JVM}', 'polybench', '--suite', 'wasm:gate']),
      ],
      notify_groups +: ['wasm'],
    },
    self.polybench_vm_daily('linux', 'amd64', 'wasm') + common.deps.wasm + {
      setup+: [
        ['mx', '--dy', '/wasm', 'build'],
        ['mx', '--dy', '/wasm', 'build', '--dependencies', 'WASM_POLYBENCH_BENCHMARKS']
      ],
      run+: [
        self.polybench_wrap(['mx', '--dy', '/wasm', '--java-home', '${POLYBENCH_JVM}', 'polybench', '--suite', 'wasm:benchmark']),
      ],
      notify_groups +: ['wasm'],
    }
  ] + [
    # Espresso polybench jobs
    self.polybench_vm_gate('linux', 'amd64', 'espresso') + {
      setup+: [
        ['mx', '--dy', '/espresso', 'build']
      ],
      run+: [
        self.polybench_wrap(['mx', '--dy', '/espresso', '--java-home', '${POLYBENCH_JVM}', 'polybench', '--suite', 'espresso:gate']),
      ],
      notify_groups +: ['espresso'],
    },
    self.polybench_vm_daily('linux', 'amd64', 'espresso') + {
      setup+: [
        ['mx', '--dy', '/espresso', 'build']
      ],
      run+: [
        self.polybench_wrap(['mx', '--dy', '/espresso', '--java-home', '${POLYBENCH_JVM}', 'polybench', '--suite', 'espresso:benchmark']),
      ],
      notify_groups +: ['espresso'],
    }
  ] + [
    # TruffleRuby polybench jobs
    self.polybench_vm_gate('linux', 'amd64', 'ruby') + common.deps.truffleruby + {
      environment+: {
        RUBY_BENCHMARKS: 'true',
      },
      setup+: [
        ['mx', '--dy', 'truffleruby', 'build']
      ],
      run+: [
        self.polybench_wrap(['mx', '--dy', 'truffleruby', '--java-home', '${POLYBENCH_JVM}', 'polybench', '--suite', 'ruby:gate']),
      ],
      notify_groups +: ['ruby'],
    },
    self.polybench_vm_daily('linux', 'amd64', 'ruby') + common.deps.truffleruby + {
      environment+: {
        RUBY_BENCHMARKS: 'true',
      },
      setup+: [
        ['mx', '--dy', 'truffleruby', 'build']
      ],
      run+: [
        self.polybench_wrap(['mx', '--dy', 'truffleruby', '--java-home', '${POLYBENCH_JVM}', 'polybench', '--suite', 'ruby:benchmark']),
      ],
      notify_groups +: ['ruby'],
    }
  ] + [
    # GraalPy polybench jobs
    self.polybench_vm_gate('linux', 'amd64', 'python') + {
      setup+: [
        ['mx', '--dy', 'graalpython', 'build']
      ],
      run+: [
        self.polybench_wrap(['mx', '--dy', 'graalpython', '--java-home', '${POLYBENCH_JVM}', 'polybench', '--suite', 'python:gate']),
      ],
      notify_groups +: ['python'],
    },
    self.polybench_vm_daily('linux', 'amd64', 'python') + {
      setup+: [
        ['mx', '--dy', 'graalpython', 'build']
      ],
      run+: [
        self.polybench_wrap(['mx', '--dy', 'graalpython', '--java-home', '${POLYBENCH_JVM}', 'polybench', '--suite', 'python:benchmark']),
      ],
      notify_groups +: ['python'],
    }
  ] + [
    # NFI polybench jobs
    self.polybench_vm_gate('linux', 'amd64', 'nfi') + {
      setup+: [
        ['mx', '--dy', '/graal-js', 'build']
      ],
      run+: [
        self.polybench_wrap(['mx', '--dy', '/graal-js', '--java-home', '${POLYBENCH_JVM}', 'polybench', '--suite', 'nfi:gate']),
      ],
    },
    self.polybench_vm_daily('linux', 'amd64', 'nfi') + {
      setup+: [
        ['mx', '--dy', '/graal-js', 'build']
      ],
      run+: [
        self.polybench_wrap(['mx', '--dy', '/graal-js', '--java-home', '${POLYBENCH_JVM}', 'polybench', '--suite', 'nfi:benchmark']),
      ],
    }
 ] + [
    # Graal.js polybench jobs
    self.polybench_vm_gate('linux', 'amd64', 'js') + {
      setup+: [
        ['mx', '--dy', '/graal-js', 'build']
      ],
      run+: [
        self.polybench_wrap(['mx', '--dy', '/graal-js', '--java-home', '${POLYBENCH_JVM}', 'polybench', '--suite', 'js:gate']),
      ],
      notify_groups +: ['javascript'],
    },
    self.polybench_vm_daily('linux', 'amd64', 'js') + {
      setup+: [
        ['mx', '--dy', '/graal-js', 'build']
      ],
      run+: [
        self.polybench_wrap(['mx', '--dy', '/graal-js', '--java-home', '${POLYBENCH_JVM}', 'polybench', '--suite', 'js:benchmark']),
      ],
      notify_groups +: ['javascript'],
    }
  ],
  # TODO (GR-60584): reimplement polybenchmarks jobs once polybench is unchained

  builds: utils.add_defined_in(builds, std.thisFile),
}
