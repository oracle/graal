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
import org.graalvm.nativeimage.ImageInfo;
import org.junit.Assert;

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
        if (!ImageInfo.inImageRuntimeCode()) {
            DemoTransformer dt = new DemoTransformer("com.oracle.svm.test.javaagent.TestJavaAgent1");
            inst.addTransformer(dt, true);
        } else {
            AgentPremainHelper.load(TestJavaAgent1.class);
            /**
             * Test {@code inst} is {@link NativeImageNoOpRuntimeInstrumentation} and behaves as
             * defined.
             */
            Assert.assertNotNull(inst);
            Assert.assertEquals(false, inst.isRetransformClassesSupported());
            Assert.assertEquals(false, inst.removeTransformer(null));
            Assert.assertEquals(false, inst.isRedefineClassesSupported());

            Assert.assertEquals(false, inst.isModifiableClass(null));

            Class<?>[] allClasses = inst.getAllLoadedClasses();
            Assert.assertTrue(allClasses.length > 0);
            Class<?> currentAgentClassFromAllLoaded = null;
            for (Class<?> c : allClasses) {
                if (c.equals(TestJavaAgent1.class)) {
                    currentAgentClassFromAllLoaded = c;
                }
            }
            Assert.assertNotNull(currentAgentClassFromAllLoaded);

            // redefineClasses should throw UnsupportedOperationException
            Exception exception = null;
            try {
                inst.redefineClasses();
            } catch (Exception e) {
                exception = e;
            }
            Assert.assertNotNull(exception);
            Assert.assertEquals(UnsupportedOperationException.class, exception.getClass());

            // getInitiatedClasses should throw UnsupportedOperationException
            exception = null;
            try {
                inst.getInitiatedClasses(null);
            } catch (Exception e) {
                exception = e;
            }
            Assert.assertNotNull(exception);
            Assert.assertEquals(UnsupportedOperationException.class, exception.getClass());

            // retransformClasses should throw UnsupportedOperationException
            exception = null;
            try {
                inst.retransformClasses();
            } catch (Exception e) {
                exception = e;
            }
            Assert.assertNotNull(exception);
            Assert.assertEquals(UnsupportedOperationException.class, exception.getClass());

            // appendToBootstrapClassLoaderSearch should throw UnsupportedOperationException
            exception = null;
            try {
                inst.appendToBootstrapClassLoaderSearch(null);
            } catch (Exception e) {
                exception = e;
            }
            Assert.assertNotNull(exception);
            Assert.assertEquals(UnsupportedOperationException.class, exception.getClass());

            // appendToSystemClassLoaderSearch should throw UnsupportedOperationException
            exception = null;
            try {
                inst.appendToSystemClassLoaderSearch(null);
            } catch (Exception e) {
                exception = e;
            }
            Assert.assertNotNull(exception);
            Assert.assertEquals(UnsupportedOperationException.class, exception.getClass());

            Assert.assertEquals(-1, inst.getObjectSize(null));
            Assert.assertEquals(false, inst.isNativeMethodPrefixSupported());

            Module currentModule = TestJavaAgent1.class.getModule();
            Assert.assertEquals(true, inst.isModifiableModule(currentModule));

            // redefineModule only does checks, no actual actions.
            inst.redefineModule(currentModule, Set.of(Class.class.getModule()), Collections.emptyMap(), null, null, null);
        }
    }

    static class DemoTransformer implements ClassFileTransformer {

        private String internalClassName;

        DemoTransformer(String name) {
            internalClassName = name.replaceAll("\\.", "/");
        }

        @Override
        public byte[] transform(
                        ClassLoader loader,
                        String className,
                        Class<?> classBeingRedefined,
                        ProtectionDomain protectionDomain,
                        byte[] classfileBuffer) {
            byte[] byteCode = classfileBuffer;

            if (internalClassName.equals(className)) {
                System.out.println("Let's do transformation for " + className);
                // Do class transformation here
            }
            return byteCode;
        }
    }
}
