/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.heap;

import java.lang.reflect.Executable;
import java.util.Set;

import com.oracle.graal.pointsto.infrastructure.OriginalMethodProvider;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;

public class ImageLayerSnapshotUtil {
    public static final String FILE_NAME_PREFIX = "layer-snapshot-";
    public static final String FILE_EXTENSION = ".json";

    public static final String PERSISTED = "persisted";

    public static final int NULL_POINTER_CONSTANT = -1;
    public static final int NOT_MATERIALIZED_CONSTANT = -2;
    public static final String OBJECT_TAG = "A";
    public static final String METHOD_POINTER_TAG = "M";
    public static final String TYPES_TAG = "types";
    public static final String METHODS_TAG = "methods";
    public static final String FIELDS_TAG = "fields";
    public static final String CLASS_JAVA_NAME_TAG = "class java name";
    public static final String CLASS_NAME_TAG = "class name";
    public static final String MODIFIERS_TAG = "modifiers";
    public static final String IS_INTERFACE_TAG = "is interface";
    public static final String IS_ENUM_TAG = "is enum";
    public static final String IS_INITIALIZED_TAG = "is initialized";
    public static final String IS_LINKED_TAG = "is linked";
    public static final String SOURCE_FILE_NAME_TAG = "source file name";
    public static final String ENCLOSING_TYPE_TAG = "enclosing type";
    public static final String COMPONENT_TYPE_TAG = "component type";
    public static final String SUPER_CLASS_TAG = "super class";
    public static final String INTERFACES_TAG = "interfaces";
    public static final String CONSTANTS_TAG = "constants";
    public static final String CONSTANTS_TO_RELINK_TAG = "constants to relink";
    public static final String TID_TAG = "tid";
    public static final String IDENTITY_HASH_CODE_TAG = "identityHashCode";
    public static final String ID_TAG = "id";
    public static final String CONSTANT_TYPE_TAG = "constant type";
    public static final String DATA_TAG = "data";
    public static final String INSTANCE_TAG = "instance";
    public static final String ARRAY_TAG = "array";
    public static final String PRIMITIVE_ARRAY_TAG = "primitive array";
    public static final String FIELD_ACCESSED_TAG = "accessed";
    public static final String FIELD_READ_TAG = "read";
    public static final String FIELD_WRITTEN_TAG = "written";
    public static final String FIELD_FOLDED_TAG = "folded";
    public static final String LOCATION_TAG = "location";
    public static final String NEXT_TYPE_ID_TAG = "next type id";
    public static final String NEXT_METHOD_ID_TAG = "next method id";
    public static final String NEXT_FIELD_ID_TAG = "next field id";
    public static final String IMAGE_HEAP_SIZE_TAG = "image heap size";
    public static final String VALUE_TAG = "value";
    public static final String ENUM_CLASS_TAG = "enum class";
    public static final String ENUM_NAME_TAG = "enum name";
    public static final String CLASS_ID_TAG = "class id";
    public static final String SIMULATED_TAG = "simulated";
    public static final String OBJECT_OFFSET_TAG = "object offset";
    public static final String STATIC_PRIMITIVE_FIELDS_TAG = "static primitive fields";
    public static final String STATIC_OBJECT_FIELDS_TAG = "static object fields";
    public static final String IMAGE_SINGLETON_KEYS = "image singleton keys";
    public static final String IMAGE_SINGLETON_OBJECTS = "image singleton objects";

    public String getTypeIdentifier(AnalysisType type) {
        String javaName = type.toJavaName(true);
        return addModuleName(javaName, type.getJavaClass().getModule().getName());
    }

    public String getMethodIdentifier(AnalysisMethod method) {
        AnalysisType declaringClass = method.getDeclaringClass();
        Executable originalMethod = OriginalMethodProvider.getJavaMethod(method);
        String moduleName = declaringClass.getJavaClass().getModule().getName();
        if (originalMethod != null) {
            return addModuleName(originalMethod.toString(), moduleName);
        }
        return addModuleName(getQualifiedName(method), moduleName);
    }

    protected static String addModuleName(String elementName, String moduleName) {
        return moduleName + ":" + elementName;
    }

    protected static String getQualifiedName(AnalysisMethod method) {
        return method.getSignature().getReturnType().toJavaName(true) + " " + method.getQualifiedName();
    }

    @SuppressWarnings("unused")
    public Set<Integer> getRelinkedFields(AnalysisType type, AnalysisMetaAccess metaAccess) {
        return Set.of();
    }
}
