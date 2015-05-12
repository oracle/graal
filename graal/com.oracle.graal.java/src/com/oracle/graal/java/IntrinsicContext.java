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

import static com.oracle.graal.api.code.BytecodeFrame.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;

/**
 * Context for a replacement being inlined as a compiler intrinsic.
 */
public class IntrinsicContext extends ReplacementContext {

    /**
     * BCI denoting an intrinsic is being parsed for inlining after the graph of the caller has been
     * built.
     */
    public static final int POST_PARSE_INLINE_BCI = -1;

    /**
     * BCI denoting an intrinsic is the compilation root.
     */
    public static final int ROOT_COMPILATION_BCI = -2;

    /**
     * The BCI of the intrinsified invocation, {@link #POST_PARSE_INLINE_BCI} or
     * {@link #ROOT_COMPILATION_BCI}.
     */
    final int bci;

    public IntrinsicContext(ResolvedJavaMethod method, ResolvedJavaMethod substitute, int bci) {
        super(method, substitute);
        this.bci = bci;
        assert !isCompilationRoot() || method.hasBytecodes() : "Cannot root compile intrinsic for native or abstract method " + method.format("%H.%n(%p)");
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

    public FrameState createFrameState(StructuredGraph graph, HIRFrameStateBuilder frameState, StateSplit forStateSplit) {
        assert forStateSplit != graph.start();
        if (forStateSplit.hasSideEffect()) {
            if (frameState.lastSideEffects != null) {
                // Only the last side effect on any execution path in a replacement
                // can inherit the stateAfter of the replaced node
                FrameState invalid = graph.add(new FrameState(INVALID_FRAMESTATE_BCI));
                for (StateSplit lastSideEffect : frameState.lastSideEffects) {
                    lastSideEffect.setStateAfter(invalid);
                }
            }
            frameState.addLastSideEffect(forStateSplit);
            return graph.add(new FrameState(AFTER_BCI));
        } else {
            if (forStateSplit instanceof AbstractMergeNode) {
                // Merge nodes always need a frame state
                if (frameState.lastSideEffects != null) {
                    // A merge after one or more side effects
                    return graph.add(new FrameState(AFTER_BCI));
                } else {
                    // A merge before any side effects
                    return graph.add(new FrameState(BEFORE_BCI));
                }
            } else {
                // Other non-side-effects do not need a state
                return null;
            }
        }
    }

    @Override
    IntrinsicContext asIntrinsic() {
        return this;
    }

    @Override
    public String toString() {
        return "Intrinsic{original: " + method.format("%H.%n(%p)") + ", replacement: " + replacement.format("%H.%n(%p)") + ", bci: " + bci + "}";
    }
}
