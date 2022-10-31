# CI Run Specification

The CI run specification is a jsonnet library to declaratively express on which _platform_ (os/arch/jdk)
and at which _frequency_ (gate/daily/weekly/etc) to run a certain job.
The main idea is to have a single point that describes _what a task does_ (e.g. run `mx unittest`) that is decoupled (logically but not spatially) from
_where_ and _when_ to execute it.

The entry point of the library is [`run-spec.jsonnet`](../../ci/ci_common/run-spec.jsonnet).
Examples and further details can be found in [`run-spec-demo.jsonnet`](../../ci/ci_common/run-spec-demo.jsonnet)
and [`run-spec-examples.jsonnet`](../../ci/ci_common/run-spec-examples.jsonnet).

## Terminology

* A `platform` is defined by an os, an architecture and a jdk.
* A `task` is the definition of something we want to execute without consideration of the platform.
* `variants` of task generate a set of tasks based on user slight alterations of a task.
* `batches` of a task splits a large task into N smaller tasks.
* A `frequency` defines how often a task must be executed: `gate`, `postmerge`, `daily`, `weekly`, `monthly`, `ondemand`.
* A `job` is the complete definition of a runnable CI job. It is defined by a task, a platform and a frequency.
* A `build` is the result of job execution within a specific CI system. It is identified with a unique number.
