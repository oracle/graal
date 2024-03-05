---
layout: docs
toc_group: pgo
link_title: Tracking Profile Quality
permalink: /reference-manual/native-image/optimizations-and-performance/PGO/profile-quality/
---

# Tracking Profile Quality

The most challenging step when using PGO for Native Image is gathering a profile for a relevant workload.
Because your source code evolves, your application is rarely static in time.
It is sometimes very time consuming to go through the steps of "build-instrumented-image", "gather-profile", and "build-optimized-image" for every change to your source code.
This document gives some answers to the question "How long can I continue to use an existing profile as my application's source code changes over time?"

## Approach 1: Reuse the Profile Indefinitely

When you build an optimized application, Native Image aims to do the best it can with the given profile.
This means that providing an out-of-date profile for your application (or even a profile for a completely different application) should not stop Native Image from producing a native executable.

> Note: An incorrect profile can result in worse performance than no profile. This is because an incorrect profile can lead the compiler to spend optimization resources on the wrong elements of the application and deprioritize the important elements.

All that said, reusing a single profile indefinitely for an evolving application will sooner or later turn counterproductive.

## Approach 2: Collect Profiles Periodically

Since your application can periodically change, it is logical to periodically collect a fresh profile.
One way to achieve this would be a daily Linux cron job that builds an instrumented version of the application using the tip of the master branch, runs a workload to gather a profile, and uploads the resulting _iprof_ file to an FTP server from which other optimizing builds can download it.
This ensures that the difference between the application version that is being built and the profile that is being used is never greater than a fixed time interval (24h in this example).

However, gathering a profile periodically extends compute time, so it should be balanced against the frequency at which the application changes.
If your application is relatively stable and the source code changes infrequently, then it is fine to re-profile less often.
Some things to keep in mind:
- Align your profiling schedule with the application-release schedule, to avoid building an application with a stale profile.
- Ideally, convert the production workload into a reproducible workload, collect the profiles as part of your build, and then create an optimized native executable with profiles that are always fresh.

That way, you do not risk having stale or misaligned profiles as long as your workload executes the same parts of the application that will later execute in production.

## Approach 3: Track the Profile-Quality Metrics Over Time

To better understand the quality of a profile, Native Image provides two metrics that you can request when building an optimized executable: *profile relevance* and *profile applicability*.
These metrics reflect the relationship between a profile and the methods and classes that appear in an optimized executable.

A change in the values of these two metrics between builds (that use the same profile) indicates that the set of classes and/or methods in those builds has also changed (between the time the profile was collected and the current build).

### How to Obtain Profile-Quality Metrics

To calculate and print the profile-quality metrics, pass the `-H:+PGOPrintProfileQuality` option when building an optimized native executable. (This option is experimental.)

Let's consider the Game Of Life example application which was introduced in [Basic Usage of Profile-Guided Optimization](PGO-Basic-Usage.md):
```bash
native-image -cp . GameOfLife -o gameoflife-pgo --pgo=gameoflife.iprof -H:+PGOPrintProfileQuality
```

In the build output, at phase 5, you should see an additional line about profile applicability and profile relevance:
```bash
GraalVM Native Image: Generating 'gameoflife-pgo' (executable)
...
[5/8] Inlining methods...     [***]                                                                      (0.4s @ 0.28GB)
Info: PGO: Profile applicability is 21.74%. Profile relevance is 72.71%.
...
```

The absolute values of these metrics do not tell you much, and should not be considered in isolation.
As mentioned earlier, these metrics describe the relationship between the profile and application's code.
If you change the application and reuse the profile, you should see a change in the values of the metrics.

For example, apply a simple "method-rename" refactoring to the `applyRules ()` application method.
From the profile's perspective, the `applyRules ()` method was removed from the set of methods in the application and a new method called `applyGameRules` was introduced.
Rerunning the optimized build with the same profile and the modified application returns the following output:

```bash
native-image -cp . GameOfLife -o gameoflife-pgo --pgo=gameoflife.iprof -H:+PGOPrintProfileQuality
========================================================================================================================
GraalVM Native Image: Generating 'gameoflife-pgo' (executable)...
...
[5/8] Inlining methods...     [***]                                                                      (0.4s @ 0.28GB)
Info: PGO: Profile applicability is 21.67%. Profile relevance is 72.66%.                                                                  (6.8s @ 0.29GB)
...
```

Recall that the profile applicability in the first build was 21.74%, and now it is 21.67%.
Similarly, the profile relevance in the first build was 72.71%, and now it is 72.66%.
The small change to the code resulted in a small metrics value change, informing you that the profile might be slightly out-of-date.

> Note: In this example you renamed of a very hot method. This change is likely to cause a performance regression since profiles cannot be applied to a hot method. A similar change made in the cold code of the application would result in a similar reduction in these metrics,
but this should not impact performance.
Note that these metrics are a measure of the relationship between the provided profile and the set of methods in the application, and are not in any way a measurement or prediction of any performance impact a change of the profile, application source code, or dependencies may have.
These numbers also make little sense or have little utility when observed in one single build.
Their utility comes from observing the metrics change when re-using profile across builds or providing a different profile for the same build of the application.

### Profile Quality Metric: _Applicability_

The applicability metric answers the following question: "How applicable is this profile to the application's methods?".
During the compilation of individual methods in the application, it keeps track of how many locations N in the code needed a profile,
and how many times S a profile was available.
The profile applicability metric is the ratio `S / N`, expressed as a percentage.

This means that adding new code to the application (and not to the profile) should result in a reduction in profile applicability.
This is because more code means more requests for profiles, and the same number of times that the profile *can* be applied (S),
is divided with a larger number of total requests for profiles (N).

> Note: It is wrong to expect a profile applicability of 100%.
A good workload will, in almost all cases, differentiate between the hot and the cold parts of the application,
and will not execute some of the cold parts of the code.
For this reason, the profile will not contain any entries for the cold parts of the application (such as, for example, the exception handlers),
which are anyway rarely executed in the real workload.
A 100% applicability would mean that all parts of the code in the image were fully profiled, which is almost never the case in practice.

### Profile Quality Metric: _Relevance_

The relevance metric aims answers the question: "To what extent do the profile contents match the application methods?".
When loading a profile, all its data is checked against the set of application methods, and all the entries that do not match those methods are dropped.
For example, if you remove a method from a class but use a profile that still has entries for that method, then all those entries will be dropped during profile-loading.
Profile relevance is the percentage of data that was *not* dropped during loading.

This means that removing code from the application (and not from the profile) should result in the profile relevance reduction, because the percent of data in the profile which is relevant to the new application version is reduced.
On the other hand, adding new code (say a new class or dependency) to the application would not affect this metric since the amount of data you need to drop from the profile does not change.

> Note: It is wrong to expect that building an optimized binary of exactly the same application as the one used to gather the profile will result in the profile relevance of 100%.
This is not the case because the methods of the instrumented binary and the optimized binary differ in subtle ways.
For example, the instrumented binary contains code for gathering the profile data as well as code for serializing that data to a file.
This code is unnecessary and thus not present in the optimized binary.
Looking at the Game Of Life example, the relevance around 70% is primarily due to the application being very small (a single Java class of less than 120 lines of code). 
Thus the differences in the set of methods of the instrumented and the optimized binary are rather exaggerated.
For larger, real-world applications, this percentage is typically larger, but not 100%.

### Further Reading

* [Merging Profiles from Multiple Sources](PGO-Merging-Profiles.md)
* [PGO Frequently Asked Questions](PGO-FAQ.md)