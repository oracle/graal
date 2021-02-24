/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.truffle.espresso.jvmti.structs;

import static com.oracle.truffle.espresso.ffi.NativeType.BOOLEAN;
import static com.oracle.truffle.espresso.ffi.NativeType.INT;
import static com.oracle.truffle.espresso.ffi.NativeType.LONG;
import static com.oracle.truffle.espresso.ffi.NativeType.OBJECT;
import static com.oracle.truffle.espresso.ffi.NativeType.POINTER;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;

@GenerateStructs(//
{
                /*-
                 * struct _jvmtiThreadInfo {
                 *     char* name;
                 *     jint priority;
                 *     jboolean is_daemon;
                 *     jthreadGroup thread_group;
                 *     jobject context_class_loader;
                 * };
                 */
                @GenerateStructs.KnownStruct(structName = "_jvmtiThreadInfo", //
                                memberNames = {
                                                "name",
                                                "priority",
                                                "is_daemon",
                                                "thread_group",
                                                "context_class_loader"
                                }, //
                                types = {
                                                POINTER,
                                                INT,
                                                BOOLEAN,
                                                POINTER,
                                                OBJECT
                                }),
                /*-
                 * struct _jvmtiMonitorStackDepthInfo {
                 *     jobject monitor;
                 *     jint stack_depth;
                 * };
                 */
                @GenerateStructs.KnownStruct(structName = "_jvmtiMonitorStackDepthInfo", //
                                memberNames = {
                                                "monitor",
                                                "stack_depth",
                                }, //
                                types = {
                                                OBJECT,
                                                INT,
                                }),
                /*-
                 * struct _jvmtiThreadGroupInfo {
                 *     jthreadGroup parent;
                 *     char* name;
                 *     jint max_priority;
                 *     jboolean is_daemon;
                 * };
                 *
                 */
                @GenerateStructs.KnownStruct(structName = "_jvmtiThreadGroupInfo", //
                                memberNames = {
                                                "parent",
                                                "name",
                                                "max_priority",
                                                "is_daemon",
                                }, //
                                types = {
                                                POINTER,
                                                POINTER,
                                                INT,
                                                BOOLEAN,
                                }),
                /*-
                 * struct _jvmtiFrameInfo {
                 *     jmethodID method;
                 *     jlocation location;
                 * };
                 */
                @GenerateStructs.KnownStruct(structName = "_jvmtiFrameInfo", //
                                memberNames = {
                                                "method",
                                                "location",
                                }, //
                                types = {
                                                LONG,
                                                LONG,
                                }),
                /*-
                 * struct _jvmtiStackInfo {
                 *     jthread thread;
                 *     jint state;
                 *     jvmtiFrameInfo* frame_buffer;
                 *     jint frame_count;
                 * };
                 */
                @GenerateStructs.KnownStruct(structName = "_jvmtiStackInfo", //
                                memberNames = {
                                                "thread",
                                                "state",
                                                "frame_buffer",
                                                "frame_count",
                                }, //
                                types = {
                                                OBJECT,
                                                INT,
                                                POINTER,
                                                INT,
                                }),
                /*-
                 * struct _jvmtiHeapReferenceInfoField {
                 *     jint index;
                 * };
                 */
                @GenerateStructs.KnownStruct(structName = "_jvmtiHeapReferenceInfoField", //
                                memberNames = {"index"}, //
                                types = {INT}),
                /*-
                 * struct _jvmtiHeapReferenceInfoArray {
                 *     jint index;
                 * };
                 */
                @GenerateStructs.KnownStruct(structName = "_jvmtiHeapReferenceInfoArray", //
                                memberNames = {"index"}, //
                                types = {INT}),
                /*-
                 * struct _jvmtiHeapReferenceInfoConstantPool {
                 *     jint index;
                 * };
                 */
                @GenerateStructs.KnownStruct(structName = "_jvmtiHeapReferenceInfoConstantPool", //
                                memberNames = {"index"}, //
                                types = {INT}),
                /*-
                 * struct _jvmtiHeapReferenceInfoStackLocal {
                 *     jlong thread_tag;
                 *     jlong thread_id;
                 *     jint depth;
                 *     jmethodID method;
                 *     jlocation location;
                 *     jint slot;
                 * };
                 */
                @GenerateStructs.KnownStruct(structName = "_jvmtiHeapReferenceInfoStackLocal", //
                                memberNames = {
                                                "thread_tag",
                                                "thread_id",
                                                "depth",
                                                "method",
                                                "location",
                                                "slot",
                                }, //
                                types = {
                                                LONG,
                                                LONG,
                                                INT,
                                                LONG,
                                                LONG,
                                                INT,
                                }),
                /*-
                 * struct _jvmtiHeapReferenceInfoJniLocal {
                 *     jlong thread_tag;
                 *     jlong thread_id;
                 *     jint depth;
                 *     jmethodID method;
                 * };
                 */
                @GenerateStructs.KnownStruct(structName = "_jvmtiHeapReferenceInfoJniLocal", //
                                memberNames = {
                                                "thread_tag",
                                                "thread_id",
                                                "depth",
                                                "method",
                                }, //
                                types = {
                                                LONG,
                                                LONG,
                                                INT,
                                                LONG,
                                }),
                /*-
                 * struct _jvmtiHeapReferenceInfoReserved {
                 *     jlong reserved1;
                 *     jlong reserved2;
                 *     jlong reserved3;
                 *     jlong reserved4;
                 *     jlong reserved5;
                 *     jlong reserved6;
                 *     jlong reserved7;
                 *     jlong reserved8;
                 * };
                 */
                @GenerateStructs.KnownStruct(structName = "_jvmtiHeapReferenceInfoReserved", //
                                memberNames = {
                                                "reserved1",
                                                "reserved2",
                                                "reserved3",
                                                "reserved4",
                                                "reserved5",
                                                "reserved6",
                                                "reserved7",
                                                "reserved8",
                                }, //
                                types = {
                                                LONG,
                                                LONG,
                                                LONG,
                                                LONG,
                                                LONG,
                                                LONG,
                                                LONG,
                                                LONG,
                                }),
                /*-
                 * struct _jvmtiHeapCallbacks {
                 *     jvmtiHeapIterationCallback heap_iteration_callback;
                 *     jvmtiHeapReferenceCallback heap_reference_callback;
                 *     jvmtiPrimitiveFieldCallback primitive_field_callback;
                 *     jvmtiArrayPrimitiveValueCallback array_primitive_value_callback;
                 *     jvmtiStringPrimitiveValueCallback string_primitive_value_callback;
                 *     jvmtiReservedCallback reserved5;
                 *     jvmtiReservedCallback reserved6;
                 *     jvmtiReservedCallback reserved7;
                 *     jvmtiReservedCallback reserved8;
                 *     jvmtiReservedCallback reserved9;
                 *     jvmtiReservedCallback reserved10;
                 *     jvmtiReservedCallback reserved11;
                 *     jvmtiReservedCallback reserved12;
                 *     jvmtiReservedCallback reserved13;
                 *     jvmtiReservedCallback reserved14;
                 *     jvmtiReservedCallback reserved15;
                 * };
                 */
                @GenerateStructs.KnownStruct(structName = "_jvmtiHeapCallbacks", //
                                memberNames = {
                                                "heap_iteration_callback",
                                                "heap_reference_callback",
                                                "primitive_field_callback",
                                                "array_primitive_value_callback",
                                                "string_primitive_value_callback",
                                                "reserved5",
                                                "reserved6",
                                                "reserved7",
                                                "reserved8",
                                                "reserved9",
                                                "reserved10",
                                                "reserved11",
                                                "reserved12",
                                                "reserved13",
                                                "reserved14",
                                                "reserved15",
                                }, //
                                types = {
                                                POINTER,
                                                POINTER,
                                                POINTER,
                                                POINTER,
                                                POINTER,
                                                POINTER,
                                                POINTER,
                                                POINTER,
                                                POINTER,
                                                POINTER,
                                                POINTER,
                                                POINTER,
                                                POINTER,
                                                POINTER,
                                                POINTER,
                                                POINTER,
                                }),
                /*-
                 * struct _jvmtiClassDefinition {
                 *     jclass klass;
                 *     jint class_byte_count;
                 *     const unsigned char* class_bytes;
                 * };
                 */
                @GenerateStructs.KnownStruct(structName = "_jvmtiClassDefinition", //
                                memberNames = {
                                                "klass",
                                                "class_byte_count",
                                                "class_bytes",
                                }, //
                                types = {
                                                OBJECT,
                                                INT,
                                                POINTER,
                                }),
                /*-
                 * struct _jvmtiMonitorUsage {
                 *     jthread owner;
                 *     jint entry_count;
                 *     jint waiter_count;
                 *     jthread* waiters;
                 *     jint notify_waiter_count;
                 *     jthread* notify_waiters;
                 * };
                 */
                @GenerateStructs.KnownStruct(structName = "_jvmtiMonitorUsage", //
                                memberNames = {
                                                "owner",
                                                "entry_count",
                                                "waiter_count",
                                                "waiters",
                                                "notify_waiter_count",
                                                "notify_waiters",
                                }, //
                                types = {
                                                OBJECT,
                                                INT,
                                                INT,
                                                POINTER,
                                                INT,
                                                POINTER,
                                }),
                /*-
                 * struct _jvmtiLineNumberEntry {
                 *     jlocation start_location;
                 *     jint line_number;
                 * };
                 */
                @GenerateStructs.KnownStruct(structName = "_jvmtiLineNumberEntry", //
                                memberNames = {
                                                "start_location",
                                                "line_number",
                                }, //
                                types = {
                                                LONG,
                                                INT,
                                }),
                /*-
                 * struct _jvmtiLocalVariableEntry {
                 *     jlocation start_location;
                 *     jint length;
                 *     char* name;
                 *     char* signature;
                 *     char* generic_signature;
                 *     jint slot;
                 * };
                 */
                @GenerateStructs.KnownStruct(structName = "_jvmtiLocalVariableEntry", //
                                memberNames = {
                                                "start_location",
                                                "length",
                                                "name",
                                                "signature",
                                                "generic_signature",
                                                "slot",
                                }, //
                                types = {
                                                LONG,
                                                INT,
                                                POINTER,
                                                POINTER,
                                                POINTER,
                                                INT,
                                }),
                /*-
                 * struct _jvmtiParamInfo {
                 *     char* name;
                 *     jvmtiParamKind kind;
                 *     jvmtiParamTypes base_type;
                 *     jboolean null_ok;
                 * };
                 */
                @GenerateStructs.KnownStruct(structName = "_jvmtiParamInfo", //
                                memberNames = {
                                                "name",
                                                "kind",
                                                "base_type",
                                                "null_ok",
                                }, //
                                types = {
                                                POINTER,
                                                INT,
                                                INT,
                                                BOOLEAN,
                                }),
                /*-
                 * struct _jvmtiExtensionFunctionInfo {
                 *     jvmtiExtensionFunction func;
                 *     char* id;
                 *     char* short_description;
                 *     jint param_count;
                 *     jvmtiParamInfo* params;
                 *     jint error_count;
                 *     jvmtiError* errors;
                 * };
                 */
                @GenerateStructs.KnownStruct(structName = "_jvmtiExtensionFunctionInfo", //
                                memberNames = {
                                                "func",
                                                "id",
                                                "short_description",
                                                "param_count",
                                                "params",
                                                "error_count",
                                                "errors",
                                }, //
                                types = {
                                                POINTER,
                                                POINTER,
                                                POINTER,
                                                INT,
                                                POINTER,
                                                INT,
                                                POINTER,
                                }),
                /*-
                 * struct _jvmtiExtensionEventInfo {
                 *     jint extension_event_index;
                 *     char* id;
                 *     char* short_description;
                 *     jint param_count;
                 *     jvmtiParamInfo* params;
                 * };
                 */
                @GenerateStructs.KnownStruct(structName = "_jvmtiExtensionEventInfo", //
                                memberNames = {
                                                "extension_event_index",
                                                "id",
                                                "short_description",
                                                "param_count",
                                                "params",
                                }, //
                                types = {
                                                POINTER,
                                                POINTER,
                                                POINTER,
                                                INT,
                                                POINTER,
                                }),
                /*-
                 * struct _jvmtiTimerInfo {
                 *     jlong max_value;
                 *     jboolean may_skip_forward;
                 *     jboolean may_skip_backward;
                 *     jvmtiTimerKind kind;
                 *     jlong reserved1;
                 *     jlong reserved2;
                 * };
                 */
                @GenerateStructs.KnownStruct(structName = "_jvmtiTimerInfo", //
                                memberNames = {
                                                "max_value",
                                                "may_skip_forward",
                                                "may_skip_backward",
                                                "kind",
                                                "reserved1",
                                                "reserved2",
                                }, //
                                types = {
                                                LONG,
                                                BOOLEAN,
                                                BOOLEAN,
                                                INT,
                                                LONG,
                                                LONG,
                                }),
                /*-
                 * struct _jvmtiAddrLocationMap {
                 *     const void* start_address;
                 *     jlocation location;
                 * };
                 */
                @GenerateStructs.KnownStruct(structName = "_jvmtiAddrLocationMap", //
                                memberNames = {
                                                "start_address",
                                                "location",
                                }, //
                                types = {
                                                POINTER,
                                                LONG,
                                }),
})
public final class Structs {
    public final JvmtiThreadInfo jvmtiThreadInfo;
    public final JvmtiMonitorStackDepthInfo jvmtiMonitorStackDepthInfo;
    public final JvmtiThreadGroupInfo jvmtiThreadGroupInfo;
    public final JvmtiFrameInfo jvmtiFrameInfo;
    public final JvmtiStackInfo jvmtiStackInfo;
    public final JvmtiHeapReferenceInfoField jvmtiHeapReferenceInfoField;
    public final JvmtiHeapReferenceInfoArray jvmtiHeapReferenceInfoArray;
    public final JvmtiHeapReferenceInfoConstantPool jvmtiHeapReferenceInfoConstantPool;
    public final JvmtiHeapReferenceInfoStackLocal jvmtiHeapReferenceInfoStackLocal;
    public final JvmtiHeapReferenceInfoJniLocal jvmtiHeapReferenceInfoJniLocal;
    public final JvmtiHeapReferenceInfoReserved jvmtiHeapReferenceInfoReserved;
    public final JvmtiHeapCallbacks jvmtiHeapCallbacks;
    public final JvmtiClassDefinition jvmtiClassDefinition;
    public final JvmtiMonitorUsage jvmtiMonitorUsage;
    public final JvmtiLineNumberEntry jvmtiLineNumberEntry;
    public final JvmtiLocalVariableEntry jvmtiLocalVariableEntry;
    public final JvmtiParamInfo jvmtiParamInfo;
    public final JvmtiExtensionFunctionInfo jvmtiExtensionFunctionInfo;
    public final JvmtiExtensionEventInfo jvmtiExtensionEventInfo;
    public final JvmtiTimerInfo jvmtiTimerInfo;
    public final JvmtiAddrLocationMap jvmtiAddrLocationMap;

