local utils = import '../../../ci/ci_common/common-utils.libsonnet';
local vm = import 'vm.jsonnet';
local vm_common = import '../ci_common/common.jsonnet';
local graal_common = import '../../../ci/ci_common/common.jsonnet';

{
  local truffle_native_tck = graal_common.deps.svm + {
    run+: [
      ['mx', '--env', 'ce', '--dynamicimports', '/tools', '--native-images=lib:jvmcicompiler', 'gate', '--tags', 'build,truffle-native-tck,truffle-native-tck-sl'],
    ],
    notify_groups: ["truffle"],
    components+: ["truffletck"],
    timelimit: '35:00',
    name: 'gate-vm-truffle-native-tck-labs' + self.jdk_name + '-linux-amd64',
    logs+: [
      "*/call_tree.txt.gz"
    ]
  },

  local truffle_native_tck_wasm = graal_common.deps.svm + {
    run+: [
      ['mx', '--env', 'ce', '--dynamicimports', '/wasm', '--native-images=lib:jvmcicompiler', 'gate', '--tags', 'build,truffle-native-tck-wasm'],
    ],
    notify_groups: ["wasm"],
    components+: ["truffletck"],
    timelimit: '35:00',
    name: 'gate-vm-truffle-native-tck-wasm-labs' + self.jdk_name + '-linux-amd64',
  },

  local truffle_maven_downloader = graal_common.deps.svm + graal_common.deps.sulong + {
    run+: [
      ['mx', '--env', 'ce-llvm', '--native-images=', 'gate', '--no-warning-as-error', '--tags', 'build,maven-downloader'],
    ],
    notify_groups: ["truffle"],
    components+: ["truffle"],
    timelimit: '30:00',
    packages+: {
      maven: '==3.5.3',
    },
    name: 'gate-vm-ce-truffle-maven-downloader-labs' + self.jdk_name + '-linux-amd64',
  },

  # Truffle Isolate Unittest Jobs
  local truffle_isolate_gate_command(tags, use_prebuild_jdk=false) = ['mx'] + (if use_prebuild_jdk then [] else ['--env', 'ce', '--components=env.COMPONENTS,nju', '--native-images=']) + ['gate', '--no-warning-as-error', '--tags', tags],

  truffleisolate_gate(mode, time_limit): graal_common.deps.svm + {
    run: [
      truffle_isolate_gate_command('build,truffle_isolate_build_unittest_library,truffle_isolate_' + mode + '_unittest'),
    ],
    components+: ["truffle"],
    notify_groups: ["truffle"],
    timelimit: time_limit,
  },

  truffleisolate_oraclejdk_gate(jdk_version, time_limit): graal_common.deps.svm + {
    local oracle_java_home = 'ORACLE_JDK' + jdk_version + '_HOME',
    downloads+: {
      [oracle_java_home]: graal_common.jdks_data['oraclejdk' + jdk_version],
    },
    run: [
      truffle_isolate_gate_command('build,truffle_isolate_build_unittest_library'),
      ['set-export', 'JAVA_HOME', '$' + oracle_java_home],
      ['mx', 'build'],
      truffle_isolate_gate_command('truffle_isolate_internal_unittest', use_prebuild_jdk=true),
    ],
    components+: ["truffle"],
    notify_groups: ["truffle"],
    timelimit: time_limit,
  },

  # Truffle Isolate Maven Tests
  truffle_unchained_isolate(mode, os, time_limit): graal_common.deps.svm + {
    local gate_name =
      if mode == 'internal' then 'Vm: Truffle Unchained Maven Polyglot Internal Isolate'
      else if mode == 'external' then 'Vm: Truffle Unchained Maven Polyglot External Isolate'
      else error 'Unsupported gate_mode: ' + mode,
    local imports = '/tools,/graal-js,/wasm,/sulong,/graalpython',
    mx_cmd:: ['mx', '--env', 'ce', '--polyglot-isolates=true', '--dynamicimports', imports],
    components+: ["truffle"],
    notify_groups: ["truffle"],
    timelimit: time_limit,
    run: [
      ['mx', 'sforceimports'],
      self.mx_cmd + ['gate', '--no-warning-as-error', '-o', '--tags', 'build'],
      self.mx_cmd + ['gate', '-o', '-t', 'Vm: Truffle Unchained Maven Deploy Local'],
      self.mx_cmd + ['gate', '-o', '-t', gate_name],
    ]
  } + (
     if (os == 'windows') then
       graal_common.deps.sulong + {
         downloads+: {
           GRADLE_JAVA_HOME: graal_common.jdks_data['oraclejdk21'],
         },
       }
     else
       vm_common.maven_download_unix
  ),

  local truffle_isolate_modes = ['internal', 'external'],
  local truffle_isolate_platforms = [
    { os: 'linux', arch: 'amd64', build_type: 'full_vm_build', build_version: false },
    { os: 'darwin', arch: 'aarch64', build_type: 'full_vm_build', build_version: false },
    { os: 'windows', arch: 'amd64', build_type: 'svm_common', build_version: true }
  ],
  local truffle_isolate_unittest_jobs = [
    (
      local explicit_target = if (platform.os == 'windows' || platform.os == 'darwin') then 'daily' else 'gate';
      local explicit_capabilities = if platform.os == 'windows' && mode == 'external' then { capabilities: ['windows_11'] } else {};
      local timelimit =
        # Darwin builders are highly affected by system load; gate times vary from ~13 minutes up to ~124 minutes.
        if platform.os == 'darwin' then  '3:15:00'
        else if platform.os == 'windows' then '2:30:00'
        else '2:00:00';
      local jdk_hint = if platform.os == 'windows' then 'Latest' else null;
      vm.vm_java_Latest + vm_common.vm_base(platform.os, platform.arch, explicit_target, jdk_hint=jdk_hint)  + self.truffleisolate_gate(mode, timelimit) + explicit_capabilities + {
        name: explicit_target + '-vm-truffleisolate-' + mode + '-latest-' + platform.os + '-' + platform.arch,
      }
    )
    for mode in truffle_isolate_modes
    for platform in truffle_isolate_platforms
  ],
  local truffle_isolate_oracle_jdk_versions = ['21', '25'],
  local truffle_isolate_oraclejdk_unittest_jobs = [
    (
      local timelimit = '1:00:00';
      vm.vm_java_Latest + vm_common.vm_base('linux', 'amd64', 'daily') + self.truffleisolate_oraclejdk_gate(jdk_version, timelimit) + {
        name: 'daily-vm-truffleisolate-internal-oraclejdk' + jdk_version + '-fallback-linux-amd64',
      }
    )
    for jdk_version in truffle_isolate_oracle_jdk_versions
  ],
  local truffle_isolate_maven_jobs = [
    (
      local explicit_target = 'weekly';
      local explicit_capabilities = if platform.os == 'windows' && mode == 'external' then { capabilities: ['windows_11'] } else {};
      local timelimit = '4:00:00';
      local jdk_hint = if platform.os == 'windows' then 'Latest' else null;
      vm.vm_java_Latest + vm_common.vm_base(platform.os, platform.arch, explicit_target, jdk_hint=jdk_hint)  + self.truffle_unchained_isolate(mode, platform.os, timelimit) + explicit_capabilities + {
        name: explicit_target + '-vm-ce-truffle-maven-isolate-' + mode + '-latest-' + platform.os + '-' + platform.arch,
      }
    )
    for mode in truffle_isolate_modes
    for platform in truffle_isolate_platforms
  ],

  local builds = [
    vm.vm_java_Latest + graal_common.deps.svm + graal_common.deps.sulong + graal_common.deps.graalpy + vm.custom_vm + vm_common.vm_base('linux', 'amd64', 'tier3') + {
     run+: [
       ['mx', '--env', vm.edition, '--native-images=true', '--dy', 'graalpython', 'gate', '-B--targets=GRAALPY_NATIVE_STANDALONE', '--no-warning-as-error', '--tags', 'build,python'],
     ],
     notify_groups: ["python"],
     timelimit: '45:00',
     name: 'gate-vm-native-graalpython-linux-amd64',
    },
    vm.vm_java_Latest + vm_common.vm_base('linux', 'amd64', 'tier3')  + truffle_native_tck,
    vm.vm_java_Latest + vm_common.vm_base('linux', 'amd64', 'tier3')  + truffle_native_tck_wasm,
    vm.vm_java_Latest + vm_common.vm_base('linux', 'amd64', 'tier3')  + truffle_maven_downloader,
  ] + truffle_isolate_unittest_jobs + truffle_isolate_oraclejdk_unittest_jobs + truffle_isolate_maven_jobs,

  builds: utils.add_defined_in(builds, std.thisFile),
}
