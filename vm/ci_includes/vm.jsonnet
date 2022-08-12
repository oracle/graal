local composable = (import '../../common-utils.libsonnet').composable;
local vm_common = import '../ci_common/common.jsonnet';
local vm_common_bench = import '../ci_common/common-bench.jsonnet';
local vm = import 'vm.jsonnet';
local vm_bench = import 'vm-bench.jsonnet';
local vm_native = import 'vm-native.jsonnet';
local graal_common = import '../../common.jsonnet';
local common_json = composable(import '../../common.json');
local jdks = common_json.jdks;

{
  vm_java_11:: graal_common.labsjdk11 + vm_common.vm_env_mixin('11'),
  vm_java_17:: graal_common.labsjdk17 + vm_common.vm_env_mixin('17'),
  vm_java_19:: graal_common.labsjdk19 + vm_common.vm_env_mixin('19'),

  vm_java_11_llvm:: self.vm_java_11 + graal_common['labsjdk-ce-11-llvm'],
  vm_java_17_llvm:: self.vm_java_17 + graal_common['labsjdk-ce-17-llvm'],

  vm_java_19_llvm:: self.vm_java_19 + {
    downloads+: {
      LLVM_JAVA_HOME: jdks['labsjdk-ce-19-llvm'],
    },
  },

  binaries_repository: 'lafo',
  svm_suite:: '/substratevm',
  libgraal_env: 'libgraal',
  custom_vm_linux: {},
  custom_vm_darwin: {},
  vm_profiles:: [],
  collect_profiles():: [],

  mx_cmd_base_no_env: ['mx'],

  check_structure: {},

  check_graalvm_base_build(os, arch, java_version): [],

  check_graalvm_complete_build: [],

  vm_setup:: {
    short_name:: 'ce',
    setup+: [
      ['set-export', 'VM_ENV', 'ce'],
      ['set-export', 'RELEASE_CATALOG', 'https://www.graalvm.org/component-catalog/v2/graal-updater-component-catalog-java${BASE_JDK_SHORT_VERSION}.properties|{ee=GraalVM Enterprise Edition}rest://gds.oracle.com/api/20220101/|gds://oca.opensource.oracle.com/gds/meta-data.json'],
      ['set-export', 'RELEASE_PRODUCT_ID', 'D53FAE8052773FFAE0530F15000AA6C6'],
      ['set-export', 'SNAPSHOT_CATALOG', ['mx', 'urlrewrite', 'http://www.graalvm.org/catalog/ce/java${BASE_JDK_SHORT_VERSION}']],
      ['cd', 'vm'],
    ],
  },

  maven_11_17:: {
    downloads+: {
      JAVA_HOME: jdks['labsjdk-ce-11'],
      EXTRA_JAVA_HOMES: jdks['labsjdk-ce-17'],
    },
    mx_cmd_base:: ['mx', '--dynamicimports', '/tools,/compiler,/graal-js,/espresso,/substratevm', '--disable-installables=true', '--force-bash-launcher=true', '--skip-libraries=true'],
    build:: self.mx_cmd_base + ['build'],
    deploy:: self.mx_cmd_base + ['--suite', 'compiler', '--suite', 'truffle', '--suite', 'sdk', '--suite', 'tools', '--suite', 'regex', '--suite', 'graal-js', '--suite', 'espresso', '--suite', 'substratevm', 'maven-deploy', '--skip', $.maven_11_17_only_native.native_distributions, '--tags=default', '--all-distribution-types', '--validate', 'full', '--licenses', 'GPLv2-CPE,UPL,MIT'],
  },

  maven_11_17_only_native:: self.maven_11_17 + {
    native_distributions:: 'TRUFFLE_NFI_NATIVE,SVM_HOSTED_NATIVE',
    mx_cmd_base:: ['mx', '--dynamicimports', '/substratevm', '--disable-installables=true', '--force-bash-launcher=true', '--skip-libraries=true'],
    build:: self.mx_cmd_base + ['build', '--dependencies', self.native_distributions],
    deploy:: self.mx_cmd_base + ['maven-deploy', '--only', self.native_distributions, '--tags=default', '--all-suites', '--all-distribution-types', '--validate', 'full', '--licenses', 'GPLv2-CPE,UPL,MIT'],
  },

  notify_releaser_build: vm_common.common_vm_linux + graal_common.linux_amd64 + {
    name: 'daily-vm-notify-releaser-build-linux-amd64',
    packages+: {
      curl: '>=7.50.1',
      git: '>=1.8.3',
    },
    run+: [
        ['test', ['git', 'rev-parse', '--abbrev-ref', 'HEAD'], '!=', 'master', '||'] + self.ci_resources.infra.notify_releaser_service,
    ],
    requireArtifacts: [
      {name: 'post-merge-deploy-vm-java11-linux-amd64'},
      {name: 'post-merge-deploy-vm-java17-linux-amd64'},
      {name: 'daily-deploy-vm-java11-linux-aarch64'},
      {name: 'daily-deploy-vm-java17-linux-aarch64'},
      {name: 'daily-deploy-vm-base-java11-darwin-amd64'},
      {name: 'daily-deploy-vm-installable-java11-darwin-amd64'},
      {name: 'daily-deploy-vm-base-java17-darwin-amd64'},
      {name: 'daily-deploy-vm-installable-java17-darwin-amd64'},
      {name: 'daily-deploy-vm-base-java11-darwin-aarch64'},
      {name: 'daily-deploy-vm-installable-java11-darwin-aarch64'},
      {name: 'daily-deploy-vm-base-java17-darwin-aarch64'},
      {name: 'daily-deploy-vm-installable-java17-darwin-aarch64'},
      {name: 'daily-deploy-vm-base-java11-windows-amd64'},
      {name: 'daily-deploy-vm-installable-java11-windows-amd64'},
      {name: 'daily-deploy-vm-base-java17-windows-amd64'},
      {name: 'daily-deploy-vm-installable-java17-windows-amd64'},
      {name: 'daily-deploy-vm-ruby-java11-linux-amd64'},
      {name: 'daily-deploy-vm-ruby-java11-darwin-amd64'},
      {name: 'daily-deploy-vm-ruby-java11-darwin-aarch64'},
    ],
    targets+: ['daily'],
  },

  deploy_vm_publish_releaser_artifact(build): build + {
    publishArtifacts: [
      {
        name: build.name,
        patterns: [build.name]
      }
    ]
  },

  diskspace_required: {
    java11_linux_mad64: "30GB",
    java17_linux_mad64: "30GB",
  },

  local builds = [
    self.vm_java_11 + vm_common.gate_vm_linux_amd64 + {
     run: [
       ['mx', 'build'],
       ['mx', 'unittest', '--suite', 'vm'],
     ],
     name: 'gate-vm-unittest-linux-amd64',
    },
    self.vm_java_11 + common_json.devkits['windows-jdk11'] + vm_common.gate_vm_windows + {
     run: [
         ['mx', 'build'],
         ['mx', 'unittest', '--suite', 'vm'],
     ],
     name: 'gate-vm-unittest-windows-amd64',
    },
    self.vm_java_11 + vm_common.gate_vm_linux_amd64 + vm_common.sulong_linux + {
     environment+: {
       DYNAMIC_IMPORTS: '/tools,/substratevm,/sulong',
       NATIVE_IMAGES: 'polyglot',
     },
     run: [
       ['rm', '-rf', '../.git'],
       ['mx', 'gate', '--strict-mode', '--tags', 'build'],
     ],
     name: 'gate-vm-build-without-vcs-linux-amd64',
    },
    vm_common.linux_deploy + vm_common.gate_vm_linux_amd64 + self.maven_11_17 + vm_common.sulong_linux + {
     run: [
       $.maven_11_17.build,
       $.maven_11_17.deploy + ['--dry-run', 'lafo-maven'],
     ],
     name: 'gate-vm-maven-dry-run-linux-amd64',
    },
    vm_common.linux_deploy + vm_common.gate_vm_linux_amd64 + self.maven_11_17 + vm_common.sulong_linux + {
     downloads+: {
       OPEN_JDK_11: common_json.jdks.openjdk11,
     },
     run: [
       $.maven_11_17.build,
       $.maven_11_17.deploy + ['--version-string', 'GATE'],
       ['set-export', 'JAVA_HOME', '$OPEN_JDK_11'],
       ['git', 'clone', '--depth', '1', ['mx', 'urlrewrite', 'https://github.com/graalvm/graal-js-jdk11-maven-demo.git'], 'graal-js-jdk11-maven-demo'],
       ['cd', 'graal-js-jdk11-maven-demo'],
       ['mvn', '-Dgraalvm.version=GATE', 'package'],
       ['mvn', '-Dgraalvm.version=GATE', 'exec:exec'],
     ],
     name: 'gate-vm-js-on-jdk11-maven-linux-amd64',
    },
    vm_common.linux_deploy + vm_common.deploy_vm_linux_amd64 + self.maven_11_17 + vm_common.sulong_linux + {
     run: [
       $.maven_11_17.build,
       $.maven_11_17.deploy + ['lafo-maven'],
     ],
     name: 'post-merge-deploy-vm-maven-linux-amd64',
     timelimit: '45:00',
    },
    vm_common.linux_deploy + vm_common.gate_vm_linux_aarch64 + self.maven_11_17_only_native + {
     run: [
       $.maven_11_17_only_native.build,
       $.maven_11_17_only_native.deploy + ['--dry-run', 'lafo-maven'],
     ],
     name: 'gate-vm-maven-dry-run-linux-aarch64',
    },
    vm_common.linux_deploy + vm_common.deploy_vm_linux_aarch64 + self.maven_11_17_only_native + {
     run: [
       $.maven_11_17_only_native.build,
       $.maven_11_17_only_native.deploy + ['lafo-maven'],
     ],
     name: 'post-merge-deploy-vm-maven-linux-aarch64',
    },
    vm_common.darwin_deploy + vm_common.gate_vm_darwin_amd64 + self.maven_11_17_only_native + {
     run: [
       $.maven_11_17_only_native.build,
       $.maven_11_17_only_native.deploy + ['--dry-run', 'lafo-maven'],
     ],
     name: 'gate-vm-maven-dry-run-darwin-amd64',
    },
    vm_common.darwin_deploy + vm_common.gate_vm_darwin_aarch64 + self.maven_11_17_only_native + {
     run: [
       $.maven_11_17_only_native.build,
       $.maven_11_17_only_native.deploy + ['--dry-run', 'lafo-maven'],
     ],
     name: 'gate-vm-maven-dry-run-darwin-aarch64',
    },
    vm_common.darwin_deploy + vm_common.deploy_daily_vm_darwin_amd64 + self.maven_11_17_only_native + {
     run: [
       $.maven_11_17_only_native.build,
       $.maven_11_17_only_native.deploy + ['lafo-maven'],
     ],
     name: 'daily-deploy-vm-maven-darwin-amd64',
    },
    vm_common.darwin_deploy + vm_common.deploy_daily_vm_darwin_aarch64 + self.maven_11_17_only_native + {
     run: [
       $.maven_11_17_only_native.build,
       $.maven_11_17_only_native.deploy + ['lafo-maven'],
     ],
     name: 'daily-deploy-vm-maven-darwin-aarch64',
    },
    vm_common.svm_common_windows_jdk11 + vm_common.gate_vm_windows + self.maven_11_17_only_native + {
     run: [
       $.maven_11_17_only_native.build,
       $.maven_11_17_only_native.deploy + ['--dry-run', 'lafo-maven'],
     ],
     name: 'gate-vm-maven-dry-run-windows-amd64',
    },
    vm_common.svm_common_windows_jdk11 + vm_common.deploy_daily_vm_windows + self.maven_11_17_only_native + {
     run: [
       $.maven_11_17_only_native.build,
       $.maven_11_17_only_native.deploy + ['lafo-maven'],
     ],
     name: 'daily-deploy-vm-maven-windows-amd64',
    },

    #
    # Deploy GraalVM Base and Installables
    #

    # Linux/AMD64
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_java11_linux_amd64),
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_java17_linux_amd64),

    # Linux/AARCH64
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_java11_linux_aarch64),
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_java17_linux_aarch64),

    # Darwin/AMD64
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_base_java11_darwin_amd64),
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_installable_java11_darwin_amd64),
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_base_java17_darwin_amd64),
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_installable_java17_darwin_amd64),

    # Darwin/AARCH64
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_base_java11_darwin_aarch64),
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_installable_java11_darwin_aarch64),
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_base_java17_darwin_aarch64),
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_installable_java17_darwin_aarch64),

    # Windows/AMD64
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_base_java11_windows_amd64),
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_installable_java11_windows_amd64),
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_base_java17_windows_amd64),
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_installable_java17_windows_amd64),

    #
    # Deploy the GraalVM Ruby image (GraalVM Base + ruby - js)
    #
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_ruby_java11_linux_amd64),
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_ruby_java11_darwin_amd64),
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_ruby_java11_darwin_aarch64),

    # Trigger the releaser service
    self.notify_releaser_build,
  ],

  builds: [vm_common.verify_name(b1) for b1 in vm_common.builds + vm_common_bench.builds + vm_bench.builds + vm_native.builds + [{'defined_in': std.thisFile} + b2  for b2 in builds]],
}
