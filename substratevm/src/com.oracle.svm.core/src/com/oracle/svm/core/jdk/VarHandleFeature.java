/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToLongFunction;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.fieldvaluetransformer.FieldValueTransformerWithAvailability;
import com.oracle.svm.core.reflect.target.ReflectionSubstitutionSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import jdk.internal.misc.Unsafe;

/**
 * This file contains most of the code necessary for supporting VarHandle (and DirectMethodHandle
 * field accessors) in native images. The actual intrinsification of the accessors happens in
 * hosted-only code during inlining before analysis.
 *
 * The VarHandle implementation in the JDK uses some invokedynamic and method handles, but also a
 * lot of explicit Java code (a lot of it automatically generated): The main entry point from the
 * point of view of the user is the class VarHandle, which contains signature-polymorphic method
 * prototypes for the various access modes. However, we do not need to do anything special for the
 * VarHandle class: when we parse bytecode, all the bootstrapping has already happened on the Java
 * HotSpot VM, and the bytecode parser already sees calls to guard methods defined in
 * VarHandleGuards. Methods of that class are method handle intrinsification roots for inlining
 * before analysis. The intrinsification removes all the method handle invocation logic and reduces
 * the logic to a single call to the actual access logic. This logic is in various automatically
 * generated accessor classes named
 * "VarHandle{Booleans|Bytes|Chars|Doubles|Floats|Ints|Longs|Shorts|Objects}.{Array|FieldInstanceReadOnly|FieldInstanceReadWrite|FieldStaticReadOnly|FieldStaticReadWrite}".
 * The intrinsification might be able to inline these methods and even transform unsafe accesses by
 * offset to field accesses, but we cannot rely on it always being able to do in every case.
 *
 * The accessor classes for field access (both instance and static field access) store the offset of
 * the field that is used for Unsafe memory access. We need to 1) properly register these fields as
 * unsafe accessed so that our static analysis is correct, and 2) recompute the field offsets from
 * the hosted offsets to the runtime offsets. Luckily, we have all information to reconstruct the
 * original {@link Field} (see {@link #findVarHandleField}). The registration for unsafe access
 * happens in {@link #processReachableHandle} which is called for every relevant object once it
 * becomes reachable and so part of the image heap. The field offset recomputations are registered
 * for all classes manually (a bit of code duplication on our side), but all recomputations use the
 * same custom field value recomputation handler: {@link VarHandleFieldOffsetComputer}.
 *
 * For static fields, also the base of the Unsafe access needs to be changed to the static field
 * holder arrays defined in {@link StaticFieldsSupport}. We cannot do a recomputation to the actual
 * arrays because the arrays are only available after static analysis. So we inject accessor methods
 * instead that read our holder fields: {@link VarHandleFieldStaticBasePrimitiveAccessor} and
 * {@link VarHandleFieldStaticBaseObjectAccessor}.
 *
 * VarHandle access to arrays is the simplest case: we only need field value recomputations for the
 * array base offset and array index shift.
 */
@AutomaticallyRegisteredFeature
public class VarHandleFeature implements InternalFeature {

    private final Map<Class<?>, VarHandleInfo> infos = new HashMap<>();

