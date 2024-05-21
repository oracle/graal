/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jni.access;

import java.util.function.Function;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.collections.UnmodifiableMapCursor;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.util.ImageHeapMap;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.MetaUtil;

/**
 * Information on a class that can be looked up and accessed via JNI.
 */
public final class JNIAccessibleClass {
    private final Class<?> classObject;
    private EconomicMap<JNIAccessibleMethodDescriptor, JNIAccessibleMethod> methods;
    private EconomicMap<CharSequence, JNIAccessibleField> fields;

    @Platforms(HOSTED_ONLY.class)
    public JNIAccessibleClass(Class<?> clazz) {
        assert clazz != null;
        this.classObject = clazz;
    }

    @Platforms(HOSTED_ONLY.class)
    JNIAccessibleClass() {
        /* For negative queries */
        this.classObject = null;
    }

    public boolean isNegative() {
        return classObject == null;
    }

    public Class<?> getClassObject() {
        return classObject;
    }

    public UnmodifiableMapCursor<CharSequence, JNIAccessibleField> getFields() {
        return (fields != null) ? fields.getEntries() : EconomicMap.emptyCursor();
    }

    public JNIAccessibleField getField(CharSequence name) {
        return (fields != null) ? fields.get(name) : null;
    }

    @Platforms(HOSTED_ONLY.class)
    public void addFieldIfAbsent(String name, Function<String, JNIAccessibleField> mappingFunction) {
        if (fields == null) {
            fields = ImageHeapMap.create(JNIReflectionDictionary.WRAPPED_CSTRING_EQUIVALENCE);
        }
        if (!fields.containsKey(name)) {
            fields.put(name, mappingFunction.apply(name));
        }
    }

    @Platforms(HOSTED_ONLY.class)
    public void addMethodIfAbsent(JNIAccessibleMethodDescriptor descriptor, Function<JNIAccessibleMethodDescriptor, JNIAccessibleMethod> mappingFunction) {
        if (methods == null) {
            methods = ImageHeapMap.create();
        }
        if (!methods.containsKey(descriptor)) {
            methods.put(descriptor, mappingFunction.apply(descriptor));
        }
    }

    public MapCursor<JNIAccessibleMethodDescriptor, JNIAccessibleMethod> getMethods() {
        return (methods != null) ? methods.getEntries() : EconomicMap.emptyCursor();
    }

    public JNIAccessibleMethod getMethod(JNIAccessibleMethodDescriptor descriptor) {
        if (methods == null) {
            return null;
        }
        JNIAccessibleMethod method = methods.get(descriptor);
        if (method == null) {
            /*
             * Negative method queries match any return type and are stored with only parameter
             * types in their signature.
             */
            String signatureWithoutReturnType = descriptor.getSignatureWithoutReturnType();
            if (signatureWithoutReturnType != null) {
                /* We only need to perform the lookup on valid signatures */
                method = methods.get(new JNIAccessibleMethodDescriptor(descriptor.getNameConvertToString(), signatureWithoutReturnType));
                VMError.guarantee(method == null || method.isNegative(), "Only negative queries should have a signature without return type");
            }
        }
        return method;
    }

    String getInternalName() {
        return MetaUtil.toInternalName(classObject.getName());
    }
}
