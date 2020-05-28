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
package com.oracle.svm.hosted.c.query;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

import org.graalvm.nativeimage.c.struct.RawStructure;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.c.CInterfaceError;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.info.AccessorInfo;
import com.oracle.svm.hosted.c.info.ElementInfo;
import com.oracle.svm.hosted.c.info.NativeCodeInfo;
import com.oracle.svm.hosted.c.info.RawStructureInfo;
import com.oracle.svm.hosted.c.info.SizableInfo.ElementKind;
import com.oracle.svm.hosted.c.info.SizableInfo.SignednessValue;
import com.oracle.svm.hosted.c.info.StructBitfieldInfo;
import com.oracle.svm.hosted.c.info.StructFieldInfo;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.util.ReflectionUtil.ReflectionUtilError;

import jdk.vm.ci.meta.ResolvedJavaType;

public final class RawStructureLayoutPlanner extends NativeInfoTreeVisitor {

    private RawStructureLayoutPlanner(NativeLibraries nativeLibs) {
        super(nativeLibs);
    }

    public static void plan(NativeLibraries nativeLibs, NativeCodeInfo nativeCodeInfo) {
        /*
         * Raw structure types have no C header file. They are stored in the built-in NativeCodeInfo
         * object. We can therefore skip all the others.
         */
        if (!nativeCodeInfo.isBuiltin()) {
            return;
        }

        RawStructureLayoutPlanner planner = new RawStructureLayoutPlanner(nativeLibs);
        nativeCodeInfo.accept(planner);
    }

    @Override
    protected void visitRawStructureInfo(RawStructureInfo info) {
        if (info.isPlanned()) {
            return;
        }

        ResolvedJavaType type = info.getAnnotatedElement();
        for (ResolvedJavaType t : type.getInterfaces()) {
            if (!nativeLibs.isPointerBase(t)) {
                throw UserError.abort("Type " + type + " must not implement " + t);
            }

            if (t.equals(nativeLibs.getPointerBaseType())) {
                continue;
            }

            ElementInfo einfo = nativeLibs.findElementInfo(t);
            if (!(einfo instanceof RawStructureInfo)) {
                throw UserError.abort(new CInterfaceError("Illegal super type " + t + " found", type).getMessage());
            }

            RawStructureInfo rinfo = (RawStructureInfo) einfo;
            rinfo.accept(this);
            assert rinfo.isPlanned();

            if (info.getParentInfo() != null) {
                throw UserError.abort(new CInterfaceError("Only single inheritance of RawStructure types is supported", type).getMessage());
            }
            info.setParentInfo(rinfo);
        }

        for (ElementInfo child : new ArrayList<>(info.getChildren())) {
            if (child instanceof StructFieldInfo) {
                StructFieldInfo fieldInfo = (StructFieldInfo) child;
                StructFieldInfo parentFieldInfo = findParentFieldInfo(fieldInfo, info.getParentInfo());
                if (parentFieldInfo != null) {
                    fieldInfo.mergeChildrenAndDelete(parentFieldInfo);
                } else {
                    computeSize(fieldInfo);
                }
            }
        }

        planLayout(info);
    }

    private void computeSize(StructFieldInfo info) {
        final int declaredSize;
        if (info.isObject()) {
            declaredSize = ConfigurationValues.getObjectLayout().getReferenceSize();
        } else {
            /*
             * Resolve field size using the declared type in its accessors. Note that the field
             * offsets are not calculated before visiting all StructFieldInfos and collecting all
             * field types.
             */
            final ResolvedJavaType fieldType;
            AccessorInfo accessor = info.getAccessorInfoWithSize();
            switch (accessor.getAccessorKind()) {
                case GETTER:
                    fieldType = accessor.getReturnType();
                    break;
                case SETTER:
                    fieldType = accessor.getValueParameterType();
                    break;
                default:
                    throw shouldNotReachHere("Unexpected accessor kind " + accessor.getAccessorKind());
            }
            if (info.getKind() == ElementKind.INTEGER) {
                info.getSignednessInfo().setProperty(isSigned(fieldType) ? SignednessValue.SIGNED : SignednessValue.UNSIGNED);
            }
            declaredSize = getSizeInBytes(fieldType);
        }
        info.getSizeInfo().setProperty(declaredSize);
    }

