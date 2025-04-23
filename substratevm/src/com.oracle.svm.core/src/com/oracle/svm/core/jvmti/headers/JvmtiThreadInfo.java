/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jvmti.headers;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.jni.headers.JNIObjectHandle;

@CContext(JvmtiDirectives.class)
@CStruct("jvmtiThreadInfo")
public interface JvmtiThreadInfo extends PointerBase {
    @CField("name")
    CCharPointer getName();

    @CField("name")
    void setName(CCharPointer name);

    @CField("priority")
    int getPriority();

    @CField("priority")
    void setPriority(int priority);

    @CField("is_daemon")
    boolean getIsDaemon();

    @CField("is_daemon")
    void setIsDaemon(boolean isDaemon);

    @CField("thread_group")
    JThreadGroup getThreadGroup();

    @CField("thread_group")
    void setThreadGroup(JThreadGroup jThreadGroup);

    @CField("context_class_loader")
    JNIObjectHandle getContextClassLoader();

    @CField("context_class_loader")
    void setContextClassLoader(JNIObjectHandle contextClassLoader);

}
