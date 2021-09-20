/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polybench;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class AllocatedBytesMetric implements Metric {

    private static Optional<Double> readFromSocket() {
        try (Socket s = new Socket("localhost", 6666)) {
            DataInputStream in = new DataInputStream(s.getInputStream());
            return Optional.of(in.readDouble());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public String name() {
        return "allocated bytes";
    }

    @Override
    public String unit() {
        return "bytes";
    }

    @Override
    public Map<String, String> getEngineOptions(Config config) {
        HashMap<String, String> map = new HashMap<>();
        map.put("allocated-bytes", "true");
        return map;
    }

    @Override
    public void beforeIteration(boolean warmup, int iteration, Config config) {
        readFromSocket();
    }

    @Override
    public Optional<Double> reportAfterIteration(Config config) {
        return readFromSocket();
    }
}
