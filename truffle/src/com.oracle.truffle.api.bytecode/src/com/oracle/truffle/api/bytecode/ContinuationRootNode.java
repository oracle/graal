/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Abstract class representing the root node for a continuation.
 * <p>
 * These root nodes have a precise calling convention; see
 * {@link ContinuationResult#getContinuationCallTarget()}.
 * <p>
 * If a bytecode interpreter {@link GenerateBytecode#enableYield supports continuations}, the
 * Bytecode DSL will generate a concrete implementation of this interface. It should not be
 * subclassed manually.
 *
 * @since 24.2
 */
public abstract class ContinuationRootNode extends RootNode {

    /**
     * Internal constructor for generated code. Do not use.
     *
     * @since 24.2
     */
    protected ContinuationRootNode(Object token, TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
        BytecodeRootNodes.checkToken(token);
    }

    /**
     * Returns the original root node from which this continuation was created.
     *
     * @since 24.2
     */
    public abstract BytecodeRootNode getSourceRootNode();

    /**
     * Returns the {@link BytecodeLocation} associated with this continuation.
     *
     * @since 24.2
     */
    public abstract BytecodeLocation getLocation();

    /**
     * Internal method implemented by the generated code. Do not use.
     *
     * @since 24.2
     */
    protected abstract Frame findFrame(Frame frame);

}
