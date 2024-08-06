/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.core.fieldvaluetransformer.FieldValueTransformerWithAvailability;
import com.oracle.svm.core.graal.nodes.FieldOffsetNode;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * See JavaDoc of VarHandleFeature in the hosted project for an overview.
 */
public abstract class VarHandleSupport {
    public static VarHandleSupport singleton() {
        return ImageSingletons.lookup(VarHandleSupport.class);
    }

    protected abstract ResolvedJavaField findVarHandleField(Object varHandle);
}

abstract class VarHandleFieldOffsetComputer implements FieldValueTransformerWithAvailability {

    private final JavaKind kind;

    VarHandleFieldOffsetComputer(JavaKind kind) {
        this.kind = kind;
    }

    @Override
    public ValueAvailability valueAvailability() {
        return ValueAvailability.AfterAnalysis;
    }

    @Override
    public Object transform(Object receiver, Object originalValue) {
        ResolvedJavaField field = VarHandleSupport.singleton().findVarHandleField(receiver);
        int offset = field.getOffset();
        if (offset <= 0) {
            throw VMError.shouldNotReachHere("Field is not marked as unsafe accessed: " + field);
        }

        switch (kind) {
            case Int:
                return Integer.valueOf(offset);
            case Long:
                return Long.valueOf(offset);
            default:
                throw VMError.shouldNotReachHere("Invalid kind: " + kind);
        }
    }

    @Override
    public ValueNode intrinsify(CoreProviders providers, JavaConstant receiver) {
        Object varHandle = providers.getSnippetReflection().asObject(Object.class, receiver);
        if (varHandle != null) {
            ResolvedJavaField field = VarHandleSupport.singleton().findVarHandleField(varHandle);
            return FieldOffsetNode.create(kind, field);
        }
        return null;
    }
}

class VarHandleFieldOffsetAsIntComputer extends VarHandleFieldOffsetComputer {
    VarHandleFieldOffsetAsIntComputer() {
        super(JavaKind.Int);
    }
}

class VarHandleFieldOffsetAsLongComputer extends VarHandleFieldOffsetComputer {
    VarHandleFieldOffsetAsLongComputer() {
        super(JavaKind.Long);
    }
}

class VarHandleStaticBaseComputer implements FieldValueTransformerWithAvailability {
    @Override
    public ValueAvailability valueAvailability() {
        return ValueAvailability.AfterAnalysis;
    }

    @Override
    public Object transform(Object receiver, Object originalValue) {
        ResolvedJavaField field = VarHandleSupport.singleton().findVarHandleField(receiver);
        return field.getType().getJavaKind().isPrimitive() ? StaticFieldsSupport.getStaticPrimitiveFields() : StaticFieldsSupport.getStaticObjectFields();
    }

    @Override
    public ValueNode intrinsify(CoreProviders providers, JavaConstant receiver) {
        Object varHandle = providers.getSnippetReflection().asObject(Object.class, receiver);
        if (varHandle != null) {
            ResolvedJavaField field = VarHandleSupport.singleton().findVarHandleField(varHandle);
            return StaticFieldsSupport.createStaticFieldBaseNode(field.getType().getJavaKind().isPrimitive());
        }
        return null;
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
 */

@TargetClass(className = "java.lang.invoke.VarHandleBooleans", innerClass = "FieldInstanceReadOnly")
final class Target_java_lang_invoke_VarHandleBooleans_FieldInstanceReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetAsLongComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleBytes", innerClass = "FieldInstanceReadOnly")
final class Target_java_lang_invoke_VarHandleBytes_FieldInstanceReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetAsLongComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleChars", innerClass = "FieldInstanceReadOnly")
final class Target_java_lang_invoke_VarHandleChars_FieldInstanceReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetAsLongComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleDoubles", innerClass = "FieldInstanceReadOnly")
final class Target_java_lang_invoke_VarHandleDoubles_FieldInstanceReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetAsLongComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleFloats", innerClass = "FieldInstanceReadOnly")
final class Target_java_lang_invoke_VarHandleFloats_FieldInstanceReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetAsLongComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleInts", innerClass = "FieldInstanceReadOnly")
final class Target_java_lang_invoke_VarHandleInts_FieldInstanceReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetAsLongComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleLongs", innerClass = "FieldInstanceReadOnly")
final class Target_java_lang_invoke_VarHandleLongs_FieldInstanceReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetAsLongComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleShorts", innerClass = "FieldInstanceReadOnly")
final class Target_java_lang_invoke_VarHandleShorts_FieldInstanceReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetAsLongComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleReferences", innerClass = "FieldInstanceReadOnly")
final class Target_java_lang_invoke_VarHandleReferences_FieldInstanceReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetAsLongComputer.class) //
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
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleStaticBaseComputer.class) //
    Object base;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetAsLongComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleBytes", innerClass = "FieldStaticReadOnly")