    private final ConcurrentMap<Object, Boolean> processedVarHandles = new ConcurrentHashMap<>();
    private Consumer<Field> markAsUnsafeAccessed;

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        try {
            for (String typeName : new String[]{"Booleans", "Bytes", "Chars", "Doubles", "Floats", "Ints", "Longs", "Shorts", "References"}) {
                buildInfo(false, "receiverType",
                                Class.forName("java.lang.invoke.VarHandle" + typeName + "$FieldInstanceReadOnly"),
                                Class.forName("java.lang.invoke.VarHandle" + typeName + "$FieldInstanceReadWrite"));
                buildInfo(true, "base",
                                Class.forName("java.lang.invoke.VarHandle" + typeName + "$FieldStaticReadOnly"),
                                Class.forName("java.lang.invoke.VarHandle" + typeName + "$FieldStaticReadWrite"));
            }

            Class<?> staticAccessorClass = Class.forName("java.lang.invoke.DirectMethodHandle$StaticAccessor");
            infos.put(staticAccessorClass, new VarHandleInfo(true, createOffsetFieldGetter(staticAccessorClass, "staticOffset"),
                            createTypeFieldGetter(staticAccessorClass, "staticBase")));

            Class<?> accessorClass = Class.forName("java.lang.invoke.DirectMethodHandle$Accessor");
            Function<Object, Class<?>> accessorTypeGetter = obj -> ((MethodHandle) obj).type().parameterType(0);
            infos.put(accessorClass, new VarHandleInfo(false, createOffsetFieldGetter(accessorClass, "fieldOffset"), accessorTypeGetter));

        } catch (ClassNotFoundException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    private void buildInfo(boolean isStatic, String typeFieldName, Class<?> readOnlyClass, Class<?> readWriteClass) {
        ToLongFunction<Object> offsetGetter = createOffsetFieldGetter(readOnlyClass, "fieldOffset");
        Function<Object, Class<?>> typeGetter = createTypeFieldGetter(readOnlyClass, typeFieldName);
        VarHandleInfo readOnlyInfo = new VarHandleInfo(isStatic, offsetGetter, typeGetter);
        infos.put(readOnlyClass, readOnlyInfo);
        infos.put(readWriteClass, readOnlyInfo);
    }

    private static ToLongFunction<Object> createOffsetFieldGetter(Class<?> clazz, String offsetFieldName) {
        Field offsetField = ReflectionUtil.lookupField(clazz, offsetFieldName);
        return obj -> {
            try {
                return offsetField.getLong(obj);
            } catch (IllegalAccessException e) {
                throw VMError.shouldNotReachHere(e);
            }
        };
    }

    private static Function<Object, Class<?>> createTypeFieldGetter(Class<?> clazz, String typeFieldName) {
        Field typeField = ReflectionUtil.lookupField(clazz, typeFieldName);
        return obj -> {
            try {
                return (Class<?>) typeField.get(obj);
            } catch (IllegalAccessException e) {
                throw VMError.shouldNotReachHere(e);
            }
        };
    }

    /**
     * Find the original {@link Field} referenced by a VarHandle that accesses an instance field or
     * a static field. The VarHandle stores the field offset and the holder type. We iterate all
     * fields of that type and look for the field with a matching offset.
     */
    Field findVarHandleField(Object varHandle) {
        VarHandleInfo info = infos.get(varHandle.getClass());
        long originalFieldOffset = info.offsetGetter.applyAsLong(varHandle);
        Class<?> type = info.typeGetter.apply(varHandle);

        for (Class<?> cur = type; cur != null; cur = cur.getSuperclass()) {
            /* Search the declared fields for a field with a matching offset. */
            for (Field field : cur.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) == info.isStatic) {
                    long fieldOffset = info.isStatic ? Unsafe.getUnsafe().staticFieldOffset(field) : Unsafe.getUnsafe().objectFieldOffset(field);
                    if (fieldOffset == originalFieldOffset) {
                        return field;
                    }
                }
            }

            if (info.isStatic) {
                /*
                 * For instance fields, we need to search the whole class hierarchy. For static
                 * fields, we must only search the exact class.
                 */
                break;
            }
        }

        throw VMError.shouldNotReachHere("Could not find field referenced in VarHandle: " + type + ", offset = " + originalFieldOffset + ", isStatic = " + info.isStatic);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        markAsUnsafeAccessed = access::registerAsUnsafeAccessed;
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        markAsUnsafeAccessed = null;
    }

    public void registerHeapVarHandle(VarHandle varHandle) {
        processReachableHandle(varHandle);
    }

    public void registerHeapMethodHandle(MethodHandle directMethodHandle) {
        processReachableHandle(directMethodHandle);
    }

    /**
     * Register all fields accessed by a VarHandle for an instance field or a static field as unsafe
     * accessed, which is necessary for correctness of the static analysis. We want to process every
     * VarHandle only once, therefore we mark all VarHandle that were already processed in
     * {@link #processedVarHandles}.
     */
    private Object processReachableHandle(Object obj) {
        VarHandleInfo info = infos.get(obj.getClass());
        if (info != null && processedVarHandles.putIfAbsent(obj, true) == null) {
            Field field = findVarHandleField(obj);
            /*
             * It is OK if we see a new VarHandle after analysis, as long as the field itself was
             * already registered as Unsafe accessed by another VarHandle during analysis. This can
             * happen when the late class initializer analysis determines that a class is safe for
             * initialization at build time after the analysis.
             */
            if (processedVarHandles.putIfAbsent(field, true) == null) {
                VMError.guarantee(markAsUnsafeAccessed != null, "New VarHandle found after static analysis");
                markAsUnsafeAccessed.accept(field);
            }
        }
        return obj;
    }
}

