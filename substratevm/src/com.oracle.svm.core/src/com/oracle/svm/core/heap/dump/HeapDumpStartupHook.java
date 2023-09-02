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
package com.oracle.svm.core.heap.dump;

import java.io.IOException;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.log.Log;

import jdk.internal.misc.Signal;

public class HeapDumpStartupHook implements RuntimeSupport.Hook {
    @Override
    public void execute(boolean isFirstIsolate) {
        if (isFirstIsolate && SubstrateOptions.EnableSignalHandling.getValue()) {
            DumpHeapReport.install();
        }

        if (SubstrateOptions.HeapDumpOnOutOfMemoryError.getValue()) {
            HeapDumping.singleton().initializeDumpHeapOnOutOfMemoryError();
        }
    }
}

class DumpHeapReport implements Signal.Handler {
    static void install() {
        Signal.handle(new Signal("USR1"), new DumpHeapReport());
    }

    @Override
    public void handle(Signal arg0) {
        try {
            HeapDumping.singleton().dumpHeap(true);
        } catch (IOException e) {
            Log.log().string("IOException during dumpHeap: ").string(e.getMessage()).newline();
        }
    }
}
