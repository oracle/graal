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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.api.debug.DebugValue;

import com.oracle.truffle.tools.chromeinspector.types.RemoteObject;

public final class RemoteObjectsHandler {

    private final Map<String, RemoteObject> remotesByIDs = new HashMap<>(100);
    private final Map<DebugValue, RemoteObject> remotesByValue = new HashMap<>(100);
    private final Map<String, DebugValue> customPreviewBodies = new HashMap<>();
    private final Map<String, DebugValue> customPreviewConfigs = new HashMap<>();
    private final Map<String, Set<String>> objectGroups = new HashMap<>();
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
                if (remote.getId() != null) {
                    remotesByIDs.put(remote.getId(), remote);
                }
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
        register(remote, null);
    }

    void register(RemoteObject remote, String objectGroup) {
        if (remote.getId() != null) {
            synchronized (remotesByIDs) {
                remotesByIDs.put(remote.getId(), remote);
                if (objectGroup != null) {
                    Set<String> group = objectGroups.get(objectGroup);
                    if (group == null) {
                        group = new HashSet<>();
                        objectGroups.put(objectGroup, group);
                    }
                    group.add(remote.getId());
                }
            }
        }
    }

    String getObjectGroupOf(String objectId) {
        synchronized (remotesByIDs) {
            for (Map.Entry<String, Set<String>> groupEntry : objectGroups.entrySet()) {
                if (groupEntry.getValue().contains(objectId)) {
                    return groupEntry.getKey();
                }
            }
        }
        return null;
    }

    void releaseObject(String objectId) {
        synchronized (remotesByIDs) {
            remotesByIDs.remove(objectId);
        }
    }

    void releaseObjectGroup(String objectGroup) {
        synchronized (remotesByIDs) {
            Set<String> group = objectGroups.remove(objectGroup);
            if (group != null) {
                for (String objectId : group) {
                    remotesByIDs.remove(objectId);
                }
            }
        }
    }

    // For tests only
    public Set<String> getRegisteredIDs() {
        Set<String> ids = new HashSet<>();
        synchronized (remotesByIDs) {
            ids.addAll(remotesByIDs.keySet());
        }
        return ids;
    }

    void reset() {
        synchronized (remotesByIDs) {
            remotesByValue.clear();
            customPreviewBodies.clear();
            customPreviewConfigs.clear();
            if (objectGroups.isEmpty()) { // no groupped objects
                remotesByIDs.clear();
            } else { // some groupped objects, remove all that do not belong to a group.
                Set<String> grouppedIds;
                if (objectGroups.size() == 1) {
                    grouppedIds = objectGroups.values().iterator().next();
                } else {
                    grouppedIds = new HashSet<>();
                    for (Set<String> group : objectGroups.values()) {
                        grouppedIds.addAll(group);
                    }
                }
                remotesByIDs.keySet().retainAll(grouppedIds);
            }
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
