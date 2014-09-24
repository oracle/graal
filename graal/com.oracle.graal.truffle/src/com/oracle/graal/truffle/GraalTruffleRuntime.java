/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle;

import static com.oracle.graal.truffle.TruffleCompilerOptions.*;

import java.util.*;
import java.util.concurrent.*;

import com.oracle.graal.api.code.stack.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

public abstract class GraalTruffleRuntime implements TruffleRuntime {

    private ArrayList<String> includes;
    private ArrayList<String> excludes;

    private StackIntrospection stackIntrospection;
    protected ResolvedJavaMethod[] callNodeMethod;
    protected ResolvedJavaMethod[] callTargetMethod;
    protected ResolvedJavaMethod[] anyFrameMethod;

    protected void lookupCallMethods(MetaAccessProvider metaAccess) {
        callNodeMethod = new ResolvedJavaMethod[]{metaAccess.lookupJavaMethod(GraalFrameInstance.CallNodeFrame.METHOD)};
        callTargetMethod = new ResolvedJavaMethod[]{metaAccess.lookupJavaMethod(GraalFrameInstance.CallTargetFrame.METHOD)};
        anyFrameMethod = new ResolvedJavaMethod[]{callNodeMethod[0], callTargetMethod[0]};
    }

    @Override
    public LoopNode createLoopNode(RepeatingNode repeating) {
        if (!(repeating instanceof Node)) {
            throw new IllegalArgumentException("Repeating node must be of type Node.");
        }
        return new OptimizedLoopNode(repeating);
    }

    @Override
    public DirectCallNode createDirectCallNode(CallTarget target) {
        if (target instanceof OptimizedCallTarget) {
            return new OptimizedDirectCallNode((OptimizedCallTarget) target);
        } else {
            throw new IllegalStateException(String.format("Unexpected call target class %s!", target.getClass()));
        }
    }

    @Override
    public IndirectCallNode createIndirectCallNode() {
        return new OptimizedIndirectCallNode();
    }

    @Override
    public VirtualFrame createVirtualFrame(Object[] arguments, FrameDescriptor frameDescriptor) {
        return OptimizedCallTarget.createFrame(frameDescriptor, arguments);
    }

    @Override
    public MaterializedFrame createMaterializedFrame(Object[] arguments) {
        return createMaterializedFrame(arguments, new FrameDescriptor());
    }

    @Override
    public MaterializedFrame createMaterializedFrame(Object[] arguments, FrameDescriptor frameDescriptor) {
        return new FrameWithoutBoxing(frameDescriptor, arguments);
    }

    @Override
    public Assumption createAssumption() {
        return createAssumption(null);
    }

    @Override
    public Assumption createAssumption(String name) {
        return new OptimizedAssumption(name);
    }

    @SlowPath
    @Override
    public <T> T iterateFrames(FrameInstanceVisitor<T> visitor) {
        initStackIntrospection();

        InspectedFrameVisitor<T> inspectedFrameVisitor = new InspectedFrameVisitor<T>() {
            private boolean skipNext = false;

            public T visitFrame(InspectedFrame frame) {
                if (skipNext) {
                    assert frame.isMethod(callTargetMethod[0]);
                    skipNext = false;
                    return null;
                }

                if (frame.isMethod(callNodeMethod[0])) {
                    skipNext = true;
                    return visitor.visitFrame(new GraalFrameInstance.CallNodeFrame(frame));
                } else {
                    assert frame.isMethod(callTargetMethod[0]);
                    return visitor.visitFrame(new GraalFrameInstance.CallTargetFrame(frame, false));
                }

            }
        };
        return stackIntrospection.iterateFrames(anyFrameMethod, anyFrameMethod, 1, inspectedFrameVisitor);
    }

    private void initStackIntrospection() {
        if (stackIntrospection == null) {
            stackIntrospection = Graal.getRequiredCapability(StackIntrospection.class);
        }
    }

    @Override
    public FrameInstance getCallerFrame() {
        return iterateFrames(frame -> frame);
    }

    @SlowPath
    @Override
    public FrameInstance getCurrentFrame() {
        initStackIntrospection();

        return stackIntrospection.iterateFrames(callTargetMethod, callTargetMethod, 0, frame -> new GraalFrameInstance.CallTargetFrame(frame, true));
    }

    protected boolean acceptForCompilation(RootNode rootNode) {
        if (TruffleCompileOnly.getValue() != null) {
            if (includes == null) {
                parseCompileOnly();
            }

            String name = rootNode.toString();
            boolean included = includes.isEmpty();
            for (int i = 0; !included && i < includes.size(); i++) {
                if (name.contains(includes.get(i))) {
                    included = true;
                }
            }
            if (!included) {
                return false;
            }
            for (String exclude : excludes) {
                if (name.contains(exclude)) {
                    return false;
                }
            }
        }
        return true;
    }

    protected void parseCompileOnly() {
        includes = new ArrayList<>();
        excludes = new ArrayList<>();

        String[] items = TruffleCompileOnly.getValue().split(",");
        for (String item : items) {
            if (item.startsWith("~")) {
                excludes.add(item.substring(1));
            } else {
                includes.add(item);
            }
        }
    }

    public abstract Replacements getReplacements();

    public abstract void compile(OptimizedCallTarget optimizedCallTarget, boolean mayBeAsynchronous);

    public abstract boolean cancelInstalledTask(OptimizedCallTarget optimizedCallTarget);

    public abstract void waitForCompilation(OptimizedCallTarget optimizedCallTarget, long timeout) throws ExecutionException, TimeoutException;

    public abstract boolean isCompiling(OptimizedCallTarget optimizedCallTarget);

    public abstract void invalidateInstalledCode(OptimizedCallTarget optimizedCallTarget);

    public abstract void reinstallStubs();
}
