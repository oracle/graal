# TRegex Changelog

This changelog summarizes major changes between TRegex versions relevant to language implementors integrating TRegex into their language. This document will focus on API changes relevant to integrators of TRegex.

## Version 1.0.0 RC10

* Added debugging options and loggers to TRegex.
     * TRegex now accepts the `--regex.always-eager`, `--regex.dump-automata` and `--regex.step-execution` debugging options, which can be used in client language launchers.
     * Introduced `regex` loggers `SwitchToEager`, `TotalCompilationTime`, `Phases`, `BailoutMessages`, `AutomatonSizes`, `CompilerFallback`, `InternalErrors` and `TRegexCompilations`. These can be enabled in the launchers by setting, e.g., `--log.regex.Phases.level=ALL`.
