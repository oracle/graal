{
  local labsjdk8 = {name: 'oraclejdk', version: '8u231-jvmci-19.3-b04', platformspecific: true},

  jdk8: {
    downloads+: {
      JAVA_HOME: labsjdk8,
    },
  },
}
