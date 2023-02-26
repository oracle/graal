local r = import "run-spec.libsonnet";
local tools = import "tools.libsonnet";
// define our task object
local unittest = r.task_spec(
  {
    target: null,
    task_name:: null,
    os:: null,
    arch:: null,
    jdk:: null,
    flags:: [],
    variations::[],
    run: std.join(" ", ["mx", "unittest"] + self.flags),
    name: std.join("-", [self.target, self.task_name] + self.variations + [self.os, self.arch, self.jdk]),
  }
);
// define a modifier for our build object
local with_flags(flags) = r.task_spec({
    task_variant:: null,
    flags+:: flags,
    variations+::[self.task_variant],
  }
);
// targets
local gate = r.task_spec({target:"gate"});
local daily = r.task_spec({target:"daily"});
// define a multiplier
local batches(num) = r.add_multiply([
  r.task_spec({
    flags+::["--batch", "%d/%d" % [i, num]],
    variations+::["batch" + i]
  }),
  for i in std.range(1, num)
]);
// define a default "matrix"
local empty = {
  "<all-os>": r.exclude,
  "variants": {
    "ea": {
      "<all-os>": r.exclude,
    }
  }
};
{
  // THE TASK CONFIGURATION
  task_dict:: {
    "unittest": unittest + r.platform_spec(empty) + r.platform_spec({
      "linux:amd64:jdk17": gate + batches(2),
      variants: {
        ea: {
          "linux:amd64:jdk17": daily + with_flags(["-ea"]),
        }
      }
    })
  },
  processed_tasks:: r.process(self.task_dict),
  builds: self.processed_tasks.list,

  //

  local check_builds() =
    local expected = [
       {
          "name": "gate-unittest-batch1-linux-amd64-jdk17",
          "run": "mx unittest --batch 1/2",
          "target": "gate",
       },
       {
          "name": "gate-unittest-batch2-linux-amd64-jdk17",
          "run": "mx unittest --batch 2/2",
          "target": "gate",
       },
       {
          "name": "daily-unittest-ea-linux-amd64-jdk17",
          "run": "mx unittest -ea",
          "target": "daily",
       }
    ];
    std.assertEqual(expected, $.builds)
  ,
  check():: check_builds(),
  assert $.check(),
}
