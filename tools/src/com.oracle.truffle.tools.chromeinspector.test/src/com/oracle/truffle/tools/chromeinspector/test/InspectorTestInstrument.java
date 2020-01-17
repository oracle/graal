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
package com.oracle.truffle.tools.chromeinspector.test;

import java.io.PrintWriter;
import java.net.URI;
import java.util.List;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.test.ReflectionUtils;

import com.oracle.truffle.tools.chromeinspector.InspectorExecutionContext;
import com.oracle.truffle.tools.chromeinspector.server.ConnectionWatcher;
import com.oracle.truffle.tools.chromeinspector.server.InspectServerSession;

@TruffleInstrument.Registration(id = InspectorTestInstrument.ID, services = InspectSessionInfoProvider.class)
public final class InspectorTestInstrument extends TruffleInstrument {

    public static final String ID = "InspectorTestInstrument";

    @Override
    protected void onCreate(final Env env) {
        env.registerService(new InspectSessionInfoProvider() {
            @Override
            public InspectSessionInfo getSessionInfo(final boolean suspend, final boolean inspectInternal, final boolean inspectInitialization, final List<URI> sourcePath) {
                return new InspectSessionInfo() {

                    private InspectServerSession iss;
                    private InspectorExecutionContext context;
                    private ConnectionWatcher connectionWatcher;
                    private long id;

                    InspectSessionInfo init() {
                        this.context = new InspectorExecutionContext("test", inspectInternal, inspectInitialization, env, sourcePath, new PrintWriter(env.err(), true));
                        this.connectionWatcher = new ConnectionWatcher();
                        this.iss = InspectServerSession.create(context, suspend, connectionWatcher);
                        this.id = context.getId();
                        // Fake connection open
                        ReflectionUtils.invoke(connectionWatcher, "notifyOpen");
                        return this;
                    }

                    @Override
                    public InspectServerSession getInspectServerSession() {
                        return iss;
                    }

                    @Override
                    public InspectorExecutionContext getInspectorContext() {
                        return context;
                    }

                    @Override
                    public ConnectionWatcher getConnectionWatcher() {
                        return connectionWatcher;
                    }

                    @Override
                    public long getId() {
                        return id;
                    }
                }.init();
            }
        });
    }

}

interface InspectSessionInfoProvider {
    InspectSessionInfo getSessionInfo(boolean suspend, boolean inspectInternal, boolean inspectInitialization, List<URI> sourcePath);
}

interface InspectSessionInfo {
    InspectServerSession getInspectServerSession();

    InspectorExecutionContext getInspectorContext();

    ConnectionWatcher getConnectionWatcher();

    long getId();
}
