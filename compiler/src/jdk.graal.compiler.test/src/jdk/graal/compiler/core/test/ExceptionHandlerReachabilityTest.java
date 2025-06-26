/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.classfile.ClassFile;
import java.lang.classfile.Label;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.common.PermanentBailoutException;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests that the parser and compiler can handle bytecode where exception handlers are reachable
 * from normal control flow. Such bytecode can be the result of obfuscation or code shrinking tools.
 * For example:
 *
 * <pre>
 * try{
 *   foo()
 * } catch (Exception e) {
 *   x = baz(x);
 *   return x;
 * }
 * (...)
 * try{
 *   bar()
 * } catch (Exception e) {
 *   doSomething()
 *   x = baz(x);
 *   return x;
 * }
 * </pre>
 *
 * On bytecode level, the second exception handler can re-use the first handler:
 *
 * <pre>
 * try{
 *   bar()
 * } catch (Exception e) {
 *   doSomething()
 *   goto handlerFoo
 * }
 * </pre>
 *
 */
public class ExceptionHandlerReachabilityTest extends GraalCompilerTest implements CustomizedBytecodePattern {

    @Test
    public void test() {
        try {
            Class<?> testClass = getClass(SharedExceptionHandlerClass.class.getName() + "$Test");
            ResolvedJavaMethod method = asResolvedJavaMethod(testClass.getMethod("sharedExceptionHandlerMethod", int.class));

            // test successful parsing
            parseEager(method, AllowAssumptions.YES, getInitialOptions());

            // test successful compilation + execution
            int actual = (int) test(method, null, 11).returnValue;
            int expected = SharedExceptionHandlerClass.sharedExceptionHandlerMethod(11);
            Assert.assertEquals(expected, actual);
        } catch (PermanentBailoutException e) {
            Assert.fail(e.getMessage());
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw GraalError.shouldNotReachHere(e);
        }
    }

    @Override
    public byte[] generateClass(String className) {
        ClassDesc classSharedExceptionHandlerClass = cd(SharedExceptionHandlerClass.class);
        MethodTypeDesc mtdII = MethodTypeDesc.of(CD_int, CD_int);

        // @formatter:off
        return ClassFile.of().build(ClassDesc.of(className), classBuilder -> classBuilder
                        .withMethodBody("sharedExceptionHandlerMethod", mtdII, ACC_PUBLIC_STATIC, b -> {
                            Label handlerEx1 = b.newLabel();
                            b
                                            .iload(0)
                                            .istore(1)
                                            .trying(tryBlock -> {
                                                tryBlock
                                                                .iload(1)
                                                                .invokestatic(classSharedExceptionHandlerClass, "foo", mtdII)
                                                                .istore(1);
                                            }, catchBuilder -> catchBuilder.catching(cd(IllegalArgumentException.class), catchBlock -> {
                                                catchBlock
                                                                .labelBinding(handlerEx1)
                                                                .iload(1)
                                                                .invokestatic(classSharedExceptionHandlerClass, "baz", mtdII)
                                                                .istore(1)
                                                                .iload(1)
                                                                .ireturn();
                                            }))
                                            .trying(tryBlock -> {
                                                tryBlock
                                                                .iload(1)
                                                                .invokestatic(classSharedExceptionHandlerClass, "bar", mtdII)
                                                                .istore(1);
                                            }, catchBuilder -> catchBuilder.catching(cd(NumberFormatException.class), catchBlock -> {
                                                catchBlock
                                                                .iload(1)
                                                                .invokestatic(classSharedExceptionHandlerClass, "doSomething", mtdII)
                                                                .istore(1)
                                                                .goto_(handlerEx1);
                                            }))
                                            .iload(1)
                                            .ireturn();
                        }));
        // @formatter:on
    }

    public class SharedExceptionHandlerClass {
        /**
         * The bytecode of this method will be modified and placed in an inner class. The modified
         * bytecode contains a {@code goto} from within the second exception handler to the first
         * exception handler. This reduces the overall bytecode size due to code sharing. The
         * pattern is produced by code obfuscation tools, see [GR-47376].
         */
        public static int sharedExceptionHandlerMethod(int i) {
            int x = i;
            try {
                x = foo(x);
            } catch (IllegalArgumentException e1) {
                x = baz(x);
                return x;
            }
            try {
                x = bar(x);
            } catch (NumberFormatException e2) {
                x = doSomething(x);
                // The following code will be replaced by a goto to the first exception handler:
                x = baz(x);
                return x;
            }
            return x;
        }

        public static int foo(int x) throws IllegalArgumentException {
            if (x < 0) {
                throw new IllegalArgumentException();
            }

            return x * 10;
        }

        public static int bar(int x) throws NumberFormatException {
            if (x > 100) {
                throw new NumberFormatException();
            }

            return x * 1000;
        }

        public static int baz(int x) {
            return x * x;
        }

        public static int doSomething(int x) {
            return x * x;
        }
    }
}
