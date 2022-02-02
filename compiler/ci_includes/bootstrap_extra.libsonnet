# Bootstrap tasks specific to the Graal CE.
{
  local c = import '../../common.jsonnet',
  local g = import '../ci_common/gate.jsonnet',

  builds: [
    {name: "gate-compiler-bootstrap-labsjdk-ee-11-linux-amd64"} +         g.bootstrap +           c.labsjdk11 + c.linux_amd64 + g.many_cores,
    {name: "gate-compiler-bootstrap-labsjdk-ee-17-linux-amd64"} +         g.bootstrap +           c.labsjdk17 + c.linux_amd64 + g.many_cores,
    {name: "gate-compiler-bootstrap-economy-labsjdk-ee-11-linux-amd64"} + g.bootstrap_economy +    c.labsjdk11 + c.linux_amd64 + g.many_cores,
    {name: "gate-compiler-bootstrap-economy-labsjdk-ee-17-linux-amd64"} + g.bootstrap_economy +    c.labsjdk17 + c.linux_amd64 + g.many_cores,
  ]
}
