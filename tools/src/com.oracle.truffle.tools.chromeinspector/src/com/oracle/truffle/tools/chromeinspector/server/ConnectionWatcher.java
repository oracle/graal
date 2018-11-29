/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector.server;

/**
 * Allows to wait for the close of the inspector protocol connection.
 */
public final class ConnectionWatcher {

    private volatile Boolean opened = null; // Initially not closed nor opened (no connection)
    private volatile boolean doWaitForClose = false; // Do not wait for close by default

    public boolean shouldWaitForClose() {
        return !isClosed() && doWaitForClose;
    }

    public synchronized void waitForClose() {
        while (!isClosed()) {
            try {
                wait();
            } catch (InterruptedException ex) {
                break;
            }
        }
    }

    void waitForOpen() {
        if (!isOpened()) {
            synchronized (this) {
                while (!isOpened()) {
                    try {
                        wait();
                    } catch (InterruptedException ex) {
                        break;
                    }
                }
            }
        }
    }

    /**
     * Call when it's necessary to keep the connection open until the client closes it. It will
     */
    public void setWaitForClose() {
        doWaitForClose = true;
    }

    public synchronized void notifyOpen() {
        opened = Boolean.TRUE;
        notifyAll();
    }

    public synchronized void notifyClosing() {
        opened = Boolean.FALSE;
        notifyAll();
    }

    private boolean isOpened() {
        return opened == Boolean.TRUE;
    }

    private boolean isClosed() {
        return opened == Boolean.FALSE;
    }
}
