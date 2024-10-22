/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector.objects;

import java.io.IOException;
import java.util.function.Supplier;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownMemberException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.tools.chromeinspector.InspectorExecutionContext;
import com.oracle.truffle.tools.chromeinspector.server.InspectorServerConnection;

/**
 * Implementation of Inspector module described at
 * https://nodejs.org/dist/latest-v11.x/docs/api/inspector.html .
 * <p>
 * This object is returned from <code>require("inspector")</code>.
 */
public final class Inspector extends AbstractInspectorObject {

    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();
    private static final String FIELD_CONSOLE = "console";
    private static final String FIELD_SESSION = "Session";
    private static final String METHOD_CLOSE = "close";
    private static final String METHOD_OPEN = "open";
    private static final String METHOD_URL = "url";
    private static final TruffleObject MEMBERS = new Members(new Object[]{
                    new FieldMember(FIELD_CONSOLE), new FieldMember(FIELD_SESSION), //
                    new MethodMember(METHOD_CLOSE), new MethodMember(METHOD_OPEN), new MethodMember(METHOD_URL)
    });

    private InspectorServerConnection connection;
    private final InspectorServerConnection.Open open;
    private final Console console;
    private final SessionClass sessionType;
    private final UndefinedProvider undefinedProvider;

    public Inspector(TruffleInstrument.Env env, InspectorServerConnection connection, InspectorServerConnection.Open open, Supplier<InspectorExecutionContext> contextSupplier) {
        this.undefinedProvider = new UndefinedProvider(env);
        this.connection = connection;
        this.open = open;
        this.console = new Console(connection, undefinedProvider);
        this.sessionType = new SessionClass(contextSupplier, undefinedProvider);
    }

    public static boolean isInstance(TruffleObject obj) {
        return obj instanceof Inspector;
    }

    @Override
    protected TruffleObject getMemberObjects() {
        return MEMBERS;
    }

    @Override
    protected boolean isField(String name) {
        switch (name) {
            case FIELD_CONSOLE:
            case FIELD_SESSION:
                return true;
            default:
                return false;
        }
    }

    @Override
    protected Object getFieldValueOrNull(String name) {
        switch (name) {
            case FIELD_CONSOLE:
                return console;
            case FIELD_SESSION:
                return sessionType;
            default:
                return null;
        }
    }

    @Override
    protected boolean isMethod(String name) {
        switch (name) {
            case METHOD_CLOSE:
            case METHOD_OPEN:
            case METHOD_URL:
                return true;
            default:
                return false;
        }
    }

    @Override
    protected Object invokeMethod(String name, Object member, Object[] arguments) throws UnknownMemberException {
        switch (name) {
            case METHOD_CLOSE:
                return methodClose();
            case METHOD_OPEN:
                return methodOpen(arguments);
            case METHOD_URL:
                return methodUrl();
            default:
                CompilerDirectives.transferToInterpreter();
                throw UnknownMemberException.create(member);
        }
    }

    @TruffleBoundary
    private Object methodClose() {
        if (connection != null) {
            try {
                connection.close();
            } catch (IOException ioex) {
                // The API does not throw errors.
            }
            connection = null;
        }
        return undefinedProvider.get();
    }

    @TruffleBoundary
    private Object methodOpen(Object[] arguments) {
        int port = -1;
        String host = null;
        boolean wait = false;
        if (arguments.length > 0) {
            try {
                if (INTEROP.fitsInInt(arguments[0])) {
                    port = INTEROP.asInt(arguments[0]);
                }
                if (arguments.length > 1) {
                    if (INTEROP.isString(arguments[1])) {
                        host = INTEROP.asString(arguments[1]);
                    }
                    if (arguments.length > 2) {
                        if (INTEROP.isBoolean(arguments[2])) {
                            wait = INTEROP.asBoolean(arguments[2]);
                        }
                    }
                }
            } catch (UnsupportedMessageException e) {
            }
        }
        InspectorServerConnection newConnection = open.open(port, host, wait);
        if (newConnection != null) {
            connection = newConnection;
            console.setConnection(newConnection);
        }
        return undefinedProvider.get();
    }

    private Object methodUrl() {
        if (connection != null) {
            return connection.getURL();
        } else {
            return undefinedProvider.get();
        }
    }

}
