---
layout: docs
toc_group: pgo
link_title: Tracking Profile Quality Over Time.
permalink: /reference-manual/native-image/pgo/profile-quality
---

# Tracking Profile Quality Over Time

The most challenging step in using PGO for Native Image is gathering the profile on a relevant workload.
Also, applications are rarely static in time - the source code evolves and changes as new features are added, bugs fixed algorithms replaced etc.
This means that it is sometimes non-trivial to go through the "build-instrumented-image", "gather-profile", and "build-optimized-image" steps for every code change in an application.
This naturally raises the question: How long can I use an existing profile as my applications source code changes over time?
This document aims to give some guidance in reaching an answer to this question.

## Approach 1: Reuse the Profile Indefinitely

When building an optimizing image of an application, Native-Image PGO aims to do the best it can with the given profile.
This means that giving an out of date profile (or even a profile of a completely different application) should not break a build.
That said, wrong profiles can lead to worse performance than building an image with no profiles.
This is because incorrect profiles can lead the compiler to spend optimization resources on the wrong parts of the code and deprioritize the important parts.

All that said, reusing a single profile indefinitely for an evolving application will sooner or later turn counterproductive.

## Approach 2: Collect Profiles Periodically

Since our application periodically changes, it seems only logical to periodically collect fresh profiles.
A concrete example of this would be a daily Linux cron job that builds an instrumented version of the application using the tip of the master branch,
runs a workload to gather a profile, and uploads the resulting `iprof` file to an FTP server from which other optimizing builds can download it.
This ensures that the difference between the application version that is being built and the profiles that are being used is never greater than a fixed time interval (24h in our example).

Periodically gathering the profiles uses compute time, so it should be balanced against the speed at which the application changes.
If the application is question is relatively stable and the source code changes infrequently, then it is fine to re-profile less often.
Some things to keep in mind:
- It is important to align the profiling gathering with the application-release schedule, to avoid building the releases with stale profiles.
- Ideally, convert the production workload into a synthetic workload, collect the profiles as part of your build, and then create an optimized image with profiles that are always fresh.
That way, you do not risk having stale or misaligned profiles as long as your workload executes the same parts of the application that will later execute in production.

## Approach 3: Track the Profile-Quality Metrics Over Time

To allow users to understand the quality of the profiles they are using for an optimized image,
we introduce two metrics that Native Image can report when building an optimizing image: *profile relevance* and *profile applicability*.
Both these metrics are meant to reflect the relationship between the provided profiles and the methods and classes that appear in the optimized image.
If values of these two metrics change between two optimizing builds (both of which use the same profile),
then something changed in the set of classes and/or methods between those builds (for example, because the source code of the application changed).
This fact allows us to quantify the change in the application between the time the profiles were collected and the current build.
Simply put - a change in the values of these metrics between two builds could be a signal that the profiles should be recollected.
The larger the change - the stronger the signal.

We discuss the details of how we calculate these metrics in the following subsections, but let's take a look at an example of how they can be used first.
Let's consider the example Game Of Life application, which we previously explored in [PGO Basic Usage](PGO-Basic-Usage.md).
If we follow the build instructions as laid out in that document, but we add the `-H:+PGOPrintProfileQuality` flag when building the optimized build,
we should see an additional INFO line reporting said metrics.

```bash
$ $GRAALVM_HOME/bin/native-image -cp . GameOfLife -o gameoflife-pgo --pgo=gameoflife.iprof
    ========================================================================================================================
    GraalVM Native Image: Generating 'gameoflife-pgo' (executable)...
    ========================================================================================================================
    For detailed information and explanations on the build output, visit:
    ...
    [5/8] Inlining methods...     [***]                                                                      (0.4s @ 0.28GB)
    Info: PGO: Profile applicability is 21.74%. Profile relevance is 72.71%.
    [6/8] Compiling methods...    [***]                                                                      (6.8s @ 0.29GB)
    ...
```

The absolute values of these metrics do not tell us much, and should not be considered in isolation.
Remember - these metrics describe the relationship between the profile and the classes and methods of the application.
This means that if we change our application and reuse the profile we should see a difference in these values during the build.

Let's consider what happens if we apply a simple "method-rename" refactoring to our application as shown in the following diff.

