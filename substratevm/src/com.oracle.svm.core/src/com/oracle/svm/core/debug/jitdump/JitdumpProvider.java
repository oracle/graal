/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.debug.jitdump;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.objectfile.debugentry.CompiledMethodEntry;
import com.oracle.objectfile.debugentry.MethodEntry;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.debug.SubstrateDebugInfoProvider;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.os.RawFileOperationSupport;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.util.LogUtils;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.serviceprovider.GlobalAtomicLong;
import jdk.graal.compiler.word.Word;

public class JitdumpProvider {
    public static class Options {
        @Option(help = "Directory where jitdump related files will be placed for perf")//
        public static final RuntimeOptionKey<String> RuntimeJitdumpDir = new RuntimeOptionKey<>("jitdump", RuntimeOptionKey.RuntimeOptionKeyFlag.RelevantForCompilationIsolates);
    }

    /*
     * A pointer to the file descriptor of the jitdump file. This needs to be a CGlobalData to be
     * accessible for run-time compilations.
     */
    private static final CGlobalData<WordPointer> fdPointer = CGlobalDataFactory.createWord(Word.zero(), null, true);
    private static final CGlobalData<WordPointer> jitdumpMappedAddress = CGlobalDataFactory.createWord(Word.nullPointer(), null, true);
    private static final GlobalAtomicLong codeIndex = new GlobalAtomicLong("JITDUMP_CODE_INDEX", 0L);

    @Fold
    static RawFileOperationSupport getFileSupport() {
        return RawFileOperationSupport.nativeByteOrder();
    }

    public static Path getJitdumpPath() {
        return Paths.get(Options.RuntimeJitdumpDir.getValue(), "jit-" + ProcessProperties.getProcessID() + ".dump");
    }

    public static RuntimeSupport.Hook startupHook() {
        return isFirstIsolate -> {
            if (!isFirstIsolate) {
                return;
            }

            try {
                Files.createDirectories(getJitdumpPath().getParent());
            } catch (IOException e) {
                LogUtils.warning("Failed to create directory for the jitdump file " + getJitdumpPath().toFile().getAbsolutePath());
                return;
            }

            RawFileOperationSupport.RawFileDescriptor fd = getFileSupport().create(getJitdumpPath().toFile(), RawFileOperationSupport.FileCreationMode.CREATE_OR_REPLACE,
                            RawFileOperationSupport.FileAccessMode.READ_WRITE);
            if (!getFileSupport().isValid(fd)) {
                LogUtils.warning("Failed to create the jitdump file " + getJitdumpPath().toFile().getAbsolutePath());
                return;
            }

            /*
             * We need a mmap call that is picked up by perf to announce a jitdump file to perf.
             */
            UnsignedWord pageSize = VirtualMemoryProvider.get().getGranularity();
            Pointer mappedAddress = VirtualMemoryProvider.get().mapFile(Word.nullPointer(), pageSize, Word.signed(fd.rawValue()), Word.zero(),
                            VirtualMemoryProvider.Access.EXECUTE | VirtualMemoryProvider.Access.READ);
            if (mappedAddress.isNull()) {
                getFileSupport().close(fd);
                LogUtils.warning("Failed to prepare the jitdump file " + getJitdumpPath().toFile().getAbsolutePath());
                return;
            }

            /* Store the open file descriptor and memory mapped region. */
            fdPointer.get().write(fd);
            jitdumpMappedAddress.get().write(mappedAddress);

            /* Write the jitdump header. */
            writeHeader(fd);
        };
    }

    public static RuntimeSupport.Hook shutdownHook() {
        return isFirstIsolate -> {
            if (!isFirstIsolate) {
                return;
            }

            /* Append the optional close record and close jitdump file. */
            RawFileOperationSupport.RawFileDescriptor fd = fdPointer.get().read();
            if (getFileSupport().isValid(fd)) {
                writeCloseRecord(fd);
                getFileSupport().close(fd);
                /* Clear file descriptor. */
                fdPointer.get().write(Word.zero());
            }

            /*
             * If the initialization was successful we have a memory mapped region that needs to be
             * freed.
             */
            Pointer mappedAddress = jitdumpMappedAddress.get().read();
            if (mappedAddress.isNonNull()) {
                UnsignedWord pageSize = VirtualMemoryProvider.get().getGranularity();
                VirtualMemoryProvider.get().free(mappedAddress, pageSize);
                /* Clear address of memory mapped region. */
                jitdumpMappedAddress.get().write(Word.nullPointer());
            }
        };
    }

