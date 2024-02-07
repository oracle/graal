---
layout: docs
toc_group: pgo
link_title: Frequently Asked Questions
permalink: /reference-manual/native-image/pgo/faq
---

# Frequently Asked Questions

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

In conclusion, while this is possible,
we do not generally recommend to use your unit tests as profiles,
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

Yes, in almost all cases, the PGO profiles are sufficiently cross-platform.
You can collect the profiles by running the instrumentation image on one platform,
but then use those profiles to build an optimized image on a different platform.

There are some cases in which Native Image uses different classes and methods
depending on the platform for which the image was built.
For example, the `PosixProcessPropertiesSupport` class contains code that
manipulates processes on POSIX-based systems,
while the `WindowsProcessPropertiesSupport` class contains code
that manipulates processes on Windows.
Similarly, certain parts the JDK contain platform-specific code.
In these cases, the profile will contain entries for one platform,
but the optimized image build will not find profile entries for its platform-specific code.
These corner-cases are rare and typically will not result in a performance impact,
but this is something to be aware of.

In conclusion, the best practice is always to collect the profiles on the same platform
that is the target for the optimized image.
However, using the profiles collected on a different platform will typically work well.

3. Can the profiling information be reused after a code change, provided it is limited,
   or do you need to collect new profiling information for each build?

Yes, the profiling information can always be reused, and the native image has to be correctly generated.
It is not necessary to collect new profiling information for each build.

Note, however, that the performance impact on the optimized image depends on the quality of the profiles.
If the new code of the program significantly diverges
from the code for which the profiles was collected,
the compiler optimizations will be misled about which code is important.

Let's consider some of the possible code changes and how they affect the program:

- If you add additional methods to your codebase, that will reduce the profile applicability.
  An optimized-image build will not be able to associate profile information to these methods,
  so they may be suboptimally compiled.
- If you remove existing methods from the codebase will reduce the profile's relevance.
  Some profile entries will not be used.
- Method renaming will do both -- from the perspective of PGO, a renamed method
  is a different method.
- Modifying the code of a method may prevent the application of the profile
  when that method is compiled during the optimized image build.
- However, all of the above may have no performance impact
  if the method is *cold* (i.e. infrequently executed).

In conclusion, it is always possible to use an outdated profile to generate an optimized image.
Moreover, if the code changes are sufficiently small, or limited to the cold parts of the program,
then using the old profile will usually not compromise the performance of the optimized image.

For more information on this topic consult the [Tracking Profile Quality Over Time](PGO-Profile-Quality.md) page.

4. Can you also run the benchmark with an instrumented image?

Yes, an instrumented native image can be produced for any program, including a benchmark.
In fact, using a representative benchmark to collect the profiles
is the recommended way of collecting profiles.

Be aware that the instrumentation overhead will typically make the instrumented image slower
than the default (non-instrumented) image.
You might notice that the benchmark runs slower when compiled to an instrumented image.
While we continually strive to minimize the overhead of instrumentation,
you will likely notice that the instrumented image is slower,
and your mileage will vary depending on the code patterns
in the application that you are running.

Also, be aware that the benchmark should ideally be representative
of the workload that you expect in production.
The more the benchmark's workload corresponds to the production workload,
the morely likely it is that PGO have a positive performance impact
on the optimized image.

In conclusion, if the benchmark accurately represents the workload that you will be running in production,
then it is a good idea to collect the profiles on the instrumented benchmark image,
and subsequently use these profiles to build an optimized image for your production workload.

5. How to find out which code paths were missed during profiling?


6. How does GraalVM generate a workload for profiling a web application?

GraalVM itself does not generate a workload for profiling a web application
that was compiled with Native Image.
Instead, you need to use a load-testing tool to generate the workload.

For example, if your web application exposes several HTTP endpoints,
then you need to use a load-tester such as `wrk`
to generate a stream of requests to those HTTP endpoints.
The setup for this would be as follows:
you build the instrumented image of your web application,
start it in one process, and start a load-tester such as `wrk` in another process.
The duration of the load-test needs to be long enough to exercise the endpoints
of your web application that will be most frequently exercised by the production users,
using request payloads that you expect to encounter in production.
For simple web applications, duration of 1 minute is typically sufficient
to produce profiles of good quality (but again, this depends on your particular application).
After the load-test completes and the web application exits,
it will dump the profiles to a file.

7. Why not collect profile in the production environment for a while?
   For example, collect it only on one instance of the service on Monday from 8:00 till 12:00.

Yes, that is a good way to collect profiles.

As argued earlier, the instrumentation image has a certain overhead,
which depends on the code patterns in a particular application.
However, if only one instance uses the instrumentation image during a particular period,
and all the other instances of your service use a normal or optimized build,
then this is generally acceptable in practice.

