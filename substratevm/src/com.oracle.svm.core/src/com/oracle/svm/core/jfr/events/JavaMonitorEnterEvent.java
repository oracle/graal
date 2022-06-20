/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Red Hat Inc. All rights reserved.
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

package src.com.oracle.svm.core.src.com.oracle.svm.core.jfr.events;


import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.JfrNativeEventWriter;
import com.oracle.svm.core.jfr.JfrNativeEventWriterData;
import com.oracle.svm.core.jfr.JfrNativeEventWriterDataAccess;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.SubstrateJVM;


public class JavaMonitorEnterEvent {

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public static void emit(Object obj, org.graalvm.nativeimage.IsolateThread previousOwner,long startTicks) {
        emit(obj, com.oracle.svm.core.jfr.SubstrateJVM.get().getThreadId(previousOwner), startTicks);
    }
    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public static void emit(Object obj, long previousOwner, long startTicks) {
        if (SubstrateJVM.isRecording() && SubstrateJVM.get().isEnabled(JfrEvent.JavaMonitorEnter)) {
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);

            JfrNativeEventWriter.beginSmallEvent(data, JfrEvent.JavaMonitorEnter);
            JfrNativeEventWriter.putLong(data, startTicks);
            JfrNativeEventWriter.putLong(data, JfrTicks.elapsedTicks() - startTicks);
            JfrNativeEventWriter.putEventThread(data);
            JfrNativeEventWriter.putLong(data, SubstrateJVM.get().getStackTraceId(JfrEvent.ThreadStart.getId(), 0));
            JfrNativeEventWriter.putClass(data, obj.getClass());
            JfrNativeEventWriter.putLong(data, previousOwner);
            JfrNativeEventWriter.putLong(data, org.graalvm.compiler.word.Word.objectToUntrackedPointer(obj).rawValue());
            JfrNativeEventWriter.endSmallEvent(data);

        }
    }}
