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

import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.MTD_void;
import static jdk.graal.compiler.core.test.SubWordFieldStoreTest.getUnsafePutMethodName;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.test.GraalTest;
import jdk.internal.misc.Unsafe;

@RunWith(Parameterized.class)
public class SubWordArrayStoreTest extends GraalCompilerTest implements CustomizedBytecodePattern {

    @Parameterized.Parameters(name = "{0}, {1}, {2}, {3}")
    public static List<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        for (int i : new int[]{0xFFFF0000, 0xFFFF0001, 0x0000FFFF, 0x01020304}) {
            for (boolean unsafeStore : new boolean[]{false, true}) {
                for (boolean unsafeLoad : new boolean[]{false, true}) {
                    ret.add(new Object[]{TypeKind.BOOLEAN, i, unsafeStore, unsafeLoad});
                    ret.add(new Object[]{TypeKind.BYTE, i, unsafeStore, unsafeLoad});
                    ret.add(new Object[]{TypeKind.SHORT, i, unsafeStore, unsafeLoad});
                    ret.add(new Object[]{TypeKind.CHAR, i, unsafeStore, unsafeLoad});
                }
            }
        }
        return ret;
    }

    private static final String SNIPPET = "snippet";
    private static final String FIELD = "array";

    private final TypeKind kind;
    private final int value;
    private final boolean unsafeStore;
    private final boolean unsafeLoad;

    public SubWordArrayStoreTest(TypeKind kind, int value, boolean unsafeStore, boolean unsafeLoad) {
        this.kind = kind;
        this.value = value;
        this.unsafeStore = unsafeStore;
        this.unsafeLoad = unsafeLoad;
    }

    @Test
    public void testArrayStore() throws ClassNotFoundException {
        Class<?> testClass = getClass(SubWordArrayStoreTest.class.getName() + "$" + kind.toString().toLowerCase() + "Getter");
        test(getResolvedJavaMethod(testClass, SNIPPET), null);
    }

    @Override
    public byte[] generateClass(String className) {
        ClassDesc thisClass = ClassDesc.of(className);
        ClassDesc targetType = kind.upperBound();

        // @formatter:off
        return ClassFile.of().build(thisClass, classBuilder -> classBuilder
                        .withField(FIELD, targetType.arrayType(), ACC_PUBLIC_STATIC)
                        .withMethodBody("<clinit>", MTD_void, ACC_STATIC, b -> b
                                        .bipush(16)
                                        .newarray(kind)
                                        .putstatic(thisClass, FIELD, targetType.arrayType())
                                        .return_())
                        .withMethodBody(SNIPPET, MethodTypeDesc.of(CD_boolean), ACC_PUBLIC_STATIC, b -> generateSnippet(b, thisClass)));
        // @formatter:on
    }

    private static long arrayBaseOffset(TypeKind kind) {
        return switch (kind) {
            case BOOLEAN -> UNSAFE.arrayBaseOffset(boolean[].class);
            case BYTE -> UNSAFE.arrayBaseOffset(byte[].class);
            case SHORT -> UNSAFE.arrayBaseOffset(short[].class);
            case CHAR -> UNSAFE.arrayBaseOffset(char[].class);
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind);
        };
    }

    private void generateSnippet(CodeBuilder b, ClassDesc thisClass) {
        // @formatter:off
        ClassDesc targetType = kind.upperBound();

        ClassDesc classGraalTest = cd(GraalTest.class);
        ClassDesc classUnsafe = cd(Unsafe.class);

        ConstantPoolBuilder cpb = ConstantPoolBuilder.of();
        FieldRefEntry unsafeField = cpb.fieldRefEntry(classGraalTest, "UNSAFE", classUnsafe);
        FieldRefEntry arrayField = cpb.fieldRefEntry(thisClass, FIELD, targetType.arrayType());

        if (unsafeStore) {
            b
                            .getstatic(unsafeField)
                            .getstatic(arrayField)
                            .ldc(arrayBaseOffset(kind))
                            .ldc(value)
                            .invokevirtual(classUnsafe, "put" + getUnsafePutMethodName(kind), MethodTypeDesc.of(CD_void, CD_Object, CD_long, targetType));
        } else {
            b
                            .getstatic(arrayField)
                            .iconst_0()
                            .ldc(value);

            switch (kind) {
                case BOOLEAN, BYTE -> b.bastore();
                case SHORT -> b.sastore();
                case CHAR -> b.castore();
            }
        }

        if (unsafeLoad) {
            b
                            .getstatic(unsafeField)
                            .getstatic(arrayField)
                            .ldc(arrayBaseOffset(kind))
                            .invokevirtual(classUnsafe, "get" + getUnsafePutMethodName(kind), MethodTypeDesc.of(targetType, CD_Object, CD_long));
        } else {
            b
                            .getstatic(arrayField)
                            .iconst_0();

            switch (kind) {
                case BOOLEAN, BYTE -> b.baload();
                case SHORT -> b.saload();
                case CHAR -> b.caload();
            }
        }

        b
                        .ldc(value)
                        .conversion(TypeKind.INT, kind)
                        .ifThenElse(Opcode.IF_ICMPEQ,
                                        thenBlock -> thenBlock.iconst_1().ireturn(),
                                        elseBlock -> elseBlock.iconst_0().ireturn());
        // @formatter:on
    }
}
