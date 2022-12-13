# Native Image Changelog

This changelog summarizes major changes to GraalVM Native Image.

## Version 23.0.0
* (GR-40187) Report invalid use of SVM specific classes on image class- or module-path as error. As a temporary workaround, `-H:+AllowDeprecatedBuilderClassesOnImageClasspath` allows turning the error into a warning.
* (GR-41196) Provide `.debug.svm.imagebuild.*` sections that contain build options and properties used in the build of the image.
* (GR-41978) Disallow `--initialize-at-build-time` without arguments. As a temporary workaround, `-H:+AllowDeprecatedInitializeAllClassesAtBuildTime` allows turning this error into a warning.
* (GR-41674) Class instanceOf and isAssignableFrom checks do need to make the checked type reachable.
* (GR-41100) Add support for `-XX:HeapDumpPath` to control where heap dumps are created.
* (GR-42148) Adjust build output to report types (primitives, classes, interfaces, and arrays) instead of classes and revise the output schema of `-H:BuildOutputJSONFile`.

## Version 22.3.0
* (GR-35721) Remove old build output style and the `-H:±BuildOutputUseNewStyle` option.
* (GR-39390) (GR-39649) (GR-40033) Red Hat added support for the JFR events `JavaMonitorEnter`, `JavaMonitorWait`, and `ThreadSleep`.
* (GR-39497) Add `-H:BuildOutputJSONFile=<file.json>` option for [JSON build output](https://github.com/oracle/graal/edit/master/docs/reference-manual/native-image/BuildOutput.md#machine-readable-build-output). Please feel free to provide feedback so that we can stabilize the schema/API.
* (GR-40170) Add `--silent` option to silence the build output.
* (GR-39475) Add initial support for jvmstat.
* (GR-39563) Add support for JDK 19 and Project Loom Virtual Threads (JEP 425) for high-throughput lightweight concurrency. Enable on JDK 19 with `native-image --enable-preview`.
* (GR-40264) Add `--enable-monitoring=<all,heapdump,jfr,jvmstat>` option to enable fine-grained control over monitoring features enabled in native executables. `-H:±AllowVMInspection` is now deprecated and will be removed in a future release.
* (GR-15630) Allow multiple classes with the same name from different class loaders.
* (GR-40198) Introduce public API for programmatic JNI / Resource / Proxy / Serialization registration from Feature classes during the image build.
* (GR-38909) Moved strictly-internal annotation classes (e.g. @AlwaysInline, @NeverInline, @Uninterruptible, ...) out of com.oracle.svm.core.annotate. Moved remaining annotation classes to org.graalvm.sdk module.
* (GR-40906) Add RuntimeResourceAccess#addResource(Module module, String resourcePath, byte[] resource) API method that allows injecting resources into images

## Version 22.2.0
* (GR-20653) Re-enable the usage of all CPU features for JIT compilation on AMD64.
* (GR-38413) Add support for `-XX:+ExitOnOutOfMemoryError`.
* (GR-37606) Add support for URLs and short descriptions to `Feature`. This info is shown as part of the build output.
* (GR-38965) Heap dumps are now supported in Community Edition.
* (GR-38951) Add `-XX:+DumpHeapAndExit` option to dump the initial heap of a native executable.
* (GR-37582) Run image-builder on module-path per default. Opt-out with env setting `USE_NATIVE_IMAGE_JAVA_PLATFORM_MODULE_SYSTEM=false`.
* (GR-38660) Expose -H:Name=<outfile> as API option -o <outfile>
* (GR-39043) Make certain native-image options command-line only and ensure they get processed before other options (--exclude-config --configurations-path --version --help --help-extra --dry-run --debug-attach --expert-options --expert-options-all --expert-options-detail --verbose-server --server-*)

## Version 22.1.0
* (GR-36568) Add "Quick build" mode, enabled through option `-Ob`, for quicker native image builds.
* (GR-35898) Improved handling of static synchronized methods: the lock is no longer stored in the secondary monitor map, but in the mutable DynamicHubCompanion object.
* Remove support for JDK8. As a result, `JDK8OrEarlier` and `JDK11OrLater` have been deprecated and will be removed in a future release.
* (GR-26814) (GR-37018) (GR-37038) (GR-37311) Red Hat added support for the following JFR events: `SafepointBegin`, `SafepointEnd`, `GarbageCollection`, `GCPhasePause`, `GCPhasePauseLevel*`, and `ExecuteVMOperation`. All GC-related JFR events are currently limited to the serial GC.
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
