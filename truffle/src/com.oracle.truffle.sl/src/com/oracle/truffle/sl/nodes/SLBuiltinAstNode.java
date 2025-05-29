/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.nodes;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.sl.SLException;
import com.oracle.truffle.sl.builtins.SLBuiltinNode;
import com.oracle.truffle.sl.runtime.SLNull;

public abstract class SLBuiltinAstNode extends SLExpressionNode {

    final int argumentCount;
    @Child private SLBuiltinNode builtin;

    SLBuiltinAstNode(int argumentCount, SLBuiltinNode builtinNode) {
        this.argumentCount = argumentCount;
        this.builtin = builtinNode;
    }

    @Override
    public final Object executeGeneric(VirtualFrame frame) {
        try {
            return executeImpl(frame);
        } catch (UnsupportedSpecializationException e) {
            throw SLException.typeError(e.getNode(), e.getSuppliedValues());
        }
    }

    public abstract Object executeImpl(VirtualFrame frame);

    @Specialization(guards = "arguments.length == argumentCount")
    @SuppressWarnings("unused")
    final Object doInBounds(VirtualFrame frame,
                    @Bind("frame.getArguments()") Object[] arguments) {
        return builtin.execute(frame, arguments);
    }

    @Fallback
    @ExplodeLoop
    final Object doOutOfBounds(VirtualFrame frame) {
        Object[] originalArguments = frame.getArguments();
        Object[] arguments = new Object[argumentCount];
        for (int i = 0; i < argumentCount; i++) {
            if (i < originalArguments.length) {
                arguments[i] = originalArguments[i];
            } else {
                arguments[i] = SLNull.SINGLETON;
            }
        }
        return builtin.execute(frame, arguments);
    }

}
