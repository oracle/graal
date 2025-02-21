/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.util.args;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Repeated option value which parses an underlying option multiple times, and collects the results
 * into a list. When parsing, this will try to consume all subsequent positional arguments. To avoid
 * ambiguities when using a list argument followed by another positional argument of the same type,
 * you can use an argument separator (`--`) as a terminator, marking the end of a list.
 */
public class ListValue<T> extends OptionValue<List<T>> {
    private final OptionValue<T> inner;

    public ListValue(String name, String help, OptionValue<T> inner) {
        super(name, help);
        this.inner = inner;
    }

    public ListValue(String name, List<T> defaultValue, String help, OptionValue<T> inner) {
        super(name, defaultValue, help);
        this.inner = inner;
    }

    @Override
    public boolean parseValue(String arg) {
        if (arg == null) {
            return false;
        }
        try {
            inner.parseValue(arg);
        } catch (InvalidArgumentException e) {
            // Terminate list if option fails to parse
            return false;
        }
        if (value == null) {
            value = new ArrayList<>();
        }
        value.add(inner.value);
        return true;
    }

    @Override
    public void printUsage(PrintWriter writer, boolean detailed) {
        writer.append('[');
        writer.append(getName());
        writer.append(" ...]");
    }
}
