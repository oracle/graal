# TRegex Changelog

This changelog summarizes major changes between TRegex versions relevant to language implementors integrating TRegex into their language. This document will focus on API changes relevant to integrators of TRegex.

## Version 22.1.0

* Added a `lastGroup` field to regex results. Its value is tracked when using the Python regex dialect and it indicates the last capture group to have been matched.
* Added boolean matching mode (`execBoolean`), which allows omitting `RegexResult` objects in cases where capture groups are known to never be queried.
* Added `MustAdvance` and `PythonMode` options for better Python support.
* Support for named character escapes in Python regular expressions.

## Version 22.0.0

* Added new `ASCII` encoding that callers can use when compiling a regex to limit the range of code point matches to [0x00, 0x7f].
* Updated Unicode data (case-folding, character properties) to version 14 of the Unicode standard.

## Version 21.3.0

* Support for case-insensitive matching in Ruby regular expressions.
* Added Regexp option IgnoreAtomicGroups for treating atomic groups as ordinary groups.
* Compiled regular expressions export the `isBacktracking` boolean member, which can be used to determine if the regexp can backtrack and potentially lead to exponential runtime.

## Version 21.2.0

* Added support for the `hasIndices` (`d`) flag in ECMAScript regular expressions.

## Version 20.2.0

* Introduced on-the-fly decoding for UTF-16 strings.

## Version 20.1.0

* Completed feature support for EcmaScript 2020 regular expressions via a new built-in back-tracking engine.

## Version 1.0.0 RC15

* Removed properties `regex`, `input`, `start`, and `end` from result objects.
* Introduced methods `getStart(groupNumber)` and `getEnd(groupNumber)` as a replacement for `start` and `end` in result objects.
* Regex syntax exceptions are now to be treated as plain `TruffleException`s - languages using TRegex should not introduce any source code dependency on TRegex.
* Moved `groupCount` property from regex result to compiled regex objects.

## Version 1.0.0 RC10

* Added the possibility to log the actions of the compiler.
     * Introduced the `regex` loggers `SwitchToEager`, `TotalCompilationTime`, `Phases`, `BailoutMessages`, `AutomatonSizes`, `CompilerFallback`, `InternalErrors` and `TRegexCompilations`. These can be enabled in your launchers by setting, e.g., `--log.regex.Phases.level=ALL`.
