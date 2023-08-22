# Native Image Changelog

This changelog summarizes major changes to GraalVM Native Image.

## GraalVM for JDK 21 (Internal Version 23.1.0)
* (GR-35746) Lower the default aligned chunk size from 1 MB to 512 KB for the serial and epsilon GCs, reducing memory usage and image size in many cases.
* (GR-45841) BellSoft added support for the JFR event ThreadCPULoad.
* (GR-45994) Removed the option `-H:EnableSignalAPI`. Please use the runtime option `EnableSignalHandling` if it is necessary to enable or disable signal handling explicitly.
* (GR-39406) Simulation of class initializer: Class initializer of classes that are not marked for initialization at image build time are simulated at image build time to avoid executing them at image run time.
* (GR-39406) All classes can now be used at image build time, even when they are not explicitly configured as `--initialize-at-build-time`. Note, however, that still only classes configured as `--initialize-at-build-time` are allowed in the image heap.
* (GR-46392) Add `--parallelism` option to control how many threads are used by the build process.
* (GR-46392) Add build resources section to the build output that shows the memory and thread limits of the build process.
* (GR-38994) Together with Red Hat, we added support for `-XX:+HeapDumpOnOutOfMemoryError`.
* (GR-47365) Throw `MissingReflectionRegistrationError` when attempting to create a proxy class without having it registered at build-time, instead of a `VMError`.
* (GR-46064) Add option `-H:±IndirectBranchTargetMarker` to mark indirect branch targets on AMD64 with an endbranch instruction. This is a prerequisite for future Intel CET support.
* (GR-46740) Add support for foreign downcalls (part of "Project Panama") on the AMD64 platform.
* (GR-27034) Add `-H:ImageBuildID` option to generate Image Build ID, which is a 128-bit UUID string generated randomly, once per bundle or digest of input args when bundles are not used.
* (GR-47647) Add `-H:±UnlockExperimentalVMOptions` for unlocking access to experimental options similar to HotSpot's `-XX:UnlockExperimentalVMOptions`. Explicit unlocking will be required in a future release, which can be tested with the env setting `NATIVE_IMAGE_EXPERIMENTAL_OPTIONS_ARE_FATAL=true`. For more details, see [issue #7105](https://github.com/oracle/graal/issues/7105).
* (GR-47647) Add `--color[=WHEN]` option to color the output WHEN ('always', 'never', or 'auto'). This API option supersedes the experimental option `-H:+BuildOutputColorful`.
* (GR-43920) Add support for executing native image bundles as jar files with extra options `--with-native-image-agent` and `--container`.
* (GR-43920) Add `,container[=<container-tool>]`, `,dockerfile=<dockerfile>` and `,dry-run` options to `--bundle-create`and `--bundle-apply`.

