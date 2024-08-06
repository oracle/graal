/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.runtime;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.NodeInfo;

/**
 * A call node with a dynamic {@link CallTarget} that can be optimized by Graal.
 */
@NodeInfo
public final class OptimizedIndirectCallNode extends IndirectCallNode {

    @CompilationFinal private Class<? extends Throwable> exceptionProfile;

    /*
     * Should be instantiated with the runtime.
     */
    OptimizedIndirectCallNode() {
    }

    @Override
    public Object call(CallTarget target, Object... arguments) {
        try {
            OptimizedCallTarget optimizedTarget = ((OptimizedCallTarget) target);
            if (CompilerDirectives.isPartialEvaluationConstant(optimizedTarget)) {
                return optimizedTarget.callDirect(this, arguments);
            } else {
                return optimizedTarget.callIndirect(this, arguments);
            }
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    private RuntimeException handleException(Throwable t) {
        Throwable profiled = profileExceptionType(t);
        OptimizedRuntimeAccessor.LANGUAGE.addStackFrameInfo(this, null, profiled, null);
        throw OptimizedCallTarget.rethrow(profiled);
    }

    @SuppressWarnings("unchecked")
    private <T extends Throwable> T profileExceptionType(T value) {
        Class<? extends Throwable> clazz = exceptionProfile;
        if (clazz != Throwable.class) {
            if (clazz != null && value.getClass() == clazz) {
                if (CompilerDirectives.inInterpreter()) {
                    return value;
                } else {
                    return (T) CompilerDirectives.castExact(value, clazz);
                }
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (clazz == null) {
                    exceptionProfile = value.getClass();
                } else {
                    exceptionProfile = Throwable.class;
                }
            }
        }
        return value;
    }
}
