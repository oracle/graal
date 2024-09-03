/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector.workflowtest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;


/**
 * Launcher of Graal process. Returns an URL to start the CDT with.
 */
public final class Launcher {

    private Launcher() {
        throw new UnsupportedOperationException();
    }

    public static String launch(String graalLauncher, String scriptFile, boolean suspend, Consumer<String> output) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(graalLauncher, "--engine.WarnInterpreterOnly=false", "--inspect=0", "--inspect.Suspend=" + suspend, "--inspect.Path=InspectCDT", scriptFile);
        Process p = pb.start();
        AtomicReference<String> devtoolsURL = new AtomicReference<>();
        CountDownLatch launcherStartedLatch = new CountDownLatch(1);
        BufferedReader errorReader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
        Thread errorReaderThread = new Thread(() -> {
            boolean haveURL = false;
            String line;
            try {
                while ((line = errorReader.readLine()) != null) {
                    if (!haveURL) {
                        int i = line.indexOf("devtools://");
                        if (i > 0) {
                            String url = line.substring(i);
                            devtoolsURL.set(url);
                            launcherStartedLatch.countDown();
                            haveURL = true;
                        }
                    }
                    System.err.println(line);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            launcherStartedLatch.countDown();
        }, "Launcher Error Reader");
        errorReaderThread.start();
        BufferedReader outputReader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        Thread outputReaderThread = new Thread(() -> {
            String line;
            try {
                while ((line = outputReader.readLine()) != null) {
                    output.accept(line);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }, "Launcher Output Reader");
        outputReaderThread.start();
        launcherStartedLatch.await();

        String url = devtoolsURL.get();
        return url;
    }
}
