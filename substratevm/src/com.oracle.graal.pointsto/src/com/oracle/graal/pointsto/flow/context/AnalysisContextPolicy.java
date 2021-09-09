/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.graal.pointsto.flow.context;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.MethodTypeFlow;
import com.oracle.graal.pointsto.flow.context.AnalysisContext.AnalysisContextKey;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;

/** Provides logic for analysis context transitions. */
public abstract class AnalysisContextPolicy<C extends AnalysisContext> {

    private final ContextFactory factory;
    private final C emptyContext;

    public AnalysisContextPolicy(C emptyContext) {
        this.factory = new ContextFactory();
        this.emptyContext = factory.lookupContext(emptyContext);
    }

    /** Returns the empty context, i.e., the outer most context. */
    public final C emptyContext() {
        return emptyContext;
    }

    public final boolean isEmpty(C context) {
        return context == emptyContext;
    }

    /**
     * Given the receiver object, caller context and callee this method returns the callee context
     * for a virtual or special invoke.
     */
    public abstract C calleeContext(PointsToAnalysis bb, AnalysisObject receiverObject, C callerContext, MethodTypeFlow callee);

    /**
     * Given the invocation location, caller context and callee this method returns the callee
     * context for a static invoke.
     */
    public abstract C staticCalleeContext(PointsToAnalysis bb, BytecodeLocation invokeLocation, C callerContext, MethodTypeFlow callee);

    /**
     * Given the allocator method context this method returns the allocation context for a heap
     * allocated object.
     */
    public abstract C allocationContext(C allocatorContext, int maxHeapContextDepth);

    /**
     * Peels off the least recent labels from the current context, down to {@code maxDepth} depth.
     * Only the most recent labels are kept in the context, preserving the temporal order.
     */
    public abstract C peel(C context, int maxDepth);

    /**
     * Extends the input label list with a context label. The depth of the context chain is bounded
     * by {@code maxDepth}. Only the most recent context labels are kept in the chain, in the order
     * that they appeared in the call chain.
     */
    public static BytecodeLocation[] extend(BytecodeLocation[] labelList, BytecodeLocation add, int maxDepth) {

        int resultingContextDepth = labelList.length == maxDepth ? maxDepth : labelList.length + 1;
        BytecodeLocation[] resultingLabelList = new BytecodeLocation[resultingContextDepth];

        for (int i = resultingContextDepth - 2, j = labelList.length - 1; i >= 0 && j >= 0; i--, j--) {
            // get only the last 'resultingContextDepth - 1' contexts from the initial chain
            resultingLabelList[i] = labelList[j];
        }
        // append the new label at the end of the chain
        if (resultingContextDepth > 0) {
            resultingLabelList[resultingContextDepth - 1] = add;
        }

        return resultingLabelList;
    }

    /** Extends the input label list with a context label. */
    public static BytecodeLocation[] extend(BytecodeLocation[] labelList, BytecodeLocation add) {
        BytecodeLocation[] result = Arrays.copyOf(labelList, labelList.length + 1);
        result[result.length - 1] = add;
        return result;
    }

    /** Prepends the context label to the input label list. */
    public static BytecodeLocation[] prepend(BytecodeLocation add, BytecodeLocation[] labelList) {
        BytecodeLocation[] result = new BytecodeLocation[labelList.length + 1];
        result[0] = add;
        System.arraycopy(labelList, 0, result, 1, labelList.length);
        return result;
    }

    public static BytecodeLocation[] peel(BytecodeLocation[] labelList, int maxDepth) {

        assert maxDepth >= 0;
        assert labelList.length > maxDepth;

        BytecodeLocation[] resultingLabelList = new BytecodeLocation[maxDepth];

        for (int i = maxDepth - 1, j = labelList.length - 1; i >= 0 && j >= 0; i--, j--) {
            // get only the last 'maxDepth' contexts from the initial chain
            resultingLabelList[i] = labelList[j];
        }

        return resultingLabelList;
    }

    public AnalysisContext lookupContext(C context) {
        return factory.lookupContext(context);
    }

    private class ContextFactory {

        private final ConcurrentHashMap<AnalysisContextKey, C> allContexts;

        protected ContextFactory() {
            this.allContexts = new ConcurrentHashMap<>();
        }

        protected C lookupContext(C newContext) {
            /* The key implements context value equality, instead of the identity equality. */
            AnalysisContextKey key = newContext.asKey();
            /* Look for an existing context with the same key. */
            C oldContext = allContexts.get(key);
            if (oldContext == null) {
                /* If no existing context was found, add it. */
                oldContext = allContexts.putIfAbsent(key, newContext);
                /* Check if another thread added the new context in the meantime. */
                oldContext = oldContext != null ? oldContext : newContext;
            }
            return oldContext;
        }
    }

}
