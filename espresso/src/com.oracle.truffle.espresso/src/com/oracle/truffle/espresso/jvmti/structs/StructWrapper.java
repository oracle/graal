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

import java.nio.ByteBuffer;

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.espresso.ffi.RawPointer;
import com.oracle.truffle.espresso.ffi.nfi.NativeUtils;
import com.oracle.truffle.espresso.jni.JNIHandles;
import com.oracle.truffle.espresso.jni.JniEnv;
import com.oracle.truffle.espresso.runtime.StaticObject;

@GenerateStructs(//
{
                /*-
                 * struct member_info {
                 *     char* id;
                 *     size_t offset;
                 *     struct member_info *next;
                 * };
                 */
                @GenerateStructs.KnownStruct(structName = "member_info", //
                                memberNames = {
                                                "id",
                                                "offset",
                                                "next",
                                }, //
                                types = {
                                                POINTER,
                                                LONG,
                                                POINTER,
                                }),
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
public abstract class StructWrapper {
    private final JNIHandles handles;

    private final TruffleObject pointer;
    private final ByteBuffer buffer;

    public TruffleObject pointer() {
        return pointer;
    }

    protected StructWrapper(JniEnv jni, TruffleObject pointer, long capacity) {
        this.handles = jni.getHandles();

        this.pointer = pointer;
        this.buffer = NativeUtils.directByteBuffer(pointer, capacity);
    }

    protected boolean getBoolean(int offset) {
        return buffer.get(offset) != 0;
    }

    protected void putBoolean(int offset, boolean value) {
        buffer.put(offset, (byte) (value ? 1 : 0));
    }

    protected byte getByte(int offset) {
        return buffer.get(offset);
    }

    protected void putByte(int offset, byte value) {
        buffer.put(offset, value);
    }

    protected char getChar(int offset) {
        return buffer.getChar(offset);
    }

    protected void putChar(int offset, char value) {
        buffer.putChar(offset, value);
    }

    protected short getShort(int offset) {
        return buffer.getShort(offset);
    }

    protected void putShort(int offset, short value) {
        buffer.putShort(offset, value);
    }

    protected int getInt(int offset) {
        return buffer.getInt(offset);
    }

    protected void putInt(int offset, int value) {
        buffer.putInt(offset, value);
    }

    protected float getFloat(int offset) {
        return buffer.getFloat(offset);
    }

    protected void putFloat(int offset, float value) {
        buffer.putFloat(offset, value);
    }

    protected long getLong(int offset) {
        return buffer.getLong(offset);
    }

    protected void putLong(int offset, long value) {
        buffer.putLong(offset, value);
    }

    protected double getDouble(int offset) {
        return buffer.getDouble(offset);
    }

    protected void putDouble(int offset, double value) {
        buffer.putDouble(offset, value);
    }

    protected TruffleObject getPointer(int offset) {
        return RawPointer.create(buffer.getLong(offset));
    }

    protected void putPointer(int offset, TruffleObject value) {
        buffer.putLong(offset, NativeUtils.interopAsPointer(value));
    }

    protected StaticObject getObject(int offset) {
        return handles.get(Math.toIntExact(buffer.getLong(offset)));
    }

    protected void putObject(int offset, StaticObject value) {
        buffer.putLong(offset, handles.createLocal(value));
    }
}
