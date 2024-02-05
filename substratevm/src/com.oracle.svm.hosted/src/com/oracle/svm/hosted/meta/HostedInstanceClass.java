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
import jdk.vm.ci.meta.ResolvedJavaField;

public class HostedInstanceClass extends HostedClass {

    protected HostedField[] instanceFieldsWithoutSuper;
    protected HostedField[] instanceFieldsWithSuper;
    protected int firstInstanceFieldOffset;
    protected int afterFieldsOffset;
    protected int instanceSize;
    protected boolean monitorFieldNeeded = false;
    protected int monitorFieldOffset = 0;
    protected int identityHashOffset = 0;

    public HostedInstanceClass(HostedUniverse universe, AnalysisType wrapped, JavaKind kind, JavaKind storageKind, HostedClass superClass, HostedInterface[] interfaces) {
        super(universe, wrapped, kind, storageKind, superClass, interfaces);
    }

    @Override
    public boolean isInstanceClass() {
        assert wrapped.isInstanceClass();
        return true;
    }

    @Override
    public boolean isArray() {
        assert !wrapped.isArray();
        return false;
    }

    @Override
    public final HostedType getComponentType() {
        return null;
    }

    @Override
    public HostedType getBaseType() {
        return this;
    }

    @Override
    public int getArrayDimension() {
        return 0;
    }

    @Override
    public HostedField[] getInstanceFields(boolean includeSuperclasses) {
        return includeSuperclasses ? instanceFieldsWithSuper : instanceFieldsWithoutSuper;
    }

    @Override
    public ResolvedJavaField findInstanceFieldWithOffset(long offset, JavaKind expectedKind) {
        assert offset >= 0;
        for (HostedField field : instanceFieldsWithSuper) {
            if (field.getLocation() == offset && (expectedKind == null || field.getStorageKind() == expectedKind)) {
                return field;
            }
        }
        return null;
    }

    public int getFirstInstanceFieldOffset() {
        /*
         * Each object has at least a header, so the firstInstanceFieldOffset should always be
         * positive.
         */
        assert firstInstanceFieldOffset > 0 : "Invalid offset " + firstInstanceFieldOffset + " class: " + getName();
        return firstInstanceFieldOffset;
    }

    public int getAfterFieldsOffset() {
        /*
         * Each object has at least a header, so the afterFieldsOffset should always be positive.
         */
        assert afterFieldsOffset > 0 : "Invalid offset " + afterFieldsOffset + " class: " + getName();
        return afterFieldsOffset;
    }

    public int getInstanceSize() {
        return instanceSize;
    }

    /*
     * Synthetic fields.
     */

    public boolean needMonitorField() {
        return monitorFieldNeeded;
    }

    public void setNeedMonitorField() {
        monitorFieldNeeded = true;
    }

    public int getMonitorFieldOffset() {
        return monitorFieldOffset;
    }

    public void setMonitorFieldOffset(int monitorFieldOffset) {
        assert this.monitorFieldOffset == 0 : "setting monitor field offset twice";
        this.monitorFieldOffset = monitorFieldOffset;
    }

    public int getIdentityHashOffset() {
        return identityHashOffset;
    }

    public void setIdentityHashOffset(int offset) {
        assert this.identityHashOffset == 0 : "setting identity hashcode field offset more than once";
        assert offset > 0;
        this.identityHashOffset = offset;
    }
}
