/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, 2025, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.svm.test.javaagent.agent3;

import org.graalvm.nativeimage.ImageInfo;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.constant.MethodTypeDesc;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/**
 * This agent inserts new method into a lambda class.
 */
public class TestJavaAgent3 {
    public static void premain(
            String agentArgs, Instrumentation inst) {
        if (!ImageInfo.inImageRuntimeCode()) {
            InjectLambdaTransformer transformer = new InjectLambdaTransformer();
            inst.addTransformer(transformer, true);
        }
    }

    static class InjectLambdaTransformer implements ClassFileTransformer {
        @Override
        public byte[] transform(
                ClassLoader loader,
                String className,
                Class<?> classBeingRedefined,
                ProtectionDomain protectionDomain,
                byte[] classfileBuffer) {
            if (className.contains("$Lambda$") && classBeingRedefined != null && Runnable.class.isAssignableFrom(classBeingRedefined)) {
                ClassFile classFile = ClassFile.of();
                ClassModel classModel = classFile.parse(classfileBuffer);
                return classFile.transformClass(classModel, (cb, ce) -> {
                    cb.with(ce).withMethod("newAdd", MethodTypeDesc.ofDescriptor("()I"), 1, (methodBuilder) -> {
                        methodBuilder.withCode(codeBuilder -> {
                            codeBuilder.iload(1).ireturn();
                        });
                    });
                });
            }
            return null;
        }
    }
}
