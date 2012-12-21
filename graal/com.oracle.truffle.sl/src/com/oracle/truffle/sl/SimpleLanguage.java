/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.sl.nodes.*;
import com.oracle.truffle.sl.parser.*;
import com.oracle.truffle.sl.tools.*;

public class SimpleLanguage {

    public static void main(String[] args) throws IOException {
        run(new FileInputStream(args[0]), System.out, 10, true);
    }

    public static void run(InputStream input, PrintStream printOutput, int repeats, boolean log) {
        System.out.printf("== running on %s\n", Truffle.getRuntime().getName());

        NodeFactory factory = new NodeFactory(printOutput);

        Parser parser = new Parser(new Scanner(input), factory);
        parser.Parse();

        FunctionDefinitionNode rootNode = factory.findFunction("main");
        if (log) {
            GraphPrinter.print(rootNode);
        }

        try {
            CallTarget function = Truffle.getRuntime().createCallTarget(rootNode, rootNode.getFrameDescriptor());
            for (int i = 0; i < repeats; i++) {
                Arguments arguments = new SLArguments(new Object[0]);

                long start = System.nanoTime();
                Object result = function.call(null, arguments);
                long end = System.nanoTime();

                if (result != null) {
                    printOutput.println(result);
                }
                if (log) {
                    System.out.printf("== iteration %d: %.3f ms\n", (i + 1), (end - start) / 1000000.0);
                }
            }

        } finally {
            if (log) {
                GraphPrinter.print(rootNode);
            }
        }
    }
}
