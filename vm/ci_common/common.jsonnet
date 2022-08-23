local composable = (import '../../common-utils.libsonnet').composable;
local vm = import '../ci_includes/vm.jsonnet';
local graal_common = import '../../common.jsonnet';
local repo_config = import '../../repo-configuration.libsonnet';
local common_json = composable(import '../../common.json');
local devkits = common_json.devkits;

{
  verify_name(build): build + {
    expected_prefix:: std.join('-', [target for target in build.targets if target != "mach5"]) + '-vm',
    expected_suffix:: build.os + '-' + build.arch,
    assert std.startsWith(build.name, self.expected_prefix) : "'%s' is defined in '%s' with '%s' targets but does not start with '%s'" % [build.name, build.defined_in, build.targets, self.expected_prefix],
    assert std.endsWith(build.name, self.expected_suffix) : "'%s' is defined in '%s' with os/arch '%s/%s' but does not end with '%s'" % [build.name, build.defined_in, build.os, build.arch, self.expected_suffix],
  },

  vm_env_mixin(shortverison): {
    local jdk = self.downloads.JAVA_HOME,
    environment+: {
      BASE_JDK_NAME: jdk.name,
      BASE_JDK_VERSION: jdk.version,
      BASE_JDK_SHORT_VERSION: shortverison,
    },
  },

  common_vm: graal_common.build_base + vm.vm_setup + {
    python_version: "3",
    logs+: [
      '*/mxbuild/dists/stripped/*.map',
      '**/install.packages.R.log',
    ],
  },

  common_vm_linux: self.common_vm + {
    packages+: {
      "00:devtoolset": "==7", # GCC 7.3.1, make 4.2.1, binutils 2.28, valgrind 3.13.0
    },
  },

  common_vm_darwin: self.common_vm + {
    environment+: {
      LANG: 'en_US.UTF-8'
    },
  },

  common_vm_windows: self.common_vm + {
    downloads+: {
      MAVEN_HOME: {name: 'maven', version: '3.3.9', platformspecific: false},
    },
    environment+: {
      PATH: '$MAVEN_HOME\\bin;$JAVA_HOME\\bin;$PATH',
    },
  },

  common_vm_windows_jdk11: self.common_vm + devkits['windows-jdk11'] + {
    downloads+: {
      MAVEN_HOME: {name: 'maven', version: '3.3.9', platformspecific: false},
    },
    environment+: {
      PATH: '$MAVEN_HOME\\bin;$JAVA_HOME\\bin;$PATH',
    },
  },

  common_vm_windows_jdk17: self.common_vm + devkits['windows-jdk17'] + {
    downloads+: {
      MAVEN_HOME: {name: 'maven', version: '3.3.9', platformspecific: false},
    },
    environment+: {
      PATH: '$MAVEN_HOME\\bin;$JAVA_HOME\\bin;$PATH',
    },
  },

  # JS
  js_windows_common: {
    downloads+: {
      NASM: {name: 'nasm', version: '2.14.02', platformspecific: true},
    },
  },

  js_windows_jdk11: self.js_windows_common + {
    setup+: [
      # Keep in sync with the 'devkits' object defined in the top level common.json file.
      # When this file has been converted to jsonnet, the value can be computed instead
      # using Jsonnet std lib functions.
      ['set-export', 'DEVKIT_VERSION', '2017'],
    ],
  },

  js_windows_jdk17: self.js_windows_common + {
    setup+: [
      # Keep in sync with the 'devkits' object defined in the top level common.json file.
      # When this file has been converted to jsonnet, the value can be computed instead
      # using Jsonnet std lib functions.
      ['set-export', 'DEVKIT_VERSION', '2019'],
    ],
  },

  js_windows: self.js_windows_common + {
    setup+: [
      # Keep in sync with the 'devkits' object defined in the top level common.json file.
      # When this file has been converted to jsonnet, the value can be computed instead
      # using Jsonnet std lib functions.
      ['set-export', 'DEVKIT_VERSION', '2017'],
    ],
  },

  # SULONG
  sulong_linux: common_json.sulong.deps.common + common_json.sulong.deps.linux,
  sulong_darwin_amd64: common_json.sulong.deps.common + common_json.sulong.deps.darwin_amd64,
  sulong_darwin_aarch64: common_json.sulong.deps.common + common_json.sulong.deps.darwin_aarch64,

  # TRUFFLERUBY, needs OpenSSL 1.0.2+, so OracleLinux 7+
  truffleruby_linux_amd64: self.sulong_linux + common_json.truffleruby.deps.common + common_json.truffleruby.deps.linux + graal_common.ol7,
  truffleruby_linux_aarch64: self.sulong_linux + common_json.truffleruby.deps.common + common_json.truffleruby.deps.linux, # OL7 is default on linux-aarch64
  truffleruby_darwin_amd64: self.sulong_darwin_amd64 + common_json.truffleruby.deps.common + common_json.truffleruby.deps.darwin,
  truffleruby_darwin_aarch64: self.sulong_darwin_aarch64 + common_json.truffleruby.deps.common + common_json.truffleruby.deps.darwin,

  # FASTR
  # Note: On both Linux and MacOS, FastR depends on the gnur module and on gfortran
  # of a specific version (4.8.5 on Linux, 10.2.0 on MacOS)
  # However, we do not need to load those modules, we only configure specific environment variables to
  # point to these specific modules. These modules and the configuration is only necessary for installation of
  # some R packages (that have Fortran code) and in order to run GNU-R

  fastr: {
    environment+: {
      FASTR_RELEASE: 'true',
    },
    downloads+: {
      F2C_BINARY: { name: 'f2c-binary', version: '7', platformspecific: true },
      FASTR_RECOMMENDED_BINARY: { name: 'fastr-recommended-pkgs', version: '12', platformspecific: true },
    },
    catch_files+: [
      'GNUR_CONFIG_LOG = (?P<filename>.+\\.log)',
      'GNUR_MAKE_LOG = (?P<filename>.+\\.log)',
    ],
  },

  fastr_linux: self.fastr + {
    packages+: {
      readline: '==6.3',
      pcre2: '==10.37',
      curl: '>=7.50.1',
      gnur: '==4.0.3-gcc4.8.5-pcre2',
    },
    environment+: {
      TZDIR: '/usr/share/zoneinfo',
      PKG_INCLUDE_FLAGS_OVERRIDE : '-I/cm/shared/apps/bzip2/1.0.6/include -I/cm/shared/apps/xz/5.2.2/include -I/cm/shared/apps/pcre2/10.37/include -I/cm/shared/apps/curl/7.50.1/include',
      PKG_LDFLAGS_OVERRIDE : '-L/cm/shared/apps/bzip2/1.0.6/lib -L/cm/shared/apps/xz/5.2.2/lib -L/cm/shared/apps/pcre2/10.37/lib -L/cm/shared/apps/curl/7.50.1/lib -L/cm/shared/apps/gcc/4.8.5/lib64',
      FASTR_FC: '/cm/shared/apps/gcc/4.8.5/bin/gfortran',
      FASTR_CC: '/cm/shared/apps/gcc/4.8.5/bin/gcc',
      GNUR_HOME_BINARY: '/cm/shared/apps/gnur/4.0.3_gcc4.8.5_pcre2-10.37/R-4.0.3',
    },
    downloads+: {
      BLAS_LAPACK_DIR: { name: 'fastr-403-blas-lapack-gcc', version: '4.8.5', platformspecific: true },
    },
  },

  fastr_darwin: self.fastr + {
    packages+: {
      'pcre2': '==10.37',
    },
    environment+: {
      PATH : '/usr/local/bin:$JAVA_HOME/bin:$PATH',
      FASTR_FC: '/cm/shared/apps/gcc/8.3.0/bin/gfortran',
      FASTR_CC: '/cm/shared/apps/gcc/8.3.0/bin/gcc',
      TZDIR: '/usr/share/zoneinfo',
      PKG_INCLUDE_FLAGS_OVERRIDE : '-I/cm/shared/apps/pcre2/pcre2-10.37/include -I/cm/shared/apps/bzip2/1.0.6/include -I/cm/shared/apps/xz/5.2.2/include -I/cm/shared/apps/curl/7.50.1/include',
      PKG_LDFLAGS_OVERRIDE : '-L/cm/shared/apps/bzip2/1.0.6/lib -L/cm/shared/apps/xz/5.2.2/lib -L/cm/shared/apps/pcre2/pcre2-10.37/lib -L/cm/shared/apps/curl/7.50.1/lib -L/cm/shared/apps/gcc/10.2.0/lib -L/usr/lib',
    },
    downloads+: {
      BLAS_LAPACK_DIR: { name: "fastr-403-blas-lapack-gcc", version: "8.3.0", platformspecific: true },
    },
  },

  fastr_no_recommended: {
    environment+: {
      FASTR_NO_RECOMMENDED: 'true'
    },
  },

  # GRAALPYTHON
  graalpython_linux: self.sulong_linux + {
    packages+: {
      libffi: '>=3.2.1',
      bzip2: '>=1.0.6',
    },
  },

  graalpython_darwin_amd64: self.sulong_darwin_amd64 + {},

  graalpython_darwin_aarch64: self.sulong_darwin_aarch64 + {},

  vm_linux_amd64: self.common_vm_linux + graal_common.linux_amd64 + graal_common.svm_deps.linux_amd64 {
    capabilities+: ['manycores', 'ram16gb', 'fast'],
  },

  vm_linux_aarch64: self.common_vm_linux + graal_common.linux_aarch64,

  vm_darwin_amd64: self.common_vm_darwin + graal_common.darwin_amd64 + {
    capabilities+: ['darwin_mojave', 'ram16gb'],
    packages+: {
      gcc: '==4.9.2',
    },
    environment+: {
      # for compatibility with macOS Sierra
      MACOSX_DEPLOYMENT_TARGET: '10.13',
    },
  },

  vm_darwin_aarch64: self.common_vm_darwin + graal_common.darwin_aarch64 + {
    environment+: {
      # for compatibility with macOS BigSur
      MACOSX_DEPLOYMENT_TARGET: '11.0',
    },
  },

  vm_windows: self.common_vm_windows + graal_common.windows_server_2016_amd64,

  vm_windows_jdk11: self.common_vm_windows_jdk11 + graal_common.windows_server_2016_amd64,

  vm_windows_jdk17: self.common_vm_windows_jdk17 + graal_common.windows_server_2016_amd64,

  gate_vm_linux_amd64: self.vm_linux_amd64 + {
    targets+: ['gate']
  },

  gate_vm_linux_aarch64: self.vm_linux_aarch64 + {
    targets+: ['gate'],
  },

  gate_vm_darwin_amd64: self.vm_darwin_amd64 + {
    targets+: ['gate'],
  },

  gate_vm_darwin_aarch64: self.vm_darwin_aarch64 + {
    targets+: ['gate'],
  },

  gate_vm_windows: self.vm_windows + {
    targets+: ['gate'],
  },

  bench_vm_linux_amd64: self.vm_linux_amd64 + {
    capabilities+: ['no_frequency_scaling'],
    targets+: ['post-merge', 'bench'],
  },

  bench_vm_darwin_amd64: self.vm_darwin_amd64 + {
    capabilities+: ['no_frequency_scaling'],
    targets+: ['post-merge', 'bench'],
  },

  bench_daily_vm_linux_amd64: self.vm_linux_amd64 + {
    capabilities+: ['no_frequency_scaling'],
    targets+: ['daily', 'bench'],
  },

  bench_daily_vm_darwin_amd64: self.vm_darwin_amd64 + {
    capabilities+: ['no_frequency_scaling'],
    targets+: ['daily', 'bench'],
  },

  bench_ondemand_vm_linux_amd64: self.vm_linux_amd64 + {
    capabilities+: ['no_frequency_scaling'],
    targets+: ['ondemand', 'bench'],
  },

  deploy_vm_linux_amd64: self.vm_linux_amd64 + {
    targets+: ['post-merge', 'deploy'],
  },

  deploy_vm_linux_aarch64: self.vm_linux_aarch64 + {
    targets+: ['post-merge', 'deploy'],
  },

  deploy_daily_vm_linux_amd64: self.vm_linux_amd64 + {
    targets+: ['daily', 'deploy'],
  },

  deploy_daily_vm_linux_aarch64: self.vm_linux_aarch64 + {
    targets+: ['daily', 'deploy'],
  },

  deploy_daily_vm_darwin_amd64: self.vm_darwin_amd64 + {
    targets+: ['daily', 'deploy'],
  },

  deploy_daily_vm_darwin_aarch64: self.vm_darwin_aarch64 + {
    targets+: ['daily', 'deploy'],
  },

  deploy_daily_vm_windows: self.vm_windows + {
    targets+: ['daily', 'deploy'],
  },

  deploy_daily_vm_windows_jdk11: self.vm_windows_jdk11 + {
    targets+: ['daily', 'deploy'],
  },

  deploy_daily_vm_windows_jdk17: self.vm_windows_jdk17 + {
    targets+: ['daily', 'deploy'],
  },

  postmerge_vm_linux_amd64: self.vm_linux_amd64 + {
    targets+: ['post-merge'],
  },

  daily_vm_linux_amd64: self.vm_linux_amd64 + {
    targets+: ['daily'],
  },

  daily_vm_linux_aarch64: self.vm_linux_aarch64 + {
    targets+: ['daily'],
  },

  daily_vm_darwin_amd64: self.vm_darwin_amd64 + {
    targets+: ['daily'],
  },

  daily_vm_windows: self.vm_windows + {
    targets+: ['daily'],
  },

  daily_vm_windows_jdk11: self.vm_windows_jdk11 + {
    targets+: ['daily'],
  },

  daily_vm_windows_jdk17: self.vm_windows_jdk17 + {
    targets+: ['daily'],
  },

  weekly_vm_linux_amd64: self.vm_linux_amd64 + {
    targets+: ['weekly'],
  },

  weekly_vm_linux_aarch64: self.vm_linux_aarch64 + {
    targets+: ['weekly'],
  },

  weekly_vm_darwin_amd64: self.vm_darwin_amd64 + {
    targets+: ['weekly'],
  },

  weekly_vm_darwin_aarch64: self.vm_darwin_aarch64+ {
    targets+: ['weekly'],
  },

  weekly_vm_windows: self.vm_windows + {
    targets+: ['weekly'],
  },

  weekly_vm_windows_jdk11: self.vm_windows_jdk11 + {
    targets+: ['weekly'],
  },

  weekly_vm_windows_jdk17: self.vm_windows_jdk17 + {
    targets+: ['weekly'],
  },

  ondemand_vm_linux_amd64: self.vm_linux_amd64 + {
    targets+: ['ondemand'],
  },

  ondemand_vm_darwin_amd64: self.vm_darwin_amd64 + {
    targets+: ['ondemand'],
  },

  ondemand_vm_darwin_aarch64: self.vm_darwin_aarch64+ {
    targets+: ['ondemand'],
  },

  ondemand_deploy_vm_linux_amd64: self.vm_linux_amd64 + {
    targets+: ['ondemand', 'deploy'],
  },

  ondemand_deploy_vm_linux_aarch64: self.vm_linux_aarch64 + {
    targets+: ['ondemand', 'deploy'],
  },

  ondemand_deploy_vm_darwin_amd64: self.vm_darwin_amd64 + {
    targets+: ['ondemand', 'deploy'],
  },

  ondemand_deploy_vm_darwin_aarch64: self.vm_darwin_aarch64 + {
    targets+: ['ondemand', 'deploy'],
  },

  ondemand_deploy_vm_windows_jdk11: self.vm_windows_jdk11 + {
    targets+: ['ondemand', 'deploy'],
  },

  ondemand_deploy_vm_windows_jdk17: self.vm_windows_jdk17 + {
    targets+: ['ondemand', 'deploy'],
  },

  mx_vm_cmd_suffix: ['--sources=sdk:GRAAL_SDK,truffle:TRUFFLE_API,compiler:GRAAL,substratevm:SVM', '--debuginfo-dists', '--base-jdk-info=${BASE_JDK_NAME}:${BASE_JDK_VERSION}'],
  mx_vm_common: vm.mx_cmd_base_no_env + ['--env', '${VM_ENV}'] + self.mx_vm_cmd_suffix,
  mx_vm_installables: vm.mx_cmd_base_no_env + ['--env', '${VM_ENV}-complete'] + self.mx_vm_cmd_suffix,

  svm_common_linux_amd64:        { environment+: graal_common.svm_deps.common.environment, logs+: graal_common.svm_deps.common.logs} + graal_common.svm_deps.linux_amd64,
  svm_common_linux_aarch64:      { environment+: graal_common.svm_deps.common.environment, logs+: graal_common.svm_deps.common.logs} + graal_common.svm_deps.linux_aarch64,
  svm_common_darwin_amd64:       { environment+: graal_common.svm_deps.common.environment, logs+: graal_common.svm_deps.common.logs} + graal_common.svm_deps.darwin_amd64,
  svm_common_darwin_aarch64:     { environment+: graal_common.svm_deps.common.environment, logs+: graal_common.svm_deps.common.logs} + graal_common.svm_deps.darwin_aarch64,
  svm_common_windows_jdk11:      { environment+: graal_common.svm_deps.common.environment, logs+: graal_common.svm_deps.common.logs} + graal_common.svm_deps.windows       + common_json.devkits['windows-jdk11'],
  svm_common_windows_jdk17:      { environment+: graal_common.svm_deps.common.environment, logs+: graal_common.svm_deps.common.logs} + graal_common.svm_deps.windows       + common_json.devkits['windows-jdk17'],

  maven_deploy_sdk: ['--suite', 'sdk', 'maven-deploy', '--validate', 'none', '--all-distribution-types', '--with-suite-revisions-metadata'],
  maven_deploy_sdk_base:               self.maven_deploy_sdk + ['--tags', 'graalvm',                             vm.binaries_repository],
  maven_deploy_sdk_base_dry_run:       self.maven_deploy_sdk + ['--tags', 'graalvm',                '--dry-run', vm.binaries_repository],
  maven_deploy_sdk_components:         self.maven_deploy_sdk + ['--tags', 'installable,standalone',              vm.binaries_repository],
  maven_deploy_sdk_components_dry_run: self.maven_deploy_sdk + ['--tags', 'installable,standalone', '--dry-run', vm.binaries_repository],

  svm_vm_build_ol6_amd64: self.svm_common_linux_amd64 + vm.custom_vm_linux,

  ruby_vm_build_linux: self.svm_common_linux_amd64 + self.sulong_linux + self.truffleruby_linux_amd64 + vm.custom_vm_linux,
  full_vm_build_linux: self.ruby_vm_build_linux + self.fastr_linux + self.graalpython_linux,
  full_vm_build_linux_aarch64: self.svm_common_linux_aarch64 + self.sulong_linux + self.truffleruby_linux_aarch64 + vm.custom_vm_linux,

  ruby_vm_build_darwin_amd64: self.svm_common_darwin_amd64 + self.sulong_darwin_amd64 + self.truffleruby_darwin_amd64 + vm.custom_vm_darwin,
  full_vm_build_darwin_amd64: self.ruby_vm_build_darwin_amd64 + self.fastr_darwin + self.graalpython_darwin_amd64,

  ruby_vm_build_darwin_aarch64: self.svm_common_darwin_aarch64 + self.sulong_darwin_aarch64 + self.truffleruby_darwin_aarch64 + vm.custom_vm_darwin,
  full_vm_build_darwin_aarch64: self.ruby_vm_build_darwin_aarch64 + self.graalpython_darwin_aarch64,


  libgraal_build(build_args):: {
    local build_command = if repo_config.graalvm_edition == 'ce' then 'build' else 'build-libgraal-pgo',
    run+: [
      ['mx', '--env', vm.libgraal_env] + ['--extra-image-builder-argument=%s' % arg for arg in build_args] + [build_command]
    ]
  },

  # enable asserts in the JVM building the image and enable asserts in the resulting native image
  libgraal_compiler_base(quickbuild_args=[]):: self.svm_common_linux_amd64 + vm.custom_vm_linux + self.libgraal_build(['-J-esa', '-J-ea', '-esa', '-ea'] + quickbuild_args) + {
    run+: [
      ['mx', '--env', vm.libgraal_env, 'gate', '--task', 'LibGraal Compiler'],
    ],
    timelimit: '1:00:00',
  },

  # enable asserts in the JVM building the image and enable asserts in the resulting native image
  libgraal_compiler:: self.libgraal_compiler_base(),
  # enable economy mode building with the -Ob flag
  libgraal_compiler_quickbuild:: self.libgraal_compiler_base(['-Ob']),


  libgraal_truffle_base(quickbuild_args=[]): self.svm_common_linux_amd64 + vm.custom_vm_linux + self.libgraal_build(['-J-ea', '-ea'] + quickbuild_args) + {
    environment+: {
      # The Truffle TCK tests run as a part of Truffle TCK gate
      TEST_LIBGRAAL_EXCLUDE: 'com.oracle.truffle.tck.tests.*'
    },
    run+: [
      ['mx', '--env', vm.libgraal_env, 'gate', '--task', 'LibGraal Truffle'],
    ],
    logs+: ['*/graal-compiler.log'],
    timelimit: '1:00:00',
  },

  # -ea assertions are enough to keep execution time reasonable
  libgraal_truffle: self.libgraal_truffle_base(),
  # enable economy mode building with the -Ob flag
  libgraal_truffle_quickbuild: self.libgraal_truffle_base(['-Ob']),

  # for cases where a maven package is not easily accessible
  maven_download_unix: {
    downloads+: {
      MAVEN_HOME: {name: 'maven', version: '3.3.9', platformspecific: false},
    },
    environment+: {
      PATH: '$MAVEN_HOME/bin:$JAVA_HOME/bin:$PATH',
    },
  },

  linux_deploy: {
    packages+: {
      maven: '>=3.3.9',
    },
  },

  darwin_deploy: self.maven_download_unix + {
    environment+: {
      PATH: '$MAVEN_HOME/bin:$JAVA_HOME/bin:$PATH:/usr/local/bin',
    },
  },

  record_file_sizes:: ['benchmark', 'file-size:*', '--results-file', 'sizes.json'],
  upload_file_sizes:: ['bench-uploader.py', 'sizes.json'],

  # These dummy artifacts are required by notify_releaser_build in order to trigger the releaser service
  # after the deploy jobs have completed.
  create_releaser_notifier_artifact:: [
    ['python3', '-c', "from os import environ; open('../' + environ['BUILD_NAME'], 'a').close()"],
  ],

  build_base_graalvm_image: [
    $.mx_vm_common + vm.vm_profiles + ['graalvm-show'],
    $.mx_vm_common + vm.vm_profiles + ['build'],
    ['set-export', 'GRAALVM_HOME', $.mx_vm_common + vm.vm_profiles + ['--quiet', '--no-warning', 'graalvm-home']],
  ],

  build_check_base_graalvm_image(os, arch, java_version): self.build_base_graalvm_image + vm.check_graalvm_base_build(os, arch, java_version),

  deploy_graalvm_linux_amd64(java_version): vm.check_structure + {
    run: [
      $.mx_vm_installables + ['graalvm-show'],
      $.mx_vm_installables + ['build'],
      ['set-export', 'GRAALVM_HOME', $.mx_vm_installables + ['--quiet', '--no-warning', 'graalvm-home']],
    ] + vm.check_graalvm_complete_build + [
      $.mx_vm_installables + $.maven_deploy_sdk_components,
      $.mx_vm_installables + $.record_file_sizes,
      $.upload_file_sizes,
    ] + vm.collect_profiles() + $.build_check_base_graalvm_image("linux", "amd64", java_version) + [
      $.mx_vm_common + vm.vm_profiles + $.record_file_sizes,
      $.upload_file_sizes,
      $.mx_vm_common + vm.vm_profiles + $.maven_deploy_sdk_base,
      self.ci_resources.infra.notify_nexus_deploy,
      ['set-export', 'GRAALVM_HOME', $.mx_vm_common + ['--quiet', '--no-warning', 'graalvm-home']],
    ] + $.create_releaser_notifier_artifact,
    notify_groups:: ['deploy'],
    timelimit: "1:30:00"
  },

  deploy_graalvm_linux_aarch64(java_version): vm.check_structure + {
    run: [
      ['set-export', 'VM_ENV', '${VM_ENV}-aarch64'],
      $.mx_vm_installables + ['graalvm-show'],
      $.mx_vm_installables + ['build'],
      ['set-export', 'GRAALVM_HOME', $.mx_vm_installables + ['--quiet', '--no-warning', 'graalvm-home']],
    ] + vm.check_graalvm_complete_build + [
      $.mx_vm_installables + $.maven_deploy_sdk_components,
      $.mx_vm_installables + $.record_file_sizes,
      $.upload_file_sizes,
    ] + vm.collect_profiles() + $.build_check_base_graalvm_image("linux", "aarch64", java_version) + [
      $.mx_vm_common + vm.vm_profiles + $.record_file_sizes,
      $.upload_file_sizes,
      $.mx_vm_common + vm.vm_profiles + $.maven_deploy_sdk_base,
      self.ci_resources.infra.notify_nexus_deploy,
    ] + $.create_releaser_notifier_artifact,
    notify_groups:: ['deploy'],
    timelimit: '1:30:00',
  },

  deploy_graalvm_base_darwin_amd64(java_version): vm.check_structure + {
    run: [
      ['set-export', 'VM_ENV', "${VM_ENV}-darwin"],
    ] + vm.collect_profiles() + $.build_check_base_graalvm_image("darwin", "amd64", java_version) + [
      $.mx_vm_common + vm.vm_profiles + $.record_file_sizes,
      $.upload_file_sizes,
      $.mx_vm_common + vm.vm_profiles + $.maven_deploy_sdk_base,
      self.ci_resources.infra.notify_nexus_deploy,
    ] + $.create_releaser_notifier_artifact,
    notify_groups:: ['deploy'],
    timelimit: '1:45:00',
  },

  deploy_graalvm_installables_darwin_amd64: {
    run: [
      ['set-export', 'VM_ENV', "${VM_ENV}-darwin"],
      $.mx_vm_installables + ['graalvm-show'],
      $.mx_vm_installables + ['build'],
      ['set-export', 'GRAALVM_HOME', $.mx_vm_installables + ['--quiet', '--no-warning', 'graalvm-home']],
    ] + vm.check_graalvm_complete_build + [
      $.mx_vm_installables + $.maven_deploy_sdk_components,
      self.ci_resources.infra.notify_nexus_deploy,
      $.mx_vm_installables + $.record_file_sizes,
      $.upload_file_sizes,
    ] + $.create_releaser_notifier_artifact,
    notify_groups:: ['deploy'],
    timelimit: '3:00:00',
  },

  deploy_graalvm_base_darwin_aarch64(java_version): vm.check_structure + {
    run: [
      # GR-34811: `ce-darwin-aarch64` can be removed once svml builds
      ['set-export', 'VM_ENV', '${VM_ENV}-darwin-aarch64'],
    ] + vm.collect_profiles() + $.build_check_base_graalvm_image("darwin", "aarch64", java_version) + [
      $.mx_vm_common + vm.vm_profiles + $.record_file_sizes,
      $.upload_file_sizes,
      $.mx_vm_common + vm.vm_profiles + $.maven_deploy_sdk_base,
      self.ci_resources.infra.notify_nexus_deploy,
    ] + $.create_releaser_notifier_artifact,
    notify_emails+: ['gilles.m.duboscq@oracle.com', 'bernhard.urban-forster@oracle.com'],
    timelimit: '1:45:00',
  },

  deploy_graalvm_installables_darwin_aarch64: {
    run: [
      ['set-export', 'VM_ENV', "${VM_ENV}-darwin-aarch64"],
      $.mx_vm_installables + ['graalvm-show'],
      $.mx_vm_installables + ['build'],
      ['set-export', 'GRAALVM_HOME', $.mx_vm_installables + ['--quiet', '--no-warning', 'graalvm-home']],
    ] + vm.check_graalvm_complete_build + [
      $.mx_vm_installables + $.maven_deploy_sdk_components,
      self.ci_resources.infra.notify_nexus_deploy,
      $.mx_vm_installables + $.record_file_sizes,
      $.upload_file_sizes,
    ] + $.create_releaser_notifier_artifact,
    notify_emails: ['gilles.m.duboscq@oracle.com', 'bernhard.urban-forster@oracle.com'],
    timelimit: '3:00:00',
  },

  deploy_graalvm_base_windows_amd64(java_version): vm.check_structure + {
    run: [
      ['set-export', 'VM_ENV', "${VM_ENV}-win"],
    ] + vm.collect_profiles() + $.build_check_base_graalvm_image("windows", "amd64", java_version) + [
      $.mx_vm_common + vm.vm_profiles + $.record_file_sizes,
      $.upload_file_sizes,
      $.mx_vm_common + vm.vm_profiles + $.maven_deploy_sdk_base,
      self.ci_resources.infra.notify_nexus_deploy,
    ] + $.create_releaser_notifier_artifact,
    notify_groups:: ['deploy'],
    timelimit: '1:30:00',
  },

  deploy_graalvm_installables_windows_amd64: {
    run: [
      ['set-export', 'VM_ENV', "${VM_ENV}-win"],
      $.mx_vm_installables + ['graalvm-show'],
      $.mx_vm_installables + ['build'],
      ['set-export', 'GRAALVM_HOME', $.mx_vm_installables + ['--quiet', '--no-warning', 'graalvm-home']],
    ] + vm.check_graalvm_complete_build + [
      $.mx_vm_installables + $.maven_deploy_sdk_components,
      self.ci_resources.infra.notify_nexus_deploy,
      $.mx_vm_installables + $.record_file_sizes,
      $.upload_file_sizes,
    ] + $.create_releaser_notifier_artifact,
    notify_groups:: ['deploy'],
    timelimit: '1:30:00',
  },

  deploy_graalvm_ruby(os, arch, java_version): vm.check_structure + {
    run: vm.collect_profiles() + [
      ['set-export', 'VM_ENV', "${VM_ENV}-ruby"],
    ] + $.build_base_graalvm_image + [
      $.mx_vm_common + vm.vm_profiles + $.maven_deploy_sdk_base,
      self.ci_resources.infra.notify_nexus_deploy,
      ['set-export', 'GRAALVM_HOME', $.mx_vm_common + ['--quiet', '--no-warning', 'graalvm-home']],
    ] + $.create_releaser_notifier_artifact,
    notify_groups:: ['deploy', 'ruby'],
    timelimit: '1:45:00',
  },

  #
  # Deploy GraalVM Base and Installables
  #

  # Linux/AMD64
  deploy_vm_java11_linux_amd64: vm.vm_java_11_llvm + self.full_vm_build_linux + self.linux_deploy + self.deploy_vm_linux_amd64 + self.deploy_graalvm_linux_amd64("java11") + {name: 'post-merge-deploy-vm-java11-linux-amd64', diskspace_required: vm.diskspace_required.java11_linux_mad64},
  deploy_vm_java17_linux_amd64: vm.vm_java_17_llvm + self.full_vm_build_linux + self.linux_deploy + self.deploy_vm_linux_amd64 + self.deploy_graalvm_linux_amd64("java17") + {name: 'post-merge-deploy-vm-java17-linux-amd64', diskspace_required: vm.diskspace_required.java17_linux_mad64},

  # Linux/AARCH64
  deploy_vm_java11_linux_aarch64: vm.vm_java_11 + self.full_vm_build_linux_aarch64 + self.linux_deploy + self.deploy_daily_vm_linux_aarch64 + self.deploy_graalvm_linux_aarch64("java11") + {name: 'daily-deploy-vm-java11-linux-aarch64'},
  deploy_vm_java17_linux_aarch64: vm.vm_java_17 + self.full_vm_build_linux_aarch64 + self.linux_deploy + self.deploy_daily_vm_linux_aarch64 + self.deploy_graalvm_linux_aarch64("java17") + {name: 'daily-deploy-vm-java17-linux-aarch64'},

  # Darwin/AMD64
  deploy_vm_base_java11_darwin_amd64: vm.vm_java_11_llvm + self.full_vm_build_darwin_amd64 + self.darwin_deploy + self.deploy_daily_vm_darwin_amd64 + self.deploy_graalvm_base_darwin_amd64("java11") + {name: 'daily-deploy-vm-base-java11-darwin-amd64'},
  deploy_vm_installable_java11_darwin_amd64: vm.vm_java_11_llvm + self.full_vm_build_darwin_amd64 + self.darwin_deploy + self.deploy_daily_vm_darwin_amd64 + self.deploy_graalvm_installables_darwin_amd64 + {name: 'daily-deploy-vm-installable-java11-darwin-amd64', diskspace_required: "31GB"},
  deploy_vm_base_java17_darwin_amd64: vm.vm_java_17_llvm + self.full_vm_build_darwin_amd64 + self.darwin_deploy + self.deploy_daily_vm_darwin_amd64 + self.deploy_graalvm_base_darwin_amd64("java17") + {name: 'daily-deploy-vm-base-java17-darwin-amd64'},
  deploy_vm_installable_java17_darwin_amd64: vm.vm_java_17_llvm + self.full_vm_build_darwin_amd64 + self.darwin_deploy + self.deploy_daily_vm_darwin_amd64 + self.deploy_graalvm_installables_darwin_amd64 + {name: 'daily-deploy-vm-installable-java17-darwin-amd64', diskspace_required: "31GB"},

  # Darwin/AARCH64
  deploy_vm_base_java11_darwin_aarch64: vm.vm_java_11 + self.full_vm_build_darwin_aarch64 + self.darwin_deploy + self.deploy_daily_vm_darwin_aarch64 + self.deploy_graalvm_base_darwin_aarch64("java11") + {name: 'daily-deploy-vm-base-java11-darwin-aarch64'},
  deploy_vm_installable_java11_darwin_aarch64: vm.vm_java_11 + self.full_vm_build_darwin_aarch64 + self.darwin_deploy + self.deploy_daily_vm_darwin_aarch64 + self.deploy_graalvm_installables_darwin_aarch64 + {name: 'daily-deploy-vm-installable-java11-darwin-aarch64', diskspace_required: "31GB"},
  deploy_vm_base_java17_darwin_aarch64: vm.vm_java_17 + self.full_vm_build_darwin_aarch64 + self.darwin_deploy + self.deploy_daily_vm_darwin_aarch64 + self.deploy_graalvm_base_darwin_aarch64("java17") + {name: 'daily-deploy-vm-base-java17-darwin-aarch64'},
  deploy_vm_installable_java17_darwin_aarch64: vm.vm_java_17 + self.full_vm_build_darwin_aarch64 + self.darwin_deploy + self.deploy_daily_vm_darwin_aarch64 + self.deploy_graalvm_installables_darwin_aarch64 + {name: 'daily-deploy-vm-installable-java17-darwin-aarch64', diskspace_required: "31GB"},

  # Windows/AMD64
  deploy_vm_base_java11_windows_amd64: vm.vm_java_11 + self.svm_common_windows_jdk11 + self.js_windows_jdk11 + self.deploy_daily_vm_windows_jdk11 + self.deploy_graalvm_base_windows_amd64("java11") + {name: 'daily-deploy-vm-base-java11-windows-amd64'},
  deploy_vm_installable_java11_windows_amd64: vm.vm_java_11 + self.svm_common_windows_jdk11 + self.js_windows_jdk11 + self.deploy_daily_vm_windows_jdk11 + self.deploy_graalvm_installables_windows_amd64 + {name: 'daily-deploy-vm-installable-java11-windows-amd64', diskspace_required: "31GB"},
  deploy_vm_base_java17_windows_amd64: vm.vm_java_17 + self.svm_common_windows_jdk17 + self.js_windows_jdk17 + self.deploy_daily_vm_windows_jdk17 + self.deploy_graalvm_base_windows_amd64("java17") + {name: 'daily-deploy-vm-base-java17-windows-amd64'},
  deploy_vm_installable_java17_windows_amd64: vm.vm_java_17 + self.svm_common_windows_jdk17 + self.js_windows_jdk17 + self.deploy_daily_vm_windows_jdk17 + self.deploy_graalvm_installables_windows_amd64 + {name: 'daily-deploy-vm-installable-java17-windows-amd64', diskspace_required: "31GB"},

  #
  # Deploy the GraalVM Ruby image (GraalVM Base + ruby - js)
  #

  deploy_vm_ruby_java11_linux_amd64: vm.vm_java_11 + self.ruby_vm_build_linux + self.linux_deploy + self.deploy_daily_vm_linux_amd64 + self.deploy_graalvm_ruby('linux', 'amd64', 'java11') + {name: 'daily-deploy-vm-ruby-java11-linux-amd64'},
  deploy_vm_ruby_java11_darwin_amd64: vm.vm_java_11 + self.ruby_vm_build_darwin_amd64 + self.darwin_deploy + self.deploy_daily_vm_darwin_amd64 + self.deploy_graalvm_ruby('darwin', 'amd64', 'java11') + {name: 'daily-deploy-vm-ruby-java11-darwin-amd64'},
  deploy_vm_ruby_java11_darwin_aarch64: vm.vm_java_11 + self.ruby_vm_build_darwin_aarch64 + self.darwin_deploy + self.deploy_daily_vm_darwin_aarch64 + self.deploy_graalvm_ruby('darwin', 'aarch64', 'java11') + {name: 'daily-deploy-vm-ruby-java11-darwin-aarch64'},

  local builds = [
    #
    # Gates
    #
    vm.vm_java_17 + common_json.downloads.eclipse + common_json.downloads.jdt + self.gate_vm_linux_amd64 + {
     run: [
       ['mx', 'gate', '-B=--force-deprecation-as-warning', '--tags', 'style,fullbuild'],
     ],
     name: 'gate-vm-style-jdk17-linux-amd64',
    },
    self.gate_vm_linux_amd64 + self.libgraal_compiler + vm.vm_java_11 + { name: 'gate-vm-libgraal-compiler-11-linux-amd64' },
    self.gate_vm_linux_amd64 + self.libgraal_compiler + vm.vm_java_17 + { name: 'gate-vm-libgraal-compiler-17-linux-amd64' },

    self.gate_vm_linux_amd64 + self.libgraal_truffle + vm.vm_java_11 + { name: 'gate-vm-libgraal-truffle-11-linux-amd64' },
    self.gate_vm_linux_amd64 + self.libgraal_truffle + vm.vm_java_17 + { name: 'gate-vm-libgraal-truffle-17-linux-amd64' },

    self.gate_vm_linux_amd64 + self.libgraal_compiler_quickbuild + vm.vm_java_17 + { name: 'gate-vm-libgraal-compiler-quickbuild-17-linux-amd64' },
    self.gate_vm_linux_amd64 + self.libgraal_truffle_quickbuild + vm.vm_java_17 + { name: 'gate-vm-libgraal-truffle-quickbuild-17-linux-amd64' },

    self.gate_vm_linux_amd64 + self.libgraal_compiler_quickbuild + vm.vm_java_19 + { name: 'gate-vm-libgraal-compiler-quickbuild-19-linux-amd64' },

    vm.vm_java_17 + self.svm_common_linux_amd64 + self.sulong_linux + vm.custom_vm_linux + self.gate_vm_linux_amd64 + {
     run: [
       ['export', 'SVM_SUITE=' + vm.svm_suite],
       ['mx', '--dynamicimports', '$SVM_SUITE,/sulong', '--disable-polyglot', '--disable-libpolyglot', 'gate', '--no-warning-as-error', '--tags', 'build,sulong'],
     ],
     timelimit: '1:00:00',
     name: 'gate-vm-native-sulong-linux-amd64',
    },
  ],

  builds:: [{'defined_in': std.thisFile} + b for b in builds],
}
