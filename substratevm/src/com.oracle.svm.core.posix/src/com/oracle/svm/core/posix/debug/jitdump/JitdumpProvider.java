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

package com.oracle.svm.core.posix.debug.jitdump;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.objectfile.debugentry.CompiledMethodEntry;
import com.oracle.objectfile.debugentry.MethodEntry;
import com.oracle.objectfile.debugentry.range.Range;
import com.oracle.objectfile.elf.ELFMachine;
import com.oracle.svm.core.OS;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.debug.SubstrateDebugInfoProvider;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.os.RawFileOperationSupport;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.posix.PosixUtils;
import com.oracle.svm.core.posix.headers.Time;
import com.oracle.svm.core.posix.headers.linux.LinuxTime;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.util.LogUtils;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.serviceprovider.GlobalAtomicLong;
import jdk.graal.compiler.word.Word;

public class JitdumpProvider {
    public static class Options {
        @Option(help = "Directory where jitdump related files will be placed for perf. Defaults to './jitdump'.")//
        public static final RuntimeOptionKey<String> RuntimeJitdumpDir = new RuntimeOptionKey<>("jitdump", Options::validateRuntimeJitdumpDir,
                        RuntimeOptionKey.RuntimeOptionKeyFlag.RelevantForCompilationIsolates);

        private static void validateRuntimeJitdumpDir(RuntimeOptionKey<String> optionKey) {
            if (optionKey.hasBeenSet() && !OS.LINUX.isCurrent()) {
                throw UserError.invalidOptionValue(optionKey, optionKey.getValue(), "The option is only supported on Linux.");
            }
        }
    }

    /**
     * A value representing the string "JiTD", which serves as a magic number for tagging jitdump
     * files. It is written is 0x4A695444 and the reader will detect an endian mismatch when it
     * reads 0x4454694a.
     */
    public static final int MAGIC = 0x4A695444;
    /**
     * The jitdump version. The implementation is based on the
     * {@code JITDUMP specification version 2}, which specifies the version number to be set to 1.
     */
    public static final int VERSION = 1;

    /**
     * A pointer to the file descriptor of the jitdump file. This needs to be a {@code CGlobalData}
     * to be accessible from any isolate in {@link #writeRecords}.
     */
    private static final CGlobalData<WordPointer> fdPointer = CGlobalDataFactory.createWord(Word.zero(), null, true);
    /**
     * The address of the memory mapped jitdump file.
     */
    private static final CGlobalData<WordPointer> jitdumpMappedAddress = CGlobalDataFactory.createWord(Word.nullPointer(), null, true);
    /**
     * A unique index for a {@link JitdumpEntry.CodeLoadRecord code load record}. This is used by
     * perf to generate one .so file per {@code code load record}
     * ({@literal 'jitted-<pid>-<code_index>.so'}) containing the run-time compiled code and debug
     * info from the corresponding {@link JitdumpEntry.DebugInfoRecord debug info record}.
     */
    private static final GlobalAtomicLong codeIndex = new GlobalAtomicLong("JITDUMP_CODE_INDEX", 0L);

    @Fold
    static RawFileOperationSupport getFileSupport() {
        return RawFileOperationSupport.nativeByteOrder();
    }

    /**
     * The file name of a jitdump file is defined as {@literal jit-<pid>.dump}. The file will be
     * placed in the directory as specified by {@link Options#RuntimeJitdumpDir}.
     * 
     * @return the full path of the jitdump file
     */
    public static Path getJitdumpPath() {
        return Paths.get(Options.RuntimeJitdumpDir.getValue(), "jit-" + ProcessProperties.getProcessID() + ".dump");
    }

