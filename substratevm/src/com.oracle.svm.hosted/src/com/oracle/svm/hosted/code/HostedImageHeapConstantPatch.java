/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.code;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.image.RelocatableBuffer;
import com.oracle.svm.util.ClassUtil;
import jdk.graal.compiler.code.CompilationResult;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.site.Reference;
import jdk.vm.ci.meta.JavaConstant;

import java.nio.ByteBuffer;

/**
 * Represents a patch for machine code during image generation. At the specified operand position,
 * the location of the constant (relative to the beginning of the image heap) must be added to the
 * value already present at that location.
 */
public class HostedImageHeapConstantPatch extends CompilationResult.CodeAnnotation {

    public final JavaConstant constant;

    public HostedImageHeapConstantPatch(int operandPosition, JavaConstant constant) {
        super(operandPosition);
        this.constant = constant;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this;
    }

    public HostedPatcher createPatcher(NativeImageHeap imageHeap, TargetDescription target) {
        return new Patcher(imageHeap, target);
    }

    private class Patcher implements HostedPatcher {
        private final NativeImageHeap imageHeap;
        private final TargetDescription target;

        Patcher(NativeImageHeap imageHeap, TargetDescription target) {
            this.imageHeap = imageHeap;
            this.target = target;
        }

        @Override
        public void relocate(Reference ref, RelocatableBuffer relocs, int compStart) {
            throw new UnsupportedOperationException("We don't support emitting relocations for image heap constants");
        }

        @Override
        @SuppressWarnings("unused")
        public void patch(int compStart, int relative, byte[] code) {
            NativeImageHeap.ObjectInfo objectInfo = imageHeap.getConstantInfo(constant);
            long objectAddress = objectInfo.getOffset();
            var targetCode = ByteBuffer.wrap(code).order(target.arch.getByteOrder());
            int originalValue = targetCode.getInt(getPosition());
            long newValue = originalValue + objectAddress;
            VMError.guarantee(NumUtil.isInt(newValue), "Image heap size is limited to 2 GByte");
            targetCode.putInt(getPosition(), (int) newValue);
        }

        @Override
        public String toString() {
            return ClassUtil.getUnqualifiedName(getClass()) + "{imageHeap=" + imageHeap + ", target=" + target + '}';
        }

    }

    @Override
    public String toString() {
        return ClassUtil.getUnqualifiedName(getClass()) + "{constant=" + constant + '}';
    }
}
