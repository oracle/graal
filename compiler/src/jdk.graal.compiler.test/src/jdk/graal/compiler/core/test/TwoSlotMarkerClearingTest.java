/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;

import java.lang.classfile.ClassFile;
import java.lang.classfile.Opcode;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class TwoSlotMarkerClearingTest extends GraalCompilerTest implements CustomizedBytecodePattern {

    @Test
    public void testTwoSlotMarkerClearing() throws ClassNotFoundException {
        Class<?> testClass = getClass("Test");
        ResolvedJavaMethod t1 = getResolvedJavaMethod(testClass, "t1");
        parseForCompile(t1);
        ResolvedJavaMethod t2 = getResolvedJavaMethod(testClass, "t2");
        parseForCompile(t2);
    }

    @Override
    public byte[] generateClass(String className) {
        // @formatter:off
        return ClassFile.of().build(ClassDesc.of(className), classBuilder -> classBuilder
                        .withMethodBody("t1", MethodTypeDesc.of(CD_int, CD_long, CD_int, CD_int), ACC_PUBLIC_STATIC, b -> b
                                        .iload(2)
                                        .istore(0)
                                        .iload(0)
                                        .ifThenElse(Opcode.IFLT,
                                                        thenBlock -> thenBlock.iload(0).ireturn(),
                                                        elseBlock -> elseBlock.iload(3).ireturn()))
                        .withMethodBody("t2", MethodTypeDesc.of(CD_long, CD_int, CD_long, CD_int, CD_long), ACC_PUBLIC_STATIC, b -> b
                                        .lload(1)
                                        .lstore(0)
                                        .iload(3)
                                        .ifThenElse(Opcode.IFLT,
                                                        thenBlock -> thenBlock.lload(0).lreturn(),
                                                        elseBlock -> elseBlock.lload(4).lreturn())));
        // @formatter:on
    }
}
