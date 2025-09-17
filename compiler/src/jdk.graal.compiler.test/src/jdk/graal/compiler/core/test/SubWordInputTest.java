/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_int;

import java.lang.classfile.ClassFile;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import jdk.vm.ci.meta.ResolvedJavaMethod;

@RunWith(Parameterized.class)
public class SubWordInputTest extends GraalCompilerTest implements CustomizedBytecodePattern {

    @Parameters(name = "{0}, {1}")
    public static List<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        for (int i : new int[]{0xFFFF0000, 0xFFFF0001, 0x0000FFFF}) {
            ret.add(new Object[]{TypeKind.BOOLEAN, i});
            ret.add(new Object[]{TypeKind.BYTE, i});
            ret.add(new Object[]{TypeKind.SHORT, i});
            ret.add(new Object[]{TypeKind.CHAR, i});
        }
        return ret;
    }

    private static final String GET = "get";
    private static final String WRAPPER = "wrapper";

    private final TypeKind kind;
    private final int value;

    public SubWordInputTest(TypeKind kind, int value) {
        this.kind = kind;
        this.value = value;
    }

    @Test
    public void testSubWordInput() throws ClassNotFoundException {
        Class<?> testClass = getClass(SubWordInputTest.class.getName() + "$" + kind.toString().toLowerCase() + "Getter");
        ResolvedJavaMethod wrapper = getResolvedJavaMethod(testClass, WRAPPER);
        Result expected = executeExpected(wrapper, null, value);
        // test standalone callee
        getCode(getResolvedJavaMethod(testClass, GET), null, false, true, getInitialOptions());
        assertEquals(executeExpected(wrapper, null, value), expected);
        // test with inlining
        testAgainstExpected(wrapper, expected, null, new Object[]{value});
    }

    @Override
    public byte[] generateClass(String className) {
        ClassDesc thisClass = ClassDesc.of(className);
        ClassDesc targetType = kind.upperBound();
        MethodTypeDesc getMethodTypeDesc = MethodTypeDesc.of(targetType, targetType);

        // @formatter:off
        return ClassFile.of().build(thisClass, classBuilder -> classBuilder
                        .withMethodBody(GET, getMethodTypeDesc, ACC_PUBLIC_STATIC, b -> b
                                        .iload(0)
                                        .ireturn())
                        .withMethodBody(WRAPPER, MethodTypeDesc.of(CD_boolean, CD_int), ACC_PUBLIC_STATIC, b -> b
                                        .iload(0)
                                        .invokestatic(thisClass, GET, getMethodTypeDesc)
                                        .iload(0)
                                        .conversion(TypeKind.INT, kind)
                                        .ifThenElse(Opcode.IF_ICMPEQ,
                                                        thenBlock -> thenBlock.iconst_1().ireturn(),
                                                        elseBlock -> elseBlock.iconst_0().ireturn())));
        // @formatter:on
    }
}
