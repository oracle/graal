/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.graphviz;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Provides functionality to process graphs in the DOT language with a Graphviz tool and obtain the generated output.
 */
public class GraphvizRunner {

    public static final String DOT_LAYOUT = "dot";

    /**
     * Processes data from an input stream with a Graphviz tool such as {@code dot}, writing output in the specified
     * format to the given output stream. The method waits for the executed tool to finish and then returns its exit
     * code.
     * 
     * @param layout
     *            The Graphviz layouter to use (e.g. "dot").
     * @param in
     *            Stream to read input from.
     * @param out
     *            Stream to write output to.
     * @param format
     *            Desired output format (-T parameter).
     * @return Exit code of the called utility.
     * @throws IOException
     *             When the process can not be started (e.g. Graphviz missing) or reading/writing a stream fails.
     */
    public static int process(String layout, InputStream in, OutputStream out, String format) throws IOException {
        byte[] buffer = new byte[4096];

        // create and start process
        ProcessBuilder pb = new ProcessBuilder("dot", "-T", format, "-K", layout);
        Process p = pb.start();

        // write data from in to stdin
        OutputStream stdin = p.getOutputStream();
        transfer(buffer, in, stdin);
        stdin.close();
        in.close();

        // read output from stdout and write to out
        InputStream stdout = p.getInputStream();
        transfer(buffer, stdout, out);
        stdout.close();

        // wait for process to terminate
        for (;;) {
            try {
                return p.waitFor();
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    /**
     * Reads all data from an {@link InputStream} and writes it to an {@link OutputStream}, using the provided buffer.
     */
    private static void transfer(byte[] buffer, InputStream in, OutputStream out) throws IOException {
        int count;
        while ((count = in.read(buffer, 0, buffer.length)) != -1) {
            out.write(buffer, 0, count);
        }
        in.close();
    }
}