    /**
     * Compute the offsets of each field.
     */
    private void planLayout(RawStructureInfo info) {
        /* Inherit from the parent type. */
        int currentOffset = info.getParentInfo() != null ? info.getParentInfo().getSizeInfo().getProperty() : 0;

        List<StructFieldInfo> fields = new ArrayList<>();
        for (ElementInfo child : info.getChildren()) {
            if (child instanceof StructFieldInfo) {
                fields.add((StructFieldInfo) child);
            } else if (child instanceof StructBitfieldInfo) {
                throw UserError.abort("StructBitfield is currently not supported by RawStructures!");
            }
        }

        /*
         * Sort fields in field size descending order. Note that prior to this, the fields are
         * already sorted in alphabetical order.
         */
        fields.sort((f1, f2) -> f2.getSizeInfo().getProperty() - f1.getSizeInfo().getProperty());

        for (StructFieldInfo finfo : fields) {
            assert findParentFieldInfo(finfo, info.getParentInfo()) == null;
            int fieldSize = finfo.getSizeInfo().getProperty();
            currentOffset = alignOffset(currentOffset, fieldSize);
            assert currentOffset % fieldSize == 0;
            finfo.getOffsetInfo().setProperty(currentOffset);
            currentOffset += fieldSize;
        }

        int totalSize;
        Class<? extends IntUnaryOperator> sizeProviderClass = info.getAnnotatedElement().getAnnotation(RawStructure.class).sizeProvider();
        if (sizeProviderClass == IntUnaryOperator.class) {
            /* No sizeProvider specified in the annotation, so no adjustment necessary. */
            totalSize = currentOffset;

        } else {
            IntUnaryOperator sizeProvider;
            try {
                sizeProvider = ReflectionUtil.newInstance(sizeProviderClass);
            } catch (ReflectionUtilError ex) {
                throw UserError.abort(
                                ex.getCause(),
                                "The size provider of @" + RawStructure.class.getSimpleName() + " " + info.getAnnotatedElement().toJavaName(true) +
                                                " cannot be instantiated via no-argument constructor");
            }

            totalSize = sizeProvider.applyAsInt(currentOffset);
            if (totalSize < currentOffset) {
                throw UserError.abort("The size provider of @" + RawStructure.class.getSimpleName() + " " + info.getAnnotatedElement().toJavaName(true) + " computed size " + totalSize +
                                " which is smaller than the minimum size of " + currentOffset);
            }
        }

        info.getSizeInfo().setProperty(totalSize);
        info.setPlanned();
    }

    private StructFieldInfo findParentFieldInfo(StructFieldInfo fieldInfo, RawStructureInfo parentInfo) {
        if (parentInfo == null) {
            return null;
        }
        StructFieldInfo result;
        if (parentInfo.getParentInfo() != null) {
            result = findParentFieldInfo(fieldInfo, parentInfo.getParentInfo());
            if (result != null) {
                return result;
            }
        }

        for (ElementInfo child : parentInfo.getChildren()) {
            if (child instanceof StructFieldInfo) {
                StructFieldInfo parentFieldInfo = (StructFieldInfo) child;
                if (fieldInfo.getName().equals(parentFieldInfo.getName())) {
                    return parentFieldInfo;
                }
            }
        }

        return null;
    }

    private static int alignOffset(int offset, int fieldSize) {
        if (offset % fieldSize == 0) {
            return offset;
        }

        return (offset / fieldSize + 1) * fieldSize;
    }
}
