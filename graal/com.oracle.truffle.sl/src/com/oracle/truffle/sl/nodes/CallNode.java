/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.sl.nodes;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.impl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.sl.runtime.*;

public abstract class CallNode extends TypedNode {

    private static final int INLINE_CACHE_SIZE = 2;

    @Child protected TypedNode functionNode;
    @Child protected ArgumentsNode argumentsNode;

    public CallNode(TypedNode functionNode, ArgumentsNode argumentsNode) {
        this.functionNode = adoptChild(functionNode);
        this.argumentsNode = adoptChild(argumentsNode);
    }

    @Override
    public final Object executeGeneric(VirtualFrame frame) {
        CallTarget function;
        try {
            function = functionNode.executeCallTarget(frame);
        } catch (UnexpectedResultException e) {
            throw new UnsupportedOperationException("Call to " + e.getMessage() + " not supported.");
        }
        Object[] arguments = argumentsNode.executeArray(frame);
        return executeCall(frame, function, arguments);
    }

    public abstract Object executeCall(VirtualFrame frame, CallTarget function, Object[] arguments);

    public static CallNode create(TypedNode function, TypedNode[] arguments) {
        return new UninitializedCallNode(function, new ArgumentsNode(arguments), 0);
    }

    private static final class UninitializedCallNode extends CallNode {

        protected final int depth;

        UninitializedCallNode(TypedNode function, ArgumentsNode args, int depth) {
            super(function, args);
            this.depth = depth;
        }

        UninitializedCallNode(UninitializedCallNode copy) {
            super(null, null);
            this.depth = copy.depth + 1;
        }

        @Override
        public Object executeCall(VirtualFrame frame, CallTarget function, Object[] arguments) {
            CompilerDirectives.transferToInterpreter();
            return specialize(function).executeCall(frame, function, arguments);
        }

        private CallNode specialize(CallTarget function) {
            CompilerAsserts.neverPartOfCompilation();

            if (depth < INLINE_CACHE_SIZE) {
                DefaultCallTarget callTarget = (DefaultCallTarget) function;
                FunctionRootNode root = (FunctionRootNode) callTarget.getRootNode();
                CallNode next = new UninitializedCallNode(this);
                InlinableDirectCallNode directCall = new InlinableDirectCallNode(functionNode, argumentsNode, next, callTarget);
                replace(directCall);
                if (root.isInlineImmediatly()) {
                    return directCall.inlineImpl();
                } else {
                    return directCall;
                }
            } else {
                CallNode topMost = (CallNode) NodeUtil.getNthParent(this, depth);
                return topMost.replace(new GenericCallNode(topMost.functionNode, topMost.argumentsNode));
            }
        }

    }

    private abstract static class DirectCallNode extends CallNode {

        protected final DefaultCallTarget cachedFunction;

        @Child protected CallNode nextNode;

        public DirectCallNode(TypedNode function, ArgumentsNode arguments, DefaultCallTarget cachedFunction, CallNode next) {
            super(function, arguments);
            this.cachedFunction = cachedFunction;
            this.nextNode = adoptChild(next);
        }

        @Override
        public Object executeCall(VirtualFrame frame, CallTarget function, Object[] arguments) {
            if (this.cachedFunction == function) {
                return executeCurrent(frame, arguments);
            }
            return nextNode.executeCall(frame, function, arguments);
        }

        protected abstract Object executeCurrent(VirtualFrame frame, Object[] arguments);

    }

    private static final class InlinableDirectCallNode extends DirectCallNode implements InlinableCallSite {

        @CompilationFinal private int callCount;

        InlinableDirectCallNode(TypedNode function, ArgumentsNode arguments, CallNode next, DefaultCallTarget cachedFunction) {
            super(function, arguments, cachedFunction, next);
        }

        @Override
        public Object executeCurrent(VirtualFrame frame, Object[] arguments) {
            if (CompilerDirectives.inInterpreter()) {
                callCount++;
            }
            return cachedFunction.call(frame.pack(), new SLArguments(arguments));
        }

        InlinedDirectCallNode inlineImpl() {
            CompilerAsserts.neverPartOfCompilation();
            RootNode root = cachedFunction.getRootNode();
            TypedNode inlinedNode = ((FunctionRootNode) root).inline();
            assert inlinedNode != null;
            return replace(new InlinedDirectCallNode(this, inlinedNode), "Inlined " + root);
        }

        @Override
        public boolean inline(FrameFactory factory) {
            inlineImpl();
            /* SL is always able to inline if required. */
            return true;
        }

        @Override
        public int getCallCount() {
            return callCount;
        }

        @Override
        public void resetCallCount() {
            callCount = 0;
        }

        @Override
        public Node getInlineTree() {
            RootNode root = cachedFunction.getRootNode();
            if (root instanceof FunctionRootNode) {
                return ((FunctionRootNode) root).getUninitializedBody();
            }
            return null;
        }

        @Override
        public CallTarget getCallTarget() {
            return cachedFunction;
        }

    }

    private static class InlinedDirectCallNode extends DirectCallNode implements InlinedCallSite {

        private final FrameDescriptor descriptor;
        @Child private TypedNode inlinedBody;

        InlinedDirectCallNode(InlinableDirectCallNode prev, TypedNode inlinedBody) {
            super(prev.functionNode, prev.argumentsNode, prev.cachedFunction, prev.nextNode);
            this.descriptor = cachedFunction.getFrameDescriptor();
            this.inlinedBody = adoptChild(inlinedBody);
        }

        @Override
        public Object executeCurrent(VirtualFrame frame, Object[] arguments) {
            SLArguments slArguments = new SLArguments(arguments);
            VirtualFrame newFrame = Truffle.getRuntime().createVirtualFrame(frame.pack(), slArguments, descriptor);
            return inlinedBody.executeGeneric(newFrame);
        }

        @Override
        public CallTarget getCallTarget() {
            return cachedFunction;
        }

    }

    private static final class GenericCallNode extends CallNode {

        GenericCallNode(TypedNode functionNode, ArgumentsNode arguments) {
            super(functionNode, arguments);
        }

        @Override
        public Object executeCall(VirtualFrame frame, CallTarget function, Object[] arguments) {
            return function.call(frame.pack(), new SLArguments(arguments));
        }
    }

}
