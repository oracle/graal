/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.flow;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.svm.common.meta.MultiMethod.MultiMethodKey;

import jdk.vm.ci.code.BytecodePosition;

public abstract class AbstractStaticInvokeTypeFlow extends DirectInvokeTypeFlow {
    protected AbstractStaticInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, PointsToAnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, MultiMethodKey callerMultiMethodKey) {
        super(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, callerMultiMethodKey);
    }

    protected AbstractStaticInvokeTypeFlow(PointsToAnalysis bb, MethodFlowsGraph methodFlows, AbstractStaticInvokeTypeFlow original) {
        super(bb, methodFlows, original);
    }

    @Override
    public String toString() {
        return "StaticInvoke<" + targetMethod.format("%h.%n") + ">" + ":" + getState();
    }
}
