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
import com.oracle.svm.core.configure.SerializationKey;

import java.io.IOException;

public class SerializationKeyConfiguration extends SerializationKey<String> implements JsonPrintable {
    public SerializationKeyConfiguration(String serializationTargetClass, String[] parameterTypes, String[] checkedExceptions, int modifiers, String targetConstructorClass) {
        super(serializationTargetClass, parameterTypes, checkedExceptions, modifiers, targetConstructorClass);
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        writer.newline().append('{');
        writer.newline();
        String typePrefix = "";
        writer.quote("name").append(":").quote(this.getSerializationTargetClass()).append(",").newline();
        writer.quote("parameterTypes").append(":").append('[');
        for (String parameterType : this.getParameterTypes()) {
            writer.append(typePrefix).quote(parameterType);
            typePrefix = ",";
        }
        writer.append(']').append(",").newline();

        typePrefix = "";
        writer.quote("checkedExceptions").append(":").append('[');
        for (String checkedException : this.getCheckedExceptions()) {
            writer.append(typePrefix).quote(checkedException);
            typePrefix = ",";
        }
        writer.append(']').append(",").newline();

        writer.quote("modifiers").append(':').append(Integer.toString(this.getModifiers())).append(",").newline();
        writer.quote("targetConstructorClass").append(':').quote(this.getTargetConstructorClass()).newline();
        writer.append('}');
    }
}
