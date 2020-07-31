/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Alibaba Group Holding Limited. All Rights Reserved.
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
package com.oracle.svm.configure.config;

import com.oracle.svm.configure.json.JsonPrintable;
import com.oracle.svm.configure.json.JsonWriter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class SerializationConfiguration implements JsonPrintable {

    private final ConcurrentHashMap.KeySetView<SerializationKeyConfiguration, Boolean> serializations = ConcurrentHashMap.newKeySet();

    public void add(String serializationTargetClass, String[] parameterTypes, String[] checkedExceptions, int modifiers, String targetConstructorClass) {
        SerializationKeyConfiguration key = new SerializationKeyConfiguration(serializationTargetClass, parameterTypes, checkedExceptions, modifiers, targetConstructorClass);
        add(key);
    }

    public void add(SerializationKeyConfiguration key) {
        serializations.add(key);
    }

    public boolean contains(String serializationTargetClass, String[] parameterTypes, String[] checkedExceptions, int modifiers, String targetConstructorClass) {
        SerializationKeyConfiguration key = new SerializationKeyConfiguration(serializationTargetClass, parameterTypes, checkedExceptions, modifiers, targetConstructorClass);
        return serializations.contains(key);
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        writer.append('[').indent();
        String prefix = "";
        for (SerializationKeyConfiguration skc : serializations) {
            writer.append(prefix);
            skc.printJson(writer);
            prefix = ",";
        }
        writer.unindent().newline();
        writer.append(']').newline();
    }
}
