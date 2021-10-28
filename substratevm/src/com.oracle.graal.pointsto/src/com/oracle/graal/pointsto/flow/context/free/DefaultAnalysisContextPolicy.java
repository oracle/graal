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
package com.oracle.graal.pointsto.flow.context.free;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.MethodTypeFlow;
import com.oracle.graal.pointsto.flow.context.AnalysisContextPolicy;
import com.oracle.graal.pointsto.flow.context.BytecodeLocation;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;

public class DefaultAnalysisContextPolicy extends AnalysisContextPolicy<DefaultAnalysisContext> {

    public DefaultAnalysisContextPolicy() {
        super(getEmptyContext());
    }

    private static DefaultAnalysisContext getEmptyContext() {
        return new DefaultAnalysisContext();
    }

    @Override
    public DefaultAnalysisContext peel(DefaultAnalysisContext context, int maxDepth) {
        assert context.equals(emptyContext());
        return context;
    }

    /**
     * Captures the context of a method invocation. Since the analysis is not context sensitive, the
     * context of the callee is the same as the caller context.
     *
     * @param bb The BigBang.
     * @param receiverObject invocation's {@linkplain AnalysisObject receiver object}
     * @return the list of {@link DefaultAnalysisContext context chains} leading to this invocation
     *         extended with the current receiver object
     */
    @Override
    public DefaultAnalysisContext calleeContext(PointsToAnalysis bb, AnalysisObject receiverObject, DefaultAnalysisContext callerContext, MethodTypeFlow callee) {
        assert callerContext.equals(emptyContext());
        assert receiverObject.isContextInsensitiveObject();
        return callerContext;
    }

    @Override
    public DefaultAnalysisContext staticCalleeContext(PointsToAnalysis bb, BytecodeLocation invokeLocation, DefaultAnalysisContext callerContext, MethodTypeFlow callee) {
        assert callerContext.equals(emptyContext());
        return callerContext;
    }

    /**
     * Record the context of a newly heap allocated object.
     *
     * @param context the {@linkplain DefaultAnalysisContext} of the enclosing method
     * @return an {@linkplain DefaultAnalysisContext} bounded by the maxHeapContextDepth
     *
     */
    @Override
    public DefaultAnalysisContext allocationContext(DefaultAnalysisContext context, int maxHeapContextDepth) {
        return peel(context, maxHeapContextDepth);
    }

    public DefaultAnalysisContext getContext(BytecodeLocation bcl) {
        return getContext(new BytecodeLocation[]{bcl});
    }

    public DefaultAnalysisContext getContext(BytecodeLocation[] bytecodeLocations) {
        return lookupContext(bytecodeLocations);
    }

    private DefaultAnalysisContext lookupContext(BytecodeLocation[] bytecodeLocations) {
        assert bytecodeLocations.length == 0;
        return emptyContext();
    }
}
