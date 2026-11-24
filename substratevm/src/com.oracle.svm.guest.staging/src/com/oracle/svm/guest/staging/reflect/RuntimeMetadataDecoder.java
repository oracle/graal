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
package com.oracle.svm.guest.staging.reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.guest.staging.configure.RuntimeDynamicAccessMetadata;

public interface RuntimeMetadataDecoder {
    int NO_DATA = -1;

    int COMPLETE_FLAG_MASK = 1 << 31;
    int IN_HEAP_FLAG_MASK = 1 << 30;
    int HIDING_FLAG_MASK = 1 << 29;
    int NEGATIVE_FLAG_MASK = 1 << 28;
    int PRESERVED_FLAG_MASK = 1 << 27;
    /* single lookup flags are filled before encoding */
    int ALL_FLAGS_MASK = COMPLETE_FLAG_MASK | IN_HEAP_FLAG_MASK | HIDING_FLAG_MASK | NEGATIVE_FLAG_MASK | PRESERVED_FLAG_MASK;

    int ALL_FIELDS_FLAG = 1 << 16;
    int ALL_DECLARED_FIELDS_FLAG = 1 << 17;
    int ALL_METHODS_FLAG = 1 << 18;
    int ALL_DECLARED_METHODS_FLAG = 1 << 19;
    int ALL_CONSTRUCTORS_FLAG = 1 << 20;
    int ALL_DECLARED_CONSTRUCTORS_FLAG = 1 << 21;
    int ALL_CLASSES_FLAG = 1 << 22;
    int ALL_DECLARED_CLASSES_FLAG = 1 << 23;
    int ALL_RECORD_COMPONENTS_FLAG = 1 << 24;
    int ALL_PERMITTED_SUBCLASSES_FLAG = 1 << 25;
    int ALL_NEST_MEMBERS_FLAG = 1 << 26;
    int ALL_SIGNERS_FLAG = 1 << 27;

    /* Same mask as Constants.JVM_RECOGNIZED_CLASS_MODIFIERS (0x7631). */
    int CLASS_ACCESS_FLAGS_MASK = 0x7631;

    static RuntimeMetadataDecoder singleton() {
        return ImageSingletons.lookup(RuntimeMetadataDecoder.class);
    }

    static int clearInternalModifiers(int modifiers) {
        return modifiers & (~ALL_FLAGS_MASK);
    }

    Field[] parseFields(Class<?> declaringType, int index, boolean publicOnly, int layerId);

    FieldDescriptor[] parseReachableFields(Class<?> declaringType, int index, int layerId);

    Method[] parseMethods(Class<?> declaringType, int index, boolean publicOnly, int layerId);

    MethodDescriptor[] parseReachableMethods(Class<?> declaringType, int index, int layerId);

    Constructor<?>[] parseConstructors(Class<?> declaringType, int index, boolean publicOnly, int layerId);

    ConstructorDescriptor[] parseReachableConstructors(Class<?> declaringType, int index, int layerId);

    Class<?>[] parseClasses(int index, Class<?> declaringType);

    Class<?>[] parseAllClasses();

    RecordComponent[] parseRecordComponents(Class<?> declaringType, int index, int layerId);

    Object[] parseObjects(int index, Class<?> declaringType);

    Parameter[] parseReflectParameters(Executable executable, byte[] encoding, Class<?> declaringType);

    Object[] parseEnclosingMethod(int index, Class<?> declaringType);

    byte[] parseByteArray(int index, Class<?> declaringType);

    RuntimeDynamicAccessMetadata parseDynamicAccessMetadata(int index, int layerId);

    boolean isHiding(int modifiers);

    boolean isNegative(int modifiers);

    boolean isPreserved(int modifiers);

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
        <T> T getObject(int index, int layerId);

        Class<?> getClass(int index, int layerId);

        String getMemberName(int index, int layerId);

        String getOtherString(int index, int layerId);
    }
}
