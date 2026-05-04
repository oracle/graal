/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.graalvm.wasm.test.suites.debugging;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.collections.EconomicMap;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.collection.ByteArrayList;
import org.graalvm.wasm.debugging.data.DebugFunction;
import org.graalvm.wasm.debugging.encoding.Attributes;
import org.graalvm.wasm.debugging.encoding.Opcodes;
import org.graalvm.wasm.debugging.encoding.Tags;
import org.graalvm.wasm.debugging.parser.DebugTranslator;
import org.graalvm.wasm.debugging.parser.DebugUtil;
import org.graalvm.wasm.test.AbstractBinarySuite;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.source.SourceSection;

public class DebugSourceLoadSuite extends AbstractBinarySuite {
    private static final int DW_FORM_ADDR = 0x01;
    private static final int DW_FORM_STRING = 0x08;
    private static final int DW_FORM_UDATA = 0x0F;
    private static final int DW_FORM_EXPRLOC = 0x18;

    private static final int FUNCTION_START_OFFSET = 2;
    private static final int FIRST_INSTRUCTION_OFFSET = 3;
    private static final int FUNCTION_END_OFFSET = 7;

    @Test
    public void testTrustedContextLoadsCSourceFromDwarfPath() throws IOException {
        final String sourceContents = "int main(void) {\n    return 0;\n}\n";
        final Path sourceFile = Files.createTempFile("graalwasm-dwarf-source", ".c").toAbsolutePath();
        Files.writeString(sourceFile, sourceContents, StandardCharsets.UTF_8);

        final byte[] data = buildModuleWithDwarfSourcePath(sourceFile.toString(), "main");
        final Source source = Source.newBuilder(WasmLanguage.ID, ByteSequence.create(data), "trusted_dwarf_path").build();

        try (Context context = Context.newBuilder(WasmLanguage.ID).sandbox(SandboxPolicy.TRUSTED).allowIO(IOAccess.ALL).build()) {
            Assert.assertEquals(sourceContents, observeSourceContents(context, source));
        } finally {
            Files.deleteIfExists(sourceFile);
        }
    }

    @Test
    public void testSandboxedContextDoesNotLoadSecretWithDenyIOFileSystem() throws IOException {
        final String secretContents = "secret from attacker-controlled DWARF path\n";
        final Path secretFile = Files.createTempFile("graalwasm-dwarf-secret", ".txt").toAbsolutePath();
        Files.writeString(secretFile, secretContents, StandardCharsets.UTF_8);
        final RecordingFileSystem fileSystem = new RecordingFileSystem(FileSystem.newDenyIOFileSystem(), secretFile);

        final byte[] data = buildModuleWithDwarfSourcePath(secretFile.toString(), "made_up_debug_function");
        final Source source = Source.newBuilder(WasmLanguage.ID, ByteSequence.create(data), "untrusted_dwarf_path").build();

        final Context.Builder contextBuilder = Context.newBuilder(WasmLanguage.ID).sandbox(SandboxPolicy.CONSTRAINED);
        contextBuilder.in(InputStream.nullInputStream());
        contextBuilder.out(OutputStream.nullOutputStream());
        contextBuilder.err(OutputStream.nullOutputStream());
        contextBuilder.allowIO(IOAccess.newBuilder().fileSystem(fileSystem).build());
        try (Context context = contextBuilder.build()) {
            Assert.assertNull(observeSourceContents(context, source));
        } finally {
            Files.deleteIfExists(secretFile);
        }
        Assert.assertTrue("Sandboxed contexts must resolve DWARF source paths through the configured file system.", fileSystem.wasTargetPathAccessed());
    }

    @Test
    public void testSandboxedContextLoadsSourceThroughConfiguredFileSystem() throws IOException {
        final String sourceContents = "int main(void) {\n    return 0;\n}\n";
        final Path sourceRoot = Files.createTempDirectory("graalwasm-dwarf-source-root").toAbsolutePath();
        final Path sourceFile = sourceRoot.resolve("main.c");
        Files.writeString(sourceFile, sourceContents, StandardCharsets.UTF_8);
        final RecordingFileSystem fileSystem = new RecordingFileSystem(FileSystem.newDefaultFileSystem(), sourceFile);

        final byte[] data = buildModuleWithDwarfSourcePath(sourceFile.toString(), "main");
        final Source source = Source.newBuilder(WasmLanguage.ID, ByteSequence.create(data), "configured_dwarf_path").build();

        final Context.Builder contextBuilder = Context.newBuilder(WasmLanguage.ID).sandbox(SandboxPolicy.CONSTRAINED);
        contextBuilder.in(InputStream.nullInputStream());
        contextBuilder.out(OutputStream.nullOutputStream());
        contextBuilder.err(OutputStream.nullOutputStream());
        contextBuilder.allowIO(IOAccess.newBuilder().fileSystem(fileSystem).build());
        try (Context context = contextBuilder.build()) {
            Assert.assertEquals(sourceContents, observeSourceContents(context, source));
        } finally {
            Files.deleteIfExists(sourceFile);
            Files.deleteIfExists(sourceRoot);
        }
        Assert.assertTrue("DWARF source paths should be loaded through the configured file system.", fileSystem.wasTargetPathAccessed());
    }

