/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.aarch64;

import java.util.EnumSet;

import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.aarch64.AArch64LIRInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import jdk.vm.ci.aarch64.AArch64;

/**
 * AArch64 variant of {@link com.oracle.svm.graal.amd64.AMD64CPUFeatureRegionOp}.
 */
@Platforms(Platform.AARCH64.class)
public class AArch64CPUFeatureRegionOp {
    public static final class AArch64CPUFeatureRegionEnterOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<AArch64CPUFeatureRegionEnterOp> TYPE = LIRInstructionClass.create(AArch64CPUFeatureRegionEnterOp.class);

        private final EnumSet<AArch64.CPUFeature> features;

        public AArch64CPUFeatureRegionEnterOp(EnumSet<AArch64.CPUFeature> features) {
            super(TYPE);
            this.features = features;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            masm.addFeatures(features);
        }
    }

    public static final class AArch64CPUFeatureRegionLeaveOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<AArch64CPUFeatureRegionLeaveOp> TYPE = LIRInstructionClass.create(AArch64CPUFeatureRegionLeaveOp.class);

        public AArch64CPUFeatureRegionLeaveOp() {
            super(TYPE);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            masm.removeFeatures();
        }
    }
}
