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
package com.oracle.svm.core.reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.reflect.target.Target_jdk_internal_reflect_ConstantPool;

import jdk.graal.compiler.api.replacements.Fold;

public interface RuntimeMetadataDecoder {
    int NO_DATA = -1;

    Field[] parseFields(DynamicHub declaringType, int index, boolean publicOnly, int layerId);

    FieldDescriptor[] parseReachableFields(DynamicHub declaringType, int index, int layerId);

    Method[] parseMethods(DynamicHub declaringType, int index, boolean publicOnly, int layerId);

    MethodDescriptor[] parseReachableMethods(DynamicHub declaringType, int index, int layerId);

    Constructor<?>[] parseConstructors(DynamicHub declaringType, int index, boolean publicOnly, int layerId);

    ConstructorDescriptor[] parseReachableConstructors(DynamicHub declaringType, int index, int layerId);

    Class<?>[] parseClasses(int index, DynamicHub declaringType);

    Class<?>[] parseAllClasses();

    RecordComponent[] parseRecordComponents(DynamicHub declaringType, int index, int layerId);

    Object[] parseObjects(int index, DynamicHub declaringType);

    Parameter[] parseReflectParameters(Executable executable, byte[] encoding, DynamicHub declaringType);

    Object[] parseEnclosingMethod(int index, DynamicHub declaringType);

    byte[] parseByteArray(int index, DynamicHub declaringType);

    boolean isHiding(int modifiers);

    boolean isNegative(int modifiers);

    class ElementDescriptor {
        private final Class<?> declaringClass;

        public ElementDescriptor(Class<?> declaringClass) {
            this.declaringClass = declaringClass;
        }

        public Class<?> getDeclaringClass() {
            return declaringClass;
        }

        protected static String[] getParameterTypeNames(Class<?>[] parameterTypes) {
            String[] parameterTypeNames = new String[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; ++i) {
                parameterTypeNames[i] = parameterTypes[i].getTypeName();
            }
            return parameterTypeNames;
        }
    }

    class FieldDescriptor extends ElementDescriptor {
        public final String name;

        public FieldDescriptor(Class<?> declaringClass, String name) {
            super(declaringClass);
            this.name = name;
        }

        public FieldDescriptor(Field field) {
            this(field.getDeclaringClass(), field.getName());
        }

        public String getName() {
            return name;
        }
    }

    class MethodDescriptor extends ElementDescriptor {
        public final String name;
        public final String[] parameterTypeNames;

        public MethodDescriptor(Class<?> declaringClass, String name, String[] parameterTypeNames) {
            super(declaringClass);
            this.name = name;
            this.parameterTypeNames = parameterTypeNames;
        }

        public MethodDescriptor(Method method) {
            this(method.getDeclaringClass(), method.getName(), getParameterTypeNames(method.getParameterTypes()));
        }

        public String getName() {
            return name;
        }

        public String[] getParameterTypeNames() {
            return parameterTypeNames;
        }
    }

    class ConstructorDescriptor extends ElementDescriptor {
        public final String[] parameterTypeNames;

        public ConstructorDescriptor(Class<?> declaringClass, String[] parameterTypeNames) {
            super(declaringClass);
            this.parameterTypeNames = parameterTypeNames;
        }

        public ConstructorDescriptor(Constructor<?> constructor) {
            this(constructor.getDeclaringClass(), getParameterTypeNames(constructor.getParameterTypes()));
        }

        public String[] getParameterTypeNames() {
            return parameterTypeNames;
        }
    }

    interface MetadataAccessor {
        @Fold
        static MetadataAccessor singleton() {
            return ImageSingletons.lookup(MetadataAccessor.class);
        }

        <T> T getObject(int index, int layerId);

        Class<?> getClass(int index, int layerId);

        String getMemberName(int index, int layerId);

        String getOtherString(int index, int layerId);
    }

    static int getConstantPoolLayerId(Target_jdk_internal_reflect_ConstantPool constPool) {
        if (ImageLayerBuildingSupport.buildingImageLayer()) {
            return constPool.getLayerId();
        } else {
            return 0;
        }
    }
}
