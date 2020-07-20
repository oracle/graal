/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.meta;

import static com.oracle.svm.core.config.ConfigurationValues.getObjectLayout;
import static com.oracle.svm.core.snippets.KnownIntrinsics.convertUnknownValue;
import static com.oracle.svm.core.util.VMError.unimplemented;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.deopt.SubstrateSpeculationLog.SubstrateSpeculation;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.SubstrateObjectConstant;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.SpeculationLog.Speculation;
import jdk.vm.ci.meta.SpeculationLog.SpeculationReason;

public class SubstrateMetaAccess implements MetaAccessProvider {

    public static SubstrateMetaAccess singleton() {
        return ImageSingletons.lookup(SubstrateMetaAccess.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateMetaAccess() {
        ImageSingletons.add(SubstrateMetaAccess.class, this);
    }

    @Override
    public SubstrateType lookupJavaType(Class<?> clazz) {
        return lookupJavaTypeFromHub(DynamicHub.fromClass(clazz));
    }

    public SubstrateType lookupJavaTypeFromHub(DynamicHub hub) {
        SubstrateType type = (SubstrateType) hub.getMetaType();
        if (type == null) {
            /*
             * If the SubstrateType is not in the image heap, we always create a new type without
             * caching. We do not want to use the DynamicHub as a cache, because that means that the
             * DynamicHub would no longer be a read-only object.
             */
            type = new SubstrateType(JavaKind.Object, hub);
        }
        return type;
    }

    @Override
    public ResolvedJavaMethod lookupJavaMethod(Executable reflectionMethod) {
        throw unimplemented();
    }

    @Override
    public ResolvedJavaField lookupJavaField(Field reflectionField) {
        throw unimplemented();
    }

    @Override
    public ResolvedJavaType lookupJavaType(JavaConstant constant) {
        if (constant.getJavaKind() != JavaKind.Object || constant.isNull()) {
            return null;
        }
        return ((SubstrateObjectConstant) constant).getType(this);
    }

    @Override
    public Signature parseMethodDescriptor(String methodDescriptor) {
        throw unimplemented();
    }

    @Override
    public JavaConstant encodeDeoptActionAndReason(DeoptimizationAction action, DeoptimizationReason reason, int speculationId) {
        return Deoptimizer.encodeDeoptActionAndReason(action, reason, speculationId);
    }

    @Override
    public DeoptimizationAction decodeDeoptAction(JavaConstant constant) {
        return Deoptimizer.decodeDeoptAction(constant);
    }

    @Override
    public DeoptimizationReason decodeDeoptReason(JavaConstant constant) {
        return Deoptimizer.decodeDeoptReason(constant);
    }

    @Override
    public int decodeDebugId(JavaConstant constant) {
        return Deoptimizer.decodeDebugId(constant);
    }

    /**
     * The offset from the origin of an array to the first element.
     *
     * @return the offset in bytes
     */
    @Override
    public int getArrayBaseOffset(JavaKind kind) {
        return getObjectLayout().getArrayBaseOffset(kind);
    }

    /**
     * The scale used for the index when accessing elements of an array of this kind.
     *
     * @return the scale in order to convert the index into a byte offset
     */
    @Override
    public int getArrayIndexScale(JavaKind elementKind) {
        return getObjectLayout().getArrayIndexScale(elementKind);
    }

    @Override
    public JavaConstant encodeSpeculation(Speculation speculation) {
        return SubstrateObjectConstant.forObject(speculation.getReason());
    }

    @Override
    public Speculation decodeSpeculation(JavaConstant constant, SpeculationLog speculationLog) {
        return new SubstrateSpeculation((SpeculationReason) convertUnknownValue(SubstrateObjectConstant.asObject(constant), Object.class));
    }

    @Override
    public long getMemorySize(JavaConstant constant) {
        throw unimplemented();
    }
}
