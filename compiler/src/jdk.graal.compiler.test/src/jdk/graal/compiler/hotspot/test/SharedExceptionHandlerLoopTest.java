/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.test;

import java.lang.classfile.ClassFile;
import java.lang.classfile.Label;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;

import org.junit.Test;

import jdk.graal.compiler.core.test.CustomizedBytecodePattern;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests a bytecode pattern where two ExceptionDispatchBlocks - one inside a loop and one outside -
 * dispatch to the same exception handler. Such a pattern cannot be produced by javac from plain
 * Java code, but the Kotlin compiler can produce such patterns when compiling coroutines. See
 * {@link #generateClass} for the Java source code from which the modified bytecode is derived.
 */
public class SharedExceptionHandlerLoopTest extends GraalCompilerTest implements CustomizedBytecodePattern {

    @Test
    public void test() throws ClassNotFoundException {
        Class<?> testClass = getClass("TestClass");
        ResolvedJavaMethod method = getResolvedJavaMethod(testClass, "testMethod");
        compile(method, null);

        /*
         * Returns o1.toString().
         */
        test(method, null, new Object(), new NPEThrower());
        /*
         * Returns null because o1.toString() throws an NPE.
         */
        test(method, null, new NPEThrower(), new NPEThrower());
        /*
         * Exits the loop because o1.toString() throws an IAE, returns null because o2.toString()
         * throws a NPE.
         */
        test(method, null, new IAEThrower(), new NPEThrower());
        /*
         * Exits the loop because o1.toString() throws an IAE, returns o2.toString().
         */
        test(method, null, new IllegalArgumentException(), new Object());
    }

    public static class NPEThrower {
        @Override
        public String toString() {
            throw new NullPointerException();
        }
    }

    public static class IAEThrower {
        @Override
        public String toString() {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Produces bytecode which resembles the following Java code except that both NPE-catches
     * dispatch to the same handler. The first exception dispatch happens from inside the loop, the
     * second from outside.
     *
     * <pre>
     * public class TestClass {
     *     public static Object testMethod(Object o1, Object o2) {
     *         for (;;) {
     *             try {
     *                 return o1.toString();
     *             } catch (NullPointerException e) {
     *                 // same NPE handler as below. dispatch block inside loop
     *                 return null;
     *             } catch (SecurityException e) {
     *                 // continue (endless loop)
     *             } catch (Exception e) {
     *                 break;
     *             }
     *         }
     *
     *         try {
     *             return o2.toString();
     *         } catch (NullPointerException npe) {
     *             // same NPE handler as above. dispatch block outside loop
     *             return null;
     *         }
     *     }
     * }
     * </pre>
     */
    @Override
    public byte[] generateClass(String internalClassName) {
        MethodTypeDesc getMethodTypeDesc = MethodTypeDesc.of(ConstantDescs.CD_Object, ConstantDescs.CD_Object, ConstantDescs.CD_Object);

        ClassDesc thisClass = ClassDesc.of(internalClassName.replace('/', '.'));
        return ClassFile.of().build(thisClass, classBuilder -> classBuilder
        // @formatter:off
                        .withMethod("testMethod", getMethodTypeDesc, ACC_PUBLIC_STATIC, methodBuilder -> methodBuilder.withCode(codeBuilder -> {
                            Label start = codeBuilder.newLabel();
                            Label loopExcEnd = codeBuilder.newLabel();
                            Label loopNPEHandler = codeBuilder.newLabel();
                            Label loopIAEHandler = codeBuilder.newLabel();
                            Label loopEHandler = codeBuilder.newLabel();
                            Label retLabel = codeBuilder.newLabel();

                            Label afterLoopExcStart = codeBuilder.newLabel();
                            Label afterLoopExcEnd = codeBuilder.newLabel();

                            codeBuilder
                                            .labelBinding(start)
                                            .aload(0)
                                            .invokevirtual(ConstantDescs.CD_Object, "toString", MethodTypeDesc.of(ConstantDescs.CD_String))
                                            .labelBinding(loopExcEnd)
                                            .areturn()
                                            .labelBinding(loopNPEHandler)
                                            .exceptionCatch(start, loopExcEnd, loopNPEHandler, cd(NullPointerException.class))
                                            .astore(2)
                                            .aconst_null()
                                            .areturn()
                                            .labelBinding(loopIAEHandler)
                                            .exceptionCatch(start, loopExcEnd, loopIAEHandler, cd(IllegalAccessError.class))
                                            .astore(2)
                                            .goto_(start)
                                            .labelBinding(loopEHandler)
                                            .exceptionCatch(start, loopExcEnd, loopEHandler, ConstantDescs.CD_Exception)
                                            .astore(2)
                                            .goto_(retLabel)
                                            .labelBinding(retLabel)
                                            .labelBinding(afterLoopExcStart)
                                            .aload(1)
                                            .invokevirtual(ConstantDescs.CD_Object, "toString", MethodTypeDesc.of(ConstantDescs.CD_String))
                                            .labelBinding(afterLoopExcEnd)
                                            .exceptionCatch(afterLoopExcStart, afterLoopExcEnd, loopNPEHandler, cd(NullPointerException.class))
                                            .areturn();
                        })));
        // @formatter:on
    }
}
