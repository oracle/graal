/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.printer;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

import com.oracle.max.cri.ri.*;
import com.oracle.max.graal.debug.*;
import com.oracle.max.graal.graph.*;

/**
 * Observes compilation events and uses {@link IdealGraphPrinter} to generate a graph representation that can be
 * inspected with the <a href="http://kenai.com/projects/igv">Ideal Graph Visualizer</a>.
 */
public class IdealGraphPrinterDumpHandler implements DebugDumpHandler {

    private static final Pattern INVALID_CHAR = Pattern.compile("[^A-Za-z0-9_.-]");

    private final String host;
    private final int port;

    public IdealGraphPrinter printer;
    private OutputStream stream;
    private Socket socket;
    private List<RiResolvedMethod> previousInlineContext = new ArrayList<RiResolvedMethod>();

    /**
     * Creates a new {@link IdealGraphPrinterDumpHandler} that writes output to a file named after the compiled method.
     */
    public IdealGraphPrinterDumpHandler() {
        this(null, -1);
    }

    /**
     * Creates a new {@link IdealGraphPrinterDumpHandler} that sends output to a remote IdealGraphVisualizer instance.
     */
    public IdealGraphPrinterDumpHandler(String host, int port) {
        this.host = host;
        this.port = port;
    }

    private IdealGraphPrinter printer() {
        return printer;
    }

    private Socket socket() {
        return socket;
    }

    private void openPrinter(RiResolvedMethod method) {
        assert stream == null && printer() == null;
        String name;
        if (method != null) {
            name = method.holder().name();
            name = name.substring(1, name.length() - 1).replace('/', '.');
            name = name + "." + method.name();
        } else {
            name = "null";
        }

        openPrinter(name, method);
    }

    private void openPrinter(String title, RiResolvedMethod method) {
        assert stream == null && printer() == null;
        if (host != null) {
            openNetworkPrinter(title, method);
        } else {
            openFilePrinter(title, method);
        }
    }

    private void openFilePrinter(String title, RiResolvedMethod method) {
        String filename = title + ".igv.xml";
        filename = INVALID_CHAR.matcher(filename).replaceAll("_");

        try {
            stream = new FileOutputStream(filename);
            printer = new IdealGraphPrinter(stream);
            printer().begin();
            printer().beginGroup(title, title, method, -1, "Graal");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean networkAvailable() {
        try {
            Socket s = new Socket(host, port);
            s.setSoTimeout(10);
            s.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void openNetworkPrinter(String title, RiResolvedMethod method) {
        try {
            socket = new Socket(host, port);
            if (socket().getInputStream().read() == 'y') {
                stream = new BufferedOutputStream(socket().getOutputStream(), 0x4000);
            } else {
                // server currently does not accept any input
                socket().close();
                socket = null;
                return;
            }


            printer = new IdealGraphPrinter(stream);
            printer().begin();
            printer().beginGroup(title, title, method, -1, "Graal");
            printer().flush();
            if (socket().getInputStream().read() != 'y') {
                // server declines input for this method
                socket().close();
                socket = null;
                stream = null;
                printer = null;
            }
        } catch (IOException e) {
            System.err.println("Error opening connection to " + host + ":" + port + ": " + e);

            if (socket() != null) {
                try {
                    socket().close();
                } catch (IOException ioe) {
                }
                socket = null;
            }
            stream = null;
            printer = null;
        }
    }

    private void closePrinter() {
        assert (printer() != null);

        try {
            printer().endGroup();
            printer().end();

            if (socket() != null) {
                socket().close(); // also closes stream
            } else {
                stream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            printer = null;
            stream = null;
            socket = null;
        }
    }

    public void compilationStarted(String groupTitle) {
        openPrinter(groupTitle, null);
    }


    @Override
    public void dump(Object object, String message) {
        if (object instanceof Graph) {
            Graph graph = (Graph) object;

            // Get all current RiResolvedMethod instances in the context.
            List<RiResolvedMethod> inlineContext = Debug.contextSnapshot(RiResolvedMethod.class);

            // Reverse list such that inner method comes after outer method.
            Collections.reverse(inlineContext);

            // Check for method scopes that must be closed since the previous dump.
            for (int i = 0; i < previousInlineContext.size(); ++i) {
                if (i >= inlineContext.size() || inlineContext.get(i) != previousInlineContext.get(i)) {
                    for (int j = previousInlineContext.size() - 1; j >= i; --j) {
                        closeMethodScope(previousInlineContext.get(j));
                    }
                }
            }

            // Check for method scopes that must be opened since the previous dump.
            for (int i = 0; i < inlineContext.size(); ++i) {
                if (i >= previousInlineContext.size() || inlineContext.get(i) != previousInlineContext.get(i)) {
                    for (int j = i; j < inlineContext.size(); ++j) {
                        openMethodScope(inlineContext.get(j));
                    }
                }
            }

            previousInlineContext = inlineContext;
        }
    }

    private void openMethodScope(RiResolvedMethod method) {
        System.out.println("OPEN " + method);

    }

    private void closeMethodScope(RiResolvedMethod method) {
        System.out.println("CLOSE " + method);

    }
}
