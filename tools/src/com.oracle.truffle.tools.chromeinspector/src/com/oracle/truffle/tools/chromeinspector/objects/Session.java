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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.tools.chromeinspector.InspectorExecutionContext;
import com.oracle.truffle.tools.chromeinspector.commands.Command;
import com.oracle.truffle.tools.chromeinspector.commands.Params;
import com.oracle.truffle.tools.chromeinspector.server.ConnectionWatcher;
import com.oracle.truffle.tools.chromeinspector.server.InspectServerSession;
import com.oracle.truffle.tools.chromeinspector.server.JSONMessageListener;
import com.oracle.truffle.tools.utils.json.JSONObject;

/**
 * Implementation of Inspector.Session module described at
 * https://nodejs.org/dist/latest-v11.x/docs/api/inspector.html#inspector_class_inspector_session .
 * It extends EventEmitter.
 */
class Session extends AbstractInspectorObject {

    private static final String METHOD_CONNECT = "connect";
    private static final String METHOD_DISCONNECT = "disconnect";
    private static final String METHOD_EVENT_NAMES = "eventNames";
    private static final String METHOD_EMIT = "emit";
    private static final String METHOD_ADD_LISTENER = "addListener";
    private static final String METHOD_ON = "on";
    private static final String METHOD_ONCE = "once";
    private static final String METHOD_OFF = "off";
    private static final String METHOD_PREPEND_LISTENER = "prependListener";
    private static final String METHOD_PREPEND_ONCE_LISTENER = "prependOnceListener";
    private static final String METHOD_REMOVE_LISTENER = "removeListener";
    private static final String METHOD_REMOVE_ALL_LISTENERS = "removeAllListeners";
    private static final String METHOD_LISTENERS = "listeners";
    private static final String METHOD_LISTENER_COUNT = "listenerCount";
    private static final String METHOD_POST = "post";
    private static final String[] METHOD_NAMES = new String[]{METHOD_CONNECT, METHOD_DISCONNECT,
                    METHOD_EVENT_NAMES, METHOD_EMIT, METHOD_ADD_LISTENER, METHOD_ON, METHOD_ONCE,
                    METHOD_OFF, METHOD_PREPEND_LISTENER, METHOD_PREPEND_ONCE_LISTENER,
                    METHOD_REMOVE_LISTENER, METHOD_REMOVE_ALL_LISTENERS, METHOD_LISTENERS,
                    METHOD_LISTENER_COUNT, METHOD_POST};
    private static final TruffleObject KEYS = new Keys(METHOD_NAMES);

    private final AtomicLong cmdId = new AtomicLong(1);
    private final Supplier<InspectorExecutionContext> contextSupplier;
    private InspectServerSession iss;
    private Listeners listeners;

    Session(Supplier<InspectorExecutionContext> contextSupplier) {
        this.contextSupplier = contextSupplier;
    }

