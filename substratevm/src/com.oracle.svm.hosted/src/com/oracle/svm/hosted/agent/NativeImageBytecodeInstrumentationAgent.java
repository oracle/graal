/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.internal.org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;

import java.lang.instrument.Instrumentation;
import java.util.ServiceLoader;

import com.oracle.svm.util.AgentSupport;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassWriter;

/*
 * Note: no java.lang.invoke.LambdaMetafactory (e.g., Java lambdas) in this file.
 */
public class NativeImageBytecodeInstrumentationAgent {

    private static TracingAdvisor advisor;

    public static void premain(String agentArgs, Instrumentation inst) {
        if (agentArgs != null && !agentArgs.isEmpty()) {
            advisor = new TracingAdvisor(agentArgs);
            inst.addTransformer(AgentSupport.createClassInstrumentationTransformer(NativeImageBytecodeInstrumentationAgent::applyInitializationTrackingTransformation));
        }
        ServiceLoader<NativeImageBytecodeInstrumentationAgentExtension> extensionLoader = ServiceLoader.load(NativeImageBytecodeInstrumentationAgentExtension.class);
        for (NativeImageBytecodeInstrumentationAgentExtension extension : extensionLoader) {
            extension.addClassFileTransformers(inst);
        }
    }

    private static byte[] applyInitializationTrackingTransformation(@SuppressWarnings("unused") String moduleName, @SuppressWarnings("unused") ClassLoader loader, String className,
                    byte[] classfileBuffer) {
        if (advisor.shouldTraceClassInitialization(className.replace('/', '.'))) {
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassWriter writer = new ClassWriter(reader, COMPUTE_FRAMES);
            ClinitGenerationVisitor visitor = new ClinitGenerationVisitor(writer);
            reader.accept(visitor, 0);
            return writer.toByteArray();
        } else {
            return classfileBuffer;
        }
    }
}
