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

package com.oracle.truffle.espresso.jvmti;

import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.jni.RawBuffer;
import com.oracle.truffle.espresso.meta.EspressoError;

public final class Structs {
    final JvmtiThreadInfo jvmtiThreadInfo;
    final JvmtiMonitorStackDepthInfo jvmtiMonitorStackDepthInfo;
    final JvmtiThreadGroupInfo jvmtiThreadGroupInfo;
    final JvmtiFrameInfo jvmtiFrameInfo;
    final JvmtiStackInfo jvmtiStackInfo;
    final JvmtiHeapReferenceInfoField jvmtiHeapReferenceInfoField;
    final JvmtiHeapReferenceInfoArray jvmtiHeapReferenceInfoArray;
    final JvmtiHeapReferenceInfoConstantPool jvmtiHeapReferenceInfoConstantPool;
    final JvmtiHeapReferenceInfoStackLocal jvmtiHeapReferenceInfoStackLocal;
    final JvmtiHeapReferenceInfoJniLocal jvmtiHeapReferenceInfoJniLocal;
    final JvmtiHeapReferenceInfoReserved jvmtiHeapReferenceInfoReserved;
    final JvmtiHeapReferenceInfo jvmtiHeapReferenceInfo;
    final JvmtiHeapCallbacks jvmtiHeapCallbacks;
    final JvmtiClassDefinition jvmtiClassDefinition;
    final JvmtiMonitorUsage jvmtiMonitorUsage;
    final JvmtiLineNumberEntry jvmtiLineNumberEntry;
    final JvmtiLocalVariableEntry jvmtiLocalVariableEntry;
    final JvmtiParamInfo jvmtiParamInfo;
    final JvmtiExtensionFunctionInfo jvmtiExtensionFunctionInfo;
    final JvmtiExtensionEventInfo jvmtiExtensionEventInfo;
    final JvmtiTimerInfo jvmtiTimerInfo;
    final JvmtiAddrLocationMap jvmtiAddrLocationMap;

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
        jvmtiHeapReferenceInfo = new JvmtiHeapReferenceInfo(off);
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

    private final static class MemberOffsetGetter {
        private final InteropLibrary library;
        private final TruffleObject memberInfoPtr;
        private final TruffleObject lookupMemberOffset;

        public MemberOffsetGetter(InteropLibrary library, TruffleObject memberInfoPtr, TruffleObject lookupMemberOffset) {
            this.library = library;
            this.memberInfoPtr = memberInfoPtr;
            this.lookupMemberOffset = lookupMemberOffset;
        }

        private long getOffset(String structName, String memberName) {
            try (RawBuffer memberBuffer = RawBuffer.getNativeString(structName + "." + memberName)) {
                return (long) library.execute(lookupMemberOffset, memberInfoPtr, memberBuffer.pointer());
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw EspressoError.shouldNotReachHere();
            }
        }
    }

    /*-
     * struct _jvmtiThreadInfo {
     *     char* name;
     *     jint priority;
     *     jboolean is_daemon;
     *     jthreadGroup thread_group;
     *     jobject context_class_loader;
     * };
     */
    public static class JvmtiThreadInfo {
        private static final String _jvmtiThreadInfo_str = "_jvmtiThreadInfo";

        private final static String name_str = "name";
        private final static String priority_str = "priority";
        private final static String is_daemon_str = "is_daemon";
        private final static String thread_group_str = "thread_group";
        private final static String context_class_loader_str = "context_class_loader";

        private final long name;
        private final long priority;
        private final long is_daemon;
        private final long thread_group;
        private final long context_class_loader;

        JvmtiThreadInfo(MemberOffsetGetter off) {
            name = off.getOffset(_jvmtiThreadInfo_str, name_str);
            priority = off.getOffset(_jvmtiThreadInfo_str, priority_str);
            is_daemon = off.getOffset(_jvmtiThreadInfo_str, is_daemon_str);
            thread_group = off.getOffset(_jvmtiThreadInfo_str, thread_group_str);
            context_class_loader = off.getOffset(_jvmtiThreadInfo_str, context_class_loader_str);
        }
    }

    /*-
     * struct _jvmtiMonitorStackDepthInfo {
     *     jobject monitor;
     *     jint stack_depth;
     * };
     */
    public static class JvmtiMonitorStackDepthInfo {
        JvmtiMonitorStackDepthInfo(MemberOffsetGetter off) {

        }

    }

    /*-
     * struct _jvmtiThreadGroupInfo {
     *     jthreadGroup parent;
     *     char* name;
     *     jint max_priority;
     *     jboolean is_daemon;
     * };
     * 
     */
    public static class JvmtiThreadGroupInfo {
        JvmtiThreadGroupInfo(MemberOffsetGetter off) {

        }

    }

