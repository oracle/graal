/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.c.info;

import java.util.ArrayList;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.c.enums.CEnumRuntimeData;

import jdk.vm.ci.meta.ResolvedJavaType;

public class EnumInfo extends SizableInfo {

    private final ResolvedJavaType annotatedType;
    private final ArrayList<AnalysisMethod> valueMethods = new ArrayList<>();
    private final ArrayList<AnalysisMethod> lookupMethods = new ArrayList<>();

    private CEnumRuntimeData runtimeData;

    public EnumInfo(String name, ResolvedJavaType annotatedType) {
        super(name, ElementKind.INTEGER);
        this.annotatedType = annotatedType;
    }

    @Override
    public void accept(InfoTreeVisitor visitor) {
        visitor.visitEnumInfo(this);
    }

    @Override
    public ResolvedJavaType getAnnotatedElement() {
        return annotatedType;
    }

    public CEnumRuntimeData getRuntimeData() {
        assert runtimeData != null;
        return runtimeData;
    }

    public void setRuntimeData(CEnumRuntimeData runtimeData) {
        assert this.runtimeData == null;
        this.runtimeData = runtimeData;
    }

    public Iterable<AnalysisMethod> getCEnumValueMethods() {
        return valueMethods;
    }

    public Iterable<AnalysisMethod> getCEnumLookupMethods() {
        return lookupMethods;
    }

    public void addCEnumValueMethod(AnalysisMethod method) {
        valueMethods.add(method);
    }

    public void addCEnumLookupMethod(AnalysisMethod method) {
        lookupMethods.add(method);
    }

    public boolean hasCEnumLookupMethods() {
        return !lookupMethods.isEmpty();
    }
}
