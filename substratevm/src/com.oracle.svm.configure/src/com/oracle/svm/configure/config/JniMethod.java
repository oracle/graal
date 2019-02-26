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
package com.oracle.svm.configure.config;

import java.io.IOException;
import java.util.Objects;

import com.oracle.svm.configure.json.JsonPrintable;
import com.oracle.svm.configure.json.JsonWriter;

public class JniMethod implements JsonPrintable {
    private final String name;
    private final String signature;

    public JniMethod(String name, String signature) {
        this.name = name;
        this.signature = signature;
    }

    public String getName() {
        return name;
    }

    public String getSignature() {
        return signature;
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        writer.append('{');
        writer.quote("name").append(':').quote(name).append(',');
        writer.quote("parameterTypes").append(":[");
        char prefix = ' ';
        for (String type : SignatureParser.getParameterTypes(signature)) {
            writer.append(prefix).quote(type);
            prefix = ',';
        }
        writer.append("] }");
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, signature);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj != this && obj instanceof JniMethod) {
            JniMethod other = (JniMethod) obj;
            return (name.equals(other.name) && signature.equals(other.signature));
        }
        return (obj == this);
    }
}
