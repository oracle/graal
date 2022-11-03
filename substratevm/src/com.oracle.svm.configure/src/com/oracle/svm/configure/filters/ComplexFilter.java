/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure.filters;

import java.io.IOException;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.configure.json.JsonWriter;

public class ComplexFilter implements ConfigurationFilter {
    private HierarchyFilterNode hierarchyFilterNode;
    private final RegexFilter regexFilter = new RegexFilter();

    public ComplexFilter(HierarchyFilterNode hierarchyFilterNode) {
        this.hierarchyFilterNode = hierarchyFilterNode;
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        writer.append('{');
        writer.indent().newline();
        hierarchyFilterNode.printJson(writer);
        writer.append(",\n").newline();
        regexFilter.printJson(writer);
        writer.unindent().newline();
        writer.append('}').newline();
    }

    @Override
    public void parseFromJson(EconomicMap<String, Object> topJsonObject) {
        hierarchyFilterNode.parseFromJson(topJsonObject);
        regexFilter.parseFromJson(topJsonObject);
    }

    @Override
    public boolean includes(String qualifiedName) {
        return hierarchyFilterNode.includes(qualifiedName) && regexFilter.includes(qualifiedName);
    }

    public HierarchyFilterNode getHierarchyFilterNode() {
        return hierarchyFilterNode;
    }

    public void setHierarchyFilterNode(HierarchyFilterNode hierarchyFilterNode) {
        this.hierarchyFilterNode = hierarchyFilterNode;
    }
}
