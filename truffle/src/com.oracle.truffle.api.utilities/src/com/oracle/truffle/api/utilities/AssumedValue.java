/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.utilities;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;

/**
 * A value that the compiler can assume is constant, but can be changed by invalidation.
 * <p>
 * Compiled code that uses the value will be invalidated each time the value changes, so you should
 * take care to only change values infrequently, or to monitor the number of times the value has
 * changed and at some point to replace the value with something more generic so that it does not
 * have to be changed and code does not have to keep being recompiled.
 *
 * @since 0.8 or earlier
 */
public class AssumedValue<T> {

    private final String name;

    // value behaves as volatile by piggybacking on Assumption semantics
    @CompilationFinal private T value;
    @CompilationFinal private volatile Assumption assumption;

    @SuppressWarnings("rawtypes") private static final AtomicReferenceFieldUpdater<AssumedValue, Assumption> ASSUMPTION_UPDATER = //
                    AtomicReferenceFieldUpdater.newUpdater(AssumedValue.class, Assumption.class, "assumption");

    /** @since 0.8 or earlier */
    public AssumedValue(T initialValue) {
        this(null, initialValue);
    }

    /** @since 0.8 or earlier */
    @TruffleBoundary
    public AssumedValue(String name, T initialValue) {
        this.name = name;
        value = initialValue;
        assumption = Truffle.getRuntime().createAssumption(name);
    }

    /**
     * Get the current value, updating it if it has been {@link #set}. The compiler may be able to
     * make this method return a constant value, but still accommodate mutation.
     *
     * @since 0.8 or earlier
     */
    public T get() {
        if (CompilerDirectives.isPartialEvaluationConstant(assumption)) {
            /*
             * Only check the assumption if we actually constant-fold the value. Otherwise, we might
             * deoptimize for no reason.
             *
             * Note that it's important that the first if condition in this method does a volatile
             * read on the assumption variable. That acts as a memory barrier, preventing the value
             * read from floating above in the case where the value is not constant folded and in
             * the interpreter code. That makes the value field effectively volatile, making this
             * class thread-safe.
             */
            CompilerAsserts.partialEvaluationConstant(value);
            if (!assumption.isValid()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
        }

        return value;
    }

    /**
     * Set a new value, which will be picked up the next time {@link #get} is called.
     *
     * @since 0.8 or earlier
     */
    @TruffleBoundary
    public void set(T newValue) {
        value = newValue;

        Assumption newAssumption = Truffle.getRuntime().createAssumption(name);
        Assumption oldAssumption = ASSUMPTION_UPDATER.getAndSet(this, newAssumption);
        oldAssumption.invalidate();
    }

}
