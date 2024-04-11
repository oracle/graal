/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.visualizer.coordinator.impl;

import java.util.Objects;

import jdk.graal.compiler.graphio.parsing.model.GraphDocument;
import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames;
import jdk.graal.compiler.graphio.parsing.model.Properties;
import org.openide.util.lookup.ServiceProvider;

import jdk.graal.compiler.graphio.parsing.DocumentFactory;

/**
 * @author sdedic
 */
@ServiceProvider(service = DocumentFactory.class)
public class VmUuidSessionFactory implements DocumentFactory {
    private final SessionManagerImpl sessionManager;
    private GraphDocument lastDocument;

    public VmUuidSessionFactory(SessionManagerImpl sessionManager) {
        this.sessionManager = sessionManager;
    }

    public VmUuidSessionFactory() {
        this(SessionManagerImpl.getInstance());
    }

    @Override
    public synchronized GraphDocument documentFor(Object id, Properties props, Group data) {
        Properties merge = Properties.newProperties(props);
        if (data != null) {
            merge.add(data.getProperties());
            // remove some group-level stuff, if it exists
            merge.remove("type");
            merge.remove("graph");
        }
        String cmdLine = merge.getString(KnownPropertyNames.PROPNAME_CMDLINE, null);
        String jvmargs = merge.getString(KnownPropertyNames.PROPNAME_JVM_ARGS, "");
        String uuid = merge.getString(KnownPropertyNames.PROPNAME_VM_UUID, null);

        GraphDocument doc = null;

        if (uuid == null && cmdLine == null) {
            // the truffle group may be without properties; attach it to the last document seen.
            String n;
            n = data.getName();
            if (n != null && n.startsWith("Truffle::")) {
                doc = lastDocument;
            }
            if (doc == null) {
                doc = sessionManager.getSingleDocument();
            }
        } else {
            String cmdLineKey = cmdLine + ":" + jvmargs;
            GraphDocument candidate = null;

            for (GraphDocument gd : sessionManager.getAppendableSessions()) {
                if (uuid != null) {
                    String u = gd.getProperties().getString(KnownPropertyNames.PROPNAME_VM_UUID, null);
                    if (uuid.equalsIgnoreCase(u)) {
                        candidate = gd;
                        break;
                    }
                } else if (cmdLine != null) {
                    String c = gd.getProperties().getString(KnownPropertyNames.PROPNAME_CMDLINE, null) + ":" +
                            gd.getProperties().getString(KnownPropertyNames.PROPNAME_JVM_ARGS, "");
                    if (cmdLineKey.equals(c)) {
                        // get the last one.
                        candidate = gd;
                    }
                }
            }
            if (candidate != null) {
                lastDocument = candidate;
                return candidate;
            }
            doc = new ManagedSessionImpl(id, merge);
            if (doc.getName() == null) {
                doc.getProperties().setProperty(KnownPropertyNames.PROPNAME_NAME,
                        SessionManagerImpl.getInstance().getSessionDisplayName(doc, false));
            }
            lastDocument = doc;
        }
        sessionManager.addSession(doc);
        return doc;
    }
}
