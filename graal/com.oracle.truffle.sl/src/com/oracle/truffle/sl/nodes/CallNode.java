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

    private CallNode(TypedNode functionNode) {
        this.functionNode = adoptChild(functionNode);
    }

    private CallTarget executeCallTargetNode(VirtualFrame frame) {
        try {
            return functionNode.executeCallTarget(frame);
        } catch (UnexpectedResultException e) {
            throw new UnsupportedOperationException("Call to " + e.getMessage() + " not supported.");
        }
    }

    @Override
    public final Object executeGeneric(VirtualFrame frame) {
        return executeGeneric(frame, executeCallTargetNode(frame));
    }

    public abstract Object executeGeneric(VirtualFrame frame, CallTarget function);

    public static CallNode create(TypedNode function, TypedNode[] arguments) {
        return new UninitializedCallNode(function, new ArgumentsNode(arguments), 0);
    }

    private static final class CachedCallNode extends CallNode {

        @Child protected CallNode nextNode;
        @Child protected TypedNode currentNode;
        private final CallTarget cachedFunction;

        public CachedCallNode(TypedNode function, TypedNode current, CallNode next, CallTarget cachedFunction) {
            super(function);
            this.currentNode = adoptChild(current);
            this.nextNode = adoptChild(next);
            this.cachedFunction = cachedFunction;
        }

        @Override
        public Object executeGeneric(VirtualFrame frame, CallTarget function) {
            if (this.cachedFunction == function) {
                return currentNode.executeGeneric(frame);
            }
            return nextNode.executeGeneric(frame, function);
        }
    }

    private static final class UninitializedCallNode extends CallNode {

        @Child protected ArgumentsNode uninitializedArgs;
        protected final int depth;

        UninitializedCallNode(TypedNode function, ArgumentsNode args, int depth) {
            super(function);
            this.uninitializedArgs = adoptChild(args);
            this.depth = depth;
        }

        UninitializedCallNode(UninitializedCallNode copy) {
            super(null);
            this.uninitializedArgs = adoptChild(copy.uninitializedArgs);
            this.depth = copy.depth + 1;
        }

        @Override
        public Object executeGeneric(VirtualFrame frame, CallTarget function) {
            CompilerDirectives.transferToInterpreter();
            return specialize(function).executeGeneric(frame, function);
        }

        private CallNode specialize(CallTarget function) {
            CompilerAsserts.neverPartOfCompilation();

            if (depth < INLINE_CACHE_SIZE) {
                TypedNode current = createCacheNode(function);
                CallNode next = new UninitializedCallNode(this);
                return replace(new CachedCallNode(this.functionNode, current, next, function));
            } else {
                CallNode topMost = (CallNode) getTopNode();
                return topMost.replace(new GenericCallNode(topMost.functionNode, uninitializedArgs));
            }
        }

        protected Node getTopNode() {
            Node parentNode = this;
            for (int i = 0; i < depth; i++) {
                parentNode = parentNode.getParent();
            }
            return parentNode;
        }

        protected TypedNode createCacheNode(CallTarget function) {
            ArgumentsNode clonedArgs = NodeUtil.cloneNode(uninitializedArgs);

            if (function instanceof DefaultCallTarget) {
                DefaultCallTarget defaultFunction = (DefaultCallTarget) function;
                RootNode rootNode = defaultFunction.getRootNode();
                if (rootNode instanceof FunctionRootNode) {
                    FunctionRootNode root = (FunctionRootNode) rootNode;
                    if (root.isAlwaysInline()) {
                        TypedNode inlinedCall = root.inline(clonedArgs);
                        if (inlinedCall != null) {
                            return inlinedCall;
                        }
                    }
                    return new InlinableCallNode((DefaultCallTarget) function, clonedArgs);
                }
            }

            // got a call target that is not inlinable (should not occur for SL)
            return new DispatchedCallNode(function, clonedArgs);
        }
    }

    private static final class InlinableCallNode extends DispatchedCallNode implements InlinableCallSite {

        private final DefaultCallTarget inlinableTarget;

        @CompilationFinal private int callCount;

        InlinableCallNode(DefaultCallTarget function, ArgumentsNode arguments) {
            super(function, arguments);
            this.inlinableTarget = function;
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
            RootNode root = inlinableTarget.getRootNode();
            if (root instanceof FunctionRootNode) {
                return ((FunctionRootNode) root).getUninitializedBody();
            }
            return null;
        }

        @Override
        public boolean inline(FrameFactory factory) {
            CompilerAsserts.neverPartOfCompilation();
            TypedNode functionCall = null;

            RootNode root = inlinableTarget.getRootNode();
            if (root instanceof FunctionRootNode) {
                functionCall = ((FunctionRootNode) root).inline(NodeUtil.cloneNode(args));
            }
            if (functionCall != null) {
                this.replace(functionCall);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            if (CompilerDirectives.inInterpreter()) {
                callCount++;
            }
            return super.executeGeneric(frame);
        }

        @Override
        public CallTarget getCallTarget() {
            return inlinableTarget;
        }

    }

    private static class DispatchedCallNode extends TypedNode {

        @Child protected ArgumentsNode args;
        protected final CallTarget function;

        DispatchedCallNode(CallTarget function, ArgumentsNode arguments) {
            this.args = adoptChild(arguments);
            this.function = function;
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            SLArguments argsObject = new SLArguments(args.executeArray(frame));
            return function.call(frame.pack(), argsObject);
        }
    }

    private static final class GenericCallNode extends CallNode {

        @Child protected ArgumentsNode args;

        GenericCallNode(TypedNode functionNode, ArgumentsNode arguments) {
            super(functionNode);
            this.args = adoptChild(arguments);
        }

        @Override
        public Object executeGeneric(VirtualFrame frame, CallTarget function) {
            SLArguments argsObject = new SLArguments(args.executeArray(frame));
            return function.call(frame.pack(), argsObject);
        }
    }

}
