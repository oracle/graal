/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Alibaba Group Holding Limited. All Rights Reserved.
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
package com.oracle.svm.agent;

import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import static com.oracle.svm.jvmtiagentbase.Support.getIntArgument;
import static com.oracle.svm.jvmtiagentbase.Support.getObjectArgument;

import com.oracle.svm.jni.nativeapi.JNIObjectHandle;

public class DynamicDefineClassSupport extends DynamicClassGenerationSupport {

    protected DynamicDefineClassSupport(JNIEnvironment jni, JNIObjectHandle callerClass, String generatedClassName, TraceWriter traceWriter, NativeImageAgent agent) {
        super(jni, callerClass, generatedClassName, traceWriter, agent);
    }

    /**
     * When java.lang.ClassLoader.defineClass's name argument is null, the name is extract after
     * class is defined. We use class' byte array hashcode to trace class name.
     */
    @Override
    public boolean traceReflects() {
        if (values != null) {
            traceWriter.traceCall("reflect", "getDeclaredMethods", generatedClassName);
            return true;
        } else {
            System.err.println("Class contents are null, cannot be traced. Method dumpDefinedClass() should be called beforehand to get class contents as byte array");
            return false;
        }
    }

    /**
     * Get value of argument "b" from java.lang.ClassLoader. defineClass(String name, byte[] b, int
     * off, int len, ProtectionDomain protectionDomain) "b" is the 3rd argument. because the 1st
     * argument of instance method is always "this"
     */
    @Override
    protected JNIObjectHandle getClassDefinitionAsBytes() {
        return getObjectArgument(1, 2);
    }

    /**
     * Get value of argument "len" from java.lang.ClassLoader. defineClass(String name, byte[] b,
     * int off, int len, ProtectionDomain protectionDomain) "len" is the 5th argument. because the
     * 1st argument of instance method is always "this"
     */
    @Override
    protected int getClassDefinitionBytesLength() {
        return getIntArgument(1, 4);
    }
}
