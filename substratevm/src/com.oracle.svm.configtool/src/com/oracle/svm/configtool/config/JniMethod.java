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
package com.oracle.svm.configtool.config;

import java.io.IOException;
import java.util.Objects;

import com.oracle.svm.configtool.json.JsonPrintable;
import com.oracle.svm.configtool.json.JsonWriter;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaUtil;

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
        writer.append("\"name\":\"").append(name).append("\",");
        writer.append("\"parameterTypes\":");
        printJsonParameterTypes(writer);
        writer.append('}');
    }

    private void printJsonParameterTypes(JsonWriter writer) throws IOException {
        if (signature.charAt(0) != '(') {
            throw new IllegalArgumentException("Invalid signature, expected '(':" + signature);
        }
        writer.append('[');
        char prefix = ' ';
        int position = 1;
        int arrayDimensions = 0;
        while (signature.charAt(position) != ')') {
            String type = null;
            if (signature.charAt(position) == '[') {
                arrayDimensions++;
            } else if (signature.charAt(position) == 'L') {
                int end = signature.indexOf(';', position + 1);
                type = MetaUtil.internalNameToJava(signature.substring(position, end + 1), true, false);
                position = end;
            } else {
                type = JavaKind.fromPrimitiveOrVoidTypeChar(signature.charAt(position)).toString();
            }
            position++;
            if (type != null) {
                writer.append(prefix).append('"').append(type);
                while (arrayDimensions > 0) {
                    writer.append("[]");
                    arrayDimensions--;
                }
                writer.append('"');
                prefix = ',';
            }
        }
        // ignore return type
        if (arrayDimensions > 0) {
            throw new IllegalArgumentException("Invalid array in signature: " + signature);
        }
        writer.append(" ]");
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
