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
package com.oracle.truffle.sl.nodes.expression;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.nodes.SLExpressionNode;
import com.oracle.truffle.sl.runtime.SLContext;
import com.oracle.truffle.sl.runtime.SLFunction;
import com.oracle.truffle.sl.runtime.SLFunctionRegistry;

/**
 * Constant literal for a {@link SLFunction function} value, created when a function name occurs as
 * a literal in SL source code. Note that function redefinition can change the {@link CallTarget
 * call target} that is executed when calling the function, but the {@link SLFunction} for a name
 * never changes. This is guaranteed by the {@link SLFunctionRegistry}.
 */
@NodeInfo(shortName = "func")
@NodeChild("functionName")
@OperationProxy.Proxyable(allowUncached = true)
public abstract class SLFunctionLiteralNode extends SLExpressionNode {

    @SuppressWarnings({"unused", "truffle-neverdefault"})
    @Specialization
    public static SLFunction perform(
                    TruffleString functionName,
                    @Bind Node node,
                    @Cached(value = "lookupFunctionCached(functionName, node)", //
                                    uncached = "lookupFunction(functionName, node)") SLFunction result) {
        if (result == null) {
            return lookupFunction(functionName, node);
        } else {
            assert result.getName().equals(functionName) : "function name should be compilation constant";
            return result;
        }
    }

    public static SLFunction lookupFunction(TruffleString functionName, Node node) {
        return SLContext.get(node).getFunctionRegistry().lookup(functionName, true);
    }

    public static SLFunction lookupFunctionCached(TruffleString functionName, Node node) {
        if (SLLanguage.get(node).isSingleContext()) {
            return lookupFunction(functionName, node);
        } else {
            return null;
        }
    }
}
