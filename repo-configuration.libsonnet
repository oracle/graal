// Holds repo specific definitions
{
  graalvm_edition:: "ce",

  compiler:: {
    default_jvm_config:: "graal-core",
    libgraal_env_file:: "libgraal",
    vm_suite:: "vm",
    compiler_suite:: "compiler"
  },

  native_image:: {
    vm_suite:: "vm",
  }
}
