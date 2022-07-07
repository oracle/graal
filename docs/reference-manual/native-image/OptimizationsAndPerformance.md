---
layout: ni-docs
toc_group: optimizations-and-performance
link_title: Optimizations and Performance
permalink: /reference-manual/native-image/optimizations-and-performance/
---

# Optimizations and Performance

Native Image provides advanced mechanisms to further optimize the generated binary:

 - Profile-Guided Optimizations (PGO) can provide additional performance gain and higher throughput for most native images. See [Optimize a Native Executable with PGO](guides/optimize-native-executable-with-pgo.md).
 - Choosing an appropriate Garbage Collector and tailoring the garbage collection policy can reduce GC times. See [Memory Management](MemoryManagement.md).
 - Loading application configuration during the image build can speed up application startup. See [Class Initialization at Image Build Time](ClassInitialization.md).
