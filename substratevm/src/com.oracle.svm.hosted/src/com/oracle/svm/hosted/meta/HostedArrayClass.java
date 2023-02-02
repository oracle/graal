/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.meta;

import com.oracle.graal.pointsto.meta.AnalysisType;

import jdk.vm.ci.meta.JavaKind;

public class HostedArrayClass extends HostedClass {

    private final HostedType componentType;
    private final HostedType baseType;
    private final int arrayDepth;

    public HostedArrayClass(HostedUniverse universe, AnalysisType wrapped, JavaKind kind, JavaKind storageKind, HostedClass superClass, HostedInterface[] interfaces, HostedType componentType) {
        super(universe, wrapped, kind, storageKind, superClass, interfaces);
        this.componentType = componentType;

        HostedType cur = this;
        int depth = 0;
        while (cur.getComponentType() != null) {
            cur = cur.getComponentType();
            depth++;
        }
        this.baseType = cur;
        this.arrayDepth = depth;

        assert componentType.arrayType == null;
        componentType.arrayType = this;
    }

    @Override
    public boolean isInstanceClass() {
        assert !wrapped.isInstanceClass();
        return false;
    }

    @Override
    public boolean isArray() {
        assert wrapped.isArray();
        return true;
    }

    @Override
    public final HostedType getComponentType() {
        return componentType;
    }

    @Override
    public HostedType getBaseType() {
        return baseType;
    }

    @Override
    public int getArrayDimension() {
        return arrayDepth;
    }

    @Override
    public HostedField[] getInstanceFields(boolean includeSuperclasses) {
        return new HostedField[0];
    }

    @Override
    public boolean isLocal() {
        return false;
    }

    @Override
    public boolean isMember() {
        return false;
    }
}
