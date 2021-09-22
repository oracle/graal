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

package com.oracle.truffle.espresso.vm.structs;

import static com.oracle.truffle.espresso.ffi.NativeType.BOOLEAN;
import static com.oracle.truffle.espresso.ffi.NativeType.INT;
import static com.oracle.truffle.espresso.ffi.NativeType.LONG;
import static com.oracle.truffle.espresso.ffi.NativeType.OBJECT;
import static com.oracle.truffle.espresso.ffi.NativeType.POINTER;

import java.nio.ByteBuffer;

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.espresso.ffi.NativeAccess;
import com.oracle.truffle.espresso.ffi.RawPointer;
import com.oracle.truffle.espresso.ffi.nfi.NativeUtils;
import com.oracle.truffle.espresso.jni.JNIHandles;
import com.oracle.truffle.espresso.jni.JniEnv;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.structs.GenerateStructs.KnownStruct;

/**
 * Commodity class that wraps around native pointers to provide an easy and concise way of accessing
 * native structs entirely from the Java world. Apart from the {@link StructWrapper#pointer()}
 * method, methods in this class are not intended to be used by users, only from generated code.
 * <p>
 * The {@link GenerateStructs} annotation below will generate wrappers with member accessors for
 * each struct declared in the annotation.
 * <p>
 * the processor will generate two classes for each of them:
 * <ul>
 * <li>A {@link StructStorage storage class} to store the size of the struct, and the offsets of
 * each struct member. It also provides a {@link StructStorage#wrap(JniEnv, TruffleObject) wrap}
 * method, that returns an instance of {@link StructWrapper this class}. These classes are intended
 * to be per-context singletons.</li>
 * <li>A {@link StructWrapper wrapper class}, as described above. This generated class will also
 * have public getters and setters for each member of the struct.</li>
 * </ul>
 * <p>
 * Furthermore, the processor will additionally generate another class that stores the singleton
 * instances for the {@link StructStorage storage class}, the {@link Structs Structs class}.
 * <p>
 * See the {@link JavaMemberOffsetGetter} class for an example of how to use the wrappers.
 */
