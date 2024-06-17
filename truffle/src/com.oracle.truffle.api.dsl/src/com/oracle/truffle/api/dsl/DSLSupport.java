/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInterface;

/**
 * APIs to support share code in generated code. APIs in this class are aggressively deprecated and
 * removed in this class.
 *
 * @since 23.0
 */
public abstract class DSLSupport {

    private DSLSupport() {
        /*
         * No instances.
         */
    }

    private static final ClassValue<Enum<?>[]> ENUM_CONSTANTS = new ClassValue<>() {
        @Override
        protected Enum<?>[] computeValue(Class<?> type) {
            return (Enum<?>[]) type.getEnumConstants();
        }
    };

    /**
     * Looks up shared enum constants for DSL generated code. This avoids unnecessary enum arrays in
     * the heap when the DSL creates constants with enum values to avoid the memory overhead of
     * calling the values() method of enum classes.
     *
     * @since 23.0
     */
    @SuppressWarnings("unchecked")
    public static <T extends Enum<?>> T[] lookupEnumConstants(Class<T> c) {
        return (T[]) ENUM_CONSTANTS.get(c);
    }

    /**
     * Inserts a node if a {@link NodeInterface} dynamically implements {@link Node}. Intended for
     * generated code only.
     *
     * @since 23.0
     */
    public static <T extends NodeInterface> T maybeInsert(Node node, T o) {
        if (o instanceof Node) {
            node.insert((Node) o);
        }
        return o;
    }

    /**
     * Inserts a node array if a {@link NodeInterface}[] dynamically implements {@link Node}[].
     * Intended for generated code only.
     *
     * @since 23.0
     */
    public static <T extends NodeInterface> T[] maybeInsert(Node node, T[] o) {
        if (o instanceof Node[]) {
            node.insert((Node[]) o);
        }
        return o;
    }

    /**
     * Helper method for DSL generated code to assert idempotence.
     *
     * @see Idempotent
     * @see NonIdempotent
     * @since 23.0
     */
    public static boolean assertIdempotence(boolean guardValue) {
        if (!guardValue) {
            throw new AssertionError("A guard was assumed idempotent, but returned a different value for a consecutive execution.");
        }
        return true;
    }

    /**
     * Interface implemented by specialization data classes. This marker interface is needed for
     * better validation and error handling during node object inlining. Intended to be used by the
     * DSL implementation, please do not use otherwise.
     *
     * @since 23.1
     */
    public static interface SpecializationDataNode {
    }

}