    private static synchronized void writeToFile(RawFileOperationSupport.RawFileDescriptor fd, byte[] content) {
        assert getFileSupport().isValid(fd) : "File descriptor for jitdump file must be initialized before writing to it";
        getFileSupport().write(fd, content);
    }

    /**
     * Write the jitdump header.
     */
    public static void writeHeader(RawFileOperationSupport.RawFileDescriptor fd) {
        ByteBuffer content = ByteBuffer.allocate(JitdumpHeader.SIZE);
        content.order(ByteOrder.nativeOrder());

        JitdumpHeader header = new JitdumpHeader();
        content.putInt(JitdumpHeader.MAGIC)
                        .putInt(JitdumpHeader.VERSION)
                        .putInt(JitdumpHeader.SIZE)
                        .putInt(header.elfMach().toShort())
                        .putInt(0)  // padding. Reserved for future use
                        .putInt(header.pid())
                        .putLong(header.timestamp())
                        .putLong(0);  // no flags needed
        assert content.remaining() == 0 : "Missing " + content.remaining() + " byte(s) in the jitdump header.";

        writeToFile(fd, content.array());
    }

    /**
     * Write the jitdump close record.
     */
    public static void writeCloseRecord(RawFileOperationSupport.RawFileDescriptor fd) {
        ByteBuffer content = ByteBuffer.allocate(JitdumpRecordHeader.SIZE);
        content.order(ByteOrder.nativeOrder());

        JitdumpRecordHeader header = new JitdumpRecordHeader(JitdumpRecordId.JIT_CODE_CLOSE, JitdumpRecordHeader.SIZE);
        putRecordHeader(header, content);
        assert content.remaining() == 0 : "Missing " + content.remaining() + " byte(s) in the jitdump close record.";

        writeToFile(fd, content.array());
    }

    /**
     * Create the byte array for the jitdump records of a run-time compilation.
     *
     * @param debugInfoProvider the debug info provider that generated the compiled method entry
     * @param compiledMethodEntry the {@code CompiledMethodEntry} of the run-time compiled method
     */
    public static void writeRecords(SubstrateDebugInfoProvider debugInfoProvider, CompiledMethodEntry compiledMethodEntry) {
        MethodEntry methodEntry = compiledMethodEntry.primary().getMethodEntry();
        JitdumpCodeLoadRecord cl = JitdumpCodeLoadRecord.create(methodEntry, debugInfoProvider.getCompilation(), debugInfoProvider.getCodeSize(), debugInfoProvider.getCodeAddress());
        JitdumpDebugInfoRecord di = JitdumpDebugInfoRecord.create(compiledMethodEntry, debugInfoProvider.getCodeAddress());

        int totalSize = cl.header().recordSize() + di.header().recordSize();
        ByteBuffer content = ByteBuffer.allocate(totalSize);
        content.order(ByteOrder.nativeOrder());

        /*
         * First write the debug info record for the run-time compiled method.
         */
        putRecordHeader(di.header(), content)
                        .putLong(di.address())
                        .putLong(di.entries().size());
        for (JitdumpDebugInfoRecord.JitdumpDebugEntry de : di.entries()) {
            content.putLong(de.address())
                            .putInt(de.line())
                            .putInt(de.discriminator())
                            .put(de.filename().getBytes())
                            .put((byte) 0); // Terminate filename string.
        }
        assert content.position() == di.header().recordSize();

        /*
         * Add corresponding code load record after the debug info record.
         */
        putRecordHeader(cl.header(), content)
                        .putInt(cl.pid())
                        .putInt(cl.tid())
                        .putLong(cl.address())  // Virtual address = address.
                        .putLong(cl.address())
                        .putLong(cl.size())
                        // Code index -> unique identifier for run-time compiled code.
                        .putLong(codeIndex.getAndIncrement())
                        .put(cl.name().getBytes())
                        .put((byte) 0)  // Terminate method name string.
                        .put(cl.code(), 0, debugInfoProvider.getCodeSize());  // Add compiled code.

        assert content.remaining() == 0 : "Missing " + content.remaining() + " byte(s) in the jitdump records for " + methodEntry.getMethodName() + ".";

        writeToFile(fdPointer.get().read(), content.array());
    }

    private static ByteBuffer putRecordHeader(JitdumpRecordHeader header, ByteBuffer content) {
        return content.putInt(header.id().value())
                        .putInt(header.recordSize())
                        .putLong(header.timestamp());
    }
}
