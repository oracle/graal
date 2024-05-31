# Native Image Changelog

This changelog summarizes major changes to GraalVM Native Image.

## GraalVM for JDK 23 (Internal Version 24.1.0)
* (GR-51520) The old class initialization strategy, which was deprecated in GraalVM for JDK 22, is removed. The option `StrictImageHeap` no longer has any effect.
* (GR-51106) Fields that are accessed via a `VarHandle` or `MethodHandle` are no longer marked as "unsafe accessed" when the `VarHandle`/`MethodHandle` can be fully intrinsified.
* (GR-49996) Ensure explicitly set image name (e.g., via `-o imagename`) is not accidentally overwritten by `-jar jarfile` option.
* (GR-48683) Together with Red Hat, we added partial support for the JFR event `OldObjectSample`.
* (GR-51851) Together with Red Hat, we added initial support for native memory tracking (`--enable-monitoring=nmt`).
* (GR-47109) Together with Red Hat, we added support for JFR event throttling and the event `ObjectAllocationSample`.
* (GR-52030) Add a stable name for `Proxy` types in Native Image. The name `$Proxy[id]` is replaced by `$Proxy.s[hashCode]` where `hashCode` is computed using the names of the `Proxy` interfaces, the name of the class loader and the name of the module if it is not a dynamic module.
* (GR-47712) Using the `--static` option without the `--libc=musl` option causes the build process to fail (and reports the appropriate error). Static linking is currently only supported with musl.
* (GR-50434) Introduce a `"type"` field in reflection and JNI configuration files to support more than simple named types.
* (GR-51053) Use [`vswhere`](https://github.com/microsoft/vswhere) to find Visual Studio installations more reliably and in non-standard installation locations.
* (GR-47832) Experimental support for upcalls from foreign code and other improvements to our implementation of the [Foreign Function & Memory API](https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/ForeignInterface.md) (part of "Project Panama", [JEP 454](https://openjdk.org/jeps/454)) on AMD64. Must be enabled with `-H:+ForeignAPISupport` (requiring `-H:+UnlockExperimentalVMOptions`).
* (GR-52314) `-XX:MissingRegistrationReportingMode` can now be used on program invocation instead of as a build option, to avoid a rebuild when debugging missing registration errors.
* (GR-51086) Introduce a new `--static-nolibc` API option as a replacement for the experimental `-H:±StaticExecutableWithDynamicLibC` option.
* (GR-52732) Introduce a new `ReduceImplicitExceptionStackTraceInformation` hosted option that reduces image size by reducing the runtime metadata for implicit exceptions, at the cost of stack trace precision. The option is diabled by default, but enabled with optimization level 3 and profile guided optimizations.
* (GR-52534) Change the digest (used e.g. for symbol names) from SHA-1 encoded as a hex string (40 bytes) to 128-bit Murmur3 as a Base-62 string (22 bytes).
* (GR-52578) Print information about embedded resources into `embedded-resources.json` using the `-H:+GenerateEmbeddedResourcesFile` option.
* (GR-51172) Add support to catch OutOfMemoryError exceptions on native image if there is no memory left.
* (GR-53803) In the strict reflection configuration mode (when `ThrowMissingRegistrationErrors` is enabled), only allow `Unsafe.allocateInstance` for types registered explicitly in the configuration.
* (GR-43837) `--report-unsupported-elements-at-runtime` is now enabled by default and the option is deprecated.
* (GR-53359) Provide the `.debug_gdb_scripts` section that triggers auto-loading of `svmhelpers.py` in GDB. Remove single and double quotes from `ClassLoader.nameAndId` in the debuginfo.
* (GR-47365) Include dynamic proxy metadata in the reflection metadata with the syntax `"type": { "proxy": [<interface list>] }`. This allows members of proxy classes to be accessed reflectively. `proxy-config.json` is now deprecated but will still be honored.
* (GR-18214) In-place compacting garbage collection for the Serial GC old generation with `-H:+CompactingOldGen`.
* (GR-52844) Add `-Os`, a new optimization mode to configure the optimizer in a way to get the smallest code size.
* (GR-49770) Add support for glob patterns in resource-config files in addition to regexp. The Tracing agent now prints entries in the glob format.

## GraalVM for JDK 22 (Internal Version 24.0.0)
* (GR-48304) Red Hat added support for the JFR event ThreadAllocationStatistics.
* (GR-48343) Red Hat added support for the JFR events AllocationRequiringGC and SystemGC.
* (GR-48612) Enable `--strict-image-heap` by default. The option is now deprecated and can be removed from your argument list. A blog post with more information will follow shortly.
* (GR-48354) Remove native-image-agent legacy `build`-option
* (GR-49221) Support for thread dumps can now be enabled with `--enable-monitoring=threaddump`. The option `-H:±DumpThreadStacksOnSignal` is deprecated and marked for removal.
* (GR-48579) Options ParseOnce, ParseOnceJIT, and InlineBeforeAnalysis are deprecated and no longer have any effect.
* (GR-39407) Add support for the `NATIVE_IMAGE_OPTIONS` environment variable, which allows users and tools to pass additional arguments via the environment. Similar to `JAVA_TOOL_OPTIONS`, the value of the environment variable is prepended to the options supplied to `native-image`.
* (GR-20827): Introduce a dedicated caller-saved branch target register for software CFI implementations.
* (GR-47937) Make the lambda-class name format in Native-Image consistent with the JDK name format.
* (GR-45651) Methods, fields and constructors of `Object`, primitive classes and array classes are now registered by default for reflection.
* (GR-45651) The Native Image agent now tracks calls to `ClassLoader.findSystemClass`, `ObjectInputStream.resolveClass` and `Bundles.of`, and registers resource bundles as bundle name-locale pairs.
* (GR-49807) Before this change the function `System#setSecurityManager` was always halting program execution with a VM error. This was inconvenient as the VM error prints an uncomprehensible error message and prevents further continuation of the program. For cases where the program is expected to throw an exception when  `System#setSecurityManager` is called, execution on Native Image was not possible. Now, `System#setSecurityManager` throws an `java.lang.UnsupportedOperationException` by default. If the property `java.security.manager` is set to anything but `disallow` at program startup this function will throw a `java.lang.SecurityException` according to the Java spec.
* (GR-30433) Disallow the deprecated environment variable USE_NATIVE_IMAGE_JAVA_PLATFORM_MODULE_SYSTEM=false.
* (GR-49655) Experimental support for parts of the [Foreign Function & Memory API](https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/ForeignInterface.md) (part of "Project Panama", [JEP 454](https://openjdk.org/jeps/454)) on AMD64. Must be enabled with `-H:+ForeignAPISupport` (requiring `-H:+UnlockExperimentalVMOptions`).
* (GR-46407) Correctly rethrow build-time linkage errors at run-time for registered reflection queries.
* (GR-51002) Improve intrinsification of method handles. This especially improves the performance of `equals` and `hashCode` methods for records, which use method handles that are now intrinsified.
* (GR-50529) Native Image now throws a specific error when trying to access unregistered resource bundles instead of failing on a subsequent reflection or resource query.

## GraalVM for JDK 21 (Internal Version 23.1.0)
* (GR-35746) Lower the default aligned chunk size from 1 MB to 512 KB for the serial and epsilon GCs, reducing memory usage and image size in many cases.
* (GR-45841) BellSoft added support for the JFR event ThreadCPULoad.
* (GR-45994) Removed the option `-H:EnableSignalAPI`. Please use the runtime option `EnableSignalHandling` if it is necessary to enable or disable signal handling explicitly.
* (GR-39406) Simulation of class initializer: Class initializer of classes that are not marked for initialization at image build time are simulated at image build time to avoid executing them at image run time.
* (GR-39406) New option `--strict-image-heap`: All classes can now be used at image build time, even when they are not explicitly configured as `--initialize-at-build-time`. Note, however, that still only classes configured as `--initialize-at-build-time` are allowed in the image heap. Adopt this option as it will become the default in the next release of GraalVM.
* (GR-46392) Add `--parallelism` option to control how many threads are used by the build process.
* (GR-46392) Add build resources section to the build output that shows the memory and thread limits of the build process.
* (GR-38994) Together with Red Hat, we added support for `-XX:+HeapDumpOnOutOfMemoryError`.
* (GR-47365) Throw `MissingReflectionRegistrationError` when attempting to create a proxy class without having it registered at build-time, instead of a `VMError`.
* (GR-46064) Add option `-H:±IndirectBranchTargetMarker` to mark indirect branch targets on AMD64 with an endbranch instruction. This is a prerequisite for future Intel CET support.
* (GR-46740) Preview of [Foreign Function & Memory API downcalls](https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/ForeignInterface.md) (part of "Project Panama", [JEP 442](https://openjdk.org/jeps/442)) on AMD64. Must be enabled with `--enable-preview`.
* (GR-27034) Add `-H:ImageBuildID` option to generate Image Build ID, which is a 128-bit UUID string generated randomly, once per bundle or digest of input args when bundles are not used.
* (GR-47647) Add `-H:±UnlockExperimentalVMOptions` for unlocking access to experimental options similar to HotSpot's `-XX:UnlockExperimentalVMOptions`. Explicit unlocking will be required in a future release, which can be tested with the env setting `NATIVE_IMAGE_EXPERIMENTAL_OPTIONS_ARE_FATAL=true`. For more details, see [issue #7105](https://github.com/oracle/graal/issues/7105).
* (GR-47647) Add `--color[=WHEN]` option to color the output WHEN ('always', 'never', or 'auto'). This API option supersedes the experimental option `-H:+BuildOutputColorful`.
* (GR-43920) Add support for executing native image bundles as jar files with extra options `--with-native-image-agent` and `--container`.
* (GR-43920) Add `,container[=<container-tool>]`, `,dockerfile=<dockerfile>` and `,dry-run` options to `--bundle-create`and `--bundle-apply`.
* (GR-46420) Switch to directly using cgroup support from the JDK.
* (GR-29688) More sophisticated intrinsification and inlining of method handle usages, both explicit and implicit (lambdas, string concatenation and record classes), and various fixes for non-intrinsified usages.

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
