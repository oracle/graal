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

import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_ACCESS_LOCAL_VARIABLES;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_FORCE_EARLY_RETURN;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GENERATE_ALL_CLASS_HOOK_EVENTS;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GENERATE_BREAKPOINT_EVENTS;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GENERATE_COMPILED_METHOD_LOAD_EVENTS;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GENERATE_EARLY_CLASS_HOOK_EVENTS;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GENERATE_EARLY_VMSTART;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GENERATE_EXCEPTION_EVENTS;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GENERATE_FIELD_ACCESS_EVENTS;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GENERATE_FIELD_MODIFICATION_EVENTS;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GENERATE_FRAME_POP_EVENTS;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GENERATE_GARBAGE_COLLECTION_EVENTS;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GENERATE_METHOD_ENTRY_EVENTS;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GENERATE_METHOD_EXIT_EVENTS;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GENERATE_MONITOR_EVENTS;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GENERATE_NATIVE_METHOD_BIND_EVENTS;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GENERATE_OBJECT_FREE_EVENTS;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GENERATE_RESOURCE_EXHAUSTION_HEAP_EVENTS;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GENERATE_RESOURCE_EXHAUSTION_THREADS_EVENTS;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GENERATE_SAMPLED_OBJECT_ALLOC_EVENTS;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GENERATE_SINGLE_STEP_EVENTS;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GENERATE_VM_OBJECT_ALLOC_EVENTS;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GET_BYTECODES;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GET_CONSTANT_POOL;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GET_CURRENT_CONTENDED_MONITOR;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GET_CURRENT_THREAD_CPU_TIME;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GET_LINE_NUMBERS;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GET_MONITOR_INFO;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GET_OWNED_MONITOR_INFO;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GET_OWNED_MONITOR_STACK_DEPTH_INFO;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GET_SOURCE_DEBUG_EXTENSION;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GET_SOURCE_FILE_NAME;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GET_SYNTHETIC_ATTRIBUTE;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_GET_THREAD_CPU_TIME;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_MAINTAIN_ORIGINAL_METHOD_ORDER;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_POP_FRAME;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_REDEFINE_ANY_CLASS;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_REDEFINE_CLASSES;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_RETRANSFORM_ANY_CLASS;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_RETRANSFORM_CLASSES;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_SET_NATIVE_METHOD_PREFIX;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_SIGNAL_THREAD;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_SUPPORT_VIRTUAL_THREADS;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_SUSPEND;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.CAN_TAG_OBJECTS;
import static com.oracle.svm.core.jvmti.JvmtiCapabilitiesEnum.getBit;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.config.ConfigurationValues;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.NumUtil;

public class JvmtiEnvSharedUtil {
    // TODO add magic?
    /**
     * Java enum used to identify shared capabilities among jvmti environments as well as their
     * initial values based on hotspot's. Note: Initial values do not accurately depict the actual
     * capabilities currently supported by native-image
     */
    public enum SHARED_CAPABILITIES_TYPE {
        ALWAYS(initAlways()),
        ON_LOAD(initOnLoad()),
        ALWAYS_SOLO(initAlwaysSolo()),
        ON_LOAD_SOLO(initOnLoadSolo()),
        ALWAYS_SOLO_REMAINING(initAlwaysSolo()),
        ON_LOAD_SOLO_REMAINING(initOnLoadSolo()),
        ACQUIRED(0L);

        private final long initial;

        SHARED_CAPABILITIES_TYPE(long initial) {
            this.initial = initial;
        }

        public long getInitial() {
            return this.initial;
        }

        private static long initAlways() {
            long always = 0L;
            always |= getBit(CAN_GET_BYTECODES);
            always |= getBit(CAN_SIGNAL_THREAD);
            always |= getBit(CAN_GET_SOURCE_FILE_NAME);
            always |= getBit(CAN_GET_LINE_NUMBERS);
            always |= getBit(CAN_GET_SYNTHETIC_ATTRIBUTE);
            always |= getBit(CAN_GET_MONITOR_INFO);
            always |= getBit(CAN_GET_CONSTANT_POOL);
            always |= getBit(CAN_GENERATE_ALL_CLASS_HOOK_EVENTS);
            always |= getBit(CAN_GENERATE_MONITOR_EVENTS);
            always |= getBit(CAN_GENERATE_GARBAGE_COLLECTION_EVENTS);
            always |= getBit(CAN_GENERATE_COMPILED_METHOD_LOAD_EVENTS);
            always |= getBit(CAN_GENERATE_NATIVE_METHOD_BIND_EVENTS);
            always |= getBit(CAN_GENERATE_VM_OBJECT_ALLOC_EVENTS);
            always |= getBit(CAN_REDEFINE_CLASSES);
            always |= getBit(CAN_REDEFINE_ANY_CLASS);
            always |= getBit(CAN_RETRANSFORM_CLASSES);
            always |= getBit(CAN_RETRANSFORM_ANY_CLASS);
            always |= getBit(CAN_SET_NATIVE_METHOD_PREFIX);
            always |= getBit(CAN_TAG_OBJECTS);
            always |= getBit(CAN_GENERATE_OBJECT_FREE_EVENTS);
            always |= getBit(CAN_GENERATE_RESOURCE_EXHAUSTION_HEAP_EVENTS);
            always |= getBit(CAN_GENERATE_RESOURCE_EXHAUSTION_THREADS_EVENTS);
            always |= getBit(CAN_SUPPORT_VIRTUAL_THREADS);
            // return always;
            // TODO add
            /*
             * if (os::is_thread_cpu_time_supported()) { jc.can_get_current_thread_cpu_time = 1;
             * jc.can_get_thread_cpu_time = 1; }
             */
            // TODO @dprcci enabled for tests
            always |= getBit(CAN_GET_CURRENT_THREAD_CPU_TIME);
            always |= getBit(CAN_GET_THREAD_CPU_TIME);
            return always;
        }

