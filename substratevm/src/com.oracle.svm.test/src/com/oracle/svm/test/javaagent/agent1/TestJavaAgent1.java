/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2024, Alibaba Group Holding Limited. All rights reserved.
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
package com.oracle.svm.test.javaagent.agent1;

import com.oracle.svm.test.javaagent.AgentPremainHelper;
import com.oracle.svm.test.javaagent.AssertInAgent;
import org.graalvm.nativeimage.ImageInfo;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.Set;

public class TestJavaAgent1 {

    public static void premain(
                    String agentArgs, Instrumentation inst) {
        AgentPremainHelper.parseOptions(agentArgs);
        System.setProperty("instrument.enable", "true");
        AgentPremainHelper.load(TestJavaAgent1.class);
        if (!ImageInfo.inImageRuntimeCode()) {
            DemoTransformer dt = new DemoTransformer();
            inst.addTransformer(dt, true);
        } else {
            /**
             * Test {@code inst} is {@link NativeImageNoOpRuntimeInstrumentation} and behaves as
             * defined.
             */
            AssertInAgent.assertNotNull(inst);
            AssertInAgent.assertEquals(false, inst.isRetransformClassesSupported());
            AssertInAgent.assertEquals(false, inst.removeTransformer(null));
            AssertInAgent.assertEquals(false, inst.isRedefineClassesSupported());

            AssertInAgent.assertEquals(false, inst.isModifiableClass(null));

            Class<?>[] allClasses = inst.getAllLoadedClasses();
            AssertInAgent.assertTrue(allClasses.length > 0);
            Class<?> currentAgentClassFromAllLoaded = null;
            for (Class<?> c : allClasses) {
                if (c.equals(TestJavaAgent1.class)) {
                    currentAgentClassFromAllLoaded = c;
                }
            }
            AssertInAgent.assertNotNull(currentAgentClassFromAllLoaded);

            // redefineClasses should throw UnsupportedOperationException
            Exception exception = null;
            try {
                inst.redefineClasses();
            } catch (Exception e) {
                exception = e;
            }
            AssertInAgent.assertNotNull(exception);
            AssertInAgent.assertEquals(UnsupportedOperationException.class, exception.getClass());

            // getInitiatedClasses should throw UnsupportedOperationException
            exception = null;
            try {
                inst.getInitiatedClasses(null);
            } catch (Exception e) {
                exception = e;
            }
            AssertInAgent.assertNotNull(exception);
            AssertInAgent.assertEquals(UnsupportedOperationException.class, exception.getClass());

            // retransformClasses should throw UnsupportedOperationException
            exception = null;
            try {
                inst.retransformClasses();
            } catch (Exception e) {
                exception = e;
            }
            AssertInAgent.assertNotNull(exception);
            AssertInAgent.assertEquals(UnsupportedOperationException.class, exception.getClass());

            // appendToBootstrapClassLoaderSearch should throw UnsupportedOperationException
            exception = null;
            try {
                inst.appendToBootstrapClassLoaderSearch(null);
            } catch (Exception e) {
                exception = e;
            }
            AssertInAgent.assertNotNull(exception);
            AssertInAgent.assertEquals(UnsupportedOperationException.class, exception.getClass());

            // appendToSystemClassLoaderSearch should throw UnsupportedOperationException
            exception = null;
            try {
                inst.appendToSystemClassLoaderSearch(null);
            } catch (Exception e) {
                exception = e;
            }
            AssertInAgent.assertNotNull(exception);
            AssertInAgent.assertEquals(UnsupportedOperationException.class, exception.getClass());

            AssertInAgent.assertEquals(-1, inst.getObjectSize(null));
            AssertInAgent.assertEquals(false, inst.isNativeMethodPrefixSupported());

            Module currentModule = TestJavaAgent1.class.getModule();
            AssertInAgent.assertEquals(true, inst.isModifiableModule(currentModule));

            // redefineModule only does checks, no actual actions.
            inst.redefineModule(currentModule, Set.of(Class.class.getModule()), Collections.emptyMap(), null, null, null);
        }
    }

    /**
     * Change the return value of {@code AgentTest#getCounter()} from 10 to 11 in the agent.
     */
    static class DemoTransformer implements ClassFileTransformer {

        private String internalClassName;

        DemoTransformer() {
            internalClassName = "com/oracle/svm/test/javaagent/AgentTest";
        }

        @Override
        public byte[] transform(
                        ClassLoader loader,
                        String className,
                        Class<?> classBeingRedefined,
                        ProtectionDomain protectionDomain,
                        byte[] classfileBuffer) {
            if (internalClassName.equals(className)) {
                ClassFile classFile = ClassFile.of();
                ClassModel classModel = classFile.parse(classfileBuffer);

                return classFile.transformClass(classModel, (classbuilder, ce) -> {
                    if (ce instanceof MethodModel mm && mm.methodName().equalsString("getCounter") && mm.methodType().equalsString("()I")) {
                        classbuilder.transformMethod(mm, (mb, me) -> {
                            mb.withCode(cb -> {
                                cb.loadConstant(11);
                                cb.ireturn();
                            });
                        });
                    } else {
                        classbuilder.with(ce);
                    }
                });
            }
            return null;
        }
    }
}