class VarHandleInfo {
    final boolean isStatic;
    final ToLongFunction<Object> offsetGetter;
    final Function<Object, Class<?>> typeGetter;

    VarHandleInfo(boolean isStatic, ToLongFunction<Object> offsetGetter, Function<Object, Class<?>> typeGetter) {
        this.isStatic = isStatic;
        this.offsetGetter = offsetGetter;
        this.typeGetter = typeGetter;
    }
}

class VarHandleFieldOffsetIntComputer implements FieldValueTransformerWithAvailability {
    @Override
    public ValueAvailability valueAvailability() {
        return ValueAvailability.AfterAnalysis;
    }

    @Override
    public Object transform(Object receiver, Object originalValue) {
        Field field = ImageSingletons.lookup(VarHandleFeature.class).findVarHandleField(receiver);
        int offset = ImageSingletons.lookup(ReflectionSubstitutionSupport.class).getFieldOffset(field, true);
        if (offset <= 0) {
            throw VMError.shouldNotReachHere("Field is not marked as unsafe accessed: " + field);
        }
        return offset;
    }
}

class VarHandleFieldOffsetComputer extends VarHandleFieldOffsetIntComputer {
    @Override
    public Object transform(Object receiver, Object originalValue) {
        Object offset = super.transform(receiver, originalValue);
        return Long.valueOf((Integer) offset);
    }
}

class VarHandleFieldStaticBasePrimitiveAccessor {
    static Object get(@SuppressWarnings("unused") Object varHandle) {
        return StaticFieldsSupport.getStaticPrimitiveFields();
    }

    @SuppressWarnings("unused")
    static void set(Object varHandle, Object value) {
        assert value == StaticFieldsSupport.getStaticPrimitiveFields();
    }
}

class VarHandleFieldStaticBaseObjectAccessor {
    static Object get(@SuppressWarnings("unused") Object varHandle) {
        return StaticFieldsSupport.getStaticObjectFields();
    }

    @SuppressWarnings("unused")
    static void set(Object varHandle, Object value) {
        assert value == StaticFieldsSupport.getStaticObjectFields();
    }
}

/*
 * Substitutions for VarHandle array access classes. They all follow the same pattern: the array
 * base offset and array index shift is stored in instance fields, and we recompute the instance
 * fields.
 *
 * I don't know why the offset and shift are actually stored in fields: since the access classes are
 * already type-specialized, the offset and shift could actually be hard-coded directly in the class
 * (instead of storing it in each individual VarHandle instance). But we just need to handle what
 * the JDK gives us.
 */
@TargetClass(className = "java.lang.invoke.VarHandleBooleans", innerClass = "Array")
final class Target_java_lang_invoke_VarHandleBooleans_Array {
    @Alias @RecomputeFieldValue(kind = Kind.ArrayBaseOffset, declClass = boolean[].class, isFinal = true) //
    int abase;
    @Alias @RecomputeFieldValue(kind = Kind.ArrayIndexShift, declClass = boolean[].class, isFinal = true) //
    int ashift;
}

@TargetClass(className = "java.lang.invoke.VarHandleBytes", innerClass = "Array")
final class Target_java_lang_invoke_VarHandleBytes_Array {
    @Alias @RecomputeFieldValue(kind = Kind.ArrayBaseOffset, declClass = byte[].class, isFinal = true) //
    int abase;
    @Alias @RecomputeFieldValue(kind = Kind.ArrayIndexShift, declClass = byte[].class, isFinal = true) //
    int ashift;
}

@TargetClass(className = "java.lang.invoke.VarHandleChars", innerClass = "Array")
final class Target_java_lang_invoke_VarHandleChars_Array {
    @Alias @RecomputeFieldValue(kind = Kind.ArrayBaseOffset, declClass = char[].class, isFinal = true) //
    int abase;
    @Alias @RecomputeFieldValue(kind = Kind.ArrayIndexShift, declClass = char[].class, isFinal = true) //
    int ashift;
}

