/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.code;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.util.Arrays;

import com.oracle.svm.shared.Uninterruptible;

import jdk.vm.ci.meta.JavaKind;

/**
 * Represents the calling-convention layout used by interpreter entry and leave stubs.
 * Each prepared argument type is encoded in a 32-bit integer with the following layout:
 *
 * <pre>
 * 32                               7            5            4                    0
 * +--------------------------------+------------+------------+--------------------+
 * |          value                 | adaptation | isRegister |     JavaKind       |
 * +--------------------------------+------------+------------+--------------------+
 * </pre>
 *
 * The {@code isRegister} bit indicates whether {@code value} is a register index or a byte offset into the
 * stack-argument area. The adaptation specifies how the Java value must be transformed
 * before it is stored at that location. A {@link JavaKind#Void} entry with value zero represents
 * an argument that is skipped. A nonzero value on a {@code Void} entry identifies a special stub
 * location instead of a physical ABI location.
 */
public final class PreparedSignature {

    public static final int STUB_LOCATION_TARGET_ADDRESS = 1;
    public static final int STUB_LOCATION_RETURN_BUFFER = 2;
    public static final int STUB_LOCATION_CAPTURED_STATE_BUFFER = 3;
    public static final int SKIPPED_ARGUMENT_TYPE = encodeArgumentType(JavaKind.Void, 0, false);

    private static final JavaKind[] JAVA_KINDS = JavaKind.values();
    private static final int KIND_BITS = 4;
    private static final int KIND_MASK = (1 << KIND_BITS) - 1;
    private static final int REGISTER_SHIFT = KIND_BITS;
    private static final int ADAPTATION_BITS = 2;
    private static final int ADAPTATION_SHIFT = REGISTER_SHIFT + 1;
    private static final int ADAPTATION_MASK = (1 << ADAPTATION_BITS) - 1;
    private static final int VALUE_SHIFT = ADAPTATION_SHIFT + ADAPTATION_BITS;
    private static final int VALUE_BITS = Integer.SIZE - VALUE_SHIFT;
    private static final int VALUE_MASK = (1 << VALUE_BITS) - 1;
    private static final ArgumentAdaptation[] ARGUMENT_ADAPTATIONS = ArgumentAdaptation.values();

    static {
        assert JAVA_KINDS.length <= (1 << KIND_BITS) : "Not enough bits reserved to encode every JavaKind value.";
        assert ARGUMENT_ADAPTATIONS.length <= (1 << ADAPTATION_BITS) : "Not enough bits reserved to encode every argument adaptation.";
    }

    public enum ArgumentAdaptation {
        NONE,
        FLOAT_TO_LONG,
        DOUBLE_TO_LONG,
        HEAP_ADDRESS
    }

    private final JavaKind returnKind;
    private final int[] preparedArgumentTypes;
    private final int stackSize;

    public PreparedSignature(JavaKind returnKind, int[] preparedArgumentTypes, int stackSize) {
        this.returnKind = returnKind;
        this.preparedArgumentTypes = preparedArgumentTypes;
        this.stackSize = stackSize;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PreparedSignature that)) {
            return false;
        }
        return returnKind == that.returnKind && stackSize == that.stackSize && Arrays.equals(preparedArgumentTypes, that.preparedArgumentTypes);
    }

    @Override
    public int hashCode() {
        int result = returnKind.hashCode();
        result = 31 * result + Arrays.hashCode(preparedArgumentTypes);
        result = 31 * result + stackSize;
        return result;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public int[] getArgumentTypes() {
        return preparedArgumentTypes;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static int encodeArgumentType(JavaKind kind, int value, boolean isRegister) {
        return encodeArgumentType(kind, value, isRegister, ArgumentAdaptation.NONE);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static int encodeArgumentType(JavaKind kind, int value, boolean isRegister, ArgumentAdaptation adaptation) {
        assert value >= 0 && value <= VALUE_MASK : "Prepared argument value does not fit in its encoding.";
        return kind.ordinal() | ((isRegister ? 1 : 0) << REGISTER_SHIFT) | (adaptation.ordinal() << ADAPTATION_SHIFT) | (value << VALUE_SHIFT);
    }

    public static int encodeStubLocation(int stubLocation, ArgumentAdaptation adaptation) {
        assert stubLocation > 0;
        return encodeArgumentType(JavaKind.Void, stubLocation, false, adaptation);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static JavaKind getKind(int argType) {
        return JAVA_KINDS[argType & KIND_MASK];
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean isRegister(int argType) {
        return ((argType >>> REGISTER_SHIFT) & 1) != 0;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean isStackSlot(int argType) {
        return !isRegister(argType);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static ArgumentAdaptation getArgumentAdaptation(int argType) {
        return ARGUMENT_ADAPTATIONS[(argType >>> ADAPTATION_SHIFT) & ADAPTATION_MASK];
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean isStubLocation(int argType) {
        return getKind(argType) == JavaKind.Void && getValue(argType) != 0;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static int getStubLocation(int argType) {
        assert isStubLocation(argType);
        return getValue(argType);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static int getRegister(int argType) {
        assert isRegister(argType);
        return getValue(argType);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static int getStackOffset(int argType) {
        assert isStackSlot(argType);
        return getValue(argType);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static int getValue(int argType) {
        return (argType >>> VALUE_SHIFT) & VALUE_MASK;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static int getDefaultArgumentType() {
        return 0;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public JavaKind getReturnKind() {
        return returnKind;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public int getStackSize() {
        return stackSize;
    }
}