    public static boolean isInstance(TruffleObject obj) {
        return obj instanceof Session;
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
    protected Object getFieldValueOrNull(String name) {
        return null;
    }

    @Override
    protected boolean isMethod(String name) {
        switch (name) {
            case METHOD_CONNECT:
            case METHOD_DISCONNECT:
            case METHOD_EVENT_NAMES:
            case METHOD_EMIT:
            case METHOD_ADD_LISTENER:
            case METHOD_ON:
            case METHOD_ONCE:
            case METHOD_OFF:
            case METHOD_PREPEND_LISTENER:
            case METHOD_PREPEND_ONCE_LISTENER:
            case METHOD_REMOVE_LISTENER:
            case METHOD_REMOVE_ALL_LISTENERS:
            case METHOD_LISTENERS:
            case METHOD_LISTENER_COUNT:
            case METHOD_POST:
                return true;
            default:
                return false;
        }
    }

    @Override
    @CompilerDirectives.TruffleBoundary
    protected Object invokeMember(String name, Object[] arguments) throws ArityException, UnsupportedTypeException, UnknownIdentifierException, UnsupportedMessageException {
        switch (name) {
            case METHOD_CONNECT:
                return connect();
            case METHOD_DISCONNECT:
                return disconnect();
            case METHOD_EVENT_NAMES:
                return getListeners().getEventNames();
            case METHOD_EMIT:
                return emit(arguments);
            case METHOD_ADD_LISTENER:
                return addListener(arguments, false);
            case METHOD_ON:
                return addListener(arguments, false);
            case METHOD_ONCE:
                return addOnceListener(arguments, false);
            case METHOD_OFF:
                return removeListener(arguments);
            case METHOD_PREPEND_LISTENER:
                return addListener(arguments, true);
            case METHOD_PREPEND_ONCE_LISTENER:
                return addOnceListener(arguments, true);
            case METHOD_REMOVE_LISTENER:
                return removeListener(arguments);
            case METHOD_REMOVE_ALL_LISTENERS:
                return removeAllListeners(arguments);
            case METHOD_LISTENERS:
                return listeners(arguments);
            case METHOD_LISTENER_COUNT:
                return listenerCount(arguments);
            case METHOD_POST:
                return post(arguments);
            default:
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.create(name);
        }
    }

    private Object connect() {
        if (iss != null) {
            throw new InspectorStateException("The inspector session is already connected");
        }
        InspectorExecutionContext execContext = contextSupplier.get();
        iss = InspectServerSession.create(execContext, false, new ConnectionWatcher());
        iss.setJSONMessageListener(getListeners());
        execContext.setSynchronous(true);
        // Enable the Runtime by default
        iss.sendCommand(new Command("{\"id\":0,\"method\":\"Runtime.enable\"}"));
        return NullObject.INSTANCE;
    }

    private Object disconnect() {
        if (iss != null) {
            iss.sendClose();
            iss = null;
        }
        return NullObject.INSTANCE;
    }

    private Object addListener(Object[] arguments, boolean prepend) throws ArityException {
        requireListenerArguments(arguments);
        getListeners().addListener(arguments[0], (TruffleObject) arguments[1], prepend);
        return this;
    }

    private Object addOnceListener(Object[] arguments, boolean prepend) throws ArityException {
        requireListenerArguments(arguments);
        getListeners().addOnceListener(arguments[0], (TruffleObject) arguments[1], prepend);
        return this;
    }

    private Object removeListener(Object[] arguments) throws ArityException {
        requireListenerArguments(arguments);
        getListeners().removeListener(arguments[0], (TruffleObject) arguments[1]);
        return this;
    }

    private static Object getEventName(Object[] arguments) {
        Object eventName = null;
        if (arguments.length > 0) {
            eventName = arguments[0];
        }
        return eventName;
    }

    private Object removeAllListeners(Object[] arguments) {
        Object eventName = getEventName(arguments);
        getListeners().removeAll(eventName);
        return this;
    }

    private Object listeners(Object[] arguments) {
        Object eventName = getEventName(arguments);
        return getListeners().listeners(eventName);
    }

    private Object listenerCount(Object[] arguments) {
        Object eventName = getEventName(arguments);
        return getListeners().listenerCount(eventName);
    }

    private Object emit(Object[] arguments) throws ArityException, UnsupportedTypeException, UnsupportedMessageException {
        if (arguments.length < 1) {
            throw ArityException.create(1, arguments.length);
        }
        Object eventName = arguments[0];
        Object[] listenerArgs = new Object[arguments.length - 1];
        System.arraycopy(arguments, 1, listenerArgs, 0, listenerArgs.length);
        return getListeners().emit(eventName, listenerArgs);
    }

    private Object post(Object[] arguments) throws ArityException, UnsupportedTypeException {
        if (arguments.length < 1) {
            throw ArityException.create(1, arguments.length);
        }
        if (!(arguments[0] instanceof String)) {
            throw UnsupportedTypeException.create(new Object[]{arguments[0]});
        }
        String method = (String) arguments[0];
        TruffleObject params = null;
        TruffleObject callback = null;
        if (arguments.length >= 2) {
            if (!(arguments[1] instanceof TruffleObject)) {
                throw UnsupportedTypeException.create(new Object[]{arguments[1]});
            }
            TruffleObject to = (TruffleObject) arguments[1];
            if (InteropLibrary.getFactory().getUncached().isExecutable(to)) {
                callback = to;
            } else {
                params = to;
            }
            if (callback == null && arguments.length >= 3) {
                if (!(arguments[2] instanceof TruffleObject)) {
                    throw UnsupportedTypeException.create(new Object[]{arguments[2]});
                }
                callback = (TruffleObject) arguments[2];
            }
        }
        post(method, params, callback);
        return NullObject.INSTANCE;
    }

    private void post(String method, TruffleObject paramsObject, TruffleObject callback) {
        long id = cmdId.getAndIncrement();
        Params params = null;
        if (paramsObject != null) {
            params = new Params(TruffleObject2JSON.fromObject(paramsObject));
        }
        if (callback != null) {
            getListeners().addCallback(id, callback);
        }
        if (iss == null) {
            throw new InspectorStateException("The inspector session is not connected.");
        }
        iss.sendCommand(new Command(id, method, params));
    }

    private static boolean requireListenerArguments(Object[] arguments) throws ArityException {
        if (arguments.length < 2) {
            throw ArityException.create(2, arguments.length);
        }
        if (!InteropLibrary.getFactory().getUncached().isExecutable(arguments[1])) {
            throw new IllegalArgumentException("The \"listener\" argument must be of type Function");
        }
        return true;
    }

    private Listeners getListeners() {
        Listeners l = listeners;
        if (l == null) {
            l = listeners = new Listeners();
        }
        return l;
    }

    static final class Listeners implements JSONMessageListener {

        private static final String EVENT_INSPECTOR = "inspectorNotification";

        private final Map<String, TruffleObject[]> listenersMap = new ConcurrentHashMap<>();
        private final Set<Object> eventNames = new LinkedHashSet<>();
        private final Map<Long, TruffleObject> callbacksMap = new ConcurrentHashMap<>();

        private void addOnceListener(Object eventName, TruffleObject listener, boolean prepend) {
            addListener(eventName, new AutoremoveListener(this, eventName, listener), prepend);
        }

        private synchronized void addListener(Object eventName, TruffleObject listener, boolean prepend) {
            CompilerAsserts.neverPartOfCompilation();
            String eventNameStr = eventName.toString();
            TruffleObject[] ls = listenersMap.get(eventNameStr);
            if (ls == null) {
                listenersMap.put(eventNameStr, new TruffleObject[]{listener});
            } else {
                int l = ls.length;
                TruffleObject[] nls;
                if (prepend) {
                    nls = new TruffleObject[l + 1];
                    System.arraycopy(ls, 0, nls, 1, l);
                    nls[0] = listener;
                } else {
                    nls = Arrays.copyOf(ls, l + 1);
                    nls[l] = listener;
                }
                listenersMap.put(eventNameStr, nls);
            }
            eventNames.add(eventName);
        }

        @CompilerDirectives.TruffleBoundary
        private synchronized void removeListener(Object eventName, TruffleObject listener) {
            String eventNameStr = eventName.toString();
            TruffleObject[] ls = listenersMap.get(eventNameStr);
            if (ls == null) {
                return;
            } else {
                TruffleObject[] nls = ls;
                for (int i = 0; i < ls.length; i++) {
                    if (listener == ls[i]) {
                        if (ls.length == 1) {
                            nls = null;
                        } else {
                            nls = new TruffleObject[ls.length - 1];
                            System.arraycopy(ls, 0, nls, 0, i);
                            System.arraycopy(ls, i + 1, nls, i, nls.length - i);
                        }
                        break;
                    }
                }
                if (nls == null) {
                    listenersMap.remove(eventNameStr);
                } else if (nls != ls) {
                    listenersMap.put(eventNameStr, nls);
                }
            }
        }

        private void removeAll(Object eventName) {
            if (eventName != null) {
                listenersMap.remove(eventName.toString());
                eventNames.remove(eventName);
            } else {
                listenersMap.clear();
                eventNames.clear();
            }
        }

        private Object listeners(Object eventName) {
            if (eventName != null) {
                TruffleObject[] ls = listenersMap.get(eventName.toString());
                if (ls == null) {
                    ls = new TruffleObject[]{};
                }
                return new JavaTruffleArray(ls);
            } else {
                List<TruffleObject> allListeners = new ArrayList<>();
                for (TruffleObject[] ls : listenersMap.values()) {
                    for (int i = 0; i < ls.length; i++) {
                        allListeners.add(ls[i]);
                    }
                }
                return new JavaTruffleArray(allListeners.toArray());
            }
        }

        private Object listenerCount(Object eventName) {
            if (eventName != null) {
                TruffleObject[] ls = listenersMap.get(eventName.toString());
                if (ls == null) {
                    return 0;
                } else {
                    return ls.length;
                }
            } else {
                int count = 0;
                for (TruffleObject[] ls : listenersMap.values()) {
                    count += ls.length;
                }
                return count;
            }
        }

        Object getEventNames() {
            return new JavaTruffleArray(eventNames.toArray());
        }

        void addCallback(long id, TruffleObject callback) {
            callbacksMap.put(id, callback);
        }

        @Override
        public void onMessage(JSONObject message) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            String method = message.optString("method", null);
            if (method != null) {
                TruffleObject[] ls = listenersMap.get(method);
                TruffleObject event = null;
                if (ls != null) {
                    event = new JSONTruffleObject(message);
                    notify(ls, event);
                }
                ls = listenersMap.get(EVENT_INSPECTOR);
                if (ls != null) {
                    if (event == null) {
                        event = new JSONTruffleObject(message);
                    }
                    notify(ls, event);
                }
            } else {
                long id = message.optLong("id", -1);
                TruffleObject callback;
                if (id >= 0 && (callback = callbacksMap.remove(id)) != null) {
                    Object[] arguments = new Object[2];
                    JSONObject error = message.optJSONObject("error");
                    if (error != null) {
                        arguments[0] = new JSONTruffleObject(error);
                        arguments[1] = NullObject.INSTANCE;
                    } else {
                        JSONObject result = message.optJSONObject("result");
                        arguments[0] = NullObject.INSTANCE;
                        arguments[1] = result != null ? new JSONTruffleObject(result) : NullObject.INSTANCE;
                    }
                    try {
                        InteropLibrary.getFactory().getUncached().execute(callback, arguments);
                    } catch (UnsupportedTypeException ex) {
                        throw UnsupportedTypeException.create(ex.getSuppliedValues());
                    } catch (ArityException ex) {
                        throw ArityException.create(ex.getExpectedArity(), ex.getActualArity());
                    } catch (UnsupportedMessageException ex) {
                        throw UnsupportedMessageException.create();
                    }
                }
            }
        }

        private static void notify(TruffleObject[] ls, TruffleObject event) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            for (TruffleObject listener : ls) {
                try {
                    InteropLibrary.getFactory().getUncached().execute(listener, event);
                } catch (UnsupportedTypeException ex) {
                    throw UnsupportedTypeException.create(ex.getSuppliedValues());
                } catch (ArityException ex) {
                    throw ArityException.create(ex.getExpectedArity(), ex.getActualArity());
                } catch (UnsupportedMessageException ex) {
                    throw UnsupportedMessageException.create();
                }
            }
        }

