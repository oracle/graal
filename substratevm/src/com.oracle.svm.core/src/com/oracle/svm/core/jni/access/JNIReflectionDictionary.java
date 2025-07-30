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

import static com.oracle.svm.core.MissingRegistrationUtils.throwMissingRegistrationErrors;
import static com.oracle.svm.core.SubstrateOptions.JNIVerboseLookupErrors;

import java.io.PrintStream;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.Function;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;
import org.graalvm.collections.UnmodifiableMapCursor;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;

import com.oracle.svm.configure.ClassNameSupport;
import com.oracle.svm.configure.config.ConfigurationMemberInfo;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.jni.MissingJNIRegistrationUtils;
import com.oracle.svm.core.jni.headers.JNIFieldId;
import com.oracle.svm.core.jni.headers.JNIMethodId;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonSupport;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.UnsavedSingleton;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.metadata.MetadataTracer;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.ImageHeapMap;
import com.oracle.svm.core.util.Utf8.WrappedAsciiCString;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.util.SignatureUtil;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Provides JNI access to predetermined classes, methods and fields at runtime.
 */
public final class JNIReflectionDictionary implements MultiLayeredImageSingleton, UnsavedSingleton {
    /**
     * Enables lookups with {@link WrappedAsciiCString}, which avoids many unnecessary character set
     * conversions and allocations.
     *
     * However, such objects are not supposed to be stored or passed out in any way because their C
     * memory might be freed. <em>Instead, regular {@link String} objects MUST be used for any other
     * purpose,</em> also because {@link CharSequence} handling is error-prone since there are no
     * guarantees that {@link Object#equals} and {@link Object#hashCode} are implemented and
     * compatible between classes.
     */
    static final Equivalence WRAPPED_CSTRING_EQUIVALENCE = new Equivalence() {
        @Override
        public boolean equals(Object a, Object b) {
            return CharSequence.compare((CharSequence) a, (CharSequence) b) == 0;
        }

        @Override
        public int hashCode(Object o) {
            assert o instanceof String || o instanceof WrappedAsciiCString;
            return o.hashCode();
        }
    };

    private static final JNIAccessibleClass NEGATIVE_CLASS_LOOKUP = new JNIAccessibleClass();

    public static void create() {
        ImageSingletons.add(JNIReflectionDictionary.class, new JNIReflectionDictionary());
    }

    @Platforms(HOSTED_ONLY.class)
    public static JNIReflectionDictionary currentLayer() {
        return LayeredImageSingletonSupport.singleton().lookup(JNIReflectionDictionary.class, false, true);
    }

    private static JNIReflectionDictionary[] layeredSingletons() {
        return MultiLayeredImageSingleton.getAllLayers(JNIReflectionDictionary.class);
    }

    private final EconomicMap<CharSequence, JNIAccessibleClass> classesByName = ImageHeapMap.createNonLayeredMap(WRAPPED_CSTRING_EQUIVALENCE);
    private final EconomicMap<Class<?>, JNIAccessibleClass> classesByClassObject = ImageHeapMap.createNonLayeredMap();
    private final EconomicMap<JNINativeLinkage, JNINativeLinkage> nativeLinkages = ImageHeapMap.createNonLayeredMap();

    private JNIReflectionDictionary() {
    }

    private static void dump(boolean condition, String label) {
        if (JNIVerboseLookupErrors.getValue() && condition) {
            int layerNum = 0;
            for (var dictionary : layeredSingletons()) {
                PrintStream ps = Log.logStream();
                ps.println("Layer " + layerNum);
                ps.println(label);
                ps.println(" classesByName:");
                MapCursor<CharSequence, JNIAccessibleClass> nameCursor = dictionary.classesByName.getEntries();
                while (nameCursor.advance()) {
                    ps.print("  ");
                    ps.println(nameCursor.getKey());
                    JNIAccessibleClass clazz = nameCursor.getValue();
                    ps.println("   methods:");
                    MapCursor<JNIAccessibleMethodDescriptor, JNIAccessibleMethod> methodsCursor = clazz.getMethods();
                    while (methodsCursor.advance()) {
                        ps.print("      ");
                        ps.print(methodsCursor.getKey().getName());
                        ps.println(methodsCursor.getKey().getSignature());
                    }
                    ps.println("   fields:");
                    UnmodifiableMapCursor<CharSequence, JNIAccessibleField> fieldsCursor = clazz.getFields();
                    while (fieldsCursor.advance()) {
                        ps.print("      ");
                        ps.println(fieldsCursor.getKey());
                    }
                }

                ps.println(" classesByClassObject:");
                MapCursor<Class<?>, JNIAccessibleClass> cursor = dictionary.classesByClassObject.getEntries();
                while (cursor.advance()) {
                    ps.print("  ");
                    ps.println(cursor.getKey());
                }
            }
        }
    }

