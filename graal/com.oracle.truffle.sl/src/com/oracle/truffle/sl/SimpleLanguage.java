/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl;

import java.io.*;

import javax.script.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.impl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.sl.runtime.*;

public class SimpleLanguage {

    private static final Object[] NO_ARGUMENTS = new Object[0];

    public static void main(String[] args) {
        run(args[0], System.out, 10, true);
    }

    public static void run(String name, String input, PrintStream printOutput, int repeats, boolean log) {
        if (log) {
            // CheckStyle: stop system..print check
            System.out.printf("== running on %s\n", Truffle.getRuntime().getName());
            // CheckStyle: resume system..print check
        }

        final SLContext context = new SLContext(printOutput);
        final Source source = context.getSourceManager().get(name, input);

        run(context, source, printOutput, repeats, log);
    }

    public static void run(String fileName, PrintStream printOutput, int repeats, boolean log) {
        if (log) {
            // CheckStyle: stop system..print check
            System.out.printf("== running on %s\n", Truffle.getRuntime().getName());
            // CheckStyle: resume system..print check
        }

        final SLContext context = new SLContext(printOutput);
        final Source source = context.getSourceManager().get(fileName);

        run(context, source, printOutput, repeats, log);
    }

    public static void run(SLContext context, Source source, PrintStream printOutput, int repeats, boolean log) {

        SLScript script;
        try {
            script = SLScript.create(context, source);
        } catch (ScriptException e) {
            // TODO temporary hack
            throw new RuntimeException(e);
        }

        if (log) {
            printScript(script);
        }
        try {
            for (int i = 0; i < repeats; i++) {
                long start = System.nanoTime();
                Object result = script.run(NO_ARGUMENTS);
                long end = System.nanoTime();

                if (result != null) {
                    printOutput.println(result);
                }
                if (log) {
                    // CheckStyle: stop system..print check
                    System.out.printf("== iteration %d: %.3f ms\n", (i + 1), (end - start) / 1000000.0);
                    // CheckStyle: resume system..print check
                }
            }

        } finally {
            if (log) {
                printScript(script);
            }
        }
    }

    private static void printScript(SLScript script) {
        NodeUtil.printTree(System.out, ((DefaultCallTarget) script.getMain()).getRootNode());
    }
}
