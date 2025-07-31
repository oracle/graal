# Common
local common = import 'ci/ci_common/common.jsonnet';
local graal_common = import 'graal-common.json';

# Compiler
local compiler = import 'compiler/ci/ci.jsonnet';

# GraalWasm
local wasm = import 'wasm/ci/ci.jsonnet';

# Espresso
local espresso = import 'espresso/ci/ci.jsonnet';

# Regex
local regex = import 'regex/ci/ci.jsonnet';

# SDK
local sdk = import 'sdk/ci/ci.jsonnet';

# SubstrateVM
local substratevm = import 'substratevm/ci/ci.jsonnet';

# Sulong
local sulong = import 'sulong/ci/ci.jsonnet';

# Tools
local tools = import 'tools/ci/ci.jsonnet';

# Truffle
local truffle = import 'truffle/ci/ci.jsonnet';

# JavaDoc
local javadoc = import "ci_includes/publish-javadoc.jsonnet";

# VM
local vm = import 'vm/ci/ci_includes/vm.jsonnet';

# Visualizer
local visualizer = import 'visualizer/ci/ci.jsonnet';

# Web Image
local web_image = import 'web-image/ci/ci.jsonnet';

local verify_ci = (import 'ci/ci_common/ci-check.libsonnet').verify_ci;

# Filter builds to include/exclude jobs whose name contains "libgraal"
local libgraal(builds, include=true) = [b for b in builds if (std.findSubstr("libgraal", b.name) != []) == include];
{
  # Ensure that non-hidden entries in ci/common.jsonnet and ci/ci_common/common.jsonnet can be resolved.
  assert std.length(std.toString(import 'ci/ci_common/common.jsonnet')) > 0,
  ci_resources:: (import 'ci/ci_common/ci-resources.libsonnet'),
  overlay: graal_common.ci.overlay,
  specVersion: "7",
  tierConfig: {
    tier1: "gate",
    tier2: "gate",
    tier3: "gate",
    tier4: "post-merge",
  },
  builds: [common.add_excludes_guard(common.with_style_component(b)) for b in (
    common.with_components(compiler.builds + libgraal(vm.builds), ["compiler"]) +
    common.with_components(wasm.builds, ["wasm"]) +
    common.with_components(espresso.builds, ["espresso"]) +
    common.with_components(regex.builds, ["regex"]) +
    common.with_components(sdk.builds, ["sdk"]) +
    common.with_components(substratevm.builds, ["svm"]) +
    common.with_components(sulong.builds, ["sulong"]) +
    common.with_components(tools.builds, ["tools"]) +
    common.with_components(truffle.builds, ["truffle"]) +
    common.with_components(javadoc.builds, ["javadoc"]) +
    common.with_components(libgraal(vm.builds, false), ["vm"]) +
    common.with_components(visualizer.builds, ["visualizer"]) +
    common.with_components(web_image.builds, ["webimage"])
  )],
  assert verify_ci(self.builds),
  // verify that the run-spec demo works
  assert (import "ci/ci_common/run-spec-demo.jsonnet").check(),
}