    private static String observeSourceContents(Context context, Source source) {
        final AtomicReference<String> observedContents = new AtomicReference<>();
        final SourceCaptureInstrument instrument = context.getEngine().getInstruments().get(SourceCaptureInstrument.ID).lookup(SourceCaptureInstrument.class);
        instrument.captureNextSourceContents(observedContents);
        final Value exports = context.eval(source).newInstance().getMember("exports");
        exports.getMember("_main").execute();
        return observedContents.get();
    }

    @TruffleInstrument.Registration(id = SourceCaptureInstrument.ID, //
                    services = SourceCaptureInstrument.class, //
                    sandbox = SandboxPolicy.UNTRUSTED)
    public static final class SourceCaptureInstrument extends TruffleInstrument {
        static final String ID = "wasm-debug-source-capture";

        private Env env;

        @Override
        protected void onCreate(Env environment) {
            env = environment;
            env.registerService(this);
        }

        void captureNextSourceContents(AtomicReference<String> observedContents) {
            final EventBinding<?>[] bindingRef = new EventBinding<?>[1];
            final SourceSectionFilter filter = SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build();
            bindingRef[0] = env.getInstrumenter().attachExecutionEventListener(filter, new ExecutionEventListener() {
                @Override
                public void onEnter(EventContext context, VirtualFrame frame) {
                    final SourceSection section = context.getInstrumentedSourceSection();
                    if (section != null) {
                        final String contents = section.getSource().hasCharacters() ? section.getSource().getCharacters().toString() : null;
                        observedContents.set(contents);
                        bindingRef[0].dispose();
                    }
                }

                @Override
                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                }

                @Override
                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                }
            });
        }
    }

    private static final class RecordingFileSystem implements FileSystem {
        private final FileSystem delegate;
        private final Path targetPath;
        private boolean targetPathAccessed;

        RecordingFileSystem(FileSystem delegate, Path targetPath) {
            this.delegate = Objects.requireNonNull(delegate);
            this.targetPath = targetPath.normalize();
        }

        boolean wasTargetPathAccessed() {
            return targetPathAccessed;
        }

        @Override
        public Path parsePath(URI uri) {
            final Path path = delegate.parsePath(uri);
            record(path);
            return path;
        }

        @Override
        public Path parsePath(String path) {
            final Path parsedPath = delegate.parsePath(path);
            record(parsedPath);
            return parsedPath;
        }

        @Override
        public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
            record(path);
            delegate.checkAccess(path, modes, linkOptions);
        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
            record(dir);
            delegate.createDirectory(dir, attrs);
        }

        @Override
        public void delete(Path path) throws IOException {
            record(path);
            delegate.delete(path);
        }

        @Override
        public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
            record(path);
            return delegate.newByteChannel(path, options, attrs);
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
            record(dir);
            return delegate.newDirectoryStream(dir, filter);
        }

        @Override
        public Path toAbsolutePath(Path path) {
            record(path);
            return delegate.toAbsolutePath(path);
        }

        @Override
        public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
            record(path);
            return delegate.toRealPath(path, linkOptions);
        }

        @Override
        public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
            record(path);
            return delegate.readAttributes(path, attributes, options);
        }

        private void record(Path path) {
            if (path != null && path.normalize().equals(targetPath)) {
                targetPathAccessed = true;
            }
        }
    }

    private static byte[] buildModuleWithDwarfSourcePath(String sourcePath, String functionName) {
        final byte[] debugInfo = debugInfo(functionName);
        final byte[] debugAbbrev = debugAbbrev();
        final byte[] debugLine = debugLine(sourcePath);
        assertDebugFunctionMapsToWasmFunction(debugInfo, debugAbbrev, debugLine);
        final BinaryBuilder builder = newBuilder();
        builder.addType(EMPTY_BYTES, EMPTY_BYTES);
        builder.addFunction((byte) 0, EMPTY_BYTES, "41 00 1A 0B");
        builder.addFunctionExport((byte) 0, "_main");
        builder.addCustomSection(DebugUtil.INFO_NAME, debugInfo);
        builder.addCustomSection(DebugUtil.ABBREV_NAME, debugAbbrev);
        builder.addCustomSection(DebugUtil.LINE_NAME, debugLine);
        return builder.build();
    }

    private static void assertDebugFunctionMapsToWasmFunction(byte[] debugInfo, byte[] debugAbbrev, byte[] debugLine) {
        final int debugInfoOffset = 0;
        final int debugSectionOffsetsOffset = debugInfoOffset + debugInfo.length;
        final int debugAbbrevOffset = debugSectionOffsetsOffset + DebugUtil.CUSTOM_DATA_SIZE;
        final int debugLineOffset = debugAbbrevOffset + debugAbbrev.length;
        final ByteArrayList customData = new ByteArrayList();
        customData.addRange(debugInfo, 0, debugInfo.length);
        for (int i = 0; i < DebugUtil.CUSTOM_DATA_SIZE; i++) {
            customData.add((byte) 0xFF);
        }
        customData.addRange(debugAbbrev, 0, debugAbbrev.length);
        customData.addRange(debugLine, 0, debugLine.length);
        setU32(customData, debugSectionOffsetsOffset, debugAbbrevOffset);
        setU32(customData, debugSectionOffsetsOffset + 4, debugAbbrev.length);
        setU32(customData, debugSectionOffsetsOffset + 8, debugInfoOffset);
        setU32(customData, debugSectionOffsetsOffset + 12, debugInfo.length);
        setU32(customData, debugSectionOffsetsOffset + 16, debugLineOffset);
        setU32(customData, debugSectionOffsetsOffset + 20, debugLine.length);
        final byte[] data = customData.toArray();
        final EconomicMap<Integer, DebugFunction> debugFunctions = new DebugTranslator(data).readCompilationUnits(data, debugSectionOffsetsOffset);
        Assert.assertTrue(debugFunctions.containsKey(FUNCTION_START_OFFSET));
    }

    private static byte[] debugInfo(String functionName) {
        final ByteArrayList data = new ByteArrayList();
        // CU header accepted by DebugParser: DWARF v4, abbrev table at offset 0, 32-bit addresses.
        addU32(data, 0); // unit_length, patched below
        addU16(data, 4); // version
        addU32(data, 0); // debug_abbrev_offset
        data.add((byte) 4); // address_size

        // Compile unit DIE. These are the minimal fields DebugTranslator requires before it will
        // parse child functions: language, line-table offset, compilation dir, and a PC range.
        addUnsignedInt32(data, 1); // abbrev_code: compile unit
        addUnsignedInt32(data, 2); // DW_AT_language: DW_LANG_C
        addUnsignedInt32(data, 0); // DW_AT_stmt_list
        addString(data, "/"); // DW_AT_comp_dir
        addU32(data, FUNCTION_START_OFFSET); // DW_AT_low_pc
        addU32(data, FUNCTION_END_OFFSET); // DW_AT_high_pc

        // Subprogram DIE. The name only needs to be non-null; DECL_FILE selects the attacker path
        // from .debug_line, and the empty FRAME_BASE expression satisfies DebugObjectFactory.
        addUnsignedInt32(data, 2); // abbrev_code: subprogram
        addString(data, functionName); // DW_AT_name
        addUnsignedInt32(data, 1); // DW_AT_decl_file
        addU32(data, FUNCTION_START_OFFSET); // DW_AT_low_pc
        addU32(data, FUNCTION_END_OFFSET); // DW_AT_high_pc
        addUnsignedInt32(data, 0); // DW_AT_frame_base exprloc length

        addUnsignedInt32(data, 0); // null child entry
        setU32(data, 0, data.size() - 4);
        return data.toArray();
    }

    private static byte[] debugAbbrev() {
        final ByteArrayList data = new ByteArrayList();
        // Abbrev 1 describes the compile unit DIE above and says it has one child DIE.
        addUnsignedInt32(data, 1); // abbrev_code
        addUnsignedInt32(data, Tags.COMPILATION_UNIT);
        data.add((byte) 1); // has_children
        addAttribute(data, Attributes.LANGUAGE, DW_FORM_UDATA);
        addAttribute(data, Attributes.STMT_LIST, DW_FORM_UDATA);
        addAttribute(data, Attributes.COMP_DIR, DW_FORM_STRING);
        addAttribute(data, Attributes.LOW_PC, DW_FORM_ADDR);
        addAttribute(data, Attributes.HIGH_PC, DW_FORM_ADDR);
        addAttribute(data, 0, 0); // end attributes

        // Abbrev 2 describes the subprogram DIE that GraalWasm turns into a DebugFunction.
        addUnsignedInt32(data, 2); // abbrev_code
        addUnsignedInt32(data, Tags.SUBPROGRAM);
        data.add((byte) 0); // has_children
        addAttribute(data, Attributes.NAME, DW_FORM_STRING);
        addAttribute(data, Attributes.DECL_FILE, DW_FORM_UDATA);
        addAttribute(data, Attributes.LOW_PC, DW_FORM_ADDR);
        addAttribute(data, Attributes.HIGH_PC, DW_FORM_ADDR);
        addAttribute(data, Attributes.FRAME_BASE, DW_FORM_EXPRLOC);
        addAttribute(data, 0, 0); // end attributes

        addUnsignedInt32(data, 0); // end abbrev table
        return data.toArray();
    }

    private static byte[] debugLine(String sourcePath) {
        final ByteArrayList data = new ByteArrayList();
        // Line table header: DWARF v4 with only DW_LNS_copy in the standard opcode table.
        addU32(data, 0); // unit_length, patched below
        addU16(data, 4); // version
        addU32(data, 0); // header_length, patched below
        final int headerStart = data.size();

        data.add((byte) 1); // minimum_instruction_length
        data.add((byte) 1); // maximum_operations_per_instruction
        data.add((byte) 1); // default_is_stmt
        data.add((byte) 0); // line_base
        data.add((byte) 1); // line_range
        data.add((byte) 2); // opcode_base
        final byte[] standardOpcodeLengths = {0}; // length of DW_LNS_COPY opcode
        data.addRange(standardOpcodeLengths, 0, standardOpcodeLengths.length);
        data.add((byte) 0); // include_directories terminator

        // One file entry is enough. Since it is absolute, DebugParser stores it directly as the
        // source path that DebugSourceLoader later opens via Env.getPublicTruffleFile.
        addString(data, sourcePath); // file name
        addUnsignedInt32(data, 0); // directory index
        addUnsignedInt32(data, 0); // last modification time
        addUnsignedInt32(data, 0); // file length
        data.add((byte) 0); // file_names terminator

        setU32(data, 6, data.size() - headerStart);

        // Minimal line program: associate a line with the first real wasm instruction so statement
        // instrumentation can materialize a SourceSection for the function.
        data.add((byte) Opcodes.EXTENDED_OPCODE);
        addUnsignedInt32(data, 5); // extended opcode length
        data.add((byte) Opcodes.LNE_SET_ADDRESS);
        addU32(data, FIRST_INSTRUCTION_OFFSET); // address
        data.add((byte) Opcodes.LNS_COPY);
        data.add((byte) Opcodes.EXTENDED_OPCODE);
        addUnsignedInt32(data, 1); // extended opcode length
        data.add((byte) Opcodes.LNE_END_SEQUENCE);

        setU32(data, 0, data.size());
        return data.toArray();
    }

    private static void addAttribute(ByteArrayList data, int attribute, int form) {
        addUnsignedInt32(data, attribute);
        addUnsignedInt32(data, form);
    }

    private static void addString(ByteArrayList data, String value) {
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        data.addRange(bytes, 0, bytes.length);
        data.add((byte) 0);
    }

    private static void addUnsignedInt32(ByteArrayList data, int valueArg) {
        int value = valueArg;
        while (true) {
            int b = value & 0x7f;
            value >>>= 7;
            if (value == 0) {
                data.add((byte) b);
                break;
            }
            data.add((byte) (b | 0x80));
        }
    }

    private static void addU16(ByteArrayList data, int value) {
        data.add((byte) (value & 0xFF));
        data.add((byte) ((value >>> 8) & 0xFF));
    }

    private static void addU32(ByteArrayList data, int value) {
        data.add((byte) (value & 0xFF));
        data.add((byte) ((value >>> 8) & 0xFF));
        data.add((byte) ((value >>> 16) & 0xFF));
        data.add((byte) ((value >>> 24) & 0xFF));
    }

    private static void setU32(ByteArrayList data, int offset, int value) {
        data.set(offset, (byte) (value & 0xFF));
        data.set(offset + 1, (byte) ((value >>> 8) & 0xFF));
        data.set(offset + 2, (byte) ((value >>> 16) & 0xFF));
        data.set(offset + 3, (byte) ((value >>> 24) & 0xFF));
    }
}
