/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.sl.*;
import com.oracle.truffle.sl.runtime.*;

/**
 * Builtin function that reads a String from the {@link SLContext#getInput() standard input}.
 */
@NodeInfo(shortName = "readln")
public abstract class SLReadlnBuiltin extends SLBuiltinNode {

    @Specialization
    public String readln() {
        String result = doRead(getContext().getInput());
        if (result == null) {
            /*
             * We do not have a sophisticated end of file handling, so returning an empty string is
             * a reasonable alternative. Note that the Java null value should never be used, since
             * it can interfere with the specialization logic in generated source code.
             */
            result = "";
        }
        return result;
    }

    @SlowPath
    private static String doRead(BufferedReader in) {
        try {
            return in.readLine();
        } catch (IOException ex) {
            throw new SLException(ex.getMessage());
        }
    }
}
