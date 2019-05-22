/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.agent;

import java.io.Closeable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public abstract class TraceWriter implements Closeable {
    /** Value to explicitly express {@code null} in a trace, instead of omitting the value. */
    public static final String EXPLICIT_NULL = new String("null");

    /** Value to express an unknown value, for example on failure to retrieve the value. */
    public static final String UNKNOWN_VALUE = new String("\0");

    static Object handleSpecialValue(Object obj) {
        if (obj == EXPLICIT_NULL) {
            return null;
        }
        if (obj instanceof Object[]) {
            Object[] array = (Object[]) obj;
            Object[] newArray = null;
            for (int i = 0; i < array.length; i++) {
                Object newValue = handleSpecialValue(array[i]);
                if (newValue != array[i]) {
                    if (newArray == null) {
                        newArray = Arrays.copyOf(array, array.length);
                    }
                    newArray[i] = newValue;
                }
            }
            return (newArray != null) ? newArray : array;
        }
        return obj;
    }

    void traceInitialization() {
        Map<String, Object> entry = new HashMap<>();
        entry.put("tracer", "meta");
        entry.put("event", "initialization");
        entry.put("version", "1");
        traceEntry(entry);
    }

    public void tracePhaseChange(String phase) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("tracer", "meta");
        entry.put("event", "phase_change");
        entry.put("phase", phase);
        traceEntry(entry);
    }

    /**
     * Trace a call to a function or method. {@link Object} arguments are represented as strings by
     * calling {@link Object#toString()} on them unless they are {@link #EXPLICIT_NULL},
     * {@link #UNKNOWN_VALUE}, {@link Boolean#TRUE}, {@link Boolean#FALSE}. {@code null} arguments
     * are omitted, except when in the {@code Object... args} array.
     *
     * @param tracer String identifying the tracing component. Required.
     * @param function The function or method that has been called. Required.
     * @param clazz The class to which {@code function} belongs.
     * @param declaringClass If the traced call resolves a member of {@code clazz}, this can be
     *            specified to provide the (super)class which actually declares that member.
     * @param callerClass The class on the call stack which performed the call.
     * @param result The result of the call.
     * @param args Arguments to the call, which may contain arrays (which can contain more arrays)
     */
    public void traceCall(String tracer, String function, Object clazz, Object declaringClass, Object callerClass, Object result, Object... args) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("tracer", tracer);
        entry.put("function", function);
        if (clazz != null) {
            entry.put("class", handleSpecialValue(clazz));
        }
        if (declaringClass != null) {
            entry.put("declaring_class", handleSpecialValue(declaringClass));
        }
        if (callerClass != null) {
            entry.put("caller_class", handleSpecialValue(callerClass));
        }
        if (result != null) {
            entry.put("result", handleSpecialValue(result));
        }
        if (args != null) {
            entry.put("args", handleSpecialValue(args));
        }
        traceEntry(entry);
    }

    abstract void traceEntry(Map<String, Object> entry);

    @Override
    public abstract void close();
}
