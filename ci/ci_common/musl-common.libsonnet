// Definitions that might be useful in other suites as well.
{
  musl_dependency:: {
    downloads+: {
      MUSL_TOOLCHAIN: {
        name: 'toolchain-gcc-10.3.0-zlib-1.2.13-musl',
        version: '1.2.5.1',
        platformspecific: true,
      },
    },
    setup+: [
      // Note that we must add the toolchain to the end of the PATH so that the system gcc still remains the first choice
      // for building the rest of GraalVM. The musl toolchain also provides a gcc executable that would shadow the system one
      // if it were added at the start of the PATH.
      ['export', 'PATH=$PATH:$MUSL_TOOLCHAIN/bin'],
    ],
  },
}
