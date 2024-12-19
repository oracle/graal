/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.server.impl;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * When a thread is suspended at a safepoint, it can not resume itself. It needs a different thread
 * to resume it.
 */
final class ServerToResidentCallThread extends Thread {

    private final BlockingQueue<ResidentCall> callQueue = new LinkedBlockingQueue<>();

    private ServerToResidentCallThread() {
        setName(ServerToResidentCallThread.class.getName());
    }

    static ServerToResidentCallThread create() {
        ServerToResidentCallThread t = new ServerToResidentCallThread();
        t.setDaemon(true);
        t.start();
        return t;
    }

    void threadResume(long threadId) {
        callQueue.add(new ResidentCall(ResidentCall.Kind.THREAD_RESUME, threadId));
    }

    long[] vmSuspend(long[] threadIds) {
        ResidentCall call = new ResidentCall(ResidentCall.Kind.VM_SUSPEND, threadIds);
        call.completionFuture = new CompletableFuture<>();
        callQueue.add(call);
        try {
            call.completionFuture.get();
        } catch (ExecutionException | InterruptedException ex) {
            throw new RuntimeException(ex.getCause());
        }
        return call.ids;
    }

    @Override
    public void run() {
        while (true) {
            ResidentCall call;
            try {
                call = callQueue.take();
            } catch (InterruptedException ex) {
                break;
            }
            call.call();
        }
    }

    private static final class ResidentCall {

        enum Kind {
            THREAD_RESUME,
            VM_SUSPEND,
        }

        private final Kind kind;

        long id;
        long[] ids;
        CompletableFuture<ResidentCall> completionFuture;

        ResidentCall(Kind kind, long id) {
            this.kind = kind;
            this.id = id;
        }

        ResidentCall(Kind kind, long[] ids) {
            this.kind = kind;
            this.ids = ids;
        }

        public void call() {
            switch (kind) {
                case THREAD_RESUME -> ServerJDWP.BRIDGE.threadResume(id);
                case VM_SUSPEND -> ids = ServerJDWP.BRIDGE.vmSuspend(ids);
            }
            if (completionFuture != null) {
                completionFuture.complete(this);
            }
        }
    }
}
