/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.image;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.nio.file.Path;

import com.oracle.svm.core.hub.DynamicHub;

public final class DotHeapPrinter {

    public static DotHeapPrinter start(Path fileName) {
        try {
            PrintWriter writer = new PrintWriter(fileName.toFile());
            DotHeapPrinter printer = new DotHeapPrinter(writer);
            printer.start();
            return printer;
        } catch (FileNotFoundException ex) {
            throw shouldNotReachHere(ex);
        }
    }

    private final PrintWriter writer;
    private NativeImageHeap.ObjectInfo lastObject;
    private boolean fromCodeCache;

    private DotHeapPrinter(PrintWriter writer) {
        this.writer = writer;
    }

    private void start() {
        writer.println("digraph g {");
        writer.println("node [shape=box]");

    }

    public void finish() {
        writer.println("}");
        writer.close();
    }

    public void addObject(NativeImageHeap.ObjectInfo info) {
        lastObject = info;
        fromCodeCache = false;

        String label = info.getPartitionName() + "[" + info.getOffsetInPartitionForDebugging() + "] " + info.getClazz().toJavaName(false);
        if (info.getClazz().isArray()) {
            label = label + " len " + Array.getLength(info.getObject());
        }

        if (info.getObject() instanceof char[]) {
            label = label + ": " + sanitize(new String((char[]) info.getObject()));
        } else if (info.getObject() instanceof byte[]) {
            label = label + ": " + sanitize(new String((byte[]) info.getObject()));
        } else if (info.getObject() instanceof DynamicHub) {
            label = label + ": " + ((DynamicHub) info.getObject()).getTypeID();
        }
        writer.format("%s [label=\"%s\"", id(info), label);

        if (info.getObject() instanceof Object[]) {
            writer.print(" color=blue");
        } else if (info.getClazz().isArray()) {
            writer.print(" color=green");
        } else if (info.getObject() instanceof DynamicHub) {
            writer.print(" color=purple");
        }
        writer.println("];");
    }

    public void addCodeCache() {
        lastObject = null;
        fromCodeCache = true;
    }

    public void addLink(NativeImageHeap.ObjectInfo to, String label) {
        String from;
        if (fromCodeCache) {
            from = "c" + label;
            writer.format("%s [label=\"Code Cache\" color=red];\n", from);
        } else {
            from = id(lastObject);
        }
        writer.format("%s -> %s  [label=\"%s\"", from, id(to), label);
        writer.println("];");
    }

    private static String id(NativeImageHeap.ObjectInfo info) {
        return "o" + info.getPartitionName() + info.getOffsetInPartitionForDebugging();
    }

    private static String sanitize(String s) {
        StringBuilder result = new StringBuilder(Math.min(s.length(), 60));
        boolean illegalAppended = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 32 && c < 255 && c != '"') {
                result.append(c);
                illegalAppended = false;
            } else if (!illegalAppended) {
                result.append("~");
                illegalAppended = true;
            }

            if (result.length() >= 50) {
                result.append(" [...]");
                break;
            }
        }
        return result.toString();
    }
}
