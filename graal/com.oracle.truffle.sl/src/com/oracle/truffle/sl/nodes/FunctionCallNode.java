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
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.impl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.sl.runtime.*;

public abstract class FunctionCallNode extends TypedNode {

    private static final int INLINE_CACHE_SIZE = 2;

    @Child protected TypedNode functionNode;
    @Child protected ArgumentsNode argumentsNode;

    public FunctionCallNode(TypedNode functionNode, ArgumentsNode argumentsNode) {
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
        return executeCall(frame, function, new SLArguments(arguments));
    }

    public abstract Object executeCall(VirtualFrame frame, CallTarget function, SLArguments arguments);

    public static FunctionCallNode create(TypedNode function, TypedNode[] arguments) {
        return new UninitializedCallNode(function, new ArgumentsNode(arguments), 0);
    }

    private static final class UninitializedCallNode extends FunctionCallNode {

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
        public Object executeCall(VirtualFrame frame, CallTarget function, SLArguments arguments) {
            CompilerDirectives.transferToInterpreter();
            return specialize(function).executeCall(frame, function, arguments);
        }

        private FunctionCallNode specialize(CallTarget function) {
            CompilerAsserts.neverPartOfCompilation();
            if (depth < INLINE_CACHE_SIZE) {
                return replace(new CachedCallNode(functionNode, argumentsNode, function, new UninitializedCallNode(this)));
            } else {
                FunctionCallNode topMost = (FunctionCallNode) NodeUtil.getNthParent(this, depth);
                return topMost.replace(new GenericCallNode(topMost.functionNode, topMost.argumentsNode));
            }
        }

    }

    private static final class CachedCallNode extends FunctionCallNode {

        protected final CallTarget cachedFunction;

        @Child protected CallNode callNode;
        @Child protected FunctionCallNode nextNode;

        public CachedCallNode(TypedNode function, ArgumentsNode arguments, CallTarget cachedFunction, FunctionCallNode next) {
            super(function, arguments);
            this.cachedFunction = cachedFunction;
            this.callNode = adoptChild(CallNode.create(cachedFunction));
            this.nextNode = adoptChild(next);

            // inline usually known functions that should always be inlined
            if (findSLFunctionRoot(cachedFunction).isInlineImmediatly()) {
                if (callNode.isInlinable() && !callNode.isInlined()) {
                    callNode.inline();
                }
            }
        }

        @Override
        public Object executeCall(VirtualFrame frame, CallTarget function, SLArguments arguments) {
            if (this.cachedFunction == function) {
                return callNode.call(frame.pack(), arguments);
            }
            return nextNode.executeCall(frame, function, arguments);
        }

        private static FunctionRootNode findSLFunctionRoot(CallTarget target) {
            return (FunctionRootNode) ((DefaultCallTarget) target).getRootNode();
        }

    }

    private static final class GenericCallNode extends FunctionCallNode {

        GenericCallNode(TypedNode functionNode, ArgumentsNode arguments) {
            super(functionNode, arguments);
        }

        @Override
        public Object executeCall(VirtualFrame frame, CallTarget function, SLArguments arguments) {
            return function.call(frame.pack(), arguments);
        }
    }

}
