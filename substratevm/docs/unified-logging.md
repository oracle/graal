# Unified logging in Substrate VM

This document describes the unified logging implementation in Substrate VM
(SVM). SVM follows the HotSpot `-Xlog` model for selections, levels, stream
outputs, decorators, and multiline messages, while using precomputed tables and
native buffers suitable for a native image.

## Using unified logging

SVM accepts the familiar HotSpot `-Xlog` syntax. `-Xlog:help` lists the tag sets
available in a particular native image. The command line interface is included
when `StrictRuntimeJavaOptions` is enabled.

## Writing log messages

`LogTagSet` provides level-specific methods and a `LogMessage` scope for
multiline events. The message bytes, line metadata, and decorations live in
native thread-local storage. Closing a message routes it to every enabled output
and preserves line order while applying each output's threshold.

Stream outputs format a complete event before entering an uninterruptible
critical section that serializes the no-transition native write. A blocked
stdout or stderr write can therefore delay a safepoint. HotSpot has the same
external I/O limitation when synchronous logging runs on its VM operation
thread.

## Configuration and allocation

Configuration publishes immutable per-level output arrays. `mostDetailedLevel`
provides the fast enablement check, and successful event processing does not
allocate on the Java heap. Host name and process id are cached at initialization;
event timestamps and thread identifiers are captured once for all destinations.

## Testing

The focused native JUnit coverage is in `UnifiedLoggingTest` and can be run with:

```text
mx native-unittest com.oracle.svm.test.logging.UnifiedLoggingTest
```


## File output and failure handling

`LogFileOutput` expands `%p`, `%i`, `%t`, and `%hn`, converts the result to an
absolute native path, and opens the active file when the output is created. `%i`
is an SVM extension for separating isolate-local descriptors, counters, and
rotation state. A preexisting active file is truncated on first open regardless
of whether rotation is enabled.

Writes, byte accounting, rotation, and reopen are serialized by the output's
prebuilt mutex. Rotation closes the descriptor, shifts archives, renames the
active file to `.0`, and reopens it. Open and write failures are diagnosed but do
not abort the VM. On POSIX, an unlinked open output remains writable through its
descriptor; Windows follows its native sharing rules.
