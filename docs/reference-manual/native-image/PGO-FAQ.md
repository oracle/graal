---
layout: docs
toc_group: pgo
link_title: Frequently Asked Questions
permalink: /reference-manual/native-image/pgo/faq
---

1. Can we use unit tests for profiling?

DRAFT:
- Yes 
    - if you can make them run on an instrumented image which might not be trivial.
    - if your unit tests are a representative workload i.e. Testing getters and setters will not get you much in terms of profiles


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
The instrumentation overhead will typically make the instrumented image slower than the default one
(while we continually strive to minimize the impact of instrumentation, your mileage will vary).

So, if the benchmark accurately represents the workload that you will be running in production,
then it is a good idea to collect the profiles on the instrumented benchmark image,
and subsequently use these profiles to build an optimized image.


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

