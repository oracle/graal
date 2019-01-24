/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.oracle.truffle.llvm.parser.macho.injector;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.llvm.parser.macho.MachOFile;
import com.oracle.truffle.llvm.parser.macho.MachOSegmentCommand;
import com.oracle.truffle.llvm.parser.macho.MachOSegmentCommand.MachOSection;

public final class Injector {

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Please provide a wllvm input file and optionally an output file!");
        }

        File file = new File(args[0]);
        if (!file.exists()) {
            throw new IllegalArgumentException("File does not exist!" + file.getAbsolutePath());
        }

        if (args.length == 2) {
            File output = new File(args[1]);
            try {
                file = Files.copy(file.toPath(), output.toPath()).toFile();
            } catch (IOException e) {
                throw new RuntimeException("Could not write into specified output file!");
            }
        }

        try {
            injectBitcode(file);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Bitcode could not be injected into WLLVM file!");
        }
    }

    public static void injectBitcode(File wllvmFile) throws IOException, InterruptedException {

        File bcFile = Files.createTempFile("extractedBc_", ".bc").toFile();
        bcFile.deleteOnExit();

        extractBc(wllvmFile, bcFile);

        // read wllvm built Mach-O executable
        ByteBuffer machOBuffer = readFile(wllvmFile);
        MachOFile machO = MachOFile.create(machOBuffer);

        MachOSection sec = getBcSection(machO);

        // zero out the paths of the bc files
        zeroOutSection(machOBuffer, sec);

        // modify the __llvm_bc section command
        int secOffset = sec.getCmdOffset();

        int offsetOffset = secOffset + MachOSegmentCommand.SEGNAME_SIZE + MachOSegmentCommand.SECTNAME_SIZE + Long.BYTES + Long.BYTES;
        machOBuffer.putInt(offsetOffset, machOBuffer.limit());

        int sizeOffset = secOffset + MachOSegmentCommand.SEGNAME_SIZE + MachOSegmentCommand.SECTNAME_SIZE + Long.BYTES;
        machOBuffer.putLong(sizeOffset, bcFile.length());

        writeToFile(wllvmFile, machOBuffer);

        // append bitcode to file
        ByteBuffer bc = readFile(bcFile);
        appendToFile(wllvmFile, bc);

    }

    private static void writeToFile(File file, ByteBuffer buffer) {
        try {
            buffer.position(0);
            FileChannel out = new FileOutputStream(file).getChannel();
            out.write(buffer);
            out.close();
        } catch (IOException e) {
            throw new RuntimeException("Error while writing to file " + file.getAbsolutePath() + " !");
        }
    }

    private static void appendToFile(File file, ByteBuffer buffer) {
        try {
            RandomAccessFile raf = new RandomAccessFile(file, "rws");
            long prevSize = raf.length();
            raf.setLength(raf.length() + buffer.capacity());

            raf.seek(prevSize);
            raf.write(buffer.array());
            raf.close();
        } catch (IOException e) {
            throw new RuntimeException("Error while processing file " + file.getAbsolutePath() + " !");
        }
    }

    private static MachOSection getBcSection(MachOFile machO) {
        MachOSegmentCommand seg = machO.getSegment("__WLLVM");

        if (seg == null) {
            throw new RuntimeException("Not a valid wllvm build (missing __WLLVM segment)!");
        }

        MachOSection sec = seg.getSection("__llvm_bc");

        if (sec == null) {
            throw new RuntimeException("Not a valid wllvm build (missing __llvm_bc section)!");
        }

        return sec;
    }

    private static ByteBuffer readFile(File file) {
        try {
            FileChannel in = new FileInputStream(file).getChannel();
            long fileSize = in.size();
            ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);
            in.read(buffer);
            in.close();
            buffer.flip();
            return buffer;
        } catch (IOException e) {
            throw new RuntimeException("Error while reading file " + file.getAbsolutePath() + " !");
        }
    }

    private static void extractBc(File wllvmFile, File bcFile) throws InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add("extract-bc");
        cmd.add("-o");
        cmd.add(bcFile.getAbsolutePath());
        cmd.add(wllvmFile.getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(cmd);

        // call extract-bc
        int retCode = -1;
        try {
            Process extractProc = pb.start();
            retCode = extractProc.waitFor();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("extract-bc could not be executed! You might want to check your WLLVM installation!");
        }

        if (retCode != 0) {
            throw new RuntimeException("Error while executing extract-bc!");
            // if llvm-link is not found by wllvm, no error is thrown (retCode == 0)
        }

    }

    private static void zeroOutSection(ByteBuffer machOBuffer, MachOSection sec) {
        byte[] zeroOut = new byte[(int) sec.getSize()];
        machOBuffer.position(sec.getOffset());
        machOBuffer.put(zeroOut);
    }

}
