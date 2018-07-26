/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.c.codegen;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.nativeimage.Platform;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.util.FileUtils;

public class CCompilerInvoker {

    protected final NativeLibraries nativeLibs;
    protected final Path tempDirectory;
    private String lastExecutedCommand = "";

    public CCompilerInvoker(NativeLibraries nativeLibs, Path tempDirectory) {
        this.nativeLibs = nativeLibs;
        this.tempDirectory = tempDirectory;
    }

    public Process startPreprocessor(List<String> options, Path sourceFile) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(Platform.includedIn(Platform.WINDOWS.class) ? "CL" : "gcc");
        command.add("-E");
        command.add(sourceFile.normalize().toString());
        command.addAll(options);
        return startCommand(command);
    }

    public Path compileAndParseError(List<String> options, Path source, Path target) {
        try {
            Process compilingProcess = startCompiler(options, source.normalize(), target.normalize());
            InputStream es = compilingProcess.getErrorStream();
            InputStream is = compilingProcess.getInputStream();

            List<String> lines = FileUtils.readAllLines(es);
            FileUtils.readAllLines(is);
            int status = compilingProcess.waitFor();

            boolean errorReported = false;
            for (String line : lines) {
                if (line.contains(": error:") || line.contains(": fatal error:")) {
                    reportCompilerError(source, line);
                    errorReported = true;
                }
            }
            if (status != 0 && !errorReported) {
                reportCompilerError(source, lines.toString());
            }
            compilingProcess.destroy();
            return target;

        } catch (InterruptedException ex) {
            throw new InterruptImageBuilding();
        } catch (IOException ex) {
            throw UserError.abort("Unable to compile C-ABI query code. Make sure GCC toolchain is installed on your system.");
        }
    }

    public Process startCompiler(List<String> options, Path source, Path target) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(Platform.includedIn(Platform.WINDOWS.class) ? "CL" : "gcc");
        command.addAll(options);
        command.add(source.normalize().toString());
        if (target != null) {
            if (Platform.includedIn(Platform.WINDOWS.class)) {
                command.add("/Fe" + target.normalize().toString());
            } else {
                command.add("-o");
                command.add(target.normalize().toString());
            }
        }
        return startCommand(command);
    }

    protected String commandString(List<String> command) {
        StringBuilder sb = new StringBuilder();
        for (String s : command) {
            sb.append(' ').append(s);
        }
        return sb.toString();
    }

    public String lastExecutedCommand() {
        return lastExecutedCommand;
    }

    public Process startCommand(List<String> command) throws IOException {
        Process proc = null;
        ProcessBuilder pb = new ProcessBuilder().command(command).directory(tempDirectory.toFile());
        proc = pb.start();
        lastExecutedCommand = commandString(pb.command());
        return proc;
    }

    public int runCommandToExit(List<String> command) {
        int status;
        try {
            Process p = startCommand(command);
            FileUtils.drainInputStream(p.getInputStream(), System.err);
            status = p.waitFor();
        } catch (IOException e) {
            throw new RuntimeException("Error when running command: " + command + "(wd: " + tempDirectory.toString() + ")", e);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while waiting for command: " + command);
        }

        if (status != 0) {
            throw new IllegalStateException("command returned " + status);
        }

        return status;
    }

    protected void reportCompilerError(@SuppressWarnings("unused") Path queryFile, String line) {
        throw shouldNotReachHere(line);
    }
}
