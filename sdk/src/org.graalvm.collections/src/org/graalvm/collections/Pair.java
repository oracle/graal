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

import java.util.Objects;

/**
 * Utility class representing a pair of values.
 *
 * @since 19.0
 */
public final class Pair<L, R> {

    private static final Pair<Object, Object> EMPTY = new Pair<>(null, null);

    private final L left;
    private final R right;

    /**
     * Returns an empty pair.
     *
     * @since 19.0
     */
    @SuppressWarnings("unchecked")
    public static <L, R> Pair<L, R> empty() {
        return (Pair<L, R>) EMPTY;
    }

    /**
     * Constructs a pair with its left value being {@code left}, or returns an empty pair if
     * {@code left} is null.
     *
     * @return the constructed pair or an empty pair if {@code left} is null.
     * @since 19.0
     */
    public static <L, R> Pair<L, R> createLeft(L left) {
        if (left == null) {
            return empty();
        } else {
            return new Pair<>(left, null);
        }
    }

    /**
     * Constructs a pair with its right value being {@code right}, or returns an empty pair if
     * {@code right} is null.
     *
     * @return the constructed pair or an empty pair if {@code right} is null.
     * @since 19.0
     */
    public static <L, R> Pair<L, R> createRight(R right) {
        if (right == null) {
            return empty();
        } else {
            return new Pair<>(null, right);
        }
    }

    /**
     * Constructs a pair with its left value being {@code left}, and its right value being
     * {@code right}, or returns an empty pair if both inputs are null.
     *
     * @return the constructed pair or an empty pair if both inputs are null.
     * @since 19.0
     */
    public static <L, R> Pair<L, R> create(L left, R right) {
        if (right == null && left == null) {
            return empty();
        } else {
            return new Pair<>(left, right);
        }
    }

    private Pair(L left, R right) {
        this.left = left;
        this.right = right;
    }

    /**
     * Returns the left value of this pair.
     *
     * @since 19.0
     */
    public L getLeft() {
        return left;
    }

    /**
     * Returns the right value of this pair.
     *
     * @since 19.0
     */
    public R getRight() {
        return right;
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(left) + 31 * Objects.hashCode(right);
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof Pair) {
            Pair<L, R> pair = (Pair<L, R>) obj;
            return Objects.equals(left, pair.left) && Objects.equals(right, pair.right);
        }

        return false;
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public String toString() {
        return String.format("(%s, %s)", left, right);
    }
}
