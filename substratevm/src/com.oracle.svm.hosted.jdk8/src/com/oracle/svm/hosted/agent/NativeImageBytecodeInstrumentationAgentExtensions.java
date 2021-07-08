/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.internal.org.objectweb.asm.ClassWriter.COMPUTE_MAXS;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;

import com.oracle.svm.hosted.agent.jdk8.lambda.LambdaMetaFactoryRewriteVisitor;
import com.oracle.svm.util.AgentSupport;
import com.oracle.svm.util.TransformerInterface;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassWriter;

/**
 * The set of {@link NativeImageBytecodeInstrumentationAgent} extensions for JDK 8.
 */
public class NativeImageBytecodeInstrumentationAgentExtensions {

    @SuppressWarnings("unused")
    public static void premain(String agentArgs, Instrumentation inst) {
        TransformerInterface transformation = NativeImageBytecodeInstrumentationAgentExtensions::applyRewriteLambdasTransformation;
        ClassFileTransformer transformer = AgentSupport.createClassInstrumentationTransformer(transformation);
        inst.addTransformer(transformer);
    }

    @SuppressWarnings("unused")
    private static byte[] applyRewriteLambdasTransformation(String moduleName, ClassLoader loader, String className, byte[] classfileBuffer) {
        ClassReader reader = new ClassReader(classfileBuffer);
        ClassWriter writer = new ClassWriter(reader, COMPUTE_MAXS);
        LambdaMetaFactoryRewriteVisitor visitor = new LambdaMetaFactoryRewriteVisitor(loader, className, writer);
        reader.accept(visitor, EXPAND_FRAMES);
        return writer.toByteArray();
    }
}
