/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.amd64.test;

import static com.oracle.graal.amd64.AMD64.*;

import java.lang.reflect.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.phases.*;

/**
 * Ensures that frame omission works in cases where it is expected to.
 */
public class AMD64FrameOmissionTest extends GraalCompilerTest {

    interface CodeGenerator {

        void generateCode(AMD64Assembler asm);
    }

    public static void test1snippet() {
        return;
    }

    @Test
    public void test1() {
        testHelper("test1snippet", new CodeGenerator() {

            @Override
            public void generateCode(AMD64Assembler asm) {
                asm.ret(0);
            }
        });
    }

    public static int test2snippet(int x) {
        return x + 5;
    }

    @Test
    public void test2() {
        testHelper("test2snippet", new CodeGenerator() {

            @Override
            public void generateCode(AMD64Assembler asm) {
                asm.addl(rsi, 5);
                asm.movl(rax, rsi);
                asm.ret(0);
            }
        });
    }

    public static double test3snippet(double x) {
        return 42.0D / x;
    }

    @Test
    public void test3() {
        testHelper("test3snippet", new CodeGenerator() {

            @Override
            public void generateCode(AMD64Assembler asm) {
                asm.movsd(xmm1, new AMD64Address(rip, -40));
                asm.divsd(xmm1, xmm0);
                asm.movapd(xmm0, xmm1);
                asm.ret(0);
            }
        });
    }

    private void testHelper(String name, CodeGenerator gen) {
        Method method = getMethod(name);
        ResolvedJavaMethod javaMethod = runtime.lookupJavaMethod(method);
        InstalledCode installedCode = getCode(javaMethod, parse(method));

        CodeCacheProvider codeCache = Graal.getRequiredCapability(CodeCacheProvider.class);
        TargetDescription target = codeCache.getTarget();
        RegisterConfig registerConfig = codeCache.lookupRegisterConfig();
        AMD64Assembler asm = new AMD64Assembler(target, registerConfig);

        gen.generateCode(asm);
        for (int i = 0; i < GraalOptions.MethodEndBreakpointGuards; ++i) {
            asm.int3();
        }
        while ((asm.codeBuffer.position() % 8) != 0) {
            asm.hlt();
        }

        byte[] expectedCode = asm.codeBuffer.close(true);
        byte[] actualCode = installedCode.getCode();

        Assert.assertArrayEquals(expectedCode, actualCode);
    }
}