    @Platforms(HOSTED_ONLY.class)
    public JNIAccessibleClass addClassIfAbsent(Class<?> classObj, Function<Class<?>, JNIAccessibleClass> mappingFunction) {
        if (!classesByClassObject.containsKey(classObj)) {
            JNIAccessibleClass instance = mappingFunction.apply(classObj);
            classesByClassObject.put(classObj, instance);
            String name = instance.getJNIName();
            classesByName.put(name, instance);
        }
        return classesByClassObject.get(classObj);
    }

    @Platforms(HOSTED_ONLY.class)
    public void addNegativeClassLookupIfAbsent(String typeName) {
        classesByName.putIfAbsent(typeName, NEGATIVE_CLASS_LOOKUP);
    }

    @Platforms(HOSTED_ONLY.class)
    public void addLinkages(Map<JNINativeLinkage, JNINativeLinkage> linkages) {
        nativeLinkages.putAll(EconomicMap.wrapMap(linkages));
    }

    @Platforms(HOSTED_ONLY.class)
    public Iterable<JNIAccessibleClass> getClasses() {
        return classesByClassObject.getValues();
    }

    public static Class<?> getClassObjectByName(CharSequence name) {
        for (var dictionary : layeredSingletons()) {
            JNIAccessibleClass clazz = dictionary.classesByName.get(name);
            if (clazz == null && !ClassNameSupport.isValidJNIName(name.toString())) {
                clazz = NEGATIVE_CLASS_LOOKUP;
            } else if (MetadataTracer.enabled()) {
                // trace if class exists (positive query) or name is valid (negative query)
                MetadataTracer.singleton().traceJNIType(ClassNameSupport.jniNameToTypeName(name.toString()));
            }
            clazz = checkClass(clazz, name.toString());
            if (clazz != null) {
                return clazz.getClassObject();
            }
        }
        dump(true, "getClassObjectByName");
        return null;
    }

