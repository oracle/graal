/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.nodes;

/**
 * Represents an execution signature of a {@link RootNode}. This is used to represent AOT
 * signatures.
 *
 * @see RootNode#prepareForAOT()
 * @since 20.3
 */
public final class ExecutionSignature {

    /**
     * Returns a generic return profile where return and argument types are unknown.
     *
     * @since 20.3
     */
    public static final ExecutionSignature GENERIC = create(null, null);

    private final Class<?>[] argumentTypes;
    private final Class<?> returnType;

    ExecutionSignature(Class<?> returnType, Class<?>[] argumentTypes) {
        this.argumentTypes = argumentTypes;
        this.returnType = returnType;
    }

    /**
     * Returns the argument types of the execution signature. The returned types may be
     * <code>null</code> to indicate that nothing is known about argument types. If the argument
     * returns a non-null value then the length of the array will be used for the number of expected
     * arguments. Each value of the return array specifies the expected argument type of the
     * execution. If an argument type is <code>null</code> then this indicates that the argument
     * type is unknown.
     *
     * @since 20.3
     */
    public Class<?>[] getArgumentTypes() {
        return argumentTypes;
    }

    /**
     * Returns the return type of the execution signature. The return type may be <code>null</code>
     * to indicate that the type is unknown.
     *
     * @since 20.3
     */
    public Class<?> getReturnType() {
        return returnType;
    }

    /**
     * Creates a new execution signature of a {@link RootNode}. The return type or the argument
     * types may be null to indicate that they are unknown. Individual argument type array values
     * may also be null to indicate they are unknown.
     *
     * @since 20.3
     */
    public static ExecutionSignature create(Class<?> returnType, Class<?>[] argumentTypes) {
        return new ExecutionSignature(returnType, argumentTypes);
    }

}
