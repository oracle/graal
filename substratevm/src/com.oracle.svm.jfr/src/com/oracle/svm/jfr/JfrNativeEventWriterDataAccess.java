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
package com.oracle.svm.jfr;

import com.oracle.svm.core.annotate.Uninterruptible;

/**
 * Helper class that holds methods related to {@link JfrNativeEventWriterData}.
 */
public final class JfrNativeEventWriterDataAccess {

    /**
     * Initialize the {@link JfrNativeEventWriterData data} so that it uses the given buffer.
     */
    @Uninterruptible(reason = "Accesses a JFR buffer", callerMustBe = true)
    public static void initialize(JfrNativeEventWriterData data, JfrBuffer buffer) {
        assert buffer.isNonNull();

        data.setJfrBuffer(buffer);
        data.setStartPos(buffer.getPos());
        data.setCurrentPos(buffer.getPos());
        data.setEndPos(JfrBufferAccess.getDataEnd(buffer));
    }

    /**
     * Initialize the {@link JfrNativeEventWriterData data} so that it uses the current thread's
     * native buffer.
     */
    @Uninterruptible(reason = "Accesses a JFR buffer", callerMustBe = true)
    public static void initializeThreadLocalNativeBuffer(JfrNativeEventWriterData data) {
        JfrThreadLocal jfrThreadLocal = (JfrThreadLocal) SubstrateJVM.getThreadLocal();
        JfrBuffer nativeBuffer = jfrThreadLocal.getNativeBuffer();
        initialize(data, nativeBuffer);
    }
}
