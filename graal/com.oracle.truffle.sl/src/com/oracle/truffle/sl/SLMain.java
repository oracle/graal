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

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.sl.parser.*;
import com.oracle.truffle.sl.runtime.*;

public class SLMain {

    private static final Object[] NO_ARGUMENTS = new Object[0];

    public static void main(String[] args) {
        SourceManager sourceManager = new SourceManager();
        Source source = sourceManager.get(args[0]);
        SLContext context = new SLContext(sourceManager, System.err);
        run(context, source, System.out, 1);
    }

    public static void run(SLContext context, Source source, PrintStream logOutput, int repeats) {
        if (logOutput != null) {
            logOutput.println("== running on " + Truffle.getRuntime().getName());
        }

        Parser.parseSL(context, source);
        SLFunction main = context.getFunctionRegistry().lookup("main");
        if (main.getCallTarget() == null) {
            throw new SLException("No function main() found.");
        }

        if (logOutput != null) {
            printScript(context, logOutput);
        }
        try {
            for (int i = 0; i < repeats; i++) {
                long start = System.nanoTime();
                Object result = main.getCallTarget().call(null, new SLArguments(NO_ARGUMENTS));
                long end = System.nanoTime();

                if (result != SLNull.INSTANCE) {
                    context.getPrintOutput().println(result);
                }
                if (logOutput != null) {
                    logOutput.printf("== iteration %d: %.3f ms\n", (i + 1), (end - start) / 1000000.0);
                }
            }

        } finally {
            if (logOutput != null) {
                printScript(context, logOutput);
            }
        }
    }

    private static void printScript(SLContext context, PrintStream logOutput) {
        for (SLFunction function : context.getFunctionRegistry().getFunctions()) {
            if (function.getCallTarget() != null) {
                logOutput.println("=== function " + function.getName());
                NodeUtil.printTree(logOutput, function.getCallTarget().getRootNode());
            }
        }
    }
}
