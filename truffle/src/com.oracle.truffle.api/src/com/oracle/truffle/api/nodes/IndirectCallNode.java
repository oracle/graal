/*
 * Copyright (c) 2012, 2026, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * Represents an indirect call to a {@link CallTarget}. Indirect calls are calls for which the
 * {@link CallTarget} may change dynamically for each consecutive call. This part of the Truffle API
 * enables the runtime system to perform additional optimizations on indirect calls.
 * <p>
 * Please note: This class is not intended to be sub classed by guest language implementations.
 * <p>
 * See {@link DirectCallNode} for faster calls with a constantly known {@link CallTarget}.
 * 
 * @since 0.8 or earlier
 */
public abstract class IndirectCallNode extends Node {

    /**
     * Constructor for implementation subclasses.
     *
     * @since 0.8 or earlier
     */
    protected IndirectCallNode() {
        super();
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

    /**
     * Performs an indirect call to the given {@link CallTarget} target with an explicit bytecode
     * index and the provided arguments.
     * <p>
     * Callers that do not have an explicit bytecode index should pass <code>-1</code>.
     *
     * @param target the {@link CallTarget} to call
     * @param bytecodeIndex the bytecode index associated with this call, or <code>-1</code> if no
     *            explicit bytecode index is available
     * @param arguments the arguments to provide
     * @return the return value of the call
     * @since 25.6
     */
    public abstract Object call(CallTarget target, int bytecodeIndex, Object[] arguments);

    /**
     * Calls the given root node with an explicit bytecode index and arguments.
     * <p>
     * A concrete root-node type allows the host compiler to optimize the call boundary further,
     * including inlining into {@link RootNode#execute}. Cast {@code root} to its concrete type
     * before calling this method, even if the root instance changes between calls.
     * <p>
     * This call node identifies the location of the call. Callers that do not have an explicit
     * bytecode index should pass {@code -1}. The root node's call target must already be
     * initialized using {@link RootNode#initializeCallTarget()} or {@link RootNode#getCallTarget()}.
     *
     * @param root the root node to call
     * @param bytecodeIndex the bytecode index associated with this call, or {@code -1} if no
     *            explicit bytecode index is available
     * @param arguments the arguments to provide
     * @return the return value of the call
     * @throws NullPointerException if {@code root} is {@code null}
     * @throws IllegalStateException if the root node's call target has not been initialized
     * @since 25.6
     */
    public Object call(RootNode root, int bytecodeIndex, Object[] arguments) {
        return root.call(this, bytecodeIndex, arguments);
    }

    /**
     * Creates a cached indirect call node.
     *
     * @return a new call node
     * @since 0.8 or earlier
     */
    public static IndirectCallNode create() {
        return NodeAccessor.RUNTIME.createIndirectCallNode();
    }

    private static final IndirectCallNode UNCACHED = new IndirectCallNode() {
        @Override
        public boolean isAdoptable() {
            return false;
        }

        @Override
        @TruffleBoundary
        public Object call(CallTarget target, Object... arguments) {
            return target.call(arguments);
        }

        @Override
        @TruffleBoundary
        public Object call(CallTarget target, int bytecodeIndex, Object[] arguments) {
            EncapsulatingNodeReference encapsulating = EncapsulatingNodeReference.getCurrent();
            Node location = encapsulating.set(null);
            try {
                return target.call(location, bytecodeIndex, arguments);
            } finally {
                encapsulating.set(location);
            }
        }

        @Override
        @TruffleBoundary
        public Object call(RootNode root, int bytecodeIndex, Object[] arguments) {
            EncapsulatingNodeReference encapsulating = EncapsulatingNodeReference.getCurrent();
            Node location = encapsulating.set(null);
            try {
                return root.call(location, bytecodeIndex, arguments);
            } finally {
                encapsulating.set(location);
            }
        }
    };

    /**
     * Returns an uncached version of an indirect call node. Uncached versions of an indirect call
     * node use the {@link EncapsulatingNodeReference#get() current encapsulating node} as source
     * location.
     *
     * @since 19.0
     */
    public static IndirectCallNode getUncached() {
        return UNCACHED;
    }

}
