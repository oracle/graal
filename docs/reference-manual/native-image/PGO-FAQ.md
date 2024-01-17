---
layout: docs
toc_group: pgo
link_title: Frequently Asked Questions
permalink: /reference-manual/native-image/pgo/faq
---

1. Can we use unit tests for profiling?

2. Are PGO profiles sufficiently cross platform, or should each target be instrumented separately?

3. Can the profiling information be reused after a code change, provided it is limited,
   or do you need to collect new profiling information for each build?

4. Can you also run the benchmark with an instrumented image?

5. How to find out which code paths were missed during profiling?

6. How does GraalVM generate a workload for profiling a web application?

7. Why not collect profile in the production environment for a while?
   For example, collect it only on one instance on Monday from 8:00 till 12:00.

