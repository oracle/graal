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

import java.util.Objects;

/**
 * Provides the capability of initializing the identity hashcode of arbitrary objects.
 * <p>
 * This may be used during continuation deserialization to restore the original hashcode of objects
 * that have been recorded.
 */
public final class IdentityHashCodes {
    /**
     * Whether the identity hashcode of the given object is initialized.
     * <p>
     * This can happen if:
     * <ul>
     * <li>{@code o.hashCode()} has been called and resolved to {@link Object#hashCode()}.</li>
     * <li>{@link System#identityHashCode(Object) System.identityHashCode(o)} has been called.</li>
     * <li>{@link #set(Object, int) IdentityHashCodes.set(o, _)} has been called.</li>
     * </ul>
     *
     * @throws UnsupportedOperationException If this VM does not support continuations.
     * @throws NullPointerException if {@code o} is {@code null}.
     */
    public static boolean has(Object o) {
        checkIsSupported();
        return isInitialized0(Objects.requireNonNull(o));
    }

    /**
     * Attempts to set the identity hashcode of the given object, returning {@code true} if it
     * succeeds, and {@code false} if the identity hashcode of the object was already initialized.
     *
     * @throws UnsupportedOperationException If this VM does not support continuations.
     * @throws NullPointerException if {@code o} is {@code null}.
     * @throws IllegalArgumentException if {@code hashcode <= 0}.
     */
    public static boolean trySet(Object o, int hashcode) {
        checkIsSupported();
        return setIHashcode0(o, hashcode);
    }

    /**
     * Sets the identity hashcode of the given object if it is not already initialized, and throws
     * {@link IllegalStateException} otherwise.
     *
     * @throws UnsupportedOperationException If this VM does not support continuations.
     * @throws NullPointerException if {@code o} is {@code null}.
     * @throws IllegalArgumentException if {@code hashcode <= 0}.
     * @throws IllegalStateException if the identity hashcode of the given object is already
     *             initialized.
     */
    public static void set(Object o, int hashcode) {
        if (!trySet(o, hashcode)) {
            throw new IllegalStateException("Setting ihashcode of an object whose ihashcode is already initialized.");
        }
    }

    private static void checkIsSupported() {
        if (!Continuation.isSupported()) {
            throw new UnsupportedOperationException("This VM does not support identity hashcode preservation.");
        }
    }

    private static native boolean isInitialized0(Object o);

    private static native boolean setIHashcode0(Object o, int hashcode);

    private IdentityHashCodes() {
    }
}
