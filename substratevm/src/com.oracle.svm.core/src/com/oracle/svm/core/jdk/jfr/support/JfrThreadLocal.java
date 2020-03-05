/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jdk.jfr.support;

import com.oracle.svm.core.jdk.jfr.recorder.storage.JfrBuffer;
import com.oracle.svm.core.jdk.jfr.recorder.storage.JfrStorage;
import com.oracle.svm.core.thread.JavaThreads;

public class JfrThreadLocal {

    private JfrBuffer buffer;
    private Object eventWriter = null;
    private boolean excluded = false;
    private boolean notified = false;

    private int dataLost = 0;

    void release(Thread t) {
        if (hasBuffer()) {
            JfrStorage.releaseThreadLocal(buffer(), t);
            this.buffer = null;
        }

        // JFR.TODO
        // Release stack frames
        if (this.eventWriter != null) {
            this.eventWriter = null;
        }

    }

    public static void excludeThread(Thread t) {
        assert (t != null);
        JfrThreadLocal jtl = JavaThreads.getThreadLocal(t);
        jtl.excluded = true;
        jtl.release(t);
    }

    private JfrBuffer acquireBuffer(boolean excluded) {
        JfrBuffer b = JfrStorage.acquireThreadLocalBuffer(Thread.currentThread());
        if (b != null && excluded) {
            b.setExcluded();
        }
        this.buffer = b;
        return this.buffer;
    }

    public JfrBuffer buffer() {
        return this.buffer != null ? this.buffer : acquireBuffer(this.excluded);
    }

    public boolean hasBuffer() {
        return this.buffer != null;
    }

    public void setBuffer(JfrBuffer buffer) {
        this.buffer = buffer;
    }

    public boolean isExcluded() {
        return this.excluded;
    }

    public int addDataLost(int value) {
        this.dataLost += value;
        return this.dataLost;
    }

    // JFR.TODO support for large buffers will need this
    public JfrBuffer shelvedBuffer() {
        return null;
    }

    public Object getEventWriter() {
        return this.eventWriter;
    }

    public void setEventWriter(Object ew) {
        this.eventWriter = ew;
    }

    public boolean hasEventWriter() {
        return this.eventWriter != null;
    }

    public void setNotified(boolean notified) {
        this.notified = notified;
    }

    public boolean isNotified() {
        return this.notified;
    }

}
