# Unified logging in Substrate VM

This document describes the unified logging implementation in Substrate VM
(SVM). SVM follows the HotSpot `-Xlog` model for selections, levels, outputs,
decorators, multiline messages, file rotation, and asynchronous logging, but
implements the runtime path with precomputed tables and native buffers suitable
for a native image.

## Using unified logging

SVM accepts the familiar HotSpot `-Xlog` syntax, so [JEP 158](https://openjdk.org/jeps/158)
is a useful introduction from the user's perspective. SVM only instantiates tag
sets used by its runtime. `-Xlog:help` is the authoritative list for a
particular native image; a tag that is not listed cannot be selected.

## Configuration and routing

`LogConfiguration.initialize` installs the default `all=warning` configuration
on `stdout` and caches the host name and process id. The `time` decorator
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


### JFR integration

JFR has two independent logging sinks. The standalone SVM JFR logger remains
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
the standalone output. Configuration changes recompute the combined JDK threshold.
`FlightRecorderLogging=disable` disables only the standalone sink, and
`-Xlog:disable` disables only unified logging.

The standalone sink preserves its existing low-level write and synchronization
behavior. The unified copy follows the synchronization rules described below:
file writes and rotation are protected by the destination's `VMMutex`, while
stream outputs write directly and asynchronous outputs copy the event into the
preallocated queue. The bridge objects, tag maps,
and per-sink threshold arrays are created in the image heap when unified logging
is supported; bridge calls fold to no-ops when it is absent. Routing a successful
JFR record through either or both sinks does not allocate on the Java heap.

## Writing log messages

`LogTagSet` provides level-specific methods such as `debug`, `info`, `warning`,
and `error`. An enabled single-line log message uses the tag set's shared
`LogMessage` to record one line and then commits it.

For multi-line events, `LogTagSet.message()` returns a shared facade for that tag
set, while the mutable message bytes, line metadata, and event decorations live
in fast thread-local native state. A carrier thread may have only one open
message scope, including an empty scope; nested scopes across tag sets are
rejected. Each line can have its own level. On close, the complete message is
routed to every output enabled by its most severe line. Each output's threshold
then filters individual lines while preserving their order.

Stream outputs format a complete event in the current thread's output buffer and
perform one raw write without a runtime mutex. File outputs format before
entering their prebuilt mutex, then perform the native write, byte accounting,
rotation, and reopen in one uninterruptible `lockNoTransition` critical section.
Consequently, file events cannot be interleaved and rotation cannot occur
between their lines, while formatting does not hold a file lock.

`LogDecorations` is a reusable event record. It captures the wall-clock
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
the standard timezone value returned by `_get_timezone` and applies the same
daylight-saving correction. The result has the ISO-8601 local-to-UTC sign and
avoids Java timezone objects and heap allocation on the event path. A delayed
asynchronous event therefore uses the DST offset for its event timestamp rather
than the offset current when the output happens to format it.

The thread-local message state is not recursive. A tag set must not be logged
again from the same carrier thread while any message is open, including through
a different tag set. Opening a message does not acquire a file-output mutex;
file locking starts only after `close` has formatted the complete event.

## Asynchronous logging

[Asynchronous logging](https://bugs.openjdk.org/browse/JDK-8229517) is
disabled by default and is enabled with:

```text
-Xlog:async[:drop|stall]
```

The default mode is `drop`. In `drop` mode, a producer returns without blocking
for queue space when no record is available; it can still contend while
acquiring the producer and consumer locks. If the queue fills partway through
a multi-line batch, already-published lines remain queued and the remaining
lines for that output are dropped. In `stall` mode, the producer waits for
queue space instead. The queue has 256 reusable records, and each record
contains one copied message line, its level, its output, and a copy of the
event decorations.

`LogAsyncWriter` has one daemon consumer thread. Producers copy message bytes into the records'
native payload buffers before returning. A producer lock serializes producers
while all lines selected for one output are copied, so another producer cannot
insert records between those lines. An event routed to several outputs is
enqueued as one batch per output. A consumer lock protects queue indices,
occupancy, records in flight, and shutdown state. Its `VMCondition` is
associated with the consumer lock and wakes producers and the consumer when
queue state changes.

The two locks have separate roles:

* `PRODUCER_LOCK` orders producers and keeps a stalled or multi-line producer
  from being overtaken by a later producer.
* `CONSUMER_LOCK` protects the queue state and coordinates queue-space waits,
  record publication, record removal, and shutdown.

The consumer removes a record and marks it in flight under `CONSUMER_LOCK`, then
performs formatting and output I/O without holding that queue lock. The in-flight
count keeps queue capacity reserved until the output write completes. Shutdown
sets the stopping flag, wakes waiters, waits for an already-running producer to
finish, and joins the consumer after queued records have been drained.

Both locks and the condition are `VMMutex` and `VMCondition` instances. They
are image-generated runtime primitives and therefore can be used by the
allocation-free synchronization path.

### Safepoint safety

Every queue-lock acquisition can block. In particular, `drop` mode avoids
waiting for queue capacity but does not make acquisition of `PRODUCER_LOCK` or
`CONSUMER_LOCK` non-blocking. Without an explicit safeguard, the following lock
cycle would be possible:

1. A Java thread acquires `PRODUCER_LOCK`.
2. A VM operation stops that thread at a safepoint while it still owns the lock.
3. The VM-operation thread logs a message and waits for `PRODUCER_LOCK`.

`LogAsyncWriter.enqueue` breaks this cycle before touching either queue lock. It
checks `VMOperation.isInProgress()` and returns `false` when called by a VM
operation. `LogTagSet` then writes the same event synchronously. This rule
applies to both asynchronous modes and to every queue state, rather than only to
the full-queue path.

The remaining queue lock order is acyclic. Producers acquire `PRODUCER_LOCK`
before `CONSUMER_LOCK`. The consumer acquires only `CONSUMER_LOCK` and releases
it before formatting or writing a record. Shutdown sets the stopping state while
holding `CONSUMER_LOCK`, releases it, and only then waits for `PRODUCER_LOCK` and
joins the consumer. It does not join the consumer while holding either queue
lock. Logging from the consumer itself also bypasses the queue, and logging
after shutdown has started falls back to synchronous output.

The synchronous fallback does not introduce an equivalent logger-lock cycle.
Stream outputs do not use a runtime mutex. File output formats an entire event
before entering an uninterruptible `lockNoTransition` critical section that
covers the native write, byte accounting, rotation, and reopen operations. A
thread cannot be stopped at a safepoint while it owns that file mutex.
`LogConfiguration.disableLogging` is not supported while a VM operation is in
progress because shutdown acquires queue locks and joins the consumer; it checks
this condition before acquiring the configuration monitor.

These rules prevent a safepoint deadlock caused by unified logging's own locks.
They do not make the underlying output device non-blocking: a no-transition raw
write can still be delayed by a full pipe or stalled file system and thereby
delay safepoint completion. Such a delay is an external I/O liveness problem,
not a cycle among unified-logging locks.

The asynchronous writer is created at the beginning of
`LogConfiguration.logInitializationComplete`, after command-line `-Xlog`
parsing. Disabling logging first drains and joins the consumer, then frees the
records' native buffers before outputs are closed.

## Allocation-free runtime behavior

Successful normal logging operations are annotated with
`RestrictHeapAccess.NO_ALLOCATION`. The contract starts after the caller has
provided the message text; string concatenation or formatting at the call site
can still allocate before logging is entered.

The runtime path maintains this contract as follows:

1. `NativeMemoryLog` stores event bytes in raw native structures containing a position,
   capacity, and inline byte storage. Message, output, and decorator buffers are
   shared by the current carrier thread through `FastThreadLocalFactory`; queue
   records retain owner-specific buffers because they cross threads. `reset`
   rewinds a reusable buffer, whereas `clear` frees it during teardown. Buffers
   may grow with native `malloc` or `realloc`, but do not allocate Java heap
   objects during logging. A thread listener releases all thread-local buffers
   when a platform thread exits.
2. `LogDecorations` formats event metadata and the explicitly supplied line
   level directly into native memory. Host name and process id are cached
   during initialization, while `SVM_localUTCOffsetSeconds` computes the local
   UTC offset for the event timestamp with native time APIs and stack storage.
3. `LogOutput` formats into the thread-local output and decorator buffers.
   Decorator padding is retained per output and updated atomically, so repeated
   events do not create formatted intermediate strings or byte arrays. Stream
   outputs need no runtime mutex; file output locks cover only native operations.
4. `LogMessage` stores line levels, byte offsets, and message bytes in the
   thread-local native state. Its local index walk filters levels and either preserves
   embedded newlines with continuation prefixes or folds them according to the
   output setting. Native line metadata starts with capacity for ten lines and
   grows outside the Java heap when necessary.
5. The asynchronous writer creates its Java record array and worker thread at
   initialization. The worker initializes its thread-local output state before
   processing records, and the thread listener releases that state when the
   worker exits. Every record owns a reusable native message buffer with 1024
   bytes reserved before the writer is published.
6. `LogFileOutput` retains the expanded active path and archive paths as native
   `RawFilePath` values. Runtime file operations use
   `RawFileOperationSupport` and do not construct `Path`, filename strings, or
   temporary Java objects.

The allocation restriction applies to successful synchronous and asynchronous
event processing, including decoration, queue copying, raw writes, and file
rotation. Configuration, help output, diagnostics, and exceptional Java paths
may allocate.

## File output and failure handling

`LogFileOutput` expands `%p`, `%t`, and `%hn`, converts the result to an absolute
path, prepares the native active and archive paths, and opens the active file
when the output is created. It does not create missing parent directories. If
opening fails, it emits an emergency diagnostic containing the path and native
error code, leaves the output configured, and does not abort the VM. A later
event for an output whose descriptor is unavailable is safely ignored.

Normal writes use the platform-specific `RawFileOperationSupport` implementation;
`LoggingSupport` supplies the platform-specific archive delete and rename
operations. If a write operation fails, `LogOutput` emits one emergency
`Could not write to log` diagnostic for that output and suppresses repeated
copies of the same diagnostic. The event path remains non-fatal.

Rotation is performed after `LogOutput` completes a write batch and the byte
threshold has been reached. For synchronous logging the batch is the whole
possibly multi-line event; for asynchronous logging it is one queued line. The
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

## Comparison with HotSpot unified logging

The relevant HotSpot implementation uses linked output lists, reader tracking,
file stream locks, a rotation semaphore, and native asynchronous buffers; see
[`logTagSet.cpp`](https://github.com/openjdk/jdk/blob/master/src/hotspot/share/logging/logTagSet.cpp),
[`logOutputList.hpp`](https://github.com/openjdk/jdk/blob/master/src/hotspot/share/logging/logOutputList.hpp),
[`logFileOutput.cpp`](https://github.com/openjdk/jdk/blob/master/src/hotspot/share/logging/logFileOutput.cpp),
and [`logAsyncWriter.hpp`](https://github.com/openjdk/jdk/blob/master/src/hotspot/share/logging/logAsyncWriter.hpp).
SVM uses the same broad configuration model but a smaller runtime design:

| Area | HotSpot | SVM |
| --- | --- | --- |
| Available tag sets | Every tag set instantiated by HotSpot logging sites. | Only tag sets used by the SVM runtime, including class loading, JFR, and logging. |
| Runtime modes | Synchronous by default; `-Xlog:async` adds a bounded queue and writer thread. | Synchronous by default; `-Xlog:async[:drop\|stall]` uses a preallocated queue with native payload buffers and a writer thread. VM operations bypass the queue and write synchronously. |
| Routing | Per-level linked-list heads with atomic reader tracking. | Per-tag-set, per-level immutable output arrays published through volatile fields. |
| Configuration | `ConfigurationLock` and reader counts protect updates and delayed reclamation; `jcmd VM.log` supports runtime changes. | Synchronized configuration methods publish replacement arrays. Configuration is startup-oriented. JFR combines the fast enablement threshold needed by its independent standalone and unified sinks, then filters each sink separately. |
| Synchronous output locking | `FileLocker` protects writes; a rotation semaphore covers file rotation. | Stream outputs write directly; file outputs format before taking a prebuilt `VMMutex`, then perform native write, accounting, rotation, and reopen in an uninterruptible critical section. |
| Asynchronous buffering and locking | Native ping-pong buffers and producer and consumer synchronization protect the queue. | A preallocated 256-record Java ring with native payload buffers, `VMMutex` producer and consumer locks, and a `VMCondition` coordinate publication, waiting, consumption, and shutdown. |
| Decoration state | Resolved event decorations can remain in asynchronous messages. | Event-only decorations live in fast thread-local state and are copied into each asynchronous queue record; line levels remain explicit per line or record. |
| File rotation | Native C++ file streams and rotation locks. | Precomputed native paths, native byte counters, and raw close/delete/rename/reopen operations. |
| JFR integration | JFR writes directly through HotSpot unified logging. | SVM preserves its standalone `FlightRecorderLogging` output and optionally emits a second copy through unified logging. |
| Allocation contract | Native C++ allocation rules apply. | Successful event processing, including dual JFR routing, is explicitly Java-heap allocation-free; native buffers may grow. |

SVM's design is optimized for a native image whose tag sets and output routing
are known from startup configuration. It avoids dynamic reader reclamation in
the event path, while its asynchronous queue uses explicit VM locking
primitives so waiting and shutdown remain usable without Java heap allocation.

## Source and tests

The native JUnit coverage is in `UnifiedLoggingTest`.
It covers level and tag parsing, decorators, selection precedence, immutable
output routing, concurrent synchronous messages, rejection of nested message
scopes, asynchronous messages, synchronous VM operation fallback, multiline
ordering, `drop` and `stall` modes, file rotation, first-configuration output options,
Windows output paths, missing-directory open failures, and POSIX deletion of
an open file, and thread-exit reclamation of logging buffers when native memory
tracking is available.

The `Target_com_oracle_svm_core_logging_*` classes in the same package use
`@Alias` to expose package-private constructors and methods needed for direct
testing. The suite can be run with:

```text
mx native-unittest com.oracle.svm.test.logging.UnifiedLoggingTest
```