        public Object emit(Object eventName, Object[] listenerArgs) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            TruffleObject[] ls = listenersMap.get(eventName.toString());
            if (ls == null) {
                return false;
            } else {
                for (TruffleObject listener : ls) {
                    try {
                        InteropLibrary.getFactory().getUncached().execute(listener, listenerArgs);
                    } catch (UnsupportedTypeException ex) {
                        throw UnsupportedTypeException.create(ex.getSuppliedValues());
                    } catch (ArityException ex) {
                        throw ArityException.create(ex.getExpectedArity(), ex.getActualArity());
                    } catch (UnsupportedMessageException ex) {
                        throw UnsupportedMessageException.create();
                    }
                }
                return true;
            }
        }

        @ExportLibrary(InteropLibrary.class)
        static class AutoremoveListener implements TruffleObject {

            private final Listeners listeners;
            private final Object eventName;
            final TruffleObject listener;

            AutoremoveListener(Listeners listeners, Object eventName, TruffleObject listener) {
                this.listeners = listeners;
                this.eventName = eventName;
                this.listener = listener;
            }

            @ExportMessage
            boolean isExecutable() {
                return true;
            }

            @ExportMessage
            final Object execute(Object[] arguments, @CachedLibrary("this.listener") InteropLibrary library) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
                listeners.removeListener(eventName, this);
                try {
                    return library.execute(listener, arguments);
                } catch (ArityException ex) {
                    CompilerDirectives.transferToInterpreter();
                    throw ArityException.create(ex.getExpectedArity(), ex.getActualArity());
                } catch (UnsupportedMessageException ex) {
                    CompilerDirectives.transferToInterpreter();
                    throw UnsupportedMessageException.create();
                } catch (UnsupportedTypeException ex) {
                    CompilerDirectives.transferToInterpreter();
                    throw UnsupportedTypeException.create(ex.getSuppliedValues());
                }
            }
        }
    }
}
