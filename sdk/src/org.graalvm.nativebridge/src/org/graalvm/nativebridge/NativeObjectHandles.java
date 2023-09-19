/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge;

import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.word.WordFactory;

/**
 * A support class for mapping objects in the native image isolate to long handles.
 */
public final class NativeObjectHandles {

    private NativeObjectHandles() {
    }

    /**
     * Creates a handle for the {@code object}. Unless the handle is {@link #remove(long) removed}
     * the {@code object} stays strongly reachable.
     */
    public static long create(Object object) {
        return ObjectHandles.getGlobal().create(object).rawValue();
    }

    /**
     * Resolves a handle into an object.
     *
     * @throws InvalidHandleException if the handle is either already removed or invalid.
     */
    public static <T> T resolve(long handle, Class<T> type) {
        try {
            return type.cast(ObjectHandles.getGlobal().get(WordFactory.pointer(handle)));
        } catch (IllegalArgumentException iae) {
            throw new InvalidHandleException(iae);
        }
    }

    /**
     * Removes a handle. Allows an object identified by the {@code handle} to be garbage collected.
     */
    public static void remove(long handle) {
        try {
            ObjectHandles.getGlobal().destroy(WordFactory.pointer(handle));
        } catch (IllegalArgumentException iae) {
            throw new InvalidHandleException(iae);
        }
    }

    /**
     * An exception thrown when an invalid handle is resolved.
     *
     * @see #resolve(long, Class)
     */
    public static final class InvalidHandleException extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        InvalidHandleException(IllegalArgumentException cause) {
            super(cause.getMessage(), cause);
            setStackTrace(cause.getStackTrace());
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }
}
