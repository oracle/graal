// Holds repo specific definitions
{
  graalvm_edition:: "ce",

  compiler:: {
    default_jvm_config:: "graal-core",
    libgraal_env_file:: "libgraal",
    vm_suite:: "vm",
    compiler_suite:: "compiler"
  },

  vm:: {
    suite_dir:: "vm",
    mx_env:: {
      libgraal:: "libgraal"
    }
  },

  native_image:: {
    vm_suite:: "vm",
  }
}
