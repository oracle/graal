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
package com.oracle.graal.reachability;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.InvokeInfo;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.debug.GraalError;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Reachability specific extension of AnalysisMethod. Contains mainly information necessary to
 * traverse the call graph - get callees and callers of the method.
 *
 * @see ReachabilityInvokeInfo
 */
public class ReachabilityAnalysisMethod extends AnalysisMethod {

    /**
     * Invokes inside this method.
     */
    private final List<InvokeInfo> invokeInfos = Collections.synchronizedList(new ArrayList<>());

    /**
     * Callers of this method.
     */
    private final List<BytecodePosition> calledFrom = Collections.synchronizedList(new ArrayList<>());

    /**
     * The first callee of this method, to construct the parsing context.
     */
    private BytecodePosition reason;

    public ReachabilityAnalysisMethod(AnalysisUniverse universe, ResolvedJavaMethod wrapped) {
        super(universe, wrapped);
    }

    @Override
    public void startTrackInvocations() {
    }

    void addInvoke(InvokeInfo invoke) {
        this.invokeInfos.add(invoke);
    }

    @Override
    public Collection<InvokeInfo> getInvokes() {
        return invokeInfos;
    }

    @Override
    public StackTraceElement[] getParsingContext() {
        List<StackTraceElement> parsingContext = new ArrayList<>();
        ReachabilityAnalysisMethod curr = this;

        /* Defend against cycles in the parsing context. GR-35744 should fix this properly. */
        int maxSize = 100;

        while (curr != null && parsingContext.size() < maxSize) {
            parsingContext.add(curr.asStackTraceElement(curr.reason.getBCI()));
            curr = ((ReachabilityAnalysisMethod) curr.reason.getMethod());
        }
        return parsingContext.toArray(new StackTraceElement[parsingContext.size()]);
    }

    public void setReason(BytecodePosition reason) {
        GraalError.guarantee(this.reason == null, "Reason already set.");
        this.reason = reason;
    }

    @Override
    public List<BytecodePosition> getInvokeLocations() {
        return calledFrom;
    }

    public void addCaller(BytecodePosition bytecodePosition) {
        calledFrom.add(bytecodePosition);
    }

    @Override
    public boolean registerAsInvoked() {
        if (super.registerAsInvoked()) {
            if (!isStatic()) {
                getDeclaringClass().addInvokedVirtualMethod(this);
            }
            return true;
        }
        return false;
    }

    @Override
    public ReachabilityAnalysisType getDeclaringClass() {
        return ((ReachabilityAnalysisType) super.getDeclaringClass());
    }
}
