/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.tools.chromeinspector.server.InspectorServerConnection;

/**
 * Implementation of Inspector.console module described at
 * https://nodejs.org/dist/latest-v11.x/docs/api/console.html .
 */
class Console extends AbstractInspectorObject {

    private static final String METHOD_DEBUG = "debug";
    private static final String METHOD_ERROR = "error";
    private static final String METHOD_INFO = "info";
    private static final String METHOD_LOG = "log";
    private static final String METHOD_WARN = "warn";
    private static final String METHOD_DIR = "dir";
    private static final String METHOD_DIRXML = "dirxml";
    private static final String METHOD_TABLE = "table";
    private static final String METHOD_TRACE = "trace";
    private static final String METHOD_GROUP = "group";
    private static final String METHOD_GROUP_COLLAPSED = "groupCollapsed";
    private static final String METHOD_GROUP_END = "groupEnd";
    private static final String METHOD_CLEAR = "clear";
    private static final String METHOD_COUNT = "count";
    private static final String METHOD_COUNT_RESET = "countReset";
    private static final String METHOD_ASSERT = "assert";
    private static final String METHOD_MARK_TIMELINE = "markTimeline";
    private static final String METHOD_PROFILE = "profile";
    private static final String METHOD_PROFILE_END = "profileEnd";
    private static final String METHOD_TIMELINE = "timeline";
    private static final String METHOD_TIMELINE_END = "timelineEnd";
    private static final String METHOD_TIME = "time";
    private static final String METHOD_TIME_END = "timeEnd";
    private static final String METHOD_TIME_STAMP = "timeStamp";
    private static final String[] METHOD_NAMES = new String[]{METHOD_DEBUG, METHOD_ERROR, METHOD_INFO, METHOD_LOG, METHOD_WARN,
                    METHOD_DIR, METHOD_DIRXML, METHOD_TABLE, METHOD_TRACE, METHOD_GROUP, METHOD_GROUP_COLLAPSED, METHOD_GROUP_END,
                    METHOD_CLEAR, METHOD_COUNT, METHOD_COUNT_RESET, METHOD_ASSERT, METHOD_MARK_TIMELINE, METHOD_PROFILE, METHOD_PROFILE_END,
                    METHOD_TIMELINE, METHOD_TIMELINE_END, METHOD_TIME, METHOD_TIME_END, METHOD_TIME_STAMP};
    private static final TruffleObject KEYS = new Keys();
    private static final Object UNKNOWN = new Object() {
        @Override
        public String toString() {
            return "unknown";
        }
    };

    private InspectorServerConnection connection;
    private final Map<Object, Long> time = new ConcurrentHashMap<>();

    Console(InspectorServerConnection connection) {
        this.connection = connection;
    }

    public static boolean isInstance(TruffleObject obj) {
        return obj instanceof Console;
    }

    void setConnection(InspectorServerConnection newConnection) {
        this.connection = newConnection;
    }

    @Override
    protected TruffleObject getMembers(boolean includeInternal) {
        return KEYS;
    }

    @Override
    protected boolean isField(String name) {
        return false;
    }

    @Override
    protected boolean isMethod(String name) {
        switch (name) {
            case METHOD_DEBUG:
            case METHOD_ERROR:
            case METHOD_INFO:
            case METHOD_LOG:
            case METHOD_WARN:
            case METHOD_DIR:
            case METHOD_DIRXML:
            case METHOD_TABLE:
            case METHOD_TRACE:
            case METHOD_GROUP:
            case METHOD_GROUP_COLLAPSED:
            case METHOD_GROUP_END:
            case METHOD_CLEAR:
            case METHOD_COUNT:
            case METHOD_COUNT_RESET:
            case METHOD_ASSERT:
            case METHOD_MARK_TIMELINE:
            case METHOD_PROFILE:
            case METHOD_PROFILE_END:
            case METHOD_TIMELINE:
            case METHOD_TIMELINE_END:
            case METHOD_TIME:
            case METHOD_TIME_END:
            case METHOD_TIME_STAMP:
                return true;
            default:
                return false;
        }
    }

    @Override
    protected Object getFieldValueOrNull(String name) {
        return null;
    }

    @Override
    @CompilerDirectives.TruffleBoundary
    protected Object invokeMember(String name, Object[] arguments) throws UnsupportedTypeException, UnknownIdentifierException, UnsupportedMessageException {
        Object arg;
        if (arguments.length < 1) {
            arg = UNKNOWN;
        } else {
            arg = arguments[0];
        }
        String type = name;
        switch (name) {
            case METHOD_DEBUG:
                break;
            case METHOD_ERROR:
                break;
            case METHOD_INFO:
                break;
            case METHOD_LOG:
                break;
            case METHOD_WARN:
                type = "warning";
                break;
            case METHOD_DIR:
                break;
            case METHOD_DIRXML:
                break;
            case METHOD_TABLE:
                break;
            case METHOD_TRACE:
                break;
            case METHOD_GROUP:
                type = "startGroup";
                break;
            case METHOD_GROUP_COLLAPSED:
                type = "startGroupCollapsed";
                break;
            case METHOD_GROUP_END:
                type = "endGroup";
                break;
            case METHOD_CLEAR:
                break;
            case METHOD_COUNT:
                break;
            case METHOD_COUNT_RESET:
                break;
            case METHOD_ASSERT:
                // Report assertion only when false
                if (isTrue(arguments[0])) {
                    return NullObject.INSTANCE;
                }
                if (arguments.length > 1) {
                    arg = arguments[1];
                } else {
                    arg = "console.assert";
                }
                break;
            case METHOD_MARK_TIMELINE:
                break;
            case METHOD_PROFILE:
                break;
            case METHOD_PROFILE_END:
                break;
            case METHOD_TIMELINE:
                break;
            case METHOD_TIMELINE_END:
                break;
            case METHOD_TIME:
                time.put(arg, System.nanoTime());
                return NullObject.INSTANCE;
            case METHOD_TIME_END:
                long t2 = System.nanoTime();
                Long t1 = time.remove(arg);
                String timer = arg.toString();
                if (t1 == null) {
                    arg = "Timer '" + timer + "' does not exist";
                    type = "warning";
                } else {
                    StringBuilder ts = new StringBuilder(timer);
                    ts.append(": ");
                    ts.append(Long.toString(t2 - t1));
                    ts.insert(ts.length() - 6, '.');
                    while (ts.charAt(ts.length() - 1) == '0') {
                        ts.deleteCharAt(ts.length() - 1);
                    }
                    ts.append("ms");
                    arg = ts.toString();
                }
                break;
            case METHOD_TIME_STAMP:
                break;
            default:
                throw UnknownIdentifierException.create(name);
        }
        if (connection == null) {
            throw new InspectorStateException("The inspector is not connected.");
        }
        connection.consoleAPICall(type, arg);
        return NullObject.INSTANCE;
    }

    private static boolean isTrue(Object obj) throws UnsupportedMessageException {
        InteropLibrary interop = InteropLibrary.getFactory().getUncached();
        if (interop.isBoolean(obj)) {
            return interop.asBoolean(obj);
        } else {
            return !interop.isNull(obj);
        }
    }

    static final class Keys extends AbstractInspectorArray {

        @Override
        int getArraySize() {
            return METHOD_NAMES.length;
        }

        @Override
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (index < 0 || index >= METHOD_NAMES.length) {
                CompilerDirectives.transferToInterpreter();
                throw InvalidArrayIndexException.create(index);
            }
            return METHOD_NAMES[(int) index];
        }
    }

}
