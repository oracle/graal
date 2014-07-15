/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.sl.builtins;

import java.io.*;

import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.sl.runtime.*;

/**
 * Builtin function to write a value to the {@link SLContext#getOutput() standard output}. The
 * different specialization leverage the typed {@code println} methods available in Java, i.e.,
 * primitive values are printed without converting them to a {@link String} first.
 * <p>
 * Printing involves a lot of Java code, so we need to tell the optimizing system that it should not
 * unconditionally inline everything reachable from the println() method. This is done via the
 * {@link SlowPath} annotations.
 */
@NodeInfo(shortName = "println")
public abstract class SLPrintlnBuiltin extends SLBuiltinNode {

    public SLPrintlnBuiltin() {
        super(new NullSourceSection("SL builtin", "println"));
    }

    @Specialization
    public long println(long value) {
        doPrint(getContext().getOutput(), value);
        return value;
    }

    @SlowPath
    private static void doPrint(PrintStream out, long value) {
        out.println(value);
    }

    @Specialization
    public boolean println(boolean value) {
        doPrint(getContext().getOutput(), value);
        return value;
    }

    @SlowPath
    private static void doPrint(PrintStream out, boolean value) {
        out.println(value);
    }

    @Specialization
    public String println(String value) {
        doPrint(getContext().getOutput(), value);
        return value;
    }

    @SlowPath
    private static void doPrint(PrintStream out, String value) {
        out.println(value);
    }

    @Specialization
    public Object println(Object value) {
        doPrint(getContext().getOutput(), value);
        return value;
    }

    @SlowPath
    private static void doPrint(PrintStream out, Object value) {
        out.println(value);
    }
}
