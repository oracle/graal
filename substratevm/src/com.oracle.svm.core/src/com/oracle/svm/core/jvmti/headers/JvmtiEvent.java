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
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumLookup;
import org.graalvm.nativeimage.c.constant.CEnumValue;

@CEnum("jvmtiEvent")
@CContext(JvmtiDirectives.class)
public enum JvmtiEvent {
    JVMTI_EVENT_VM_INIT(true, true),
    JVMTI_EVENT_VM_DEATH(true, true),
    JVMTI_EVENT_THREAD_START(true, false),
    JVMTI_EVENT_THREAD_END(false, false),
    JVMTI_EVENT_CLASS_FILE_LOAD_HOOK(false, false),
    JVMTI_EVENT_CLASS_LOAD(false, false),
    JVMTI_EVENT_CLASS_PREPARE(false, false),
    JVMTI_EVENT_VM_START(true, true),
    JVMTI_EVENT_EXCEPTION(false, false),
    JVMTI_EVENT_EXCEPTION_CATCH(false, false),
    JVMTI_EVENT_SINGLE_STEP(false, false),
    JVMTI_EVENT_FRAME_POP(false, false),
    JVMTI_EVENT_BREAKPOINT(false, false),
    JVMTI_EVENT_FIELD_ACCESS(false, false),
    JVMTI_EVENT_FIELD_MODIFICATION(false, false),
    JVMTI_EVENT_METHOD_ENTRY(false, false),
    JVMTI_EVENT_METHOD_EXIT(false, false),
    JVMTI_EVENT_NATIVE_METHOD_BIND(false, false),
    JVMTI_EVENT_COMPILED_METHOD_LOAD(true, false),
    JVMTI_EVENT_COMPILED_METHOD_UNLOAD(true, false),
    JVMTI_EVENT_DYNAMIC_CODE_GENERATED(true, false),
    JVMTI_EVENT_DATA_DUMP_REQUEST(true, false),
    JVMTI_EVENT_MONITOR_WAIT(false, false),
    JVMTI_EVENT_MONITOR_WAITED(false, false),
    JVMTI_EVENT_MONITOR_CONTENDED_ENTER(false, false),
    JVMTI_EVENT_MONITOR_CONTENDED_ENTERED(false, false),
    JVMTI_EVENT_RESOURCE_EXHAUSTED(false, false),
    JVMTI_EVENT_GARBAGE_COLLECTION_START(false, false),
    JVMTI_EVENT_GARBAGE_COLLECTION_FINISH(false, false),
    JVMTI_EVENT_OBJECT_FREE(false, false),
    JVMTI_EVENT_VM_OBJECT_ALLOC(false, false),
    JVMTI_EVENT_SAMPLED_OBJECT_ALLOC(false, false),
    JVMTI_EVENT_VIRTUAL_THREAD_START(false, false),
    JVMTI_EVENT_VIRTUAL_THREAD_END(false, false);

    private final boolean isSupported;
    private final boolean isGlobal;

    JvmtiEvent(boolean isGlobal, boolean isSupported) {
        this.isGlobal = isGlobal;
        this.isSupported = isSupported;
    }

    public boolean isSupported() {
        return isSupported;
    }

    public boolean isGlobal() {
        return isGlobal;
    }

    @CEnumValue
    public native int getCValue();

    @CEnumLookup
    public static native JvmtiEvent fromValue(int value);
}
