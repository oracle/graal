/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jfr;

import jdk.graal.compiler.word.Word;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.Uninterruptible;

/**
 * Helper class that holds methods related to {@link JfrNativeEventWriterData}.
 */
public final class JfrNativeEventWriterDataAccess {

    private JfrNativeEventWriterDataAccess() {
    }

    /**
     * Initialize the {@link JfrNativeEventWriterData data} so that it uses the given buffer.
     */
    @Uninterruptible(reason = "Accesses a JFR buffer", callerMustBe = true)
    public static void initialize(JfrNativeEventWriterData data, JfrBuffer buffer) {
        if (buffer.isNonNull()) {
            assert JfrBufferAccess.verify(buffer);
            data.setJfrBuffer(buffer);
            data.setStartPos(buffer.getCommittedPos());
            data.setCurrentPos(buffer.getCommittedPos());
            data.setEndPos(JfrBufferAccess.getDataEnd(buffer));
        } else {
            data.setJfrBuffer(Word.nullPointer());
            data.setStartPos(Word.nullPointer());
            data.setCurrentPos(Word.nullPointer());
            data.setEndPos(Word.nullPointer());
        }
    }

    /**
     * Initialize the {@link JfrNativeEventWriterData data} so that it uses the current thread's
     * native buffer.
     */
    @Uninterruptible(reason = "Accesses a JFR buffer", callerMustBe = true)
    public static void initializeThreadLocalNativeBuffer(JfrNativeEventWriterData data) {
        JfrBuffer nativeBuffer = SubstrateJVM.getThreadLocal().getNativeBuffer();
        initialize(data, nativeBuffer);
    }

    @Uninterruptible(reason = "Accesses a native JFR buffer.", callerMustBe = true)
    public static boolean verify(JfrNativeEventWriterData data) {
        if (data.isNull() || !JfrBufferAccess.verify(data.getJfrBuffer())) {
            return false;
        }

        JfrBuffer buffer = data.getJfrBuffer();
        Pointer dataStart = JfrBufferAccess.getDataStart(buffer);
        Pointer dataEnd = JfrBufferAccess.getDataEnd(buffer);

        return data.getStartPos() == buffer.getCommittedPos() &&
                        (data.getEndPos() == dataEnd || data.getEndPos().isNull()) &&
                        data.getCurrentPos().aboveOrEqual(dataStart) && data.getCurrentPos().belowOrEqual(dataEnd) && data.getCurrentPos().aboveOrEqual(data.getStartPos());
    }
}
