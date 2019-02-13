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

import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

public class AnalysisMetaAccess extends UniverseMetaAccess {

    public AnalysisMetaAccess(AnalysisUniverse analysisUniverse, MetaAccessProvider originalMetaAccess) {
        super(analysisUniverse, originalMetaAccess);
    }

    @Override
    public AnalysisType lookupJavaType(Class<?> clazz) {
        return (AnalysisType) super.lookupJavaType(clazz);
    }

    public Optional<AnalysisType> optionalLookupJavaType(Class<?> clazz) {
        AnalysisType result = (AnalysisType) getTypeCacheEntry(clazz);
        if (result != null) {
            return Optional.of(result);
        }
        result = ((AnalysisUniverse) getUniverse()).optionalLookup(getWrapped().lookupJavaType(clazz));
        return Optional.ofNullable(result);
    }

    @Override
    public AnalysisType lookupJavaType(JavaConstant constant) {
        return (AnalysisType) super.lookupJavaType(constant);
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
        throw shouldNotReachHere();
    }

    @Override
    public int getArrayBaseOffset(JavaKind elementKind) {
        throw shouldNotReachHere();
    }
}