@TargetClass(className = "java.lang.invoke.VarHandleDoubles", innerClass = "Array")
final class Target_java_lang_invoke_VarHandleDoubles_Array {
    @Alias @RecomputeFieldValue(kind = Kind.ArrayBaseOffset, declClass = double[].class, isFinal = true) //
    int abase;
    @Alias @RecomputeFieldValue(kind = Kind.ArrayIndexShift, declClass = double[].class, isFinal = true) //
    int ashift;
}

@TargetClass(className = "java.lang.invoke.VarHandleFloats", innerClass = "Array")
final class Target_java_lang_invoke_VarHandleFloats_Array {
    @Alias @RecomputeFieldValue(kind = Kind.ArrayBaseOffset, declClass = float[].class, isFinal = true) //
    int abase;
    @Alias @RecomputeFieldValue(kind = Kind.ArrayIndexShift, declClass = float[].class, isFinal = true) //
    int ashift;
}

@TargetClass(className = "java.lang.invoke.VarHandleInts", innerClass = "Array")
final class Target_java_lang_invoke_VarHandleInts_Array {
    @Alias @RecomputeFieldValue(kind = Kind.ArrayBaseOffset, declClass = int[].class, isFinal = true) //
    int abase;
    @Alias @RecomputeFieldValue(kind = Kind.ArrayIndexShift, declClass = int[].class, isFinal = true) //
    int ashift;
}

@TargetClass(className = "java.lang.invoke.VarHandleLongs", innerClass = "Array")
final class Target_java_lang_invoke_VarHandleLongs_Array {
    @Alias @RecomputeFieldValue(kind = Kind.ArrayBaseOffset, declClass = long[].class, isFinal = true) //
    int abase;
    @Alias @RecomputeFieldValue(kind = Kind.ArrayIndexShift, declClass = long[].class, isFinal = true) //
    int ashift;
}

@TargetClass(className = "java.lang.invoke.VarHandleShorts", innerClass = "Array")
final class Target_java_lang_invoke_VarHandleShorts_Array {
    @Alias @RecomputeFieldValue(kind = Kind.ArrayBaseOffset, declClass = short[].class, isFinal = true) //
    int abase;
    @Alias @RecomputeFieldValue(kind = Kind.ArrayIndexShift, declClass = short[].class, isFinal = true) //
    int ashift;
}

@TargetClass(className = "java.lang.invoke.VarHandleReferences", innerClass = "Array")
final class Target_java_lang_invoke_VarHandleReferences_Array {
    @Alias @RecomputeFieldValue(kind = Kind.ArrayBaseOffset, declClass = Object[].class, isFinal = true) //
    int abase;
    @Alias @RecomputeFieldValue(kind = Kind.ArrayIndexShift, declClass = Object[].class, isFinal = true) //
    int ashift;
}

/*
 * Substitutions for VarHandle instance field access classes. They all follow the same pattern: they
 * store the receiver type (no need to recompute that) and the field offset (we need to recompute
 * that).
 *
 * Because the offset field values can be set only after instance field offsets are assigned
 * following the analysis, the unsafe accesses cannot be constant-folded unless inlining before
 * analysis is already successful in transforming them to a field access.
 */

@TargetClass(className = "java.lang.invoke.VarHandleBooleans", innerClass = "FieldInstanceReadOnly")
final class Target_java_lang_invoke_VarHandleBooleans_FieldInstanceReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleBytes", innerClass = "FieldInstanceReadOnly")
final class Target_java_lang_invoke_VarHandleBytes_FieldInstanceReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleChars", innerClass = "FieldInstanceReadOnly")
final class Target_java_lang_invoke_VarHandleChars_FieldInstanceReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleDoubles", innerClass = "FieldInstanceReadOnly")
final class Target_java_lang_invoke_VarHandleDoubles_FieldInstanceReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleFloats", innerClass = "FieldInstanceReadOnly")
final class Target_java_lang_invoke_VarHandleFloats_FieldInstanceReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleInts", innerClass = "FieldInstanceReadOnly")
final class Target_java_lang_invoke_VarHandleInts_FieldInstanceReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleLongs", innerClass = "FieldInstanceReadOnly")
final class Target_java_lang_invoke_VarHandleLongs_FieldInstanceReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleShorts", innerClass = "FieldInstanceReadOnly")
final class Target_java_lang_invoke_VarHandleShorts_FieldInstanceReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleReferences", innerClass = "FieldInstanceReadOnly")
final class Target_java_lang_invoke_VarHandleReferences_FieldInstanceReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

