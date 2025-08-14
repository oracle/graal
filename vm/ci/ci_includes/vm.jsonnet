local utils = import '../../../ci/ci_common/common-utils.libsonnet';
local vm_common_runspec = import '../ci_common/common-runspec.jsonnet';
local vm_common = import '../ci_common/common.jsonnet';
local vm_common_bench = import '../ci_common/common-bench.jsonnet';
local vm = import 'vm.jsonnet';
local vm_bench = import 'vm-bench.jsonnet';
local vm_native = import 'vm-native.jsonnet';
local graal_common = import '../../../ci/ci_common/common.jsonnet';

{
  vm_java_21:: graal_common.labsjdk21 + vm_common.vm_env_mixin('21'),
  vm_java_Latest::
    local jdk = graal_common.labsjdkLatest;
    jdk + vm_common.vm_env_mixin(std.toString(jdk.jdk_version)),

  deploy_espress_standalone: false,
  vm_java_21_llvm:: self.vm_java_21 + graal_common['labsjdk-ce-21-llvm'],
  vm_java_Latest_llvm:: self.vm_java_Latest + graal_common['labsjdk-ce-latest-llvm'],

  binaries_repository: 'lafo',
  maven_deploy_repository: 'lafo-maven',
  edition:: 'ce',
  vm_dir:: 'vm',
  svm_suite:: '/substratevm',
  libgraal_env: 'libgraal',
  custom_vm: {},
  vm_profiles:: [],
  collect_profiles(mx_prefix=vm_common.mx_vm_common):: [],

  mx_cmd_base_no_env: ['mx'],

  check_structure: {},

  check_graalvm_base_build(path, os, arch, java_version): [],

  vm_setup:: {
    short_name:: $.edition,
    setup+: [
      ['set-export', 'VM_ENV', self.short_name],
      ['cd', $.vm_dir],
    ],
  },

  vm_notifier_daily: vm_common.vm_base('linux', 'amd64', 'daily', deploy=true) + {
    name: 'daily-deploy-vm-notifier-linux-amd64',
    packages+: {
      curl: '==7.50.1',
    },
    run+: [
      ['test', ['git', 'rev-parse', '--abbrev-ref', 'HEAD'], '!=', 'master', '||'] + self.ci_resources.infra.notify_releaser_service,
      ['test', ['git', 'rev-parse', '--abbrev-ref', 'HEAD'], '!=', 'master', '||'] + self.ci_resources.infra.notify_indexer_service('java-latest', 'ce'),
    ],
    runAfter: [
      'post-merge-deploy-vm-base-java-latest-linux-amd64',
      'daily-deploy-vm-standalones-java-latest-linux-amd64',
      'daily-deploy-vm-base-java-latest-linux-aarch64',
      'daily-deploy-vm-standalones-java-latest-linux-aarch64',
      'daily-deploy-vm-base-java-latest-darwin-amd64',
      'daily-deploy-vm-standalones-java-latest-darwin-amd64',
      'daily-deploy-vm-base-java-latest-darwin-aarch64',
      'daily-deploy-vm-standalones-java-latest-darwin-aarch64',
      'daily-deploy-vm-base-java-latest-windows-amd64',
      'daily-deploy-vm-standalones-java-latest-windows-amd64',
      'daily-deploy-vm-maven-linux-amd64',
    ],
    notify_groups:: ['deploy'],
    timelimit: '30:00',
  },

  maven_deploy_base_functions: {
    edition:: vm.edition,

    mx_args(os, arch, reduced=false):: ['--native-images=false'],

    dynamic_imports(os, arch)::
      ['--dynamicimports', vm_common.maven_deploy_base_functions.dynamic_ce_imports(os, arch)],

    ee_suites(os, arch)::
      error 'The vm suite does not define ee suites',

    ee_licenses()::
      error 'The vm suite does not define ee licenses',

    reduced_ce_dists:: error 'The vm suite does not define reduced dists',
    reduced_ee_dists:: error 'The vm suite does not define reduced dists',
  },

  local builds = [
    self.vm_java_Latest + vm_common.vm_base('linux', 'amd64', 'post-merge') + graal_common.deps.sulong + {
     environment+: {
       DYNAMIC_IMPORTS: '/tools,/substratevm,/sulong',
       NATIVE_IMAGES: 'polyglot',
     },
     run: [
       ['rm', '-rf', '../.git'],
       ['mx', 'gate', '--strict-mode', '--tags', 'build'],
     ],
     name: 'post-merge-vm-build-without-vcs-linux-amd64',
     timelimit: '30:00',
     notify_groups:: ['deploy'],
    },

    # Linux/AMD64
    vm_common.graalvm_complete_build_deps('ce', 'linux', 'amd64', java_version='latest') + vm_common.linux_deploy + vm_common.vm_base('linux', 'amd64', 'daily', deploy=true) + vm_common.maven_deploy_base_functions.base_object('linux', 'amd64', dry_run=false, remote_mvn_repo=$.maven_deploy_repository, remote_non_mvn_repo=$.binaries_repository, local_repo='local') + {
      name: 'daily-deploy-vm-maven-linux-amd64',
      timelimit: '1:00:00',
      notify_groups:: ['deploy'],
    },
    # Linux/AARCH64
    vm_common.graalvm_complete_build_deps('ce', 'linux', 'aarch64', java_version='latest') + vm_common.linux_deploy + vm_common.vm_base('linux', 'aarch64', 'daily', deploy=true) + vm_common.maven_deploy_base_functions.base_object('linux', 'aarch64', dry_run=false, remote_mvn_repo=$.maven_deploy_repository, remote_non_mvn_repo=$.binaries_repository, local_repo='local') + {
      name: 'daily-deploy-vm-maven-linux-aarch64',
      timelimit: '1:00:00',
      notify_groups:: ['deploy'],
    },
    # Darwin/AMD64
    vm_common.graalvm_complete_build_deps('ce', 'darwin', 'amd64', java_version='latest') + vm_common.darwin_deploy + vm_common.vm_base('darwin', 'amd64', 'daily', deploy=true, jdk_hint='Latest') + vm_common.maven_deploy_base_functions.base_object('darwin', 'amd64', dry_run=false, remote_mvn_repo=$.maven_deploy_repository, remote_non_mvn_repo=$.binaries_repository, local_repo='local') + {
      name: 'daily-deploy-vm-maven-darwin-amd64',
      capabilities+: ['darwin_bigsur'],
      timelimit: '1:00:00',
      notify_groups:: ['deploy'],
    },
    # Darwin/AARCH64
    vm_common.graalvm_complete_build_deps('ce', 'darwin', 'aarch64', java_version='latest') + vm_common.darwin_deploy + vm_common.vm_base('darwin', 'aarch64', 'daily', deploy=true) + vm_common.maven_deploy_base_functions.base_object('darwin', 'aarch64', dry_run=false, remote_mvn_repo=$.maven_deploy_repository, remote_non_mvn_repo=$.binaries_repository, local_repo='local') + {
      name: 'daily-deploy-vm-maven-darwin-aarch64',
      capabilities+: ['darwin_bigsur'],
      timelimit: '1:00:00',
      notify_groups:: ['deploy'],
    },
    # Windows/AMD64
    vm_common.graalvm_complete_build_deps('ce', 'windows', 'amd64', java_version='latest') + vm_common.deploy_build + vm_common.vm_base('windows', 'amd64', 'daily', deploy=true, jdk_hint='Latest') + vm_common.maven_deploy_base_functions.base_object('windows', 'amd64', dry_run=false, remote_mvn_repo=$.maven_deploy_repository, remote_non_mvn_repo=$.binaries_repository, local_repo='local') + {
      name: 'daily-deploy-vm-maven-windows-amd64',
      timelimit: '1:00:00',
      notify_groups:: ['deploy'],
    },

    #
    # Update the `stable` mx branch with the currently imported revision
    #
    vm_common.vm_base('linux', 'amd64', 'post-merge') + {
      run: [
        ['set-export', 'BRANCH_NAME', ['git', 'rev-parse', '--abbrev-ref', 'HEAD']],
        ['bash', '-c', 'if [[ ${BRANCH_NAME} == master ]] || [[ ${BRANCH_NAME} == release/* ]] || [[ ${BRANCH_NAME} == cpu/* ]]; then git -C ${MX_HOME} push origin +HEAD:refs/heads/graal/${BRANCH_NAME}; fi']
      ],
      name: 'post-merge-vm-update-stable-mx-branch-linux-amd64',
      timelimit: '30:00',
      notify_groups:: ['deploy'],
    },


    #
    # Deploy Truffle Languages Standalones
    # NOTE: After adding or removing deploy jobs, please make sure you modify ce-release-artifacts.json accordingly.
    #

    # Linux/AMD64
    vm_common.deploy_vm_standalones_javaLatest_linux_amd64,
    # Linux/AARCH64
    vm_common.deploy_vm_standalones_javaLatest_linux_aarch64,
    # Darwin/AMD64
    vm_common.deploy_vm_standalones_javaLatest_darwin_amd64,
    # Darwin/AARCH64
    vm_common.deploy_vm_standalones_javaLatest_darwin_aarch64,
    # Windows/AMD64
    vm_common.deploy_vm_standalones_javaLatest_windows_amd64,
    # Trigger the releaser service and notify the indexer
    self.vm_notifier_daily,
  ],

  builds: [vm_common.verify_name(b) for b in vm_common.builds + vm_common_runspec.builds + vm_common_bench.builds + vm_bench.builds + vm_native.builds + utils.add_defined_in(builds, std.thisFile)],

  compiler_gate:: (import '../../../compiler/ci/ci_common/gate.jsonnet')
}