        private static long initOnLoad() {
            long onLoad = 0L;
            onLoad |= getBit(CAN_POP_FRAME);
            onLoad |= getBit(CAN_FORCE_EARLY_RETURN);
            onLoad |= getBit(CAN_GET_SOURCE_DEBUG_EXTENSION);
            onLoad |= getBit(CAN_ACCESS_LOCAL_VARIABLES);
            onLoad |= getBit(CAN_MAINTAIN_ORIGINAL_METHOD_ORDER);
            onLoad |= getBit(CAN_GENERATE_SINGLE_STEP_EVENTS);
            onLoad |= getBit(CAN_GENERATE_EXCEPTION_EVENTS);
            onLoad |= getBit(CAN_GENERATE_FRAME_POP_EVENTS);
            onLoad |= getBit(CAN_GENERATE_METHOD_ENTRY_EVENTS);
            onLoad |= getBit(CAN_GENERATE_METHOD_EXIT_EVENTS);
            onLoad |= getBit(CAN_GET_OWNED_MONITOR_INFO);
            onLoad |= getBit(CAN_GET_OWNED_MONITOR_STACK_DEPTH_INFO);
            onLoad |= getBit(CAN_GET_CURRENT_CONTENDED_MONITOR);
            onLoad |= getBit(CAN_GENERATE_EARLY_VMSTART);
            onLoad |= getBit(CAN_GENERATE_EARLY_CLASS_HOOK_EVENTS);
            return onLoad;
        }

        private static long initAlwaysSolo() {
            long alwaysSolo = 0L;
            alwaysSolo |= getBit(CAN_SUSPEND);
            alwaysSolo |= getBit(CAN_GENERATE_SAMPLED_OBJECT_ALLOC_EVENTS);
            return alwaysSolo;
        }

        private static long initOnLoadSolo() {
            long onLoadSolo = 0L;
            onLoadSolo |= getBit(CAN_GENERATE_FIELD_MODIFICATION_EVENTS);
            onLoadSolo |= getBit(CAN_GENERATE_FIELD_ACCESS_EVENTS);
            onLoadSolo |= getBit(CAN_GENERATE_BREAKPOINT_EVENTS);
            return onLoadSolo;
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private JvmtiEnvSharedUtil() {
    }

    public static JvmtiEnvShared allocate() {
        JvmtiEnvShared sharedCapabilities = ImageSingletons.lookup(UnmanagedMemorySupport.class).calloc(WordFactory.unsigned(sharedEnvironmentSize()));
        if (sharedCapabilities.isNull()) {
            return WordFactory.nullPointer();
        }
        return sharedCapabilities;
    }

    public static void free(JvmtiEnvShared sharedCapabilities) {
        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(sharedCapabilities);
    }

    public static void initialize(JvmtiEnvShared envShared) {
        assert envShared.isNonNull();
        JvmtiCapabilitiesUtil.initSharedCapabilities(envShared);
    }

    @Fold
    static int sharedEnvironmentSize() {
        return NumUtil.roundUp(sharedCapabilitiesOffset() + (SizeOf.get(com.oracle.svm.core.jvmti.headers.JvmtiCapabilities.class) * SHARED_CAPABILITIES_TYPE.values().length),
                        ConfigurationValues.getTarget().wordSize);
    }

    @Fold
    static int sharedCapabilitiesOffset() {
        return NumUtil.roundUp(SizeOf.get(JvmtiEnvShared.class), ConfigurationValues.getTarget().wordSize);
    }

    @Fold
    static int capabilitiesSize() {
        return SizeOf.get(com.oracle.svm.core.jvmti.headers.JvmtiCapabilities.class);
    }

    public static com.oracle.svm.core.jvmti.headers.JvmtiCapabilities getSharedCapability(JvmtiEnvShared envShared, SHARED_CAPABILITIES_TYPE type) {
        assert envShared.isNonNull();
        return addOffset(envShared, sharedCapabilitiesOffset() + (type.ordinal() * capabilitiesSize()));
    }

    @SuppressWarnings("unchecked")
    private static <T extends PointerBase> T addOffset(JvmtiEnvShared env, int offset) {
        return (T) ((Pointer) env).add(offset);
    }

}
