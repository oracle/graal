---
layout: docs
toc_group: pgo
link_title: PGO Frequently Asked Questions
permalink: /reference-manual/native-image/optimizations-and-performance/PGO/faq/
---

# Frequently Asked Questions

### Can I use unit tests for profiling?

Yes, it is possible, but usually not recommended.
To use the unit tests to generate the profiles, you should generate an instrumented binary for your test suite, in the same way you generate a native executable for any application.
For example, you could have a `main()` method that starts the test harness.
Once the instrumented binary executes, it will dump the file with the profiles, same as any instrumented binary.

Note that the quality of Profile-Guided Optimization depends on the quality of the profile that you provide as an input to the optimized native build.
You should make sure that your tests accurately represent the workload that will run in production.
In general, it is not easy to guarantee that, because:

- Unit tests are designed to test all the corner-cases of the components, many of which are uncommon in practice (in other words, while they need to be tested and work correctly, corner-cases in your code usually do not need to be fast).
- Different components of your code are not always represented with the same number of unit tests.
  A profile based on a unit-test suite may over-represent the importance of one component, and under-represent the importance of others.
- Unit-test suites evolve over time as more and more tests get added.
  What might accurately represent your application's behavior today, might not accurately represent it tomorrow.

For example, you are implementing web server that serves static content.
Most of the time, the web server will be reading a file from the disk or an in-memory cache, compressing that file, and sending the compressed bytes over the network.
However, a good unit-test suite will test all components of the web server, including code for configuration-file parsing, cache invalidation, or remote debugging, which may execute infrequently or not at all during a typical execution of the web server.
If you collect the profiles across all the unit tests, they will over-represent parts of the code that execute rarely in practice, and in this way misdirect the compiler optimizations.

In conclusion, while this is possible, we do not recommend to use unit tests as profiles, because it is not clear how well they represent what the application does. 
What we recommend instead is to either:

- Identify a subset of *end-to-end tests* that represent important production workloads.
  An end-to-end test simulates what your application does in production, and is more likely to correctly portray how and where the time is spent in your code.
  In the previous web server example, an end-to-end test would start the web server, send thousands of requests to retrieve various URLs, and then shut down the server.
- Or, create a benchmark workload that represents what your application does in production.
  A good benchmark would incorporate characteristics of a typical workload.
  In the previous web server example, a realistic benchmark would incorporate the distribution of requests that was observed when the web-server was running in production.
  That is, the benchmark would model how often a file of a particular size was requested in production, as well as the compression ratios of the files.

### Are PGO profiles sufficiently cross-platform, or should each target be instrumented separately?

Yes, in most cases, the PGO profiles are sufficiently cross-platform.
You can collect the profiles by running an instrumented binary on one platform, but then use those profiles to build an optimized native executable on a different platform.

There are some cases in which Native Image uses different classes and methods depending on the platform for which the binary was built.
For example, the `PosixProcessPropertiesSupport` class contains code that manipulates processes on POSIX-based systems, while the `WindowsProcessPropertiesSupport` class contains code that manipulates processes on Windows.
Similarly, certain parts of the JDK contain platform-specific code.
In these cases, the profile will contain entries for one platform, but the optimized native build will not find profile entries for its platform-specific code.
These corner-cases are rare and typically do not result in a performance impact, but this is something to be aware of.

In conclusion, the best practice is always to collect the profiles on the same platform that is the target for the optimized native executable.
However, using the profiles collected on a different platform should typically work well.

### Can the profiling information be reused after a code change, provided it is limited, or do I need to collect new profiling information for each build?

Yes, the profiling information can always be reused, and a native executable has to be correctly generated.
It is not necessary to collect new profiling information for each build.

Note, however, that the performance impact on the optimized native executable depends on the quality of the profiles.
If the new code of the program significantly diverges from the code for which the profiles was collected, the compiler optimizations will be misled about which code is important. 
If the code changes are sufficiently small, or limited to the cold parts of the program, then using the old profile will usually not compromise the performance of the optimized native binary.

Read more on this topic in the [Tracking Profile Quality guidelines](PGO-Profile-Quality.md).

### Can I also run the benchmark with an instrumented binary?

Yes, an instrumented binary can be produced for any program, including a benchmark.
In fact, using a representative benchmark to collect the profiles is the recommended way of collecting profiles.

Be aware that the instrumentation overhead will typically make the instrumented binary slower than the default (non-instrumented) native executable.
While we continually strive to minimize the overhead of instrumentation, you will likely notice that the instrumented binary is slower,
and your mileage will vary depending on the code patterns in the application that you are running.

Also, note that the benchmark should ideally be representative of the workload that you expect in production.
The more the benchmark's workload corresponds to the production workload, the more likely it is that PGO have a positive performance impact
on the optimized native build.

In conclusion, if the benchmark accurately represents the workload that you will be running in production, then it is a good idea to collect the profiles on the instrumented benchmark binary, and subsequently use these profiles to build an optimized native executable for your production workload.

### How does GraalVM generate a workload for profiling a web application?

GraalVM itself does not generate a workload for profiling a web application that was compiled with Native Image.
Instead, you need to use a load-testing tool to generate the workload.

For example, if your web application exposes several HTTP endpoints, then you need to use a load-tester such as `wrk` to generate a stream of requests to those HTTP endpoints.
The setup for this would be as follows: you build an instrumented binary of your web application, start it in one process, and start a load-tester such as `wrk` in another process.
The duration of the load-test needs to be long enough to exercise the endpoints of your web application that will be most frequently accessed by the production users, using request payloads that you expect to encounter in production.
For a simple web application, duration of 1 minute is typically sufficient to produce profiles of good quality (but this depends on your particular application).
After the load-test completes and the web application exits, it will dump the profiles to a file.

### Why not collect profile in the production environment for a while? For example, collect it only on one instance of the service on Monday from 8:00 till 12:00.

Yes, that is a good way to collect profiles.

An instrumented binary has a certain overhead, which depends on the code patterns in a particular application.
However, if only one instance uses the instrumented binary during a particular period, and all other instances of your service use a normal or PGO-optimized build, then this is generally acceptable in practice.

Find more information on this topic in the [Tracking Profile Quality guidelines](PGO-Profile-Quality.md).
