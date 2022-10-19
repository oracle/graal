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
package com.oracle.svm.core.graal.nodes;

import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.type.AbstractObjectStamp;
import org.graalvm.compiler.core.common.type.CompressibleConstant;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.CompressionNode.CompressionOp;
import org.graalvm.compiler.nodes.type.NarrowOopStamp;

import com.oracle.svm.core.graal.meta.SubstrateMemoryAccessProvider;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.meta.CompressedNullConstant;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class SubstrateNarrowOopStamp extends NarrowOopStamp {
    public SubstrateNarrowOopStamp(ResolvedJavaType type, boolean exactType, boolean nonNull, boolean alwaysNull, boolean alwaysArray, CompressEncoding encoding) {
        super(type, exactType, nonNull, alwaysNull, alwaysArray, encoding);
        assert getEncoding().equals(ReferenceAccess.singleton().getCompressEncoding()) : "Using a non-default encoding is not supported: reference map support is needed.";
    }

    @Override
    protected AbstractObjectStamp copyWith(ResolvedJavaType type, boolean exactType, boolean nonNull, boolean alwaysNull, boolean alwaysArray) {
        return new SubstrateNarrowOopStamp(type, exactType, nonNull, alwaysNull, alwaysArray, getEncoding());
    }

    public static AbstractObjectStamp compressed(AbstractObjectStamp stamp, CompressEncoding encoding) {
        return new SubstrateNarrowOopStamp(stamp.type(), stamp.isExactType(), stamp.nonNull(), stamp.alwaysNull(), stamp.isAlwaysArray(), encoding);
    }

    @Override
    public Constant readConstant(MemoryAccessProvider memoryAccessProvider, Constant base, long displacement) {
        JavaConstant constant = ((SubstrateMemoryAccessProvider) memoryAccessProvider).readNarrowObjectConstant(base, displacement, getEncoding());
        assert constant == null || ((CompressibleConstant) constant).isCompressed();
        return constant;
    }

    @Override
    public JavaConstant nullConstant() {
        return CompressedNullConstant.COMPRESSED_NULL;
    }

    @Override
    public boolean isCompatible(Constant c) {
        return c instanceof CompressibleConstant && ((CompressibleConstant) c).isCompressed();
    }

    public static Stamp mkStamp(CompressionOp op, Stamp input, CompressEncoding encoding) {
        switch (op) {
            case Compress:
                if (input instanceof ObjectStamp) {
                    return compressed((ObjectStamp) input, encoding);
                }
                break;
            case Uncompress:
                if (input instanceof NarrowOopStamp) {
                    NarrowOopStamp inputStamp = (NarrowOopStamp) input;
                    assert encoding.equals(inputStamp.getEncoding());
                    return inputStamp.uncompressed();
                }
                break;
        }
        throw GraalError.shouldNotReachHere("Unexpected input stamp " + input);
    }
}
