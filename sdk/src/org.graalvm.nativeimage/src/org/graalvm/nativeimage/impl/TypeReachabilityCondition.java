/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage.impl;

import java.util.Objects;

import org.graalvm.nativeimage.dynamicaccess.AccessCondition;

/**
 * Type that represents both {@code typeReached} and {@code typeReachable} condition. When
 * {@link TypeReachabilityCondition#runtimeChecked} is <code>true</code> denotes that this is a
 * <code>typeReached</code> condition.
 */
public final class TypeReachabilityCondition implements AccessCondition {

    /* Cached to save space: it is used as a marker for all non-conditional elements */
    public static final TypeReachabilityCondition JAVA_LANG_OBJECT_REACHED = new TypeReachabilityCondition(Object.class, true);
    private final Class<?> type;

    private final boolean runtimeChecked;

    /**
     * Creates either a type-reached condition ({@code runtimeChecked = true}) or a type-reachable
     * condition.
     *
     * @param type that has to be reached (or reachable) for this condition to be satisfied
     * @param runtimeChecked makes this a type-reachable condition when false
     * @return instance of the condition
     */
    public static TypeReachabilityCondition create(Class<?> type, boolean runtimeChecked) {
        Objects.requireNonNull(type);
        if (TypeReachabilityCondition.JAVA_LANG_OBJECT_REACHED.getType().equals(type)) {
            return TypeReachabilityCondition.JAVA_LANG_OBJECT_REACHED;
        }
        return new TypeReachabilityCondition(type, runtimeChecked);
    }

    private TypeReachabilityCondition(Class<?> type, boolean runtimeChecked) {
        this.runtimeChecked = runtimeChecked;
        this.type = type;
    }

    public boolean isAlwaysTrue() {
        return AccessCondition.unconditional().equals(this);
    }

    public Class<?> getType() {
        return type;
    }

    public boolean isRuntimeChecked() {
        return runtimeChecked;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TypeReachabilityCondition that = (TypeReachabilityCondition) o;
        return runtimeChecked == that.runtimeChecked && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, runtimeChecked);
    }

    @Override
    public String toString() {
        return "TypeReachabilityCondition(" +
                        "type=" + type +
                        ", runtimeChecked=" + runtimeChecked +
                        ')';
    }
}
