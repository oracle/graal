{
  local composable = (import "common-utils.libsonnet").composable,

  local mx_version = (import "graal-common.json").mx_version,
  local common_json = composable(import "common.json"),
  local jdks = common_json.jdks,
  local deps = common_json.deps,
  local downloads = common_json.downloads,
  # This must always point to HEAD in the master branch but can be used to point
  # to another branch/commit in a Graal PR when mx changes are required for the PR.
  mx:: {
    packages +: {
      mx: mx_version
    }
  },

  oraclejdk8::           { downloads+: { JAVA_HOME : jdks.oraclejdk8,      EXTRA_JAVA_HOMES : { pathlist :[ jdks["labsjdk-ee-11"] ]} }},
  oraclejdk8Only::       { downloads+: { JAVA_HOME : jdks.oraclejdk8 }},
  oraclejdk8Debug::      { downloads+: { JAVA_HOME : jdks.oraclejdk8Debug, EXTRA_JAVA_HOMES : { pathlist :[ jdks["labsjdk-ee-11"] ]} }},
  oraclejdk8OnlyDebug::  { downloads+: { JAVA_HOME : jdks.oraclejdk8Debug }},

  openjdk8::             { downloads+: { JAVA_HOME : jdks.openjdk8 }},

  oraclejdk11::          { downloads+: { JAVA_HOME : jdks.oraclejdk11 }},
  oraclejdk15::          { downloads+: { JAVA_HOME : jdks.oraclejdk15 }},
  openjdk11::            { downloads+: { JAVA_HOME : jdks.openjdk11 }},

  "labsjdk-ce-11"::      { downloads+: { JAVA_HOME : jdks["labsjdk-ce-11"] }},
  "labsjdk-ee-11"::      { downloads+: { JAVA_HOME : jdks["labsjdk-ee-11"] }},
  "labsjdk-ce-15"::      { downloads+: { JAVA_HOME : jdks["labsjdk-ce-15"] }},
  "labsjdk-ee-15"::      { downloads+: { JAVA_HOME : jdks["labsjdk-ee-15"] }},
  "labsjdk-ce-15Debug":: { downloads+: { JAVA_HOME : jdks["labsjdk-ce-15Debug"] }},
  "labsjdk-ee-15Debug":: { downloads+: { JAVA_HOME : jdks["labsjdk-ee-15Debug"] }},

  common:: deps.common + self.mx + {
    # enforce self.os (useful for generating job names)
    os:: error "self.os not set",
    # enforce self.arch (useful for generating job names)
    arch:: error "self.arch not set",
    capabilities +: [],
    catch_files +: [
      "Graal diagnostic output saved in (?P<filename>.+\\.zip)"
    ]
  },

  linux:: deps.linux + self.common + {
    os::"linux",
    capabilities+: [self.os],
  },

  darwin:: deps.darwin + self.common + {
    os::"darwin",
    capabilities+: [self.os],
  },

  windows:: deps.windows + self.common + {
    os::"windows",
    capabilities+: [self.os],
  },

  amd64:: {
    arch::"amd64",
    capabilities+: [self.arch],
  },

  aarch64:: {
    arch::"aarch64",
    capabilities+: [self.arch],
  },

  "linux-amd64"::     self.linux + self.amd64,
  "darwin-amd64"::    self.darwin + self.amd64,
  "windows-amd64"::   self.windows + self.amd64,
  "linux-aarch64"::   self.linux + self.aarch64,

  eclipse:: downloads.eclipse,
  jdt:: downloads.jdt,
}
