# CI Run Specification

The CI run specification is a Jsonnet library used to declaratively define on which _platform_ (OS/architecture/JDK) and at which _frequency_ (gate/daily/weekly/etc.) a job should run.
The main idea is to have a single place that describes _what a task does_ (for example, running `mx unittest`), while keeping it logically decoupled from
_where_ and _when_ it runs.

The entry point of the library is [run-spec.jsonnet](../../ci/ci_common/run-spec.jsonnet).
Examples and further details can be found in [run-spec-demo.jsonnet](../../ci/ci_common/run-spec-demo.jsonnet) and [run-spec-examples.jsonnet](../../ci/ci_common/run-spec-examples.jsonnet).

## Terminology

* A `platform` is defined by an operating system, an architecture, and a JDK version.
* A `task` defines something to execute, independent of the platform.
* `variants` of a task generate multiple tasks based on small user modifications.
* `batches` of a task split a large task into N smaller tasks.
* A `frequency` defines how often a task must be executed: `gate`, `postmerge`, `daily`, `weekly`, `monthly`, `ondemand`.
* A `job` is a runnable CI unit created by combining a task, a platform, and a frequency.
* A `build` is the result of executing one or more jobs within a CI system. Each build is identified by a unique number.