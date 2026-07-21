/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.code;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.lang.module.ModuleDescriptor;
import java.util.Optional;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.espresso.classfile.Constants;
import com.oracle.svm.shared.Uninterruptible;

import jdk.graal.compiler.nodes.FrameState;
import jdk.internal.loader.BuiltinClassLoader;

public abstract class FrameSourceInfo {
    public static final int LINENUMBER_UNKNOWN = -1;
    public static final int LINENUMBER_NATIVE = -2;

    protected Class<?> sourceClass;
    protected String sourceMethodName;
    protected int sourceLineNumber;
    protected long encodedBci;
    protected int sourceMethodFlags;

    protected FrameSourceInfo(Class<?> sourceClass, String sourceMethodName, int sourceLineNumber, int bci, int sourceMethodFlags) {
        this.sourceClass = sourceClass;
        this.sourceMethodName = sourceMethodName;
        this.sourceLineNumber = sourceLineNumber;
        this.encodedBci = FrameInfoEncoder.encodeBci(bci, FrameState.StackState.BeforePop);
        this.sourceMethodFlags = sourceMethodFlags;
    }

    @SuppressWarnings("this-escape")
    protected FrameSourceInfo() {
        init();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected void init() {
        sourceClass = CodeInfoEncoder.Encoders.INVALID_CLASS;
        sourceMethodName = CodeInfoEncoder.Encoders.INVALID_METHOD_NAME;
        sourceLineNumber = LINENUMBER_UNKNOWN;
        encodedBci = -1;
        sourceMethodFlags = CodeInfoEncoder.Encoders.INVALID_METHOD_MODIFIERS;
    }

    public abstract FrameSourceInfo getCaller();

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public Class<?> getSourceClass() {
        fillSourceFieldsIfMissing();
        return sourceClass;
    }

    public String getSourceClassName() {
        Class<?> clazz = getSourceClass();
        return (clazz != null) ? clazz.getName() : "";
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public String getSourceMethodName() {
        fillSourceFieldsIfMissing();
        return sourceMethodName;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getSourceLineNumber() {
        return sourceLineNumber;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public String getSourceFileName() {
        Class<?> clazz = getSourceClass();
        return (clazz != null) ? DynamicHub.fromClass(clazz).getSourceFileName() : null;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected abstract void fillSourceFieldsIfMissing();

    /**
     * Returns the bytecode index.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getBci() {
        return FrameInfoDecoder.decodeBci(encodedBci);
    }

    public boolean isNativeMethod() {
        return sourceLineNumber == LINENUMBER_NATIVE;
    }

    /**
     * Returns flags that can be decoded with the methods of {@link MethodFlags}.
     * <p>
     * Those include internal properties of methods and method modifiers depending on configuration.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public int getSourceMethodFlags() {
        fillSourceFieldsIfMissing();
        return sourceMethodFlags;
    }

    /**
     * Returns the name and source code location of the method.
     */
    public StackTraceElement getSourceReference() {
        fillSourceFieldsIfMissing();

        if (sourceClass == null) {
            return new StackTraceElement("", getSourceMethodName(), null, getSourceLineNumber());
        }

        return getSourceReference(getSourceClass(), getSourceMethodName(), getSourceLineNumber());
    }

    public static StackTraceElement getSourceReference(Class<?> sourceClass, String sourceMethodName, int sourceLineNumber) {
        ClassLoader classLoader = sourceClass.getClassLoader();
        String classLoaderName = null;
        if (classLoader != null && !(classLoader instanceof BuiltinClassLoader)) {
            classLoaderName = classLoader.getName();
        }

        Module module = sourceClass.getModule();
        String moduleName = module.getName();
        String moduleVersion = Optional.ofNullable(module.getDescriptor())
                        .flatMap(ModuleDescriptor::version)
                        .map(ModuleDescriptor.Version::toString)
                        .orElse(null);
        String className = sourceClass.getName();
        String sourceFileName = DynamicHub.fromClass(sourceClass).getSourceFileName();

        return new StackTraceElement(classLoaderName, moduleName, moduleVersion, className, sourceMethodName, sourceFileName, sourceLineNumber);
    }

    /**
     * Constants used to encode internal method flags. Those are packed into unused bits of the 16
     * bits of method modifiers.
     * <p>
     * Note that the flags are currently kept grouped in the most significant bits to simplify the
     * mode in which those extra internal flags are stored as bitmaps (see {@code encodeMethodFlags}
     * in {@link CodeInfoEncoder.Encoders}).
     * <p>
     * As of JDK 25 the {@linkplain Constants#JVM_RECOGNIZED_METHOD_MODIFIERS used bits for method
     * modifiers} are 0x1dff.
     *
     * @see FrameSourceInfo#getSourceMethodFlags()
     */
    public static final class MethodFlags {
        private static final int HIDDEN_METHOD_FLAG = 0x8000;
        private static final int LAMBDA_FORM_COMPILED_METHOD_FLAG = 0x4000;
        private static final int BYTECODE_HANDLER_STUB_METHOD_FLAG = 0x2000;

        static final int EXTRA_FLAGS_MASK = HIDDEN_METHOD_FLAG | LAMBDA_FORM_COMPILED_METHOD_FLAG | BYTECODE_HANDLER_STUB_METHOD_FLAG;
        /*
         * Extend the occupied flag span to the next power of two so that slots do not cross byte
         * boundaries. Align the flags with the most significant end of each slot, as they are in
         * the full 16-bit value. For example, the current mask 0xE000 occupies three bits and is
         * stored in a four-bit compact slot: 0xE000 >> 12 = 0xE. The least significant slot bit is
         * padding. This does not make that bit available for another internal flag: when full method
         * modifiers are encoded, 0x1000 is the Java synthetic modifier and only the three bits in
         * EXTRA_FLAGS_MASK are available.
         */
        static final int EXTRA_FLAGS_BITS = computeExtraFlagsBits(EXTRA_FLAGS_MASK);
        static final int EXTRA_FLAGS_POS = Short.SIZE - EXTRA_FLAGS_BITS;
        static {
            /*
             * check constants in a method to be able to supress warnings about "always true"
             * expressions.
             */
            checkConstants();
        }

        private MethodFlags() {
        }

        /**
         * Returns the smallest power-of-two slot that contains the complete span of {@code mask}.
         * Compact method flags use such slots so that no entry crosses a byte boundary. The span,
         * rather than the number of set bits, accounts for method-modifier bits between internal
         * flags that cannot be used by this encoding.
         */
        private static int computeExtraFlagsBits(int mask) {
            int occupiedBits = Integer.SIZE - Integer.numberOfLeadingZeros(mask) - Integer.numberOfTrailingZeros(mask);
            return Integer.highestOneBit(2 * occupiedBits - 1);
        }

        @SuppressWarnings("all")
        private static void checkConstants() {
            assert EXTRA_FLAGS_MASK != 0 : "At least one extra method flag is required";
            assert (HIDDEN_METHOD_FLAG & Constants.JVM_RECOGNIZED_METHOD_MODIFIERS) == 0 : "HIDDEN_METHOD_FLAG collides with specified method modifier";
            assert (LAMBDA_FORM_COMPILED_METHOD_FLAG & Constants.JVM_RECOGNIZED_METHOD_MODIFIERS) == 0 : "LAMBDA_FORM_COMPILED_METHOD_FLAG collides with specified method modifier";
            assert (BYTECODE_HANDLER_STUB_METHOD_FLAG & Constants.JVM_RECOGNIZED_METHOD_MODIFIERS) == 0 : "BYTECODE_HANDLER_STUB_METHOD_FLAG collides with specified method modifier";
            assert (CodeInfoEncoder.Encoders.INVALID_METHOD_MODIFIERS &
                            Constants.JVM_RECOGNIZED_METHOD_MODIFIERS) == CodeInfoEncoder.Encoders.INVALID_METHOD_MODIFIERS : "INVALID_METHOD_MODIFIERS should only used specified method modifiers to avoid collitions with internal method flags";
            assert (EXTRA_FLAGS_MASK & HIDDEN_METHOD_FLAG) == HIDDEN_METHOD_FLAG : "HIDDEN_METHOD_FLAG is not covered by EXTRA_FLAGS_MASK";
            assert (EXTRA_FLAGS_MASK & LAMBDA_FORM_COMPILED_METHOD_FLAG) == LAMBDA_FORM_COMPILED_METHOD_FLAG : "LAMBDA_FORM_COMPILED_METHOD_FLAG is not covered by EXTRA_FLAGS_MASK";
            assert (EXTRA_FLAGS_MASK & BYTECODE_HANDLER_STUB_METHOD_FLAG) == BYTECODE_HANDLER_STUB_METHOD_FLAG : "BYTECODE_HANDLER_STUB_METHOD_FLAG is not covered by EXTRA_FLAGS_MASK";
            /*
             * These properties are used by extra flag encoding/decoding when full modifiers are
             * not serialized. See `CodeInfoDecoder.getMethodFlags` and
             * `CodeInfoEncoder.Encoders.encodeMethodTable`.
             */
            assert EXTRA_FLAGS_BITS <= Byte.SIZE : "Extra method flags do not fit into one byte";
            assert Byte.SIZE % EXTRA_FLAGS_BITS == 0 : "The extra flag slot size must divide a byte";
            assert (EXTRA_FLAGS_MASK >>> EXTRA_FLAGS_POS) < (1 << EXTRA_FLAGS_BITS) : "Extra method flags do not fit into their compact slot";
        }

        public static int computeSourceMethodFlags(int modifiers, boolean isHidden, boolean isLambdaFormCompiled) {
            return computeSourceMethodFlags(modifiers, isHidden, isLambdaFormCompiled, false);
        }

        public static int computeSourceMethodFlags(int modifiers, boolean isHidden, boolean isLambdaFormCompiled, boolean isBytecodeHandlerStub) {
            int flags = modifiers;
            if (isHidden) {
                flags |= HIDDEN_METHOD_FLAG;
            }
            if (isLambdaFormCompiled) {
                flags |= LAMBDA_FORM_COMPILED_METHOD_FLAG;
            }
            if (isBytecodeHandlerStub) {
                flags |= BYTECODE_HANDLER_STUB_METHOD_FLAG;
            }
            return flags;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public static boolean isHidden(int flags) {
            return (flags & HIDDEN_METHOD_FLAG) != 0;
        }

        public static boolean isLambdaFormCompiled(int flags) {
            return (flags & LAMBDA_FORM_COMPILED_METHOD_FLAG) != 0;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public static boolean isBytecodeHandlerStub(int flags) {
            return (flags & BYTECODE_HANDLER_STUB_METHOD_FLAG) != 0;
        }

        /**
         * Returns the JVM method modifiers. Those are only valid if
         * {@link #hasValidMethodModifiers} returns true.
         */
        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public static int getMethodModifiers(int flags) {
            return flags & Constants.JVM_RECOGNIZED_METHOD_MODIFIERS;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public static boolean hasValidMethodModifiers(int flags) {
            return getMethodModifiers(flags) != CodeInfoEncoder.Encoders.INVALID_METHOD_MODIFIERS;
        }
    }
}
