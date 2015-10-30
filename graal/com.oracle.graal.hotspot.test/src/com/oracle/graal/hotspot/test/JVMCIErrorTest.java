/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.test;

import java.util.function.Consumer;

import jdk.vm.ci.code.CompilationResult;
import jdk.vm.ci.code.CompilationResult.ConstantReference;
import jdk.vm.ci.code.CompilationResult.DataPatch;
import jdk.vm.ci.code.CompilationResult.DataSectionReference;
import jdk.vm.ci.code.CompilationResult.Infopoint;
import jdk.vm.ci.code.CompilationResult.Reference;
import jdk.vm.ci.code.DataSection.Data;
import jdk.vm.ci.code.DataSection.DataBuilder;
import jdk.vm.ci.code.InfopointReason;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspot.HotSpotConstant;
import jdk.vm.ci.meta.Assumptions.Assumption;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.VMConstant;

import org.junit.Test;

import com.oracle.graal.compiler.test.GraalCompilerTest;

public class JVMCIErrorTest extends GraalCompilerTest {

    public static void testMethod() {
    }

    private void test(Consumer<CompilationResult> modify) {
        ResolvedJavaMethod method = getResolvedJavaMethod("testMethod");
        CompilationResult compResult = compile(method, null);

        modify.accept(compResult);

        getCodeCache().addCode(method, compResult, null, null);
    }

    @Test(expected = JVMCIError.class)
    public void testInvalidAssumption() {
        test(r -> r.setAssumptions(new Assumption[]{new InvalidAssumption()}));
    }

    private static class InvalidAssumption extends Assumption {
    }

    @Test(expected = JVMCIError.class)
    public void testInvalidAlignment() {
        test(r -> r.getDataSection().insertData(new Data(7, 1, DataBuilder.zero(1))));
    }

    @Test(expected = JVMCIError.class)
    public void testInvalidDataSectionReference() {
        test(r -> {
            DataSectionReference ref = r.getDataSection().insertData(new Data(1, 1, DataBuilder.zero(1)));
            Data data = new Data(1, 1, (buffer, patch) -> {
                patch.accept(new DataPatch(buffer.position(), ref));
                buffer.put((byte) 0);
            });
            r.getDataSection().insertData(data);
        });
    }

    @Test(expected = JVMCIError.class)
    public void testInvalidNarrowMethodInDataSection() {
        test(r -> {
            ResolvedJavaMethod method = getResolvedJavaMethod("testMethod");
            HotSpotConstant c = (HotSpotConstant) method.getEncoding();
            Data data = new Data(4, 4, (buffer, patch) -> {
                patch.accept(new DataPatch(buffer.position(), new ConstantReference((VMConstant) c.compress())));
                buffer.putInt(0);
            });
            r.getDataSection().insertData(data);
        });
    }

    @Test(expected = JVMCIError.class)
    public void testInvalidConstantInDataSection() {
        test(r -> {
            Data data = new Data(1, 1, (buffer, patch) -> {
                patch.accept(new DataPatch(buffer.position(), new ConstantReference(new InvalidVMConstant())));
            });
            r.getDataSection().insertData(data);
        });
    }

    @Test(expected = JVMCIError.class)
    public void testInvalidConstantInCode() {
        test(r -> r.recordDataPatch(0, new ConstantReference(new InvalidVMConstant())));
    }

    private static class InvalidVMConstant implements VMConstant {

        public boolean isDefaultForKind() {
            return false;
        }

        public String toValueString() {
            return null;
        }
    }

    @Test(expected = JVMCIError.class)
    public void testInvalidReference() {
        test(r -> r.recordDataPatch(0, new InvalidReference()));
    }

    private static class InvalidReference extends Reference {

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public boolean equals(Object obj) {
            return false;
        }
    }

    @Test(expected = JVMCIError.class)
    public void testOutOfBoundsDataSectionReference() {
        test(r -> {
            DataSectionReference ref = new DataSectionReference();
            ref.setOffset(0x1000);
            r.recordDataPatch(0, ref);
        });
    }

    @Test(expected = JVMCIError.class)
    public void testInvalidMark() {
        test(r -> r.recordMark(0, new Object()));
    }

    @Test(expected = JVMCIError.class)
    public void testInvalidMarkInt() {
        test(r -> r.recordMark(0, -1));
    }

    @Test(expected = JVMCIError.class)
    public void testUnknownInfopointReason() {
        test(r -> r.addInfopoint(new Infopoint(0, null, InfopointReason.UNKNOWN)));
    }

    @Test(expected = JVMCIError.class)
    public void testInfopointMissingDebugInfo() {
        test(r -> r.addInfopoint(new Infopoint(0, null, InfopointReason.METHOD_START)));
    }

    @Test(expected = JVMCIError.class)
    public void testSafepointMissingDebugInfo() {
        test(r -> r.addInfopoint(new Infopoint(0, null, InfopointReason.SAFEPOINT)));
    }
}