```
$ git diff GameOfLife.java
diff --git a/GameOfLife/GameOfLife.java b/GameOfLife/GameOfLife.java
index d229305..e8c6932 100644
--- a/GameOfLife/GameOfLife.java
+++ b/GameOfLife/GameOfLife.java
@@ -35,13 +35,13 @@ public class GameOfLife {
         // Loop through every cell
         for (int l = 0; l < M; l++) {
             for (int m = 0; m < N; m++) {
-                applyRules(grid, future, l, m, getAliveNeighbours(grid, l, m));
+                applyGameRules(grid, future, l, m, getAliveNeighbours(grid, l, m));
             }
         }
         return future;
     }

-    private static void applyRules(int[][] grid, int[][] future, int l, int m, int aliveNeighbours) {
+    private static void applyGameRules(int[][] grid, int[][] future, int l, int m, int aliveNeighbours) {
         // Implementing the Rules of Life

         if ((grid[l][m] == 1) && (aliveNeighbours < 2)) {
```

From the profile's perspective the `applyRules` method was removed from the set of methods in the application and a new method called `applyGameRules` was introduced.
Rerunning the optimized build with the same profile and the modified applications gives us the following output.

```bash
$ $GRAALVM_HOME/bin/native-image -cp . GameOfLife -o gameoflife-pgo --pgo=gameoflife.iprof
    ========================================================================================================================
    GraalVM Native Image: Generating 'gameoflife-pgo' (executable)...
    ========================================================================================================================
    For detailed information and explanations on the build output, visit:
    ...
    [5/8] Inlining methods...     [***]                                                                      (0.4s @ 0.28GB)
    Info: PGO: Profile applicability is 21.67%. Profile relevance is 72.66%.
    [6/8] Compiling methods...    [***]                                                                      (6.8s @ 0.29GB)
    ...
```

Recall that the profile applicability in our original build was 21.74% and in this new one it is 21.67%.
Similarly, the profile relevance in our original build was 72.71% and in this new one it is 72.66%.
The small change we made to the code resulted in a small change in these metrics,
informing us that the profile might be slightly out-of-date.

NOTE: The change that we made in this example is a rename of a very hot method.
This change is very likely to cause a performance regression since profiles cannot be applied to a hot method.
A similar change made in the cold code of the application would result in a similar reduction in these metrics,
but this would likely not impact performance.
It is important to keep in mind that these metrics are a measure of the relationship between the provided profile and the set of methods in the application,
and are not in any way a measurement or prediction of any performance impact a change of the profile, application source code or dependencies may have.
It is also important to keep in mind that there numbers have very little utility when observed in one single build.
Their utility comes from observing the change of these metrics when re-using profile across build or providing a different profile for the same build of an application.

### Profile Applicability

Profile applicability as a metric aims to answer the following question:
"To what extent do the methods of the application have a corresponding profile that can be applied to them?".
Put differently: "How applicable is this profile to the methods of the application?".
During the compilation of individual methods in the application, we keep track of how many locations N in the code needed a profile,
as well as how many times S a profile was found for those locations.
The profile applicability metric is the ratio `S / N`, expressed as a percentage.

This means that adding new code to the application (and not to the profile) should result in a reduction in profile applicability.
This is because more code means more requests for profiles, and the same number of times S that the profile *can* be applied
is divided with a larger number N of total requests for profiles.

Note: it is wrong to expect a profile applicability of 100%.
A good workload will in almost all cases differentiate between the hot and the cold parts of the application,
and will not execute some of the cold parts of the code.
For this reason, a good profile will not have a 100% applicability --
the profile will not contain any entries for the cold parts of the application (such as, for example, the exception handlers),
which are anyway never executed in the real workload.
A 100% applicability would mean that all parts of the code in the image were fully profiled, which is almost never the case in practice.

### Profile Relevance

Profile relevance as a metric aims to answer the question: "To what extent do the profile contents match the methods of the application?".
In other words, "How relevant is the profile for the methods being compiled?".
When loading a profile, all its data is checked against the set of methods of the application, and all the entries that do not match those methods are dropped.
For example, if we remove a method from a class but use a profile that still has entries for that method, then all those entries will be dropped during profile-loading.
Profile relevance is the percentage of data that was *not* dropped (i.e. was relevant) during loading.

This means that removing code from the application (and not from the profile) should result in a reduction in profile relevance,
as the percent of data in the profile which is relevant to the new application version is reduced.

On the other hand, adding new code (say a new class or dependency) to the application would not affect this metric since the amount of data we need to drop from the profile does not change.

Note: it is wrong to expect that building an optimized image of exactly the same application
as the one used to gather the profile needs to result in in the profile relevance of 100%.
This is not the case because the methods of the instrumented image and the optimized image differ in subtle ways.
For example, the instrumented image contains code for gathering the profile data as well as code for serializing that data to a file.
This code is unnecessary and thus not present in the optimizing image.
Looking at our Game-of-Life example, we can see that the relevance is around 70%.
This is primarily due to our application being very small (a single Java class of less than 120 lines of code)
thus the differences in the set of methods of the instrumented and the optimized image are rather exaggerated.
For larger, real-world applications, this percentage is typically larger, but not 100%.