    /**
     * Create a jitdump file, write the jitdump header, and set up the {@link JitdumpProvider} for
     * processing run-time debug info of a native image.
     * <p>
     * Produces a mmap event for perf by mapping the jitdump file to virtual memory. This allows
     * perf to find and parse the jitdump file during {@code perf inject}. {@code perf inject}
     * creates one .so file per run-time compilation containing the compiled code and debug info
     * related to a code load record and injects references to the .so files into the perf profiling
     * data.
     * <p>
     * Only if the file was created and the memory mapping was successful, a
     * {@link JitdumpEntry.FileHeader jitdump header} is written to the jitdump file.
     *
     * @return the startup hook that only runs for the first isolate
     */
    public static RuntimeSupport.Hook startupHook() {
        return isFirstIsolate -> {
            if (!isFirstIsolate) {
                return;
            }

            /* Fetch the jitdump path and create parent directories. */
            Path jitdumpPath = getJitdumpPath();
            try {
                Files.createDirectories(jitdumpPath.getParent());
            } catch (IOException e) {
                LogUtils.warning("Failed to create directory for the jitdump file " + jitdumpPath.toFile().getAbsolutePath());
                return;
            }

            /* Create the jitdump file and get a raw file descriptor. */
            RawFileOperationSupport.RawFileDescriptor fd = getFileSupport().create(jitdumpPath.toFile(), RawFileOperationSupport.FileCreationMode.CREATE_OR_REPLACE,
                            RawFileOperationSupport.FileAccessMode.READ_WRITE);
            if (!getFileSupport().isValid(fd)) {
                LogUtils.warning("Failed to create the jitdump file " + jitdumpPath.toFile().getAbsolutePath());
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
                LogUtils.warning("Failed to prepare the jitdump file " + jitdumpPath.toFile().getAbsolutePath());
                return;
            }

            /* Store the open file descriptor and memory mapped region. */
            fdPointer.get().write(fd);
            jitdumpMappedAddress.get().write(mappedAddress);

            /* Write the jitdump header. */
            writeHeader(fd);
        };
    }

