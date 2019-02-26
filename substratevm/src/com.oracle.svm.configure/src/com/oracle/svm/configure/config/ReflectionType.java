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

import com.oracle.svm.configure.json.JsonPrintable;
import com.oracle.svm.configure.json.JsonWriter;

public class ReflectionType implements JsonPrintable {

    private final String qualifiedName;

    private ReflectionMemberSet declaredMembers;
    private ReflectionMemberSet publicMembers;

    public ReflectionType(String qualifiedName) {
        this.qualifiedName = qualifiedName;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public ReflectionMemberSet getDeclared() {
        if (declaredMembers == null) {
            declaredMembers = new ReflectionMemberSet();
        }
        return declaredMembers;
    }

    public ReflectionMemberSet getPublic() {
        if (publicMembers == null) {
            publicMembers = new ReflectionMemberSet();
        }
        return publicMembers;
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        writer.append('{').indent().newline();
        writer.quote("name").append(':').quote(qualifiedName);
        MatchSet<String> fields = null;
        MatchSet<ReflectionMethod> methods = null;
        if (declaredMembers != null) {
            declaredMembers.printJson(writer, "Declared");
            fields = declaredMembers.getIndividualFields();
            methods = declaredMembers.getIndividualMethodsAndConstructors();
        }
        if (publicMembers != null) {
            publicMembers.printJson(writer, "Public");
            fields = MatchSet.union(fields, publicMembers.getIndividualFields());
            methods = MatchSet.union(methods, publicMembers.getIndividualMethodsAndConstructors());
        }
        if (fields != null && !fields.isEmpty()) {
            writer.append(',').newline().quote("fields").append(':');
            fields.printJson(writer);
        }
        if (methods != null && !methods.isEmpty()) {
            writer.append(',').newline().quote("methods").append(':');
            methods.printJson(writer);
        }
        writer.unindent().newline();
        writer.append('}');
    }
}
