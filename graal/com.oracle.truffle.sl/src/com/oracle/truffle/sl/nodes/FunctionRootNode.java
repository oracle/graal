/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.sl.builtins.*;
import com.oracle.truffle.sl.runtime.*;

public final class FunctionRootNode extends RootNode {

    @Child private TypedNode body;

    private final TypedNode uninitializedBody;
    private final String name;
    private final boolean alwaysInline;

    private FunctionRootNode(TypedNode body, String name, boolean alwaysInline) {
        super(null);
        this.uninitializedBody = NodeUtil.cloneNode(body);
        this.body = adoptChild(body);
        this.name = name;
        this.alwaysInline = alwaysInline;
    }

    public static CallTarget createBuiltin(SLContext context, NodeFactory<? extends BuiltinNode> factory, String name) {
        int argumentCount = factory.getExecutionSignature().size();
        TypedNode[] arguments = new TypedNode[argumentCount];
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = new ReadArgumentNode(i);
        }
        BuiltinNode buitinBody = factory.createNode(arguments, context);
        FunctionRootNode root = new FunctionRootNode(buitinBody, name, true);
        return Truffle.getRuntime().createCallTarget(root);
    }

    public static CallTarget createFunction(StatementNode body, FrameDescriptor frameDescriptor, String name, TypedNode returnValue, String[] parameterNames) {
        FunctionBodyNode bodyContainer = new FunctionBodyNode(frameDescriptor, body, returnValue, parameterNames);
        FunctionRootNode root = new FunctionRootNode(bodyContainer, name, false);
        return Truffle.getRuntime().createCallTarget(root, frameDescriptor);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return body.executeGeneric(frame);
    }

    public boolean isAlwaysInline() {
        return alwaysInline;
    }

    public TypedNode inline(ArgumentsNode clonedArgs) {
        TypedNode clonedBody = NodeUtil.cloneNode(uninitializedBody);
        if (clonedBody instanceof BuiltinNode) {
            return inlineBuiltin(clonedArgs, (BuiltinNode) clonedBody);
        } else if (clonedBody instanceof FunctionBodyNode) {
            return inlineFunction(clonedArgs, (FunctionBodyNode) clonedBody);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    private InlinedFunctionNode inlineFunction(ArgumentsNode clonedArgs, FunctionBodyNode clonedBody) {
        return new InlinedFunctionNode(getCallTarget(), clonedBody, clonedArgs);
    }

    private static TypedNode inlineBuiltin(ArgumentsNode clonedArgs, BuiltinNode builtin) {
        TypedNode[] callerArgs = clonedArgs.getArguments();
        TypedNode[] builtinArgs = builtin.getArguments();
        for (int i = 0; i < builtinArgs.length; i++) {
            if (i < callerArgs.length) {
                builtinArgs[i].replace(callerArgs[i]);
            } else {
                builtinArgs[i].replace(new NullLiteralNode());
            }
        }
        return builtin;
    }

    public Node getUninitializedBody() {
        return uninitializedBody;
    }

    @Override
    public String toString() {
        return "function " + name;
    }

    private static final class InlinedFunctionNode extends TypedNode implements InlinedCallSite {

        @Child private FunctionBodyNode body;
        @Child private ArgumentsNode arguments;

        private final CallTarget callTarget;
        private final FrameDescriptor frameDescriptor;

        public InlinedFunctionNode(CallTarget callTarget, FunctionBodyNode body, ArgumentsNode arguments) {
            this.callTarget = callTarget;
            this.body = adoptChild(body);
            this.frameDescriptor = body.getFrameDescriptor();
            this.arguments = adoptChild(arguments);
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            SLArguments args = new SLArguments(arguments.executeArray(frame));
            VirtualFrame childFrame = Truffle.getRuntime().createVirtualFrame(frame.pack(), args, frameDescriptor);
            return body.executeGeneric(childFrame);
        }

        public CallTarget getCallTarget() {
            return callTarget;
        }

    }

    public String getName() {
        return name;
    }

}
