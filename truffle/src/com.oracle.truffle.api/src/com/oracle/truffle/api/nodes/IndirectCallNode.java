/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;

/**
 * Represents an indirect call to a {@link CallTarget}. Indirect calls are calls for which the
 * {@link CallTarget} may change dynamically for each consecutive call. This part of the Truffle API
 * enables the runtime system to perform additional optimizations on indirect calls.
 *
 * Please note: This class is not intended to be sub classed by guest language implementations.
 *
 * @see DirectCallNode for faster calls with a constantly known {@link CallTarget}.
 * @since 0.8 or earlier
 */
public abstract class IndirectCallNode extends Node {

    static final ThreadLocal<Object> CURRENT_CALL_NODE = new ThreadLocal<>();

    /**
     * Constructor for implementation subclasses.
     *
     * @since 0.8 or earlier
     */
    protected IndirectCallNode() {
    }

    /**
     * Performs an indirect call to the given {@link CallTarget} target with the provided arguments.
     *
     * @param target the {@link CallTarget} to call
     * @param arguments the arguments to provide
     * @return the return value of the call
     * @since 0.23
     */
    public abstract Object call(CallTarget target, Object... arguments);

    /** @since 0.8 or earlier */
    public static IndirectCallNode create() {
        return Truffle.getRuntime().createIndirectCallNode();
    }

    private static final IndirectCallNode UNCACHED = NodeAccessor.RUNTIME.createUncachedIndirectCall();

    /**
     * Returns an uncached version of an indirect call node. Uncached versions of an indirect call
     * node use the {@link EncapsulatingNodeReference#get() current encapsulating node} as source
     * location.
     *
     * @since 19.0
     */
    public static IndirectCallNode getUncached() {
        assert !UNCACHED.isAdoptable();
        return UNCACHED;
    }

}
