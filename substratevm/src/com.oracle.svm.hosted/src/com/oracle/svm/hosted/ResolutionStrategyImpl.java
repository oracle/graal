/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.reachability.SerializableMethodSummary;
import com.oracle.graal.reachability.summaries.ResolutionStrategy;

public class ResolutionStrategyImpl implements ResolutionStrategy {
    private final ImageClassLoader loader;
    private final AnalysisMetaAccess metaAccess;

    public ResolutionStrategyImpl(ImageClassLoader loader, AnalysisMetaAccess metaAccess) {
        this.loader = loader;
        this.metaAccess = metaAccess;
    }

    @Override
    public AnalysisType resolveClass(SerializableMethodSummary.ClassId classId) {
        try {
            Class<?> clazz = loader.forName(classId.className);
            return metaAccess.lookupJavaType(clazz);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        } catch (UnsupportedFeatureException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public AnalysisMethod resolveMethod(AnalysisType clazz, SerializableMethodSummary.MethodId methodId) {
        for (AnalysisMethod method : clazz.getDeclaredMethods()) {
            if (getId(method).equals(methodId)) {
                return method;
            }
        }
        for (AnalysisMethod ctor : clazz.getDeclaredConstructors()) {
            if (getId(ctor).equals(methodId)) {
                return ctor;
            }
        }
        return null;
    }

    @Override
    public AnalysisMethod resolveMethod(SerializableMethodSummary.MethodId methodId) {
        AnalysisType clazz = resolveClass(methodId.classId);
        if (clazz == null) {
            return null;
        }
        return resolveMethod(clazz, methodId);
    }

    @Override
    public AnalysisField resolveField(AnalysisType clazz, SerializableMethodSummary.FieldId fieldId) {
        for (AnalysisField instanceField : clazz.getInstanceFields(false)) {
            if (getId(instanceField).equals(fieldId)) {
                return instanceField;
            }
        }
        for (AnalysisField staticField : clazz.getStaticFields()) {
            if (getId(staticField).equals(fieldId)) {
                return staticField;
            }
        }
        return null;
    }

    @Override
    public AnalysisField resolveField(SerializableMethodSummary.FieldId fieldId) {
        AnalysisType clazz = resolveClass(fieldId.classId);
        if (clazz == null) {
            return null;
        }
        return resolveField(clazz, fieldId);
    }

    @Override
    public SerializableMethodSummary.ClassId getId(AnalysisType type) {
        return new SerializableMethodSummary.ClassId(type.getJavaClass().getName());
    }

    @Override
    public SerializableMethodSummary.MethodId getId(AnalysisMethod method) {
        return new SerializableMethodSummary.MethodId(getId(method.getDeclaringClass()), method.getQualifiedName());
    }

    @Override
    public SerializableMethodSummary.FieldId getId(AnalysisField field) {
        return new SerializableMethodSummary.FieldId(getId(field.getDeclaringClass()), field.getName());
    }
}
