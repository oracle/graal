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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.InvokeInfo;

import jdk.vm.ci.code.BytecodePosition;

/**
 * Reachability specific abstraction for one invoke inside an AnalysisMethod.
 */
public class ReachabilityInvokeInfo implements InvokeInfo {

    private final ReachabilityAnalysisMethod targetMethod;
    private final BytecodePosition position;
    private final boolean isDirectInvoke;

    public ReachabilityInvokeInfo(ReachabilityAnalysisMethod targetMethod, BytecodePosition position, boolean isDirectInvoke) {
        this.targetMethod = targetMethod;
        this.position = position;
        this.isDirectInvoke = isDirectInvoke;
    }

    @Override
    public boolean canBeStaticallyBound() {
        return getCallees().size() <= 1;
    }

    @Override
    public AnalysisMethod getTargetMethod() {
        return targetMethod;
    }

    @Override
    public Collection<AnalysisMethod> getCallees() {
        if (isDirectInvoke) {
            return List.of(targetMethod);
        }
        return Arrays.asList(targetMethod.getImplementations());
    }

    @Override
    public BytecodePosition getPosition() {
        return position;
    }

    @Override
    public boolean isDirectInvoke() {
        return isDirectInvoke;
    }
}
