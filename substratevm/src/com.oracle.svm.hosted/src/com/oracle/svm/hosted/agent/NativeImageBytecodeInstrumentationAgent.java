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

import static jdk.internal.org.objectweb.asm.ClassReader.EXPAND_FRAMES;
import static jdk.internal.org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static jdk.internal.org.objectweb.asm.ClassWriter.COMPUTE_MAXS;

import java.lang.instrument.Instrumentation;

import com.oracle.svm.hosted.agent.jdk8.lambda.LambdaMetaFactoryRewriteVisitor;
import com.oracle.svm.util.AgentSupport;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassWriter;

/*
 * Note: no java.lang.invoke.LambdaMetafactory (e.g., Java lambdas) in this file.
 */
@SuppressWarnings({"Anonymous2MethodRef", "Convert2Lambda"})
public class NativeImageBytecodeInstrumentationAgent {

    @SuppressWarnings({"unused", "Convert2Lambda"})
    public static void premain(String agentArgs, Instrumentation inst) {
        /* In 11+ we modify the JDK */
        if (getJavaVersion() == 8) {
            inst.addTransformer(AgentSupport.createClassInstrumentationTransformer(NativeImageBytecodeInstrumentationAgent::applyRewriteLambdasTransformation));
        }
        if ("traceInitialization".equals(agentArgs)) {
            inst.addTransformer(AgentSupport.createClassInstrumentationTransformer(NativeImageBytecodeInstrumentationAgent::applyInitializationTrackingTransformation));
        }
    }

    private static byte[] applyInitializationTrackingTransformation(String moduleName, ClassLoader loader, String className, byte[] classfileBuffer) {
        ClassReader reader = new ClassReader(classfileBuffer);
        ClassWriter writer = new ClassWriter(reader, COMPUTE_FRAMES);
        ClassInitializationTrackingVisitor visitor = new ClassInitializationTrackingVisitor(moduleName, loader, className, writer);
        reader.accept(visitor, 0);
        return writer.toByteArray();
    }

    @SuppressWarnings("unused")
    private static byte[] applyRewriteLambdasTransformation(String moduleName, ClassLoader loader, String className, byte[] classfileBuffer) {
        ClassReader reader = new ClassReader(classfileBuffer);
        ClassWriter writer = new ClassWriter(reader, COMPUTE_MAXS);
        LambdaMetaFactoryRewriteVisitor visitor = new LambdaMetaFactoryRewriteVisitor(loader, className, writer);
        reader.accept(visitor, EXPAND_FRAMES);
        return writer.toByteArray();
    }

    public static int getJavaVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            version = version.substring(2, 3);
        } else {
            int dot = version.indexOf(".");
            if (dot != -1) {
                version = version.substring(0, dot);
            }
            int dash = version.indexOf("-");
            if (dash != -1) {
                version = version.substring(0, dash);
            }
        }
        return Integer.parseInt(version);
    }
}
