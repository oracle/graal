/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/**
 * A boolean flag argument that is {@code false} when not present in the program arguments and
 * {@code true} when it is present.
 */
public class Option<T> extends Argument {
    private Value<T> value;

    public Option(String name, boolean required, String help) {
        super(name, required, help);
        this.value = null;
    }

    public Option<T> withValue(Value<T> value) {
        this.value = value;
        return this;
    }

    @Override
    public int parse(String[] args, int offset) throws InvalidArgumentException {
        if (value == null) {
            assert getName().equals(args[offset]) : String.format("tried to parse option %s from argument %s", getName(), args[offset]);
            set = true;
            return offset + 1;
        }

        if (getName().equals(args[offset])) {
            set = true;
            if (offset + 1 >= args.length) {
                throw new InvalidArgumentException(this, "requires a value which is missing");
            }
            return value.parse(args, offset + 1);
        }

        assert getName().startsWith(args[offset]);
        int equalsIndex = args[offset].indexOf('=');
        assert equalsIndex != -1;
        assert !(value instanceof ListValue<?>) : "list values set with an equals sign are not allowed";
        String valueString = args[offset].substring(equalsIndex + 1);
        value.parseValue(new String[]{valueString}, 0);
        set = value.set;
        return offset + 1;
    }

    public final T getValue() {
        return value.getValue();
    }

    @Override
    public void printUsage(HelpPrinter help) {
        help.print("%s", getName());
        if (value != null) {
            help.print(" ");
            value.printUsage(help);
        }
    }
}
