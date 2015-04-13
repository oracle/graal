/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.java;

import static com.oracle.graal.java.HIRFrameStateBuilder.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.java.GraphBuilderPhase.Instance.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;

/**
 * Context for a replacement being inlined as a compiler intrinsic. Deoptimization within a compiler
 * intrinsic must replay the intrinsified call. This context object retains the information required
 * to build a frame state denoting the JVM state just before the intrinsified call.
 */
public class IntrinsicContext extends ReplacementContext {

    /**
     * BCI denoting an intrinsic is being parsed for inlining after the caller has been parsed.
     */
    public static final int POST_PARSE_INLINE_BCI = -1;

    /**
     * BCI denoting an intrinsic is the compilation root.
     */
    public static final int ROOT_COMPILATION_BCI = -2;

    /**
     * The arguments to the intrinsic.
     */
    ValueNode[] args;

    /**
     * The BCI of the intrinsified invocation, {@link #POST_PARSE_INLINE_BCI} or
     * {@link #ROOT_COMPILATION_BCI}.
     */
    final int bci;

    private FrameState stateBeforeCache;

    public IntrinsicContext(ResolvedJavaMethod method, ResolvedJavaMethod substitute, ValueNode[] args, int bci) {
        super(method, substitute);
        assert bci != POST_PARSE_INLINE_BCI || args == null;
        this.args = args;
        this.bci = bci;
        assert !isCompilationRoot() || method.hasBytecodes() : "Cannot intrinsic for native or abstract method " + method.format("%H.%n(%p)");
    }

    @Override
    public boolean isIntrinsic() {
        return true;
    }

    public boolean isPostParseInlined() {
        return bci == POST_PARSE_INLINE_BCI;
    }

    public boolean isCompilationRoot() {
        return bci == ROOT_COMPILATION_BCI;
    }

    public FrameState getInvokeStateBefore(StructuredGraph graph, BytecodeParser parent) {
        if (isCompilationRoot()) {
            int maxLocals = method.getMaxLocals();
            // The 'args' were initialized based on the intrinsic method but a
            // frame state's 'locals' needs to have the same length as the frame
            // state method's 'max_locals'.
            ValueNode[] locals = maxLocals == args.length ? args : Arrays.copyOf(args, maxLocals);
            ValueNode[] stack = EMPTY_ARRAY;
            int stackSize = 0;
            ValueNode[] locks = EMPTY_ARRAY;
            List<MonitorIdNode> monitorIds = Collections.emptyList();
            return graph.add(new FrameState(null, method, 0, locals, stack, stackSize, locks, monitorIds, false, false));
        } else if (isPostParseInlined()) {
            return graph.add(new FrameState(BytecodeFrame.BEFORE_BCI));
        } else {
            assert !parent.parsingReplacement() || parent.replacementContext instanceof IntrinsicContext;
            if (stateBeforeCache == null) {
                assert stateBeforeCache == null;

                // Find the non-intrinsic ancestor calling the intrinsified method
                BytecodeParser ancestor = parent;
                while (ancestor.parsingReplacement()) {
                    assert ancestor.replacementContext instanceof IntrinsicContext;
                    ancestor = ancestor.getParent();
                }
                FrameState stateDuring = ancestor.getFrameState().create(ancestor.bci(), ancestor.getParent(), true);
                stateBeforeCache = stateDuring.duplicateModifiedBeforeCall(bci, Kind.Void, args);
            }
            return stateBeforeCache;
        }
    }

    @Override
    IntrinsicContext asIntrinsic() {
        return this;
    }

    @Override
    public String toString() {
        return "Intrinsic{original: " + method.format("%H.%n(%p)") + ", replacement: " + replacement.format("%H.%n(%p)") + ", bci: " + bci + (args == null ? "" : ", args: " + Arrays.toString(args)) +
                        (stateBeforeCache == null ? "" : ", stateBefore: " + stateBeforeCache) + "}";
    }
}