    private static JNIAccessibleClass checkClass(JNIAccessibleClass clazz, String name) {
        if (throwMissingRegistrationErrors() && clazz == null) {
            MissingJNIRegistrationUtils.reportClassAccess(name);
        } else if (clazz != null && clazz.isNegative()) {
            return null;
        }
        return clazz;
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
    public static JNINativeLinkage getLinkage(CharSequence declaringClass, CharSequence name, CharSequence descriptor) {
        JNINativeLinkage key = new JNINativeLinkage(declaringClass, name, descriptor);
        for (var dictionary : layeredSingletons()) {
            var linkage = dictionary.nativeLinkages.get(key);
            if (linkage != null) {
                return linkage;
            }
        }
        return null;
    }

    public static void unsetEntryPoints(String declaringClass) {
        for (var dictionary : layeredSingletons()) {
            for (JNINativeLinkage linkage : dictionary.nativeLinkages.getKeys()) {
                if (declaringClass.equals(linkage.getDeclaringClassName())) {
                    linkage.unsetEntryPoint();
                }
            }
        }
    }

    private static JNIAccessibleMethod findMethod(Class<?> clazz, JNIAccessibleMethodDescriptor descriptor, String dumpLabel) {
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

    private static JNIAccessibleMethod findSuperinterfaceMethod(Class<?> clazz, JNIAccessibleMethodDescriptor descriptor) {
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

    public static JNIMethodId getDeclaredMethodID(Class<?> classObject, JNIAccessibleMethodDescriptor descriptor, boolean isStatic) {
        JNIAccessibleMethod method = getDeclaredMethod(classObject, descriptor, "getDeclaredMethodID");
        boolean match = (method != null && method.isStatic() == isStatic);
        return toMethodID(match ? method : null);
    }

    private static JNIAccessibleMethod getDeclaredMethod(Class<?> classObject, JNIAccessibleMethodDescriptor descriptor, String dumpLabel) {
        if (MetadataTracer.enabled()) {
            MetadataTracer.singleton().traceJNIType(classObject);
            MetadataTracer.singleton().traceMethodAccess(classObject, descriptor.getNameConvertToString(), descriptor.getSignatureConvertToString(),
                            ConfigurationMemberInfo.ConfigurationMemberDeclaration.DECLARED);
        }
        boolean foundClass = false;
        for (var dictionary : layeredSingletons()) {
            JNIAccessibleClass clazz = dictionary.classesByClassObject.get(classObject);
            if (clazz != null) {
                foundClass = true;
                JNIAccessibleMethod method = clazz.getMethod(descriptor);
                if (method != null) {
                    return method;
                }
            }
        }
        dump(!foundClass && dumpLabel != null, dumpLabel);
        return null;
    }

    public static JNIMethodId getMethodID(Class<?> classObject, CharSequence name, CharSequence signature, boolean isStatic) {
        JNIAccessibleMethod method = findMethod(classObject, new JNIAccessibleMethodDescriptor(name, signature), "getMethodID");
        method = checkMethod(method, classObject, name, signature);
        boolean match = (method != null && method.isStatic() == isStatic && method.isDiscoverableIn(classObject));
        return toMethodID(match ? method : null);
    }

    private static JNIMethodId toMethodID(JNIAccessibleMethod method) {
        if (method == null) {
            return Word.zero();
        }
        assert Heap.getHeap().isInImageHeap(method);
        return (JNIMethodId) Word.objectToUntrackedPointer(method).subtract(KnownIntrinsics.heapBase());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static JNIAccessibleMethod getMethodByID(JNIMethodId method) {
        if (!SubstrateOptions.SpawnIsolates.getValue() && method == Word.zero()) {
            return null;
        }
        Pointer p = KnownIntrinsics.heapBase().add((Pointer) method);
        JNIAccessibleMethod jniMethod = p.toObject(JNIAccessibleMethod.class, false);
        VMError.guarantee(jniMethod == null || !jniMethod.isNegative(), "Existing methods can't correspond to a negative query");
        return jniMethod;
    }

    private static JNIAccessibleMethod checkMethod(JNIAccessibleMethod method, Class<?> clazz, CharSequence name, CharSequence signature) {
        if (throwMissingRegistrationErrors() && method == null && SignatureUtil.isSignatureValid(signature.toString(), false)) {
            /*
             * A malformed signature never throws a missing registration error since it can't
             * possibly match an existing method.
             */
            MissingJNIRegistrationUtils.reportMethodAccess(clazz, name.toString(), signature.toString());
        } else if (method != null && method.isNegative()) {
            return null;
        }
        return method;
    }

    private static JNIAccessibleField getDeclaredField(Class<?> classObject, CharSequence name, boolean isStatic, String dumpLabel) {
        if (MetadataTracer.enabled()) {
            MetadataTracer.singleton().traceJNIType(classObject);
            MetadataTracer.singleton().traceFieldAccess(classObject, name.toString(), ConfigurationMemberInfo.ConfigurationMemberDeclaration.DECLARED);
        }
        boolean foundClass = false;
        for (var dictionary : layeredSingletons()) {
            JNIAccessibleClass clazz = dictionary.classesByClassObject.get(classObject);
            if (clazz != null) {
                foundClass = true;
                JNIAccessibleField field = clazz.getField(name);
                if (field != null && (field.isStatic() == isStatic || field.isNegative())) {
                    return field;
                }
            }
        }
        dump(!foundClass && dumpLabel != null, dumpLabel);
        return null;
    }

    public static JNIFieldId getDeclaredFieldID(Class<?> classObject, String name, boolean isStatic) {
        JNIAccessibleField field = getDeclaredField(classObject, name, isStatic, "getDeclaredFieldID");
        field = checkField(field, classObject, name);
        return (field != null) ? field.getId() : Word.nullPointer();
    }

    private static JNIAccessibleField findField(Class<?> clazz, CharSequence name, boolean isStatic, String dumpLabel) {
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

    private static JNIAccessibleField findSuperinterfaceField(Class<?> clazz, CharSequence name) {
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

    public static JNIFieldId getFieldID(Class<?> clazz, CharSequence name, boolean isStatic) {
        JNIAccessibleField field = findField(clazz, name, isStatic, "getFieldID");
        field = checkField(field, clazz, name);
        return (field != null && field.isDiscoverableIn(clazz)) ? field.getId() : Word.nullPointer();
    }

    public static String getFieldNameByID(Class<?> classObject, JNIFieldId id) {
        for (var dictionary : layeredSingletons()) {
            JNIAccessibleClass clazz = dictionary.classesByClassObject.get(classObject);
            if (clazz != null) {
                UnmodifiableMapCursor<CharSequence, JNIAccessibleField> fieldsCursor = clazz.getFields();
                while (fieldsCursor.advance()) {
                    JNIAccessibleField field = fieldsCursor.getValue();
                    if (id.equal(field.getId())) {
                        VMError.guarantee(!field.isNegative(), "Existing fields can't correspond to a negative query");
                        return (String) fieldsCursor.getKey();
                    }
                }
            }
        }
        return null;
    }

    private static JNIAccessibleField checkField(JNIAccessibleField field, Class<?> clazz, CharSequence name) {
        if (throwMissingRegistrationErrors() && field == null) {
            MissingJNIRegistrationUtils.reportFieldAccess(clazz, name.toString());
        } else if (field != null && field.isNegative()) {
            return null;
        }
        return field;
    }

    public static JNIAccessibleMethodDescriptor getMethodDescriptor(JNIAccessibleMethod method) {
        if (method != null) {
            JNIAccessibleClass clazz = method.getDeclaringClass();
            var cursor = clazz.getMethods();
            while (cursor.advance()) {
                if (cursor.getValue() == method) {
                    return cursor.getKey();
                }
            }
        }
        return null;
    }

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.ALL_ACCESS;
    }
}
