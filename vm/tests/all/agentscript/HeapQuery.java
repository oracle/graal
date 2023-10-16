/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.graalvm.visualvm.lib.jfluid.heap.Heap;
import org.graalvm.visualvm.lib.jfluid.heap.HeapFactory;
import org.graalvm.visualvm.lib.profiler.oql.engine.api.OQLEngine;

public class HeapQuery {
    public static void main(String... args) throws Exception {
        final File file = new File(args[0]);
        if (!file.exists()) {
            throw new IOException("Cannot find " + file);
        }
        Heap heap = HeapFactory.createHeap(file);
        System.setProperty("polyglot.js.nashorn-compat", "true");
        System.setProperty("polyglot.js.ecmascript-version", "6");
        final OQLEngine eng = new OQLEngine(heap);
        final String script;
        if (args[1].equals("-e")) {
            script = args[2];
        } else {
            script = new String(Files.readAllBytes(new File(args[1]).toPath()), StandardCharsets.UTF_8);
        }
        eng.executeQuery(script, OQLEngine.ObjectVisitor.DEFAULT);
    }
}
