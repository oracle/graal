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
package com.oracle.graal.pointsto.meta;

import static com.oracle.graal.pointsto.util.AnalysisError.shouldNotReachHere;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.Optional;

import com.oracle.graal.pointsto.heap.TypedConstant;
import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

public class AnalysisMetaAccess extends UniverseMetaAccess {

    @SuppressWarnings("this-escape")
    public AnalysisMetaAccess(AnalysisUniverse analysisUniverse, MetaAccessProvider originalMetaAccess) {
        super(analysisUniverse, originalMetaAccess);

        /* Make sure that Object type is added to the universe before any other types. */
        lookupJavaType(Object.class);
        /* Cloneable is needed before any other instance class can be created. */
        lookupJavaType(Cloneable.class);
    }

    @Override
    public AnalysisType lookupJavaType(Class<?> clazz) {
        return (AnalysisType) super.lookupJavaType(clazz);
    }

    @Override
    public AnalysisType[] lookupJavaTypes(Class<?>[] classes) {
        AnalysisType[] result = new AnalysisType[classes.length];

        for (int i = 0; i < result.length; ++i) {
            result[i] = this.lookupJavaType(classes[i]);
        }

        return result;
    }

    public Optional<AnalysisType> optionalLookupJavaType(Class<?> clazz) {
        AnalysisType result = (AnalysisType) getTypeCacheEntry(clazz);
        if (result != null) {
            return Optional.of(result);
        }
        result = getUniverse().optionalLookup(getWrapped().lookupJavaType(clazz));
        return Optional.ofNullable(result);
    }

    @Override
    public AnalysisType lookupJavaType(JavaConstant constant) {
        if (constant.getJavaKind() != JavaKind.Object || constant.isNull()) {
            return null;
        } else if (constant instanceof TypedConstant typedConstant) {
            return typedConstant.getType();
        }
        /*
         * Ideally, this path should be unreachable, i.e., we should only see TypedConstant. But the
         * image heap scanning during static analysis is not implemented cleanly enough and invokes
         * this method both with image heap constants and original HotSpot object constants.
         */
        return getUniverse().lookup(getWrapped().lookupJavaType(constant));
    }

    @Override
    public AnalysisMethod lookupJavaMethod(Executable reflectionMethod) {
        return (AnalysisMethod) super.lookupJavaMethod(reflectionMethod);
    }

    @Override
    public AnalysisField lookupJavaField(Field reflectionField) {
        return (AnalysisField) super.lookupJavaField(reflectionField);
    }

    @Override
    public int getArrayIndexScale(JavaKind elementKind) {
        throw shouldNotReachHere("should not be reached during analysis");
    }

    @Override
    public int getArrayBaseOffset(JavaKind elementKind) {
        throw shouldNotReachHere("should not be reached during analysis");
    }

    @Override
    public AnalysisUniverse getUniverse() {
        return (AnalysisUniverse) universe;
    }

}
