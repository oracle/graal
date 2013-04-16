/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.server;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.hotspot.logging.*;

/**
 * A collection of java.lang.reflect proxies that communicate over a socket connection.
 * 
 * Calling a method sends the method name and the parameters through the socket. Afterwards this
 * class waits for a result. While waiting for a result three types of objects can arrive through
 * the socket: a method invocation, a method result or an exception. Method invocation can thus be
 * recursive.
 */
public class InvocationSocket {

    private static final boolean DEBUG = false;
    private static final boolean COUNT_CALLS = false;

    private static final HashSet<String> cachedMethodNames = new HashSet<>();
    private static final HashSet<String> forbiddenMethodNames = new HashSet<>();

    static {
        cachedMethodNames.add("name");
        cachedMethodNames.add("kind");
        cachedMethodNames.add("isResolved");
        cachedMethodNames.add("getCompilerToVM");
        cachedMethodNames.add("exactType");
        cachedMethodNames.add("isInitialized");
        forbiddenMethodNames.add("javaClass");
    }

    private final ObjectOutputStream output;
    private final ObjectInputStream input;

    private final Map<String, Integer> counts = new HashMap<>();

    public InvocationSocket(ObjectOutputStream output, ObjectInputStream input) {
        this.output = output;
        this.input = input;

        if (COUNT_CALLS) {
            Runtime.getRuntime().addShutdownHook(new Thread() {

                @Override
                public void run() {
                    SortedMap<Integer, String> sorted = new TreeMap<>();
                    for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                        sorted.put(entry.getValue(), entry.getKey());
                    }
                    for (Map.Entry<Integer, String> entry : sorted.entrySet()) {
                        TTY.println(entry.getKey() + ": " + entry.getValue());
                    }
                }
            });
        }
    }

    /**
     * Represents one invocation of a method that is transferred via the socket connection.
     * 
     */
    private static class Invocation implements Serializable {

        private static final long serialVersionUID = -799162779226626066L;

        public Object receiver;
        public String methodName;
        public Object[] args;

        public Invocation(Object receiver, String methodName, Object[] args) {
            this.receiver = receiver;
            this.methodName = methodName;
            this.args = args;
        }
    }

    /**
     * Represents the result of an invocation that is transferred via the socket connection.
     * 
     */
    private static class Result implements Serializable {

        private static final long serialVersionUID = -7496058356272415814L;

        public Object result;

        public Result(Object result) {
            this.result = result;
        }
    }

    private void incCount(String name, Object[] args) {
        if (COUNT_CALLS) {
            String nameAndArgCount = name + (args == null ? 0 : args.length);
            if (counts.get(nameAndArgCount) != null) {
                counts.put(nameAndArgCount, counts.get(nameAndArgCount) + 1);
            } else {
                counts.put(nameAndArgCount, 1);
            }
        }
    }

    /**
     * Each instance of this class handles remote invocations for one instance of a Remote class. It
     * will forward all interface methods to the other end of the socket and cache the results of
     * calls to certain methods.
     * 
     */
    public class Handler implements InvocationHandler {

        private final Object receiver;
        private final HashMap<String, Object> cache = new HashMap<>();

        public Handler(Object receiver) {
            this.receiver = receiver;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // only interface methods can be transferred, java.lang.Object methods
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(receiver, args);
            }
            String methodName = method.getName();
            // check if the result of this zero-arg method was cached
            if (args == null || args.length == 0) {
                if (cache.containsKey(methodName)) {
                    return cache.get(methodName);
                }
            }
            if (forbiddenMethodNames.contains(methodName)) {
                throw new IllegalAccessException(methodName + " not allowed");
            }
            Object result = null;
            try {
                if (DEBUG) {
                    Logger.startScope("invoking remote " + methodName);
                }
                incCount(methodName, args);

                output.writeObject(new Invocation(receiver, methodName, args));
                output.flush();
                result = waitForResult(false);

                // result caching for selected methods
                if ((args == null || args.length == 0) && cachedMethodNames.contains(methodName)) {
                    cache.put(methodName, result);
                }
                return result;
            } catch (Throwable t) {
                t.printStackTrace();
                throw t;
            } finally {
                if (DEBUG) {
                    Logger.endScope(" = " + result);
                }
            }
        }
    }

    /**
     * Waits for the result of a remote method invocation. Invocations that should be executed in
     * this VM might arrive while waiting for the result, and these invocations will be executed
     * before again waiting fort he result.
     */
    @SuppressWarnings("unused")
    public Object waitForResult(boolean eofExpected) throws IOException, ClassNotFoundException {
        while (true) {
            Object in;
            try {
                in = input.readObject();
            } catch (EOFException e) {
                if (eofExpected) {
                    return null;
                }
                throw e;
            }
            if (in instanceof Result) {
                return ((Result) in).result;
            } else if (in instanceof RuntimeException) {
                throw (RuntimeException) in;
            } else if (in instanceof Throwable) {
                throw new RuntimeException((Throwable) in);
            }

            Invocation invoke = (Invocation) in;
            Method method = null;
            for (Class<?> clazz = invoke.receiver.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
                for (Method m : clazz.getDeclaredMethods()) {
                    if (invoke.methodName.equals(m.getName())) {
                        method = m;
                        break;
                    }
                }
            }
            if (method == null) {
                Exception e = new UnsupportedOperationException("unknown method " + invoke.methodName);
                e.printStackTrace();
                output.writeObject(e);
                output.flush();
            } else {
                Object result = null;
                try {
                    if (invoke.args == null) {
                        if (DEBUG) {
                            Logger.startScope("invoking local " + invoke.methodName);
                        }
                        result = method.invoke(invoke.receiver);
                    } else {
                        if (Logger.ENABLED && DEBUG) {
                            StringBuilder str = new StringBuilder();
                            str.append("invoking local " + invoke.methodName + "(");
                            for (int i = 0; i < invoke.args.length; i++) {
                                str.append(i == 0 ? "" : ", ");
                                str.append(Logger.pretty(invoke.args[i]));
                            }
                            str.append(")");
                            Logger.startScope(str.toString());
                        }
                        result = method.invoke(invoke.receiver, invoke.args);
                    }
                    result = new Result(result);
                } catch (IllegalArgumentException e) {
                    TTY.println("error while invoking " + invoke.methodName);
                    e.getCause().printStackTrace();
                    result = e.getCause();
                } catch (InvocationTargetException e) {
                    TTY.println("error while invoking " + invoke.methodName);
                    e.getCause().printStackTrace();
                    result = e.getCause();
                } catch (IllegalAccessException e) {
                    TTY.println("error while invoking " + invoke.methodName);
                    e.getCause().printStackTrace();
                    result = e.getCause();
                } finally {
                    if (DEBUG) {
                        if (result instanceof Result) {
                            Logger.endScope(" = " + ((Result) result).result);
                        } else {
                            Logger.endScope(" = " + result);
                        }
                    }
                }
                output.writeObject(result);
                output.flush();
            }
        }
    }

    /**
     * Sends a result without invoking a method, used by CompilationServer startup code.
     */
    public void sendResult(Object obj) throws IOException {
        output.writeObject(new Result(obj));
        output.flush();
    }
    // CheckStyle: resume system..print check
}
