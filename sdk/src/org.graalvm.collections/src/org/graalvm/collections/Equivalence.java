/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.collections;

/**
 * Strategy for comparing two objects. Default predefined strategies are {@link #DEFAULT},
 * {@link #IDENTITY}, and {@link #IDENTITY_WITH_SYSTEM_HASHCODE}.
 *
 * @since 19.0
 */
public abstract class Equivalence {

    /**
     * Default equivalence calling {@link #equals(Object)} to check equality and {@link #hashCode()}
     * for obtaining hash values. Do not change the logic of this class as it may be inlined in
     * other places.
     *
     * @since 19.0
     */
    public static final Equivalence DEFAULT = new Equivalence() {

        @Override
        public boolean equals(Object a, Object b) {
            return a.equals(b);
        }

        @Override
        public int hashCode(Object o) {
            return o.hashCode();
        }
    };

    /**
     * Identity equivalence using {@code ==} to check equality and {@link #hashCode()} for obtaining
     * hash values. Do not change the logic of this class as it may be inlined in other places.
     *
     * @since 19.0
     */
    public static final Equivalence IDENTITY = new Equivalence() {

        @Override
        public boolean equals(Object a, Object b) {
            return a == b;
        }

        @Override
        public int hashCode(Object o) {
            return o.hashCode();
        }
    };

    /**
     * Identity equivalence using {@code ==} to check equality and
     * {@link System#identityHashCode(Object)} for obtaining hash values. Do not change the logic of
     * this class as it may be inlined in other places.
     *
     * @since 19.0
     */
    public static final Equivalence IDENTITY_WITH_SYSTEM_HASHCODE = new Equivalence() {

        @Override
        public boolean equals(Object a, Object b) {
            return a == b;
        }

        @Override
        public int hashCode(Object o) {
            return System.identityHashCode(o);
        }
    };

    /**
     * Subclass for creating custom equivalence definitions.
     *
     * @since 19.0
     */
    protected Equivalence() {
    }

    /**
     * Returns {@code true} if the non-{@code null} arguments are equal to each other and
     * {@code false} otherwise.
     *
     * @since 19.0
     */
    public abstract boolean equals(Object a, Object b);

    /**
     * Returns the hash code of a non-{@code null} argument {@code o}.
     *
     * @since 19.0
     */
    public abstract int hashCode(Object o);
}
