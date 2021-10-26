/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.common.type;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.serviceprovider.BufferUtil;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;

/**
 * Type describing primitive values.
 */
public abstract class PrimitiveStamp extends ArithmeticStamp {

    private final int bits;

    protected PrimitiveStamp(int bits, ArithmeticOpTable ops) {
        super(ops);
        this.bits = bits;
    }

    @Override
    public void accept(Visitor v) {
        v.visitInt(bits);
    }

    /**
     * The width in bits of the value described by this stamp.
     */
    public final int getBits() {
        return bits;
    }

    public static int getBits(Stamp stamp) {
        if (stamp instanceof PrimitiveStamp) {
            return ((PrimitiveStamp) stamp).getBits();
        } else {
            return 0;
        }
    }

    @Override
    public Constant readConstant(MemoryAccessProvider provider, Constant base, long displacement) {
        try {
            int numBits = getBits();
            GraalError.guarantee((numBits & 7) == 0, "numBits not a multiple of 8: %d", numBits);
            int numBytes = numBits / 8;
            boolean aligned = displacement % numBytes == 0;
            if (aligned) {
                return provider.readPrimitiveConstant(getStackKind(), base, displacement, numBits);
            } else {
                return readConstantUnaligned(provider, base, displacement, numBytes, getStackKind());
            }
        } catch (IllegalArgumentException e) {
            /*
             * It's possible that the base and displacement aren't valid together so simply return
             * null.
             */
            return null;
        }
    }

    /**
     * As a result of JDK-8275874, {@link MemoryAccessProvider#readPrimitiveConstant} cannot be used
     * for unaligned reads. Instead, it has to be done by reading individual bytes and stitching
     * them together. The read is non-atomic which is fine for constant folding stable values (i.e.
     * compile-time constant fields/elements).
     */
    private static Constant readConstantUnaligned(MemoryAccessProvider provider, Constant base, long displacement, int numBytes, JavaKind stackKind) {
        ByteBuffer buf = ByteBuffer.allocate(numBytes);
        for (int i = 0; i < numBytes; i++) {
            JavaConstant c = provider.readPrimitiveConstant(JavaKind.Byte, base, displacement + i, 8);
            if (c == null) {
                // Some implementations of MemoryAccessProvider (e.g. HostedMemoryAccessProvider)
                // can return null from readPrimitiveConstant
                return null;
            }
            byte b = (byte) c.asInt();
            buf.put(b);
        }
        BufferUtil.asBaseBuffer(buf).rewind();
        buf.order(ByteOrder.nativeOrder());
        if (numBytes == 8) {
            return forPrimitive(stackKind, buf.getLong());
        }
        if (numBytes == 4) {
            return forPrimitive(stackKind, buf.getInt());
        }
        GraalError.guarantee(numBytes == 2, "unexpected numBytes: %d", numBytes);
        return forPrimitive(stackKind, buf.getShort());
    }

    /**
     * Work around for {@code JavaConstant.forPrimitive(JavaKind kind, long rawValue)} only be
     * available in more recent JVMCI versions.
     */
    private static PrimitiveConstant forPrimitive(JavaKind stackKind, long rawValue) {
        switch (stackKind) {
            case Int:
                return JavaConstant.forInt((int) rawValue);
            case Long:
                return JavaConstant.forLong(rawValue);
            case Float:
                return JavaConstant.forFloat(Float.intBitsToFloat((int) rawValue));
            case Double:
                return JavaConstant.forDouble(Double.longBitsToDouble(rawValue));
            default:
                throw new IllegalArgumentException("Unexpected stack kind: " + stackKind);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + bits;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PrimitiveStamp)) {
            return false;
        }
        PrimitiveStamp other = (PrimitiveStamp) obj;
        if (bits != other.bits) {
            return false;
        }
        return super.equals(obj);
    }
}
