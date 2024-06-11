/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.igvutil.args;

import java.util.ArrayList;
import java.util.List;

/**
 * A positional argument that can be parsed multiple times, and collected into a list.
 * When parsing, this will try to consume all subsequent positional arguments.
 * To avoid ambiguities when using a list argument followed by another positional argument of the same type,
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
    public int parseValue(String[] args, int offset) {
        int index;
        for (index = offset; index < args.length; ) {
            if (args[index].contentEquals(Command.SEPARATOR)) {
                index++;
                break;
            }
            index = inner.parseValue(args, index);
            if (inner.value == null) {
                break;
            }
            if (value == null) {
                value = new ArrayList<>();
            }
            value.add(inner.value);
        }
        return index;
    }

    @Override
    public String getUsage() {
        return String.format("[%s ...]", getName());
    }
}
