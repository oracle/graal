/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime.debug.disassembler;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * Disassembles machine code by running it through objdump, which needs to be available on the path. Standard versions
 * included in at least recent Linux distributions and macOS can be used.
 */
public class ObjdumpDisassembler implements Disassembler {

    public String disassemble(MachineCodeAccessor machineCode) throws IOException {
        final Process process = new ProcessBuilder()
                .command("objdump", "--no-show-raw-insn", "-d", "/dev/stdin")
                .start();
        final OutputStream objdumpInputStream = process.getOutputStream();
        final InputStream objdumpErrorStream = process.getErrorStream();
        final InputStream objdumpOutputStream = process.getInputStream();
        objdumpInputStream.write(ElfCoreWriter.writeElf(machineCode));
        objdumpInputStream.close();
        final ByteArrayOutputStream objdumpError = new ByteArrayOutputStream();
        final ByteArrayOutputStream objdumpOutput = new ByteArrayOutputStream();
        final byte[] buffer = new byte[1024];
        while (process.isAlive() || objdumpErrorStream.available() > 0 || objdumpOutputStream.available() > 0) {
            if (objdumpErrorStream.available() > 0) {
                final int read = objdumpErrorStream.read(buffer);
                objdumpError.write(buffer, 0, read);
            }
            if (objdumpOutputStream.available() > 0) {
                final int read = objdumpOutputStream.read(buffer);
                objdumpOutput.write(buffer, 0, read);
            }
        }
        int objdumpExitCode;
        while (true) {
            try {
                objdumpExitCode = process.waitFor();
                break;
            } catch (InterruptedException e) {
                continue;
            }
        }
        if (objdumpExitCode == 0) {
            final StringBuilder builder = new StringBuilder();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(objdumpOutput.toByteArray())));
            while (true) {
                final String line = reader.readLine();
                if (line == null) {
                    break;
                }
                if (line.isEmpty() || line.startsWith("/dev/stdin:") || line.startsWith("Disassembly of section .text:") || line.startsWith(".text:")) {
                    continue;
                }
                builder.append(line);
                builder.append(System.lineSeparator());
            }
            return builder.toString().trim();
        } else {
            return objdumpError.toString(Charset.defaultCharset().name());
        }
    }

}
