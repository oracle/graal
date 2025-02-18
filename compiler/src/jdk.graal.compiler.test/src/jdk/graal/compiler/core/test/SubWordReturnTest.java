/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static java.lang.classfile.ClassFile.ACC_PRIVATE;
import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.constant.ConstantDescs.CD_int;

import java.lang.classfile.ClassFile;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.ConstantValueAttribute;
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
public class SubWordReturnTest extends GraalCompilerTest implements CustomizedBytecodePattern {

    @Parameters(name = "{0}, {1}")
    public static List<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        for (int i : new int[]{1000000, 1000001, -1000000, -1}) {
            ret.add(new Object[]{TypeKind.BOOLEAN, i});
            ret.add(new Object[]{TypeKind.BYTE, i});
            ret.add(new Object[]{TypeKind.SHORT, i});
            ret.add(new Object[]{TypeKind.CHAR, i});
        }
        return ret;
    }

    private static final String GET = "get";
    private static final String WRAPPER = "wrapper";
    private static final String FIELD = "intField";

    private final TypeKind kind;
    private final int value;

    public SubWordReturnTest(TypeKind kind, int value) {
        this.kind = kind;
        this.value = value;
    }

    @Test
    public void testSubWordReturn() throws ClassNotFoundException {
        Class<?> testClass = getClass(SubWordReturnTest.class.getName() + "$" + kind.toString().toLowerCase() + "Getter");
        ResolvedJavaMethod method = getResolvedJavaMethod(testClass, WRAPPER);
        test(method, null);
    }

    /**
     * {@link #generateClass} generates a class looking like this for the types boolean, byte,
     * short, and char.
     */
    static class ByteGetter {

        // private static int intField = 1000000;

        private static byte get() {
            // GETSTATIC intField
            // IRETURN
            return 0;
        }

        public static int wrapper() {
            return get();
        }
    }

    @Override
    public byte[] generateClass(String className) {
        ClassDesc thisClass = ClassDesc.of(className);
        ClassDesc targetType = kind.upperBound();
        MethodTypeDesc getMethodTypeDesc = MethodTypeDesc.of(targetType);

        // @formatter:off
        return ClassFile.of().build(thisClass, classBuilder -> classBuilder
                        .withField(FIELD, CD_int, fieldBuilder -> fieldBuilder
                                        .withFlags(ACC_PRIVATE | ACC_STATIC)
                                        .with(ConstantValueAttribute.of(value)))
                        .withMethodBody(GET, getMethodTypeDesc, ACC_PUBLIC_STATIC, b -> b
                                        .getstatic(thisClass, FIELD, CD_int)
                                        .ireturn())
                        .withMethodBody(WRAPPER, MethodTypeDesc.of(CD_int), ACC_PUBLIC_STATIC, b -> b
                                        .invokestatic(thisClass, GET, getMethodTypeDesc)
                                        .ireturn()));
        // @formatter:on
    }
}
