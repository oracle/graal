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

package com.oracle.svm.core.jdk.jfr.recorder;

import com.oracle.svm.core.jdk.jfr.JfrOptions;
import com.oracle.svm.core.jdk.jfr.recorder.checkpoint.JfrCheckpointManager;
import com.oracle.svm.core.jdk.jfr.recorder.repository.JfrRepository;
import com.oracle.svm.core.jdk.jfr.recorder.service.JfrPostBox;
import com.oracle.svm.core.jdk.jfr.recorder.service.JfrPostBox.JfrMsg;
import com.oracle.svm.core.jdk.jfr.recorder.service.JfrRecorderService;
import com.oracle.svm.core.jdk.jfr.recorder.service.JfrRecorderThread;
import com.oracle.svm.core.jdk.jfr.recorder.storage.JfrStorage;
import com.oracle.svm.core.jdk.jfr.recorder.stringpool.JfrStringPool;

public final class JfrRecorder {
    private static JfrPostBox postBox = null;
    private static JfrRepository repository = null;
    private static JfrStorage storage = null;
    private static JfrCheckpointManager checkpointManager = null;
    private static JfrStringPool stringPool = null;

    private static boolean created = false;
    private static boolean enabled = false;

    private JfrRecorder() {
    }

    public static boolean isDisabled() {
        // JFR.TODO
        // Return whether or not -XX:-FlightRecorder has been set
        return false;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    private static boolean enable() {
        assert (!enabled);
        // JFR.TODO
        // if (!FlightRecorder) {
        // FLAG_SET_MGMT(FlightRecorder, true);
        // }
        enabled = true;
        assert (enabled);

        return enabled;
    }

    public static boolean isCreated() {
        return created;
    }

    public static boolean create(boolean simulateFailure) {
        assert (!isDisabled());
        assert (!isCreated());

        if (!isEnabled()) {
            enable();
        }

        if (!JfrOptions.initialize()) {
            return false;
        }

        if (!createComponents() || simulateFailure) {
            destroyComponents();
            return false;
        }

        if (!createRecorderThread()) {
            destroyComponents();
            return false;
        }

        created = true;

        return true;
    }

    private static boolean createComponents() {
        if (!createPostBox()) {
            return false;
        }
        if (!createChunkRepository()) {
            return false;
        }
        if (!createStorage()) {
            return false;
        }
        if (!createCheckpointManager()) {
            return false;
        }
        if (!createStringPool()) {
            return false;
        }
        return true;
    }

    private static boolean createStringPool() {
        assert (stringPool == null);
        assert (repository != null);
        stringPool = JfrStringPool.create();
        return stringPool != null && stringPool.initialize();
    }

    private static boolean createCheckpointManager() {
        assert (checkpointManager == null);
        assert (repository != null);
        checkpointManager = JfrCheckpointManager.create(JfrRepository.getChunkWriter());
        return checkpointManager != null && checkpointManager.initialize();
    }

    private static boolean createStorage() {
        assert (repository != null);
        assert (postBox != null);
        storage = JfrStorage.create(JfrRepository.getChunkWriter(), postBox);
        return storage != null && storage.initialize();
    }

    private static boolean createChunkRepository() {
        assert (repository == null);
        assert (postBox != null);
        repository = JfrRepository.create(postBox);
        return repository != null && repository.initialize();
    }

    private static boolean createPostBox() {
        assert (postBox == null);
        postBox = JfrPostBox.create();
        return postBox != null;
    }

    private static void destroyComponents() {
        // JFR.TODO
    }

    private static boolean createRecorderThread() {
        return JfrRecorderThread.start(postBox, Thread.currentThread());
    }

    public static boolean isRecording() {
        return JfrRecorderService.isRecording();
    }

    public static void startRecording() {
        postBox.post(JfrMsg.START);
    }

    public static void destroy() {
        assert (isCreated());
        postBox.post(JfrMsg.SHUTDOWN);
    }

    public static void stopRecording() {
        postBox.post(JfrMsg.STOP);
    }
}
