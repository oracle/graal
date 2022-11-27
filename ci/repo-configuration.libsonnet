// Holds repo specific definitions
{
  graalvm_edition:: "ce",
  repo_name:: "graal",

  compiler:: {
    default_jvm_config:: "graal-core",
    libgraal_jvm_config(pgo):: "graal-core-libgraal",
    libgraal_env_file:: "libgraal",
    vm_suite:: "vm",
    compiler_suite:: "compiler",

    # Returns a command line to collect a profile to build libgraal with PGO
    #
    # @param mx_prefix the mx command line prior to the specific command being run
    collect_libgraal_profile(mx_prefix=["mx"]):: [],

    # Returns mx arguments to have native image generation use
    # the profile created by `collect_libgraal_profile`.
    use_libgraal_profile:: [],
  },

  vm:: {
    suite_dir:: "vm",
    mx_env:: {
      libgraal:: "libgraal"
    },

    libgraal_predicate_conf:: {
      suites:: [
        "sdk",
        "truffle",
        "compiler",
        "substratevm",
        "vm"
      ],

      # Updating language imports should not run libgraal gates
      extra_excludes:: [
        "vm/mx.vm/suite.py"
      ]
    }
  },

  native_image:: {
    vm_suite:: "vm",
    extra_deps:: {},
  }
}
