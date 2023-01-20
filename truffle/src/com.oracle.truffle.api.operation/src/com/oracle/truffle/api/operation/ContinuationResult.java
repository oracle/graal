/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.operation;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

public final class ContinuationResult {

    final ContinuationLocation location;
    final MaterializedFrame frame;
    private final Object result;

    ContinuationResult(ContinuationLocation location, MaterializedFrame frame, Object result) {
        this.location = location;
        this.frame = frame;
        this.result = result;
    }

    public Object continueWith(Object value) {
        return location.getRootNode().getCallTarget().call(frame, value);
    }

    public Object getResult() {
        return result;
    }

    @Override
    public String toString() {
        return String.format("ContinuationResult [location=%s, result=%s]", location, result);
    }

    @GenerateInline(true)
    public abstract static class ContinueNode extends Node {

        public final Object execute(ContinuationResult result, Object value) {
            return execute(null, result, value);
        }

        public abstract Object execute(Node node, ContinuationResult result, Object value);

        public static final int LIMIT = 3;

        @SuppressWarnings("unused")
        @Specialization(guards = {"result.location.getRootNode() == rootNode"}, limit = "LIMIT")
        public static Object invokeDirect(ContinuationResult result, Object value,
                        @Cached(value = "result.location.getRootNode()", inline = false) RootNode rootNode,
                        @Cached(value = "create(rootNode.getCallTarget())", inline = false) DirectCallNode callNode) {
            return callNode.call(result.frame, value);
        }

        @Specialization(replaces = "invokeDirect")
        public static Object invokeIndirect(ContinuationResult result, Object value,
                        @Cached(inline = false) IndirectCallNode callNode) {
            return callNode.call(result.location.getRootNode().getCallTarget(), result.frame, value);
        }
    }
}
