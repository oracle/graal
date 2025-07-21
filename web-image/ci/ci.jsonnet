local ci_common = import '../../ci/ci_common/common.jsonnet';
local common = import 'ci_common/common.jsonnet';
local utils = (import '../../ci/ci_common/common-utils.libsonnet');
local r = import 'ci_common/wi-run-spec.jsonnet';

local mxgate(tags) = r.mxgate(tags, suite='web-image');

local platforms = r.platforms;
local t = r.t;

local tier1 = r.tier1;
local tier2 = r.tier2;
local weekly = r.weekly;

{
  // THE TASK CONFIGURATION
  task_dict:: {
    // TODO GR-67831 Split into separate style and fullbuild jobs
    'style-fullbuild': mxgate('style,fullbuild,webimagehelp,webimageoptions') + t('30:00') + r.eclipse + r.jdt + r.spotbugs + r.prettier + platforms({
      'linux:amd64:jdk-latest': tier1,
    }),
    'unittest': mxgate('webimagebuild,webimageunittest') + t('30:00') + r.node22 + platforms({
      'linux:amd64:jdk-latest': tier2,
    }),
  },
  processed_tasks:: r.process(self.task_dict),
  builds: [
    utils.add_gate_predicate(b, common.guard_suites, common.extra_includes)
    for b in utils.add_defined_in(self.processed_tasks.list, std.thisFile)
  ],
}
