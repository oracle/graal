/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.interpreter;

import static com.oracle.svm.interpreter.InterpreterOptions.DebuggerWithInterpreter;
import static com.oracle.svm.interpreter.InterpreterOptions.InterpreterTraceSupport;
import static com.oracle.svm.interpreter.InterpreterUtil.traceInterpreter;

import java.lang.reflect.Array;
import java.lang.reflect.Field;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.MissingReflectionRegistrationError;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.impl.ClassLoading;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordBase;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.meta.KnownOffsets;
import com.oracle.svm.core.graal.snippets.OpenTypeWorldDispatchTableSnippets;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubUtils;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.meta.MethodRef;
import com.oracle.svm.core.monitor.MonitorInflationCause;
import com.oracle.svm.core.monitor.MonitorSupport;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.espresso.shared.resolver.CallKind;
import com.oracle.svm.guest.staging.jdk.InternalVMMethod;
import com.oracle.svm.interpreter.metadata.CremaResolvedJavaMethodImpl;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaField;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedObjectType;
import com.oracle.svm.interpreter.metadata.MetadataUtil;
import com.oracle.svm.interpreter.metadata.ReferenceConstant;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaType;

@InternalVMMethod
public final class InterpreterToVM {

    private static final JavaKind WORD_KIND = ConfigurationValues.getWordKind();

    static {
        VMError.guarantee(WORD_KIND == JavaKind.Int || WORD_KIND == JavaKind.Long);
    }

    public static JavaKind wordJavaKind() {
        return WORD_KIND;
    }

    private InterpreterToVM() {
        throw VMError.shouldNotReachHereAtRuntime();
    }

    // region Get (array) operations

