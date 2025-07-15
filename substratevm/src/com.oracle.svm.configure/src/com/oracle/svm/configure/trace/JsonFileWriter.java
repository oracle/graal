/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure.trace;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

import jdk.graal.compiler.util.json.JsonPrintable;
import jdk.graal.compiler.util.json.JsonWriter;

public class JsonFileWriter implements Closeable {
    private final Object lock = new Object();
    private final BufferedWriter writer;
    private boolean open = true;
    private int written = 0;

    public JsonFileWriter(Path path) throws IOException {
        writer = Files.newBufferedWriter(path);
        JsonWriter json = new JsonWriter(writer);
        json.append('[').newline();
        json.flush(); // avoid close() on underlying stream
    }

    public void printObject(EconomicMap<String, Object> entry) {
        try {
            StringWriter str = new StringWriter();
            try (JsonWriter json = new JsonWriter(str)) {
                json.append('{');
                boolean first = true;
                MapCursor<String, Object> cursor = entry.getEntries();
                while (cursor.advance()) {
                    if (!first) {
                        json.append(", ");
                    }
                    json.quote(cursor.getKey()).append(':');
                    if (cursor.getValue() instanceof JsonPrintable value) {
                        value.printJson(json);
                    } else if (cursor.getValue() instanceof Object[]) {
                        printArray(json, (Object[]) cursor.getValue());
                    } else {
                        printValue(json, cursor.getValue());
                    }
                    first = false;
                }
                json.append('}');
            }
            writeEntry(str.toString());
        } catch (IOException e) {
            throw new RuntimeException("StringWriter should not throw IOExceptions", e);
        }
    }

    private static void printArray(JsonWriter json, Object[] array) throws IOException {
        json.append('[');
        for (int i = 0; i < array.length; i++) {
            if (i > 0) {
                json.append(',');
            }
            Object obj = array[i];
            if (obj instanceof Object[]) {
                printArray(json, (Object[]) obj);
            } else {
                printValue(json, array[i]);
            }
        }
        json.append(']');
    }

    private static void printValue(JsonWriter json, Object value) throws IOException {
        Object s = null;
        if (value instanceof byte[]) {
            s = Base64.getEncoder().encodeToString((byte[]) value);
        } else if (value != null) {
            s = value;
        }
        json.printValue(s);
    }

    private void writeEntry(String s) throws IOException {
        synchronized (lock) {
            if (open) { // late events on exit
                if (written > 0) {
                    writer.write(",\n");
                }
                writer.write(s);
                written++;
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
