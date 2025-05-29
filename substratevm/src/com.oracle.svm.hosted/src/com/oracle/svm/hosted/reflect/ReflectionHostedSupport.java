/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.reflect;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.Map;
import java.util.Set;

import com.oracle.graal.pointsto.ObjectScanner.ScanReason;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.configure.ConditionalRuntimeValue;

public interface ReflectionHostedSupport {
    Map<Class<?>, Set<Class<?>>> getReflectionInnerClasses();

    Map<AnalysisType, Map<AnalysisField, ConditionalRuntimeValue<Field>>> getReflectionFields();

    Map<AnalysisType, Map<AnalysisMethod, ConditionalRuntimeValue<Executable>>> getReflectionExecutables();

    Object getAccessor(AnalysisMethod method);

    /**
     * Returns the fields that shadow a superclass element registered for reflection, to be excluded
     * from reflection queries.
     */
    Set<?> getHidingReflectionFields();

    /**
     * Returns the methods that shadow a superclass element registered for reflection, to be
     * excluded from reflection queries.
     */
    Set<?> getHidingReflectionMethods();

    RecordComponent[] getRecordComponents(Class<?> type);

    void registerHeapDynamicHub(Object hub, ScanReason reason);

    Set<?> getHeapDynamicHubs();

    void registerHeapReflectionField(Field field, ScanReason reason);

    void registerHeapReflectionExecutable(Executable executable, ScanReason reason);

    Map<AnalysisField, Field> getHeapReflectionFields();

    Map<AnalysisMethod, Executable> getHeapReflectionExecutables();

    Map<AnalysisType, Set<String>> getNegativeFieldQueries();

    Map<AnalysisType, Set<AnalysisMethod.Signature>> getNegativeMethodQueries();

    Map<AnalysisType, Set<AnalysisType[]>> getNegativeConstructorQueries();

    Map<Class<?>, Throwable> getClassLookupErrors();

    Map<Class<?>, Throwable> getFieldLookupErrors();

    Map<Class<?>, Throwable> getMethodLookupErrors();

    Map<Class<?>, Throwable> getConstructorLookupErrors();

    Map<Class<?>, Throwable> getRecordComponentLookupErrors();

    int getReflectionMethodsCount();

    int getReflectionFieldsCount();
}
