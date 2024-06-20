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

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serial;
import java.util.function.Consumer;

/**
 * An object provided by the system that lets you yield control and return from
 * {@link Continuation#resume()}.
 */
public final class SuspendCapability extends ContinuationSerializable {
    @Serial private static final long serialVersionUID = 4790341975992263909L;

    private ContinuationImpl continuation;

    // region API

    /**
     * Suspends the continuation, unwinding the stack to the point at which it was previously
     * resumed.
     *
     * <p>
     * If successful, execution will continue at after the call to {@link Continuation#resume()}.
     *
     * @throws IllegalContinuationStateException if trying to suspend outside a continuation, or if
     *             there are native frames on the stack, or if the thread is inside a synchronized
     *             block.
     */
    public void suspend() {
        continuation.trySuspend();
    }

    // endregion API

    // region internals

    static SuspendCapability create(ContinuationImpl continuation) {
        return new SuspendCapability(continuation);
    }

    private SuspendCapability(ContinuationImpl continuation) {
        this.continuation = continuation;
    }

    private SuspendCapability() {
    }

    @Override
    void writeObjectExternal(ObjectOutput out) throws IOException {
        out.writeObject(continuation);
    }

    static SuspendCapability readObjectExternal(ObjectInput in, Consumer<SuspendCapability> registerFreshObject) throws IOException, ClassNotFoundException {
        if (!Continuation.isSupported()) {
            throw new UnsupportedOperationException("This VM does not support continuations.");
        }
        SuspendCapability suspend = new SuspendCapability();
        registerFreshObject.accept(suspend);
        Object obj = in.readObject();
        if (obj instanceof ContinuationImpl) {
            suspend.continuation = (ContinuationImpl) obj;
            return suspend;
        }
        throw new FormatVersionException("Erroneous deserialization of SuspendCapability.\n" +
                        "read object was not a continuation:" + obj);
    }

    // endregion internals
}
