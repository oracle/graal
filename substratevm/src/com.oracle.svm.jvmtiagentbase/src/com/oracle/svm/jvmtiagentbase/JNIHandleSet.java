/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jvmtiagentbase;

import static com.oracle.svm.core.util.VMError.guarantee;
import static com.oracle.svm.jni.JNIObjectHandles.nullHandle;

import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.nativeimage.c.type.CTypeConversion;

import com.oracle.svm.jni.JNIObjectHandles;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIMethodId;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;

/**
 * Helps with creation and management of JNI handles for JVMTI agents.
 *
 * A JVMTI agent must provide either this class or a subclass of this class. It should contain
 * handles to classes and methods that are needed across different JVMTI calls (for example, a
 * JNIObjectHandle to java/lang/Class).
 *
 * For JNI handles that are created in a JVMTI callback and that should survive a return from native
 * code, a helper method {@link #newTrackedGlobalRef} is provided.
 *
 * @see JvmtiAgentBase
 */
public abstract class JNIHandleSet {
    private static final int INITIAL_GLOBAL_HANDLE_CAPACITY = 16;
    private final ReentrantLock globalRefsLock = new ReentrantLock();
    private JNIObjectHandle[] globalRefs = new JNIObjectHandle[INITIAL_GLOBAL_HANDLE_CAPACITY];
    private int globalRefCount = 0;

    private boolean destroyed = false;

    final JNIMethodId javaLangClassGetName;

    public JNIHandleSet(JNIEnvironment env) {
        JNIObjectHandle javaLangClass = findClass(env, "java/lang/Class");
        try (CTypeConversion.CCharPointerHolder name = Support.toCString("getName"); CTypeConversion.CCharPointerHolder signature = Support.toCString("()Ljava/lang/String;")) {
            javaLangClassGetName = Support.jniFunctions().getGetMethodID().invoke(env, javaLangClass, name.get(), signature.get());
            guarantee(javaLangClassGetName.isNonNull());
        }
    }

    /**
     * Returns a local handle to a Java class object.
     *
     * The class must exist. If not found, the VM terminates.
     *
     * @param env JNI environment of the thread running the JVMTI callback.
     * @param className The VM type signature of the class.
     * @return Local JNI handle representing the class object.
     */
    public JNIObjectHandle findClass(JNIEnvironment env, String className) {
        assert !destroyed;
        try (CTypeConversion.CCharPointerHolder name = Support.toCString(className)) {
            JNIObjectHandle h = Support.jniFunctions().getFindClass().invoke(env, name.get());
            guarantee(h.notEqual(nullHandle()));
            return h;
        }
    }

    /**
     * A convenience method to return a global handle to a Java class object. The handle will be
     * tracked and freed with a call to {@link #destroy}. The reference must not be freed manually.
     *
     * The class must exist. If not found, the VM terminates.
     *
     * @param env JNI environment of the thread running the JVMTI callback.
     * @param className Java VM type signature of the class.
     * @return Global JNI handle representing the class object.
     *
     * @see #newTrackedGlobalRef
     */
    public JNIObjectHandle newClassGlobalRef(JNIEnvironment env, String className) {
        assert !destroyed;
        return newTrackedGlobalRef(env, findClass(env, className));
    }

    /**
     * Returns a JNI method ID of a Java method.
     *
     * The method must exist. If not found, the VM terminates.
     *
     * @param env JNI environment of the thread running the JVMTI callback.
     * @param clazz Handle to the class containing the method.
     * @param name Name of the method.
     * @param signature Signature of the method. See the JNI specification for more details.
     * @param isStatic Specifies whether the method is static or not.
     * @return JNI method ID of the Java method.
     */
    public JNIMethodId getMethodId(JNIEnvironment env, JNIObjectHandle clazz, String name, String signature, boolean isStatic) {
        assert !destroyed;
        JNIMethodId id = getMethodIdOptional(env, clazz, name, signature, isStatic);
        guarantee(id.isNonNull());
        return id;
    }

    /**
     * Returns a JNI method ID of a Java method. If the method is not found, returns a
     * {@link JNIObjectHandles#nullHandle()}.
     *
     * @param env JNI environment of the thread running the JVMTI callback.
     * @param clazz Handle to the class containing the method.
     * @param name Name of the method.
     * @param signature Signature of the method. See the JNI specification for more details.
     * @param isStatic Specifies whether the method is static or not.
     * @return JNI method ID of the Java method if found, {@link JNIObjectHandles#nullHandle}
     *         otherwise.
     */
    public JNIMethodId getMethodIdOptional(JNIEnvironment env, JNIObjectHandle clazz, String name, String signature, boolean isStatic) {
        assert !destroyed;
        try (CTypeConversion.CCharPointerHolder cname = Support.toCString(name); CTypeConversion.CCharPointerHolder csignature = Support.toCString(signature)) {
            if (isStatic) {
                return Support.jniFunctions().getGetStaticMethodID().invoke(env, clazz, cname.get(), csignature.get());
            } else {
                return Support.jniFunctions().getGetMethodID().invoke(env, clazz, cname.get(), csignature.get());
            }
        }
    }

    /**
     * Creates a global JNI handle of the specified local JNI handle. The handle will be tracked and
     * freed with a call to {@link #destroy}. The handle must not be freed manually.
     *
     * If the specified handle is a {@link JNIObjectHandles#nullHandle} the VM terminates.
     *
     * @param env JNI environment of the thread running the JVMTI callback.
     * @param ref A local JNI object handle.
     * @return A global JNI object handle.
     */
    public JNIObjectHandle newTrackedGlobalRef(JNIEnvironment env, JNIObjectHandle ref) {
        assert !destroyed;
        JNIObjectHandle global = Support.jniFunctions().getNewGlobalRef().invoke(env, ref);
        guarantee(global.notEqual(nullHandle()));
        globalRefsLock.lock();
        try {
            if (globalRefCount == globalRefs.length) {
                globalRefs = Arrays.copyOf(globalRefs, globalRefs.length * 2);
            }
            globalRefs[globalRefCount] = global;
            globalRefCount++;
        } finally {
            globalRefsLock.unlock();
        }
        return global;
    }

    /**
     * Releases all global JNI handles. This class and the handles must not be used after a call to
     * this method. This function is automatically called by {@link JvmtiAgentBase} after the agent
     * unloads. It should not be called manually.
     *
     * @param env JNI environment of the thread running the JVMTI callback.
     */
    void destroy(JNIEnvironment env) {
        assert !destroyed;
        destroyed = true;
        for (int i = 0; i < globalRefCount; i++) {
            Support.jniFunctions().getDeleteGlobalRef().invoke(env, globalRefs[i]);
        }
    }
}
