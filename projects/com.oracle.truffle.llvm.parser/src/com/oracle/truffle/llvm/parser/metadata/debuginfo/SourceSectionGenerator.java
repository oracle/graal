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
package com.oracle.truffle.llvm.parser.metadata.debuginfo;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.parser.metadata.MDAttachment;
import com.oracle.truffle.llvm.parser.metadata.MDBaseNode;
import com.oracle.truffle.llvm.parser.metadata.MDCompileUnit;
import com.oracle.truffle.llvm.parser.metadata.MDFile;
import com.oracle.truffle.llvm.parser.metadata.MDKind;
import com.oracle.truffle.llvm.parser.metadata.MDLexicalBlock;
import com.oracle.truffle.llvm.parser.metadata.MDLexicalBlockFile;
import com.oracle.truffle.llvm.parser.metadata.MDLocation;
import com.oracle.truffle.llvm.parser.metadata.MDNamedNode;
import com.oracle.truffle.llvm.parser.metadata.MDOldNode;
import com.oracle.truffle.llvm.parser.metadata.MDReference;
import com.oracle.truffle.llvm.parser.metadata.MDSubprogram;
import com.oracle.truffle.llvm.parser.metadata.MDTypedValue;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.Call;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.Instruction;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;

final class SourceSectionGenerator {

    private final Map<MDFile, Source> sources;

    SourceSectionGenerator() {
        sources = new HashMap<>();
    }

    private final class SSVisitor implements MDFollowRefVisitor {

        private Source source = null;

        private long line = -1;
        private long column = -1;

        private String sourceName = null;
        private String sourceMimeType = null;

        @Override
        public void visit(MDLocation md) {
            updateLocationInSource(md.getLine(), md.getColumn());
            md.getScope().accept(this);
        }

        @Override
        public void visit(MDFile md) {
            if (sources.containsKey(md)) {
                source = sources.get(md);

            } else {
                File file = md.asFile();
                sourceName = file.getName();
                sourceMimeType = getMimeType(file);

                if (file.exists()) {
                    try {
                        source = Source.newBuilder(file).mimeType(sourceMimeType).name(sourceName).build();
                        sources.put(md, source);
                    } catch (IOException e) {
                        throw new IllegalStateException(String.format("Could not access Source: %s\ncaused by %s", file.getAbsolutePath(), e.getMessage()), e);
                    }
                }
            }
        }

        @Override
        public void visit(MDLexicalBlock md) {
            updateLocationInSource(md.getLine(), md.getColumn());
            md.getFile().accept(this);
        }

        @Override
        public void visit(MDLexicalBlockFile md) {
            md.getFile().accept(this);
        }

        @Override
        public void visit(MDSubprogram md) {
            updateLocationInSource(md.getLine(), 1);
            md.getFile().accept(this);
        }

        @Override
        public void ifVisitNotOverwritten(MDBaseNode md) {
        }

        private void updateLocationInSource(long newLine, long newCol) {
            if (line <= 0) {
                line = newLine;
                column = newCol;
            }
        }

        SourceSection generateSourceSection() {
            if (line < 0) {
                // -1 indicates that we have not found any metadata
                return null;
            }

            if (source != null) {
                SourceSection actualLocation = createSection(source, line, column);
                if (actualLocation != null) {
                    // if debug information and the actual file do not match or if the file is
                    // inaccessible we fall back to creating a dummy source to at lest preserve some
                    // information for stacktraces
                    return actualLocation;
                }
            }

            if (sourceName == null) {
                sourceName = "<unavailable source>";
            }
            if (sourceMimeType == null) {
                sourceMimeType = MIMETYPE_PLAINTEXT;
            }

            StringBuilder builder = new StringBuilder();
            for (int i = 1; i < line; i++) {
                builder.append('\n');
            }
            for (int i = 0; i < column; i++) {
                builder.append(' ');
            }
            builder.append('\n');

            return createSection(Source.newBuilder(builder.toString()).mimeType(sourceMimeType).name(sourceName).build(), line, column);
        }
    }

    private static SourceSection createSection(Source source, long line, long column) {
        try {
            if (line <= 0) {
                // this happens e.g. for functions implicitly generated by llvm in section
                // '.text.startup'
                return source.createSection(1);

            } else if (column <= 0) {
                // columns in llvm 3.2 metadata are usually always 0
                return source.createSection((int) line);

            } else {
                return source.createSection((int) line, (int) column, 0);
            }

        } catch (Throwable ignored) {
            // if the source file has changed since it was last compiled the line and column
            // information in the metadata might not be accurate anymore
            return null;
        }
    }

    SourceSection getOrDefault(Instruction instruction) {
        MDLocation mdLocation = instruction.getDebugLocation();
        if (mdLocation == null) {
            return null;
        }

        SSVisitor visitor = new SSVisitor();
        mdLocation.accept(visitor);
        if (visitor.line <= 0 && instruction instanceof Call) {
            Symbol callTarget = ((Call) instruction).getCallTarget();
            if (callTarget instanceof FunctionDefinition) {
                final MDBaseNode di = getFunctionDI((FunctionDefinition) callTarget);
                if (di != null) {
                    di.accept(visitor);
                }
            }
        }

        return visitor.generateSourceSection();
    }

    SourceSection getOrDefault(FunctionDefinition function) {
        final MDBaseNode di = getFunctionDI(function);
        if (di != null) {
            SSVisitor visitor = new SSVisitor();
            di.accept(visitor);
            return visitor.generateSourceSection();
        }
        return null;
    }

    private static MDBaseNode getFunctionDI(FunctionDefinition function) {
        final MDBaseNode functionAttachment = function.getMetadataAttachment(MDKind.DBG_NAME);
        if (functionAttachment != null) {
            return functionAttachment;
        }

        final MDNamedNode cuNode = function.getMetadata().find(MDNamedNode.COMPILEUNIT_NAME);
        if (cuNode == null) {
            return null;
        }

        final FindOldSubprogramVisitor visitor = new FindOldSubprogramVisitor(function);
        for (MDReference ref : cuNode) {
            if (ref != MDReference.VOID) {
                final MDBaseNode target = ref.get();
                if (target instanceof MDCompileUnit) {
                    final MDCompileUnit cu = (MDCompileUnit) target;
                    if (cu.getSubprograms() != MDReference.VOID) {
                        cu.getSubprograms().accept(visitor);
                        if (visitor.found != null) {
                            return visitor.found;
                        }
                    }
                }
            }
        }

        return null;
    }

    private static final class FindOldSubprogramVisitor implements MDFollowRefVisitor {

        private final FunctionDefinition search;

        private MDSubprogram found = null;

        private FindOldSubprogramVisitor(FunctionDefinition search) {
            this.search = search;
        }

        @Override
        public void visit(MDOldNode md) {
            for (MDTypedValue value : md) {
                if (value instanceof MDReference) {
                    ((MDReference) value).accept(this);
                }
            }
        }

        @Override
        public void visit(MDSubprogram md) {
            if (search.equals(md.getFunction().get())) {
                found = md;
            }
        }
    }

    static final String MIMETYPE_PLAINTEXT = "text/plain";

    private static String getMimeType(File file) {
        String path = file.getPath();
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex <= 0) {
            return MIMETYPE_PLAINTEXT;
        }
        String fileExtension = path.substring(dotIndex + 1);
        switch (fileExtension) {
            case "c":
                return "text/x-c";
            case "h":
                return "text/x-h";
            case "f":
            case "f90":
            case "for":
                return "text/x-fortran";
            default:
                return MIMETYPE_PLAINTEXT;
        }
    }

}
