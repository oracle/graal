/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.nodes.call;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.sl.nodes.SLExpressionNode;
import com.oracle.truffle.sl.nodes.interop.SLForeignToSLTypeNode;
import com.oracle.truffle.sl.nodes.interop.SLForeignToSLTypeNodeGen;
import com.oracle.truffle.sl.runtime.SLFunction;
import com.oracle.truffle.sl.runtime.SLNull;

/**
 * The node for function invocation in SL. Since SL has first class functions, the {@link SLFunction
 * target function} can be computed by an arbitrary expression. This node is responsible for
 * evaluating this expression, as well as evaluating the {@link #argumentNodes arguments}. The
 * actual dispatch is then delegated to a chain of {@link SLDispatchNode} that form a polymorphic
 * inline cache.
 */
@NodeInfo(shortName = "invoke")
@NodeChildren({@NodeChild(value = "functionNode", type = SLExpressionNode.class)})
public abstract class SLInvokeNode extends SLExpressionNode {
    @Children private final SLExpressionNode[] argumentNodes;
    @Child private SLDispatchNode dispatchNode;

    SLInvokeNode(SLExpressionNode[] argumentNodes) {
        this.argumentNodes = argumentNodes;
        this.dispatchNode = SLDispatchNodeGen.create();
    }

    @Specialization
    @ExplodeLoop
    public Object executeGeneric(VirtualFrame frame, SLFunction function) {
        /*
         * The number of arguments is constant for one invoke node. During compilation, the loop is
         * unrolled and the execute methods of all arguments are inlined. This is triggered by the
         * ExplodeLoop annotation on the method. The compiler assertion below illustrates that the
         * array length is really constant.
         */
        CompilerAsserts.compilationConstant(argumentNodes.length);

        Object[] argumentValues = new Object[argumentNodes.length];
        for (int i = 0; i < argumentNodes.length; i++) {
            argumentValues[i] = argumentNodes[i].executeGeneric(frame);
        }
        return dispatchNode.executeDispatch(frame, function, argumentValues);
    }

    /*
     * The child node to call the foreign function.
     */
    @Child private Node crossLanguageCall;

    /*
     * The child node to convert the result of the foreign function call to an SL value.
     */
    @Child private SLForeignToSLTypeNode toSLType;

    /*
     * If the receiver object (i.e., the function object) is a foreign value we use Truffle's
     * interop API to execute the foreign function.
     */
    @Specialization
    @ExplodeLoop
    protected Object executeGeneric(VirtualFrame frame, TruffleObject function) {
        /*
         * The number of arguments is constant for one invoke node. During compilation, the loop is
         * unrolled and the execute methods of all arguments are inlined. This is triggered by the
         * ExplodeLoop annotation on the method. The compiler assertion below illustrates that the
         * array length is really constant.
         */
        CompilerAsserts.compilationConstant(argumentNodes.length);

        Object[] argumentValues = new Object[argumentNodes.length];
        for (int i = 0; i < argumentNodes.length; i++) {
            argumentValues[i] = argumentNodes[i].executeGeneric(frame);
        }

        // Lazily insert the foreign object access nodes upon the first execution.
        if (crossLanguageCall == null) {
            // SL maps a function invocation to an EXECUTE message.
            CompilerDirectives.transferToInterpreterAndInvalidate();
            crossLanguageCall = insert(Message.createExecute(argumentValues.length).createNode());
            toSLType = insert(SLForeignToSLTypeNodeGen.create(null));
        }
        try {
            // Perform the foreign function call.
            Object res = ForeignAccess.sendExecute(crossLanguageCall, frame, function, argumentValues);
            // Convert the result to an SL value.
            Object slValue = toSLType.executeWithTarget(frame, res);
            return slValue;
        } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e) {
            // In case the foreign function call is not successful, we return null.
            return SLNull.SINGLETON;
        }
    }

    @Override
    protected boolean isTaggedWith(Class<?> tag) {
        if (tag == StandardTags.CallTag.class) {
            return true;
        }
        return super.isTaggedWith(tag);
    }

}
