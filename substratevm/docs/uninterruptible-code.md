# Pitfalls in `@Uninterruptible` code

## Overview

Native Image omits safepoint and stack overflow checks in `@Uninterruptible` code. This prevents
interruptions triggered by these checks, such as stopping for garbage collection or other VM
operations that require a safepoint, or executing recurring callbacks. Java heap allocations and
Java monitor acquisition (`synchronized`) are forbidden.
Native Image checks these restrictions and the `@Uninterruptible` annotation's caller/callee contracts
during image construction.

## Pitfalls

- **Hidden allocations:** watch for boxing, varargs arrays, string concatenation, formatting,
  `Enum.values()`, iterators, capturing lambdas, and streams. Error paths count too.
- **Runtime class initialization:** disallowed unless `calleeMustBe = false`, in which case it must
  be safe to execute interruptible code there.
- **JDK annotations:** `@AnnotateOriginal` can add `@Uninterruptible` to existing JDK methods. Keep
  this use minimal, because JDK implementations can change and must continue to satisfy the contract.
  Prefer existing uninterruptible alternatives in utility classes such as `UninterruptibleUtils`.
- **Overrides:** preserve the `@Uninterruptible` annotation and caller/callee requirements. Avoid
  covariant returns, which can introduce synthetic bridge methods that violate the contract.
- **Boundaries:** derive and use an untracked object address without returning to interruptible code
  between those operations. Separate annotated helpers do not protect the code between calls. Use
  `callerMustBe = true` when returning a value that requires the caller to remain uninterruptible.
- **Inlining:** `mayBeInlined = true` allows inlining into interruptible code and removes protection
  from that copy. It is not a request to inline. Helpers that are uninterruptible only because their
  callers require it normally use `reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true`.
- **Inlining callees:** a `mayBeInlined = true` method must not normally call an uninterruptible
  Java method with `mayBeInlined = false`, because inlining could remove the callee's protection.
  Remove `mayBeInlined = true` from the caller or prevent callee inlining with `@NeverInline`.
- **Interruptible callees:** use `calleeMustBe = false` only when the method needs uninterruptible
  execution before or after calls that may safely be interruptible. At each such call, ensure that
  interruption cannot invalidate state needed by this method or its callers. Document why the call
  is safe. This flag does not permit allocations in the annotated method itself.
  Keep these methods as short as possible, ideally just a bridge to interruptible code, because the
  flag relaxes checking for all their callees.
- **Concurrency and locking:** other threads can still execute the same code and access shared data.
  Synchronization options include, for example, `VMMutex`, `VMCondition`, and spin locks.
- **Deadlocks:** thread A holds a lock in interruptible code and stops at a safepoint. Thread B waits
  for the same lock in uninterruptible code. The safepoint waits for B, so A cannot resume to release
  the lock.
- **Enum switch expressions:** with JDK 25, an exhaustive enum switch without an explicit `default`
  can generate a fallback path that allocates a `MatchException`. If the default case should be
  unreachable, use an explicit `default` that throws `VMError.shouldNotReachHere` with a constant message.

Details: [annotation contract](../src/com.oracle.svm.shared/src/com/oracle/svm/shared/Uninterruptible.java)
and [checker](../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/code/UninterruptibleAnnotationChecker.java).
