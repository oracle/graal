/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2024, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core;

import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.heap.dump.HeapDumping;
import com.oracle.svm.core.nmt.NativeMemoryTracking;
import java.io.IOException;

import jdk.internal.misc.Signal;

public class Sigusr1Handler implements Signal.Handler {
    static boolean installed;

    /**
     * Depending on which features are included in the image, this method may be called multiple
     * times by the thread executing startup hooks.
     */
    public static void install() {
        if (!installed) {
            installed = true;
            Signal.handle(new Signal("USR1"), new Sigusr1Handler());
        }
    }

    @Override
    public void handle(Signal arg0) {
        if (VMInspectionOptions.hasNativeMemoryTrackingSupport()) {
            try {
                NativeMemoryTracking.singleton().dumpReport();
            } catch (IOException e) {
                Log.log().string("IOException during NMT report dump: ").string(e.getMessage()).newline();
            }
        }

        if (VMInspectionOptions.hasHeapDumpSupport()) {
            try {
                HeapDumping.singleton().dumpHeap(true);
            } catch (IOException e) {
                Log.log().string("IOException during dumpHeap: ").string(e.getMessage()).newline();
            }
        }

    }
}
