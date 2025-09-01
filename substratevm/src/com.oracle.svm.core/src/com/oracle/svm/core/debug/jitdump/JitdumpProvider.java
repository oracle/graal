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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.objectfile.debugentry.CompiledMethodEntry;
import com.oracle.objectfile.debugentry.MethodEntry;
import com.oracle.svm.core.debug.SubstrateDebugInfoProvider;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.os.RawFileOperationSupport;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.util.LogUtils;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.util.Digest;
import jdk.graal.compiler.word.Word;

public class JitdumpProvider {
    public static class Options {
        @Option(help = "Directory where jitdump related files will be placed for perf")//
        public static final RuntimeOptionKey<String> RuntimeJitdumpDir = new RuntimeOptionKey<>("jitdump", RuntimeOptionKey.RuntimeOptionKeyFlag.RelevantForCompilationIsolates);
    }

    private PointerBase addressBegin = Word.nullPointer();
    private UnsignedWord pageSize = Word.zero();

    @Fold
    public static JitdumpProvider singleton() {
        return ImageSingletons.lookup(JitdumpProvider.class);
    }

    @Fold
    static RawFileOperationSupport getFileSupport() {
        return RawFileOperationSupport.nativeByteOrder();
    }

    public static Path getJitdumpPath() {
        return Paths.get(Options.RuntimeJitdumpDir.getValue(), "jit-" + ProcessProperties.getProcessID() + ".dump");
    }

    public RuntimeSupport.Hook startupHook() {
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

            pageSize = VirtualMemoryProvider.get().getGranularity();
            if (pageSize.rawValue() == -1) {
                getFileSupport().close(fd);
                LogUtils.warning("Failed to prepare the jitdump file " + getJitdumpPath().toFile().getAbsolutePath());
                return;
            }

            /*
             * We need a mmap call that is picked up by perf to announce a jitdump file to perf.
             */
            addressBegin = VirtualMemoryProvider.get().mapFile(Word.nullPointer(), pageSize, Word.signed(fd.rawValue()), Word.zero(),
                            VirtualMemoryProvider.Access.EXECUTE | VirtualMemoryProvider.Access.READ);
            if (addressBegin.isNull()) {
                getFileSupport().close(fd);
                LogUtils.warning("Failed to prepare the jitdump file " + getJitdumpPath().toFile().getAbsolutePath());
                return;
            }

            /* Write the jitdump header. */
            writeHeader(fd);
            getFileSupport().close(fd);
        };
    }

    public RuntimeSupport.Hook shutdownHook() {
        return isFirstIsolate -> {
            if (!isFirstIsolate) {
                return;
            }

            /*
             * If the initialization was successful we have a memory mapped region, and we need to
             * do some cleanup.
             */
            if (addressBegin.isNonNull()) {
                /* Append the optional close record. */
                RawFileOperationSupport.RawFileDescriptor fd = getFileSupport().open(getJitdumpPath().toFile(), RawFileOperationSupport.FileAccessMode.APPEND);
                if (getFileSupport().isValid(fd)) {
                    writeCloseRecord(fd);
                    getFileSupport().close(fd);
                }

                /* And unmap the memory mapped region. */
                VirtualMemoryProvider.get().free(addressBegin, pageSize);
            }
        };
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

        assert getFileSupport().isValid(fd) : "File descriptor for jitdump file must be initialized before writing to it";
        getFileSupport().write(fd, content.array());
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

        assert getFileSupport().isValid(fd) : "File descriptor for jitdump file must be initialized before writing to it";
        getFileSupport().write(fd, content.array());
    }

    /**
     * Create the byte array for the jitdump records of a run-time compilation.
     *
     * @param debugInfoProvider the debug info provider that generated the compiled method entry
     * @param compiledMethodEntry the {@code CompiledMethodEntry} of the run-time compiled method
     * @return the {@code ByteBuffer} containing the content of the records for a run-time
     *         compilation
     */
    public static ByteBuffer createRecords(SubstrateDebugInfoProvider debugInfoProvider, CompiledMethodEntry compiledMethodEntry) {
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
                        .putLong(Digest.digestAsUUID(methodEntry.getSymbolName()).getLeastSignificantBits())
                        .put(cl.name().getBytes())
                        .put((byte) 0)  // Terminate method name string.
                        .put(cl.code(), 0, debugInfoProvider.getCodeSize());  // Add compiled code.

        assert content.remaining() == 0 : "Missing " + content.remaining() + " byte(s) in the jitdump records for " + methodEntry.getMethodName() + ".";

        return content.flip();
    }

    private static ByteBuffer putRecordHeader(JitdumpRecordHeader header, ByteBuffer content) {
        return content.putInt(header.id().value())
                        .putInt(header.recordSize())
                        .putLong(header.timestamp());
    }
}
