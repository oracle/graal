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
package com.oracle.svm.core.jvmti;

public enum JvmtiCapabilitiesEnum {
    CAN_TAG_OBJECTS,
    CAN_GENERATE_FIELD_MODIFICATION_EVENTS,
    CAN_GENERATE_FIELD_ACCESS_EVENTS,
    CAN_GET_BYTECODES,
    CAN_GET_SYNTHETIC_ATTRIBUTE,
    CAN_GET_OWNED_MONITOR_INFO,
    CAN_GET_CURRENT_CONTENDED_MONITOR,
    CAN_GET_MONITOR_INFO,
    CAN_POP_FRAME,
    CAN_REDEFINE_CLASSES,
    CAN_SIGNAL_THREAD,
    CAN_GET_SOURCE_FILE_NAME,
    CAN_GET_LINE_NUMBERS,
    CAN_GET_SOURCE_DEBUG_EXTENSION,
    CAN_ACCESS_LOCAL_VARIABLES,
    CAN_MAINTAIN_ORIGINAL_METHOD_ORDER,
    CAN_GENERATE_SINGLE_STEP_EVENTS,
    CAN_GENERATE_EXCEPTION_EVENTS,
    CAN_GENERATE_FRAME_POP_EVENTS,
    CAN_GENERATE_BREAKPOINT_EVENTS,
    CAN_SUSPEND,
    CAN_REDEFINE_ANY_CLASS,
    CAN_GET_CURRENT_THREAD_CPU_TIME,
    CAN_GET_THREAD_CPU_TIME,
    CAN_GENERATE_METHOD_ENTRY_EVENTS,
    CAN_GENERATE_METHOD_EXIT_EVENTS,
    CAN_GENERATE_ALL_CLASS_HOOK_EVENTS,
    CAN_GENERATE_COMPILED_METHOD_LOAD_EVENTS,
    CAN_GENERATE_MONITOR_EVENTS,
    CAN_GENERATE_VM_OBJECT_ALLOC_EVENTS,
    CAN_GENERATE_NATIVE_METHOD_BIND_EVENTS,
    CAN_GENERATE_GARBAGE_COLLECTION_EVENTS,
    CAN_GENERATE_OBJECT_FREE_EVENTS,
    CAN_FORCE_EARLY_RETURN,
    CAN_GET_OWNED_MONITOR_STACK_DEPTH_INFO,
    CAN_GET_CONSTANT_POOL,
    CAN_SET_NATIVE_METHOD_PREFIX,
    CAN_RETRANSFORM_CLASSES,
    CAN_RETRANSFORM_ANY_CLASS,
    CAN_GENERATE_RESOURCE_EXHAUSTION_HEAP_EVENTS,
    CAN_GENERATE_RESOURCE_EXHAUSTION_THREADS_EVENTS,
    CAN_GENERATE_EARLY_VMSTART,
    CAN_GENERATE_EARLY_CLASS_HOOK_EVENTS,
    CAN_GENERATE_SAMPLED_OBJECT_ALLOC_EVENTS,
    CAN_SUPPORT_VIRTUAL_THREADS;

    public static long getBit(JvmtiCapabilitiesEnum capability) {
        return 1L << capability.ordinal();
    }
}
