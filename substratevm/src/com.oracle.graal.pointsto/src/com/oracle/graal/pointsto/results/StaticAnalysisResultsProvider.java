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
package com.oracle.graal.pointsto.results;

import java.util.List;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.flow.context.AnalysisContext;
import com.oracle.graal.pointsto.meta.AnalysisMethod;

public final class StaticAnalysisResultsProvider {

    public static StaticAnalysisResultsProvider factory(BigBang bigbang) {
        return new StaticAnalysisResultsProvider(bigbang);
    }

    private final BigBang bb;

    private StaticAnalysisResultsProvider(BigBang bb) {
        this.bb = bb;
    }

    /**
     * Get the list of all context sensitive callers.
     *
     * @param method the method for which the callers are requested
     * @param context the context for which the callers are requested
     * @return a list containing all the callers for the given method in the given context
     */
    public List<MethodFlowsGraph> callers(AnalysisMethod method, AnalysisContext context) {
        MethodFlowsGraph methodFlows = method.getTypeFlow().getFlows(context);
        return methodFlows.callers(bb);
    }
}
