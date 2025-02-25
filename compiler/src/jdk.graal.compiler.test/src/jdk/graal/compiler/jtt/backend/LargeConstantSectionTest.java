/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.jtt.backend;

import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;

import java.lang.classfile.ClassFile;
import java.lang.classfile.Label;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import jdk.graal.compiler.api.test.ExportingClassLoader;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.jtt.JTTTest;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import junit.framework.AssertionFailedError;

/**
 * This test let the compiler deal with a large amount of constant data in a method. This data is
 * stored typically in the constant section of the native method. Especially on the SPARC platform
 * the backend can address only 8k of memory with an immediate offset. Beyond this barrier, a
 * different addressing mode must be used.
 *
 * In order to do this this test generates a large method containing a large switch statement in
 * form of
 *
 * <code>
 *  static long run(long a) {
 *    switch(a) {
 *    case 1:
 *    return 0xF0F0F0F0F0L + 1;
 *    case 2:
 *    return 0xF0F0F0F0F0L + 2;
 *    ....
 *    default:
 *    return 0;
 *    }
 *
 *  }
 *  </code>
 *
 */
@RunWith(Parameterized.class)
public class LargeConstantSectionTest extends JTTTest {
    private static final String NAME = "LargeConstantSection";
    private static final long LARGE_CONSTANT = 0xF0F0F0F0F0L;
    private static LargeConstantClassLoader LOADER;
    @Parameter(value = 0) public int numberBlocks;

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        List<Object[]> parameters = new ArrayList<>();
        for (int i = 4; i < 13; i += 2) {
            parameters.add(new Object[]{1 << i});
        }
        return parameters;
    }

    @Before
    public void before() {
        LOADER = new LargeConstantClassLoader(LargeConstantSectionTest.class.getClassLoader());
    }

    public class LargeConstantClassLoader extends ExportingClassLoader {
        public LargeConstantClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.equals(NAME)) {
                byte[] bytes = ClassFile.of().build(ClassDesc.of(NAME), classBuilder -> classBuilder
                                .withMethod("run", MethodTypeDesc.of(CD_long, CD_int), ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL, methodBuilder -> methodBuilder
                                                .withCode(codeBuilder -> {
                                                    Label defaultLabel = codeBuilder.newLabel();
                                                    List<SwitchCase> cases = new ArrayList<>(numberBlocks);

                                                    for (int i = 0; i < cases.size(); i++) {
                                                        cases.add(SwitchCase.of(i, codeBuilder.newLabel()));
                                                    }

                                                    codeBuilder.iload(0)
                                                                    .lookupswitch(defaultLabel, cases);

                                                    for (int i = 0; i < cases.size(); i++) {
                                                        codeBuilder.labelBinding(cases.get(i).target())
                                                                        .ldc(Long.valueOf(0xF0F0F0F0F0L + i))
                                                                        .lreturn();
                                                    }

                                                    codeBuilder.labelBinding(defaultLabel)
                                                                    .ldc(Long.valueOf(3L))
                                                                    .lreturn();
                                                })));
                return defineClass(name, bytes, 0, bytes.length);
            } else {
                return super.findClass(name);
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void run0() throws Exception {
        test("run", numberBlocks - 3);
    }

    @Override
    protected ResolvedJavaMethod getResolvedJavaMethod(String methodName) {
        try {
            for (Method method : LOADER.findClass(NAME).getDeclaredMethods()) {
                if (method.getName().equals(methodName)) {
                    return asResolvedJavaMethod(method);
                }
            }
        } catch (ClassNotFoundException e) {
            throw new AssertionFailedError("Cannot find class " + NAME);
        }
        throw GraalError.shouldNotReachHereUnexpectedValue(methodName); // ExcludeFromJacocoGeneratedReport
    }
}
