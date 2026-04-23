# AGENTS.md — Standalone points-to tests (`com.oracle.graal.pointsto.standalone.test`)

Scope:

- standalone points-to test harness and test cases in this project

This file extends:

- `../com.oracle.graal.pointsto/AGENTS.md`
- `../com.oracle.graal.pointsto.standalone/AGENTS.md`

If instructions conflict for standalone tests, this file takes precedence.

## Primary test gate

- `mx -p /abs/path/graal/substratevm standalone-pointsto-unittest host`

Espresso variant:

- `mx -p /abs/path/graal/substratevm --dy /espresso-compiler-stub standalone-pointsto-unittest espresso`

Important:

- Rebuild before running either gate.
- Keep the exact same `mx` context between build and test.
- Host flow:
  - `mx -p /abs/path/graal/substratevm build -f`
  - `mx -p /abs/path/graal/substratevm standalone-pointsto-unittest host`
- Espresso flow:
  - `mx -p /abs/path/graal/substratevm --dy /espresso-compiler-stub build -f`
  - `mx -p /abs/path/graal/substratevm --dy /espresso-compiler-stub standalone-pointsto-unittest espresso`

Suite wiring reference:

- `substratevm/mx.substratevm/suite.py`
    - project: `com.oracle.graal.pointsto.standalone.test`
    - project: `com.oracle.graal.pointsto.standalone.test.classes`
    - distribution: `STANDALONE_POINTSTO_TESTS`

## Key files

- `StandaloneAnalysisTest.java`
- `StandaloneAnalysisAssertionsTest.java`
- `StandaloneAnalyzerReachabilityTest.java`
- `StandaloneAnalysisReportTest.java`
- `AnalysisPerformanceTest.java`

## Test structure

- Test code lives in `com.oracle.graal.pointsto.standalone.test`.
- Analyzed fixture code lives in the separate project `com.oracle.graal.pointsto.standalone.test.classes`.
- Keep analyzed inputs out of the test-harness project. Put new fixtures under the `.classes` project unless there is a strong reason not to.

## Main test API

`StandaloneAnalysisTest` is the entry point for new standalone tests.

Use it for:

- running analysis from an entry class
- running analysis from a specific entry method
- option customization through `extraOptions()` and `addOption(...)`
- lookups with `findClass(...)`, `findMethod(...)`, and `findField(...)`
- temp-dir and resource helpers for file-driven tests

Preferred execution pattern:

1. run analysis once
2. retain results on the test instance
3. assert analysis semantics directly

Prefer one analysis run per logical scenario. If multiple assertions exercise the same input, keep them in one test and split readability with private helper methods instead of rerunning analysis.

## Assertions

Use the strongest assertion that matches the intended regression signal.

Basic reachability:

- `assertReachable(...)`
- `assertNotReachable(...)`

Semantic assertions:

- `assertFieldTypes(...)`
- `assertFieldCanBeNull(...)`
- `assertParameterTypes(...)`
- `assertParameterNotAnalyzed(...)`
- `assertResultTypes(...)`
- `assertInvokeCallees(...)`

Reachability expectations should be strict:

- expected reachable elements should exist in the universe and be reachable
- expected unreachable elements may be absent, and that is acceptable

## Authoring guidance

- Prefer semantic analysis assertions over coarse reachability checks when the test is really about precision.
- Keep standalone-specific execution details in the standalone suite. Mirror test ergonomics, not implementation from elsewhere.
- When a test wants a narrowly chosen root, use the explicit entry-method API instead of relying on the default class entry.
- Keep new internal-analysis access narrow and helper-driven. Do not spread raw analysis internals through every test.

## Next test areas

Good follow-up coverage includes:

- exact type-flow precision for fields, parameters, and results
- invoke target precision and dispatch behavior
- class initialization behavior
- option-sensitive precision changes that are observable through the standalone API

## When changing analysis semantics

1. Keep this suite green first.
2. Then run hosted smoke test: `mx helloworld` from `substratevm`.
