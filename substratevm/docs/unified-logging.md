# Unified logging in Substrate VM

This document describes the unified logging implementation in Substrate VM
(SVM). SVM follows the HotSpot `-Xlog` model for selections, levels, outputs,
decorators, multiline messages, and file rotation, but
implements the runtime path with precomputed tables and native buffers suitable
for a native image.

## Using unified logging

SVM accepts the familiar HotSpot `-Xlog` syntax, so [JEP 158](https://openjdk.org/jeps/158)
is a useful introduction from the user's perspective. SVM only instantiates tag
sets used by its runtime. `-Xlog:help` is the authoritative list for a
particular native image; a tag that is not listed cannot be selected.

The `-Xlog` command line interface is included when
`StrictRuntimeJavaOptions` is enabled. The logging API remains available when
it is disabled because legacy GC options use the same message and routing
infrastructure. `VerboseGC` and `PrintGC` remain synchronized with the `gc` tag
set on standard output, so either compatibility options or `-Xlog` can
reconfigure GC logging at run time. In an image without `-Xlog` support, those
legacy options enable DEBUG or INFO output for the `gc` tag set on the low-level
VM log.

The native G1 collector has its own HotSpot logging implementation. SVM forwards
the synchronized `PrintGC` and `VerboseGC` values so that the native collector
follows the `gc` threshold configured on standard output, including later
runtime changes. Native G1 messages still use the collector's standard output
and formatting. An `-Xlog` file destination, decorators, folding, or rotation
applies only to messages emitted through SVM's Java logging
routes and is not forwarded to the native collector.

## Writing log messages

Every message is logged against a specific tag set value (e.g. `LogTagSet.class_load`).
It starts with a level predicate so minimal work is done when the tag set is not configured
to log at the level specified in the predicate. Except for `LogTagSet.gc`, the folded
`HasXlogSupport.get()` check should precede the predicate to keep unified logging
infrastructure out of images that do not support parsing `-Xlog`:

```java
if (HasXlogSupport.get() && LogTagSet.class_load.isInfo()) {
    String loaderDescription = prepareLoaderDescription(loader);
    logClassLoad(className, loaderDescription);
}

@NeverInline(LogTagSet.LOG_METHOD_REASON)
@Uninterruptible(reason = LogTagSet.LOG_METHOD_REASON)
private static void logClassLoad(String className, String loaderDescription) {
    LogMessage message = LogTagSet.class_load.message();
    try {
        message.info().string(className).string(" loader=").string(loaderDescription);
    } finally {
        message.close();
    }
}
```

Ideally, applying `@AlwaysInline` to the level predicates would make the explicit
`HasXlogSupport.get()` check at each logging site unnecessary. However, inlining is not guaranteed
to happen before analysis, so the annotation cannot be relied upon to fold the guarded message
construction away. The support check therefore remains explicit at each non-GC logging site.

Values are appended directly to the message's native buffer instead of being combined with Java
string concatenation.
The dedicated `log*` method owns formatting and synchronous delivery through `LogMessage.close()`.
Except for the two Serial GC methods described below, every call to `LogTagSet.message()`, including calls in tests, must appear directly in a `void` method whose name starts with `log`.
That method must declare both annotations shown above, using `LogTagSet.LOG_METHOD_REASON` and the default `@Uninterruptible` options.
Native Image checks this caller contract during image building, before inlining can hide a call.
Its annotations prevent inlining, heap allocation, safepoints, and class initialization on the checked path.
`@Uninterruptible` implies `RestrictHeapAccess.NO_ALLOCATION`; `UninterruptibleAnnotationChecker` enforces this without a separate allocation annotation.
Keep level predicates and any allocating preparation outside that method.
Use try-finally because try-with-resources generates an interruptible `Throwable.addSuppressed` call that violates the annotation contract.
Serial GC's `GCImpl.printGCBefore(GCCause)` and `GCImpl.printGCAfter(GCCause)` are the only exceptions to this caller contract.
They execute in the allocation-free collection VM operation, whose thread cannot safepoint.
These methods open and close messages directly, passing `LogMessage` into formatting helpers.
Their existing GC context provides the allocation and safepoint guarantees without requiring annotations throughout the formatting code.

The `gc` tag set omits `HasXlogSupport.get()` because `PrintGC` and
`VerboseGC` can route it to the low-level VM log in images without `-Xlog` support.

The level predicates and message APIs use the same output table for configured
`-Xlog` routes and the legacy GC fallback. The fallback is filtered in the same
way as any other output, but uses only the uptime decorator and the GC prefix.
It writes through `Log.log()`, so it honors `-XX:LogFile` and embedding log
callbacks rather than creating a separate unified-log destination.

For multi-line events (e.g., logging a stack trace), `LogTagSet.message()` returns
a shared facade for the tag set, while the mutable message bytes, line metadata, and event decorations live
in fast thread-local native state. A carrier thread may have only one open
message scope, including an empty scope; nested scopes across tag sets are
rejected. Each line can have its own level. On close, the complete message is
routed to every output enabled by its most severe line. Each output's threshold
then filters individual lines while preserving their order.

Stream outputs format a complete event in the current thread's output buffer,
then perform one no-transition raw write in an uninterruptible critical section
that holds their dedicated `VMMutex`. File outputs also format before entering
their prebuilt mutex, then perform the no-transition native write, byte
accounting, rotation, and reopen in the same kind of critical section. A file
output discards any preexisting active file contents at startup regardless of
its rotation configuration; only output from the current process is rotated.
Consequently, synchronous events cannot be interleaved on a destination, file
rotation cannot occur between their lines, and formatting does not hold an
output lock.
The low-level VM log fallback uses the synchronization provided by `Log.log()` instead of the
stream-output mutex. The safepoint consequences of a blocked raw write are
described below.

`LogDecorations` is a static utility backed by native thread-local state. It captures the wall-clock
timestamp, isolate uptime, and thread id once before an event is sent to its
outputs, but only when at least one active output requests the corresponding
decorator. The message level is passed separately for each line. The tag set
maintains the union of decorators requested by its active outputs, while each
output formats only its own decorators. The captured epoch, uptime, and thread
id are therefore identical on all outputs. Host name and process id are fixed
at initialization. The local UTC offset is not part of the event record: a
`time` decorator passes the captured event timestamp to the native
`LibCHelper.SVM_localUTCOffsetSeconds(millisecondsSince19700101)` helper when
that output is formatted.
The helper converts that timestamp to local time and follows HotSpot's
`local_to_UTC` calculation, using `tm_gmtoff` where available and the platform
timezone value with a daylight-saving adjustment otherwise. On Windows it uses
`localtime_s` to obtain the local wall-clock fields for that instant and
`_mkgmtime64` to reinterpret those fields as UTC, with their difference giving
the local offset. The result has the ISO-8601 local-to-UTC sign and avoids Java
timezone objects and heap allocation on the event path. Each output uses the
DST offset associated with the captured event timestamp.

The thread-local message state is not recursive. A tag set must not be logged
again from the same carrier thread while any message is open, including through
a different tag set. Opening a message does not acquire a file-output mutex;
file locking starts only after `close` has formatted the complete event.

## Safepoint Safety

Stream and file outputs hold their mutex only in an uninterruptible critical
section and use no-transition native operations. A thread cannot stop at a
safepoint while holding one of these mutexes. A slow file system, full pipe, or
wait for an output mutex can nevertheless delay safepoint progress. A VM operation
that blocks in output cannot complete until that output returns.

Configuration changes remain interruptible and cannot run within a VM operation.
Disabling output first stops new writers and waits for admitted writers to finish
before clearing their routes and closing destinations.

The low-level VM-log destination and output-error diagnostics use `RealLog`.
Its `AbstractLog.writeBytes0` bridge deliberately permits interruptible callees.
The installed `LogHandler` must not allocate, but its safepoint behavior is trusted rather than enforced.
The annotation checks therefore do not provide a transitive safepoint guarantee for this legacy boundary.
Production stream and file destinations use checked no-transition native operations; managed test destinations are outside that guarantee.

## Allocation-free runtime behavior

Outside the two Serial GC exceptions, successful normal logging operations are checked by `UninterruptibleAnnotationChecker`, which also forbids heap allocation.
The contract starts after the caller has provided the message text; string concatenation or formatting
at the call site can still allocate before logging is entered if the broader context
allows allocation.

The runtime path maintains this contract as follows:

1. `NativeMemoryLog` stores thread-local event bytes in raw native structures containing a position,
   capacity, and inline byte storage. Message, output, and decorator buffers are
   shared by the current carrier thread through `FastThreadLocalFactory`. `reset`
   rewinds a reusable buffer, whereas `clear` frees it during teardown. These buffers
   may grow with native `malloc` or `realloc`, but do not allocate Java heap
   objects during logging. A thread listener releases all thread-local buffers
   after Java thread execution, including launcher handoff, and again during thread exit if needed.
2. `LogDecorations` formats event metadata and the explicitly supplied line
   level directly into native memory. Host name and process id are cached
   during initialization, while `SVM_localUTCOffsetSeconds` computes the local
   UTC offset for the event timestamp with native time APIs and stack storage.
3. `LogOutput` formats into the thread-local output and decorator buffers.
   Decorator padding is retained per output and updated atomically, so repeated
   events do not create formatted intermediate strings or byte arrays. Stream
   and file output locks cover only native operations after formatting.
4. `LogMessage` stores line levels, byte offsets, and message bytes in the
   thread-local native state. Its local index walk filters levels and either preserves
   embedded newlines with continuation prefixes or folds them according to the
   output setting. Native line metadata starts with capacity for ten lines and
   grows outside the Java heap when necessary.
5. `LogFileOutput` retains the expanded active path and archive paths as native
   `RawFilePath` values. Runtime file operations use
   `RawFileOperationSupport` and do not construct `Path`, filename strings, or
   temporary Java objects.

The allocation restriction applies to successful event processing, including
decoration, raw writes, and file
rotation. Configuration, help output, diagnostics, and exceptional Java paths
may allocate.

## Configuration and routing

When `-Xlog` is supported, `LogConfiguration.initialize` installs the default
`all=warning` configuration on `stdout` and caches the host name, process id,
and local startup timestamp used for filename expansion.
Without `-Xlog`, no default output table is installed. `VerboseGC`, `PrintGC`,
and `MemoryMXBean.setVerbose` update the GC threshold on standard output when
`-Xlog` is available and on the fallback output otherwise. Changes to the GC
threshold on standard output are mirrored back to `VerboseGC` and `PrintGC`.
Shared message buffers and their thread-exit cleanup remain present for fallback
GC logging.
The `time` decorator
obtains the local UTC offset for the event timestamp through the native
`LibCHelper.SVM_localUTCOffsetSeconds(millisecondsSince19700101)` helper when
the timestamp is formatted.

Mutating configuration methods synchronize on `LogConfiguration.class`. Each
tag set's `LogOutputList` also synchronizes updates and publishes a replacement
`outputsByLevel` table through a volatile field. The arrays in a published table
are immutable, so a logging thread reads one stable routing snapshot without taking the
configuration monitor.

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

Normal writes use the platform-specific `RawFileOperationSupport` implementation;
`LoggingSupport` supplies the platform-specific archive delete and rename
operations. If a write operation fails, `LogOutput` emits one emergency
`Could not write to log` diagnostic for that output and suppresses repeated
copies of the same diagnostic. The event path remains non-fatal.

Rotation is performed after `LogOutput` completes a write batch and the byte
threshold has been reached. The batch is the whole possibly multi-line event. The
active descriptor is closed, the oldest archive is deleted, existing archives
are renamed toward the last slot, the active path is renamed to `.0`, and the
active file is reopened. Active-file rename failures produce an emergency
diagnostic; archive maintenance failures are best-effort. If reopening fails,
the same non-fatal open-file handling applies.

On POSIX systems, an open log file can be unlinked while its descriptor remains
open. Subsequent writes continue to use that descriptor and do not recreate the
directory entry. This behavior is covered by the native logging tests. Windows
uses platform file-sharing rules, so tests requiring unlinking an open file are
restricted to POSIX platforms.

### JFR integration

JFR has two independent logging sinks. The legacy SVM JFR logger remains
configured by `-XX:FlightRecorderLogging` and writes its established
`[level][tag set] message` format through the low-level SVM log. Its destination
therefore remains the low-level log destination, which is normally standard
error and can be changed with `-XX:LogFile` or an embedding log callback.

JFR records are also offered to unified logging. The
`com.oracle.svm.core.logging.jfr.JfrUnifiedLogging` bridge maps
`jdk.jfr.internal.LogTag` values to `LogTagSet` instances. A regular JFR record
becomes one unified message, while a JFR event containing several lines becomes
one atomic `LogMessage`. The unified copy is written only when its `jfr` tag set
is enabled by `-Xlog`. When both configurations enable a record, one copy
appears in each sink using that sink's format and destination.

The JDK performs a fast enablement check through the volatile `tagSetLevel`
field on each `jdk.jfr.internal.LogTag`. SVM publishes the most detailed level
required by either sink into this field. Each sink then checks its own threshold
again before writing, so a level enabled only by `FlightRecorderLogging` cannot
leak into unified output and a level enabled only by `-Xlog` cannot leak into
the legacy output. Configuration changes recompute the combined JDK threshold.
`FlightRecorderLogging=disable` disables only the legacy sink, and
`-Xlog:disable` disables only unified logging.

The legacy sink preserves its existing low-level write and synchronization
behavior. Unified stream and file writes are protected by destination `VMMutex`
instances. The bridge objects, tag maps,
and per-sink threshold arrays are created in the image heap when `-Xlog` is
supported; bridge calls fold to no-ops when it is absent. Routing a successful
JFR record through either or both sinks does not allocate on the Java heap.

## Comparison with HotSpot unified logging

This comparison uses HotSpot from OpenJDK JDK 25 build 36
([`jdk-25+36`](https://github.com/openjdk/jdk/tree/jdk-25%2B36)) as its baseline.
The relevant HotSpot implementation uses linked output lists, reader tracking,
file stream locks, and a rotation semaphore; see
[`logTagSet.cpp`](https://github.com/openjdk/jdk/blob/jdk-25%2B36/src/hotspot/share/logging/logTagSet.cpp),
[`logOutputList.hpp`](https://github.com/openjdk/jdk/blob/jdk-25%2B36/src/hotspot/share/logging/logOutputList.hpp),
[`logFileOutput.cpp`](https://github.com/openjdk/jdk/blob/jdk-25%2B36/src/hotspot/share/logging/logFileOutput.cpp).
SVM uses the same broad configuration model but a smaller runtime design.

| Area | HotSpot | SVM |
| --- | --- | --- |
| Available tag sets | Every tag set instantiated by HotSpot logging sites. | Every tag set used by the SVM runtime. |
| Output routing | Per-level linked-list heads with atomic reader tracking. | Per-tag-set, per-level immutable output arrays published through volatile fields. The legacy GC fallback uses the same table with a low-level VM-log destination. |
| Configuration | `ConfigurationLock` and reader counts protect updates and delayed reclamation; `jcmd VM.log` supports runtime changes. | Synchronized configuration methods publish replacement arrays. Configuration is startup-oriented except for GC verbosity changes through `MemoryMXBean` and `RuntimeOptions.set` with `PrintGC` or `VerboseGC`. |
| Synchronous output locking | `FileLocker` protects writes; a rotation semaphore covers file rotation. | Stream outputs use an uninterruptible critical section to serialize no-transition native writes with a dedicated `VMMutex`; file outputs use the same pattern with a prebuilt `VMMutex` across the no-transition write, accounting, rotation, and reopen. VM operations use the same serialized paths. |
| Decoration state | Event decorations are resolved for each message. | Event metadata lives in fast thread-local state; line levels remain explicit per line. |
| File rotation | Native C++ file streams and rotation locks. An existing active file is archived at startup when rotation is enabled. | Precomputed native paths, native byte counters, and raw close/delete/rename/reopen operations. An existing active file is truncated at startup regardless of rotation settings. The `%i` filename placeholder separates isolate-local rotation state into distinct operating-system paths. |
| JFR integration | JFR writes directly through HotSpot unified logging. | SVM preserves its legacy `FlightRecorderLogging` output and optionally emits a second copy through unified logging. |
| Allocation contract | Native C++ allocation rules apply. | Successful event processing, including dual JFR routing, is explicitly Java-heap allocation-free; native buffers may grow. |

SVM's design is optimized for a native image whose tag sets and output routing
are known from startup configuration. It avoids dynamic reader reclamation in
the event path and writes complete events directly to their destinations.
