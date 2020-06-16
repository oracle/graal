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
package com.oracle.svm.jvmtiagentbase.jvmti;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumValue;

@CEnum("jvmtiEvent")
@CContext(JvmtiDirectives.class)
public enum JvmtiEvent {
    JVMTI_EVENT_VM_START,
    JVMTI_EVENT_VM_INIT,
    JVMTI_EVENT_VM_DEATH,
    JVMTI_EVENT_BREAKPOINT,
    JVMTI_EVENT_THREAD_END,
    JVMTI_EVENT_NATIVE_METHOD_BIND,
    JVMTI_EVENT_CLASS_PREPARE,
    JVMTI_EVENT_CLASS_FILE_LOAD_HOOK;

    @CEnumValue
    public native int getCValue();
}
