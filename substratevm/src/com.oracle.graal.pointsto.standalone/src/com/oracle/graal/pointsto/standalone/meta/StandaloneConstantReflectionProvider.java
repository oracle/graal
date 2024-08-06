/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.standalone.meta;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.standalone.StandaloneHost;

import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public class StandaloneConstantReflectionProvider extends HotSpotConstantReflectionProvider {
    private final AnalysisUniverse universe;

    public StandaloneConstantReflectionProvider(AnalysisUniverse universe, HotSpotJVMCIRuntime runtime) {
        super(runtime);
        this.universe = universe;
    }

    @Override
    public final JavaConstant readFieldValue(ResolvedJavaField field, JavaConstant receiver) {
        ResolvedJavaField wrappedField = ((AnalysisField) field).getWrapped();
        JavaConstant ret = universe.getHostedValuesProvider().interceptHosted(super.readFieldValue(wrappedField, receiver));
        if (ret == null) {
            ret = wrappedField.getConstantValue();
            if (ret == null) {
                ret = JavaConstant.defaultForKind(wrappedField.getJavaKind());
            }
        }
        return ret;
    }

    /**
     * The correctness of this method is verified by
     * com.oracle.graal.pointsto.test.ClassEqualityTest.
     */
    @Override
    public JavaConstant asJavaClass(ResolvedJavaType type) {
        return super.asJavaClass(markReachable(type));
    }

    @Override
    public final Constant asObjectHub(ResolvedJavaType type) {
        return super.asObjectHub(markReachable(type));
    }

    private static ResolvedJavaType markReachable(ResolvedJavaType type) {
        if (type instanceof AnalysisType) {
            AnalysisType t = (AnalysisType) type;
            t.registerAsReachable("registered by the StandaloneConstantReflectionProvider");
            return t.getWrapped();
        } else {
            return type;
        }
    }

    @Override
    public ResolvedJavaType asJavaType(Constant constant) {
        if (constant instanceof HotSpotObjectConstant) {
            HotSpotObjectConstant hotSpotObjectConstant = (HotSpotObjectConstant) constant;
            Object obj = hotSpotObjectConstant.asObject(hotSpotObjectConstant.getType());
            if (obj instanceof Class) {
                return getHostVM().lookupType((Class<?>) obj);
            }
        }
        return null;
    }

    private StandaloneHost getHostVM() {
        return (StandaloneHost) universe.hostVM();
    }
}
