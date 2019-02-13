/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.analysis;

import java.lang.reflect.Modifier;

import org.graalvm.compiler.nodes.ValueNode;

import com.oracle.graal.pointsto.flow.context.AnalysisContext;
import com.oracle.graal.pointsto.flow.context.BytecodeLocation;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.typestate.TypeState;

import jdk.vm.ci.meta.JavaKind;

public class SVMTypeState {

    /** Wraps the analysis pinned object corresponding into a non-null type state. */
    public static TypeState forPinned(Inflation bb, ValueNode allocation, BytecodeLocation allocationLabel, AnalysisType exactType) {
        return forPinned(bb, allocation, allocationLabel, exactType, bb.contextPolicy().emptyContext());
    }

    /** Wraps the analysis pinned object corresponding into a non-null type state. */
    public static TypeState forPinned(Inflation bb, ValueNode allocation, BytecodeLocation allocationLabel, AnalysisType objectType, AnalysisContext allocationContext) {
        assert objectType.isArray() || (objectType.isInstanceClass() && !Modifier.isAbstract(objectType.getModifiers())) : objectType;
        assert allocation.getStackKind() == JavaKind.Object;
        AnalysisObject pinnedObject = bb.svmAnalysisPolicy().createPinnedObject(bb, objectType, allocationLabel, allocationContext);
        return TypeState.forNonNullObject(bb, pinnedObject);
    }
}
