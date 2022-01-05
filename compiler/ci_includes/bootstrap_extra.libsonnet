# Bootstrap tasks specific to the Graal CE.
{
  local c = import '../../common.jsonnet',
  local g = import '../ci_common/gate.jsonnet',

  builds: [
    g.Bootstrap +           g.LabsJDK11 + c.LinuxAMD64 + g.ManyCores + {name: "gate-compiler-bootstrap-labsjdk-ee-11-linux-amd64"},
    g.Bootstrap +           g.LabsJDK17 + c.LinuxAMD64 + g.ManyCores + {name: "gate-compiler-bootstrap-labsjdk-ee-17-linux-amd64"},
    g.BootstrapEconomy +    g.LabsJDK11 + c.LinuxAMD64 + g.ManyCores + {name: "gate-compiler-bootstrap-economy-labsjdk-ee-11-linux-amd64"},
    g.BootstrapEconomy +    g.LabsJDK17 + c.LinuxAMD64 + g.ManyCores + {name: "gate-compiler-bootstrap-economy-labsjdk-ee-17-linux-amd64"},
  ]
}
