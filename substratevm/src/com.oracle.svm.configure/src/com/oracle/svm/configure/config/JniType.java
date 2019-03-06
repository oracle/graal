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
import java.util.Comparator;

import com.oracle.svm.configure.json.JsonPrintable;
import com.oracle.svm.configure.json.JsonWriter;

public class JniType implements JsonPrintable {
    private final String qualifiedName;

    private MatchSet<String> fields;
    private MatchSet<JniMethod> methods;

    public JniType(String qualifiedName) {
        assert qualifiedName.indexOf('/') == -1 : "Illegal class name format: " + qualifiedName;
        this.qualifiedName = qualifiedName;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public MatchSet<String> getFields() {
        if (fields == null) {
            fields = MatchSet.create(Comparator.naturalOrder(), (String s, JsonWriter w) -> w.append('{').quote("name").append(':').quote(s).append('}'));
        }
        return fields;
    }

    public MatchSet<JniMethod> getMethods() {
        if (methods == null) {
            methods = MatchSet.create(Comparator.comparing(JniMethod::getName).thenComparing(JniMethod::getSignature));
        }
        return methods;
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        writer.append('{').indent().newline();
        writer.quote("name").append(':').quote(qualifiedName);
        if (fields != null) {
            writer.append(',').newline().quote("fields").append(':');
            fields.printJson(writer);
        }
        if (methods != null) {
            writer.append(',').newline().quote("methods").append(':');
            methods.printJson(writer);
        }
        writer.unindent().newline();
        writer.append('}');
    }
}
