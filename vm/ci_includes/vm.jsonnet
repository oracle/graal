local composable = (import '../../common-utils.libsonnet').composable;
local vm_common = import '../ci_common/common.jsonnet';
local vm_common_bench = import '../ci_common/common-bench.jsonnet';
local vm_bench = import 'vm-bench.jsonnet';
local vm_native = import 'vm-native.jsonnet';
local graal_common = import '../../common.jsonnet';
local common_json = composable(import '../../common.json');
local jdks = common_json.jdks;

{
  vm_java_8:: graal_common.openjdk8 + {
    environment+: {
      BASE_JDK_NAME: jdks.openjdk8.name,
      BASE_JDK_VERSION: jdks.openjdk8.version,
      BASE_JDK_SHORT_VERSION: '8',
    },
  },
  vm_java_11:: graal_common.labsjdk11 + {
    environment+: {
      BASE_JDK_NAME: jdks['labsjdk-ce-11'].name,
      BASE_JDK_VERSION: jdks['labsjdk-ce-11'].version,
      BASE_JDK_SHORT_VERSION: '11',
    },
  },
  vm_java_17:: graal_common.labsjdk17 + {
    environment+: {
      BASE_JDK_NAME: jdks['labsjdk-ce-17'].name,
      BASE_JDK_VERSION: jdks['labsjdk-ce-17'].version,
      BASE_JDK_SHORT_VERSION: '17',
    },
  },

  vm_common_windows_jdk8:: vm_common.svm_common_windows_openjdk8,

  vm_linux_amd64_java_11:: self.vm_java_11 + {
    downloads+: {
      LLVM_JAVA_HOME: jdks['labsjdk-ce-11-llvm'],
    },
  },

  vm_linux_amd64_java_17:: self.vm_java_17 + {
    downloads+: {
      LLVM_JAVA_HOME: jdks['labsjdk-ce-17-llvm'],
    },
  },

  binaries_repository: 'lafo',
  svm_suite:: '/substratevm',
  libgraal_env: 'libgraal',
  custom_vm_linux: {},
  custom_vm_darwin: {},
  vm_profiles:: [],
  collect_profiles:: [],

  vm_setup:: {
    short_name:: 'ce',
    setup+: [
      ['set-export', 'VM_ENV', 'ce'],
      ['set-export', 'RELEASE_CATALOG', '{ee=GraalVM Enterprise Edition}gds://oca.opensource.oracle.com/gds/meta-data.json|https://www.graalvm.org/component-catalog/v2/graal-updater-component-catalog-java${BASE_JDK_SHORT_VERSION}.properties'],
      ['set-export', 'SNAPSHOT_CATALOG', ['mx', 'urlrewrite', 'http://www.graalvm.org/catalog/ce/java${BASE_JDK_SHORT_VERSION}']],
      ['cd', 'vm'],
    ],
  },

  maven_base_8_11:: {
    downloads+: {
      JAVA_HOME: jdks.openjdk8,
      EXTRA_JAVA_HOMES: jdks['labsjdk-ce-11'],
    },
    mx_cmd_base:: ['mx', '--dynamicimports', '/tools,/compiler,/graal-js,/espresso', '--disable-installables=true'],
    build:: self.mx_cmd_base + ['build'],
    deploy:: self.mx_cmd_base + ['--suite', 'compiler', '--suite', 'truffle', '--suite', 'sdk', '--suite', 'tools', '--suite', 'regex', '--suite', 'graal-js', '--suite', 'espresso', 'maven-deploy', '--tags=default', '--all-distribution-types', '--validate', 'full', '--licenses', 'GPLv2-CPE,UPL,MIT'],
  },

  maven_base_native:: {
    native_distributions:: 'TRUFFLE_NFI_NATIVE,SVM_HOSTED_NATIVE',
    mx_cmd_base:: ['mx', '--dynamicimports', '/substratevm', '--disable-installables=true', '--force-bash-launcher=true', '--skip-libraries=true'],
    build:: self.mx_cmd_base + ['build', '--dependencies', self.native_distributions],
    deploy:: self.mx_cmd_base + ['maven-deploy', '--only', self.native_distributions, '--tags=default', '--all-suites', '--all-distribution-types', '--validate', 'full', '--licenses', 'GPLv2-CPE,UPL,MIT'],
  },

  maven_base_8_native:: self.maven_base_native + {
    downloads+: {
      JAVA_HOME: jdks.openjdk8,
    },
  },

  maven_base_11_native:: self.maven_base_native + {
    downloads+: {
      JAVA_HOME: jdks['labsjdk-ce-11'],
    },
  },

  vm_unittest:: {
    environment+: {
      MX_TEST_RESULTS_PATTERN: 'es-XXX.json',
      MX_TEST_RESULT_TAGS: 'vm',
    },
  },

  notify_releaser_build: vm_common.common_vm_linux + graal_common.linux_amd64 + {
    name: 'notify-releaser-build',
    packages+: {
      curl: '>=7.50.1',
      git: '>=1.8.3',
    },
    run+: [
      [
        ['test', ['git', 'rev-parse', '--abbrev-ref', 'HEAD'], '!=', 'master', '||'] + self.ci_resources.infra.notify_releaser_service,
      ]
    ],
    requireArtifacts: [
      {name: 'deploy-vm-java11-linux-amd64'},
      {name: 'deploy-vm-java17-linux-amd64'},
      {name: 'deploy-vm-java11-linux-aarch64'},
      {name: 'deploy-vm-java17-linux-aarch64'},
      {name: 'deploy-vm-base-java11-darwin-amd64'},
      {name: 'deploy-vm-installable-java11-darwin-amd64'},
      {name: 'deploy-vm-base-java17-darwin-amd64'},
      {name: 'deploy-vm-installable-java17-darwin-amd64'},
      {name: 'deploy-vm-base-java11-windows-amd64'},
      {name: 'deploy-vm-installable-java11-windows-amd64'},
      {name: 'deploy-vm-base-java17-windows-amd64'},
      {name: 'deploy-vm-installable-java17-windows-amd64'},
      {name: 'deploy-vm-ruby-java11-linux-amd64'},
      {name: 'deploy-vm-ruby-java11-darwin-amd64'},
    ],
    targets+: ['daily'],
  },

  builds: vm_common.builds + vm_common_bench.builds + vm_bench.builds + vm_native.builds + [
    self.vm_java_8 + vm_common.gate_vm_linux_amd64 + self.vm_unittest + {
      run: [
        ['mx', 'build'],
        ['mx', 'unittest', '--suite', 'vm'],
      ],
      name: 'gate-vm-unittest-linux-amd64',
    },
    graal_common.oraclejdk8 + common_json.devkits['windows-oraclejdk8'] + vm_common.gate_vm_windows + self.vm_unittest + {
      run: [
          ['mx', 'build'],
          ['mx', 'unittest', '--suite', 'vm'],
      ],
      name: 'gate-vm-unittest-windows',
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
      name: 'gate-vm-build-without-vcs',
    },
    vm_common.linux_deploy + vm_common.gate_vm_linux_amd64 + self.maven_base_8_11 + vm_common.sulong_linux + {
      run: [
        $.maven_base_8_11.build,
        $.maven_base_8_11.deploy + ['--dry-run', 'lafo-maven'],
      ],
      name: 'gate-vm-maven-dry-run-linux-amd64',
    },
    vm_common.linux_deploy + vm_common.gate_vm_linux_amd64 + self.maven_base_8_11 + vm_common.sulong_linux + {
      downloads+: {
        OPEN_JDK_11: common_json.jdks.openjdk11,
      },
      run: [
        $.maven_base_8_11.build,
        $.maven_base_8_11.deploy + ['--version-string', 'GATE'],
        ['set-export', 'JAVA_HOME', '$OPEN_JDK_11'],
        ['git', 'clone', '--depth', '1', ['mx', 'urlrewrite', 'https://github.com/graalvm/graal-js-jdk11-maven-demo.git'], 'graal-js-jdk11-maven-demo'],
        ['cd', 'graal-js-jdk11-maven-demo'],
        ['mvn', '-Dgraalvm.version=GATE', 'package'],
        ['mvn', '-Dgraalvm.version=GATE', 'exec:exec'],
      ],
      name: 'gate-vm-js-on-jdk11-maven-linux-amd64',
    },
    vm_common.linux_deploy + vm_common.deploy_vm_linux_amd64 + self.maven_base_8_11 + vm_common.sulong_linux + {
      run: [
        $.maven_base_8_11.build,
        $.maven_base_8_11.deploy + ['lafo-maven'],
      ],
      name: 'deploy-vm-maven-linux-amd64',
      timelimit: '45:00',
    },
    vm_common.linux_deploy + vm_common.gate_vm_linux_aarch64 + self.maven_base_11_native + {
      run: [
        $.maven_base_11_native.build,
        $.maven_base_11_native.deploy + ['--dry-run', 'lafo-maven'],
      ],
      name: 'gate-vm-maven-dry-run-linux-aarch64',
    },
    vm_common.linux_deploy + vm_common.deploy_vm_linux_aarch64 + self.maven_base_11_native + {
      run: [
        $.maven_base_11_native.build,
        $.maven_base_11_native.deploy + ['lafo-maven'],
      ],
      name: 'deploy-vm-maven-linux-aarch64',
    },
    vm_common.darwin_deploy + vm_common.gate_vm_darwin + self.maven_base_11_native + {
      run: [
        $.maven_base_11_native.build,
        $.maven_base_11_native.deploy + ['--dry-run', 'lafo-maven'],
      ],
      name: 'gate-vm-maven-dry-run-darwin-amd64',
    },
    vm_common.darwin_deploy + vm_common.deploy_daily_vm_darwin + self.maven_base_11_native + {
      run: [
        $.maven_base_11_native.build,
        $.maven_base_11_native.deploy + ['lafo-maven'],
      ],
      name: 'deploy-vm-maven-darwin-amd64',
    },
    self.vm_common_windows_jdk8 + vm_common.gate_vm_windows + self.maven_base_8_native + {
      run: [
        $.maven_base_8_native.build,
        $.maven_base_8_native.deploy + ['--dry-run', 'lafo-maven'],
      ],
      name: 'gate-vm-maven-dry-run-windows-amd64',
    },
    self.vm_common_windows_jdk8 + vm_common.deploy_daily_vm_windows + self.maven_base_8_native + {
      run: [
        $.maven_base_8_native.build,
        $.maven_base_8_native.deploy + ['lafo-maven'],
      ],
      name: 'deploy-vm-maven-windows-amd64',
    },

    #
    # Deploy GraalVM Base and Installables
    #

    # Linux/AMD64
    vm_common.deploy_vm_java11_linux_amd64 + {publishArtifacts: [{name: 'deploy-vm-java11-linux-amd64', patterns: ['deploy-vm-java11-linux-amd64']}]},
    vm_common.deploy_vm_java17_linux_amd64 + {publishArtifacts: [{name: 'deploy-vm-java17-linux-amd64', patterns: ['deploy-vm-java17-linux-amd64']}]},

    # Linux/AARCH64
    vm_common.deploy_vm_java11_linux_aarch64 + {publishArtifacts: [{name: 'deploy-vm-java11-linux-aarch64', patterns: ['deploy-vm-java11-linux-aarch64']}]},
    vm_common.deploy_vm_java17_linux_aarch64 + {publishArtifacts: [{name: 'deploy-vm-java17-linux-aarch64', patterns: ['deploy-vm-java17-linux-aarch64']}]},

    # Darwin/AMD64
    vm_common.deploy_vm_base_java11_darwin_amd64 + {publishArtifacts: [{name: 'deploy-vm-base-java11-darwin-amd64', patterns: ['deploy-vm-base-java11-darwin-amd64']}]},
    vm_common.deploy_vm_installable_java11_darwin_amd64 + {publishArtifacts: [{name: 'deploy-vm-installable-java11-darwin-amd64', patterns: ['deploy-vm-installable-java11-darwin-amd64']}]},
    vm_common.deploy_vm_base_java17_darwin_amd64 + {publishArtifacts: [{name: 'deploy-vm-base-java17-darwin-amd64', patterns: ['deploy-vm-base-java17-darwin-amd64']}]},
    vm_common.deploy_vm_installable_java17_darwin_amd64 + {publishArtifacts: [{name: 'deploy-vm-installable-java17-darwin-amd64', patterns: ['deploy-vm-installable-java17-darwin-amd64']}]},

    # Windows/AMD64
    vm_common.deploy_vm_base_java11_windows_amd64 + {publishArtifacts: [{name: 'deploy-vm-base-java11-windows-amd64', patterns: ['deploy-vm-base-java11-windows-amd64']}]},
    vm_common.deploy_vm_installable_java11_windows_amd64 + {publishArtifacts: [{name: 'deploy-vm-installable-java11-windows-amd64', patterns: ['deploy-vm-installable-java11-windows-amd64']}]},
    vm_common.deploy_vm_base_java17_windows_amd64 + {publishArtifacts: [{name: 'deploy-vm-base-java17-windows-amd64', patterns: ['deploy-vm-base-java17-windows-amd64']}]},
    vm_common.deploy_vm_installable_java17_windows_amd64 + {publishArtifacts: [{name: 'deploy-vm-installable-java17-windows-amd64', patterns: ['deploy-vm-installable-java17-windows-amd64']}]},

    #
    # Deploy the GraalVM Ruby image (GraalVM Base + ruby - js)
    #
    vm_common.deploy_vm_ruby_java11_linux_amd64 + {publishArtifacts: [{name: 'deploy-vm-ruby-java11-linux-amd64', patterns: ['deploy-vm-ruby-java11-linux-amd64']}]},
    vm_common.deploy_vm_ruby_java11_darwin_amd64 + {publishArtifacts: [{name: 'deploy-vm-ruby-java11-darwin-amd64', patterns: ['deploy-vm-ruby-java11-darwin-amd64']}]},

    #
    # Deploy GraalVM Complete
    #
    vm_common.deploy_vm_complete_java11_linux_amd64,
    vm_common.deploy_vm_complete_java11_darwin_amd64,

    # Trigger the releaser service
    self.notify_releaser_build,
  ],
}