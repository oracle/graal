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

import static java.lang.constant.ConstantDescs.CD_Class;
import static java.lang.constant.ConstantDescs.CD_Exception;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.CD_void;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.ExceptionsAttribute;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import jdk.graal.compiler.test.GraalTest;
import jdk.internal.misc.Unsafe;

@RunWith(Parameterized.class)
public class SubWordFieldStoreTest extends GraalCompilerTest implements CustomizedBytecodePattern {

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
    private static final String FIELD = "field";

    private final TypeKind kind;
    private final int value;
    private final boolean unsafeStore;
    private final boolean unsafeLoad;

    public SubWordFieldStoreTest(TypeKind kind, int value, boolean unsafeStore, boolean unsafeLoad) {
        this.kind = kind;
        this.value = value;
        this.unsafeStore = unsafeStore;
        this.unsafeLoad = unsafeLoad;
    }

    @Test
    public void testFieldStore() throws ClassNotFoundException {
        Class<?> testClass = getClass(SubWordFieldStoreTest.class.getName() + "$" + kind.toString().toLowerCase() + "Getter");
        test(getResolvedJavaMethod(testClass, SNIPPET), null);
    }

    @Override
    public byte[] generateClass(String className) {
        ClassDesc thisClass = ClassDesc.of(className);
        ClassDesc targetType = kind.upperBound();

        // @formatter:off
        return ClassFile.of().build(thisClass, classBuilder -> classBuilder
                        .withField(FIELD, targetType, ACC_PUBLIC_STATIC)
                        .withMethod(SNIPPET, MethodTypeDesc.of(CD_boolean), ACC_PUBLIC_STATIC, methodBuilder -> methodBuilder
                                        .with(ExceptionsAttribute.ofSymbols(CD_Exception))
                                        .withCode(b -> generateSnippet(b, thisClass))));
        // @formatter:on
    }

    static String getUnsafePutMethodName(TypeKind kind) {
        String name = kind.name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    private void generateSnippet(CodeBuilder b, ClassDesc thisClass) {
        ClassDesc targetType = kind.upperBound();

        ClassDesc classField = cd(Field.class);
        ClassDesc classGraalTest = cd(GraalTest.class);
        ClassDesc classUnsafe = cd(Unsafe.class);

        FieldRefEntry unsafeField = ConstantPoolBuilder.of().fieldRefEntry(classGraalTest, "UNSAFE", classUnsafe);

        // @formatter:off
        if (unsafeStore | unsafeLoad) {
            b
                            .ldc(thisClass)
                            .ldc(FIELD)
                            .invokevirtual(CD_Class, "getField", MethodTypeDesc.of(classField, CD_String))
                            .astore(0)
                            .getstatic(unsafeField)
                            .aload(0)
                            .invokevirtual(classUnsafe, "staticFieldBase", MethodTypeDesc.of(CD_Object, classField))
                            .astore(1)
                            .getstatic(unsafeField)
                            .aload(0)
                            .invokevirtual(classUnsafe, "staticFieldOffset", MethodTypeDesc.of(CD_long, classField))
                            .lstore(2);
        }

        if (unsafeStore) {
            b.getstatic(unsafeField)
                            .aload(1)
                            .lload(2)
                            .ldc(value)
                            .invokevirtual(classUnsafe, "put" + getUnsafePutMethodName(kind), MethodTypeDesc.of(CD_void, CD_Object, CD_long, targetType));
        } else {
            b.ldc(value)
                            .putstatic(thisClass, FIELD, targetType);
        }
        if (unsafeLoad) {
            b.getstatic(unsafeField)
                            .aload(1)
                            .lload(2)
                            .invokevirtual(classUnsafe, "get" + getUnsafePutMethodName(kind), MethodTypeDesc.of(targetType, CD_Object, CD_long));
        } else {
            b.getstatic(thisClass, FIELD, targetType);
        }

        b.ldc(value)
                        .conversion(TypeKind.INT, kind)
                        .ifThenElse(Opcode.IF_ICMPEQ,
                                        thenBlock -> thenBlock.iconst_1().ireturn(),
                                        elseBlock -> elseBlock.iconst_0().ireturn());
        // @formatter:on
    }
}