@GenerateStructs(//
{
                /*-
                 * struct JavaVMAttachArgs {
                 *     jint version;
                 *     char *name;
                 *     jobject group;
                 * };
                 */
                @KnownStruct(structName = "JavaVMAttachArgs", //
                                memberNames = {
                                                "version",
                                                "name",
                                                "group",
                                }, //
                                types = {
                                                INT,
                                                POINTER,
                                                OBJECT,
                                }),
                /*-
                 * struct jdk_version_info {
                 *     unsigned int jdk_version; // <- The only one we are interested in.
                 *     unsigned int update_version : 8;
                 *     unsigned int special_update_version : 8;
                 *     unsigned int reserved1 : 16;
                 *     unsigned int reserved2;
                 *     unsigned int thread_park_blocker : 1;
                 *     unsigned int post_vm_init_hook_enabled : 1;
                 *     unsigned int pending_list_uses_discovered_field : 1;
                 *     unsigned int : 29;
                 *     unsigned int : 32;
                 *     unsigned int : 32;
                 * };
                 */
                @KnownStruct(structName = "jdk_version_info", //
                                memberNames = {
                                                "jdk_version",
                                }, //
                                types = {
                                                INT,
                                }),
                /*-
                 * struct member_info {
                 *     char* id;
                 *     size_t offset;
                 *     struct member_info *next;
                 * };
                 */
                @KnownStruct(structName = "member_info", //
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
                @KnownStruct(structName = "_jvmtiThreadInfo", //
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
                                                OBJECT,
                                                OBJECT
                                }),
                /*-
                 * struct _jvmtiMonitorStackDepthInfo {
                 *     jobject monitor;
                 *     jint stack_depth;
                 * };
                 */
                @KnownStruct(structName = "_jvmtiMonitorStackDepthInfo", //
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
                @KnownStruct(structName = "_jvmtiThreadGroupInfo", //
                                memberNames = {
                                                "parent",
                                                "name",
                                                "max_priority",
                                                "is_daemon",
                                }, //
                                types = {
                                                OBJECT,
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
                @KnownStruct(structName = "_jvmtiFrameInfo", //
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
                @KnownStruct(structName = "_jvmtiStackInfo", //
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
                @KnownStruct(structName = "_jvmtiHeapReferenceInfoField", //
                                memberNames = {"index"}, //
                                types = {INT}),
                /*-
                 * struct _jvmtiHeapReferenceInfoArray {
                 *     jint index;
                 * };
                 */
                @KnownStruct(structName = "_jvmtiHeapReferenceInfoArray", //
                                memberNames = {"index"}, //
                                types = {INT}),
                /*-
                 * struct _jvmtiHeapReferenceInfoConstantPool {
                 *     jint index;
                 * };
                 */
                @KnownStruct(structName = "_jvmtiHeapReferenceInfoConstantPool", //
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
                @KnownStruct(structName = "_jvmtiHeapReferenceInfoStackLocal", //
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
                @KnownStruct(structName = "_jvmtiHeapReferenceInfoJniLocal", //
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
                @KnownStruct(structName = "_jvmtiHeapReferenceInfoReserved", //
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
                @KnownStruct(structName = "_jvmtiHeapCallbacks", //
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
                @KnownStruct(structName = "_jvmtiClassDefinition", //
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
                @KnownStruct(structName = "_jvmtiMonitorUsage", //
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
                @KnownStruct(structName = "_jvmtiLineNumberEntry", //
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
                @KnownStruct(structName = "_jvmtiLocalVariableEntry", //
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
                @KnownStruct(structName = "_jvmtiParamInfo", //
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
                @KnownStruct(structName = "_jvmtiExtensionFunctionInfo", //
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
                @KnownStruct(structName = "_jvmtiExtensionEventInfo", //
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
                @KnownStruct(structName = "_jvmtiTimerInfo", //
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
                @KnownStruct(structName = "_jvmtiAddrLocationMap", //
                                memberNames = {
                                                "start_address",
                                                "location",
                                }, //
                                types = {
                                                POINTER,
                                                LONG,
                                }),
                /*-
                * typedef struct {
                //   50 : VM Initialization Event
                jvmtiEventVMInit VMInit;
                //   51 : VM Death Event
                jvmtiEventVMDeath VMDeath;
                //   52 : Thread Start
                jvmtiEventThreadStart ThreadStart;
                //   53 : Thread End
                jvmtiEventThreadEnd ThreadEnd;
                //   54 : Class File Load Hook
                jvmtiEventClassFileLoadHook ClassFileLoadHook;
                //   55 : Class Load
                jvmtiEventClassLoad ClassLoad;
                //   56 : Class Prepare
                jvmtiEventClassPrepare ClassPrepare;
                //   57 : VM Start Event
                jvmtiEventVMStart VMStart;
                //   58 : Exception
                jvmtiEventException Exception;
                //   59 : Exception Catch
                jvmtiEventExceptionCatch ExceptionCatch;
                //   60 : Single Step
                jvmtiEventSingleStep SingleStep;
                //   61 : Frame Pop
                jvmtiEventFramePop FramePop;
                //   62 : Breakpoint
                jvmtiEventBreakpoint Breakpoint;
                //   63 : Field Access
                jvmtiEventFieldAccess FieldAccess;
                //   64 : Field Modification
                jvmtiEventFieldModification FieldModification;
                //   65 : Method Entry
                jvmtiEventMethodEntry MethodEntry;
                //   66 : Method Exit
                jvmtiEventMethodExit MethodExit;
                //   67 : Native Method Bind
                jvmtiEventNativeMethodBind NativeMethodBind;
                //   68 : Compiled Method Load
                jvmtiEventCompiledMethodLoad CompiledMethodLoad;
                //   69 : Compiled Method Unload
                jvmtiEventCompiledMethodUnload CompiledMethodUnload;
                //   70 : Dynamic Code Generated
                jvmtiEventDynamicCodeGenerated DynamicCodeGenerated;
                //   71 : Data Dump Request
                jvmtiEventDataDumpRequest DataDumpRequest;
                //   72
                jvmtiEventReserved reserved72;
                //   73 : Monitor Wait
                jvmtiEventMonitorWait MonitorWait;
                //   74 : Monitor Waited
                jvmtiEventMonitorWaited MonitorWaited;
                //   75 : Monitor Contended Enter
                jvmtiEventMonitorContendedEnter MonitorContendedEnter;
                //   76 : Monitor Contended Entered
                jvmtiEventMonitorContendedEntered MonitorContendedEntered;
                //   77
                jvmtiEventReserved reserved77;
                //   78
                jvmtiEventReserved reserved78;
                //   79
                jvmtiEventReserved reserved79;
                //   80 : Resource Exhausted
                jvmtiEventResourceExhausted ResourceExhausted;
                //   81 : Garbage Collection Start
                jvmtiEventGarbageCollectionStart GarbageCollectionStart;
                //   82 : Garbage Collection Finish
                jvmtiEventGarbageCollectionFinish GarbageCollectionFinish;
                //   83 : Object Free
                jvmtiEventObjectFree ObjectFree;
                //   84 : VM Object Allocation
                jvmtiEventVMObjectAlloc VMObjectAlloc;
                //   85
                jvmtiEventReserved reserved85;
                //   86 : Sampled Object Allocation
                jvmtiEventSampledObjectAlloc SampledObjectAlloc;
                } jvmtiEventCallbacks;
                */
                @KnownStruct(structName = "_jvmtiEventCallbacks", //
                                memberNames = {
                                                "VMInit",
                                                "VMDeath",
                                                "ThreadStart",
                                                "ThreadEnd",
                                                "ClassFileLoadHook",
                                                "ClassLoad",
                                                "ClassPrepare",
                                                "VMStart",
                                                "Exception",
                                                "ExceptionCatch",
                                                "SingleStep",
                                                "FramePop",
                                                "Breakpoint",
                                                "FieldAccess",
                                                "FieldModification",
                                                "MethodEntry",
                                                "MethodExit",
                                                "NativeMethodBind",
                                                "CompiledMethodLoad",
                                                "CompiledMethodUnload",
                                                "DynamicCodeGenerated",
                                                "DataDumpRequest",
                                                "reserved72",
                                                "MonitorWait",
                                                "MonitorWaited",
                                                "MonitorContendedEnter",
                                                "MonitorContendedEntered",
                                                "reserved77",
                                                "reserved78",
                                                "reserved79",
                                                "ResourceExhausted",
                                                "GarbageCollectionStart",
                                                "GarbageCollectionFinish",
                                                "ObjectFree",
                                                "VMObjectAlloc",
                                                "reserved85",
                                                "SampledObjectAlloc",
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
                                                POINTER,
                                                POINTER,
                                                POINTER,
                                                POINTER,
                                                POINTER,
                                }),
})
public abstract class StructWrapper {
    private final JNIHandles handles;

    private final TruffleObject pointer;
    private final ByteBuffer buffer;

    public TruffleObject pointer() {
        return pointer;
    }

    public void free(NativeAccess nativeAccess) {
        nativeAccess.freeMemory(pointer);
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
