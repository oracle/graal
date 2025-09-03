/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
//Checkstyle: stop
// @formatter:off
package com.oracle.svm.jdwp.bridge;

import com.oracle.svm.jdwp.bridge.nativebridge.BinaryMarshaller;
import com.oracle.svm.jdwp.bridge.nativebridge.ForeignException;
import com.oracle.svm.jdwp.bridge.nativebridge.JNIClassCache;
import com.oracle.svm.jdwp.bridge.nativebridge.JNIConfig;
import com.oracle.svm.jdwp.bridge.jniutils.JNI.JClass;
import com.oracle.svm.jdwp.bridge.jniutils.JNI.JNIEnv;
import com.oracle.svm.jdwp.bridge.jniutils.JNI.JObject;
import com.oracle.svm.jdwp.bridge.jniutils.JNI.JValue;
import com.oracle.svm.jdwp.bridge.jniutils.JNICalls.JNIMethod;
import com.oracle.svm.jdwp.bridge.jniutils.JNIEntryPoint;
import com.oracle.svm.jdwp.bridge.jniutils.JNIMethodScope;
import com.oracle.svm.jdwp.bridge.jniutils.JNIUtil;
import org.graalvm.nativeimage.StackValue;

/* Checkout README.md before modifying */
final class NativeToHSJDWPEventHandlerBridgeGen {

    static NativeToHSJDWPEventHandlerBridge createNativeToHS(JNIEnv env, JObject handle) {
        return new StartPoint(env, handle);
    }

    private static final class StartPoint extends NativeToHSJDWPEventHandlerBridge {

        private static final BinaryMarshaller<Throwable> throwableMarshaller;
        static  {
            JNIConfig config = JDWPJNIConfig.getInstance();
            throwableMarshaller = config.lookupMarshaller(Throwable.class);
        }
        
        static final class JNIData {

            static JNIData cache_;
            final JClass endPointClass;
            final JNIMethod onEventAtMethod;
            final JNIMethod onThreadDeathMethod;
            final JNIMethod onThreadStartMethod;
            final JNIMethod onVMDeathMethod;
            final JNIMethod spawnServerMethod;

            JNIData(JNIEnv jniEnv) {
                this.endPointClass = JNIClassCache.lookupClass(jniEnv, EndPoint.class);
                this.onEventAtMethod = JNIMethod.findMethod(jniEnv, endPointClass, true, "onEventAt", "(Lcom/oracle/svm/jdwp/bridge/JDWPEventHandlerBridge;JJBJIBJI)V");
                this.onThreadDeathMethod = JNIMethod.findMethod(jniEnv, endPointClass, true, "onThreadDeath", "(Lcom/oracle/svm/jdwp/bridge/JDWPEventHandlerBridge;J)V");
                this.onThreadStartMethod = JNIMethod.findMethod(jniEnv, endPointClass, true, "onThreadStart", "(Lcom/oracle/svm/jdwp/bridge/JDWPEventHandlerBridge;J)V");
                this.onVMDeathMethod = JNIMethod.findMethod(jniEnv, endPointClass, true, "onVMDeath", "(Lcom/oracle/svm/jdwp/bridge/JDWPEventHandlerBridge;)V");
                this.spawnServerMethod = JNIMethod.findMethod(jniEnv, endPointClass, true, "spawnServer", "(Lcom/oracle/svm/jdwp/bridge/JDWPEventHandlerBridge;Ljava/lang/String;Ljava/lang/String;JJJLjava/lang/String;Ljava/lang/String;Z)V");
            }
        }

        final JNIData jniMethods_;

        StartPoint(JNIEnv env, JObject handle) {
            super(env, handle);
            JNIData localJNI = JNIData.cache_;
            if (localJNI == null) {
                localJNI = JNIData.cache_ = new JNIData(env);
            }
            this.jniMethods_ = localJNI;
        }
        
        @Override
        public void onEventAt(long threadId, long classId, byte typeTag, long methodId, int bci, byte resultTag, long resultPrimitiveOrId, int eventKindFlags) {
            try {
                JNIEnv jniEnv = JNIMethodScope.env();
                JValue jniArgs = StackValue.get(9, JValue.class);
                jniArgs.addressOf(0).setJObject(this.getHandle());
                jniArgs.addressOf(1).setLong(threadId);
                jniArgs.addressOf(2).setLong(classId);
                jniArgs.addressOf(3).setByte(typeTag);
                jniArgs.addressOf(4).setLong(methodId);
                jniArgs.addressOf(5).setInt(bci);
                jniArgs.addressOf(6).setByte(resultTag);
                jniArgs.addressOf(7).setLong(resultPrimitiveOrId);
                jniArgs.addressOf(8).setInt(eventKindFlags);
                ForeignException.getJNICalls().callStaticVoid(jniEnv, jniMethods_.endPointClass, jniMethods_.onEventAtMethod, jniArgs);
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            }
        }
        
