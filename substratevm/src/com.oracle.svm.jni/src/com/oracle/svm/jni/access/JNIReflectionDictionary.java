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
package com.oracle.svm.jni.access;

// Checkstyle: allow reflection

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.jni.nativeapi.JNIFieldId;
import com.oracle.svm.jni.nativeapi.JNIMethodId;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Provides JNI access to predetermined classes, methods and fields at runtime.
 */
public final class JNIReflectionDictionary {

    static void initialize() {
        ImageSingletons.add(JNIReflectionDictionary.class, new JNIReflectionDictionary());
    }

    public static JNIReflectionDictionary singleton() {
        return ImageSingletons.lookup(JNIReflectionDictionary.class);
    }

    private final Map<String, JNIAccessibleClass> classesByName = new HashMap<>();
    private final Map<Class<?>, JNIAccessibleClass> classesByClassObject = new HashMap<>();
    private final Map<JNINativeLinkage, JNINativeLinkage> nativeLinkages = new HashMap<>();

    private JNIReflectionDictionary() {
    }

    @Platforms(HOSTED_ONLY.class)
    JNIAccessibleClass addClassIfAbsent(Class<?> classObj, Function<Class<?>, JNIAccessibleClass> mappingFunction) {
        if (!classesByClassObject.containsKey(classObj)) {
            JNIAccessibleClass instance = mappingFunction.apply(classObj);
            classesByClassObject.put(classObj, instance);
            classesByName.put(instance.getInternalName(), instance);
        }
        return classesByClassObject.get(classObj);
    }

    @Platforms(HOSTED_ONLY.class)
    void addLinkages(Map<JNINativeLinkage, JNINativeLinkage> linkages) {
        nativeLinkages.putAll(linkages);
    }

    public Collection<JNIAccessibleClass> getClasses() {
        return Collections.unmodifiableCollection(classesByClassObject.values());
    }

    public Class<?> getClassObjectByName(String name) {
        JNIAccessibleClass clazz = classesByName.get(name);
        return (clazz != null) ? clazz.getClassObject() : null;
    }

    /**
     * Gets the linkage for a native method.
     *
     * @param declaringClass the {@linkplain JavaType#getName() name} of the class declaring the
     *            native method
     * @param name the name of the native method
     * @param descriptor the {@linkplain Signature#toMethodDescriptor() descriptor} of the native
     *            method
     * @return the linkage for the native method or {@code null} if no linkage exists
     */
    public JNINativeLinkage getLinkage(String declaringClass, String name, String descriptor) {
        JNINativeLinkage key = new JNINativeLinkage(declaringClass, name, descriptor);
        return nativeLinkages.get(key);
    }

    public Iterable<JNINativeLinkage> getLinkages(String declaringClass) {
        return () -> nativeLinkages.keySet().stream().filter(l -> declaringClass.equals(l.getDeclaringClassName())).iterator();
    }

    public JNIMethodId getMethodID(Class<?> classObject, JNIAccessibleMethodDescriptor descriptor, boolean isStatic) {
        JNIMethodId methodID = WordFactory.nullPointer();
        JNIAccessibleClass clazz = classesByClassObject.get(classObject);
        if (clazz != null) {
            JNIAccessibleMethod method = clazz.getMethod(descriptor);
            if (method != null && method.isStatic() == isStatic) {
                // safe because JNIAccessibleMethod is immutable (non-movable)
                methodID = (JNIMethodId) Word.objectToUntrackedPointer(method);
            }
        }
        return methodID;
    }

    public JNIMethodId getMethodID(Class<?> classObject, String name, String signature, boolean isStatic) {
        return getMethodID(classObject, new JNIAccessibleMethodDescriptor(name, signature), isStatic);
    }

    public static JNIAccessibleMethod getMethodByID(JNIMethodId method) {
        Object obj = ((Pointer) method).toObject();
        return KnownIntrinsics.convertUnknownValue(obj, JNIAccessibleMethod.class);
    }

    public JNIAccessibleField getField(Class<?> classObject, String name) {
        JNIAccessibleClass clazz = classesByClassObject.get(classObject);
        return (clazz != null) ? clazz.getField(name) : null;
    }

    public JNIFieldId getFieldID(Class<?> clazz, String name) {
        JNIAccessibleField field = getField(clazz, name);
        return field != null ? field.getId() : WordFactory.zero();
    }

    public String getFieldNameByID(Class<?> classObject, JNIFieldId id) {
        JNIAccessibleClass clazz = classesByClassObject.get(classObject);
        if (clazz != null) {
            for (Entry<String, JNIAccessibleField> entry : clazz.getFieldsByName().entrySet()) {
                JNIAccessibleField field = entry.getValue();
                if (id.equal(field.getId())) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    public static JNIAccessibleMethodDescriptor getMethodDescriptor(JNIAccessibleMethod method) {
        if (method != null) {
            JNIAccessibleClass clazz = method.getDeclaringClass();
            for (Entry<JNIAccessibleMethodDescriptor, JNIAccessibleMethod> entry : clazz.getMethodsByDescriptor().entrySet()) {
                if (entry.getValue() == method) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

}
