/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.asm.amd64.test;

import static org.junit.Assume.assumeTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.test.AssemblerTest;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.code.DataSection;
import jdk.graal.compiler.code.DataSection.Data;
import jdk.graal.compiler.code.DataSection.RawData;
import jdk.graal.compiler.code.DataSection.SerializableData;
import org.junit.Before;
import org.junit.Test;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.site.DataSectionReference;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

public class SimpleAssemblerTest extends AssemblerTest {

    @Before
    public void checkAMD64() {
        assumeTrue("skipping AMD64 specific test", codeCache.getTarget().arch instanceof AMD64);
    }

    @Test
    public void intTest() {
        CodeGenTest test = new CodeGenTest() {

            @Override
            public byte[] generateCode(CompilationResult compResult, TargetDescription target, RegisterConfig registerConfig, CallingConvention cc) {
                AMD64Assembler asm = new AMD64Assembler(target);
                Register ret = registerConfig.getReturnRegister(JavaKind.Int);
                asm.movl(ret, 8472);
                asm.ret(0);
                return asm.close(true);
            }
        };
        assertReturn("intStub", test, 8472);
    }

    @Test
    public void doubleTest() {
        CodeGenTest test = new CodeGenTest() {

            @Override
            public byte[] generateCode(CompilationResult compResult, TargetDescription target, RegisterConfig registerConfig, CallingConvention cc) {
                AMD64MacroAssembler asm = new AMD64MacroAssembler(target);
                Register ret = registerConfig.getReturnRegister(JavaKind.Double);
                Data data = new SerializableData(JavaConstant.forDouble(84.72), 8);
                DataSectionReference ref = compResult.getDataSection().insertData(data);
                compResult.recordDataPatch(asm.position(), ref);
                asm.movdbl(ret, asm.getPlaceholder(-1));
                asm.ret(0);
                return asm.close(true);
            }
        };
        assertReturn("doubleStub", test, 84.72);
    }

    @SuppressWarnings("unlikely-arg-type")
    @Test
    public void rawDoubleTest() {
        CodeGenTest test = new CodeGenTest() {

            @Override
            public byte[] generateCode(CompilationResult compResult, TargetDescription target, RegisterConfig registerConfig, CallingConvention cc) {
                AMD64MacroAssembler asm = new AMD64MacroAssembler(target);
                Register ret = registerConfig.getReturnRegister(JavaKind.Double);

                byte[] rawBytes = new byte[8];
                ByteBuffer.wrap(rawBytes).order(ByteOrder.nativeOrder()).putDouble(84.72);
                Data data = new RawData(rawBytes, 8);
                DataSectionReference ref = compResult.getDataSection().insertData(data);

                // tests Data class
                assertTrue(data.equals(data));
                assertFalse(data.equals(new RawData(rawBytes, 8))); // unequal ref
                assertFalse(data.equals(new RawData(rawBytes, 0)));
                // test DataSection class
                DataSection dataSection = compResult.getDataSection();
                assertTrue(dataSection.toString().length() > 0); // check for NPE
                assertTrue(dataSection.equals(dataSection));
                assertFalse(dataSection.equals(ret));

                compResult.recordDataPatch(asm.position(), ref);
                asm.movdbl(ret, asm.getPlaceholder(-1));
                asm.ret(0);
                return asm.close(true);
            }
        };
        assertReturn("doubleStub", test, 84.72);
    }

    public static int intStub() {
        return 0;
    }

    public static double doubleStub() {
        return 0.0;
    }

    @Test
    public void testDataSections() {
        Data serializableData = new SerializableData(JavaConstant.forDouble(84.72), 8);
        Data serializableData2 = new SerializableData(JavaConstant.forInt(42), 8);
        assertTrue(serializableData.toString().length() > 0); // just check for NPE
        assertTrue(serializableData2.toString().length() > 0); // just check for NPE

        DataSection.PackedData packedData = new DataSection.PackedData(0, 8, new Data[]{serializableData});
        assertTrue(packedData.toString().length() >= 0); // just check for NPE
    }
}
