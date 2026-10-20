# Unified logging in Substrate VM

This document describes the unified logging implementation in Substrate VM
(SVM). SVM follows the HotSpot `-Xlog` model for selections, levels, outputs,
decorators, multiline messages, and file rotation, but
implements the runtime path with precomputed tables and native buffers suitable
for a native image.

## Using unified logging

SVM accepts the familiar HotSpot `-Xlog` syntax, so
[JEP 158](https://openjdk.org/jeps/158) is a useful introduction from the user's
perspective. SVM only instantiates tag sets used by its runtime.
`-Xlog:help` is the authoritative list for a particular native image; a tag that
is not listed cannot be selected.

Unified logging is included when `StrictRuntimeJavaOptions` is enabled.
`VerboseGC` and `PrintGC` stay synchronized with the `gc` threshold on standard output, so either compatibility options or `-Xlog` can reconfigure GC logging at run time.
Without unified logging, GC producers use those legacy options directly and deliver buffered text through `Log.log()`.
That route honors `-XX:LogFile` and embedding callbacks without retaining unified routing or decorator state.

Images with unified logging reject an explicitly registered custom `LogHandler` during image building because native unified outputs would bypass it.
The public legacy handler contract and JNI callbacks remain unchanged.
Unified logging itself never invokes a legacy handler.

The native G1 collector has its own HotSpot logging implementation. SVM forwards
the synchronized `PrintGC` and `VerboseGC` values so that the native collector
follows the `gc` threshold configured on standard output, including later
runtime changes. Native G1 messages still use the collector's standard output
and formatting. An `-Xlog` file destination, decorators, folding, or rotation
applies only to messages emitted through SVM's Java logging
routes and is not forwarded to the native collector.

## Writing log messages

Every unified message is logged against a tag set such as `LogTagSet.class_load`.
Guard the level predicate and any allocating preparation with the folded `HasXlogSupport.get()` check so image analysis can discard this code when unified logging is disabled:

```java
if (HasXlogSupport.get() && LogTagSet.class_load.isInfo()) {
    String loaderDescription = prepareLoaderDescription(loader);
    logClassLoad(className, loaderDescription);
}

@Uninterruptible(reason = LogTagSet.LOG_MESSAGE_OWNER)
private static void logClassLoad(String className, String loaderDescription) {
    LogMessage message = LogTagSet.class_load.message();
    try {
        message.info().string(className).string(" loader=")
                        .string(loaderDescription);
    } finally {
        message.close();
    }
}
```

Append values directly to the native buffer instead of concatenating Java strings.
A dedicated `log*` method owns the complete scope, including synchronous delivery from `LogMessage.close()`.
Annotate that owner with plain `@Uninterruptible(reason = LogTagSet.LOG_MESSAGE_OWNER)`.
Neither `@NeverInline` nor a separate `@RestrictHeapAccess` annotation is necessary.
The uninterruptible compiler contract forbids Java allocation, safepoints, and runtime class initialization transitively.
Scope-sensitive logging APIs require an uninterruptible caller, so ordinary compiler checks detect an interruptible call site without a logging-specific checker.

Keep allocating preparation outside the owner.
Use try/finally to close the scope because try-with-resources generates an interruptible `Throwable.addSuppressed` call.
Configuration and level predicates can run outside a scope.
The explicit build-time support guard remains necessary because inlining is not guaranteed before reachability analysis.

GC producers use `GCLogMessage` to select the route at build time and share text formatting through `NativeMemoryBuffer`.
With unified logging, the same checked scope and output path applies to GC messages.
Without it, the GC adapter buffers text and uses the existing legacy handler contract.
GC producers add the collection prefix, including the previous collection's identity for epilogue work, rather than exposing a prefix hook on the generic GC interface.
Logging remains outside collector regions that move or scan Java objects.
Heap chunk records share one buffer formatter with fatal diagnostics.
Fatal diagnostics borrow bounded stack storage for one row and immediately deliver it to their supplied legacy logger, without allocating native logging storage or acquiring GC event prefixes.
The borrowed carrier binding is cleared before delivery; virtual threads stay pinned until the legacy handler has consumed the stack bytes.

For multi-line events (e.g., logging a stack trace), `LogTagSet.message()`
returns a shared facade for the tag set, while the mutable message bytes, line
metadata, and event decorations live in fast thread-local native state. A
carrier thread may have only one open message scope, including an empty scope;
nested scopes across tag sets are rejected.
Each line can have its own level. On close, the complete message is
routed to every output enabled by its most severe line. Each output's threshold
then filters individual lines while preserving their order.

Stream outputs format a complete event in the current thread's output buffer,
then perform one no-transition raw write in an uninterruptible critical section
that holds their dedicated `VMMutex`. File outputs also format before entering
their prebuilt mutex, then perform the no-transition native write, byte
accounting, rotation, and reopen in the same kind of critical section. A file
output archives an existing active file at startup when rotation is enabled.
With `filecount=0`, startup truncates the active file.
Within an isolate, synchronous unified logging events using the same output cannot
interleave, rotation cannot occur between their lines, and formatting does not hold
an output lock. These locks do not coordinate application writes, legacy handlers,
or other isolates that share the destination.
A blocked raw write can delay safepoint progress, as described below.

`LogDecorations` is a static utility backed by native thread-local state. It
captures the wall-clock timestamp, isolate uptime, and thread id once before an
event is sent to its outputs, but only when at least one active output requests
the corresponding decorator.
The message level is passed separately for each line. The tag set
maintains the union of decorators requested by its active outputs, while each
output formats only its own decorators. The captured epoch, uptime, and thread
id are therefore identical on all outputs. Host name and process id are fixed
at initialization. The local UTC offset is not part of the event record: a
`time` decorator passes the captured event timestamp to the native
`LibCHelper.localUTCOffsetSeconds(millisecondsSince19700101)` helper when
that output is formatted.
The helper converts that timestamp to local time and follows HotSpot's
`local_to_UTC` calculation, using `tm_gmtoff` where available and the platform
timezone value with a daylight-saving adjustment otherwise.
The platform helper uses `localtime_r` on POSIX and `localtime_s` on Windows.
Windows uses `_get_timezone` and the same one-hour daylight-saving fallback as HotSpot.
The result has the ISO-8601 local-to-UTC sign and avoids Java timezone objects and heap allocation on the event path.

The thread-local message state is not recursive. A tag set must not be logged
again from the same carrier thread while any message is open, including through
a different tag set. Opening a message does not acquire a file-output mutex;
file locking starts only after `close` has formatted the complete event.

## Safepoint Safety

Stream and file outputs hold their mutex only in an uninterruptible critical
section and use no-transition native operations. A thread cannot stop at a
safepoint while holding one of these mutexes. A slow file system, full pipe, or
wait for an output mutex can nevertheless delay safepoint progress. A VM
operation that blocks in output cannot complete until that output returns.

Configuration changes remain interruptible and cannot run within a VM operation.
Disabling output first stops new writers and waits for admitted writers to
finish before clearing their routes and closing destinations.

Unified message construction, formatting, and output have a transitive uninterruptible contract.
Production and test destinations must satisfy that contract.
Logging-internal failures use `StdErrWriter`, not `RealLog` or a configurable `LogHandler`.
Legacy GC delivery and fatal-error logging retain their separate, existing contracts.

## Allocation-free runtime behavior

Logging owners and their callees are validated by `UninterruptibleAnnotationChecker`, which also forbids Java heap allocation.
String preparation at the call site can still allocate before entering the owner when the surrounding context permits it.

The runtime path maintains this contract as follows:

1. `NativeMemoryBuffer` formats into carrier-owned native allocations with position, capacity, indentation, and inline byte storage.
   It neither extends `RealLog` nor implements `Log`.
   A static native thread-local slot block lets generic formatter methods select the message, output, or scratch buffer without relying on inlining.
   `reset` retains storage for reuse; `release` frees it.
   Growth uses native `malloc` or `realloc`, not Java allocation.
   Lifecycle callbacks release buffers after Java execution, including launcher handoff, and again during thread exit if necessary.
2. `LogDecorations` formats event metadata and the explicitly supplied line
   level directly into native memory. Host name and process id are cached
   during initialization, while `localUTCOffsetSeconds` computes the local
   UTC offset for the event timestamp with native time APIs and stack storage.
3. `LogOutput` formats into the thread-local output and decorator buffers.
   Decorator padding is retained per output and updated atomically, so repeated
   events do not create formatted intermediate strings or byte arrays. Stream
   and file output locks cover only native operations after formatting.
4. `LogMessage` stores line levels, byte offsets, and message bytes in the
   thread-local native state. Its local index walk filters levels and either
   preserves embedded newlines with continuation prefixes or folds them
   according to the output setting. Native line metadata starts with capacity
   for ten lines and grows outside the Java heap when necessary.
5. `LogFileOutput` retains the expanded active path and archive paths as native
   `RawFilePath` values. Runtime file operations use
   `RawFileOperationSupport` and do not construct `Path`, filename strings, or
   temporary Java objects.

The allocation restriction applies to every event-processing path, including
decoration, raw writes, file rotation, and failure handling. Core logging must
not allocate Java heap objects or throw ordinary exceptions. Configuration and
help output run outside message scopes and may allocate.

## Configuration and routing

When unified logging is supported, `LogConfiguration.initialize` installs the default `all=warning` configuration on `stdout` before runtime option parsing.
It registers teardown before opening resources and caches the host name, process ID, and local startup timestamp for filename expansion.
Using unified logging before this baseline is installed is a fatal invariant violation, distinct from intentionally disabling its outputs.
An isolate initialization hook completes logging startup and reports image classes after parsing.

Ordinary parser calls preserve the current configuration.
`VerboseGC`, `PrintGC`, and `MemoryMXBean.setVerbose` update the unified GC threshold when supported.
Changes to the GC threshold on standard output are mirrored back to the legacy options.
Without unified logging, GC producers read the legacy options directly, including image defaults available before initialization hooks.
Only the independent native-buffer formatter and its cleanup remain necessary for that route.

The `time` decorator obtains the UTC offset for the captured timestamp through native `LibCHelper.localUTCOffsetSeconds` when the timestamp is formatted.

Mutating configuration methods synchronize on `LogConfiguration.class`. Each
tag set's `LogOutputList` also synchronizes updates and publishes a replacement
`outputsByLevel` table through a volatile field. The arrays in a published table
are immutable, so a logging thread reads one stable routing snapshot without
taking the configuration monitor.
Delivery selects this snapshot when the message scope closes, not when it opens.
The snapshot remains unchanged throughout delivery, even if another thread reconfigures logging.

For example, after the default configuration and

```text
-Xlog:all=info:file=app.log
```

the relevant arrays for each selected tag set are:

```text
outputsByLevel[TRACE]   = []
outputsByLevel[DEBUG]   = []
outputsByLevel[INFO]    = [file=app.log]
outputsByLevel[WARNING] = [stdout, file=app.log]
outputsByLevel[ERROR]   = [stdout, file=app.log]
mostDetailedLevel       = INFO
```

`mostDetailedLevel` provides the fast enablement check. A single-line event
uses its own level as the array index. A multi-line event uses the most severe
line to find every output that can receive at least one line, then recovers
each selected output's threshold from the same arrays to filter its lines.

Output options are properties of the output, not of an individual selection.
They are parsed only when an output is first configured. Options supplied when
the same output is selected again are ignored with a warning. File output
parsing also recognizes Windows drive-letter colons in native and slash-style
paths, with or without the `file=` prefix.

## File output and failure handling

`LogFileOutput` expands `%p`, `%i`, `%t`, and `%hn`, retains the resulting path
as specified, prepares the native active and archive paths, and opens the active
file when the output is created. Relative paths therefore remain relative. The
`%i` placeholder is an SVM extension that
expands to the current isolate ID, allowing isolates in the same process to use
distinct output files; `%p` alone identifies only the process. File output does
not create missing parent directories. If opening fails, it emits an emergency
diagnostic containing the path and native error code, leaves the output
configured, and does not abort the VM. Each later event whose descriptor is
still unavailable retries opening the active file before safely skipping its
write when the retry fails.

All file operations use the platform-specific `RawFileOperationSupport`
implementation, including existence and regular file checks, modification time
queries, archive deletion, and renaming.
VM options are parsed before the Java I/O native library is initialized, so
configuration does not use Java filesystem operations.
`LibC.hostname()` provides native host name lookup without depending on Java
networking initialization.
If a write operation fails, `LogOutput` emits one emergency
`Could not write to log` diagnostic for that output and suppresses repeated
copies of the same diagnostic.
The event path remains non-fatal.

Rotation is performed after `LogOutput` completes a write batch and the byte
threshold has been reached. The batch is the whole possibly multi-line event.
The active descriptor is closed, the next circular archive slot is replaced, and the active file is reopened.
At startup, the first unused archive slot is selected, or the oldest slot if all slots exist.
Archive numbers use the width of the largest configured slot, matching HotSpot.
Active-file rename failures produce an emergency
diagnostic and preserve the active file for subsequent appending.
Startup archive failures reject the configuration instead of truncating the existing file. If reopening fails,
the same non-fatal open-file handling applies.

On POSIX systems, an open log file can be unlinked while its descriptor remains
open. Subsequent writes continue to use that descriptor and do not recreate the
directory entry. This behavior is covered by the native logging tests. Windows
uses platform file-sharing rules, so tests requiring unlinking an open file are
restricted to POSIX platforms.

### JFR integration

When `-Xlog` is supported, ordinary JFR records use unified logging as their only sink.
`-XX:FlightRecorderLogging` acts as a compatibility option for JFR tag sets on standard output.
It is applied in command-line order with `-Xlog`, so later selections determine the final configuration.
The JDK `LogTag.tagSetLevel` thresholds are published during baseline logging initialization and refreshed after configuration changes, including startup options and output disabling.

The `com.oracle.svm.core.logging.jfr.JfrUnifiedLogging` bridge maps JDK JFR tag sets to `LogTagSet` instances.
A regular record becomes one unified message, and a multiline JFR event becomes one atomic `LogMessage`.
JFR uses the same routing, decorators, and output synchronization as other unified logging records.

Without `-Xlog` support, JFR retains its existing `FlightRecorderLogging` configuration and low-level log destination.
Its destination is normally standard error and can be changed with `-XX:LogFile` or an embedding log callback.
There is no independent second sink or separate threshold array.

The emergency JFR informational, warning, and system error helpers always use the low-level log destination even when `-Xlog` is supported.
They respect the published JFR thresholds but bypass unified output routing and decorators to avoid allocating native logging buffers during a native memory shortage.

## Testing

From the `substratevm-enterprise` suite, you can run compatible logging cases in one native image:

```shell
mx --java-home=lookup:default svmtest UnifiedLoggingTestSuite
```

The suite enables JFR and native memory tracking so the allocation and launcher-handoff cleanup checks exercise real native buffers.
It runs cases sequentially because they reconfigure shared logging state.
Platform-specific cases use explicit platform metadata.
Tests with custom startup options or expected image-building failures remain standalone:

```shell
mx --java-home=lookup:default svmtest UnifiedLoggingTest_testOrdersJfrOptions \
    UnifiedLoggingTest_testRejectsLateJfrOptionWithoutChangingRoutes \
    LegacyGCLoggingInitializationTest UnifiedLoggingBuildTests
```

## Comparison with HotSpot unified logging

This comparison uses HotSpot from OpenJDK JDK 25 build 36
([`jdk-25+36`](https://github.com/openjdk/jdk/tree/jdk-25%2B36))
as its baseline.
The relevant HotSpot implementation uses linked output lists, reader tracking,
file stream locks, and a rotation semaphore; see
[`logTagSet.cpp`](https://github.com/openjdk/jdk/blob/jdk-25%2B36/src/hotspot/share/logging/logTagSet.cpp),
[`logOutputList.hpp`](https://github.com/openjdk/jdk/blob/jdk-25%2B36/src/hotspot/share/logging/logOutputList.hpp),
[`logFileOutput.cpp`](https://github.com/openjdk/jdk/blob/jdk-25%2B36/src/hotspot/share/logging/logFileOutput.cpp).
SVM uses the same broad configuration model but a smaller runtime design.

| Area | HotSpot | SVM |
| --- | --- | --- |
| Available tag sets | Every tag set instantiated by HotSpot logging sites. | Every tag set used by the SVM runtime. |
| Output routing | Per-level linked-list heads with atomic reader tracking. | Per-tag-set, per-level immutable output arrays published through volatile fields. Legacy-only GC logging does not use these tables. |
| Configuration | `ConfigurationLock` and reader counts protect updates and delayed reclamation; `jcmd VM.log` supports runtime changes. | Synchronized configuration methods publish replacement arrays. Configuration is startup-oriented except for GC verbosity changes through `MemoryMXBean` and `RuntimeOptions.set` with `PrintGC` or `VerboseGC`. |
| Synchronous output locking | `FileLocker` protects writes; a rotation semaphore covers file rotation. | Stream outputs use an uninterruptible critical section to serialize no-transition native writes with a dedicated `VMMutex`; file outputs use the same pattern with a prebuilt `VMMutex` across the no-transition write, accounting, rotation, and reopen. VM operations use the same serialized paths. |
| Decoration state | Event decorations are resolved for each message. | Event metadata lives in fast thread-local state; line levels remain explicit per line. |
| File rotation | Native C++ file streams and rotation locks. An existing active file is archived at startup when rotation is enabled. | Precomputed native paths, native byte counters, and raw close/delete/rename/reopen operations. An existing active file is archived at startup when rotation is enabled; `filecount=0` truncates it. The `%i` filename placeholder separates isolate-local rotation state into distinct operating-system paths. |
| JFR integration | JFR writes directly through HotSpot unified logging. | `FlightRecorderLogging` configures unified JFR stdout routes when supported; otherwise JFR retains its legacy output. |
| Allocation contract | Native C++ allocation rules apply. | Unified event processing is transitively uninterruptible and Java-heap allocation-free. Native buffers may grow; emergency JFR helpers bypass this path. |

SVM's design is optimized for a native image whose tag sets and output routing
are known from startup configuration. It avoids dynamic reader reclamation in
the event path and writes complete events directly to their destinations.
