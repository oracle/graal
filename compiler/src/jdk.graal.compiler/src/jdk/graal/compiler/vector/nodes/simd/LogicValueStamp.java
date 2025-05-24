/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.nodes.simd;

import java.nio.ByteBuffer;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.spi.LIRKindTool;
import jdk.graal.compiler.core.common.type.ArithmeticStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SerializableConstant;

/**
 * Target-specific stamp that represents logic values in a form suitable for use in SIMD vectors on
 * architectures that use opmasks. These are architectures like AMD64 AVX-512 and AArch64 SVE, which
 * represent a vector of logic values as a vector of bits (one bit per input vector element). On
 * such architectures the width of the vector is independent of the input stamp.
 * <p/>
 *
 * Other architectures like AMD64 AVX2 and AArch64 Neon represent a vector of logic values as a
 * vector of bitmasks. Each bitmask has a value of 0 or -1 and the same width as some data type that
 * was used to produce the logic value. We do <em>not</em> use this stamp on such architectures;
 * instead we use plain integer stamps.
 */
public final class LogicValueStamp extends ArithmeticStamp {
    // Simple bitmask space, the 0-th bit denotes whether this stamp can take the value of false,
    // the 1-st bit denotes whether this stamp can take the value of true.
    private static final int MASK_EMPTY = 0b00; // Cannot be either false nor true
    private static final int MASK_FALSE = 0b01; // Can be false, cannot be true
    private static final int MASK_TRUE = 0b10; // Can be true, cannot be false
    private static final int MASK_UNRESTRICTED = 0b11; // Can be either true or false

    public static final LogicValueStamp EMPTY = new LogicValueStamp(MASK_EMPTY);
    public static final LogicValueStamp FALSE = new LogicValueStamp(MASK_FALSE);
    public static final LogicValueStamp TRUE = new LogicValueStamp(MASK_TRUE);
    public static final LogicValueStamp UNRESTRICTED = new LogicValueStamp(MASK_UNRESTRICTED);

    private static final LogicValueStamp[] INSTANCES = {EMPTY, FALSE, TRUE, UNRESTRICTED};

    private final byte mask;

    private LogicValueStamp(int mask) {
        super(SimdStamp.OPMASK_OPS);
        this.mask = (byte) mask;
    }

    private static LogicValueStamp get(int mask) {
        return INSTANCES[mask];
    }

    @Override
    public ResolvedJavaType javaType(MetaAccessProvider metaAccess) {
        throw GraalError.shouldNotReachHere("logic value stamp has no Java type"); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public JavaKind getStackKind() {
        return JavaKind.Illegal;
    }

    @Override
    public LIRKind getLIRKind(LIRKindTool tool) {
        throw GraalError.shouldNotReachHere("logic value stamp would need target-specific information to determine the LIR kind"); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public LogicValueStamp meet(Stamp other) {
        GraalError.guarantee(this.isCompatible(other), "incompatible stamps: %s / %s", this, other);
        return get(mask | ((LogicValueStamp) other).mask);
    }

    @Override
    public Stamp join(Stamp other) {
        GraalError.guarantee(this.isCompatible(other), "incompatible stamps: %s / %s", this, other);
        return get(mask & ((LogicValueStamp) other).mask);
    }

    @Override
    public Stamp unrestricted() {
        return UNRESTRICTED;
    }

    @Override
    public Stamp empty() {
        return EMPTY;
    }

    @Override
    public Stamp constant(Constant c, MetaAccessProvider meta) {
        LogicValueConstant lc = (LogicValueConstant) c;
        return lc.value() ? TRUE : FALSE;
    }

    @Override
    public boolean isCompatible(Stamp other) {
        return other instanceof LogicValueStamp;
    }

    @Override
    public boolean isCompatible(Constant constant) {
        return constant instanceof LogicValueConstant;
    }

    @Override
    public boolean hasValues() {
        return mask > 0;
    }

    @Override
    public Constant readConstant(MemoryAccessProvider provider, Constant base, long displacement) {
        throw GraalError.shouldNotReachHere("LogicValueConstant is not serializable");
    }

    @Override
    public SerializableConstant deserialize(ByteBuffer buffer) {
        throw GraalError.shouldNotReachHere("LogicValueConstant is not serializable");
    }

    @Override
    public Stamp improveWith(Stamp other) {
        return join(other);
    }

    @Override
    public String toString() {
        return switch (mask) {
            case MASK_EMPTY -> "logic(empty)";
            case MASK_FALSE -> "logic(0)";
            case MASK_TRUE -> "logic(1)";
            case MASK_UNRESTRICTED -> "logic(*)";
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(mask);
        };
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof LogicValueStamp l && mask == l.mask;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(mask);
    }

    @Override
    public void accept(Visitor v) {
        // Primitive stamp just visitInt the number of bits, so we do the same
        v.visitInt(1);
    }
}
