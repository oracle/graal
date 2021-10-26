{
  local common = import '../common.jsonnet',
  local devkits = (import "../common.json").devkits,

  local tools_common = {
    setup+: [
      ["cd", "./tools"],
    ],
    timelimit: "30:00",
  },

  local tools_gate = tools_common + common["linux-amd64"] + common.eclipse + common.jdt + {
    name: 'gate-tools-oraclejdk' + self.jdk_version + '-' + self.os + '-' + self.arch,
    run: [["mx", "--strict-compliance", "gate", "--strict-mode"]],
    targets: ["gate"],
  },

  local tools_gate_lite = tools_common + {
    name: 'gate-tools-lite-oraclejdk' + self.jdk_version + '-' + self.os + '-' + self.arch,
    run: [
      ["mx", "build"],
      ["mx", "unittest", "--verbose"],
      ["mx", "sigtest"],
    ],
    notify_groups:: ["tools"],
    targets: ["weekly"],
  },

  local coverage_whitelisting = [
    "--jacoco-whitelist-package",
    "org.graalvm.tools",
    "--jacoco-whitelist-package",
    "com.oracle.truffle.tools"
  ],

  builds: [
    common.oraclejdk11 + tools_gate,
    common.oraclejdk17 + tools_gate,

    common["linux-amd64"] + common.oraclejdk11 + tools_common + {
      name: "gate-tools-javadoc",
      run: [
        ["mx", "build"],
        ["mx", "javadoc"],
      ],
      targets: ["gate"],
    },

    common["windows-amd64"] + common.oraclejdk11 + devkits["windows-oraclejdk11"] + tools_gate_lite + {
      packages+: {
        "mx": "HEAD",
        "pip:isort": "==4.3.19",
        "pip:logilab-common": "==1.4.4",
        "pip:ninja_syntax": "==1.7.2",
        "pip:pylint": "==1.9.3",
      },
    },

    common["darwin-amd64"] + common.oraclejdk11 + tools_gate_lite,
    common["darwin-amd64"] + common.oraclejdk17 + tools_gate_lite,
    
    common["linux-aarch64"] + common.labsjdk11 + tools_gate_lite,

    common["linux-amd64"] + common.oraclejdk11 + tools_common + common.eclipse + common.jdt + {
      name: "weekly-tools-coverage",
      run: [
        ["mx"] + coverage_whitelisting + [
          "--strict-compliance",
          "gate", 
          "--strict-mode",
          "--jacoco-omit-excluded",
          "--jacocout", 
          "html",
        ],
        ["mx"] + coverage_whitelisting + ["coverage-upload"],
      ],
      targets: ["weekly"],
    }
  ],
}