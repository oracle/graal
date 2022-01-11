# Bootstrap tasks specific to the Graal CE.
{
  local c = import '../../common.jsonnet',
  local g = import '../ci_common/gate.jsonnet',

  builds: [
    {name: "gate-compiler-bootstrap-labsjdk-ee-11-linux-amd64"} +         g.Bootstrap +           c.labsjdk11 + c.LinuxAMD64 + g.ManyCores,
    {name: "gate-compiler-bootstrap-labsjdk-ee-17-linux-amd64"} +         g.Bootstrap +           c.labsjdk17 + c.LinuxAMD64 + g.ManyCores,
    {name: "gate-compiler-bootstrap-economy-labsjdk-ee-11-linux-amd64"} + g.BootstrapEconomy +    c.labsjdk11 + c.LinuxAMD64 + g.ManyCores,
    {name: "gate-compiler-bootstrap-economy-labsjdk-ee-17-linux-amd64"} + g.BootstrapEconomy +    c.labsjdk17 + c.LinuxAMD64 + g.ManyCores,
  ]
}
