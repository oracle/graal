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

import static com.oracle.svm.jvmtiagentbase.Support.jniFunctions;
import static com.oracle.svm.jvmtiagentbase.Support.jvmtiFunctions;
import static com.oracle.svm.jvmtiagentbase.Support.jvmtiEnv;
import static com.oracle.svm.jvmtiagentbase.Support.getClassNameOrNull;
import static com.oracle.svm.jvmtiagentbase.Support.getMethodDeclaringClass;
import static com.oracle.svm.jvmtiagentbase.Support.getMethodName;
import static com.oracle.svm.jni.JNIObjectHandles.nullHandle;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiError;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiFrameInfo;
import com.oracle.svm.jni.nativeapi.JNIMethodId;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;

public abstract class DynamicClassGenerationSupport {

    protected JNIEnvironment jni;
    protected JNIObjectHandle callerClass;
    protected final String generatedClassName;
    protected TraceWriter traceWriter;
    protected NativeImageAgent agent;

    private static String dynclassDumpDir = null;
    private static final String DEFAULT_DUMP = "dynClass";

    static {
        // Delete the dumping directory if already exists
        if (dynclassDumpDir == null) {
            System.out.println("Warning: dynmaic-class-dump-dir= was not set in -agentlib:native-image-agent=, using default location dynClass");
            dynclassDumpDir = DEFAULT_DUMP;
        }
        Path dumpDir = new File(dynclassDumpDir).toPath();
        try {
            if (!Files.exists(dumpDir)) {
                Files.createDirectory(dumpDir);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected byte[] values = null;

    public static void setDynClassDumpDir(String dir) {
        dynclassDumpDir = dir;
    }

    public static DynamicClassGenerationSupport getSerializeSupport(JNIEnvironment jni, JNIObjectHandle callerClass,
                    JNIObjectHandle serializationTargetClass, String generatedClassName, JNIObjectHandle targetParentClass,
                    TraceWriter traceWriter, NativeImageAgent agent) {
        return new SerializationSupport(jni, callerClass, serializationTargetClass, generatedClassName, targetParentClass, traceWriter, agent);
    }

    public static DynamicClassGenerationSupport getDynamicClassGenerationSupport(JNIEnvironment jni, JNIObjectHandle callerClass,
                    String generatedClassName, TraceWriter traceWriter, NativeImageAgent agent) {
        return new DynamicDefineClassSupport(jni, callerClass, generatedClassName, traceWriter, agent);
    }

    protected DynamicClassGenerationSupport(JNIEnvironment jni, JNIObjectHandle callerClass,
                    String generatedClassName, TraceWriter traceWriter, NativeImageAgent agent) {
        this.jni = jni;
        this.callerClass = callerClass;
        // Make sure use qualified name for generatedClassName
        if (generatedClassName.indexOf('/') != -1) {
            this.generatedClassName = generatedClassName.replace('/', '.');
        } else {
            this.generatedClassName = generatedClassName;
        }
        this.traceWriter = traceWriter;
        this.agent = agent;
    }

    public abstract boolean traceReflects();

    protected abstract JNIObjectHandle getClassDefinitionAsBytes();

    protected abstract int getClassDefinitionBytesLength();

    /**
     * Save dynamically defined class to file system.
     *
     * @return true if successfully dumped
     */
    public boolean dumpDefinedClass() {
        // bytes parameter of defineClass method
        JNIObjectHandle bytes = getClassDefinitionAsBytes();
        // len parameter of defineClass method
        int length = getClassDefinitionBytesLength();
        // Get generated class' byte array
        CCharPointer byteArray = jniFunctions().getGetByteArrayElements().invoke(jni, bytes, WordFactory.nullPointer());
        values = new byte[length];
        try {
            CTypeConversion.asByteBuffer(byteArray, length).get(values);
        } finally {
            jniFunctions().getReleaseByteArrayElements().invoke(jni, bytes, byteArray, 0);
        }
        // Get name for generated class
        String internalName = generatedClassName;
        if (internalName.indexOf('.') != -1) {
            internalName = internalName.replace('.', '/');
        }
        String dumpFileName = dynclassDumpDir + "/" + internalName + ".class";
        String dumpTraceFile = dynclassDumpDir + "/" + internalName + ".txt";
        // Get directory from package
        String dumpDirs = dumpFileName.substring(0, dumpFileName.lastIndexOf('/'));
        try {
            File dirs = new File(dumpDirs);
            if (!dirs.exists()) {
                dirs.mkdirs();
            }
            FileOutputStream stream = new FileOutputStream(dumpFileName);
            stream.write(values);
            stream.close();

            JvmtiFrameInfo frameInfo = StackValue.get(JvmtiFrameInfo.class);
            CIntPointer countPtr = StackValue.get(CIntPointer.class);
            StringBuilder trace = new StringBuilder();
            int i = 0;
            while (true) {
                JvmtiError result = jvmtiFunctions().GetStackTrace().invoke(jvmtiEnv(), nullHandle(), i++, 1, frameInfo, countPtr);
                if (result == JvmtiError.JVMTI_ERROR_NONE && countPtr.read() == 1) {
                    JNIMethodId m = frameInfo.getMethod();
                    trace.append(getClassNameOrNull(jni, getMethodDeclaringClass(m))).append(".").append(getMethodName(m)).append("\n");
                } else {
                    break;
                }
            }
            FileOutputStream traceStream = new FileOutputStream(dumpTraceFile);
            traceStream.write(trace.toString().getBytes());
            traceStream.close();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
