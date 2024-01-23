---
layout: docs
toc_group: pgo
link_title: Frequently Asked Questions
permalink: /reference-manual/native-image/pgo/faq
---

1. Can we use unit tests for profiling?

Yes, that is possible, but usually not recommended.

To use the unit tests to generate the profiles,
you should generate an instrumented native image for your test suite,
in the same way you generate a native image for any program.
For example, you could have a `main` function that starts the test harness.
After this instrumented image executes, it will dump the file with the profiles,
same as any instrumented image.

However, be aware that the quality of profile-guided optimizations depends on the quality of the profile
that you provide as an input to the optimized-image build.
You should make sure that your tests accurately represent the workload that will run in production.
In general, it is not easy to guarantee that, because:

- Unit tests are designed to test all the corner-cases of the components,
  many of which are uncommon in practice (i.e. while they need to be tested and work correctly,
  corner-cases in your code usually do not need to be fast).
- Different components of your code are not always represented with the same number of unit tests.
  A profile based on a unit-test suite may over-represent the importance of one component,
  and under-represent the importance of others.
- Moreover, unit-test suites evolve over time as more and more tests get added.
  What might accurately represent your application's behavior today,
  might not accurately represent it tomorrow.

For example, let's say that you are implementing web server that serves static content.
Most of the time, the web server will be reading a file from the disk or an in-memory cache,
compressing that file, and sending the compressed bytes over the network.
However, a good unit-test suite will test all components of the web server,
including code for configuration-file parsing, cache invalidation, or remote debugging,
which may execute infrequently or not at all during a typical execution of the web-server.
If you collect the profiles across all the unit tests,
they will over-represent parts of the code that execute rarely in practice,
and in this way misdirect the compiler optimizations.

Thus, we do not generally recommend to use your unit tests as profiles,
because it is not clear how well they represent what the application does.
What we recommend instead is to either:

- Identify a subset of *end-to-end tests* that represent important production workloads.
  An end-to-end test simulates what your application does in production, and is more likely
  to correctly portray how and where the time is spent in your code.
  In the previous web-server example, an end-to-end test would start the web-server,
  send thousands of requests to retrieve various URLs, and then shut down the server.
- Or, create a benchmark workload that represents what your application does in production.
  A good benchmark would incorporate characteristics of a typical workload.
  In the previous web-server example, a realistic benchmark would incorporate
  the distribution of requests that was observed when the web-server was running in production.
  I.e. the benchmark would model how often a file of a particular size was requested in production,
  as well as the compression ratios of the files.


2. Are PGO profiles sufficiently cross platform, or should each target be instrumented separately?

DRAFT:
    - Yes they are for the most part BUT
        - We don't do any mapping 
            - there might be some platform specific classes and methods that are different from the profile (e.g. com.oracle.svm.core.posix.headers.linux.LinuxPthread)
            - This profiles will be dropped and the equivalent platform-specifc class will be missing profiles
    - Best practice is - get profiles on the same platform you build the optimized build.


3. Can the profiling information be reused after a code change, provided it is limited,
   or do you need to collect new profiling information for each build?

DRAFT:
    - Yes they are for the most part BUT
        - Adding methods will reduce your profile coverage
        - Removing methods will reduce your profile applicability 
        - Modifying methods might make the profile unaplicable  (invoke on bci x becomes on bci y, the compiler can't make the connection)


4. Can you also run the benchmark with an instrumented image?

Yes, an instrumented native image can be produced for any program, including a benchmark.
Be aware that the instrumentation overhead will typically make the instrumented image slower
than the default (non-instrumented) image.
While we continually strive to minimize the overhead of instrumentation,
you will likely notice that the instrumented image is slower,
and your mileage will vary depending on the code patterns
in the application that you are running.

In short, if the benchmark accurately represents the workload that you will be running in production,
then it is a good idea to collect the profiles on the instrumented benchmark image,
and subsequently use these profiles to build an optimized image for your production workload.


5. How to find out which code paths were missed during profiling?

DRAFT:
    - LCOV coverage


6. How does GraalVM generate a workload for profiling a web application?

DRAFT:
    - It does not and pretty much can not...  It's up to the developers


7. Why not collect profile in the production environment for a while?
   For example, collect it only on one instance on Monday from 8:00 till 12:00.

DRAFT:
    - If you can - DO! Those are the best profiles. Be mindful that the overhead of the instrumented image is currently quite high.

