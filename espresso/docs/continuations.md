# Espresso Continuations

The continuations feature of the Espresso VM allows you to control the program stack. When a continuation is _suspended_
the stack is unwound and copied onto the heap as ordinary Java objects. When a continuation is _resumed_ those objects
are put back onto the stack, along with all the needed metadata to resume execution at the pause point. The heap objects
can be serialized to resume execution in a different JVM running the same code (e.g. after a restart).

## Usage

See the JavaDoc of the `org.graalvm.continuations` package, and make sure to add the `continuations.jar` in the Espresso
distribution to your classpath when compiling (but not at runtime).

Currently, only the Espresso VM supports the continuations feature. Since it is still experimental, the option needs to
be enabled by using the flags `--experimental-options --java.Continuum=true`.

### High level

If you can model your use case as code that emits (or _yields_) a stream of objects, you can use the `Generator<T>`
class. This provides something similar to Python's generators with a convenient API. Just subclass it,
implement `generate` and call `emit` from inside it.

### Low level

You create a new `Continuation` by passing the constructor an object that implements the functional
interface `ContinuationEntryPoint` (which can be a lambda). That object's `start` method receives
a `SuspendCapability` that lets you trigger suspension. You can do that from _any_ depth in the stack as
long as the code was invoked via the entry point, and all the frames between the call to `suspend` and `resume` will be
unwound and stored inside the `Continuation` object. You can then call `resume()` on it to kick it off for the first
time or to restart from the last suspend point.

Continuations are single-threaded constructs. There are no second threads involved, and the `resume()` method blocks
until the continuation either finishes successfully, throws an exception or calls suspend. `isResumable()` can be called
to determine if the continuation can be resumed (if the continuation has been freshly created or it has been previously
suspended), and `isCompleted()` can be called to determine if the continuation has completed (either by returning
normally, or if an exception escaped).

`Continuation` implements `Serializable` and can serialize to a backwards compatible format. Because frames can point to
anything in their parameters and local variables, the class `ContinuationSerializable` provides static
methods `readObjectExternal` and `writeObjectExternal` which may be used to coordinate serialization of
continuation-related objects with a non-jdk serialization engine.

## Security

Continuations that have **not** been _materialized_ are safe, as the frame record is kept internal to the VM.

Materializing a continuation refers to making the record visible to Java code, through the
private `ContinuationImpl.stackFrameHead` field. Currently, the only path for materialization is through serialization.

When restoring from a materialized frame (_dematerialization_), only minimal checks are performed by the VM, and only to
prevent a VM crash. Examples of these checks are:

- Ensures that resume only happens on `invoke` bytecodes.
- Ensures that the recorded frame data is consistent with what was computed by the bytecode verifier.
- Ensures that the last frame in the record is `ContinuationImpl.suspend`.

Deserializing a continuation supplied by an attacker will allow complete takeover of the JVM. Only resume continuations
you persisted yourself!

## Use cases

Serializing a continuation makes it _multi-shot_, meaning you can restart a continuation more than once and thus redo
the same computation with different inputs. This ability to explore "parallel worlds" opens up many interesting use
cases.

