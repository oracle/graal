---
layout: docs
toc_group: pgo
link_title: Tracking Profile Quality Over Time.
permalink: /reference-manual/native-image/pgo/profile-quality
---

# Tracking Profile Quality Over Time

The most challenging step in using PGO for Native Image is gathering the profile on a relevant workload.
Also, applications are rarely static in time - the source code evolves and changes as new features are added, bugs fixed algorithms replaced etc.
This means that it is sometimes non-trivial to go through the "build instrumented", "gather profile", "build optimized" steps for every code change in an application.
This naturally raises the question: How long can I use an existing profile as my applications source code changes over time?
This document aims to give some guidance in reaching an answer to this question.

## Approach One: Reuse the profile indefinitely

When building an optimizing build of an application PGO for Native Image is designed to do the best it can with the given profile.
This means that giving an out of date profile (or even a profile of a completely different application) should not break a build.
With that said - wrong profiles can lead to worse performance than building an image with no profiles.
This is because it can lead the compiler to spend optimization resources on the wrong parts of the code and deprioritize the hot ones.

With all that said, reusing a single profiles indefinitely for an evolving application will sooner or later turn counter productive.

## Approach Two: Collect profiles chronologically

Since our application periodically changes it seems only logical to periodically collect fresh profiles.
A concrete example of this would be a daily Linux cron job that builds an instrumented version of the application using the tip of the master branch,
run a workload to gather a profile and upload the resulting iprof file to a FTP server from which other optimizing builds could download it.
This ensures that the difference between the application version that is being build and profiles that are being used is never more than a fixed time interval (24h in our example).

The downside of this approach is that gathering the profiles uses compute time and striking a good balance between
the profile latency and compute resources with respect to how fast the application actually changes is a hard problem.
For example, if the application is question is relatively stable and the source code changes infrequency -
is it OK to re-collect the profiles once a month?
What if the application release schedule does not line up with when we gather the profiles?
This would mean that all releases are built with stale profiles.
Also, what if the application is changing daily, but gathering the profiles for the entire application requires a 12h long workload?

Collecting profiles chronologically seems to be, on first glance, an easy way to deal with the stale profile problem and in some cases it is,
but in many real world cases it requires hard compromises that might be partially or wholly unacceptable.

## Approach Three: Track the profile quality metrics over time

TODO BS Can we talk about Hosted Universe here? Is this something we expect users to understand?

In an attempt to enable users to know the quality of the profile they are using from one build to the next we introduce 
two metrics that native image can report when building an optimizing build: Profile Relevance and Profile Applicability.
Both these metrics are meant to reflect the relationship between the provided profiles and the Hosted Universe of the optimizing build.
This means that a change in the values of these metrics for two optimizing builds using the same profiles reflects a difference in the Universe of the builds.
This fact allows us to quantify the change in the application between the time the profiles were collected and the current build.
Simply put - a change in the values of these metrics between two build is a signal that the profiles should be recollected.
The larger the change - the stronger the signal.

We discuss the details of how we calculate these metrics in the following subsections, but let's take a look at an example of how they can be used first.
Let's consider the example Game Of Life application we explored in [PGO Basic Usage](PGO-Basic-Usage.md).
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

The absolute values of these metrics does not tells us much, and should not be taken as relevant in isolation.
Remember - these metrics describe a relationship between the profile and the Universe of the application being built.
This means that if we change our application and reuse the profile we should see a difference in these values during the build.

Let's consider what happens if we apply a simple "method rename" refactoring to our application as shown in the following diff.

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

From the profile's perspective the `applyRules` method was removed from the Universe and a new method called `applyGameRules` was introduced. 
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

NOTE: The change we made in this example is a rename of a very hot method. 
This change is very likely to cause a performance regression since profiles cannot be applied to a hot method.
A similar change made in the cold code of the application would result in a similar reduction in these metrics, 
but this would likely not impact performance. 
It is important to keep in mind that these metrics are a measure of the relationship between the provided profile and the application universe,
and are not in any way a measurement or prediction of any performance impact a change of the profile, application source code or dependencies may have.
It is also important to keep in mind that there numbers have very little utility when observed in one single build.
Their utility comes from observing the change of these metrics when re-using profile across build or providing a different profile for the same build of an application.

### Profile Applicability

Profile applicability as a metric aims to answer the question "To what extends do elements of the application universe have a corresponding profile which can be applied to them?" 
i.e. "how applicable is this profile to this universe?".
During the compilation of individual methods in the application we keep track of how many times the compilation pipeline request profile information 
as well as how many times it actually receives it.
The profile applicability metric is the ratio of these two number expressed as a percentage.

This means that adding new code to the application (and not to the profile) should result in a reduction in profile applicability.
This is because more code means more requests for profiles and the same number of times the profile *can* be applied is divided with a larger number of total requests for profiles.

Note: A reasonable but wrong expectation would be that a high quality profile should have a profile applicability of 100%, 
as a "good" workload should produce a profile for every opportunity for the compiler.
This is normally not true because a high quality workload will clearly differentiate between the hot and cold parts of the application.
Having a profile applicability of 100% means that all parts of the code are fully profiled, including the cold parts 
(e.g. exception handlers which by should happen exceptionally i.e. rarely).
What this value *should* be is impossible to say as it heavily depends on the application code as well as the representative workload used to produce the profile.
If one is interested if key parts of the hot code are covered by the profile, one should consult the [LCOV coverage](PGO-LCOV.md) 
of the profile to ensure all expected hot paths are covered.

### Profile Relevance

Profile relevance as a metric aims to answer the question "to what extent do the profile contents match the universe?" 
i.e. "how relevant is the profile to the current universe?".
When loading a profile all the data in it is checked against the universe of the application and all data that does not match the universe is dropped.
For example, if we remove a method from a class but use a profile that still has data about that method - all that data will be dropped on loading.
Profile relevance is the percentage of data that was *not* dropped (i.e. was relevant) during loading.

This means that removing code from the application (and not from the profile) should result in a reduction in profile relevance, 
as the percent of data in the profile which is relevant to the new application version is reduced.

On the other hand, adding new code (say a new class or dependency) to the universe would not affect this metric since the amount of data we need to drop from the profile does not change.

Note: A reasonable but wrong expectation would be that gathering building an application with the exact same application version 
as the one used to gather the profile would result in in the profile relevance being 100%.
This is unfortunately not the case because the Hosted Universes of the instrumented image and the optimized image differ in subtle ways. 
For example, the universe of the instrumented image will contain code for gathering the profile data as well as serializing it to a file.
This code is unnecessary and thus not present in the Hosted Universe of the optimizing build.
Looking at our Game Of Life example we can see that the relevance is around 70%.
This is primarily due to our application being very small (a single java class of less than 120 lines of code) thus the differences in the size of the universe are rather exaggerated.
For larger, real-world applications this percentage is expected to be larger, but importantly is executed not to be 100%.