    /**
     * Close the jitdump file and clean up the {@link JitdumpProvider}.
     * <p>
     * If the {@link #startupHook()} ran at startup and successfully set up the
     * {@code JitdumpProvider} the optional {@link #writeCloseRecord close record} is written, the
     * jitdump file is closed, and the memory mapped region is unmapped. .
     * <p>
     * If the startup hook did not run or failed to create the jitdump file, the shutdown hook has
     * no effect.
     *
     * @return the shutdown hook that only runs for the first isolate
     */
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
                /* Free the memory mapped region with a munmap call. */
                VirtualMemoryProvider.get().free(mappedAddress, pageSize);
                /* Clear address of memory mapped region. */
                jitdumpMappedAddress.get().write(Word.nullPointer());
            }
        };
    }

    private static boolean writeToFile(RawFileOperationSupport.RawFileDescriptor fd, Pointer data, UnsignedWord size) {
        assert getFileSupport().isValid(fd) : "File descriptor for jitdump file must be initialized before writing to it";
        return getFileSupport().write(fd, data, size);
    }

    private static boolean writeToFile(RawFileOperationSupport.RawFileDescriptor fd, byte[] data) {
        assert getFileSupport().isValid(fd) : "File descriptor for jitdump file must be initialized before writing to it";
        return getFileSupport().write(fd, data);
    }

    private static long getTimestamp() {
        // Get a CLOCK_MONOTONIC timestamp.
        Time.timespec tp = StackValue.get(Time.timespec.class);
        int status = LinuxTime.NoTransitions.clock_gettime(LinuxTime.CLOCK_MONOTONIC(), tp);
        PosixUtils.checkStatusIs0(status, "JitdumpProvider.getTimestamp(): clock_gettime(CLOCK_MONOTONIC) failed.");
        return tp.tv_sec() * TimeUtils.nanosPerSecond + tp.tv_nsec();
    }

    /**
     * Create a {@link JitdumpEntry.FileHeader jitdump header} and writes it to the jitdump file.
     * 
     * @param fd the file descriptor to write to.
     */
    private static void writeHeader(RawFileOperationSupport.RawFileDescriptor fd) {
        int headerSize = SizeOf.get(JitdumpEntry.FileHeader.class);

        JitdumpEntry.FileHeader fileHeader = StackValue.get(headerSize);
        fileHeader.setMagic(MAGIC);
        fileHeader.setVersion(VERSION);
        fileHeader.setTotalSize(headerSize);
        fileHeader.setElfMach(ELFMachine.from(ImageSingletons.lookup(Platform.class).getArchitecture()).toShort());
        fileHeader.setPad1(0);  // Padding for future use.
        fileHeader.setPid(NumUtil.safeToInt(ProcessProperties.getProcessID()));
        fileHeader.setTimestamp(getTimestamp());
        fileHeader.setFlags(0);  // No flags needed.

        boolean success = writeToFile(fd, (Pointer) fileHeader, Word.unsigned(headerSize));
        assert success : "Failed to write jitdump header";
    }

    /**
     * Create a code close record and append it to the jitdump file.
     * <p>
     * A code close record only consists of a {@link JitdumpEntry.RecordHeader} with the record id
     * {@link JitdumpEntry.RecordType#JIT_CODE_CLOSE} and no record body.
     * 
     * @param fd the file descriptor to write to.
     */
    private static void writeCloseRecord(RawFileOperationSupport.RawFileDescriptor fd) {
        int closeRecordSize = SizeOf.get(JitdumpEntry.RecordHeader.class);

        JitdumpEntry.RecordHeader closeRecord = StackValue.get(closeRecordSize);
        closeRecord.setId(JitdumpEntry.RecordType.JIT_CODE_CLOSE.getCValue());
        closeRecord.setTotalSize(closeRecordSize);
        closeRecord.setTimestamp(getTimestamp());

        boolean success = writeToFile(fd, (Pointer) closeRecord, Word.unsigned(closeRecordSize));
        assert success : "Failed to write jitdump close record";
    }

    /**
     * Create a {@link JitdumpEntry.CodeLoadRecord code load record} and a
     * {@link JitdumpEntry.DebugInfoRecord debug info record} for a run-time compilation and append
     * them to the jitdump file.
     * <p>
     * As perf processes the records one after another and there are no links between jitdump
     * records, a debug info record must come before the corresponding code load record. If e.g. two
     * debug info records follow another, the first one is dropped and the second one is used as
     * debug info record of the next code load record.
     *
     * @param debugInfoProvider the debug info provider that generated the compiled method entry
     * @param compiledMethodEntry the {@code CompiledMethodEntry} of the run-time compiled method
     */
    public static void writeRecords(SubstrateDebugInfoProvider debugInfoProvider, CompiledMethodEntry compiledMethodEntry) {
        MethodEntry methodEntry = compiledMethodEntry.primary().getMethodEntry();

        /* Calculate the total size for the record headers and ByteBuffer. */
        int deSize = SizeOf.get(JitdumpEntry.DebugEntry.class);
        int debugEntriesSize = compiledMethodEntry.topDownRangeStream(false)
                        .mapToInt(r -> {
                            try (CTypeConversion.CCharPointerHolder filename = CTypeConversion.toCString(r.getMethodEntry().getFullFileName())) {
                                /* Filename +1 for null-termination. */
                                int filenameLen = NumUtil.safeToInt(SubstrateUtil.strlen(filename.get()).rawValue()) + 1;
                                return deSize + filenameLen;
                            }
                        })
                        .sum();
        int debugInfoRecordSize = SizeOf.get(JitdumpEntry.DebugInfoRecord.class);
        int debugInfoRecordTotalSize = debugInfoRecordSize + debugEntriesSize;

        /* Symbol name +1 for null-termination. */
        int symbolNameLen = 1;
        try (CTypeConversion.CCharPointerHolder symbolName = CTypeConversion.toCString(methodEntry.getSymbolName())) {
            symbolNameLen += NumUtil.safeToInt(SubstrateUtil.strlen(symbolName.get()).rawValue());
        }
        int codeSize = debugInfoProvider.getCompilation().getTargetCodeSize();
        int codeLoadRecordSize = SizeOf.get(JitdumpEntry.CodeLoadRecord.class);
        int codeLoadRecordTotalSize = codeLoadRecordSize + codeSize + symbolNameLen;

        /*
         * Setup ByteBuffer for filling it with jitdump records. The ByteBuffer is needed here
         * because the size of the jitdump records is not known at compile time. We also want to
         * write both records with a single write operation to the file to ensure they are written
         * right after each other.
         */
        ByteBuffer content = ByteBuffer.allocate(debugInfoRecordTotalSize + codeLoadRecordTotalSize);
        content.order(ByteOrder.nativeOrder());

        /*
         * First add the debug info record for the run-time compiled method.
         */
        JitdumpEntry.DebugInfoRecord di = StackValue.get(debugInfoRecordSize);
        /* Fill the jitdump record header. */
        di.setId(JitdumpEntry.RecordType.JIT_CODE_DEBUG_INFO.getCValue());
        di.setTotalSize(debugInfoRecordTotalSize);
        di.setTimestamp(getTimestamp());
        /* Fill the debug info record fields. */
        di.setCodeAddr(compiledMethodEntry.primary().getCodeOffset());
        List<Range> ranges = compiledMethodEntry.topDownRangeStream(false).toList();
        di.setNrEntry(ranges.size());

        content.put(CTypeConversion.asByteBuffer(di, debugInfoRecordSize));

        JitdumpEntry.DebugEntry de = StackValue.get(deSize);
        ByteBuffer deBuffer = CTypeConversion.asByteBuffer(de, deSize);
        /* Add all debug entries. */
        for (Range range : ranges) {
            de.setCodeAddr(range.getLo());
            de.setLine(range.getLine());
            de.setDiscrim(0);

            /*
             * Add debug entry and reset the corresponding buffer to be ready for adding the next
             * debug entry.
             */
            content.put(deBuffer);
            deBuffer.clear();

            try (CTypeConversion.CCharPointerHolder filename = CTypeConversion.toCString(range.getMethodEntry().getFullFileName())) {
                /* Filename +1 for null-termination. */
                int filenameLen = NumUtil.safeToInt(SubstrateUtil.strlen(filename.get()).rawValue()) + 1;
                content.put(CTypeConversion.asByteBuffer(filename.get(), filenameLen));
            }
        }
        assert content.position() == debugInfoRecordTotalSize : "Written " + content.position() + " bytes as jitdump debug info record, should be " + debugInfoRecordTotalSize + " bytes";

        /*
         * Add the corresponding code load record after the debug info record.
         */
        JitdumpEntry.CodeLoadRecord cl = StackValue.get(codeLoadRecordSize);

        /* Fill the jitdump record header. */
        cl.setId(JitdumpEntry.RecordType.JIT_CODE_LOAD.getCValue());
        cl.setTotalSize(codeLoadRecordTotalSize);
        cl.setTimestamp(getTimestamp());

        /* Fill the debug info record fields. */
        cl.setPid(NumUtil.safeToInt(ProcessProperties.getProcessID()));
        cl.setTid(NumUtil.safeToInt(Thread.currentThread().threadId()));
        // Virtual address = address.
        cl.setVma(debugInfoProvider.getCodeAddress());
        cl.setCodeAddr(debugInfoProvider.getCodeAddress());
        cl.setCodeSize(codeSize);
        // Code index -> unique identifier for run-time compiled code.
        cl.setCodeIndex(codeIndex.getAndIncrement());

        content.put(CTypeConversion.asByteBuffer(cl, codeLoadRecordSize));
        try (CTypeConversion.CCharPointerHolder symbolName = CTypeConversion.toCString(methodEntry.getSymbolName())) {
            /* Add symbol name and raw bytes of the compiled code. */
            content.put(CTypeConversion.asByteBuffer(symbolName.get(), symbolNameLen));
        }
        content.put(debugInfoProvider.getCompilation().getTargetCode(), 0, codeSize);
        assert content.remaining() == 0 : "Missing " + content.remaining() + " byte(s) in the jitdump records for " + methodEntry.getMethodName() + ".";

        /*
         * Append records to the jitdump file. If the file descriptor is invalid (e.g. if the file
         * is already closed), do not write to it.
         */
        RawFileOperationSupport.RawFileDescriptor fd = fdPointer.get().read();
        if (getFileSupport().isValid(fd)) {
            boolean success = writeToFile(fd, content.array());
            assert success : "Failed to write debug info record and code load record";
        }
    }
}
