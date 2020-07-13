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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.oracle.graal.pointsto.meta.AnalysisType;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;

public class HostedInstanceClass extends HostedClass {

    protected HostedField[] instanceFields;
    protected int instanceSize;
    protected boolean monitorFieldNeeded = false;
    protected int monitorFieldOffset = 0;
    protected boolean hashCodeFieldNeeded = false;
    protected int hashCodeFieldOffset = 0;

    public HostedInstanceClass(HostedUniverse universe, AnalysisType wrapped, JavaKind kind, JavaKind storageKind, HostedClass superClass, HostedInterface[] interfaces, boolean isCloneable) {
        super(universe, wrapped, kind, storageKind, superClass, interfaces, isCloneable);
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
        assert instanceFields != null;

        if (includeSuperclasses && getSuperclass() != null) {
            List<HostedField> fields = new ArrayList<>();
            fields.addAll(Arrays.asList(getSuperclass().getInstanceFields(true)));
            fields.addAll(Arrays.asList(instanceFields));
            return fields.toArray(new HostedField[fields.size()]);
        }
        return instanceFields;
    }

    @Override
    public ResolvedJavaField findInstanceFieldWithOffset(long offset, JavaKind expectedKind) {
        assert offset >= 0;
        for (HostedField field : instanceFields) {
            if (field.getLocation() == offset && (expectedKind == null || field.getStorageKind() == expectedKind)) {
                return field;
            }
        }
        if (getSuperclass() != null) {
            return getSuperclass().findInstanceFieldWithOffset(offset, expectedKind);
        }
        return null;
    }

    public int getInstanceSize() {
        return instanceSize;
    }

    /*
     * Monitor field.
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

    /*
     * identityHashCode field.
     */

    public boolean needHashCodeField() {
        return hashCodeFieldNeeded;
    }

    public void setNeedHashCodeField() {
        this.hashCodeFieldNeeded = true;
    }

    public int getHashCodeFieldOffset() {
        return hashCodeFieldOffset;
    }

    public void setHashCodeFieldOffset(int hashCodeFieldOffset) {
        assert this.hashCodeFieldOffset == 0 : "setting hash code field offset twice";
        this.hashCodeFieldOffset = hashCodeFieldOffset;
    }
}