    public Structs(TruffleObject memberInfoPtr, TruffleObject lookupMemberOffset) {
        InteropLibrary library = InteropLibrary.getUncached();
        MemberOffsetGetter off = new MemberOffsetGetter(library, memberInfoPtr, lookupMemberOffset);
        jvmtiThreadInfo = new JvmtiThreadInfo(off);
        jvmtiMonitorStackDepthInfo = new JvmtiMonitorStackDepthInfo(off);
        jvmtiThreadGroupInfo = new JvmtiThreadGroupInfo(off);
        jvmtiFrameInfo = new JvmtiFrameInfo(off);
        jvmtiStackInfo = new JvmtiStackInfo(off);
        jvmtiHeapReferenceInfoField = new JvmtiHeapReferenceInfoField(off);
        jvmtiHeapReferenceInfoArray = new JvmtiHeapReferenceInfoArray(off);
        jvmtiHeapReferenceInfoConstantPool = new JvmtiHeapReferenceInfoConstantPool(off);
        jvmtiHeapReferenceInfoStackLocal = new JvmtiHeapReferenceInfoStackLocal(off);
        jvmtiHeapReferenceInfoJniLocal = new JvmtiHeapReferenceInfoJniLocal(off);
        jvmtiHeapReferenceInfoReserved = new JvmtiHeapReferenceInfoReserved(off);
        jvmtiHeapCallbacks = new JvmtiHeapCallbacks(off);
        jvmtiClassDefinition = new JvmtiClassDefinition(off);
        jvmtiMonitorUsage = new JvmtiMonitorUsage(off);
        jvmtiLineNumberEntry = new JvmtiLineNumberEntry(off);
        jvmtiLocalVariableEntry = new JvmtiLocalVariableEntry(off);
        jvmtiParamInfo = new JvmtiParamInfo(off);
        jvmtiExtensionFunctionInfo = new JvmtiExtensionFunctionInfo(off);
        jvmtiExtensionEventInfo = new JvmtiExtensionEventInfo(off);
        jvmtiTimerInfo = new JvmtiTimerInfo(off);
        jvmtiAddrLocationMap = new JvmtiAddrLocationMap(off);
    }
}
