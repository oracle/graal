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
package com.oracle.svm.core.genscavenge;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.jfr.JfrBuffer;
import com.oracle.svm.core.jfr.JfrEnabled;
import com.oracle.svm.core.jfr.JfrEvents;
import com.oracle.svm.core.jfr.JfrNativeEventWriter;
import com.oracle.svm.core.jfr.JfrNativeEventWriterData;
import com.oracle.svm.core.jfr.JfrNativeEventWriterDataAccess;
import com.oracle.svm.core.jfr.JfrThreadLocal;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.util.VMError;

class JfrGCEventSupport {
    public static final int MAX_PHASE_LEVEL = 4;
    private static int currentPhase;

    @Platforms(Platform.HOSTED_ONLY.class)
    JfrGCEventSupport() {
    }

    public static long startGCPhasePause() {
        if (!JfrEnabled.get()) {
            return 0;
        }
        pushPausePhaseLevel();
        return JfrTicks.elapsedTicks();
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public static void emitGCPhasePauseEvent(UnsignedWord gcEpoch, String name, long startTicks) {
        if (!JfrEnabled.get()) {
            return;
        }

        int level = popPausePhaseLevel();
        JfrEvents event = getGCPhasePauseEvent(level);
        if (SubstrateJVM.isRecording() && SubstrateJVM.get().isEnabled(event)) {
            long end = JfrTicks.elapsedTicks();
            JfrBuffer buffer = ((JfrThreadLocal) SubstrateJVM.getThreadLocal()).getNativeBuffer();
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initialize(data, buffer);

            JfrNativeEventWriter.beginEventWrite(data, false);
            JfrNativeEventWriter.putLong(data, event.getId());
            JfrNativeEventWriter.putLong(data, startTicks);
            JfrNativeEventWriter.putLong(data, end - startTicks);
            JfrNativeEventWriter.putEventThread(data);
            JfrNativeEventWriter.putLong(data, gcEpoch.rawValue());
            JfrNativeEventWriter.putString(data, name);
            JfrNativeEventWriter.endEventWrite(data, false);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static JfrEvents getGCPhasePauseEvent(int level) {
        switch (level) {
            case 0:
                return JfrEvents.GCPhasePauseEvent;
            case 1:
                return JfrEvents.GCPhasePauseLevel1Event;
            case 2:
                return JfrEvents.GCPhasePauseLevel2Event;
            case 3:
                return JfrEvents.GCPhasePauseLevel3Event;
            case 4:
                return JfrEvents.GCPhasePauseLevel4Event;
            default:
                throw VMError.shouldNotReachHere("At most " + MAX_PHASE_LEVEL + " levels");
        }
    }

    private static void pushPausePhaseLevel() {
        assert currentPhase < MAX_PHASE_LEVEL;
        currentPhase++;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int popPausePhaseLevel() {
        assert currentPhase > 0;
        currentPhase--;
        return currentPhase;
    }
}