## GraalVM for JDK 17 and GraalVM for JDK 20 (Internal Version 23.0.0)
* (GR-40187) Report invalid use of SVM specific classes on image class- or module-path as error. As a temporary workaround, `-H:+AllowDeprecatedBuilderClassesOnImageClasspath` allows turning the error into a warning.
* (GR-41196) Provide `.debug.svm.imagebuild.*` sections that contain build options and properties used in the build of the image.
* (GR-41978) Disallow `--initialize-at-build-time` without arguments. As a temporary workaround, `-H:+AllowDeprecatedInitializeAllClassesAtBuildTime` allows turning this error into a warning.
* (GR-41674) Class instanceOf and isAssignableFrom checks do need to make the checked type reachable.
* (GR-41100) Add support for `-XX:HeapDumpPath` to control where heap dumps are created.
* (GR-42148) Adjust build output to report types (primitives, classes, interfaces, and arrays) instead of classes and revise the output schema of `-H:BuildOutputJSONFile`.
* (GR-42375) Add `-H:±GenerateBuildArtifactsFile` option, which generates a `build-artifacts.json` file with a list of all artifacts produced by Native Image. `.build_artifacts.txt` files are now deprecated, disabled (can be re-enabled with env setting `NATIVE_IMAGE_DEPRECATED_BUILD_ARTIFACTS_TXT=true`), and will be removed in a future release.
* (GR-34179) Red Hat improved debugging support on Windows: Debug information now includes information about Java types.
* (GR-41096) Support services loaded through the `java.util.ServiceLoader.ModuleServicesLookupIterator`. An example of such service is the `com.sun.jndi.rmi.registry.RegistryContextFactory`.
* (GR-41912) The builder now generated reports for internal errors, which users can share when creating issues. By default, error reports follow the `svm_err_b_<timestamp>_pid<pid>.md` pattern and are created in the working directory. Use `-H:ErrorFile` to adjust the path or filename.
* (GR-36951) Add [RISC-V support](https://medium.com/p/899be38eddd9) for Native Image through the LLVM backend.
* (GR-42964) Deprecate `--enable-monitoring` without an argument. The option will no longer default to `all` in a future release. Instead, please always explicitly specify the list of monitoring features to be enabled (for example, `--enable-monitoring=heapdump,jfr,jvmstat`").
* (GR-19890) Native Image now sets up build environments for Windows users automatically. Running in an x64 Native Tools Command Prompt is no longer a requirement.
* (GR-43410) Added support for the JFR event `ExecutionSample`.
* (GR-44058) (GR-44087) Red Hat added support for the JFR events `ObjectAllocationInNewTLAB` and `JavaMonitorInflate`.
* (GR-42467) The search path for `System.loadLibrary()` by default includes the directory containing the native image.
* (GR-44216) Native Image is now shipped as part of the GraalVM JDK and thus no longer needs to be installed via `gu install native-image`.
* (GR-44105) A warning is displayed when trying to generate debug info on macOS since that is not supported. It is now an error to use `-H:+StripDebugInfo` on macOS or `-H:-StripDebugInfo` on Windows since those values are not supported.
* (GR-43966) Remove analysis options -H:AnalysisStatisticsFile and -H:ImageBuildStatisticsFile. Output files are now written to fixed subdirectories relative to image location (reports/image_build_statistics.json). 
* (GR-38414) BellSoft implemented the `MemoryPoolMXBean` for the serial and epsilon GCs.
* (GR-40641) Dynamic linking of AWT libraries on Linux.
* (GR-40463) Red Hat added experimental support for JMX, which can be enabled with the `--enable-monitoring` option (e.g. `--enable-monitoring=jmxclient,jmxserver`).
* (GR-42740) Together with Red Hat, we added experimental support for JFR event streaming.
* (GR-44110) Native Image now targets `x86-64-v3` by default on AMD64 and supports a new `-march` option. Use `-march=compatibility` for best compatibility (previous default) or `-march=native` for best performance if the native executable is deployed on the same machine or on a machine with the same CPU features. To list all available machine types, use `-march=list`.
* (GR-43971) Add native-image option `-E<env-var-key>[=<env-var-value>]` and support environment variable capturing in bundles. Previously almost all environment variables were available in the builder. To temporarily revert back to the old behaviour, env setting `NATIVE_IMAGE_DEPRECATED_BUILDER_SANITATION=true` can be used. The old behaviour will be removed in a future release.
* (GR-43382) The build output now includes a section with recommendations that help you get the best out of Native Image.
* (GR-44722) The output of `native-image --version` and various Java properties (e.g. `java.vm.version`) have been aligned with the OpenJDK. To distinguish between GraalVM CE, Oracle GraalVM, and GraalVM distributions from other vendors, please refer to `java.vm.vendor` or `java.vendor.version`.
* (GR-45673) Improve the memory footprint of the Native Image build process. The builder now takes available memory into account to reduce memory pressure when many other processes are running on the same machine. It also consumes less memory in many cases and is therefore also less likely to fail due to out-of-memory errors. At the same time, we have raised its memory limit from 14GB to 32GB.

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
