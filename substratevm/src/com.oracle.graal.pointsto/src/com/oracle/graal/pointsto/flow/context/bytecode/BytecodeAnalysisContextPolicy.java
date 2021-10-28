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
package com.oracle.graal.pointsto.flow.context.bytecode;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.flow.MethodTypeFlow;
import com.oracle.graal.pointsto.flow.context.AnalysisContextPolicy;
import com.oracle.graal.pointsto.flow.context.BytecodeLocation;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.flow.context.object.AllocationContextSensitiveObject;

import jdk.vm.ci.common.JVMCIError;

public class BytecodeAnalysisContextPolicy extends AnalysisContextPolicy<BytecodeAnalysisContext> {

    public BytecodeAnalysisContextPolicy() {
        super(getEmptyContext());
    }

    private static BytecodeAnalysisContext getEmptyContext() {
        return new BytecodeAnalysisContext(BytecodeAnalysisContext.emptyLabelList);
    }

    @Override
    public BytecodeAnalysisContext peel(BytecodeAnalysisContext context, int maxDepth) {

        assert maxDepth >= 0;

        if (context.labels().length <= maxDepth) {
            return context;
        }

        assert context.labels().length > maxDepth;

        BytecodeLocation[] resultingLabelList = peel(context.labels(), maxDepth);

        return lookupContext(resultingLabelList);
    }

    /**
     * Captures the context of a method invocation.
     * <p>
     * Iterates over the list of {@linkplain BytecodeAnalysisContext context chains} of the receiver
     * object, i.e. the contexts from which it's allocator method was invoked, and extends each of
     * this chains with the current receiver's {@linkplain AnalysisObject analysis object}. The
     * depth of each context chain is bounded to {@link PointstoOptions#MaxCallingContextDepth}.
     * <p>
     *
     *
     * @param bb the bigbang.
     * @param receiverObject invocation's {@linkplain AnalysisObject receiver object}
     * @return the list of {@link BytecodeAnalysisContext context chains} leading to this invocation
     *         extended with the current receiver object
     */
    @Override
    public BytecodeAnalysisContext calleeContext(PointsToAnalysis bb, AnalysisObject receiverObject, BytecodeAnalysisContext callerContext, MethodTypeFlow callee) {
        int maxCalleeContextDepth = callee.getLocalCallingContextDepth();

        /*
         * If the calling context depth is 0 return ContextChain.EMPTY_CONTEXT so that the unique
         * clone is linked in.
         */
        if (maxCalleeContextDepth == 0 || !receiverObject.isAllocationContextSensitiveObject()) {
            return emptyContext();
        }

        AllocationContextSensitiveObject receiverHeapObject = (AllocationContextSensitiveObject) receiverObject;

        /*
         * If the context depth is greater than 0 and the receiver object has context information
         * then extend the receiver's context by appending the receiver object allocation site,
         * otherwise create a context containing the receiver object allocation site.
         */

        BytecodeLocation[] labelList;
        if (receiverHeapObject.allocationContext() != null) {
            labelList = extend(((BytecodeAnalysisContext) receiverHeapObject.allocationContext()).labels(), receiverHeapObject.allocationLabel(), maxCalleeContextDepth);
        } else {
            // TODO remove branch if never taken
            JVMCIError.shouldNotReachHere("CoreAnalysisContextPolicy.merge: receiverHeapObject.heapContext() is null");
            labelList = new BytecodeLocation[]{receiverHeapObject.allocationLabel()};
        }

        return lookupContext(labelList);
    }

    @Override
    public BytecodeAnalysisContext staticCalleeContext(PointsToAnalysis bb, BytecodeLocation invokeLocation, BytecodeAnalysisContext callerContext, MethodTypeFlow callee) {
        assert callerContext != null;

        /*
         * If the AnalysisOptions.hybridStaticContext() is set then extends the caller context with
         * the invoke location, otherwise just reuse the caller context.
         */

        if (!PointstoOptions.HybridStaticContext.getValue(bb.getOptions())) {
            return callerContext;
        }

        int maxCallingContextDepth = callee.getLocalCallingContextDepth();

        /*
         * If the calling context depth is 0 return ContextChain.EMPTY_CONTEXT so that the unique
         * clone is linked in.
         */
        if (maxCallingContextDepth == 0) {
            return emptyContext();
        }

        BytecodeLocation[] labelList = extend(callerContext.labels(), invokeLocation, maxCallingContextDepth);
        return lookupContext(labelList);
    }

    /**
     * Record the context of a newly heap allocated object.
     *
     * @param context the {@linkplain BytecodeAnalysisContext} of the enclosing method
     * @return an {@linkplain BytecodeAnalysisContext} bounded by the maxHeapContextDepth
     *
     */
    @Override
    public BytecodeAnalysisContext allocationContext(BytecodeAnalysisContext context, int maxHeapContextDepth) {
        return peel(context, maxHeapContextDepth);
    }

    public BytecodeAnalysisContext getContext(BytecodeLocation bcl) {
        return getContext(new BytecodeLocation[]{bcl});
    }

    public BytecodeAnalysisContext getContext(BytecodeLocation[] bytecodeLocations) {
        return lookupContext(bytecodeLocations);
    }

    private BytecodeAnalysisContext lookupContext(BytecodeLocation[] bytecodeLocations) {
        return (BytecodeAnalysisContext) lookupContext(new BytecodeAnalysisContext(bytecodeLocations));
    }

}