final class Target_java_lang_invoke_VarHandleBytes_FieldStaticReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleStaticBaseComputer.class) //
    Object base;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetAsLongComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleChars", innerClass = "FieldStaticReadOnly")
final class Target_java_lang_invoke_VarHandleChars_FieldStaticReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleStaticBaseComputer.class) //
    Object base;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetAsLongComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleDoubles", innerClass = "FieldStaticReadOnly")
final class Target_java_lang_invoke_VarHandleDoubles_FieldStaticReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleStaticBaseComputer.class) //
    Object base;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetAsLongComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleFloats", innerClass = "FieldStaticReadOnly")
final class Target_java_lang_invoke_VarHandleFloats_FieldStaticReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleStaticBaseComputer.class) //
    Object base;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetAsLongComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleInts", innerClass = "FieldStaticReadOnly")
final class Target_java_lang_invoke_VarHandleInts_FieldStaticReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleStaticBaseComputer.class) //
    Object base;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetAsLongComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleLongs", innerClass = "FieldStaticReadOnly")
final class Target_java_lang_invoke_VarHandleLongs_FieldStaticReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleStaticBaseComputer.class) //
    Object base;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetAsLongComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleShorts", innerClass = "FieldStaticReadOnly")
final class Target_java_lang_invoke_VarHandleShorts_FieldStaticReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleStaticBaseComputer.class) //
    Object base;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetAsLongComputer.class) //
    long fieldOffset;
}

@TargetClass(className = "java.lang.invoke.VarHandleReferences", innerClass = "FieldStaticReadOnly")
final class Target_java_lang_invoke_VarHandleReferences_FieldStaticReadOnly {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleStaticBaseComputer.class) //
    Object base;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = VarHandleFieldOffsetAsLongComputer.class) //
    long fieldOffset;
}

/*
 * DirectMethodHandle$Accessor and DirectMethodHandle$StaticAccessor predate VarHandle, but have a
 * similar purpose and must be handled similarly.
 */

@TargetClass(className = "java.lang.invoke.DirectMethodHandle", innerClass = "Accessor")
final class Target_java_lang_invoke_DirectMethodHandle_Accessor {
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = VarHandleFieldOffsetAsIntComputer.class) //
    int fieldOffset;
}

@TargetClass(className = "java.lang.invoke.DirectMethodHandle", innerClass = "StaticAccessor")
final class Target_java_lang_invoke_DirectMethodHandle_StaticAccessor {
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = VarHandleStaticBaseComputer.class) //
    Object staticBase;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = VarHandleFieldOffsetAsLongComputer.class) //
    long staticOffset;
}

@TargetClass(className = "java.lang.invoke.LazyInitializingVarHandle", onlyWith = JDKLatest.class)
final class Target_java_lang_invoke_LazyInitializingVarHandle {
    @Alias @RecomputeFieldValue(isFinal = true, kind = RecomputeFieldValue.Kind.None) //
    Class<?> refc;

    @Substitute
    void ensureInitialized() {
        /*
         * Without JIT compilation, there is no point in speculating on a @Stable initialized flag.
         * By emitting a EnsureClassInitializedNode, a VarHandle access to a static field is
         * optimized like a direct access of a static field, e.g., the class initialization check is
         * removed when the class initializer can be simulated.
         */
        EnsureClassInitializedNode.ensureClassInitialized(refc);
    }
}
