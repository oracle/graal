/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.nio.ByteBuffer;

import org.graalvm.compiler.code.DataSection.Data;
import org.graalvm.compiler.code.DataSection.Patches;
import org.graalvm.compiler.code.DataSection.SerializableData;
import org.graalvm.compiler.code.DataSection.ZeroData;
import org.graalvm.compiler.lir.asm.DataBuilder;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.meta.SubstrateObjectConstant;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.SerializableConstant;
import jdk.vm.ci.meta.VMConstant;

public class SubstrateDataBuilder extends DataBuilder {
    @Override
    public Data createDataItem(Constant constant) {
        int size;
        if (constant instanceof VMConstant) {
            assert constant instanceof SubstrateObjectConstant : "should be only VMConstant";
            return new ObjectData((SubstrateObjectConstant) constant);
        } else if (JavaConstant.isNull(constant)) {
            if (SubstrateObjectConstant.isCompressed((JavaConstant) constant)) {
                size = ConfigurationValues.getObjectLayout().getReferenceSize();
            } else {
                size = FrameAccess.uncompressedReferenceSize();
            }
            return ZeroData.create(size, size);
        } else if (constant instanceof SerializableConstant) {
            SerializableConstant s = (SerializableConstant) constant;
            return new SerializableData(s);
        } else {
            throw new JVMCIError(String.valueOf(constant));
        }
    }

    public static class ObjectData extends Data {
        private final SubstrateObjectConstant constant;

        protected ObjectData(SubstrateObjectConstant constant) {
            super(ConfigurationValues.getObjectLayout().getReferenceSize(), ConfigurationValues.getObjectLayout().getReferenceSize());
            assert constant.isCompressed() == ReferenceAccess.singleton().haveCompressedReferences() : "Constant object references in compiled code must be compressed (base-relative)";
            this.constant = constant;
        }

        public SubstrateObjectConstant getConstant() {
            return constant;
        }

        @Override
        protected void emit(ByteBuffer buffer, Patches patches) {
            int position = buffer.position();
            if (getSize() == Integer.BYTES) {
                buffer.putInt(0);
            } else if (getSize() == Long.BYTES) {
                buffer.putLong(0L);
            } else {
                shouldNotReachHere("Unsupported object constant reference size: " + getSize());
            }
            patches.registerPatch(position, constant);
        }
    }
}
