/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.agent;

import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;

import java.lang.instrument.Instrumentation;
import java.util.function.Consumer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.AgentSupport;
import com.oracle.svm.util.TransformerInterface;

/*
 * Note: no java.lang.invoke.LambdaMetafactory (e.g., Java lambdas) in this file.
 */
@SuppressWarnings({"Anonymous2MethodRef", "Convert2Lambda"})
public class NativeImageBytecodeInstrumentationAgent {
    private static boolean metafactoryReplacementHappened;

    @SuppressWarnings({"unused", "Convert2Lambda"})
    public static void premain(String agentArgs, Instrumentation inst) {
        /* In 11+ we modify the JDK */
        if (getJavaVersion() < 11) {
            inst.addTransformer(AgentSupport.createClassInstrumentationTransformer(new TransformerInterface() {
                @Override
                public byte[] apply(String moduleName, ClassLoader loader, String className, byte[] classfileBuffer) {
                    return applyLambdaMetaFactoryTransformation(className, classfileBuffer);
                }
            }), false);

            /*
             * Now we initialize the InnerClassLambdaMetafactory so rest of the code can use
             * lambdas.
             */
            try {
                Class.forName("java.lang.invoke.LambdaMetafactory");
                Class.forName("java.lang.invoke.InnerClassLambdaMetafactory");
            } catch (ClassNotFoundException e) {
                VMError.shouldNotReachHere();
            }
            assert metafactoryReplacementHappened;
        }
        if ("traceInitialization".equals(agentArgs)) {
            inst.addTransformer(AgentSupport.createClassInstrumentationTransformer(new TransformerInterface() {
                @Override
                public byte[] apply(String moduleName, ClassLoader loader, String className, byte[] classfileBuffer) {
                    return applyInitializationTrackingTransformation(moduleName, loader, className, classfileBuffer);
                }
            }), false);
        }
    }

    private static byte[] applyInitializationTrackingTransformation(String moduleName, ClassLoader loader, String className, byte[] classfileBuffer) {
        ClassReader reader = new ClassReader(classfileBuffer);
        ClassWriter writer = new ClassWriter(reader, COMPUTE_FRAMES);
        ClassInitializationTrackingVisitor visitor = new ClassInitializationTrackingVisitor(moduleName, loader, className, writer);
        reader.accept(visitor, 0);
        return writer.toByteArray();
    }

    private static byte[] applyLambdaMetaFactoryTransformation(String className, byte[] classfileBuffer) {
        if (className != null && className.equals("java/lang/invoke/InnerClassLambdaMetafactory")) {
            metafactoryReplacementHappened = true;
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassWriter writer = new ClassWriter(reader, COMPUTE_FRAMES);
            InnerClassLambdaMetaFactoryRewriter visitor = new InnerClassLambdaMetaFactoryRewriter(writer, new Consumer<Boolean>() {
                @Override
                public void accept(Boolean b) {
                    if (!b) {
                        throw VMError.shouldNotReachHere("InnerClassLambdaMetafactory has not been transformed properly. Check if the code changed in the current JDK version.");
                    }
                }
            });
            reader.accept(visitor, 0);
            return writer.toByteArray();
        }

        return null;
    }

    static int getJavaVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            version = version.substring(2, 3);
        } else {
            int dot = version.indexOf(".");
            if (dot != -1) {
                version = version.substring(0, dot);
            }
        }
        return Integer.parseInt(version);
    }
}
