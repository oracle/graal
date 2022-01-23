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

import static com.oracle.svm.core.SubstrateOptions.JNIVerboseLookupErrors;

import java.io.PrintStream;
import java.util.Map;
import java.util.function.Function;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.util.ImageHeapMap;
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

    private final EconomicMap<String, JNIAccessibleClass> classesByName = ImageHeapMap.create();
    private final EconomicMap<Class<?>, JNIAccessibleClass> classesByClassObject = ImageHeapMap.create();
    private final EconomicMap<JNINativeLinkage, JNINativeLinkage> nativeLinkages = ImageHeapMap.create();

    private JNIReflectionDictionary() {
    }

    private void dump(boolean condition, String label) {
        if (JNIVerboseLookupErrors.getValue() && condition) {
            PrintStream ps = Log.logStream();
            ps.println(label);
            ps.println(" classesByName:");
            MapCursor<String, JNIAccessibleClass> nameCursor = classesByName.getEntries();
            while (nameCursor.advance()) {
                ps.print("  ");
                ps.println(nameCursor.getKey());
                JNIAccessibleClass clazz = nameCursor.getValue();
                ps.println("   methods:");
                MapCursor<JNIAccessibleMethodDescriptor, JNIAccessibleMethod> methodsCursor = clazz.getMethodsByDescriptor();
                while (methodsCursor.advance()) {
                    ps.print("      ");
                    ps.println(methodsCursor.getKey().getNameAndSignature());
                }
                ps.println("   fields:");
                MapCursor<String, JNIAccessibleField> fieldsCursor = clazz.getFieldsByName();
                while (fieldsCursor.advance()) {
                    ps.print("      ");
                    ps.println(fieldsCursor.getKey());
                }
            }

            ps.println(" classesByClassObject:");
            MapCursor<Class<?>, JNIAccessibleClass> cursor = classesByClassObject.getEntries();
            while (cursor.advance()) {
                ps.print("  ");
                ps.println(cursor.getKey());
            }
        }
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
        nativeLinkages.putAll(EconomicMap.wrapMap(linkages));
    }

    public Iterable<JNIAccessibleClass> getClasses() {
        return classesByClassObject.getValues();
    }

    public Class<?> getClassObjectByName(String name) {
        JNIAccessibleClass clazz = classesByName.get(name);
        dump(clazz == null, "getClassObjectByName");
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

    public void unsetEntryPoints(String declaringClass) {
        for (JNINativeLinkage linkage : nativeLinkages.getKeys()) {
            if (declaringClass.equals(linkage.getDeclaringClassName())) {
                linkage.unsetEntryPoint();
            }
        }
    }

    private JNIAccessibleMethod findMethod(Class<?> clazz, JNIAccessibleMethodDescriptor descriptor, String dumpLabel) {
        JNIAccessibleMethod method = getDeclaredMethod(clazz, descriptor, dumpLabel);
        if (descriptor.isConstructor() || descriptor.isClassInitializer()) { // never recurse
            return method;
        }
        if (method == null && clazz.getSuperclass() != null) {
            method = findMethod(clazz.getSuperclass(), descriptor, null);
        }
        if (method == null) {
            // NOTE: this likely needs special handling for resolving default methods.
            method = findSuperinterfaceMethod(clazz, descriptor);
        }
        return method;
    }

    private JNIAccessibleMethod findSuperinterfaceMethod(Class<?> clazz, JNIAccessibleMethodDescriptor descriptor) {
        for (Class<?> parent : clazz.getInterfaces()) {
            JNIAccessibleMethod method = getDeclaredMethod(parent, descriptor, null);
            if (method == null) {
                method = findSuperinterfaceMethod(parent, descriptor);
            }
            if (method != null && method.isPublic() && !method.isStatic()) {
                // non-public or static interface methods are not externally visible
                return method;
            }
        }
        return null;
    }

    public JNIMethodId getDeclaredMethodID(Class<?> classObject, JNIAccessibleMethodDescriptor descriptor, boolean isStatic) {
        JNIAccessibleMethod method = getDeclaredMethod(classObject, descriptor, "getDeclaredMethodID");
        boolean match = (method != null && method.isStatic() == isStatic);
        return toMethodID(match ? method : null);
    }

    private JNIAccessibleMethod getDeclaredMethod(Class<?> classObject, JNIAccessibleMethodDescriptor descriptor, String dumpLabel) {
        JNIAccessibleClass clazz = classesByClassObject.get(classObject);
        dump(clazz == null && dumpLabel != null, dumpLabel);
        JNIAccessibleMethod method = null;
        if (clazz != null) {
            method = clazz.getMethod(descriptor);
        }
        return method;
    }

    public JNIMethodId getMethodID(Class<?> classObject, String name, String signature, boolean isStatic) {
        JNIAccessibleMethod method = findMethod(classObject, new JNIAccessibleMethodDescriptor(name, signature), "getMethodID");
        boolean match = (method != null && method.isStatic() == isStatic && method.isDiscoverableIn(classObject));
        return toMethodID(match ? method : null);
    }

    private static JNIMethodId toMethodID(JNIAccessibleMethod method) {
        SignedWord value = WordFactory.zero();
        if (method != null) {
            value = Word.objectToUntrackedPointer(method); // safe because it is in the image heap
            if (SubstrateOptions.SpawnIsolates.getValue()) { // use offset: valid across isolates
                value = value.subtract((SignedWord) Isolates.getHeapBase(CurrentIsolate.getIsolate()));
            }
        }
        return (JNIMethodId) value;
    }

    public static JNIAccessibleMethod getMethodByID(JNIMethodId method) {
        Object obj = null;
        if (method.notEqual(WordFactory.zero())) {
            Pointer p = (Pointer) method;
            if (SubstrateOptions.SpawnIsolates.getValue()) {
                p = p.add((UnsignedWord) Isolates.getHeapBase(CurrentIsolate.getIsolate()));
            }
            obj = p.toObject();
        }
        return (JNIAccessibleMethod) obj;
    }

    private JNIAccessibleField getDeclaredField(Class<?> classObject, String name, boolean isStatic, String dumpLabel) {
        JNIAccessibleClass clazz = classesByClassObject.get(classObject);
        dump(clazz == null && dumpLabel != null, dumpLabel);
        if (clazz != null) {
            JNIAccessibleField field = clazz.getField(name);
            if (field != null && field.isStatic() == isStatic) {
                return field;
            }
        }
        return null;
    }

    public JNIFieldId getDeclaredFieldID(Class<?> classObject, String name, boolean isStatic) {
        JNIAccessibleField field = getDeclaredField(classObject, name, isStatic, "getDeclaredFieldID");
        return (field != null) ? field.getId() : WordFactory.nullPointer();
    }

    private JNIAccessibleField findField(Class<?> clazz, String name, boolean isStatic, String dumpLabel) {
        // Lookup according to JVM spec 5.4.3.2: local fields, superinterfaces, superclasses
        JNIAccessibleField field = getDeclaredField(clazz, name, isStatic, dumpLabel);
        if (field == null && isStatic) {
            field = findSuperinterfaceField(clazz, name);
        }
        if (field == null && clazz.getSuperclass() != null) {
            field = findField(clazz.getSuperclass(), name, isStatic, null);
        }
        return field;
    }

    private JNIAccessibleField findSuperinterfaceField(Class<?> clazz, String name) {
        for (Class<?> parent : clazz.getInterfaces()) {
            JNIAccessibleField field = getDeclaredField(parent, name, true, null);
            if (field == null) {
                field = findSuperinterfaceField(parent, name);
            }
            if (field != null) {
                return field;
            }
        }
        return null;
    }

    public JNIFieldId getFieldID(Class<?> clazz, String name, boolean isStatic) {
        JNIAccessibleField field = findField(clazz, name, isStatic, "getFieldID");
        return (field != null && field.isDiscoverableIn(clazz)) ? field.getId() : WordFactory.nullPointer();
    }

    public String getFieldNameByID(Class<?> classObject, JNIFieldId id) {
        JNIAccessibleClass clazz = classesByClassObject.get(classObject);
        if (clazz != null) {
            MapCursor<String, JNIAccessibleField> fieldsCursor = clazz.getFieldsByName();
            while (fieldsCursor.advance()) {
                JNIAccessibleField field = fieldsCursor.getValue();
                if (id.equal(field.getId())) {
                    return fieldsCursor.getKey();
                }
            }
        }
        return null;
    }

    public static JNIAccessibleMethodDescriptor getMethodDescriptor(JNIAccessibleMethod method) {
        if (method != null) {
            JNIAccessibleClass clazz = method.getDeclaringClass();
            MapCursor<JNIAccessibleMethodDescriptor, JNIAccessibleMethod> methodsCursor = clazz.getMethodsByDescriptor();
            while (methodsCursor.advance()) {
                if (methodsCursor.getValue() == method) {
                    return methodsCursor.getKey();
                }
            }
        }
        return null;
    }

}
