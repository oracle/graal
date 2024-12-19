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
package com.oracle.svm.jdwp.server.impl;

import com.oracle.svm.jdwp.bridge.JDWP;
import com.oracle.svm.jdwp.bridge.Packet;
import com.oracle.svm.jdwp.server.api.ConnectionController;
import com.oracle.svm.jdwp.server.api.VMEventListener;

public final class DebuggerController {

    private final long initialThreadId;
    private final ConnectionController connectionController;
    private final JDWPContext context;
    private final EventFilters eventFilters;
    private final VMEventListener eventListener;
    private final RequestedJDWPEvents requestedJDWPEvents;
    private final JDWP serverJDWPImpl;

    public DebuggerController(long initialThreadId, ConnectionController connectionController) {
        this.initialThreadId = initialThreadId;
        this.connectionController = connectionController;
        this.context = new JDWPContext();
        this.eventListener = new VMEventListenerImpl();
        this.eventFilters = new EventFilters();
        this.requestedJDWPEvents = new RequestedJDWPEvents(this);
        this.serverJDWPImpl = new ServerJDWP(this);
    }

    void init() {
        ((VMEventListenerImpl) eventListener).activate(initialThreadId, context);
    }

    public VMEventListener getEventListener() {
        return eventListener;
    }

    JDWPContext getContext() {
        return context;
    }

    public EventFilters getEventFilters() {
        return eventFilters;
    }

    RequestedJDWPEvents getRequestedJDWPEvents() {
        return requestedJDWPEvents;
    }

    public JDWP getServerJDWP() {
        return serverJDWPImpl;
    }

    @SuppressWarnings("static-method")
    void clearBreakpoints() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    void disposeDebugger(Packet replyPacket) {
        eventListener.setConnection(null);
        connectionController.dispose(replyPacket);
        eventListener.disposeAllRequests();
        context.getThreadsCollector().releaseAllThreadsAndDispose();
        connectionController.restart();
    }

    void disposeConnection(Packet replyPacket) {
        connectionController.dispose(replyPacket);
    }
}
