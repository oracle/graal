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
import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 * An {@link Enumeration} that emits an element any time {@link #emit(Object)} is called from inside
 * the {@link #generate()} method. Emit can be called anywhere in the call stack. This type of
 * enumeration is sometimes called a <i>generator</i>.
 */
public abstract class Generator<E> implements Enumeration<E>, Serializable {
    @Serial private static final long serialVersionUID = -5614372125614425080L;

    private final Continuation continuation;
    private SuspendCapability suspendCapability;
    private transient E currentElement;
    private transient boolean hasProduced;

    /**
     * This constructor exists only for deserialization purposes. Don't call it directly.
     */
    @SuppressWarnings("this-escape")
    protected Generator() {
        continuation = Continuation.create((ContinuationEntryPoint & Serializable) cap -> {
            this.suspendCapability = cap;
            generate();
        });
    }

    /**
     * Runs the generator and returns true if it emitted an element. If it finished running, returns
     * false. If the generator throws an exception it will be propagated from this method.
     */
    @Override
    public final boolean hasMoreElements() {
        if (hasProduced) {
            return true;
        }
        boolean ready = continuation.isResumable();
        if (!ready) {
            return false;
        }
        continuation.resume();
        return hasProduced;
    }

    /**
     * Runs the generator if necessary, and returns the element it yielded.
     *
     * @throws NoSuchElementException if the generator has finished and no longer emits elements, or
     *             if the generator has previously thrown an exception and failed.
     */
    @Override
    public final E nextElement() {
        if (!hasMoreElements()) {
            throw new NoSuchElementException();
        }
        E el = currentElement;
        currentElement = null;
        hasProduced = false;
        return el;
    }

    /**
     * Call this method to emit an element from inside {@link #generate()}.
     */
    protected final void emit(E element) {
        assert !hasProduced;
        currentElement = element;
        hasProduced = true;
        suspendCapability.suspend();
    }

    /**
     * Implement this method to {@link #emit(Object)} elements from the enumeration.
     */
    protected abstract void generate();

    private transient boolean reentrancy = false;

    @Override
    public String toString() {
        // Printing the continuation will invoke toString on everything reachable from the stack,
        // thus we need to cancel the re-entrancy here.
        if (reentrancy) {
            return "this generator";
        }
        reentrancy = true;
        String result = continuation.toString();
        reentrancy = false;
        return result;
    }
}
