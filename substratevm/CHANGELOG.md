# Native Image Changelog

This changelog summarizes major changes to GraalVM Native Image.

## Version 22.1.0
* (GR-36568) Add "Quick build" mode, enabled through option `-Ob`, for quicker native image builds.
* (GR-35898) Improved handling of static synchronized methods: the lock is no longer stored in the secondary monitor map, but in the mutable DynamicHubCompanion object.
* Remove support for JDK8. As a result, `JDK8OrEarlier` and `JDK11OrLater` have been deprecated and will be removed in a future release.
* (GR-26814) (GR-37018) (GR-37038) Red Hat added support for the following JFR events: `SafepointBegin`, `SafepointEnd`, `GarbageCollection`, `GCPhasePause`, and `GCPhasePauseLevel*`. All GC-related JFR events are currently limited to the serial GC.
* (GR-35721) Deprecate `-H:±BuildOutputUseNewStyle` option. The old build output style will be removed in a future release.
* (GR-36905) Allow incomplete classes at build-time is now default. Add --link-at-build-time option and @<prop-values-file> support for native-image.properties. Add --link-at-build-time-paths option.

## Version 22.0.0
* (GR-33930) Decouple HostedOptionParser setup from classpath/modulepath scanning (use ServiceLoader for collecting options).
* (GR-33504) Implement --add-reads for native-image and fix --add-opens error handling.
* (GR-33983) Remove obsolete com.oracle.svm.thirdparty.jline.JLineFeature from substratevm:LIBRARY_SUPPORT.
* (GR-34577) Remove support for outdated JDK versions between 11 and 17. Since JDK versions 12, 13, 14, 15, 16 are no longer supported, there is no need to explicitly check for and allow these versions.
* (GR-29957) Removed the option -H:SubstitutionFiles= to register substitutions via a JSON file. This was an early experiment and is no longer necessary.
* (GR-32403) Use more compressed encoding for stack frame metadata.
* (GR-35152) Add -H:DisableURLProtocols to allow specifying URL protocols that must never be included in the image.
* (GR-35085) Custom prologue/epilogue/handleException customizations of @CEntryPoint must be annotated with @Uninterruptible. The synthetic methods created for entry points are now implicitly annotated with @Uninterruptible too.
* (GR-34935) More compiler optimization phases are run before static analysis: Conditional Elimination (to remove redundant conditions) and Escape Analysis.
* (GR-33602) Enable [new user-friendly build output mode](https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/BuildOutput.md). The old output can be restored with `-H:-BuildOutputUseNewStyle`.
