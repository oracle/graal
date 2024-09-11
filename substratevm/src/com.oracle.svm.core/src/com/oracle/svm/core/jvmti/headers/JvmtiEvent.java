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
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumLookup;
import org.graalvm.nativeimage.c.constant.CEnumValue;

import com.oracle.svm.core.collections.EnumBitmask;

@CContext(JvmtiDirectives.class)
@CEnum("jvmtiEvent")
public enum JvmtiEvent {
    JVMTI_EVENT_VM_INIT(true, JvmtiEventFlags.Global),
    JVMTI_EVENT_VM_DEATH(true, JvmtiEventFlags.Global),
    JVMTI_EVENT_THREAD_START(false, JvmtiEventFlags.Global),
    JVMTI_EVENT_THREAD_END(false),
    JVMTI_EVENT_CLASS_FILE_LOAD_HOOK(false),
    JVMTI_EVENT_CLASS_LOAD(false),
    JVMTI_EVENT_CLASS_PREPARE(false),
    JVMTI_EVENT_VM_START(true, JvmtiEventFlags.Global),
    JVMTI_EVENT_EXCEPTION(false),
    JVMTI_EVENT_EXCEPTION_CATCH(false),
    JVMTI_EVENT_SINGLE_STEP(false),
    JVMTI_EVENT_FRAME_POP(false),
    JVMTI_EVENT_BREAKPOINT(false),
    JVMTI_EVENT_FIELD_ACCESS(false),
    JVMTI_EVENT_FIELD_MODIFICATION(false),
    JVMTI_EVENT_METHOD_ENTRY(false),
    JVMTI_EVENT_METHOD_EXIT(false),
    JVMTI_EVENT_NATIVE_METHOD_BIND(false),
    JVMTI_EVENT_COMPILED_METHOD_LOAD(false, JvmtiEventFlags.Global),
    JVMTI_EVENT_COMPILED_METHOD_UNLOAD(false, JvmtiEventFlags.Global),
    JVMTI_EVENT_DYNAMIC_CODE_GENERATED(false, JvmtiEventFlags.Global),
    JVMTI_EVENT_DATA_DUMP_REQUEST(false, JvmtiEventFlags.Global),
    JVMTI_EVENT_MONITOR_WAIT(false),
    JVMTI_EVENT_MONITOR_WAITED(false),
    JVMTI_EVENT_MONITOR_CONTENDED_ENTER(false),
    JVMTI_EVENT_MONITOR_CONTENDED_ENTERED(false),
    JVMTI_EVENT_RESOURCE_EXHAUSTED(false),
    JVMTI_EVENT_GARBAGE_COLLECTION_START(false),
    JVMTI_EVENT_GARBAGE_COLLECTION_FINISH(false),
    JVMTI_EVENT_OBJECT_FREE(false),
    JVMTI_EVENT_VM_OBJECT_ALLOC(false),
    JVMTI_EVENT_SAMPLED_OBJECT_ALLOC(false),
    JVMTI_EVENT_VIRTUAL_THREAD_START(false, JvmtiEventFlags.Global),
    JVMTI_EVENT_VIRTUAL_THREAD_END(false);

    private final boolean isSupported;
    private final int flags;

    JvmtiEvent(boolean isSupported, JvmtiEventFlags... flags) {
        this.isSupported = isSupported;
        this.flags = EnumBitmask.computeBitmask(flags);

    }

    public boolean isSupported() {
        return isSupported;
    }

    public boolean isGlobal() {
        return EnumBitmask.hasBit(flags, JvmtiEventFlags.Global);
    }

    public static long getBit(JvmtiEvent eventType) {
        int index = eventType.getCValue() - JvmtiEvent.getMinEventType();
        assert index < 64;
        return 1L << index;
    }

    @CConstant("JVMTI_MIN_EVENT_TYPE_VAL")
    public static native int getMinEventType();

    @CEnumValue
    public native int getCValue();

    @CEnumLookup
    public static native JvmtiEvent fromValue(int value);

    private enum JvmtiEventFlags {
        Global
    }
}
