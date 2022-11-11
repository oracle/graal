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

package com.oracle.svm.core.jfr.events;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.JfrNativeEventWriter;
import com.oracle.svm.core.jfr.JfrNativeEventWriterData;
import com.oracle.svm.core.jfr.JfrNativeEventWriterDataAccess;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.jfr.HasJfrSupport;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.StackValue;

public class ThreadParkEvent {
    public static void emit(long startTicks, Object obj, long timeout, long until) {
        if (HasJfrSupport.get()) {
            emit0(startTicks, obj, timeout, until);
        }
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static void emit0(long startTicks, Object obj, long timeout, long until) {
        if (SubstrateJVM.isRecording() && SubstrateJVM.get().isEnabled(JfrEvent.ThreadPark)) {
            Class<?> parkedClass = null;
            if (obj != null) {
                parkedClass = obj.getClass();
            }
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);
            JfrNativeEventWriter.beginSmallEvent(data, JfrEvent.ThreadPark);
            JfrNativeEventWriter.putLong(data, startTicks);
            JfrNativeEventWriter.putLong(data, JfrTicks.elapsedTicks() - startTicks);
            JfrNativeEventWriter.putEventThread(data);
            JfrNativeEventWriter.putLong(data, SubstrateJVM.get().getStackTraceId(JfrEvent.ThreadPark, 0));
            JfrNativeEventWriter.putClass(data, parkedClass);
            JfrNativeEventWriter.putLong(data, timeout);
            JfrNativeEventWriter.putLong(data, until);
            JfrNativeEventWriter.putLong(data, Word.objectToUntrackedPointer(obj).rawValue());
            JfrNativeEventWriter.endSmallEvent(data);
        }
    }
}
