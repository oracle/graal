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

import com.oracle.svm.core.jdk.jfr.Jfr;
import com.oracle.svm.core.jdk.jfr.logging.JfrLogger;
import com.oracle.svm.core.jdk.jfr.recorder.service.JfrPostBox.JfrMsg;

public class JfrRecorderThread {

    private static JfrPostBox postBox;

    public static boolean start(JfrPostBox postBox, Thread currentThread) {

        assert (postBox != null);

        JfrRecorderThread.postBox = postBox;

        /*
         * Create recorder thread running JfrRecorderThreadLoop.recorderThreadEntry()
         * under the system thread group with the system loader
         */
        ThreadGroup group = Thread.currentThread().getThreadGroup().getParent();
        if (!(group.getName().equals("system"))) {
            group = new ThreadGroup("jfr");
        }
        Thread t = new Thread(group, JfrRecorderThreadLoop::recorderThreadEntry);
        // JFR.WORKAROUND: Set to daemon thread so JVM doesn't hang waiting for this thread
        // to shutdown. This should be checked again when the overall system lifecycle
        // is complete as it should shutdown cleanly as well
        t.setDaemon(true);
        t.start();
        Jfr.excludeThread(t);
        return true;
    }

    public static JfrPostBox getPostBox() {
        return JfrRecorderThread.postBox;
    }
}

class JfrRecorderThreadLoop {

    static void recorderThreadEntry() {
        // JFR.TODO
        // Handle all messages appropriately
        JfrPostBox postBox = JfrRecorderThread.getPostBox();

        postBox.lock.lock();
        boolean done = false;
        while (!done) {
            if (postBox.isEmpty()) {
                try {
                    postBox.processing.await();
                } catch (InterruptedException e) {
                    // JFR.TODO
                    // What should we do here?
                    break;
                }
            }
            int messages = postBox.collect();
            postBox.lock.unlock();

            if (JfrMsg.FULLBUFFER.in(messages) || JfrMsg.ROTATE.in(messages) || JfrMsg.STOP.in(messages)) {
                JfrRecorderService.processFullBuffers();
            }

            JfrRecorderService.evaluateChunkSizeForRotation();

            if (JfrMsg.DEADBUFFER.in(messages)) {
                // JFR.TODO
            }

            if (JfrMsg.START.in(messages)) {
                JfrRecorderService.start();
            } else if (JfrMsg.ROTATE.in(messages) || JfrMsg.STOP.in(messages)) {
                JfrRecorderService.rotate(messages);
            } else if (JfrMsg.FLUSHPOINT.in(messages)) {
                JfrLogger.logTrace("JfrPostBox FLUSHPOINT");
            }

            postBox.lock.lock();
            postBox.notifyWaiters();

            if (JfrMsg.SHUTDOWN.in(messages)) {
                done = true;
            }
        }

        // assert (!JfrLocks.messageLock.ownedBySelf());

        postBox.notifyCollectionStop();
        // JfrRecorderThread.onRecorderThreadExit();
        postBox.lock.unlock();
    }
}
