# AGENTS.md — Standalone points-to driver (`com.oracle.graal.pointsto.standalone`)

Scope:

- standalone analysis driver/runtime wiring under
  `src/com/oracle/graal/pointsto/standalone/**`

This file extends:

- `../com.oracle.graal.pointsto/AGENTS.md`

If instructions conflict for standalone-driver code, this file takes precedence.

## Purpose

`com.oracle.graal.pointsto.standalone` provides the CLI-style standalone points-to runner around
the core points-to engine.

Main entry and orchestration class:

- `src/com/oracle/graal/pointsto/standalone/PointsToAnalyzer.java`

## Key entrypoints and lifecycle

- `PointsToAnalyzer.main(String[] args)`
    - process args and run analyzer.
- `PointsToAnalyzer.createAnalyzer(String[] args)`
    - separates main-entry class vs hosted-style options and initializes analyzer state.
- `PointsToAnalyzer.run()`
    - registers entry methods, runs feature hooks (`before/during/onAnalysisExit`), executes analysis,
      and emits reports.
- `PointsToAnalyzer.registerEntryMethods()`
    - supports both main-class entry and file-driven entries.

## Important supporting classes

- `StandaloneOptions`
    - standalone-specific options:
        - `AnalysisTargetAppCP`
        - `AnalysisEntryPointsFile`
        - `ReportsPath`
    - report directory helper: `StandaloneOptions.reportsPath(...)`.
- `MethodConfigReader`
    - parses and registers entry methods from configured entrypoint file.
- `StandalonePointsToAnalysis`
    - standalone `PointsToAnalysis` specialization (notably cleanup + `<clinit>` handling).
- `StandaloneHost`
    - standalone host VM behavior and graph-builder integration.
- `features/StandaloneAnalysisFeatureManager`
    - loads analysis-time features from options.
- `features/StandaloneAnalysisFeatureImpl`
    - feature access implementations for before/during/on-exit phases.

## Wiring and suite references

- `substratevm/mx.substratevm/suite.py`
    - project: `com.oracle.graal.pointsto.standalone`
    - distribution: `STANDALONE_POINTSTO`
    - test project: `com.oracle.graal.pointsto.standalone.test` depends on `STANDALONE_POINTSTO`
    - test distribution: `STANDALONE_POINTSTO_TESTS`

## Validation workflow

1. `mx unittest com.oracle.graal.pointsto.standalone.test`
2. `mx helloworld` (from `substratevm`) when hosted integration behavior is touched.

## Related AGENTS files

- Core points-to guidance:
    - `../com.oracle.graal.pointsto/AGENTS.md`
- Standalone test guidance (consumes this module):
    - `../com.oracle.graal.pointsto.standalone.test/AGENTS.md`
