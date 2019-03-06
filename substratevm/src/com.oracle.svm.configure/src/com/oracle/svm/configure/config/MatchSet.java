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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oracle.svm.configure.json.JsonPrintable;
import com.oracle.svm.configure.json.JsonPrinter;
import com.oracle.svm.configure.json.JsonWriter;

public class MatchSet<T> implements JsonPrintable {
    private final Comparator<T> comparator;
    private final JsonPrinter<T> printer;
    private boolean all;
    private Set<T> set;

    static <T extends JsonPrintable> MatchSet<T> create(Comparator<T> comparator) {
        return new MatchSet<>(comparator, JsonPrintable::printJson);
    }

    static <T> MatchSet<T> create(Comparator<T> comparator, JsonPrinter<T> printer) {
        return new MatchSet<>(comparator, printer);
    }

    protected MatchSet(Comparator<T> comparator, JsonPrinter<T> printer) {
        this.comparator = comparator;
        this.printer = printer;
    }

    public void includeAll() {
        all = true;
        set = null;
    }

    public void add(T t) {
        if (!all) {
            if (set == null) {
                set = new HashSet<>();
            }
            set.add(t);
        }
    }

    public boolean matchesAll() {
        return all;
    }

    public boolean isEmpty() {
        return !all && set == null;
    }

    public static <T> MatchSet<T> union(MatchSet<T> a, MatchSet<T> b) {
        if (a == null) {
            return b; // can return null
        }
        if (b == null) {
            return a;
        }
        assert a.printer == b.printer && a.comparator == b.comparator;
        MatchSet<T> u = new MatchSet<>(a.comparator, a.printer);
        u.all = a.all || b.all;
        if (!u.all) {
            u.set = new HashSet<>();
            u.set.addAll(a.set);
            u.set.addAll(b.set);
        }
        return u;
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        if (all) {
            throw new RuntimeException("Must be handled in caller");
        }
        writer.append('[');
        if (set != null) {
            writer.indent();
            String prefix = "";
            List<T> list = new ArrayList<>(set);
            list.sort(comparator);
            for (T t : list) {
                writer.append(prefix);
                if (set.size() > 1) {
                    writer.newline();
                }
                printer.print(t, writer);
                prefix = ", ";
            }
            writer.unindent();
            if (set.size() > 1) {
                writer.newline();
            }
        }
        writer.append("]");
    }
}
