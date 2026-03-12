# AGENTS.md — Standalone points-to tests (`com.oracle.graal.pointsto.standalone.test`)

Scope:

- standalone points-to test harness and test cases in this project

This file extends:

- `../com.oracle.graal.pointsto/AGENTS.md`
- `../com.oracle.graal.pointsto.standalone/AGENTS.md`

If instructions conflict for standalone tests, this file takes precedence.

## Primary test gate

- `mx unittest com.oracle.graal.pointsto.standalone.test`

Suite wiring reference:

- `substratevm/mx.substratevm/suite.py`
    - project: `com.oracle.graal.pointsto.standalone.test`
    - distribution: `STANDALONE_POINTSTO_TESTS`

## Key files

- `PointstoAnalyzerTester.java`
- `StandaloneAnalyzerReachabilityTest.java`
- `StandaloneAnalysisReportTest.java`
- `AnalysisPerformanceTest.java`

## When changing analysis semantics

1. Keep this suite green first.
2. Then run hosted smoke test: `mx helloworld` from `substratevm`.
