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

import static com.oracle.svm.core.jvmti.headers.JvmtiError.JVMTI_ERROR_NONE;
import static com.oracle.svm.core.jvmti.headers.JvmtiError.JVMTI_ERROR_NOT_AVAILABLE;

import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.jvmti.headers.JvmtiCapabilities;
import com.oracle.svm.core.jvmti.headers.JvmtiError;
import com.oracle.svm.core.jvmti.headers.JvmtiEvent;
import com.oracle.svm.core.jvmti.headers.JvmtiEventCallbacks;
import com.oracle.svm.core.jvmti.headers.JvmtiExternalEnv;
import com.oracle.svm.core.jvmti.headers.JvmtiInterface;
import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.NumUtil;

/** Methods related to {@link JvmtiEnv}. */
public final class JvmtiEnvUtil {
    private static final int VALID_ENV_MAGIC = 0x71EE;
    private static final int DISPOSED_ENV_MAGIC = 0xDEFC;

    @Platforms(Platform.HOSTED_ONLY.class)
    private JvmtiEnvUtil() {
    }

    static JvmtiEnv allocate() {
        JvmtiInterface functionTable = JvmtiFunctionTable.allocateFunctionTable();
        if (functionTable.isNull()) {
            return Word.nullPointer();
        }

        JvmtiEnv env = NullableNativeMemory.calloc(Word.unsigned(internalEnvSize()), NmtCategory.JVMTI);
        if (env.isNull()) {
            JvmtiFunctionTable.freeFunctionTable(functionTable);
            return Word.nullPointer();
        }

        env.setIsolate(CurrentIsolate.getIsolate());
        env.setMagic(VALID_ENV_MAGIC);

        JvmtiExternalEnv externalEnv = toExternal(env);
        externalEnv.setFunctions(functionTable);
        return env;
    }

    static void dispose(JvmtiEnv env) {
        JvmtiCapabilities capabilities = getCapabilities(env);
        relinquishCapabilities(capabilities);

        env.setMagic(DISPOSED_ENV_MAGIC);
    }

    static void free(JvmtiEnv env) {
        JvmtiExternalEnv externalEnv = toExternal(env);
        JvmtiFunctionTable.freeFunctionTable(externalEnv.getFunctions());
        externalEnv.setFunctions(Word.nullPointer());

        NullableNativeMemory.free(env);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static JvmtiEnv toInternal(JvmtiExternalEnv externalEnv) {
        assert externalEnv.isNonNull();
        return (JvmtiEnv) ((Pointer) externalEnv).subtract(externalEnvOffset());
    }

    public static JvmtiExternalEnv toExternal(JvmtiEnv env) {
        assert env.isNonNull();
        return (JvmtiExternalEnv) ((Pointer) env).add(externalEnvOffset());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isValid(JvmtiEnv env) {
        assert env.isNonNull();
        return env.getMagic() == VALID_ENV_MAGIC;
    }

    public static boolean isDead(JvmtiEnv env) {
        return env.getMagic() == DISPOSED_ENV_MAGIC;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Isolate getIsolate(JvmtiEnv env) {
        assert isValid(env);
        return env.getIsolate();
    }

    public static JvmtiCapabilities getCapabilities(JvmtiEnv env) {
        assert isValid(env);
        return addOffset(env, capabilitiesOffset());
    }

    public static JvmtiError addCapabilities(JvmtiCapabilities capabilities) {
        assert capabilities.isNonNull();
        /* We don't support any capabilities at the moment. */
        if (JvmtiCapabilitiesUtil.hasAny(capabilities)) {
            return JVMTI_ERROR_NOT_AVAILABLE;
        }
        return JVMTI_ERROR_NONE;
    }

    public static JvmtiError relinquishCapabilities(JvmtiCapabilities capabilities) {
        assert capabilities.isNonNull();
        /* Nothing to do because we don't support any capabilities at the moment. */
        return JVMTI_ERROR_NONE;
    }

    static JvmtiEventCallbacks getEventCallbacks(JvmtiEnv env) {
        assert isValid(env);
        return addOffset(env, eventCallbacksOffset());
    }

    public static void setEventCallbacks(JvmtiEnv env, JvmtiEventCallbacks newCallbacks, int sizeOfCallbacks) {
        JvmtiEventCallbacks eventCallbacks = getEventCallbacks(env);
        JvmtiEventCallbacksUtil.setEventCallbacks(eventCallbacks, newCallbacks, sizeOfCallbacks);
    }

    public static boolean hasEventCapability() {
        /* At the moment, we only support events that don't need any specific capabilities. */
        return true;
    }

    public static void setEventUserEnabled(JvmtiEnv env, Thread javaEventThread, JvmtiEvent eventType, boolean value) {
        assert javaEventThread == null : "thread-local events are not supported at the moment";

        long enabledBits = env.getEventUserEnabled();
        long bit = JvmtiEvent.getBit(eventType);
        if (value) {
            enabledBits |= bit;
        } else {
            enabledBits &= ~bit;
        }
        env.setEventUserEnabled(enabledBits);
    }

    public static boolean isEventEnabled(JvmtiEnv env, JvmtiEvent eventType) {
        /* At the moment, this only checks if an event is user-enabled. */
        return (env.getEventUserEnabled() & JvmtiEvent.getBit(eventType)) != 0;
    }

    @SuppressWarnings("unchecked")
    private static <T extends PointerBase> T addOffset(JvmtiEnv env, int offset) {
        return (T) ((Pointer) env).add(offset);
    }

    @Fold
    static int capabilitiesOffset() {
        return NumUtil.roundUp(SizeOf.get(JvmtiEnv.class), ConfigurationValues.getTarget().wordSize);
    }

    @Fold
    static int eventCallbacksOffset() {
        return NumUtil.roundUp(capabilitiesOffset() + SizeOf.get(JvmtiCapabilities.class), ConfigurationValues.getTarget().wordSize);
    }

    @Fold
    static int externalEnvOffset() {
        return NumUtil.roundUp(eventCallbacksOffset() + SizeOf.get(JvmtiEventCallbacks.class), ConfigurationValues.getTarget().wordSize);
    }

    @Fold
    static int internalEnvSize() {
        return externalEnvOffset() + SizeOf.get(JvmtiExternalEnv.class);
    }
}