/*
 * Substitutions for VarHandle static field access classes. They all follow the same pattern: the
 * field offset recomputation is the same as for the instance field access classes. In addition, we
 * also need to recompute the static field base: it is the java.lang.Class instance on the HotSpot
 * VM, but a single byte[] array (for primitive types) or Object[] array (for Object types).
 */

@TargetClass(className = "java.lang.invoke.VarHandleBooleans", innerClass = "FieldStaticReadOnly")
final class Target_java_lang_invoke_VarHandleBooleans_FieldStaticReadOnly {
    @Alias @InjectAccessors(VarHandleFieldStaticBasePrimitiveAccessor.class) //
    Object base;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleBytes", innerClass = "FieldStaticReadOnly")
final class Target_java_lang_invoke_VarHandleBytes_FieldStaticReadOnly {
    @Alias @InjectAccessors(VarHandleFieldStaticBasePrimitiveAccessor.class) //
    Object base;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleChars", innerClass = "FieldStaticReadOnly")
final class Target_java_lang_invoke_VarHandleChars_FieldStaticReadOnly {
    @Alias @InjectAccessors(VarHandleFieldStaticBasePrimitiveAccessor.class) //
    Object base;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleDoubles", innerClass = "FieldStaticReadOnly")
final class Target_java_lang_invoke_VarHandleDoubles_FieldStaticReadOnly {
    @Alias @InjectAccessors(VarHandleFieldStaticBasePrimitiveAccessor.class) //
    Object base;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleFloats", innerClass = "FieldStaticReadOnly")
final class Target_java_lang_invoke_VarHandleFloats_FieldStaticReadOnly {
    @Alias @InjectAccessors(VarHandleFieldStaticBasePrimitiveAccessor.class) //
    Object base;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleInts", innerClass = "FieldStaticReadOnly")
final class Target_java_lang_invoke_VarHandleInts_FieldStaticReadOnly {
    @Alias @InjectAccessors(VarHandleFieldStaticBasePrimitiveAccessor.class) //
    Object base;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleLongs", innerClass = "FieldStaticReadOnly")
final class Target_java_lang_invoke_VarHandleLongs_FieldStaticReadOnly {
    @Alias @InjectAccessors(VarHandleFieldStaticBasePrimitiveAccessor.class) //
    Object base;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleShorts", innerClass = "FieldStaticReadOnly")
final class Target_java_lang_invoke_VarHandleShorts_FieldStaticReadOnly {
    @Alias @InjectAccessors(VarHandleFieldStaticBasePrimitiveAccessor.class) //
    Object base;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleReferences", innerClass = "FieldStaticReadOnly")
final class Target_java_lang_invoke_VarHandleReferences_FieldStaticReadOnly {
    @Alias @InjectAccessors(VarHandleFieldStaticBaseObjectAccessor.class) //
    Object base;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

/*
 * DirectMethodHandle$Accessor and DirectMethodHandle$StaticAccessor predate VarHandle, but have a
 * similar purpose and must be handled similarly.
 */

@TargetClass(className = "java.lang.invoke.DirectMethodHandle", innerClass = "Accessor")
final class Target_java_lang_invoke_DirectMethodHandle_Accessor {
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = VarHandleFieldOffsetIntComputer.class) //
    int fieldOffset;
}

@TargetClass(className = "java.lang.invoke.DirectMethodHandle", innerClass = "StaticAccessor")
final class Target_java_lang_invoke_DirectMethodHandle_StaticAccessor {
    @Alias //
    Class<?> fieldType;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = StaticAccessorFieldStaticBaseComputer.class) //
    Object staticBase;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long staticOffset;
}

class StaticAccessorFieldStaticBaseComputer implements FieldValueTransformerWithAvailability {
    @Override
    public FieldValueTransformerWithAvailability.ValueAvailability valueAvailability() {
        return FieldValueTransformerWithAvailability.ValueAvailability.AfterAnalysis;
    }

    @Override
    public Object transform(Object receiver, Object originalValue) {
        Field field = ImageSingletons.lookup(VarHandleFeature.class).findVarHandleField(receiver);
        if (field.getType().isPrimitive()) {
            return StaticFieldsSupport.getStaticPrimitiveFields();
        }
        return StaticFieldsSupport.getStaticObjectFields();
    }
}
