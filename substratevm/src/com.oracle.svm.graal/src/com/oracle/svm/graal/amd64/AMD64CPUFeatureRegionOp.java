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
package com.oracle.svm.graal.amd64;

import java.util.EnumSet;

import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.amd64.AMD64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.cpufeature.RuntimeCPUFeatureCheck;

import jdk.vm.ci.amd64.AMD64;

/**
 * Marker operation for instructing the assembler to switch into a mode with or without support for
 * a given CPU feature. Regions with special CPU features must be guarded by appropriate checks
 * ({@link RuntimeCPUFeatureCheck#isSupported}).
 * <p>
 * Because we cannot predict the final code layout on the graph level, regions using special CPU
 * features must not stretch over LIR block boundaries. In other words, every LIR block using
 * operations requiring a special CPU feature must have an enter/exit region pair. Such LIR blocks
 * may contain operations with internal control flow, as long as such operations expand to assembly
 * code with a single entry and a single exit, so that the enter/leave region pair guards a
 * consecutively laid out section of machine code.
 */
@Platforms(Platform.AMD64.class)
public class AMD64CPUFeatureRegionOp {
    public static final class AMD64CPUFeatureRegionEnterOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<AMD64CPUFeatureRegionEnterOp> TYPE = LIRInstructionClass.create(AMD64CPUFeatureRegionEnterOp.class);

        private final EnumSet<AMD64.CPUFeature> features;

        public AMD64CPUFeatureRegionEnterOp(EnumSet<AMD64.CPUFeature> features) {
            super(TYPE);
            this.features = features;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            masm.addFeatures(features);
        }
    }

    public static final class AMD64CPUFeatureRegionLeaveOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<AMD64CPUFeatureRegionLeaveOp> TYPE = LIRInstructionClass.create(AMD64CPUFeatureRegionLeaveOp.class);

        public AMD64CPUFeatureRegionLeaveOp() {
            super(TYPE);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (masm.isCurrentRegionFeature(AMD64.CPUFeature.AVX)) {
                masm.vzeroupper();
            }
            masm.removeFeatures();
        }
    }
}
