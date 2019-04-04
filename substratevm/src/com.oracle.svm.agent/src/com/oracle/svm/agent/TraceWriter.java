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

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TimeZone;

import com.oracle.svm.configure.json.JsonWriter;
import com.oracle.svm.core.util.VMError;

class TraceWriter implements Closeable {
    public static final TimeZone UTC_TIMEZONE = TimeZone.getTimeZone("UTC");

    /** Value to explicitly express {@code null} in a trace, instead of omitting the value. */
    public static final Object EXPLICIT_NULL = new Object();

    /** Value to express an unknown value, for example on failure to retrieve the value. */
    public static final Object UNKNOWN_VALUE = new String("\0");

    private final Object lock = new Object();
    private final BufferedWriter writer;
    private boolean open = true;

    TraceWriter(Path path) throws IOException {
        writer = Files.newBufferedWriter(path);
        JsonWriter json = new JsonWriter(writer);
        json.append('[').newline();
        json.append('{');
        json.quote("tracer").append(':').quote("meta").append(", ");
        json.quote("event").append(':').quote("initialization").append(", ");
        json.quote("version").append(':').quote("1");
        json.append('}');
        json.flush(); // avoid closing underlying stream
    }

    public void tracePhaseChange(String phase) {
        try {
            StringWriter strwriter = new StringWriter();
            try (JsonWriter json = new JsonWriter(strwriter)) {
                json.append('{');
                json.quote("tracer").append(':').quote("meta").append(", ");
                json.quote("event").append(':').quote("phase_change").append(", ");
                json.quote("phase").append(':').quote(phase);
                json.append('}');
            }
            traceEntry(strwriter.toString());
        } catch (IOException e) {
            throw VMError.shouldNotReachHere(e);
        }
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
     * @param args Arguments to the call, which may contain arrays (which can further contain
     */
    public void traceCall(String tracer, String function, Object clazz, Object declaringClass, Object callerClass, Object result, Object... args) {
        try {
            StringWriter strwriter = new StringWriter();
            try (JsonWriter json = new JsonWriter(strwriter)) {
                json.append('{').quote("tracer").append(':').quote(tracer);
                json.append(", ").quote("function").append(':').quote(function);
                if (clazz != null) {
                    json.append(", ").quote("class").append(':').quote(handleSpecialValue(clazz));
                }
                if (declaringClass != null) {
                    json.append(", ").quote("declaring_class").append(':').quote(handleSpecialValue(declaringClass));
                }
                if (callerClass != null) {
                    json.append(", ").quote("caller_class").append(':').quote(handleSpecialValue(callerClass));
                }
                if (result != null) {
                    json.append(", ").quote("result").append(':').quote(handleSpecialValue(result));
                }
                if (args != null && args.length > 0) {
                    json.append(", ").quote("args").append(":");
                    printArray(json, args);
                }
                json.append("}");
            }

            traceEntry(strwriter.toString());
        } catch (IOException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private void printArray(JsonWriter json, Object[] array) throws IOException {
        json.append('[');
        for (int i = 0; i < array.length; i++) {
            if (i > 0) {
                json.append(',');
            }
            Object obj = array[i];
            if (obj instanceof Object[]) {
                printArray(json, (Object[]) obj);
            } else {
                json.quote(array[i]);
            }
        }
        json.append(']');
    }

    private static Object handleSpecialValue(Object obj) {
        return (obj == EXPLICIT_NULL) ? null : obj;
    }

    private void traceEntry(String s) throws IOException {
        synchronized (lock) {
            if (open) { // late events on exit
                writer.write(",\n");
                writer.write(s);
            }
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            try {
                writer.write("\n]\n");
                writer.close();
            } catch (IOException ignored) {
            }
            open = false;
        }
    }
}
