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

import static com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton.LAYER_NUM_UNINSTALLED;

import com.oracle.graal.pointsto.infrastructure.OriginalFieldProvider;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaField;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.meta.SharedField;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ameta.FieldValueInterceptionSupport;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Store the compile-time information for a field in the Substrate VM, such as the field offset.
 */
public class HostedField extends HostedElement implements OriginalFieldProvider, SharedField, WrappedJavaField {

    static final int LOC_UNMATERIALIZED_STATIC_CONSTANT = -10;

    public static final HostedField[] EMPTY_ARRAY = new HostedField[0];

    public final AnalysisField wrapped;

    private final HostedType holder;
    private final HostedType type;

    protected int location;
    private int installedLayerNum;

    public HostedField(AnalysisField wrapped, HostedType holder, HostedType type) {
        this.wrapped = wrapped;
        this.holder = holder;
        this.type = type;
        this.location = LOC_UNINITIALIZED;
        this.installedLayerNum = LAYER_NUM_UNINSTALLED;
    }

    @Override
    public AnalysisField getWrapped() {
        return wrapped;
    }

    public void setLocation(int newLocation, int newInstallLayerNum) {
        assert this.location == LOC_UNINITIALIZED;
        assert newLocation >= 0 || newLocation == LOC_UNMATERIALIZED_STATIC_CONSTANT;

        if (newLocation != LOC_UNMATERIALIZED_STATIC_CONSTANT) {
            wrapped.checkGuaranteeFolded();
        }
        this.location = newLocation;

        setInstalledLayerNum(newInstallLayerNum);
    }

    private void setInstalledLayerNum(int newInstallLayerNum) {
        assert this.installedLayerNum == LAYER_NUM_UNINSTALLED;
        assert newInstallLayerNum != LAYER_NUM_UNINSTALLED;
        if (wrapped.isStatic()) {
            assert ImageLayerBuildingSupport.buildingImageLayer() ? newInstallLayerNum >= 0 : newInstallLayerNum == MultiLayeredImageSingleton.UNUSED_LAYER_NUMBER;
        } else {
            assert newInstallLayerNum == MultiLayeredImageSingleton.NONSTATIC_FIELD_LAYER_NUMBER;
        }
        this.installedLayerNum = newInstallLayerNum;
    }

    protected void setUnmaterializedStaticConstant(int newInstalledLayerNum) {
        assert isStatic();
        if (location == LOC_UNMATERIALIZED_STATIC_CONSTANT) {
            // already set via prior layer
            assert installedLayerNum != newInstalledLayerNum;
            return;
        }
        setLocation(LOC_UNMATERIALIZED_STATIC_CONSTANT, newInstalledLayerNum);
    }

    public boolean isUnmaterialized() {
        return this.location == LOC_UNMATERIALIZED_STATIC_CONSTANT;
    }

    public boolean hasLocation() {
        return location >= 0;
    }

    /**
     * The offset or index of the field. The value depends on the kind of field:
     * <ul>
     * <li>instance fields: the offset (in bytes) from the origin of the instance.
     * <li>static fields of primitive type: the offset (in bytes) into the static primitive data
     * area.
     * <li>static reference fields: the offset (in bytes) into the static object data area.
     * <li>static fields that are never written (including but not limited to static final fields):
     * unused, this method must not be called.
     * </ul>
     */
    @Override
    public int getLocation() {
        return location;
    }

    @Override
    public boolean isAccessed() {
        return wrapped.isAccessed();
    }

    @Override
    public boolean isReachable() {
        return wrapped.isReachable();
    }

    public boolean isRead() {
        return wrapped.isRead();
    }

    @Override
    public boolean isWritten() {
        return wrapped.isWritten();
    }

    public boolean isValueAvailable() {
        return FieldValueInterceptionSupport.singleton().isValueAvailable(wrapped);
    }

    @Override
    public String getName() {
        return wrapped.getName();
    }

    @Override
    public HostedType getType() {
        return type;
    }

    @Override
    public int getModifiers() {
        return wrapped.getModifiers();
    }

    @Override
    public int getOffset() {
        return getLocation();
    }

    @Override
    public int hashCode() {
        return wrapped.hashCode();
    }

    @Override
    public HostedType getDeclaringClass() {
        return holder;
    }

    @Override
    public boolean isInternal() {
        return wrapped.isInternal();
    }

    @Override
    public boolean isSynthetic() {
        return wrapped.isSynthetic();
    }

    @Override
    public String toString() {
        return "HostedField<" + format("%h.%n") + " -> " + wrapped.toString() + ", location: " + location + ">";
    }

    @Override
    public JavaKind getStorageKind() {
        return getType().getStorageKind();
    }

    @Override
    public ResolvedJavaField unwrapTowardsOriginalField() {
        return wrapped;
    }

    public boolean hasInstalledLayerNum() {
        return !(installedLayerNum == LAYER_NUM_UNINSTALLED || installedLayerNum == MultiLayeredImageSingleton.NONSTATIC_FIELD_LAYER_NUMBER);
    }

    @Override
    public int getInstalledLayerNum() {
        VMError.guarantee(hasInstalledLayerNum(), "Bad installed layer value: %s %s", installedLayerNum, this);
        return installedLayerNum;
    }
}
