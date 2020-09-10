/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.c.util;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.c.libc.TemporaryBuildDirectoryProvider;

public class FileUtils {

    public static void drainInputStream(InputStream source, OutputStream sink) {
        byte[] buffer = new byte[4 * 1024];
        try {
            while (true) {
                int read = source.read(buffer);
                if (read == -1) {
                    break;
                }
                sink.write(buffer, 0, read);
            }
        } catch (IOException ex) {
            throw shouldNotReachHere(ex);
        }
    }

    public static List<String> readAllLines(InputStream source) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(source));
            List<String> result = new ArrayList<>();
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                result.add(line);
            }
            return result;
        } catch (IOException ex) {
            throw shouldNotReachHere(ex);
        }
    }

    public static Process executeCommand(String... args) throws IOException, InterruptedException {
        return executeCommand(Arrays.asList(args));
    }

    public static Process executeCommand(List<String> args) throws IOException, InterruptedException {
        ProcessBuilder command = prepareCommand(args, null).redirectErrorStream(true);

        traceCommand(command);

        Process process = command.start();

        try (InputStream inputStream = process.getInputStream()) {
            traceCommandOutput(readAllLines(inputStream));
        }

        process.waitFor();

        return process;
    }

    public static ProcessBuilder prepareCommand(List<String> args, Path commandDir) throws IOException {
        ProcessBuilder command = new ProcessBuilder().command(args);

        Path tempDir;
        if (commandDir != null) {
            tempDir = commandDir;
        } else {
            Path temp = ImageSingletons.lookup(TemporaryBuildDirectoryProvider.class).getTemporaryBuildDirectory();
            tempDir = temp.resolve(Paths.get(args.get(0)).getFileName());
        }
        Files.createDirectories(tempDir);
        command.directory(tempDir.toFile());

        return command;
    }

    public static void traceCommand(ProcessBuilder command) {
        if (SubstrateOptions.TraceNativeToolUsage.getValue()) {
            String commandLine = SubstrateUtil.getShellCommandString(command.command(), false);
            System.out.printf(">> %s%n", commandLine);
        }
    }

    public static void traceCommandOutput(List<String> lines) {
        if (SubstrateOptions.TraceNativeToolUsage.getValue()) {
            for (String line : lines) {
                System.out.printf("># %s%n", line);
            }
        }
    }
}
