/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jfr.events;

import java.util.Map;
import java.util.Properties;

import org.graalvm.nativeimage.StackValue;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.JfrEventWriteStatus;
import com.oracle.svm.core.jfr.JfrNativeEventWriter;
import com.oracle.svm.core.jfr.JfrNativeEventWriterData;
import com.oracle.svm.core.jfr.JfrNativeEventWriterDataAccess;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.SubstrateJVM;

import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Period;

@Name("EndChunkPeriodEvents")
@Period(value = "endChunk")
public class EndChunkNativePeriodicEvents extends Event {

    private static String formatOSInformation() {
        String name = System.getProperty("os.name");
        String ver = System.getProperty("os.version");
        String arch = System.getProperty("os.arch");
        return (name + " (" + ver + ") arch:" + arch);
    }

    public static void emit() {
        emitClassLoadingStatistics(Heap.getHeap().getClassCount());
        emitJVMInformation(JVMInformation.getJVMInfo());
        emitOSInformation(formatOSInformation());
        emitInitialEnvironmentVariables(getEnvironmentVariables());
        emitInitialSystemProperties(getSystemProperties());
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static void emitInitialEnvironmentVariables(StringEntry[] envs) {
        if (SubstrateJVM.isRecording() && SubstrateJVM.get().isEnabled(JfrEvent.InitialEnvironmentVariable)) {
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);
            boolean isLarge = SubstrateJVM.get().isLarge(JfrEvent.InitialEnvironmentVariable);
            for (StringEntry env : envs) {
                if (emitInitialEnvironmentVariable(data, env, isLarge) == JfrEventWriteStatus.RetryLarge) {
                    isLarge = true;
                    SubstrateJVM.get().setLarge(JfrEvent.InitialEnvironmentVariable, true);
                    emitInitialEnvironmentVariable(data, env, true);
                }
            }
        }
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static JfrEventWriteStatus emitInitialEnvironmentVariable(JfrNativeEventWriterData data, StringEntry env, boolean isLarge) {
        JfrNativeEventWriter.beginEvent(data, JfrEvent.InitialEnvironmentVariable, isLarge);
        JfrNativeEventWriter.putLong(data, JfrTicks.elapsedTicks());
        JfrNativeEventWriter.putString(data, env.key);
        JfrNativeEventWriter.putString(data, env.value);
        return JfrNativeEventWriter.endEvent(data, isLarge);
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static void emitInitialSystemProperties(StringEntry[] systemProperties) {
        if (SubstrateJVM.isRecording() && SubstrateJVM.get().isEnabled(JfrEvent.InitialSystemProperty)) {
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);
            boolean isLarge = SubstrateJVM.get().isLarge(JfrEvent.InitialSystemProperty);
            for (StringEntry systemProperty : systemProperties) {
                if (emitInitialSystemProperty(data, systemProperty, isLarge) == JfrEventWriteStatus.RetryLarge) {
                    isLarge = true;
                    SubstrateJVM.get().setLarge(JfrEvent.InitialSystemProperty, true);
                    emitInitialSystemProperty(data, systemProperty, true);
                }
            }
        }
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static JfrEventWriteStatus emitInitialSystemProperty(JfrNativeEventWriterData data, StringEntry systemProperty, boolean isLarge) {
        JfrNativeEventWriter.beginEvent(data, JfrEvent.InitialSystemProperty, isLarge);
        JfrNativeEventWriter.putLong(data, JfrTicks.elapsedTicks());
        JfrNativeEventWriter.putString(data, systemProperty.key);
        JfrNativeEventWriter.putString(data, systemProperty.value);
        return JfrNativeEventWriter.endEvent(data, isLarge);
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static void emitClassLoadingStatistics(long loadedClassCount) {
        if (SubstrateJVM.isRecording() && SubstrateJVM.get().isEnabled(JfrEvent.ClassLoadingStatistics)) {
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);

            JfrNativeEventWriter.beginSmallEvent(data, JfrEvent.ClassLoadingStatistics);
            JfrNativeEventWriter.putLong(data, JfrTicks.elapsedTicks());
            JfrNativeEventWriter.putLong(data, loadedClassCount);
            JfrNativeEventWriter.putLong(data, 0); /* unloadedClassCount */
            JfrNativeEventWriter.endSmallEvent(data);
        }
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static void emitJVMInformation(JVMInformation jvmInformation) {
        if (SubstrateJVM.isRecording() && SubstrateJVM.get().isEnabled(JfrEvent.JVMInformation)) {
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);

            boolean isLarge = SubstrateJVM.get().isLarge(JfrEvent.JVMInformation);
            if (emitJVMInformation0(data, jvmInformation, isLarge) == JfrEventWriteStatus.RetryLarge) {
                SubstrateJVM.get().setLarge(JfrEvent.JVMInformation, true);
                emitJVMInformation0(data, jvmInformation, true);
            }
        }
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static JfrEventWriteStatus emitJVMInformation0(JfrNativeEventWriterData data, JVMInformation jvmInformation, boolean isLarge) {
        JfrNativeEventWriter.beginEvent(data, JfrEvent.JVMInformation, isLarge);
        JfrNativeEventWriter.putLong(data, JfrTicks.elapsedTicks());
        JfrNativeEventWriter.putString(data, jvmInformation.getJvmName());
        JfrNativeEventWriter.putString(data, jvmInformation.getJvmVersion());
        JfrNativeEventWriter.putString(data, jvmInformation.getJvmArguments());
        JfrNativeEventWriter.putString(data, jvmInformation.getJvmFlags());
        JfrNativeEventWriter.putString(data, jvmInformation.getJavaArguments());
        JfrNativeEventWriter.putLong(data, jvmInformation.getJvmStartTime());
        JfrNativeEventWriter.putLong(data, jvmInformation.getJvmPid());
        return JfrNativeEventWriter.endEvent(data, isLarge);
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static void emitOSInformation(String osVersion) {
        if (SubstrateJVM.isRecording() && SubstrateJVM.get().isEnabled(JfrEvent.OSInformation)) {
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);

            boolean isLarge = SubstrateJVM.get().isLarge(JfrEvent.OSInformation);
            if (emitOSInformation0(data, osVersion, isLarge) == JfrEventWriteStatus.RetryLarge) {
                SubstrateJVM.get().setLarge(JfrEvent.OSInformation, true);
                emitOSInformation0(data, osVersion, true);
            }
        }
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static JfrEventWriteStatus emitOSInformation0(JfrNativeEventWriterData data, String osVersion, boolean isLarge) {
        JfrNativeEventWriter.beginEvent(data, JfrEvent.OSInformation, isLarge);
        JfrNativeEventWriter.putLong(data, JfrTicks.elapsedTicks());
        JfrNativeEventWriter.putString(data, osVersion);
        return JfrNativeEventWriter.endEvent(data, isLarge);
    }

    private static StringEntry[] getEnvironmentVariables() {
        Map<String, String> env = System.getenv();
        StringEntry[] result = new StringEntry[env.size()];

        int i = 0;
        for (Map.Entry<String, String> entry : env.entrySet()) {
            result[i] = new StringEntry(entry.getKey(), entry.getValue());
            i++;
        }

        return result;
    }

    public static StringEntry[] getSystemProperties() {
        Properties properties = System.getProperties();
        StringEntry[] result = new StringEntry[properties.size()];

        int i = 0;
        for (String key : properties.stringPropertyNames()) {
            result[i] = new StringEntry(key, properties.getProperty(key));
            i++;
        }

        return result;
    }

    private static class StringEntry {
        public final String key;
        public final String value;

        StringEntry(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
}
