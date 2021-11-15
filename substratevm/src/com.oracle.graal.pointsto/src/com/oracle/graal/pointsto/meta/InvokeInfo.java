/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.meta;

import jdk.vm.ci.code.BytecodePosition;

import java.util.Collection;
import java.util.Collections;

public final class InvokeInfo {
    private final AnalysisMethod targetMethod;
    private final Collection<AnalysisMethod> possibleCallees;
    private final BytecodePosition position;
    private final boolean isDirect;

    public static InvokeInfo direct(AnalysisMethod targetMethod, BytecodePosition position) {
        return new InvokeInfo(targetMethod, Collections.singletonList(targetMethod), position, true);
    }

    public static InvokeInfo virtual(AnalysisMethod targetMethod, Collection<AnalysisMethod> possibleCallees, BytecodePosition position) {
        return new InvokeInfo(targetMethod, possibleCallees, position, false);
    }

    private InvokeInfo(AnalysisMethod targetMethod, Collection<AnalysisMethod> possibleCallees, BytecodePosition position, boolean isDirect) {
        this.targetMethod = targetMethod;
        this.possibleCallees = possibleCallees;
        this.position = position;
        this.isDirect = isDirect;
    }

    public boolean canBeStaticallyBound() {
        return targetMethod.canBeStaticallyBound() || possibleCallees.size() == 1;
    }

    public AnalysisMethod getTargetMethod() {
        return targetMethod;
    }

    public Collection<AnalysisMethod> getPossibleCallees() {
        return possibleCallees;
    }

    public BytecodePosition getPosition() {
        return position;
    }

    public boolean isDirect() {
        return isDirect;
    }
}
