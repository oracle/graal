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
import com.oracle.svm.test.javaagent.AgentTest;
import com.oracle.svm.test.javaagent.AssertInAgent;
import org.graalvm.nativeimage.ImageInfo;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class TestJavaAgent1 {

    public static void premain(
                    String agentArgs, Instrumentation inst) throws UnmodifiableClassException {
        AgentPremainHelper.parseOptions(agentArgs);
        System.setProperty("instrument.enable", "true");
        AgentPremainHelper.load(TestJavaAgent1.class);
        if (!ImageInfo.inImageRuntimeCode()) {
            DemoTransformer dt = new DemoTransformer();
            inst.addTransformer(dt, true);
            inst.retransformClasses(dt.getTargetClasses());
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
     * Change the return value of {@code AgentTest#getCounter()} from 10 to 11 in the agent. Also
     * intercept {@code com.oracle.svm.hosted.InstrumentFeature#getRequiredFeatures()} to return an
     * empty list.
     *
     * <p>
     * Note: the interception of {@code InstrumentFeature#getRequiredFeatures()} is expected to be
     * suppressed by {@code ClassFileTransformerProxy} during a Native Image build, because
     * {@code InstrumentFeature} belongs to the {@code org.graalvm.nativeimage.builder} module which
     * is in the protected {@code SYSTEM_MODULES} set. Therefore the build-time
     * {@code getRequiredFeatures()} will still return its original value; only the shaded copy
     * (loaded under the {@code shaded.*} namespace in DEBUG mode) will reflect the transformation.
     */
    static class DemoTransformer implements ClassFileTransformer {

        private static final String AGENT_TEST_CLASS = "com/oracle/svm/test/javaagent/AgentTest";
        private static final String INSTRUMENT_FEATURE_CLASS = "com/oracle/svm/hosted/InstrumentFeature";

        private final List<Class<?>> targetClasses = new ArrayList<>();

        DemoTransformer() {
            try {
                targetClasses.add(AgentTest.class);
            } catch (NoClassDefFoundError e) {
                // AgentTest may not be available at this point
            }
        }

        public Class<?>[] getTargetClasses() {
            return targetClasses.toArray(new Class[0]);
        }

        @Override
        public byte[] transform(
                        ClassLoader loader,
                        String className,
                        Class<?> classBeingRedefined,
                        ProtectionDomain protectionDomain,
                        byte[] classfileBuffer) {
            if (AGENT_TEST_CLASS.equals(className)) {
                // Change getCounter() return value from 10 to 11
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
            } else if (INSTRUMENT_FEATURE_CLASS.equals(className)) {
                // Attempt to intercept InstrumentFeature#getRequiredFeatures() to return an empty
                // list. This transformation targets a protected GraalVM system module and will be
                // suppressed by ClassFileTransformerProxy during a Native Image build.
                return transformInstrumentFeatureGetRequiredFeatures(classfileBuffer);
            }
            return null;
        }

        /**
         * Transforms {@code InstrumentFeature#getRequiredFeatures()} so that it returns an empty
         * list ({@code Collections.emptyList()}) instead of its actual value.
         */
        private static byte[] transformInstrumentFeatureGetRequiredFeatures(byte[] classfileBuffer) {
            ClassFile classFile = ClassFile.of();
            ClassModel classModel = classFile.parse(classfileBuffer);
            ClassDesc collectionsDesc = ClassDesc.of("java.util.Collections");
            ClassDesc listDesc = ClassDesc.of("java.util.List");
            return classFile.transformClass(classModel, (cb, ce) -> {
                if (ce instanceof MethodModel mm &&
                                mm.methodName().equalsString("getRequiredFeatures") &&
                                mm.methodType().equalsString("()Ljava/util/List;")) {
                    cb.transformMethod(mm, (mb, me) -> {
                        if (me instanceof CodeModel) {
                            mb.withCode(codeBuilder -> {
                                // return Collections.emptyList();
                                codeBuilder.invokestatic(
                                                collectionsDesc, "emptyList",
                                                MethodTypeDesc.of(listDesc));
                                codeBuilder.areturn();
                            });
                        } else {
                            mb.with(me);
                        }
                    });
                } else {
                    cb.with(ce);
                }
            });
        }
    }
}
