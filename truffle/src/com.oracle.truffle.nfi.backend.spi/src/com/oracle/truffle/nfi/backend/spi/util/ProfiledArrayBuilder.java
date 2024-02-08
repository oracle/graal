/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.backend.spi.util;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.NeverDefault;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public final class ProfiledArrayBuilder<T> {

    private T[] storage;
    private int size;

    private final ArraySizeMemento memento;

    private ProfiledArrayBuilder(T[] storage, ArraySizeMemento memento) {
        this.storage = storage;
        this.size = 0;
        this.memento = memento;
    }

    public int getSize() {
        return size;
    }

    public void add(T obj) {
        if (size >= storage.length) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            /*
             * This grows by quite a bit the first time we hit it, so we don't have to do it too
             * often. Since the array size is profiled, this is only ever expected to happen on the
             * first run.
             */
            storage = Arrays.copyOf(storage, (storage.length + 5) * 2);
        }
        storage[size++] = obj;
    }

    public T[] getFinalArray() {
        T[] ret = storage;
        storage = null; // prevent further modification
        if (memento != null) {
            memento.feedbackProfile(size);
        }
        return ret;
    }

    @FunctionalInterface
    public interface ArrayFactory<T> {

        T[] create(int size);
    }

    public abstract static class ArrayBuilderFactory {

        @NeverDefault
        public static ArrayBuilderFactory create() {
            return new ProfiledArrayBuilderFactory();
        }

        public static ArrayBuilderFactory getUncached() {
            return UncachedArrayBuilderFactory.INSTANCE;
        }

        public abstract <T> ProfiledArrayBuilder<T> allocate(ArrayFactory<T> factory);

        // prevent subclassing
        private ArrayBuilderFactory() {
        }
    }

    private static final class ArraySizeMemento {

        @CompilationFinal volatile int profiledInitialSize = 0;
        @CompilationFinal volatile Assumption assumption = Truffle.getRuntime().createAssumption("ProfiledArrayBuilder");

        private static final AtomicIntegerFieldUpdater<ArraySizeMemento> SIZE_UPDATER = //
                        AtomicIntegerFieldUpdater.newUpdater(ArraySizeMemento.class, "profiledInitialSize");
        private static final AtomicReferenceFieldUpdater<ArraySizeMemento, Assumption> ASSUMPTION_UPDATER = //
                        AtomicReferenceFieldUpdater.newUpdater(ArraySizeMemento.class, Assumption.class, "assumption");

        void feedbackProfile(int newSize) {
            if (newSize > profiledInitialSize) {
                /*
                 * We don't need to check the assumption here. We know profiledInitialSize only ever
                 * increases. So even if the assumption is invalid, we know that the "true" value of
                 * profiledInitialSize must be bigger than the "outdated" value. So even if some
                 * other code updates the profiled size, as long as we never see a bigger value at
                 * this point, we don't need to deopt here.
                 */
                CompilerDirectives.transferToInterpreterAndInvalidate();
                int oldProfiledInitialSize;
                do {
                    oldProfiledInitialSize = profiledInitialSize;
                    if (newSize <= oldProfiledInitialSize) {
                        // another thread was faster, nothing to do
                        return;
                    }
                } while (!SIZE_UPDATER.compareAndSet(this, oldProfiledInitialSize, newSize));

                Assumption newAssumption = Truffle.getRuntime().createAssumption("ProfiledArrayBuilder");
                Assumption oldAssumption = ASSUMPTION_UPDATER.getAndSet(this, newAssumption);
                oldAssumption.invalidate();
            }
        }
    }

    private static final class ProfiledArrayBuilderFactory extends ArrayBuilderFactory {

        final ArraySizeMemento memento = new ArraySizeMemento();

        @Override
        public <T> ProfiledArrayBuilder<T> allocate(ArrayFactory<T> factory) {
            if (!memento.assumption.isValid()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            return new ProfiledArrayBuilder<>(factory.create(memento.profiledInitialSize), memento);
        }
    }

    private static final class UncachedArrayBuilderFactory extends ArrayBuilderFactory {

        private static final UncachedArrayBuilderFactory INSTANCE = new UncachedArrayBuilderFactory();
        private static final int UNCACHED_INITIAL_SIZE = 10;

        @Override
        public <T> ProfiledArrayBuilder<T> allocate(ArrayFactory<T> factory) {
            return new ProfiledArrayBuilder<>(factory.create(UNCACHED_INITIAL_SIZE), null);
        }
    }
}
