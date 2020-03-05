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

package com.oracle.svm.core.jdk.jfr.recorder.repository;

import java.io.IOException;
import java.nio.file.Path;

import com.oracle.svm.core.jdk.jfr.Jfr;
import com.oracle.svm.core.jdk.jfr.recorder.service.JfrPostBox;
import com.oracle.svm.core.jdk.jfr.recorder.service.JfrPostBox.JfrMsg;

public final class JfrRepository {
    private Path path;
    private final JfrPostBox postBox;

    private static JfrRepository instance;
    private static JfrChunkWriter chunkWriter;

    private JfrRepository(JfrPostBox postBox) {
        this.path = null;
        this.postBox = postBox;
    }

    public boolean initialize() {
        assert (chunkWriter == null);
        chunkWriter = new JfrChunkWriter();
        return chunkWriter != null;
    }

    private void setPath(Path path) {
        this.path = path;
    }

    private void setChunkPath(Path path) {
        getChunkWriter().setPath(path);
    }

    public boolean openChunk(boolean vmError) throws IOException {
        if (vmError) {
            // JFR.TODO Implement JfrEmergencyDump
            // getChunkWriter().setPath(JfrEmergencyDump::build_dump_path(_path));
        }
        return getChunkWriter().open();
    }

    public long closeChunk() throws IOException {
        return getChunkWriter().close();
    }

    private void flushChunk() throws IOException {
        getChunkWriter().flush();
    }

    private void onVmError() {
        if (path == null) {
            // completed already
            return;
        }
        // TODO Implement JfrEmergencyDump
        // JfrEmergencyDump.onVmError(path);
    }

    public static JfrRepository create(JfrPostBox postBox) {
        assert (instance == null);
        instance = new JfrRepository(postBox);
        return instance;
    }

    public static JfrRepository getInstance() {
        return instance;
    }

    public static void destroy() {
        assert (chunkWriter != null);
        chunkWriter = null;
    }

    public static JfrChunkWriter getChunkWriter() {
        return chunkWriter;
    }

    public static void notifyOnNewChunkPath() {
        if (Jfr.isRecording()) {
            // rotations are synchronous, block until rotation completes
            getInstance().postBox.post(JfrMsg.ROTATE);
        }
    }

    public static void setInstancePath(Path path) {
        if (path != null) {
            getInstance().setPath(path);
        }
    }

    /**
     * Sets the file where data should be written.
     *
     * Recording  Previous  Current  Action
     * ==============================================
     *   true     null      null     Ignore, keep recording in-memory
     *   true     null      file1    Start disk recording
     *   true     file      null     Copy out metadata to disk and continue in-memory recording
     *   true     file1     file2    Copy out metadata and start with new File (file2)
     *   false     *        null     Ignore, but start recording to memory
     *   false     *        file     Ignore, but start recording to disk
     */
    public static void setInstanceChunkPath(Path path) {
        if (path == null && !getChunkWriter().isValid()) {
            // new output is NULL and current output is NULL
            return;
        }
        getInstance().setChunkPath(path);
        notifyOnNewChunkPath();
    }

    public static void markChunkFinal() {
        getChunkWriter().markChunkFinal();
    }

    public static void flush(Thread jt) {
        if (!Jfr.isRecording()) {
            return;
        }
        if (!getChunkWriter().isValid()) {
            return;
        }
        getInstance().postBox.post(JfrMsg.FLUSHPOINT);
    }

    public static long getCurrentChunkStartNanos() {
        return getChunkWriter().getCurrentChunkStartNanos();
    }

}