- **Speculative Execution**: CPU-style data speculation to accelerate usage of remote high-latency data
  stores. [See below](#speculative-execution).
- **Backtracking in Search Algorithms**: Utilizing continuations to represent states in search trees, allowing easy
  return to these states for further exploration.
- **Implementing Coroutines/Yield**: Facilitating cooperative multitasking by allowing functions to yield and resume
  execution at certain points.
- **Web Request Handling**: Maintaining state across HTTP requests in web applications without relying on global
  variables or session storage.
- **Functional Reactive Programming (FRP)**: Managing control flow in FRP systems, where continuations represent future
  states of data streams.
- **Undo/Redo Functionality**: Capturing application state at various points to enable undoing or redoing actions. Model
  your app as a continuation-based actor and serialize after each state mutation.
- **Game Development**: Model NPCs and other interactive entities using continuations instead of hand-crafted state
  machines. For example `monster.walkTo(...)` can be added to game logic, even though the walk operation is very slow.
  The state of these continuations can be included into save game files.
- **Delimited Continuations for Modularity**: Encapsulating control flow in modular components, aiding in separation of
  concerns and code maintainability.
- **Serialized Continuations for Distributed Computing**: Serializing continuation state, allowing distributed systems
  to migrate units of work.
- **Parsing**: Implementing non-deterministic parsers or interpreters where multiple potential parsing paths are
  explored simultaneously.
- **Custom Control Structures**: Creating new control structures (like loops, exception handling) that are not natively
  supported in the programming language.
- **Time-travel Debugging**: Capturing continuations at various points in a program to enable "stepping back" in time
  during debugging sessions.
- **Live Programming/Hot Code Swapping**: Using continuations to maintain program state while parts of the program are
  updated or reloaded.

### Speculative Execution

CPUs attempt to guess the direction of a branch when the data it depends on hasn't arrived yet. This is helpful because
memory is slow. The same trick can be done at much higher levels using continuations when reading data from slow data
sources like a far away server. If you have a computation where CPU intensive work is interleaved with long blocking
periods, this can speed things up.

When a continuation performs a slow operation that yields a value (e.g. an RPC), we can suspend, serialize, dispatch the
request and then instead of waiting for the result and scheduling other work - as would be standard in a
continuation-based async threads implementation like Loom - we can instead pick one or more values that we think the RPC
_might_ return. Then we deserialize the continuation again for each value we want to try and resume it. This forks
execution into multiple parallel universes. The new paths can then fork again, forming a tree of possibilities. If the
continuation tries to do something that isn't controlled by the surrounding framework and can't be undone (a side
effect), it suspends at that point and doesn't continue (this is as far as we can speculate).

As results arrive from the remote server we can steadily resolve our way down the tree discarding all the serialized
continuations that exist in worlds that weren't taken. Eventually the final result is received and the continuation can
finish, or continue past a side effecting point.

If the server protocol allows results to be chained together (e.g.
as [Cap'n'Proto RPC does](https://capnproto.org/rpc.html#time-travel-promise-pipelining),
or [FoundationDB](https://github.com/apple/foundationdb/wiki/Everything-about-GetMappedRange)), back-to-back speculative
calls can be transmitted to the server for local processing, avoiding roundtrips.

### Limitations

There are special situations in which a call to `suspend` may fail with `IllegalContinuationStateException`. These are:

- If there is no call to `resume` in the call-stack.
- If in between the call to `resume` and `suspend` any of the following holds:
    - A lock is held (this may be an object monitor through the `MONITORENTER` bytecode, or even
      a `java.util.concurrent.locks.ReentrantLock`).
    - There is a non-java frame on the stack (this could be a `native` method, or even a VM instrinsic).

Furthermore, there is currently no support for continuation-in-continuation.

## Internal implementation notes

*This section is only relevant for people working on Espresso itself.*

Continuations interact with the VM via private intrinsics registered on the `Continuation` and `ContinuationImpl` class.

A continuation starts by calling into the VM. Execution resurfaces in the guest world at the private `run` method of
`ContinuationImpl`, which then invokes the user's given entry point.

Suspending throws a host-side exception that is caught by the `BytecodeNode` interpreter loop. The stack frame is copied
into a new host-side object called a `HostFrameRecord` (HFR). The exception is then rethrown. The HFRs are chained
together in a linked list. Once execution reaches the private `run` method of `ContinuationImpl` the HFR list is
attached into a hidden field of `ContinuationImpl`. Control is then returned to the guest.

On resuming a `Continuation`, the entire call stack needs to be re-winded. This happens through a different `CallTarget`
than for regular calls, and there is one such call target per encountered resume `bci`.

These call targets take a single argument: the `HostFrameRecord` that was stored into the `Continuation`. Using this
record, we restore the frame for the current method, we unlink the current record from the rest (for GC purposes), and
we pass the rest of the records to the next method. This is all done in a special invoke node, `InvokeContinuableNode`.

The separation of the call targets has two advantages:

- It does not interfere with regular calls.
- Resuming and suspending can be partial-evaluated, leading to fast suspend/resume cycles.

Serialization is done entirely in guest-side code, by having the `Continuation` class implement `Serializable`. The
format is designed to enable backwards-compatible evolution of the format.