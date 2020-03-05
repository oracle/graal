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

package com.oracle.svm.core.jdk.jfr.recorder.service;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.svm.core.jdk.jfr.recorder.checkpoint.JfrCheckpointManager;
import com.oracle.svm.core.jdk.jfr.recorder.repository.JfrChunkRotation;
import com.oracle.svm.core.jdk.jfr.recorder.repository.JfrChunkWriter;
import com.oracle.svm.core.jdk.jfr.recorder.repository.JfrRepository;
import com.oracle.svm.core.jdk.jfr.recorder.service.JfrPostBox.JfrMsg;
import com.oracle.svm.core.jdk.jfr.recorder.storage.JfrStorage;
import com.oracle.svm.core.jdk.jfr.recorder.storage.operations.JfrContentOperations;
import com.oracle.svm.core.jdk.jfr.recorder.stringpool.JfrStringPool;
import com.oracle.svm.core.thread.JavaVMOperation;

public class JfrRecorderService {
    enum RecorderState {
        STOPPED, RUNNING
    }

    // JFR.TODO
    // Needs to be replaced with lock that supports identifying (and stopping)
    // recursive lock attempts with proper guarding of critical section.
    // Current usage is not safe
    private static final ReentrantLock rotationlock = new ReentrantLock();

    static volatile RecorderState recorderState = RecorderState.STOPPED;

    public static boolean isRecording() {
        return recorderState == RecorderState.RUNNING;
    }

    public static void start() {
        rotationlock.lock();

        try {
            assert (!isRecording());
            clear();
            openNewChunk(false);
            startRecorder();
            assert (isRecording());
        } finally {
            rotationlock.unlock();
        }
    }

    static void startRecorder() {
        assert (rotationlock.isHeldByCurrentThread());
        setRecorderState(RecorderState.STOPPED, RecorderState.RUNNING);
    }

    static void stop() {
        assert (rotationlock.isHeldByCurrentThread());
        setRecorderState(RecorderState.RUNNING, RecorderState.STOPPED);
    }

    private static void setRecorderState(RecorderState from, RecorderState to) {
        assert (from == recorderState);
        // OrderAccess::storestore();
        recorderState = to;
        assert (recorderState == to);
    }

    public static void rotate(int message) {
        if (rotationlock.isHeldByCurrentThread()) {
            // recursive call
            return;
        }
        rotationlock.lock();
        try {
            // JFR.TODO
            // vm error rotation
            if (JfrStorage.instance().getStorageControl().toDisk()) {
                chunkRotation();
            } else {
                inMemoryRotation();
            }

            if (JfrMsg.STOP.in(message)) {
                stop();
            }
        } finally {
            rotationlock.unlock();
        }
    }

    private static void openNewChunk(boolean vmError) {
        assert (rotationlock.isHeldByCurrentThread());
        JfrChunkRotation.onRotation();
        try {
            boolean validChunk = JfrRepository.getInstance().openChunk(vmError);
            JfrStorage.instance().getStorageControl().setToDisk(validChunk);
            if (validChunk) {
                JfrCheckpointManager.instance().writeStaticTypeSetAndThreads();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void clear() {
        assert (rotationlock.isHeldByCurrentThread());
        preSafepointClear();
        invokeSafepointClear();
        postSafepointClear();
    }

    private static void preSafepointClear() {
        JfrStorage.instance().clear();
        // JFR.TODO
        // _stack_trace_repository.clear();
    }

    private static void invokeSafepointClear() {
        JavaVMOperation.enqueueBlockingSafepoint("ClearJFR", () -> {
            JfrCheckpointManager.instance().beginEpochShift();
            JfrStorage.instance().clear();
            JfrRepository.getChunkWriter().setTimeStamp();
            // JFR.TODO
            // _stack_trace_repository.clear();
            JfrCheckpointManager.instance().endEpochShift();
        });
    }

    private static void postSafepointClear() {
        JfrStringPool.instance().clear();
        JfrCheckpointManager.instance().clear();
    }

    private static void inMemoryRotation() {
        assert (rotationlock.isHeldByCurrentThread());
        // currently running an in-memory recording
        assert (!JfrStorage.instance().getStorageControl().toDisk());
        openNewChunk(false);
        if (JfrRepository.getChunkWriter().isValid()) {
            JfrStorage.instance().write();
        }
    }

    private static void chunkRotation() {
        assert (rotationlock.isHeldByCurrentThread());
        finalizeCurrentChunk();
        openNewChunk(false);
    }

    private static void finalizeCurrentChunk() {
        assert (JfrRepository.getChunkWriter().isValid());
        write();
    }

    private static void write() {
        preSafepointWrite();
        invokeSafepointWrite();
        postSafepointWrite();
    }

    private static void preSafepointWrite() {
        JfrStorage.instance().write();

    }

    private static void invokeSafepointWrite() {
        JavaVMOperation.enqueueBlockingSafepoint("WriteJFR", () -> {
            JfrCheckpointManager.instance().beginEpochShift();
            JfrCheckpointManager.instance().onRotation();
            JfrStorage.instance().writeAtSafepoint();
            JfrRepository.getChunkWriter().setTimeStamp();
            JfrCheckpointManager.instance().endEpochShift();
        });
    }

    private static void postSafepointWrite() {
        assert (JfrRepository.getChunkWriter().isValid());
        if (JfrStringPool.instance().isModified()) {
            writeStringPool(JfrStringPool.instance(), JfrRepository.getChunkWriter());
        }
        JfrCheckpointManager.instance().writeTypeSet();
        writeMetadata(JfrRepository.getChunkWriter());
        try {
            JfrRepository.getInstance().closeChunk();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static int writeMetadata(JfrChunkWriter chunkWriter) {
        assert (chunkWriter.isValid());
        JfrContentOperations.MetadataEventContent me = new JfrContentOperations.MetadataEventContent(chunkWriter);
        JfrContentOperations.Write wm = new JfrContentOperations.Write(chunkWriter, me);
        wm.process();
        return wm.elements();
    }

    private static void writeStringPool(JfrStringPool stringPool, JfrChunkWriter chunkWriter) {
        stringPool.write(chunkWriter);
    }

    public static void processFullBuffers() {
        rotationlock.lock();
        try {
            if (JfrRepository.getChunkWriter().isValid()) {
                JfrStorage.instance().writeFull(JfrRepository.getChunkWriter());
            }
        } finally {
            rotationlock.unlock();
        }
    }

    public static void evaluateChunkSizeForRotation() {
        JfrChunkRotation.evaluate(JfrRepository.getChunkWriter());
    }

}
