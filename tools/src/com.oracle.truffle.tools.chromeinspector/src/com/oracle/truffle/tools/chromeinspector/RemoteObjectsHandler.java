/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.debug.DebugValue;

import com.oracle.truffle.tools.chromeinspector.types.RemoteObject;

public final class RemoteObjectsHandler {

    private final Map<String, RemoteObject> remotesByIDs = new HashMap<>(100);
    private final Map<DebugValue, RemoteObject> remotesByValue = new HashMap<>(100);
    private final Map<String, DebugValue> customPreviewBodies = new HashMap<>();
    private final Map<String, DebugValue> customPreviewConfigs = new HashMap<>();
    private final InspectorExecutionContext context;

    RemoteObjectsHandler(InspectorExecutionContext context) {
        this.context = context;
    }

    public RemoteObject getRemote(DebugValue value) {
        RemoteObject remote;
        synchronized (remotesByIDs) {
            remote = remotesByValue.get(value);
            if (remote == null) {
                remote = new RemoteObject(value, false, context);
                remotesByValue.put(value, remote);
                remotesByIDs.put(remote.getId(), remote);
            }
        }
        return remote;
    }

    RemoteObject getRemote(String objectId) {
        synchronized (remotesByIDs) {
            return remotesByIDs.get(objectId);
        }
    }

    void register(RemoteObject remote) {
        synchronized (remotesByIDs) {
            remotesByIDs.put(remote.getId(), remote);
        }
    }

    void reset() {
        synchronized (remotesByIDs) {
            remotesByIDs.clear();
            remotesByValue.clear();
            customPreviewBodies.clear();
            customPreviewConfigs.clear();
        }
    }

    public void registerCustomPreviewBody(String id, DebugValue body) {
        synchronized (remotesByIDs) {
            customPreviewBodies.put(id, body);
        }
    }

    DebugValue getCustomPreviewBody(String id) {
        synchronized (remotesByIDs) {
            return customPreviewBodies.get(id);
        }
    }

    public void registerCustomPreviewConfig(String objectId, DebugValue config) {
        synchronized (remotesByIDs) {
            customPreviewConfigs.put(objectId, config);
        }
    }

    DebugValue getCustomPreviewConfig(String id) {
        synchronized (remotesByIDs) {
            return customPreviewConfigs.get(id);
        }
    }
}