    /*-
     * struct _jvmtiFrameInfo {
     *     jmethodID method;
     *     jlocation location;
     * };
     */
    public static class JvmtiFrameInfo {
        JvmtiFrameInfo(MemberOffsetGetter off) {

        }

    }

    /*-
     * struct _jvmtiStackInfo {
     *     jthread thread;
     *     jint state;
     *     jvmtiFrameInfo* frame_buffer;
     *     jint frame_count;
     * };
     */
    public static class JvmtiStackInfo {
        JvmtiStackInfo(MemberOffsetGetter off) {

        }

    }

    /*-
     * struct _jvmtiHeapReferenceInfoField {
     *     jint index;
     * };
     */
    public static class JvmtiHeapReferenceInfoField {
        JvmtiHeapReferenceInfoField(MemberOffsetGetter off) {

        }

    }

    /*-
     * struct _jvmtiHeapReferenceInfoArray {
     *     jint index;
     * };
     */
    public static class JvmtiHeapReferenceInfoArray {
        JvmtiHeapReferenceInfoArray(MemberOffsetGetter off) {

        }

    }

    /*-
     * struct _jvmtiHeapReferenceInfoConstantPool {
     *     jint index;
     * };
     */
    public static class JvmtiHeapReferenceInfoConstantPool {
        JvmtiHeapReferenceInfoConstantPool(MemberOffsetGetter off) {

        }

    }

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
    public static class JvmtiHeapReferenceInfoStackLocal {
        JvmtiHeapReferenceInfoStackLocal(MemberOffsetGetter off) {

        }

    }

    /*-
     * struct _jvmtiHeapReferenceInfoJniLocal {
     *     jlong thread_tag;
     *     jlong thread_id;
     *     jint depth;
     *     jmethodID method;
     * };
     */
    public static class JvmtiHeapReferenceInfoJniLocal {
        JvmtiHeapReferenceInfoJniLocal(MemberOffsetGetter off) {

        }

    }

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
    public static class JvmtiHeapReferenceInfoReserved {
        JvmtiHeapReferenceInfoReserved(MemberOffsetGetter off) {

        }

    }

    /*-
     * union _jvmtiHeapReferenceInfo {
     *     jvmtiHeapReferenceInfoField field;
     *     jvmtiHeapReferenceInfoArray array;
     *     jvmtiHeapReferenceInfoConstantPool constant_pool;
     *     jvmtiHeapReferenceInfoStackLocal stack_local;
     *     jvmtiHeapReferenceInfoJniLocal jni_local;
     *     jvmtiHeapReferenceInfoReserved other;
     * };
     */
    public static class JvmtiHeapReferenceInfo {
        JvmtiHeapReferenceInfo(MemberOffsetGetter off) {

        }

    }

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
    public static class JvmtiHeapCallbacks {
        JvmtiHeapCallbacks(MemberOffsetGetter off) {

        }

    }

    /*-
     * struct _jvmtiClassDefinition {
     *     jclass klass;
     *     jint class_byte_count;
     *     const unsigned char* class_bytes;
     * };
     */
    public static class JvmtiClassDefinition {
        JvmtiClassDefinition(MemberOffsetGetter off) {

        }

    }

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
    public static class JvmtiMonitorUsage {
        JvmtiMonitorUsage(MemberOffsetGetter off) {

        }

    }

    /*-
     * struct _jvmtiLineNumberEntry {
     *     jlocation start_location;
     *     jint line_number;
     * };
     */
    public static class JvmtiLineNumberEntry {
        JvmtiLineNumberEntry(MemberOffsetGetter off) {

        }

    }

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
    public static class JvmtiLocalVariableEntry {
        JvmtiLocalVariableEntry(MemberOffsetGetter off) {

        }

    }

    /*-
     * struct _jvmtiParamInfo {
     *     char* name;
     *     jvmtiParamKind kind;
     *     jvmtiParamTypes base_type;
     *     jboolean null_ok;
     * };
     */
    public static class JvmtiParamInfo {
        JvmtiParamInfo(MemberOffsetGetter off) {

        }

    }

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
    public static class JvmtiExtensionFunctionInfo {
        JvmtiExtensionFunctionInfo(MemberOffsetGetter off) {

        }

    }

    /*-
     * struct _jvmtiExtensionEventInfo {
     *     jint extension_event_index;
     *     char* id;
     *     char* short_description;
     *     jint param_count;
     *     jvmtiParamInfo* params;
     * };
     */
    public static class JvmtiExtensionEventInfo {
        JvmtiExtensionEventInfo(MemberOffsetGetter off) {

        }

    }

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
    public static class JvmtiTimerInfo {
        JvmtiTimerInfo(MemberOffsetGetter off) {

        }

    }

    /*-
     * struct _jvmtiAddrLocationMap {
     *     const void* start_address;
     *     jlocation location;
     * };
     */
    public static class JvmtiAddrLocationMap {
        JvmtiAddrLocationMap(MemberOffsetGetter off) {

        }

    }
}
