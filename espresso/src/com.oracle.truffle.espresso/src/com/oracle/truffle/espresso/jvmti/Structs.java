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

import java.nio.ByteBuffer;

import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.ffi.nfi.NativeUtils;
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
            return getInfo(structName + "." + memberName);
        }

        private long getInfo(String str) {
            long result;
            try (RawBuffer memberBuffer = RawBuffer.getNativeString(str)) {
                result = (long) library.execute(lookupMemberOffset, memberInfoPtr, memberBuffer.pointer());
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw EspressoError.shouldNotReachHere();
            }
            if (result == -1) {
                throw EspressoError.shouldNotReachHere("Struct offset lookup for " + str + " failed.");
            }
            return result;
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

        public final class JvmtiThreadInfoWrapper {
            private final TruffleObject structPtr;
            private final ByteBuffer buf;

            private JvmtiThreadInfoWrapper(TruffleObject structPtr) {
                this.structPtr = structPtr;
                buf = NativeUtils.directByteBuffer(structPtr, structSize);
            }

            public TruffleObject pointer() {
                return structPtr;
            }
        }

        JvmtiThreadInfoWrapper wrap(TruffleObject structPtr) {
            return new JvmtiThreadInfoWrapper(structPtr);
        }

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

        private final long structSize;

        JvmtiThreadInfo(MemberOffsetGetter off) {
            structSize = off.getInfo(_jvmtiThreadInfo_str);
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

        public final class JvmtiMonitorStackDepthInfoWrapper {
            private final TruffleObject structPtr;
            private final ByteBuffer buf;

            private JvmtiMonitorStackDepthInfoWrapper(TruffleObject structPtr) {
                this.structPtr = structPtr;
                buf = NativeUtils.directByteBuffer(structPtr, structSize);
            }

            public TruffleObject pointer() {
                return structPtr;
            }
        }

        public JvmtiMonitorStackDepthInfoWrapper wrap(TruffleObject structPtr) {
            return new JvmtiMonitorStackDepthInfoWrapper(structPtr);
        }

        private static final String _jvmtiMonitorStackDepthInfo_str = "_jvmtiMonitorStackDepthInfo";

        private static final String monitor_str = "monitor";
        private static final String stack_depth_str = "stack_depth";

        private final long monitor;
        private final long stack_depth;

        private final long structSize;

        JvmtiMonitorStackDepthInfo(MemberOffsetGetter off) {
            structSize = off.getInfo(_jvmtiMonitorStackDepthInfo_str);
            monitor = off.getOffset(_jvmtiMonitorStackDepthInfo_str, monitor_str);
            stack_depth = off.getOffset(_jvmtiMonitorStackDepthInfo_str, stack_depth_str);
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

        public final class JvmtiThreadGroupInfoWrapper {
            private final TruffleObject structPtr;
            private final ByteBuffer buf;

            private JvmtiThreadGroupInfoWrapper(TruffleObject structPtr) {
                this.structPtr = structPtr;
                buf = NativeUtils.directByteBuffer(structPtr, structSize);
            }

            public TruffleObject pointer() {
                return structPtr;
            }
        }

        public JvmtiThreadGroupInfoWrapper wrap(TruffleObject structPtr) {
            return new JvmtiThreadGroupInfoWrapper(structPtr);
        }

        private static final String _jvmtiThreadGroupInfo_str = "_jvmtiThreadGroupInfo";

        private static final String parent_str = "parent";
        private static final String name_str = "name";
        private static final String max_priority_str = "max_priority";
        private static final String is_daemon_str = "is_daemon";

        private final long parent;
        private final long name;
        private final long max_priority;
        private final long is_daemon;

        private final long structSize;

        JvmtiThreadGroupInfo(MemberOffsetGetter off) {
            structSize = off.getInfo(_jvmtiThreadGroupInfo_str);
            parent = off.getOffset(_jvmtiThreadGroupInfo_str, parent_str);
            name = off.getOffset(_jvmtiThreadGroupInfo_str, name_str);
            max_priority = off.getOffset(_jvmtiThreadGroupInfo_str, max_priority_str);
            is_daemon = off.getOffset(_jvmtiThreadGroupInfo_str, is_daemon_str);
        }

    }

    /*-
     * struct _jvmtiFrameInfo {
     *     jmethodID method;
     *     jlocation location;
     * };
     */
    public static class JvmtiFrameInfo {

        public final class JvmtiFrameInfoWrapper {
            private final TruffleObject structPtr;
            private final ByteBuffer buf;

            private JvmtiFrameInfoWrapper(TruffleObject structPtr) {
                this.structPtr = structPtr;
                buf = NativeUtils.directByteBuffer(structPtr, structSize);
            }

            public TruffleObject pointer() {
                return structPtr;
            }
        }

        JvmtiFrameInfoWrapper wrap(TruffleObject structPtr) {
            return new JvmtiFrameInfoWrapper(structPtr);
        }

        private static final String _jvmtiFrameInfo_str = "_jvmtiFrameInfo";

        private static final String method_str = "method";
        private static final String location_str = "location";

        private final long method;
        private final long location;

        private final long structSize;

        JvmtiFrameInfo(MemberOffsetGetter off) {
            structSize = off.getInfo(_jvmtiFrameInfo_str);
            method = off.getOffset(_jvmtiFrameInfo_str, method_str);
            location = off.getOffset(_jvmtiFrameInfo_str, location_str);
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

        public final class JvmtiStackInfoWrapper {
            private final TruffleObject structPtr;
            private final ByteBuffer buf;

            private JvmtiStackInfoWrapper(TruffleObject structPtr) {
                this.structPtr = structPtr;
                buf = NativeUtils.directByteBuffer(structPtr, structSize);
            }

            public TruffleObject pointer() {
                return structPtr;
            }
        }

        JvmtiStackInfoWrapper wrap(TruffleObject structPtr) {
            return new JvmtiStackInfoWrapper(structPtr);
        }

        private static final String _jvmtiStackInfo_str = "_jvmtiStackInfo";

        private static final String thread_str = "thread";
        private static final String state_str = "state";
        private static final String frame_buffer_str = "frame_buffer";
        private static final String frame_count_str = "frame_count";

        private final long thread;
        private final long state;
        private final long frame_buffer;
        private final long frame_count;

        private final long structSize;

        JvmtiStackInfo(MemberOffsetGetter off) {
            structSize = off.getInfo(_jvmtiStackInfo_str);
            thread = off.getOffset(_jvmtiStackInfo_str, thread_str);
            state = off.getOffset(_jvmtiStackInfo_str, state_str);
            frame_buffer = off.getOffset(_jvmtiStackInfo_str, frame_buffer_str);
            frame_count = off.getOffset(_jvmtiStackInfo_str, frame_count_str);
        }

    }

    /*-
     * struct _jvmtiHeapReferenceInfoField {
     *     jint index;
     * };
     */
    public static class JvmtiHeapReferenceInfoField {

        public final class JvmtiHeapReferenceInfoFieldWrapper {
            private final TruffleObject structPtr;
            private final ByteBuffer buf;

            private JvmtiHeapReferenceInfoFieldWrapper(TruffleObject structPtr) {
                this.structPtr = structPtr;
                buf = NativeUtils.directByteBuffer(structPtr, structSize);
            }

            public TruffleObject pointer() {
                return structPtr;
            }
        }

        public JvmtiHeapReferenceInfoFieldWrapper wrap(TruffleObject structPtr) {
            return new JvmtiHeapReferenceInfoFieldWrapper(structPtr);
        }

        private static final String _jvmtiHeapReferenceInfoField_str = "_jvmtiHeapReferenceInfoField";

        private static final String index_str = "index";

        private final long index;

        private final long structSize;

        JvmtiHeapReferenceInfoField(MemberOffsetGetter off) {
            structSize = off.getInfo(_jvmtiHeapReferenceInfoField_str);
            index = off.getOffset(_jvmtiHeapReferenceInfoField_str, index_str);
        }

    }

    /*-
     * struct _jvmtiHeapReferenceInfoArray {
     *     jint index;
     * };
     */
    public static class JvmtiHeapReferenceInfoArray {

        public final class JvmtiHeapReferenceInfoArrayWrapper {
            private final TruffleObject structPtr;
            private final ByteBuffer buf;

            private JvmtiHeapReferenceInfoArrayWrapper(TruffleObject structPtr) {
                this.structPtr = structPtr;
                buf = NativeUtils.directByteBuffer(structPtr, structSize);
            }

            public TruffleObject pointer() {
                return structPtr;
            }
        }

        public JvmtiHeapReferenceInfoArrayWrapper wrap(TruffleObject structPtr) {
            return new JvmtiHeapReferenceInfoArrayWrapper(structPtr);
        }

        private static final String _jvmtiHeapReferenceInfoArray_str = "_jvmtiHeapReferenceInfoArray";

        private static final String index_str = "index";

        private final long index;

        private final long structSize;

        JvmtiHeapReferenceInfoArray(MemberOffsetGetter off) {
            structSize = off.getInfo(_jvmtiHeapReferenceInfoArray_str);
            index = off.getOffset(_jvmtiHeapReferenceInfoArray_str, index_str);
        }

    }

    /*-
     * struct _jvmtiHeapReferenceInfoConstantPool {
     *     jint index;
     * };
     */
    public static class JvmtiHeapReferenceInfoConstantPool {

        public final class JvmtiHeapReferenceInfoConstantPoolWrapper {
            private final TruffleObject structPtr;
            private final ByteBuffer buf;

            private JvmtiHeapReferenceInfoConstantPoolWrapper(TruffleObject structPtr) {
                this.structPtr = structPtr;
                buf = NativeUtils.directByteBuffer(structPtr, structSize);
            }

            public TruffleObject pointer() {
                return structPtr;
            }
        }

        public JvmtiHeapReferenceInfoConstantPoolWrapper wrap(TruffleObject structPtr) {
            return new JvmtiHeapReferenceInfoConstantPoolWrapper(structPtr);
        }

        private static final String _jvmtiHeapReferenceInfoConstantPool_str = "_jvmtiHeapReferenceInfoConstantPool";

        private static final String index_str = "index";

        private final long index;

        private final long structSize;

        JvmtiHeapReferenceInfoConstantPool(MemberOffsetGetter off) {
            structSize = off.getInfo(_jvmtiHeapReferenceInfoConstantPool_str);
            index = off.getOffset(_jvmtiHeapReferenceInfoConstantPool_str, index_str);
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

        public final class JvmtiHeapReferenceInfoStackLocalWrapper {
            private final TruffleObject structPtr;
            private final ByteBuffer buf;

            private JvmtiHeapReferenceInfoStackLocalWrapper(TruffleObject structPtr) {
                this.structPtr = structPtr;
                buf = NativeUtils.directByteBuffer(structPtr, structSize);
            }

            public TruffleObject pointer() {
                return structPtr;
            }
        }

        public JvmtiHeapReferenceInfoStackLocalWrapper wrap(TruffleObject structPtr) {
            return new JvmtiHeapReferenceInfoStackLocalWrapper(structPtr);
        }

        private static final String _jvmtiHeapReferenceInfoStackLocal_str = "_jvmtiHeapReferenceInfoStackLocal";

        private static final String thread_tag_str = "thread_tag";
        private static final String thread_id_str = "thread_id";
        private static final String depth_str = "depth";
        private static final String method_str = "method";
        private static final String location_str = "location";
        private static final String slot_str = "slot";

        private final long thread_tag;
        private final long thread_id;
        private final long depth;
        private final long method;
        private final long location;
        private final long slot;

        private final long structSize;

        JvmtiHeapReferenceInfoStackLocal(MemberOffsetGetter off) {
            structSize = off.getInfo(_jvmtiHeapReferenceInfoStackLocal_str);
            thread_tag = off.getOffset(_jvmtiHeapReferenceInfoStackLocal_str, thread_tag_str);
            thread_id = off.getOffset(_jvmtiHeapReferenceInfoStackLocal_str, thread_id_str);
            depth = off.getOffset(_jvmtiHeapReferenceInfoStackLocal_str, depth_str);
            method = off.getOffset(_jvmtiHeapReferenceInfoStackLocal_str, method_str);
            location = off.getOffset(_jvmtiHeapReferenceInfoStackLocal_str, location_str);
            slot = off.getOffset(_jvmtiHeapReferenceInfoStackLocal_str, slot_str);
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

        public final class JvmtiHeapReferenceInfoJniLocalWrapper {
            private final TruffleObject structPtr;
            private final ByteBuffer buf;

            private JvmtiHeapReferenceInfoJniLocalWrapper(TruffleObject structPtr) {
                this.structPtr = structPtr;
                buf = NativeUtils.directByteBuffer(structPtr, structSize);
            }

            public TruffleObject pointer() {
                return structPtr;
            }
        }

        public JvmtiHeapReferenceInfoJniLocalWrapper wrap(TruffleObject structPtr) {
            return new JvmtiHeapReferenceInfoJniLocalWrapper(structPtr);
        }

        private static final String _jvmtiHeapReferenceInfoJniLocal_str = "_jvmtiHeapReferenceInfoJniLocal";

        private static final String thread_tag_str = "thread_tag";
        private static final String thread_id_str = "thread_id";
        private static final String depth_str = "depth";
        private static final String method_str = "method";

        private final long thread_tag;
        private final long thread_id;
        private final long depth;
        private final long method;

        private final long structSize;

        JvmtiHeapReferenceInfoJniLocal(MemberOffsetGetter off) {
            structSize = off.getInfo(_jvmtiHeapReferenceInfoJniLocal_str);
            thread_tag = off.getOffset(_jvmtiHeapReferenceInfoJniLocal_str, thread_tag_str);
            thread_id = off.getOffset(_jvmtiHeapReferenceInfoJniLocal_str, thread_id_str);
            depth = off.getOffset(_jvmtiHeapReferenceInfoJniLocal_str, depth_str);
            method = off.getOffset(_jvmtiHeapReferenceInfoJniLocal_str, method_str);
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

        public final class JvmtiHeapReferenceInfoReservedWrapper {
            private final TruffleObject structPtr;
            private final ByteBuffer buf;

            private JvmtiHeapReferenceInfoReservedWrapper(TruffleObject structPtr) {
                this.structPtr = structPtr;
                buf = NativeUtils.directByteBuffer(structPtr, structSize);
            }

            public TruffleObject pointer() {
                return structPtr;
            }
        }

        public JvmtiHeapReferenceInfoReservedWrapper wrap(TruffleObject structPtr) {
            return new JvmtiHeapReferenceInfoReservedWrapper(structPtr);
        }

        private static final String _jvmtiHeapReferenceInfoReserved_str = "_jvmtiHeapReferenceInfoReserved";

        private static final String reserved1_str = "reserved1";
        private static final String reserved2_str = "reserved2";
        private static final String reserved3_str = "reserved3";
        private static final String reserved4_str = "reserved4";
        private static final String reserved5_str = "reserved5";
        private static final String reserved6_str = "reserved6";
        private static final String reserved7_str = "reserved7";
        private static final String reserved8_str = "reserved8";

        private final long reserved1;
        private final long reserved2;
        private final long reserved3;
        private final long reserved4;
        private final long reserved5;
        private final long reserved6;
        private final long reserved7;
        private final long reserved8;

        private final long structSize;

        JvmtiHeapReferenceInfoReserved(MemberOffsetGetter off) {
            structSize = off.getInfo(_jvmtiHeapReferenceInfoReserved_str);
            reserved1 = off.getOffset(_jvmtiHeapReferenceInfoReserved_str, reserved1_str);
            reserved2 = off.getOffset(_jvmtiHeapReferenceInfoReserved_str, reserved2_str);
            reserved3 = off.getOffset(_jvmtiHeapReferenceInfoReserved_str, reserved3_str);
            reserved4 = off.getOffset(_jvmtiHeapReferenceInfoReserved_str, reserved4_str);
            reserved5 = off.getOffset(_jvmtiHeapReferenceInfoReserved_str, reserved5_str);
            reserved6 = off.getOffset(_jvmtiHeapReferenceInfoReserved_str, reserved6_str);
            reserved7 = off.getOffset(_jvmtiHeapReferenceInfoReserved_str, reserved7_str);
            reserved8 = off.getOffset(_jvmtiHeapReferenceInfoReserved_str, reserved8_str);
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

        public final class JvmtiHeapReferenceInfoWrapper {
            private final TruffleObject structPtr;
            private final ByteBuffer buf;

            private JvmtiHeapReferenceInfoWrapper(TruffleObject structPtr) {
                this.structPtr = structPtr;
                buf = NativeUtils.directByteBuffer(structPtr, structSize);
            }

            public TruffleObject pointer() {
                return structPtr;
            }
        }

        public JvmtiHeapReferenceInfoWrapper wrap(TruffleObject structPtr) {
            return new JvmtiHeapReferenceInfoWrapper(structPtr);
        }

        private static final String _jvmtiHeapReferenceInfo = "_jvmtiHeapCallbacks";

        private final long structSize;

        JvmtiHeapReferenceInfo(MemberOffsetGetter off) {
            structSize = off.getInfo(_jvmtiHeapReferenceInfo);

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

        public final class JvmtiHeapCallbacksWrapper {
            private final TruffleObject structPtr;
            private final ByteBuffer buf;

            private JvmtiHeapCallbacksWrapper(TruffleObject structPtr) {
                this.structPtr = structPtr;
                buf = NativeUtils.directByteBuffer(structPtr, structSize);
            }

            public TruffleObject pointer() {
                return structPtr;
            }
        }

        JvmtiHeapCallbacksWrapper wrap(TruffleObject structPtr) {
            return new JvmtiHeapCallbacksWrapper(structPtr);
        }

        private static final String _jvmtiHeapCallbacks_str = "_jvmtiHeapCallbacks";

        private static final String heap_iteration_callback_str = "heap_iteration_callback";
        private static final String heap_reference_callback_str = "heap_reference_callback";
        private static final String primitive_field_callback_str = "primitive_field_callback";
        private static final String array_primitive_value_callback_str = "array_primitive_value_callback";
        private static final String string_primitive_value_callback_str = "string_primitive_value_callback";
        private static final String reserved5_str = "reserved5";
        private static final String reserved6_str = "reserved6";
        private static final String reserved7_str = "reserved7";
        private static final String reserved8_str = "reserved8";
        private static final String reserved9_str = "reserved9";
        private static final String reserved10_str = "reserved10";
        private static final String reserved11_str = "reserved11";
        private static final String reserved12_str = "reserved12";
        private static final String reserved13_str = "reserved13";
        private static final String reserved14_str = "reserved14";
        private static final String reserved15_str = "reserved15";

        private final long heap_iteration_callback;
        private final long heap_reference_callback;
        private final long primitive_field_callback;
        private final long array_primitive_value_callback;
        private final long string_primitive_value_callback;
        private final long reserved5;
        private final long reserved6;
        private final long reserved7;
        private final long reserved8;
        private final long reserved9;
        private final long reserved10;
        private final long reserved11;
        private final long reserved12;
        private final long reserved13;
        private final long reserved14;
        private final long reserved15;

        private final long structSize;

        JvmtiHeapCallbacks(MemberOffsetGetter off) {
            structSize = off.getInfo(_jvmtiHeapCallbacks_str);
            heap_iteration_callback = off.getOffset(_jvmtiHeapCallbacks_str, heap_iteration_callback_str);
            heap_reference_callback = off.getOffset(_jvmtiHeapCallbacks_str, heap_reference_callback_str);
            primitive_field_callback = off.getOffset(_jvmtiHeapCallbacks_str, primitive_field_callback_str);
            array_primitive_value_callback = off.getOffset(_jvmtiHeapCallbacks_str, array_primitive_value_callback_str);
            string_primitive_value_callback = off.getOffset(_jvmtiHeapCallbacks_str, string_primitive_value_callback_str);
            reserved5 = off.getOffset(_jvmtiHeapCallbacks_str, reserved5_str);
            reserved6 = off.getOffset(_jvmtiHeapCallbacks_str, reserved6_str);
            reserved7 = off.getOffset(_jvmtiHeapCallbacks_str, reserved7_str);
            reserved8 = off.getOffset(_jvmtiHeapCallbacks_str, reserved8_str);
            reserved9 = off.getOffset(_jvmtiHeapCallbacks_str, reserved9_str);
            reserved10 = off.getOffset(_jvmtiHeapCallbacks_str, reserved10_str);
            reserved11 = off.getOffset(_jvmtiHeapCallbacks_str, reserved11_str);
            reserved12 = off.getOffset(_jvmtiHeapCallbacks_str, reserved12_str);
            reserved13 = off.getOffset(_jvmtiHeapCallbacks_str, reserved13_str);
            reserved14 = off.getOffset(_jvmtiHeapCallbacks_str, reserved14_str);
            reserved15 = off.getOffset(_jvmtiHeapCallbacks_str, reserved15_str);
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

        public final class JvmtiClassDefinitionWrapper {
            private final TruffleObject structPtr;
            private final ByteBuffer buf;

            private JvmtiClassDefinitionWrapper(TruffleObject structPtr) {
                this.structPtr = structPtr;
                buf = NativeUtils.directByteBuffer(structPtr, structSize);
            }

            public TruffleObject pointer() {
                return structPtr;
            }
        }

        public JvmtiClassDefinitionWrapper wrap(TruffleObject structPtr) {
            return new JvmtiClassDefinitionWrapper(structPtr);
        }

        private static final String _jvmtiClassDefinition_str = "_jvmtiClassDefinition";

        private static final String klass_str = "klass";
        private static final String class_byte_count_str = "class_byte_count";
        private static final String class_bytes_str = "class_bytes";

        private final long klass;
        private final long class_byte_count;
        private final long class_bytes;

        private final long structSize;

        JvmtiClassDefinition(MemberOffsetGetter off) {
            structSize = off.getInfo(_jvmtiClassDefinition_str);
            klass = off.getOffset(_jvmtiClassDefinition_str, klass_str);
            class_byte_count = off.getOffset(_jvmtiClassDefinition_str, class_byte_count_str);
            class_bytes = off.getOffset(_jvmtiClassDefinition_str, class_bytes_str);
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

        public final class JvmtiMonitorUsageWrapper {
            private final TruffleObject structPtr;
            private final ByteBuffer buf;

            private JvmtiMonitorUsageWrapper(TruffleObject structPtr) {
                this.structPtr = structPtr;
                buf = NativeUtils.directByteBuffer(structPtr, structSize);
            }

            public TruffleObject pointer() {
                return structPtr;
            }
        }

        JvmtiMonitorUsageWrapper wrap(TruffleObject structPtr) {
            return new JvmtiMonitorUsageWrapper(structPtr);
        }

        private static final String _jvmtiMonitorUsage_str = "_jvmtiMonitorUsage";

        private static final String owner_str = "owner";
        private static final String entry_count_str = "entry_count";
        private static final String waiter_count_str = "waiter_count";
        private static final String waiters_str = "waiters";
        private static final String notify_waiter_count_str = "notify_waiter_count";
        private static final String notify_waiters_str = "notify_waiters";

        private final long owner;
        private final long entry_count;
        private final long waiter_count;
        private final long waiters;
        private final long notify_waiter_count;
        private final long notify_waiters;

        private final long structSize;

        JvmtiMonitorUsage(MemberOffsetGetter off) {
            structSize = off.getInfo(_jvmtiMonitorUsage_str);
            owner = off.getOffset(_jvmtiMonitorUsage_str, owner_str);
            entry_count = off.getOffset(_jvmtiMonitorUsage_str, entry_count_str);
            waiter_count = off.getOffset(_jvmtiMonitorUsage_str, waiter_count_str);
            waiters = off.getOffset(_jvmtiMonitorUsage_str, waiters_str);
            notify_waiter_count = off.getOffset(_jvmtiMonitorUsage_str, notify_waiter_count_str);
            notify_waiters = off.getOffset(_jvmtiMonitorUsage_str, notify_waiters_str);
        }

    }

    /*-
     * struct _jvmtiLineNumberEntry {
     *     jlocation start_location;
     *     jint line_number;
     * };
     */
    public static class JvmtiLineNumberEntry {

        public final class JvmtiLineNumberEntryWrapper {
            private final TruffleObject structPtr;
            private final ByteBuffer buf;

            private JvmtiLineNumberEntryWrapper(TruffleObject structPtr) {
                this.structPtr = structPtr;
                buf = NativeUtils.directByteBuffer(structPtr, structSize);
            }

            public TruffleObject pointer() {
                return structPtr;
            }
        }

        public JvmtiLineNumberEntryWrapper wrap(TruffleObject structPtr) {
            return new JvmtiLineNumberEntryWrapper(structPtr);
        }

        private static final String _jvmtiLineNumberEntry_str = "_jvmtiLineNumberEntry";

        private static final String start_location_str = "start_location";
        private static final String line_number_str = "line_number";

        private final long start_location;
        private final long line_number;

        private final long structSize;

        JvmtiLineNumberEntry(MemberOffsetGetter off) {
            structSize = off.getInfo(_jvmtiLineNumberEntry_str);
            start_location = off.getOffset(_jvmtiLineNumberEntry_str, start_location_str);
            line_number = off.getOffset(_jvmtiLineNumberEntry_str, line_number_str);
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

        public final class JvmtiLocalVariableEntryWrapper {
            private final TruffleObject structPtr;
            private final ByteBuffer buf;

            private JvmtiLocalVariableEntryWrapper(TruffleObject structPtr) {
                this.structPtr = structPtr;
                buf = NativeUtils.directByteBuffer(structPtr, structSize);
            }

            public TruffleObject pointer() {
                return structPtr;
            }
        }

        public JvmtiLocalVariableEntryWrapper wrap(TruffleObject structPtr) {
            return new JvmtiLocalVariableEntryWrapper(structPtr);
        }

        private static final String _jvmtiLocalVariableEntry_str = "_jvmtiLocalVariableEntry";

        private static final String start_location_str = "start_location";
        private static final String length_str = "length";
        private static final String name_str = "name";
        private static final String signature_str = "signature";
        private static final String generic_signature_str = "generic_signature";
        private static final String slot_str = "slot";

        private final long start_location;
        private final long length;
        private final long name;
        private final long signature;
        private final long generic_signature;
        private final long slot;

        private final long structSize;

        JvmtiLocalVariableEntry(MemberOffsetGetter off) {
            structSize = off.getInfo(_jvmtiLocalVariableEntry_str);
            start_location = off.getOffset(_jvmtiLocalVariableEntry_str, start_location_str);
            length = off.getOffset(_jvmtiLocalVariableEntry_str, length_str);
            name = off.getOffset(_jvmtiLocalVariableEntry_str, name_str);
            signature = off.getOffset(_jvmtiLocalVariableEntry_str, signature_str);
            generic_signature = off.getOffset(_jvmtiLocalVariableEntry_str, generic_signature_str);
            slot = off.getOffset(_jvmtiLocalVariableEntry_str, slot_str);
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

        public final class JvmtiParamInfoWrapper {
            private final TruffleObject structPtr;
            private final ByteBuffer buf;

            private JvmtiParamInfoWrapper(TruffleObject structPtr) {
                this.structPtr = structPtr;
                buf = NativeUtils.directByteBuffer(structPtr, structSize);
            }

            public TruffleObject pointer() {
                return structPtr;
            }
        }

        JvmtiParamInfoWrapper wrap(TruffleObject structPtr) {
            return new JvmtiParamInfoWrapper(structPtr);
        }

        private static final String _jvmtiParamInfo_str = "_jvmtiParamInfo";

        private static final String name_str = "name";
        private static final String kind_str = "kind";
        private static final String base_type_str = "base_type";
        private static final String null_ok_str = "null_ok";

        private final long name;
        private final long kind;
        private final long base_type;
        private final long null_ok;

        private final long structSize;

        JvmtiParamInfo(MemberOffsetGetter off) {
            structSize = off.getInfo(_jvmtiParamInfo_str);
            name = off.getOffset(_jvmtiParamInfo_str, name_str);
            kind = off.getOffset(_jvmtiParamInfo_str, kind_str);
            base_type = off.getOffset(_jvmtiParamInfo_str, base_type_str);
            null_ok = off.getOffset(_jvmtiParamInfo_str, null_ok_str);
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

        public final class JvmtiExtensionFunctionInfoWrapper {
            private final TruffleObject structPtr;
            private final ByteBuffer buf;

            private JvmtiExtensionFunctionInfoWrapper(TruffleObject structPtr) {
                this.structPtr = structPtr;
                buf = NativeUtils.directByteBuffer(structPtr, structSize);
            }

            public TruffleObject pointer() {
                return structPtr;
            }
        }

        public JvmtiExtensionFunctionInfoWrapper wrap(TruffleObject structPtr) {
            return new JvmtiExtensionFunctionInfoWrapper(structPtr);
        }

        private static final String _jvmtiExtensionFunctionInfo_str = "_jvmtiExtensionFunctionInfo";

        private static final String func_str = "func";
        private static final String id_str = "id";
        private static final String short_description_str = "short_description";
        private static final String param_count_str = "param_count";
        private static final String params_str = "params";
        private static final String error_count_str = "error_count";
        private static final String errors_str = "errors";

        private final long func;
        private final long id;
        private final long short_description;
        private final long param_count;
        private final long params;
        private final long error_count;
        private final long errors;

        private final long structSize;

        JvmtiExtensionFunctionInfo(MemberOffsetGetter off) {
            structSize = off.getInfo(_jvmtiExtensionFunctionInfo_str);
            func = off.getOffset(_jvmtiExtensionFunctionInfo_str, func_str);
            id = off.getOffset(_jvmtiExtensionFunctionInfo_str, id_str);
            short_description = off.getOffset(_jvmtiExtensionFunctionInfo_str, short_description_str);
            param_count = off.getOffset(_jvmtiExtensionFunctionInfo_str, param_count_str);
            params = off.getOffset(_jvmtiExtensionFunctionInfo_str, params_str);
            error_count = off.getOffset(_jvmtiExtensionFunctionInfo_str, error_count_str);
            errors = off.getOffset(_jvmtiExtensionFunctionInfo_str, errors_str);
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

        public final class JvmtiExtensionEventInfoWrapper {
            private final TruffleObject structPtr;
            private final ByteBuffer buf;

            private JvmtiExtensionEventInfoWrapper(TruffleObject structPtr) {
                this.structPtr = structPtr;
                buf = NativeUtils.directByteBuffer(structPtr, structSize);
            }

            public TruffleObject pointer() {
                return structPtr;
            }
        }

        public JvmtiExtensionEventInfoWrapper wrap(TruffleObject structPtr) {
            return new JvmtiExtensionEventInfoWrapper(structPtr);
        }

        private static final String _jvmtiExtensionEventInfo_str = "_jvmtiExtensionEventInfo";

        private static final String extension_event_index_str = "extension_event_index";
        private static final String id_str = "id";
        private static final String short_description_str = "short_description";
        private static final String param_count_str = "param_count";
        private static final String params_str = "params";

        private final long extension_event_index;
        private final long id;
        private final long short_description;
        private final long param_count;
        private final long params;

        private final long structSize;

        JvmtiExtensionEventInfo(MemberOffsetGetter off) {
            structSize = off.getInfo(_jvmtiExtensionEventInfo_str);
            extension_event_index = off.getOffset(_jvmtiExtensionEventInfo_str, extension_event_index_str);
            id = off.getOffset(_jvmtiExtensionEventInfo_str, id_str);
            short_description = off.getOffset(_jvmtiExtensionEventInfo_str, short_description_str);
            param_count = off.getOffset(_jvmtiExtensionEventInfo_str, param_count_str);
            params = off.getOffset(_jvmtiExtensionEventInfo_str, params_str);
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

        public final class JvmtiTimerInfoWrapper {
            private final TruffleObject structPtr;
            private final ByteBuffer buf;

            private JvmtiTimerInfoWrapper(TruffleObject structPtr) {
                this.structPtr = structPtr;
                buf = NativeUtils.directByteBuffer(structPtr, structSize);
            }

            public TruffleObject pointer() {
                return structPtr;
            }
        }

        JvmtiTimerInfoWrapper wrap(TruffleObject structPtr) {
            return new JvmtiTimerInfoWrapper(structPtr);
        }

        private static final String _jvmtiTimerInfo_str = "_jvmtiTimerInfo";

        private static final String max_value_str = "max_value";
        private static final String may_skip_forward_str = "may_skip_forward";
        private static final String may_skip_backward_str = "may_skip_backward";
        private static final String kind_str = "kind";
        private static final String reserved1_str = "reserved1";
        private static final String reserved2_str = "reserved2";

        private final long max_value;
        private final long may_skip_forward;
        private final long may_skip_backward;
        private final long kind;
        private final long reserved1;
        private final long reserved2;

        private final long structSize;

        JvmtiTimerInfo(MemberOffsetGetter off) {
            structSize = off.getInfo(_jvmtiTimerInfo_str);
            max_value = off.getOffset(_jvmtiTimerInfo_str, max_value_str);
            may_skip_forward = off.getOffset(_jvmtiTimerInfo_str, may_skip_forward_str);
            may_skip_backward = off.getOffset(_jvmtiTimerInfo_str, may_skip_backward_str);
            kind = off.getOffset(_jvmtiTimerInfo_str, kind_str);
            reserved1 = off.getOffset(_jvmtiTimerInfo_str, reserved1_str);
            reserved2 = off.getOffset(_jvmtiTimerInfo_str, reserved2_str);
        }

    }

    /*-
     * struct _jvmtiAddrLocationMap {
     *     const void* start_address;
     *     jlocation location;
     * };
     */
    public static class JvmtiAddrLocationMap {

        public final class JvmtiAddrLocationMapWrapper {
            private final TruffleObject structPtr;
            private final ByteBuffer buf;

            private JvmtiAddrLocationMapWrapper(TruffleObject structPtr) {
                this.structPtr = structPtr;
                buf = NativeUtils.directByteBuffer(structPtr, structSize);
            }

            public TruffleObject pointer() {
                return structPtr;
            }
        }

        public JvmtiAddrLocationMapWrapper wrap(TruffleObject structPtr) {
            return new JvmtiAddrLocationMapWrapper(structPtr);
        }

        private static final String _jvmtiAddrLocationMap_str = "_jvmtiAddrLocationMap";

        private static final String start_address_str = "start_address";
        private static final String location_str = "location";

        private final long start_address;
        private final long location;

        private final long structSize;

        JvmtiAddrLocationMap(MemberOffsetGetter off) {
            structSize = off.getInfo(_jvmtiAddrLocationMap_str);
            start_address = off.getOffset(_jvmtiAddrLocationMap_str, start_address_str);
            location = off.getOffset(_jvmtiAddrLocationMap_str, location_str);
        }

    }
}
