/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.disassembler;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * A Panama-based visitor for hsdis decode_instructions_virtual using the stable Panama API (JDK
 * 25).
 */
@SuppressWarnings("restricted")
public class PanamaDisassemblerVisitor extends HotSpotDisassembler.Visitor {
    /**
     * The path to the copy of the hsdis library for this platform. If this is unset then the
     * library is looked up from the platform library path.
     */
    private static final String DISASSEMBLER_PROPERTY = "test.jdk.graal.compiler.disassembler.path";

    private static SymbolLookup getHsDisLookup() {
        String arch = System.getProperty("os.arch");
        String hsdisLib = switch (arch) {
            case "x86_64", "amd64" -> "hsdis-amd64";
            case "aarch64", "arm64" -> "hsdis-aarch64";
            default -> throw new IllegalArgumentException("Unsupported ISA: " + arch);
        };
        // hsdis uses a non-standard naming so drop the leading lib part of the name
        String libraryName = System.mapLibraryName(hsdisLib);
        if (libraryName.startsWith("lib")) {
            libraryName = libraryName.substring(3);
        }

        String libpath = System.getProperty(DISASSEMBLER_PROPERTY);
        if (libpath != null) {
            // Load from an explicitly specified path
            Path lib = Path.of(libpath);
            if (Files.isDirectory(lib)) {
                lib = lib.resolve(libraryName);
            }
            return SymbolLookup.libraryLookup(lib, Arena.global());
        } else {
            /*
             * Try to load the normal hsdis library from the library path. This will find hsdis that
             * has been installed in the JDK itself or if it has been specified through
             * LD_LIBRARY_PATH
             */
            return SymbolLookup.libraryLookup(libraryName, Arena.global());
        }
    }

    /**
     * hsdis decode_instructions_virtual.
     */
    private static final FunctionDescriptor DECODE_FN_DESC = FunctionDescriptor.of(ValueLayout.ADDRESS,
                    ValueLayout.JAVA_LONG, // start_va
                    ValueLayout.JAVA_LONG, // end_va
                    ValueLayout.ADDRESS,   // buffer
                    ValueLayout.JAVA_LONG, // length
                    ValueLayout.ADDRESS,   // event_callback (function pointer)
                    ValueLayout.ADDRESS,   // event_stream
                    ValueLayout.ADDRESS,   // printf_callback (function pointer)
                    ValueLayout.ADDRESS,   // printf_stream
                    ValueLayout.ADDRESS,   // options (const char*)
                    ValueLayout.JAVA_INT   // newline
    );

    private static final FunctionDescriptor FMEMOPEN_FN_DESC = FunctionDescriptor.of(ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);