    public static int getArrayInt(int index, int[] array) throws SemanticJavaException {
        assert array != null;
        try {
            return array[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw SemanticJavaException.raise(e);
        }
    }

    public static Object getArrayObject(int index, Object[] array) throws SemanticJavaException {
        assert array != null;
        try {
            return array[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw SemanticJavaException.raise(e);
        }
    }

    public static long getArrayLong(int index, long[] array) throws SemanticJavaException {
        assert array != null;
        try {
            return array[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw SemanticJavaException.raise(e);
        }
    }

    public static WordBase getArrayWord(int index, WordBase[] wordArray) throws SemanticJavaException {
        assert wordArray != null;
        try {
            return wordArray[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw SemanticJavaException.raise(e);
        }
    }

    public static float getArrayFloat(int index, float[] array) throws SemanticJavaException {
        assert array != null;
        try {
            return array[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw SemanticJavaException.raise(e);
        }
    }

    public static double getArrayDouble(int index, double[] array) throws SemanticJavaException {
        assert array != null;
        try {
            return array[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw SemanticJavaException.raise(e);
        }
    }

    public static byte getArrayByte(int index, Object array) throws SemanticJavaException {
        assert array != null;
        try {
            if (array instanceof byte[]) {
                return ((byte[]) array)[index];
            } else {
                return ((boolean[]) array)[index] ? (byte) 1 : 0;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            throw SemanticJavaException.raise(e);
        }
    }

    public static char getArrayChar(int index, char[] array) throws SemanticJavaException {
        assert array != null;
        try {
            return array[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw SemanticJavaException.raise(e);
        }
    }

    public static short getArrayShort(int index, short[] array) throws SemanticJavaException {
        assert array != null;
        try {
            return array[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw SemanticJavaException.raise(e);
        }
    }

    // endregion

    // region Set (array) operations

    public static void setArrayInt(int value, int index, int[] array) throws SemanticJavaException {
        assert array != null;
        try {
            array[index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw SemanticJavaException.raise(e);
        }
    }

    public static void setArrayLong(long value, int index, long[] array) throws SemanticJavaException {
        assert array != null;
        try {
            array[index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw SemanticJavaException.raise(e);
        }
    }

    public static void setArrayWord(WordBase value, int index, WordBase[] array) throws SemanticJavaException {
        assert array != null;
        try {
            array[index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw SemanticJavaException.raise(e);
        }
    }

    public static void setArrayFloat(float value, int index, float[] array) throws SemanticJavaException {
        assert array != null;
        try {
            array[index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw SemanticJavaException.raise(e);
        }
    }

    public static void setArrayDouble(double value, int index, double[] array) throws SemanticJavaException {
        assert array != null;
        try {
            array[index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw SemanticJavaException.raise(e);
        }
    }

    public static void setArrayByte(byte value, int index, /* byte[].class or boolean[].class */ Object array) throws SemanticJavaException {
        assert array != null;
        try {
            if (array instanceof byte[]) {
                ((byte[]) array)[index] = value;
            } else {
                ((boolean[]) array)[index] = (value & 1) != 0; // masked from Java 9+.
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            throw SemanticJavaException.raise(e);
        }
    }

    public static void setArrayChar(char value, int index, char[] array) throws SemanticJavaException {
        assert array != null;
        try {
            array[index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw SemanticJavaException.raise(e);
        }
    }

    public static void setArrayShort(short value, int index, short[] array) throws SemanticJavaException {
        assert array != null;
        try {
            array[index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw SemanticJavaException.raise(e);
        }
    }

    public static void setArrayObject(Object value, int index, Object[] array) throws SemanticJavaException {
        assert array != null;
        try {
            array[index] = value;
        } catch (ArrayIndexOutOfBoundsException | ArrayStoreException e) {
            throw SemanticJavaException.raise(e);
        }
    }

    // endregion Set (array) operations

    // region Monitor enter/exit

    public static void monitorEnter(InterpreterFrame frame, Object obj) throws SemanticJavaException {
        assert obj != null;
        MonitorSupport.singleton().monitorEnter(obj, MonitorInflationCause.MONITOR_ENTER);
        frame.addLock(obj);
    }

    @SuppressFBWarnings(value = "IMSE_DONT_CATCH_IMSE", justification = "Intentional.")
    public static void monitorExit(InterpreterFrame frame, Object obj) throws SemanticJavaException {
        assert obj != null;
        try {
            MonitorSupport.singleton().monitorExit(obj, MonitorInflationCause.VM_INTERNAL);
            // GR-55049: Ensure that SVM doesn't allow non-structured locking.
            frame.removeLock(obj);
        } catch (IllegalMonitorStateException e) {
            // GR-55050: Hide intermediate frames on exception.
            throw SemanticJavaException.raise(e);
        }
    }

    @SuppressFBWarnings(value = "IMSE_DONT_CATCH_IMSE", justification = "Intentional.")
    public static void releaseInterpreterFrameLocks(@SuppressWarnings("unused") InterpreterFrame frame) throws SemanticJavaException {
        Object[] locks = frame.getLocks();
        for (int i = 0; i < locks.length; ++i) {
            Object ref = locks[i];
            if (ref != null) {
                try {
                    MonitorSupport.singleton().monitorExit(ref, MonitorInflationCause.VM_INTERNAL);
                    // GR-55049: Ensure that SVM doesn't allow non-structured locking.
                    locks[i] = null;
                } catch (IllegalMonitorStateException e) {
                    // GR-55050: Hide intermediate frames on exception.
                    throw SemanticJavaException.raise(e);
                }
            }
        }
    }

    // endregion

    private static final Unsafe U = initUnsafe();

    private static Unsafe initUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException se) {
            try {
                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                return (Unsafe) theUnsafe.get(Unsafe.class);
            } catch (Exception e) {
                throw new RuntimeException("Exception while trying to get Unsafe", e);
            }
        }
    }

    public static WordBase getFieldWord(Object obj, InterpreterResolvedJavaField wordField) throws SemanticJavaException {
        assert obj != null;
        assert wordField.isWordStorage();
        return switch (wordJavaKind()) {
            case Long -> Word.signed(getFieldLong(obj, wordField));
            case Int -> Word.signed(getFieldInt(obj, wordField));
            default -> throw VMError.shouldNotReachHere("Unexpected word kind " + wordJavaKind());
        };
    }

    public static boolean getFieldBoolean(Object obj, InterpreterResolvedJavaField field) {
        assert obj != null;
        if (field.isUndefined()) {
            throw VMError.shouldNotReachHere("Cannot load undefined field: " + field);
        }
        if (field.isUnmaterializedConstant()) {
            return field.getUnmaterializedConstant().asBoolean();
        }
        if (field.isVolatile()) {
            return U.getBooleanVolatile(obj, field.getOffset());
        } else {
            return U.getBoolean(obj, field.getOffset());
        }
    }

    public static int getFieldInt(Object obj, InterpreterResolvedJavaField field) {
        assert obj != null;
        if (field.isUndefined()) {
            throw VMError.shouldNotReachHere("Cannot load undefined field: " + field);
        }
        if (field.isUnmaterializedConstant()) {
            return field.getUnmaterializedConstant().asInt();
        }
        if (field.isVolatile()) {
            return U.getIntVolatile(obj, field.getOffset());
        } else {
            return U.getInt(obj, field.getOffset());
        }
    }

    public static long getFieldLong(Object obj, InterpreterResolvedJavaField field) {
        assert obj != null;
        if (field.isUndefined()) {
            throw VMError.shouldNotReachHere("Cannot load undefined field: " + field);
        }
        if (field.isUnmaterializedConstant()) {
            return field.getUnmaterializedConstant().asLong();
        }
        if (field.isVolatile()) {
            return U.getLongVolatile(obj, field.getOffset());
        } else {
            return U.getLong(obj, field.getOffset());
        }
    }

    public static byte getFieldByte(Object obj, InterpreterResolvedJavaField field) {
        assert obj != null;
        if (field.isUndefined()) {
            throw VMError.shouldNotReachHere("Cannot load undefined field: " + field);
        }
        if (field.isUnmaterializedConstant()) {
            return (byte) field.getUnmaterializedConstant().asInt();
        }
        if (field.isVolatile()) {
            return U.getByteVolatile(obj, field.getOffset());
        } else {
            return U.getByte(obj, field.getOffset());
        }
    }

    public static short getFieldShort(Object obj, InterpreterResolvedJavaField field) {
        assert obj != null;
        if (field.isUndefined()) {
            throw VMError.shouldNotReachHere("Cannot load undefined field: " + field);
        }
        if (field.isUnmaterializedConstant()) {
            return (short) field.getUnmaterializedConstant().asInt();
        }
        if (field.isVolatile()) {
            return U.getShortVolatile(obj, field.getOffset());
        } else {
            return U.getShort(obj, field.getOffset());
        }
    }

    public static float getFieldFloat(Object obj, InterpreterResolvedJavaField field) {
        assert obj != null;
        if (field.isUndefined()) {
            throw VMError.shouldNotReachHere("Cannot load undefined field: " + field);
        }
        if (field.isUnmaterializedConstant()) {
            return field.getUnmaterializedConstant().asFloat();
        }
        if (field.isVolatile()) {
            return U.getFloatVolatile(obj, field.getOffset());
        } else {
            return U.getFloat(obj, field.getOffset());
        }
    }

    public static double getFieldDouble(Object obj, InterpreterResolvedJavaField field) {
        assert obj != null;
        if (field.isUndefined()) {
            throw VMError.shouldNotReachHere("Cannot load undefined field: " + field);
        }
        if (field.isUnmaterializedConstant()) {
            return field.getUnmaterializedConstant().asDouble();
        }
        if (field.isVolatile()) {
            return U.getDoubleVolatile(obj, field.getOffset());
        } else {
            return U.getDouble(obj, field.getOffset());
        }
    }

    public static Object getFieldObject(Object obj, InterpreterResolvedJavaField field) {
        assert obj != null;
        if (field.isUndefined()) {
            throw VMError.shouldNotReachHere("Cannot load undefined field: " + field);
        }
        if (field.isUnmaterializedConstant()) {
            JavaConstant constant = field.getUnmaterializedConstant();
            if (JavaConstant.NULL_POINTER.equals(constant)) {
                return null;
            }
            VMError.guarantee(!constant.equals(PrimitiveConstant.ILLEGAL), Interpreter.FAILURE_CONSTANT_NOT_PART_OF_IMAGE_HEAP);
            VMError.guarantee(constant.isNonNull(), Interpreter.FAILURE_CONSTANT_NOT_PART_OF_IMAGE_HEAP);
            return ((ReferenceConstant<?>) constant).getReferent();
        }
        if (field.isVolatile()) {
            return U.getReferenceVolatile(obj, field.getOffset());
        } else {
            return U.getReference(obj, field.getOffset());
        }
    }

    public static char getFieldChar(Object obj, InterpreterResolvedJavaField field) {
        assert obj != null;
        if (field.isUndefined()) {
            throw VMError.shouldNotReachHere("Cannot load undefined field: " + field);
        }
        if (field.isUnmaterializedConstant()) {
            return (char) field.getUnmaterializedConstant().asInt();
        }
        if (field.isVolatile()) {
            return U.getCharVolatile(obj, field.getOffset());
        } else {
            return U.getChar(obj, field.getOffset());
        }
    }

    public static void setFieldBoolean(boolean value, Object obj, InterpreterResolvedJavaField field) {
        assert obj != null;
        ensureMaterialized(field);
        if (field.isVolatile()) {
            U.putBooleanVolatile(obj, field.getOffset(), value);
        } else {
            U.putBoolean(obj, field.getOffset(), value);
        }
    }

    public static void setFieldByte(byte value, Object obj, InterpreterResolvedJavaField field) {
        assert obj != null;
        ensureMaterialized(field);
        if (field.isVolatile()) {
            U.putByteVolatile(obj, field.getOffset(), value);
        } else {
            U.putByte(obj, field.getOffset(), value);
        }
    }

    public static void setFieldChar(char value, Object obj, InterpreterResolvedJavaField field) {
        assert obj != null;
        ensureMaterialized(field);
        if (field.isVolatile()) {
            U.putCharVolatile(obj, field.getOffset(), value);
        } else {
            U.putChar(obj, field.getOffset(), value);
        }
    }

    public static void setFieldShort(short value, Object obj, InterpreterResolvedJavaField field) {
        assert obj != null;
        ensureMaterialized(field);
        if (field.isVolatile()) {
            U.putShortVolatile(obj, field.getOffset(), value);
        } else {
            U.putShort(obj, field.getOffset(), value);
        }
    }

    public static void setFieldInt(int value, Object obj, InterpreterResolvedJavaField field) {
        assert obj != null;
        ensureMaterialized(field);
        assert field.getJavaKind() == JavaKind.Int || field.isWordStorage();
        if (field.isVolatile()) {
            U.putIntVolatile(obj, field.getOffset(), value);
        } else {
            U.putInt(obj, field.getOffset(), value);
        }
    }

    public static void setFieldLong(long value, Object obj, InterpreterResolvedJavaField field) {
        assert obj != null;
        ensureMaterialized(field);
        assert field.getJavaKind() == JavaKind.Long || field.isWordStorage();
        if (field.isVolatile()) {
            U.putLongVolatile(obj, field.getOffset(), value);
        } else {
            U.putLong(obj, field.getOffset(), value);
        }
    }

    public static void setFieldWord(WordBase value, Object obj, InterpreterResolvedJavaField field) {
        assert obj != null;
        ensureMaterialized(field);
        switch (wordJavaKind()) {
            case Int -> setFieldInt((int) value.rawValue(), obj, field);
            case Long -> setFieldLong(value.rawValue(), obj, field);
            default -> throw VMError.shouldNotReachHere("Unexpected word kind " + wordJavaKind());
        }
    }

    public static void setFieldFloat(float value, Object obj, InterpreterResolvedJavaField field) {
        assert obj != null;
        ensureMaterialized(field);
        if (field.isVolatile()) {
            U.putFloatVolatile(obj, field.getOffset(), value);
        } else {
            U.putFloat(obj, field.getOffset(), value);
        }
    }

    public static void setFieldDouble(double value, Object obj, InterpreterResolvedJavaField field) {
        assert obj != null;
        ensureMaterialized(field);
        if (field.isVolatile()) {
            U.putDoubleVolatile(obj, field.getOffset(), value);
        } else {
            U.putDouble(obj, field.getOffset(), value);
        }
    }

    public static void setFieldObject(Object value, Object obj, InterpreterResolvedJavaField field) {
        assert obj != null;
        ensureMaterialized(field);
        if (field.isVolatile()) {
            U.putReferenceVolatile(obj, field.getOffset(), value);
        } else {
            U.putReference(obj, field.getOffset(), value);
        }
    }

    private static void ensureMaterialized(InterpreterResolvedJavaField field) {
        if (RuntimeClassLoading.isSupported() && field.isUndefined()) {
            throw VMError.shouldNotReachHere("Cannot set undefined field " + field);
        } else {
            InterpreterUtil.assertion(field.getOffset() >= 0, "Bad field offset");
        }
    }

    /**
     * Subtyping among Array Types The following rules define the direct supertype relation among
     * array types:
     *
     * <ul>
     * <li>If S and T are both reference types, then S[] >1 T[] iff S >1 T.
     * <li>Object >1 Object[]
     * <li>Cloneable >1 Object[]
     * <li>java.io.Serializable >1 Object[]
     * <li>If P is a primitive type, then: Object >1 P[] Cloneable >1 P[] java.io.Serializable >1
     * P[]
     * </ul>
     */
    public static boolean instanceOf(Object instance, InterpreterResolvedJavaType typeToCheck) {
        return instanceOf(instance, typeToCheck.getJavaClass());
    }

    public static boolean instanceOf(Object instance, Class<?> classToCheck) {
        if (instance == null) {
            return false;
        }
        return classToCheck.isAssignableFrom(instance.getClass());
    }

    private static String cannotCastMsg(Object instance, Class<?> clazz) {
        return "Cannot cast " + instance.getClass().getName() + " to " + clazz.getName();
    }

    public static Object checkCast(Object instance, Class<?> classToCheck) throws SemanticJavaException {
        assert classToCheck != null;
        // Avoid Class#cast since it pollutes stack traces.
        if (GraalDirectives.injectBranchProbability(GraalDirectives.SLOWPATH_PROBABILITY, instance != null && !instanceOf(instance, classToCheck))) {
            throw SemanticJavaException.raise(new ClassCastException(cannotCastMsg(instance, classToCheck)));
        }
        return instance;
    }

    public static Object checkCast(Object instance, InterpreterResolvedJavaType typeToCheck) throws SemanticJavaException {
        return checkCast(instance, typeToCheck.getJavaClass());
    }

    public static int arrayLength(Object array) {
        assert array != null && array.getClass().isArray();
        return ArrayLengthNode.arrayLength(array);
    }

    public static Object createNewReference(InterpreterResolvedJavaType klass) throws SemanticJavaException {
        assert !klass.isPrimitive();
        Class<?> clazz = klass.getJavaClass();
        ensureClassInitialized(clazz);
        try {
            // GR-55050: Ensure that the type can be allocated on SVM.
            // At this point failing allocation should only imply OutOfMemoryError or
            // StackOverflowError which are handled specially by the interpreter.
            // GR-55050: Hide/remove the Unsafe#allocateInstance frame e.g. use a
            // DynamicNewInstanceNode intrinsic.
            return U.allocateInstance(clazz);
        } catch (InstantiationException | IllegalArgumentException | MissingReflectionRegistrationError e) {
            throw SemanticJavaException.raise(e);
        }
    }

    public static Object createNewPrimitiveArray(byte jvmPrimitiveType, int length) throws SemanticJavaException {
        try {
            return switch (jvmPrimitiveType) {
                case 4 -> new boolean[length];
                case 5 -> new char[length];
                case 6 -> new float[length];
                case 7 -> new double[length];
                case 8 -> new byte[length];
                case 9 -> new short[length];
                case 10 -> new int[length];
                case 11 -> new long[length];
                default -> throw VMError.shouldNotReachHereAtRuntime();
            };
        } catch (NegativeArraySizeException e) {
            throw SemanticJavaException.raise(e);
        }
    }

    public static Object createNewReferenceArray(InterpreterResolvedJavaType componentType, int length) throws SemanticJavaException {
        assert componentType.getJavaKind() != JavaKind.Void;
        assert !componentType.getJavaKind().isPrimitive();
        assert getDimensions(componentType) + 1 <= 255;
        if (length < 0) {
            throw SemanticJavaException.raise(new NegativeArraySizeException(String.valueOf(length)));
        }
        // GR-55050: Ensure that the array type can be allocated on SVM.
        // At this point failing allocation should only imply OutOfMemoryError or
        // StackOverflowError which are handled specially by the interpreter.
        // GR-55050: Hide/remove the Array.newInstance (and other intermediate) frames
        // e.g. use a DynamicNewArrayInstanceNode intrinsic.
        try (var _ = ClassLoading.allowArbitraryClassLoading(RuntimeClassLoading.isSupported())) {
            return Array.newInstance(componentType.getJavaClass(), length);
        }
    }

    private static int getDimensions(ResolvedJavaType object) {
        int dimensions = 0;
        for (ResolvedJavaType elem = object; elem.isArray(); elem = elem.getComponentType()) {
            dimensions++;
        }
        return dimensions;
    }

    public static Object createMultiArray(InterpreterResolvedJavaType multiArrayType, int[] dimensions) throws SemanticJavaException {
        assert dimensions.length > 0;
        assert getDimensions(multiArrayType) >= dimensions.length;
        assert getDimensions(multiArrayType) <= 255;
        // GR-55050: Ensure that the array type can be allocated on SVM.
        InterpreterResolvedJavaType component = multiArrayType;
        for (int d : dimensions) {
            if (d < 0) {
                throw SemanticJavaException.raise(new NegativeArraySizeException(String.valueOf(d)));
            }
            component = (InterpreterResolvedJavaType) component.getComponentType();
        }
        // At this point failing allocation should only imply OutOfMemoryError or
        // StackOverflowError which are handled specially by the interpreter.
        // GR-55050: Hide/remove the Array.newInstance (and other intermediate) frames
        // e.g. use a DynamicNewArrayInstanceNode intrinsic.
        return Array.newInstance(component.getJavaClass(), dimensions);
    }

    public static void ensureClassInitialized(InterpreterResolvedObjectType type) {
        ensureClassInitialized(type.getJavaClass());
    }

    /**
     * Ensures that the given class is initialized, which may execute the static initializer of the
     * given class it's superclasses/superinterfaces. Exceptions thrown by class initialization are
     * propagated as a semantic/valid Java exception to the interpreter.
     */
    public static void ensureClassInitialized(Class<?> clazz) throws SemanticJavaException {
        assert clazz != null;
        try {
            EnsureClassInitializedNode.ensureClassInitialized(clazz);
        } catch (Error e) {
            // Only Error is expected here.
            throw SemanticJavaException.raise(e);
        }
    }

    static CFunctionPointer peekAtSVMVTable(Class<?> callTargetClass, Class<?> thisClass, int vTableIndex) {
        DynamicHub callTargetHub = DynamicHub.fromClass(callTargetClass);
        DynamicHub thisHub = DynamicHub.fromClass(thisClass);

        int vtableOffset = DynamicHubUtils.determineDispatchTableOffset(thisHub, callTargetHub, vTableIndex);
        MethodRef vtableEntry = Word.objectToTrackedPointer(thisHub).readWord(vtableOffset);
        return getSVMVTableCodePointer(vtableEntry);
    }

    private static CFunctionPointer getSVMVTableCodePointer(MethodRef vtableEntry) {
        Pointer codePointer = (Pointer) vtableEntry;
        if (SubstrateOptions.useRelativeCodePointers()) {
            codePointer = codePointer.add(KnownIntrinsics.codeBase());
        }
        return (CFunctionPointer) codePointer;
    }

    private static InterpreterResolvedJavaMethod peekAtInterpreterVTable(Class<?> seedClass, Class<?> thisClass, int vTableIndex) {
        ResolvedJavaType thisType;
        if (RuntimeClassLoading.isSupported()) {
            thisType = DynamicHub.fromClass(thisClass).getInterpreterType();
        } else {
            assert DebuggerWithInterpreter.getValue();
            DebuggerSupport interpreterSupport = ImageSingletons.lookup(DebuggerSupport.class);
            thisType = interpreterSupport.getUniverse().lookupType(thisClass);
        }
        VMError.guarantee(thisType != null);
        VMError.guarantee(thisType instanceof InterpreterResolvedObjectType);

        InterpreterResolvedJavaMethod[] vTable = ((InterpreterResolvedObjectType) thisType).getVtable();
        VMError.guarantee(vTable != null);

        DynamicHub seedHub = DynamicHub.fromClass(seedClass);

        int idx;
        if (SubstrateOptions.useClosedTypeWorldHubLayout() || !seedHub.isInterface()) {
            idx = vTableIndex;
        } else {
            idx = vTableIndex + determineITableStartingIndex(DynamicHub.fromClass(thisClass), seedHub.getInterfaceID());
        }
        VMError.guarantee(idx >= 0 && idx < vTable.length);
        return vTable[idx];
    }

    private static int determineITableStartingIndex(DynamicHub thisHub, int interfaceID) {
        /*
         * iTableStartingOffset includes the initial offset to the vtable array and describes an
         * offset (not index)
         */
        long iTableStartingOffset = OpenTypeWorldDispatchTableSnippets.determineITableStartingOffset(thisHub, interfaceID);

        int vtableBaseOffset = KnownOffsets.singleton().getVTableBaseOffset();
        int vtableEntrySize = KnownOffsets.singleton().getVTableEntrySize();

        return (int) (iTableStartingOffset - vtableBaseOffset) / vtableEntrySize;
    }

    public static Object dispatchInvocation(InterpreterResolvedJavaMethod seedMethod, Object[] calleeArgs, CallKind callKind,
                    boolean forceStayInInterpreter, boolean preferStayInInterpreter, boolean quiet)
                    throws SemanticJavaException {

        if (InterpreterTraceSupport.getValue() && !quiet) {
            traceInterpreter().string("Dispatching ").string(callKind.toString()).string(" invocation for seed: ").string(seedMethod.toString()).newline();
        }

        // First, find the target method.
        InterpreterResolvedJavaMethod target = resolveCallSiteTarget(seedMethod, calleeArgs, callKind, quiet);

        // Next, determine whether the call should stay in interpreter or call the compiled target.
        boolean callAOTEntryPoint = shouldCallAOTEntryPoint(forceStayInInterpreter, preferStayInInterpreter, target, quiet);

        InterpreterUtil.guarantee(target.getSymbolicName() == seedMethod.getSymbolicName() && target.getSymbolicSignature() == seedMethod.getSymbolicSignature(),
                        "Erroneous dispatching for seed: %s%n  With dispatch index: %s%n  Resulted in : %s", seedMethod, seedMethod.getVTableIndex(), target);

        /* arguments to Log methods might have side-effects */
        if (InterpreterOptions.InterpreterTraceSupport.getValue() && !quiet) {
            traceInterpreter()
                            .string(" -> calling (")
                            .string(callAOTEntryPoint ? "compiled" : "interp").string(") ")
                            .string(target.hasNativeEntryPoint() ? "(compiled entry available) " : "");
            if (target.hasNativeEntryPoint()) {
                traceInterpreter("(addr: ").hex(target.getNativeEntryPoint()).string(" ) ");
            }
            traceInterpreter(target.getDeclaringClass().getName())
                            .string("::").string(target.getName())
                            .string(target.getSignature().toMethodDescriptor())
                            .newline();
        }

        // All done, we can do the call.
        try {
            if (callAOTEntryPoint) {
                return InterpreterStubSection.leaveInterpreter(target.getNativeEntryPoint(), target, calleeArgs);
            } else {
                // Note: this call may still end up in compiled code if JIT code is available.
                return InterpreterStubSection.call(target, calleeArgs);
            }
        } catch (Throwable t) {
            throw SemanticJavaException.raise(t);
        }
    }

    private static InterpreterResolvedJavaMethod resolveCallSiteTarget(InterpreterResolvedJavaMethod seedMethod, Object[] calleeArgs, CallKind callKind, boolean quiet) {
        boolean isVirtual = callKind.hasLookup();
        if (callKind.isStatic()) {
            InterpreterUtil.guarantee(seedMethod.isStatic(), "Statically calling a non-static method: %s", seedMethod);
            ensureClassInitialized(seedMethod.getDeclaringClass());
            return seedMethod;
        } else if (isVirtual && seedMethod.hasDispatchIndex()) {
            InterpreterUtil.guarantee(
                            // Ensure itable lookup happens only for interface method seeds.
                            seedMethod.getDeclaringClass().isInterface() == (callKind == CallKind.ITABLE_LOOKUP),
                            "Wrong call kind (%s) for the given method: %s", callKind.toString(), seedMethod);
            Class<?> receiverClass = calleeArgs[0].getClass();
            if (receiverClass.isArray()) {
                // Arrays do not have a vtable
                return seedMethod;
            } else {
                return peekAtInterpreterVTable(seedMethod.getDeclaringClass().getJavaClass(), receiverClass, seedMethod.getVTableIndex());
            }
        } else if (isVirtual && seedMethod.isDevirtualized()) {
            InterpreterResolvedJavaMethod target = seedMethod.devirtualizationTarget();
            if (InterpreterTraceSupport.getValue() && !quiet) {
                traceInterpreter().string("found devirtualized target: ").string(target.toString()).newline();
            }
            return target;
        } else {
            InterpreterUtil.guarantee(!isVirtual, "Unable to perform a virtual dispatch for method: %s", seedMethod);
            return seedMethod;
        }
    }

    private static boolean shouldCallAOTEntryPoint(boolean forceStayInInterpreter, boolean preferStayInInterpreter, InterpreterResolvedJavaMethod target, boolean quiet) {
        boolean canBeInterpreterInvoked = target.hasBytecodes() || (RuntimeClassLoading.isSupported() && target.isSignaturePolymorphicIntrinsic());
        boolean canBeAOTCalled = target.hasNativeEntryPoint() && target.getNativeEntryPoint().isNonNull();

        if (!canBeInterpreterInvoked && !canBeAOTCalled) {
            String source;
            String reason = "Unable to call method because it can neither be interpreted, nor does it have a valid native entry point.";

            if (target instanceof CremaResolvedJavaMethodImpl) {
                source = "runtime-loaded";
                if (target.isNative()) {
                    reason = "Linking native methods not yet supported.";
                }
            } else {
                source = "AOT";
                if (!ClassForNameSupport.isPreserved(target.getDeclaringClass().getJavaClass())) {
                    String dotPkg = target.getDeclaringClass().getSymbolicRuntimePackage().toString().replace('/', '.');
                    reason = MetadataUtil.fmt("Class was not preserved during image build. Consider using '-H:Preserve=package=%s'.", dotPkg);
                }
            }
            InterpreterUtil.guarantee(false, "Unable to call %s method: %s%n%s", source, target, reason);
        }

        if (!canBeInterpreterInvoked) {
            // No valid interpretation target: Unconditional call to compiled target.
            if (InterpreterTraceSupport.getValue() && !quiet) {
                traceInterpreter().string("No valid interpretation target, calling compiled target");
                if (forceStayInInterpreter || preferStayInInterpreter) {
                    traceInterpreter(" (Unable to enforce stay in interpreter)");
                }
                traceInterpreter(".").newline();
            }
            return true;
        } else if (!canBeAOTCalled) {
            if (InterpreterTraceSupport.getValue() && !quiet) {
                traceInterpreter().string("Invalid native entry point: invoking in interpreter.").newline();
            }
            return false;
        } else if (forceStayInInterpreter) {
            // Unconditionally stay in interpreter.
            if (InterpreterTraceSupport.getValue() && !quiet) {
                traceInterpreter().string("'forceStayInInterpreter' set to true: invoking in interpreter.").newline();
            }
            return false;
        } else {
            // No more unconditional requirements. Use heuristics.
            if (preferStayInInterpreter) {
                // Follow the given hint
                if (InterpreterTraceSupport.getValue() && !quiet) {
                    traceInterpreter().string("'preferStayInInterpreter' set to true: invoking in interpreter.").newline();
                }
                return false;
            } else if (target instanceof CremaResolvedJavaMethodImpl) {
                if (InterpreterTraceSupport.getValue() && !quiet) {
                    traceInterpreter().string("Runtime-loaded method detected: invoking in interpreter.").newline();
                }
                /*
                 * This is a runtime-loaded class. While there exists a valid AOT entry point (the
                 * function pointer we insert in the SVM vtable), it is merely a stub calling back
                 * into the interpreter.
                 */
                return false;
            } else {
                if (InterpreterTraceSupport.getValue() && !quiet) {
                    traceInterpreter().string("Defaulting to calling compiled target.").newline();
                }
                // Otherwise prefer calling optimized code.
                return true;
            }
        }
    }

    public static Object nullCheck(Object value) throws SemanticJavaException {
        if (GraalDirectives.injectBranchProbability(GraalDirectives.FASTPATH_PROBABILITY, value != null)) {
            return value;
        }
        throw SemanticJavaException.raise(new NullPointerException());
    }
}
