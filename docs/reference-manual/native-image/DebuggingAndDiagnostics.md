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
 - For performance analysis, see [Linux Perf Profiler Support in Native Image](PerfProfiling.md)
 - For an overall insight regarding build phases and the contents of a native executable, use [Build Reports](BuildReport.md)
 - For native memory tracking, see [Native Memory Tracking (NMT)](NMT.md)
 - See the [Java Diagnostic Command documentation](JCmd.md) for instructions on using `jcmd`.
 - For Java Debug Wire Protocol (JDWP) support in Native Image to enable debugging with standard Java tooling, see [Java Debug Wire Protocol (JDWP)](JDWP.md).
