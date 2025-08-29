/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.graal;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Helps collect stack traces of calls to some methods that probably shouldn't be called. For
 * example when using {@link EspressoGraalRuntime} in the context of running native-image on top of
 * espresso, we shouldn't expect any calls to methods that query machine-specific details.
 * <p>
 * Such methods should call {@link #onSuspiciousHostAccess}.
 * <p>
 * Setting {@code suspicious.host.access.collector.enabled} to {@code true} will enable collection
 * of the call-sites of those methods and report the tree of stack traces reaching them on exit.
 */
final class SuspiciousHostAccessCollector {
    private static final boolean ENABLED = Boolean.getBoolean("suspicious.host.access.collector.enabled");
    private static final Node ROOT = new Node();
    static {
        if (ENABLED) {
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    dumpSuspiciousAccesses(System.out);
                }
            });
        }
    }

    static void onSuspiciousHostAccess() {
        if (!ENABLED) {
            return;
        }
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        add(stackTrace);
    }

    static void dumpSuspiciousAccesses(PrintStream out) {
        out.println("Suspicious host accesses:");
        ROOT.dump(out, 0);
    }

    private static void add(StackTraceElement[] stackTrace) {
        Node currentNode = ROOT;
        int currentIndex = 2;
        while (currentIndex < stackTrace.length) {
            StackTraceElement element = stackTrace[currentIndex];
            currentNode = currentNode.add(element);
            currentIndex++;
        }
    }

    private static final class Node {
        Map<StackTraceElement, Node> children;

        synchronized Node add(StackTraceElement element) {
            if (children == null) {
                Node child = new Node();
                children = Map.of(element, child);
                return child;
            }
            Node existing = children.get(element);
            if (existing != null) {
                return existing;
            }
            Node newChild = new Node();
            if (children.size() == 1) {
                Map.Entry<StackTraceElement, Node> oldEntry = children.entrySet().iterator().next();
                children = HashMap.newHashMap(2);
                children.put(oldEntry.getKey(), oldEntry.getValue());
            }
            children.put(element, newChild);
            return newChild;
        }

        void dump(PrintStream out, int indent) {
            if (children == null) {
                return;
            }
            int nextIndent = children.size() > 1 ? indent + 1 : indent;
            for (Map.Entry<StackTraceElement, Node> entry : children.entrySet()) {
                StringBuilder sb = new StringBuilder();
                sb.repeat("  ", indent);
                sb.append("* ");
                sb.append(entry.getKey());
                out.println(sb);
                entry.getValue().dump(out, nextIndent);
            }
        }
    }
}