        @Override
        public void onThreadDeath(long threadId) {
            try {
                JNIEnv jniEnv = JNIMethodScope.env();
                JValue jniArgs = StackValue.get(2, JValue.class);
                jniArgs.addressOf(0).setJObject(this.getHandle());
                jniArgs.addressOf(1).setLong(threadId);
                ForeignException.getJNICalls().callStaticVoid(jniEnv, jniMethods_.endPointClass, jniMethods_.onThreadDeathMethod, jniArgs);
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            }
        }
        
        @Override
        public void onThreadStart(long threadId) {
            try {
                JNIEnv jniEnv = JNIMethodScope.env();
                JValue jniArgs = StackValue.get(2, JValue.class);
                jniArgs.addressOf(0).setJObject(this.getHandle());
                jniArgs.addressOf(1).setLong(threadId);
                ForeignException.getJNICalls().callStaticVoid(jniEnv, jniMethods_.endPointClass, jniMethods_.onThreadStartMethod, jniArgs);
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            }
        }
        
        @Override
        public void onVMDeath() {
            try {
                JNIEnv jniEnv = JNIMethodScope.env();
                JValue jniArgs = StackValue.get(1, JValue.class);
                jniArgs.addressOf(0).setJObject(this.getHandle());
                ForeignException.getJNICalls().callStaticVoid(jniEnv, jniMethods_.endPointClass, jniMethods_.onVMDeathMethod, jniArgs);
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            }
        }
        
        @Override
        public void spawnServer(String jdwpOptions, String additionalOptions, long isolate, long initialThreadId, long jdwpBridgeHandle, String metadataHashString, String metadataPath, boolean tracing) {
            try {
                JNIEnv jniEnv = JNIMethodScope.env();
                JValue jniArgs = StackValue.get(9, JValue.class);
                jniArgs.addressOf(0).setJObject(this.getHandle());
                jniArgs.addressOf(1).setJObject(JNIUtil.createHSString(jniEnv, jdwpOptions));
                jniArgs.addressOf(2).setJObject(JNIUtil.createHSString(jniEnv, additionalOptions));
                jniArgs.addressOf(3).setLong(isolate);
                jniArgs.addressOf(4).setLong(initialThreadId);
                jniArgs.addressOf(5).setLong(jdwpBridgeHandle);
                jniArgs.addressOf(6).setJObject(JNIUtil.createHSString(jniEnv, metadataHashString));
                jniArgs.addressOf(7).setJObject(JNIUtil.createHSString(jniEnv, metadataPath));
                jniArgs.addressOf(8).setBoolean(tracing);
                ForeignException.getJNICalls().callStaticVoid(jniEnv, jniMethods_.endPointClass, jniMethods_.spawnServerMethod, jniArgs);
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            }
        }
    }

    private static class EndPoint {

        private static final BinaryMarshaller<Throwable> throwableMarshaller;
        static  {
            JNIConfig config = JDWPJNIConfig.getInstance();
            throwableMarshaller = config.lookupMarshaller(Throwable.class);
        }

        @SuppressWarnings({"unused"})
        @JNIEntryPoint
        static void onEventAt(JDWPEventHandlerBridge receiverObject, long threadId, long classId, byte typeTag, long methodId, int bci, byte resultTag, long resultPrimitiveOrId, int eventKindFlags) {
            try {
                receiverObject.onEventAt(threadId, classId, typeTag, methodId, bci, resultTag, resultPrimitiveOrId, eventKindFlags);
            } catch (Throwable e) {
                throw ForeignException.forThrowable(e, throwableMarshaller);
            }
        }

        @SuppressWarnings({"unused"})
        @JNIEntryPoint
        static void onThreadDeath(JDWPEventHandlerBridge receiverObject, long threadId) {
            try {
                receiverObject.onThreadDeath(threadId);
            } catch (Throwable e) {
                throw ForeignException.forThrowable(e, throwableMarshaller);
            }
        }

        @SuppressWarnings({"unused"})
        @JNIEntryPoint
        static void onThreadStart(JDWPEventHandlerBridge receiverObject, long threadId) {
            try {
                receiverObject.onThreadStart(threadId);
            } catch (Throwable e) {
                throw ForeignException.forThrowable(e, throwableMarshaller);
            }
        }

        @SuppressWarnings({"unused"})
        @JNIEntryPoint
        static void onVMDeath(JDWPEventHandlerBridge receiverObject) {
            try {
                receiverObject.onVMDeath();
            } catch (Throwable e) {
                throw ForeignException.forThrowable(e, throwableMarshaller);
            }
        }

        @SuppressWarnings({"unused"})
        @JNIEntryPoint
        static void spawnServer(JDWPEventHandlerBridge receiverObject, String jdwpOptions, String additionalOptions, long isolate, long initialThreadId, long jdwpBridgeHandle, String metadataHashString, String metadataPath, boolean tracing) {
            try {
                receiverObject.spawnServer(jdwpOptions, additionalOptions, isolate, initialThreadId, jdwpBridgeHandle, metadataHashString, metadataPath, tracing);
            } catch (Throwable e) {
                throw ForeignException.forThrowable(e, throwableMarshaller);
            }
        }
    }
}