    private static final FunctionDescriptor INT_FILESTAR_FN_DESC = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS);

    private static final FunctionDescriptor VOID_FILESTAR_FN_DESC = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS);

    /**
     * An oversized buffer for writes from hsdis through fprintf. The FILE* only has access to N-1
     * so the entire buffer always appears NUL terminated.
     */
    private static final MemoryLayout BUFFER_LAYOUT = MemoryLayout.sequenceLayout(1024, ValueLayout.JAVA_BYTE);

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup HS_DIS_LOOKUP = getHsDisLookup();
    private static final SymbolLookup LIBC_LOOKUP = LINKER.defaultLookup();
    private static final MethodHandle rewindFunction = lookupDowncall(LIBC_LOOKUP, "rewind", VOID_FILESTAR_FN_DESC);
    private static final MethodHandle fflushFunction = lookupDowncall(LIBC_LOOKUP, "fflush", VOID_FILESTAR_FN_DESC);
    private static final MethodHandle fcloseFunction = lookupDowncall(LIBC_LOOKUP, "fclose", INT_FILESTAR_FN_DESC);
    private static final MethodHandle fmemopenFunction = lookupDowncall(LIBC_LOOKUP, "fmemopen", FMEMOPEN_FN_DESC);

    private static MethodHandle lookupDowncall(SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(lookup.find(name).orElseThrow(() -> new RuntimeException("Could not find " + name)), descriptor);
    }

    /**
     * The arena for all storage allocated by this instance.
     */
    private final Arena arena;

    /**
     * The native buffer containing the instructions to be decoded.
     */
    private final MemorySegment buffer;

    /**
     * The native buffer backing the FILE* created by fmemopen. This contains any output printed by
     * hsdis and is cleared and rewound after consuming the output through {@link #getRawOutput()}.
     */
    private final MemorySegment fileBuffer;

    /**
     * The native FILE* writing to {@link #fileBuffer}.
     */
    private final MemorySegment fileStar;

    public PanamaDisassemblerVisitor(HotSpotDisassembler disassembler, byte[] section, long startPc) {
        super(disassembler, section, startPc);
        this.arena = Arena.ofConfined();
        this.buffer = arena.allocate(section.length, 8);
        this.fileBuffer = arena.allocate(BUFFER_LAYOUT);
        clearFileBuffer();

        // Copy assembly bytes into the the C heap buffer
        for (int i = 0; i < section.length; ++i) {
            buffer.set(ValueLayout.JAVA_BYTE, i, section[i]);
        }

        try {
            // fmemopen fileBuffer for writing.
            this.fileStar = (MemorySegment) fmemopenFunction.invokeExact(fileBuffer, fileBuffer.byteSize() - 1, cstring(arena, "w"));
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<DecodedInstruction> visit() {
        // Event callback: void* (*event_callback)(void*, const char*, void*)
        FunctionDescriptor eventCallbackDesc = FunctionDescriptor.of(ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, // void* eventStream
                        ValueLayout.ADDRESS, // const char* eventMsg
                        ValueLayout.ADDRESS  // void* data
        );
        // Panama upcallStub expects a static MethodHandle
        java.lang.invoke.MethodHandle callbackHandle;
        try {
            callbackHandle = java.lang.invoke.MethodHandles.lookup().findStatic(
                            PanamaDisassemblerVisitor.class,
                            "eventCallbackStatic",
                            java.lang.invoke.MethodType.methodType(MemorySegment.class, MemorySegment.class, MemorySegment.class, MemorySegment.class));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to acquire callback MethodHandle for eventCallbackStatic", e);
        }
        MemorySegment eventCallback = Linker.nativeLinker().upcallStub(callbackHandle, eventCallbackDesc, arena);

        // printf_callback: use fprintf from libc
        MemorySegment fprintf = LIBC_LOOKUP.find("fprintf").orElseThrow(() -> new RuntimeException("Could not find fprintf"));

        MethodHandle decode = lookupDowncall(HS_DIS_LOOKUP, "decode_instructions_virtual", DECODE_FN_DESC);

        try {
            // Store this visitor in a thread local so the callback can dispatch (since static cb)
            CURRENT_VISITOR.set(this);
            MemorySegment memorySegment = (MemorySegment) decode.invokeExact(
                            startPc,
                            startPc + code.length,
                            buffer,
                            buffer.byteSize(),
                            eventCallback,
                            MemorySegment.NULL,
                            fprintf, // Pass function pointer, not MethodHandle
                            fileStar,
                            cstring(arena, disassembler.options),
                            0);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        } finally {
            CURRENT_VISITOR.remove();
            fclose();
        }

        return instructions;
    }

    // ThreadLocal to bridge static callback to instance method
    private static final ThreadLocal<PanamaDisassemblerVisitor> CURRENT_VISITOR = new ThreadLocal<>();

    /**
     * The C callback as required by Panama that matches the signature (void*, const char*, void*,
     * void*).
     */
    @SuppressWarnings("unused")
    private static MemorySegment eventCallbackStatic(MemorySegment eventStream, MemorySegment eventMsg, MemorySegment data) {
        try {
            PanamaDisassemblerVisitor visitor = CURRENT_VISITOR.get();
            if (visitor != null) {
                return visitor.handleEvent(eventMsg, data);
            }
        } catch (Throwable t) {
            /*
             * Don't allow exceptions to leak out as UpcallStubs calls
             * jdk.internal.foreign.abi.SharedUtils.handleUncaughtException which exits the VM when
             * uncaught exceptions are seen.
             */
            t.printStackTrace();
            CURRENT_VISITOR.remove();
        }
        return MemorySegment.NULL;
    }

    private MemorySegment handleEvent(MemorySegment eventMsg, MemorySegment data) {
        String event = eventMsg.reinterpret(Long.MAX_VALUE).getString(0L);
        long value = data.address();
        if (matchTag(event, "insn")) {
            // Defensively check for extraneous output as this signals something has gone wrong with
            // the decoding process.
            String s = getRawOutput();
            if (!s.isEmpty()) {
                System.err.println(getClass().getSimpleName() + ": Unexpected output seen: \"" + s + "\"");
            }
            startInstruction(value);
        } else if (matchTag(event, "/insn")) {
            /*
             * The event string includes some other information though this appears to be useless on
             * aarch64 as it always just " type='noninsn'"
             */
            endInstruction(value);
        } else if (matchTag(event, "mach")) {
            String architecture = data.reinterpret(Long.MAX_VALUE).getString(0L);
            notifyArchitecture(event, architecture);
        }

        return MemorySegment.NULL;
    }

    /**
     * Allocates a UTF-8 null-terminated string in the provided Arena, similar to the former
     * Arena.allocateUtf8String.
     *
     * @param arena the arena to use for allocation
     * @param str the Java string to encode and allocate
     * @return a MemorySegment pointing to the native null-terminated UTF-8 string
     */
    private static MemorySegment cstring(Arena arena, String str) {
        byte[] utf8 = (str + "\0").getBytes(StandardCharsets.UTF_8);
        MemorySegment segment = arena.allocate(utf8.length, 1); // 1-byte alignment
        segment.asByteBuffer().put(utf8);
        return segment;
    }

    @Override
    protected String getRawOutput() {
        fflush();
        // Convert char* buffer to String by scanning for null-terminator
        String result = cStringToJava(fileBuffer);
        // we've consumed the current contents so reset the stream
        rewindAndClear();
        return result;
    }

    // Minimal native C-string (null-terminated) to Java String utility (UTF-8 decode)
    private String cStringToJava(MemorySegment ptr) {
        int max = (int) ptr.byteSize();
        byte[] bytes = new byte[max];
        ptr.asByteBuffer().get(bytes);
        int len = 0;
        while (len < max && bytes[len] != 0) {
            len++;
        }
        return new String(bytes, 0, len, StandardCharsets.UTF_8);
    }

    @Override
    protected void startInstruction(long address) {
        rewindAndClear();
        super.startInstruction(address);
    }

    private void fclose() {
        try {
            int result = (int) fcloseFunction.invokeExact(fileStar);
            if (result != 0) {
                throw new IllegalStateException("fclose returned " + result);
            }
        } catch (RuntimeException t) {
            throw t;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private void rewindAndClear() {
        // Clear fileBuffer contents
        clearFileBuffer();
        try {
            rewindFunction.invokeExact(fileStar);
        } catch (RuntimeException t) {
            throw t;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * Zero out {@link #fileBuffer} so reads from it will be null-terminated.
     */
    private void clearFileBuffer() {
        fileBuffer.asSlice(0, fileBuffer.byteSize()).fill((byte) 0);
    }

    /**
     * Flush the {@link #fileStar} to ensure the contents are in {@link #fileBuffer}. Some
     * implementations use the buffer directly in the FILE* but other do not so an explicitly flush
     * may be necessary.
     */
    private void fflush() {
        // Clear fileBuffer contents
        try {
            fflushFunction.invokeExact(fileStar);
        } catch (RuntimeException t) {
            throw t;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
