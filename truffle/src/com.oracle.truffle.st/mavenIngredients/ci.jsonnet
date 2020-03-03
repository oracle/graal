{
  local basicBuild = {
    targets: ['gate'],
    timelimit: '00:59:59',
    run: [
      ['mvn', 'clean'],
      ['mvn', 'package'],
      ['./simpletool', 'js', 'example.js'],
    ],
  },

  local graalvm = {
    downloads+: {
      JAVA_HOME: { name: 'graalvm', version: '19.2.0', platformspecific: true },
    },
  },

  local linux = {
    capabilities+: ['linux', 'amd64'],
    packages+: {
      maven: '==3.3.9',
    },
  },

  local darwin = {
    capabilities+: ['darwin_sierra', 'amd64'],
    environment+: {
      MACOSX_DEPLOYMENT_TARGET: '10.11',
    },
  },

  builds: [
    basicBuild + linux + graalvm + { name: 'linux' },

    basicBuild + darwin + graalvm + { name: 'darwin' },
  ],
}
