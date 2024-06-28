/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.graalvm.continuations;

import java.io.Serial;
import java.io.Serializable;

/**
 * <h1>Continuation</h1>
 *
 * <p>
 * A delimited one-shot continuation, which encapsulates a part of the program's execution such that
 * it can be resumed from the point at which it was suspended.
 *
 * <p>
 * Continuations allow you to mark a region of code in which the program can <i>suspend</i>, which
 * passes control flow up the stack to the point at which the continuation was <i>resumed</i>. This
 * implementation is low level and doesn't address common needs, such as passing objects in and out
 * of the continuation as it suspends and resumes.
 *
 * <p>
 * Continuations are not threads. When accessing thread locals they see the values that their
 * hosting thread would see. Continuations are also not thread safe.
 *
 * <p>
 * Exceptions thrown from the entry point propagate out of {@link #resume()} as
 * {@link ContinuationExecutionException} and then mark the continuation as failed. Resuming the
 * continuation after that point will fail with {@link IllegalContinuationStateException}. If you
 * want to retry a failed continuation you must have a clone from before the failure (see note
 * below).
 *
 * <h1>Serialization</h1>
 *
 * <p>
 * Continuations can be serialized to disk and resumed later in a separate process. Alternatively
 * they can be discarded and left for the GC to clean up.
 *
 * <p>
 * Continuation deserialization is <b>not secure</b>. You should only deserialize continuations you
 * yourself suspended, as resuming a malicious continuation can cause arbitrary undefined behaviour,
 * i.e. is equivalent to handing control of the JVM to the attacker.
 *
 * <h2>Java serialization</h2>
 *
 * <p>
 * By default, {@link Continuation} supports standard java serialization (i.e. implements
 * {@link Serializable}. This works well only if all that is reachable from the continuation is
 * serializable.
 *
 * <p>
 * For a continuation to be serialized, the given {@link ContinuationEntryPoint} itself must be
 * serializable.
 *
 * <h2>External serialization</h2>
 *
 * <p>
 * Given that requiring all reachable objects from the continuation is very restrictive, some users
 * may want to use other serialization frameworks. For that purpose, the class
 * {@link ContinuationSerializable} is provided. See its documentation and its methods'
 * documentation for more details.
 *
 * <h3>Note:</h3>
 * <p>
 * This class implements <i>one shot</i> continuations, meaning you cannot restart a continuation
 * from the same suspend point more than once. For that reason this class is mutable, and the act of
 * calling {@code resume} changes its state. If you want to roll back and retry a resume operation
 * you should start by serializing the continuation, then trying to deserialize it to obtain a new
 * {@code Continuation} that you can resume again. This is required because the continuation may
 * have mutated the heap in arbitrary ways, and so resuming a continuation more than once could
 * cause extremely confusing and apparently 'impossible' states to occur.
 */
public abstract class Continuation extends ContinuationSerializable {
    @Serial private static final long serialVersionUID = 4269758961568150833L;
    private static final boolean IS_SUPPORTED = supported();

    /**
     * Returns {@code true} if this VM supports the continuations feature, {@code false} otherwise.
     */
    public static boolean isSupported() {
        return IS_SUPPORTED;
    }

    /**
     * Creates a new suspended continuation, taking in an {@link ContinuationEntryPoint}.
     *
     * <p>
     * To begin execution call the {@link Continuation#resume()} method. The entry point will be
     * passed a {@link SuspendCapability capability object} allowing it to suspend itself.
     *
     * <p>
     * The continuation will be serializable so long as the given {@link ContinuationEntryPoint} is
     * serializable.
     *
     * @throws UnsupportedOperationException If this VM does not support continuation.
     */
    public static Continuation create(ContinuationEntryPoint continuationEntryPoint) {
        if (!isSupported()) {
            throw new UnsupportedOperationException("This VM does not support continuations.");
        }
        return new ContinuationImpl(continuationEntryPoint);
    }

    /**
     * Returns {@code true} if this continuation is able to be {@link #resume() resumed}.
     * <p>
     * A continuation is resumable if it is freshly created, or if it has been resumed then
     * suspended.
     * <p>
     * A continuation that has {@link #isCompleted() finished executing} is not resumable.
     * <p>
     * A continuation that is being serialized will return {@code false}, until serialization
     * completes.
     */
    public abstract boolean isResumable();

    /**
     * Returns {@code true} if execution of this continuation has completed, whether because the
     * {@link ContinuationEntryPoint} has returned normally, or if an uncaught exception has
     * propagated out of the {@link ContinuationEntryPoint}.
     * <p>
     * A continuation that is being serialized will return {@code false}, until serialization
     * completes.
     */
    public abstract boolean isCompleted();

    /**
     * Runs the continuation until it either completes or calls {@link SuspendCapability#suspend()}.
     * A continuation may not be resumed if it's already {@link #isCompleted() completed}, nor if it
     * is already running.
     *
     * <p>
     * If an exception is thrown by the continuation and escapes the entry point, it will be
     * rethrown here. The continuation is then no longer usable and must be discarded.
     * 
     * @return {@code true} if the continuation was {@link SuspendCapability#suspend() suspended},
     *         or {@code false} if execution of the continuation has completed normally.
     *
     * @throws ContinuationExecutionException if execution of the entry point has thrown an
     *             exception that escapes the continuation scope.
     * @throws IllegalContinuationStateException if the continuation is not {@link #isResumable()
     *             resumable}.
     * @throws IllegalMaterializedRecordException if the VM rejects the continuation. This can
     *             happen, for example, if a continuation was obtained by deserializing a malformed
     *             stream.
     */
    public abstract boolean resume();

    // region internals

    private static boolean supported() {
        try {
            return isSupported0();
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    private static native boolean isSupported0();

    // endregion internals

    // Disallow subclassing outside this package.
    Continuation() {
    }
}
