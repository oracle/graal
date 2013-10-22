/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.meta.test;

import org.junit.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.java.*;

/**
 * Tests for {@link BytecodeDisassemblerProvider}.
 */
public class TestBytecodeDisassemblerProvider extends MethodUniverse {

    public TestBytecodeDisassemblerProvider() {
    }

    /**
     * Tests that successive disassembling of the same method produces the same result.
     */
    @Test
    public void disassembleTest() {
        BytecodeDisassemblerProvider dis = new BytecodeDisassembler();
        if (dis != null) {
            int count = 0;
            for (ResolvedJavaMethod m : methods.values()) {
                String disasm1 = dis.disassemble(m);
                String disasm2 = dis.disassemble(m);
                if (disasm1 == null) {
                    Assert.assertTrue(disasm2 == null);
                } else {
                    Assert.assertTrue(String.valueOf(m), disasm1.length() > 0);
                    Assert.assertEquals(String.valueOf(m), disasm1, disasm2);
                }
                if (count++ > 20) {
                    break;
                }
            }
        }
    }
}
