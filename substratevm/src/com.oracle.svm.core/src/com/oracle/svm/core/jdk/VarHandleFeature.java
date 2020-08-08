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

//Checkstyle: allow reflection

import static com.oracle.svm.core.util.VMError.guarantee;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Function;

import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.meta.SharedField;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import sun.misc.Unsafe;

/**
 * This file contains most of the code necessary for supporting VarHandle in native images. The
 * actual intrinsification of VarHandle accessors is in hosted-only code in the
 * IntrinsifyMethodHandlesInvocationPlugin.
 *
 * The VarHandle implementation in the JDK uses some invokedynamic and method handles, but also a
 * lot of explicit Java code (a lot of it automatically generated): The main entry point from the
 * point of view of the user is the class VarHandle, which contains signature-polymorphic method
 * prototypes for the various access modes. However, we do not need to do anything special for the
 * VarHandle class: when we parse bytecode, all the bootstrapping has already happened on the Java
 * HotSpot VM, and the bytecode parser already sees calls to guard methods defined in
 * VarHandleGuards. Method of that class are the intrinsification root for the
 * IntrinsifyMethodHandlesInvocationPlugin. The intrinsification removes all the method handle
 * invocation logic and reduces the logic to a single call to the actual access logic. This logic is
 * in various automatically generated accessor classes named
 * "VarHandle{Booleans|Bytes|Chars|Doubles|Floats|Ints|Longs|Shorts|Objects}.{Array|FieldInstanceReadOnly|FieldInstanceReadWrite|FieldStaticReadOnly|FieldStaticReadWrite}".
 * The intrinsification must not inline these methods, because they contain complicated logic.
 *
 * The accessor classes for field access (both instance and static field access) store the offset of
 * the field that is used for Unsafe memory access. We need to 1) properly register these fields as
 * unsafe accessed so that our static analysis is correct, and 2) recompute the field offsets from
 * the hosted offsets to the runtime offsets. Luckily, we have all information to reconstruct the
 * original {@link Field} (see {@link #findVarHandleField}). The registration for unsafe access
 * happens in an object replacer: the method {@link #processVarHandle} is called for every object
 * (and therefore every VarHandle) that is reachable in the image heap. The field offset
 * recomputations are registered for all classes manually (a bit of code duplication on our side),
 * but all recomputations use the same custom field value recomputation handler:
 * {@link VarHandleFieldOffsetComputer}.
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
@AutomaticFeature
public class VarHandleFeature implements Feature {
    private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();

    /** The JDK 11 class VarHandleObjects got renamed to VarHandleReferences. */
    static final String OBJECT_SUFFIX = JavaVersionUtil.JAVA_SPEC > 11 ? "References" : "Objects";

    private final Map<Class<?>, VarHandleInfo> infos = new HashMap<>();

    private final ConcurrentMap<Object, Boolean> processedVarHandles = new ConcurrentHashMap<>();
    private Consumer<Field> markAsUnsafeAccessed;

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return JavaVersionUtil.JAVA_SPEC >= 11;
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        try {
            for (String typeName : new String[]{"Booleans", "Bytes", "Chars", "Doubles", "Floats", "Ints", "Longs", "Shorts", OBJECT_SUFFIX}) {
                // Checkstyle: stop
                buildInfo(false, "receiverType",
                                Class.forName("java.lang.invoke.VarHandle" + typeName + "$FieldInstanceReadOnly"),
                                Class.forName("java.lang.invoke.VarHandle" + typeName + "$FieldInstanceReadWrite"));
                buildInfo(true, "base",
                                Class.forName("java.lang.invoke.VarHandle" + typeName + "$FieldStaticReadOnly"),
                                Class.forName("java.lang.invoke.VarHandle" + typeName + "$FieldStaticReadWrite"));
                // Checkstyle: resume
            }
        } catch (ClassNotFoundException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    private void buildInfo(boolean isStatic, String typeFieldName, Class<?> readOnlyClass, Class<?> readWriteClass) {
        VarHandleInfo info = new VarHandleInfo(isStatic, ReflectionUtil.lookupField(readOnlyClass, "fieldOffset"), ReflectionUtil.lookupField(readOnlyClass, typeFieldName));
        infos.put(readOnlyClass, info);
        infos.put(readWriteClass, info);
    }

    /**
     * Find the original {@link Field} referenced by a VarHandle that accessed an instance field or
     * a static field field. The VarHandle stores the field offset and the holder type. We iterate
     * all fields of that type and look for the field with a matching offset.
     */
    Field findVarHandleField(Object varHandle) {
        try {
            VarHandleInfo info = infos.get(varHandle.getClass());
            long originalFieldOffset = info.fieldOffsetField.getLong(varHandle);
            Class<?> type = (Class<?>) info.typeField.get(varHandle);

            for (Class<?> cur = type; cur != null; cur = cur.getSuperclass()) {
                /* Search the declared fields for a field with a matching offset. */
                for (Field field : cur.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) == info.isStatic) {
                        long fieldOffset = info.isStatic ? UNSAFE.staticFieldOffset(field) : UNSAFE.objectFieldOffset(field);
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
        } catch (ReflectiveOperationException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        access.registerObjectReplacer(this::processVarHandle);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        markAsUnsafeAccessed = access::registerAsUnsafeAccessed;
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        markAsUnsafeAccessed = null;
    }

    /**
     * Register all fields accessed by a VarHandle for an instance field or a static field as unsafe
     * accessed, which is necessary for correctness of the static analysis. We want to process every
     * VarHandle only once, therefore we mark all VarHandle that were already processed in in
     * {@link #processedVarHandles}.
     */
    private Object processVarHandle(Object obj) {
        VarHandleInfo info = infos.get(obj.getClass());
        if (info != null && processedVarHandles.putIfAbsent(obj, true) == null) {
            VMError.guarantee(markAsUnsafeAccessed != null, "New VarHandle found after static analysis");

            Field field = findVarHandleField(obj);
            markAsUnsafeAccessed.accept(field);
        }
        return obj;
    }
}

class VarHandleInfo {
    final boolean isStatic;
    final Field fieldOffsetField;
    final Field typeField;

    VarHandleInfo(boolean isStatic, Field fieldOffsetField, Field typeField) {
        this.isStatic = isStatic;
        this.fieldOffsetField = fieldOffsetField;
        this.typeField = typeField;
    }
}

class VarHandleFieldOffsetComputer implements RecomputeFieldValue.CustomFieldValueComputer {
    @Override
    public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object varHandle) {
        Field field = ImageSingletons.lookup(VarHandleFeature.class).findVarHandleField(varHandle);
        SharedField sField = (SharedField) metaAccess.lookupJavaField(field);

        guarantee(sField.isAccessed() && sField.getLocation() > 0, "Field not marked as accessed");
        return Long.valueOf(sField.getLocation());
    }
}

class VarHandleFieldStaticBasePrimitiveAccessor {
    static Object get(@SuppressWarnings("unused") Object varHandle) {
        return StaticFieldsSupport.getStaticPrimitiveFields();
    }
}

class VarHandleFieldStaticBaseObjectAccessor {
    static Object get(@SuppressWarnings("unused") Object varHandle) {
        return StaticFieldsSupport.getStaticObjectFields();
    }
}

class VarHandleObjectsClassNameProvider implements Function<TargetClass, String> {
    @Override
    public String apply(TargetClass t) {
        return "java.lang.invoke.VarHandle" + VarHandleFeature.OBJECT_SUFFIX;
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
@TargetClass(className = "java.lang.invoke.VarHandleBooleans", innerClass = "Array", onlyWith = JDK11OrLater.class)
final class Target_java_lang_invoke_VarHandleBooleans_Array {
    @Alias @RecomputeFieldValue(kind = Kind.ArrayBaseOffset, declClass = boolean[].class) //
    int abase;
    @Alias @RecomputeFieldValue(kind = Kind.ArrayIndexShift, declClass = boolean[].class) //
    int ashift;
}

@TargetClass(className = "java.lang.invoke.VarHandleBytes", innerClass = "Array", onlyWith = JDK11OrLater.class)
final class Target_java_lang_invoke_VarHandleBytes_Array {
    @Alias @RecomputeFieldValue(kind = Kind.ArrayBaseOffset, declClass = byte[].class) //
    int abase;
    @Alias @RecomputeFieldValue(kind = Kind.ArrayIndexShift, declClass = byte[].class) //
    int ashift;
}

@TargetClass(className = "java.lang.invoke.VarHandleChars", innerClass = "Array", onlyWith = JDK11OrLater.class)
final class Target_java_lang_invoke_VarHandleChars_Array {
    @Alias @RecomputeFieldValue(kind = Kind.ArrayBaseOffset, declClass = char[].class) //
    int abase;
    @Alias @RecomputeFieldValue(kind = Kind.ArrayIndexShift, declClass = char[].class) //
    int ashift;
}

@TargetClass(className = "java.lang.invoke.VarHandleDoubles", innerClass = "Array", onlyWith = JDK11OrLater.class)
final class Target_java_lang_invoke_VarHandleDoubles_Array {
    @Alias @RecomputeFieldValue(kind = Kind.ArrayBaseOffset, declClass = double[].class) //
    int abase;
    @Alias @RecomputeFieldValue(kind = Kind.ArrayIndexShift, declClass = double[].class) //
    int ashift;
}

@TargetClass(className = "java.lang.invoke.VarHandleFloats", innerClass = "Array", onlyWith = JDK11OrLater.class)
final class Target_java_lang_invoke_VarHandleFloats_Array {
    @Alias @RecomputeFieldValue(kind = Kind.ArrayBaseOffset, declClass = float[].class) //
    int abase;
    @Alias @RecomputeFieldValue(kind = Kind.ArrayIndexShift, declClass = float[].class) //
    int ashift;
}

@TargetClass(className = "java.lang.invoke.VarHandleInts", innerClass = "Array", onlyWith = JDK11OrLater.class)
final class Target_java_lang_invoke_VarHandleInts_Array {
    @Alias @RecomputeFieldValue(kind = Kind.ArrayBaseOffset, declClass = int[].class) //
    int abase;
    @Alias @RecomputeFieldValue(kind = Kind.ArrayIndexShift, declClass = int[].class) //
    int ashift;
}

@TargetClass(className = "java.lang.invoke.VarHandleLongs", innerClass = "Array", onlyWith = JDK11OrLater.class)
final class Target_java_lang_invoke_VarHandleLongs_Array {
    @Alias @RecomputeFieldValue(kind = Kind.ArrayBaseOffset, declClass = long[].class) //
    int abase;
    @Alias @RecomputeFieldValue(kind = Kind.ArrayIndexShift, declClass = long[].class) //
    int ashift;
}

@TargetClass(className = "java.lang.invoke.VarHandleShorts", innerClass = "Array", onlyWith = JDK11OrLater.class)
final class Target_java_lang_invoke_VarHandleShorts_Array {
    @Alias @RecomputeFieldValue(kind = Kind.ArrayBaseOffset, declClass = short[].class) //
    int abase;
    @Alias @RecomputeFieldValue(kind = Kind.ArrayIndexShift, declClass = short[].class) //
    int ashift;
}

@TargetClass(classNameProvider = VarHandleObjectsClassNameProvider.class, innerClass = "Array", onlyWith = JDK11OrLater.class)
final class Target_java_lang_invoke_VarHandleObjects_Array {
    @Alias @RecomputeFieldValue(kind = Kind.ArrayBaseOffset, declClass = Object[].class) //
    int abase;
    @Alias @RecomputeFieldValue(kind = Kind.ArrayIndexShift, declClass = Object[].class) //
    int ashift;
}

/*
 * Substitutions for VarHandle instance field access classes. They all follow the same pattern: they
 * store the receiver type (no need to recompute that) and the field offset (we need to recompute
 * that).
 */

@TargetClass(className = "java.lang.invoke.VarHandleBooleans", innerClass = "FieldInstanceReadOnly", onlyWith = JDK11OrLater.class)
final class Target_java_lang_invoke_VarHandleBooleans_FieldInstanceReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleBytes", innerClass = "FieldInstanceReadOnly", onlyWith = JDK11OrLater.class)
final class Target_java_lang_invoke_VarHandleBytes_FieldInstanceReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleChars", innerClass = "FieldInstanceReadOnly", onlyWith = JDK11OrLater.class)
final class Target_java_lang_invoke_VarHandleChars_FieldInstanceReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleDoubles", innerClass = "FieldInstanceReadOnly", onlyWith = JDK11OrLater.class)
final class Target_java_lang_invoke_VarHandleDoubles_FieldInstanceReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleFloats", innerClass = "FieldInstanceReadOnly", onlyWith = JDK11OrLater.class)
final class Target_java_lang_invoke_VarHandleFloats_FieldInstanceReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleInts", innerClass = "FieldInstanceReadOnly", onlyWith = JDK11OrLater.class)
final class Target_java_lang_invoke_VarHandleInts_FieldInstanceReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleLongs", innerClass = "FieldInstanceReadOnly", onlyWith = JDK11OrLater.class)
final class Target_java_lang_invoke_VarHandleLongs_FieldInstanceReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleShorts", innerClass = "FieldInstanceReadOnly", onlyWith = JDK11OrLater.class)
final class Target_java_lang_invoke_VarHandleShorts_FieldInstanceReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(classNameProvider = VarHandleObjectsClassNameProvider.class, innerClass = "FieldInstanceReadOnly", onlyWith = JDK11OrLater.class)
final class Target_java_lang_invoke_VarHandleObjects_FieldInstanceReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

/*
 * Substitutions for VarHandle static field access classes. They all follow the same pattern: the
 * field offset recomputation is the same as for the instance field access classes. In addition, we
 * also need to recompute the static field base: it is the java.lang.Class instance on the HotSpot
 * VM, but a single byte[] array (for primitive types) or Object[] array (for Object types).
 */

@TargetClass(className = "java.lang.invoke.VarHandleBooleans", innerClass = "FieldStaticReadOnly", onlyWith = JDK11OrLater.class)
final class Target_java_lang_invoke_VarHandleBooleans_FieldStaticReadOnly {
    @Alias @InjectAccessors(VarHandleFieldStaticBasePrimitiveAccessor.class) //
    Object base;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleBytes", innerClass = "FieldStaticReadOnly", onlyWith = JDK11OrLater.class)
final class Target_java_lang_invoke_VarHandleBytes_FieldStaticReadOnly {
    @Alias @InjectAccessors(VarHandleFieldStaticBasePrimitiveAccessor.class) //
    Object base;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleChars", innerClass = "FieldStaticReadOnly", onlyWith = JDK11OrLater.class)
final class Target_java_lang_invoke_VarHandleChars_FieldStaticReadOnly {
    @Alias @InjectAccessors(VarHandleFieldStaticBasePrimitiveAccessor.class) //
    Object base;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleDoubles", innerClass = "FieldStaticReadOnly", onlyWith = JDK11OrLater.class)
final class Target_java_lang_invoke_VarHandleDoubles_FieldStaticReadOnly {
    @Alias @InjectAccessors(VarHandleFieldStaticBasePrimitiveAccessor.class) //
    Object base;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleFloats", innerClass = "FieldStaticReadOnly", onlyWith = JDK11OrLater.class)
final class Target_java_lang_invoke_VarHandleFloats_FieldStaticReadOnly {
    @Alias @InjectAccessors(VarHandleFieldStaticBasePrimitiveAccessor.class) //
    Object base;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleInts", innerClass = "FieldStaticReadOnly", onlyWith = JDK11OrLater.class)
final class Target_java_lang_invoke_VarHandleInts_FieldStaticReadOnly {
    @Alias @InjectAccessors(VarHandleFieldStaticBasePrimitiveAccessor.class) //
    Object base;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleLongs", innerClass = "FieldStaticReadOnly", onlyWith = JDK11OrLater.class)
final class Target_java_lang_invoke_VarHandleLongs_FieldStaticReadOnly {
    @Alias @InjectAccessors(VarHandleFieldStaticBasePrimitiveAccessor.class) //
    Object base;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleShorts", innerClass = "FieldStaticReadOnly", onlyWith = JDK11OrLater.class)
final class Target_java_lang_invoke_VarHandleShorts_FieldStaticReadOnly {
    @Alias @InjectAccessors(VarHandleFieldStaticBasePrimitiveAccessor.class) //
    Object base;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(classNameProvider = VarHandleObjectsClassNameProvider.class, innerClass = "FieldStaticReadOnly", onlyWith = JDK11OrLater.class)
final class Target_java_lang_invoke_VarHandleObjects_FieldStaticReadOnly {
    @Alias @InjectAccessors(VarHandleFieldStaticBaseObjectAccessor.class) //
    Object base;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandle", onlyWith = JDK11OrLater.class)
final class Target_java_lang_invoke_VarHandle {

    /**
     * JDK 11 does not have an override of toString(), but later JDK versions do. The implementation
     * collects details about the MemberName, which are method handle internals that must not be
     * reachable.
     */
    @TargetElement(onlyWith = JDK14OrLater.class)
    @Substitute
    @Override
    public String toString() {
        return "VarHandle[printing VarHandle details is not supported on Substrate VM]";
    }
}
