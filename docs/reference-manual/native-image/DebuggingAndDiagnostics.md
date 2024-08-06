---
layout: docs
toc_group: debugging-and-diagnostics
link_title: Debugging and Diagnostics
permalink: /reference-manual/native-image/debugging-and-diagnostics/
---

# Debugging and Diagnostics

Native Image provides utilities for debugging and inspecting the produced binary:
 - For debugging produced binaries and obtaining performance profile statistics, see [Debug Information](DebugInfo.md)
 - For generating heap dumps, see [Heap Dump Support](guides/create-heap-dump-from-native-executable.md)
 - For JFR events recording, see [JDK Flight Recorder (JFR)](JFR.md)
 - For checking which methods were included in a native executable or a shared library, use the [Inspection Tool](InspectTool.md)
 - For an overview of static analysis results, see [Static Analysis Reports](StaticAnalysisReports.md)
 - For the performance analysis, see the [Linux Perf Profiler Support in Native Image](PerfProfiling.md)