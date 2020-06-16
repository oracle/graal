/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;

/**
 * Enables the dynamic creation of generated nodes. It provides an convenient way to instantiate
 * generated node classes without using reflection.
 *
 * @since 0.8 or earlier
 */
public interface NodeFactory<T> {

    /**
     * Instantiates the node using the arguments array. The arguments length and types must suffice
     * one of the returned signatures in {@link #getNodeSignatures()}. If the arguments array does
     * not suffice one of the node signatures an {@link IllegalArgumentException} is thrown.
     *
     * @param arguments the argument values
     * @return the instantiated node
     * @throws IllegalArgumentException
     * @since 0.8 or earlier
     */
    T createNode(Object... arguments);

    /**
     * Returns the node class that will get created by {@link #createNode(Object...)}. The node
     * class does not match exactly to the instantiated object but they are guaranteed to be
     * assignable.
     *
     * @since 0.8 or earlier
     */
    Class<T> getNodeClass();

    /**
     * Returns a list of signatures that can be used to invoke {@link #createNode(Object...)}.
     *
     * @since 0.8 or earlier
     */
    List<List<Class<?>>> getNodeSignatures();

    /**
     * Returns a list of children that will be executed by the created node. This is useful for base
     * nodes that can execute a variable amount of nodes.
     *
     * @since 0.8 or earlier
     */
    List<Class<? extends Node>> getExecutionSignature();

    /**
     * Returns the uncached version of this node or <code>null</code> if {@link GenerateUncached}
     * was not applied to the node.
     *
     * @since 19.1.0
     */
    default T getUncachedInstance() {
        return null;
    }

}
