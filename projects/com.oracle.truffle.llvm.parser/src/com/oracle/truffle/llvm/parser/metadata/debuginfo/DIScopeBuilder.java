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

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.parser.metadata.MDBaseNode;
import com.oracle.truffle.llvm.parser.metadata.MDBasicType;
import com.oracle.truffle.llvm.parser.metadata.MDCompileUnit;
import com.oracle.truffle.llvm.parser.metadata.MDCompositeType;
import com.oracle.truffle.llvm.parser.metadata.MDDerivedType;
import com.oracle.truffle.llvm.parser.metadata.MDFile;
import com.oracle.truffle.llvm.parser.metadata.MDGlobalVariable;
import com.oracle.truffle.llvm.parser.metadata.MDGlobalVariableExpression;
import com.oracle.truffle.llvm.parser.metadata.MDLexicalBlock;
import com.oracle.truffle.llvm.parser.metadata.MDLexicalBlockFile;
import com.oracle.truffle.llvm.parser.metadata.MDLocalVariable;
import com.oracle.truffle.llvm.parser.metadata.MDLocation;
import com.oracle.truffle.llvm.parser.metadata.MDMacroFile;
import com.oracle.truffle.llvm.parser.metadata.MDModule;
import com.oracle.truffle.llvm.parser.metadata.MDNamespace;
import com.oracle.truffle.llvm.parser.metadata.MDString;
import com.oracle.truffle.llvm.parser.metadata.MDSubprogram;
import com.oracle.truffle.llvm.parser.metadata.MDVoidNode;
import com.oracle.truffle.llvm.parser.metadata.MetadataValueList;
import com.oracle.truffle.llvm.parser.metadata.MetadataVisitor;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

final class DIScopeBuilder {

    private static final String MIMETYPE_PLAINTEXT = "text/plain";

    static String getMimeType(String path) {
        if (path == null) {
            return MIMETYPE_PLAINTEXT;
        }

        int extStartIndex = path.lastIndexOf('.') + 1;
        if (extStartIndex <= 0 || extStartIndex >= path.length()) {
            return MIMETYPE_PLAINTEXT;
        }

        switch (path.substring(extStartIndex)) {
            case "c":
                return "text/x-c";
            case "h":
                return "text/x-h";
            case "f":
            case "f90":
            case "for":
                return "text/x-fortran";
            case "rs":
                return "text/x-rust";
            default:
                return MIMETYPE_PLAINTEXT;
        }
    }

    private static Path getPath(MDFile file) {
        if (file == null) {
            return null;
        }

        final String name = MDString.getIfInstance(file.getFile());
        if (name == null) {
            return null;
        }

        Path path = Paths.get(name);
        if (path.isAbsolute()) {
            return path.normalize();
        }

        final String directory = MDString.getIfInstance(file.getDirectory());
        if (directory != null) {
            path = Paths.get(directory, name);
        }

        return path.normalize();
    }

    private final Map<MDBaseNode, LLVMSourceLocation> globalCache;
    private final Map<MDBaseNode, LLVMSourceLocation> localCache;
    private final Map<MDFile, Source> sources;
    private final MetadataValueList metadata;
    private final FileExtractor fileExtractor;

    DIScopeBuilder(MetadataValueList metadata) {
        this.metadata = metadata;
        this.fileExtractor = new FileExtractor();
        this.globalCache = new HashMap<>();
        this.localCache = new HashMap<>();
        this.sources = new HashMap<>();
    }

    private static boolean isLocalScope(LLVMSourceLocation location) {
        switch (location.getKind()) {
            case LINE:
            case LOCAL:
                return true;
            default:
                return false;
        }
    }

    LLVMSourceLocation buildLocation(MDBaseNode md) {
        if (globalCache.containsKey(md)) {
            return globalCache.get(md);
        } else if (localCache.containsKey(md)) {
            return localCache.get(md);
        }

        final Builder builder = new Builder();
        md.accept(builder);

        final LLVMSourceLocation location = builder.build();
        if (isLocalScope(location)) {
            localCache.put(md, location);
        } else {
            globalCache.put(md, location);
        }

        return location;
    }

    void clearLocalScopes() {
        localCache.clear();
    }

    private final class Builder implements MetadataVisitor {

        LLVMSourceLocation loc;

        private LLVMSourceLocation parent;
        private LLVMSourceLocation.Kind kind;
        private String name;
        private SourceSection sourceSection;
        private LLVMSourceLocation compileUnit;

        private MDFile file;
        private long line;
        private long col;

        private Builder() {
            parent = null;
            kind = LLVMSourceLocation.Kind.UNKNOWN;
            name = null;
            sourceSection = null;
            compileUnit = null;
            file = null;
            line = -1;
            col = -1;
        }

        public LLVMSourceLocation build() {
            if (loc == null) {
                sourceSection = buildSection(file, line, col);

                if (sourceSection != null) {
                    loc = LLVMSourceLocation.create(parent, kind, name, sourceSection, compileUnit);

                } else {
                    final Path path = getPath(file);
                    final String fileName = path != null ? path.toString() : null;
                    loc = LLVMSourceLocation.createUnavailable(kind, name, fileName, (int) line, (int) col);
                }
            }

            return loc;
        }

        @Override
        public void visit(MDLocation md) {
            if (md.getInlinedAt() != MDVoidNode.INSTANCE) {
                loc = buildLocation(md.getInlinedAt());
                localCache.put(md, loc);
                return;
            }

            parent = buildLocation(md.getScope());
            kind = LLVMSourceLocation.Kind.LINE;
            file = fileExtractor.extractFile(md);
            line = md.getLine();
            col = md.getColumn();
        }

        @Override
        public void visit(MDLexicalBlock md) {
            parent = buildLocation(md.getScope());
            kind = LLVMSourceLocation.Kind.BLOCK;
            file = fileExtractor.extractFile(md);
            line = md.getLine();
            col = md.getColumn();
        }

        @Override
        public void visit(MDLexicalBlockFile md) {
            parent = buildLocation(md.getFile());
            kind = LLVMSourceLocation.Kind.BLOCK;
            file = fileExtractor.extractFile(md);
        }

        @Override
        public void visit(MDSubprogram md) {
            if (md.getScope() != MDVoidNode.INSTANCE) {
                parent = buildLocation(md.getScope());
            } else {
                parent = buildLocation(md.getCompileUnit());
            }

            kind = LLVMSourceLocation.Kind.FUNCTION;
            file = fileExtractor.extractFile(md);
            line = md.getLine();
            name = MDNameExtractor.getName(md.getName());
            compileUnit = buildLocation(md.getCompileUnit());

            sourceSection = buildSection(file, line, col);
            sourceSection = extend(sourceSection);

            if (sourceSection != null) {
                loc = LLVMSourceLocation.create(parent, kind, name, sourceSection, compileUnit);

            } else {
                final Path path = getPath(file);
                final String fileName = path != null ? path.toString() : null;
                loc = LLVMSourceLocation.createUnavailable(kind, name, fileName, (int) line, (int) col);
            }
        }

        @Override
        public void visit(MDNamespace md) {
            parent = buildLocation(md.getScope());
            kind = LLVMSourceLocation.Kind.NAMESPACE;
            file = fileExtractor.extractFile(md);
            line = md.getLine();
            name = MDNameExtractor.getName(md.getName());
        }

        @Override
        public void visit(MDCompileUnit md) {
            kind = LLVMSourceLocation.Kind.COMPILEUNIT;
            file = fileExtractor.extractFile(md);
        }

        @Override
        public void visit(MDFile md) {
            kind = LLVMSourceLocation.Kind.FILE;
            file = fileExtractor.extractFile(md);
        }

        @Override
        public void visit(MDModule md) {
            parent = buildLocation(md.getScope());
            kind = LLVMSourceLocation.Kind.MODULE;
            file = fileExtractor.extractFile(md);
            name = MDNameExtractor.getName(md.getName());
        }

        @Override
        public void visit(MDBasicType md) {
            kind = LLVMSourceLocation.Kind.TYPE;
            file = fileExtractor.extractFile(md);
            name = MDNameExtractor.getName(md.getName());
        }

        @Override
        public void visit(MDCompositeType md) {
            parent = buildLocation(md.getScope());
            kind = LLVMSourceLocation.Kind.TYPE;
            file = fileExtractor.extractFile(md);
            name = MDNameExtractor.getName(md.getName());
            line = md.getLine();
        }

        @Override
        public void visit(MDDerivedType md) {
            parent = buildLocation(md.getScope());
            kind = LLVMSourceLocation.Kind.TYPE;
            file = fileExtractor.extractFile(md);
            name = MDNameExtractor.getName(md.getName());
            line = md.getLine();
        }

        @Override
        public void visit(MDGlobalVariable md) {
            if (md.getScope() != MDVoidNode.INSTANCE) {
                parent = buildLocation(md.getScope());
            } else {
                // in LLVM 3.2 metadata globals often do not have scopes attached, we fall back to
                // the compileunit
                parent = buildLocation(md.getCompileUnit());
            }
            kind = LLVMSourceLocation.Kind.GLOBAL;
            file = fileExtractor.extractFile(md);
            name = MDNameExtractor.getName(md.getName());
            line = md.getLine();
        }

        @Override
        public void visit(MDLocalVariable md) {
            parent = buildLocation(md.getScope());
            kind = LLVMSourceLocation.Kind.LOCAL;
            file = fileExtractor.extractFile(md);
            name = MDNameExtractor.getName(md.getName());
            line = md.getLine();
        }

        @Override
        public void visit(MDString md) {
            final MDCompositeType actualType = metadata.identifyType(md.getString());
            loc = buildLocation(actualType);
            globalCache.put(md, loc);
        }

        @Override
        public void visit(MDGlobalVariableExpression md) {
            final MDBaseNode variable = md.getGlobalVariable();
            loc = buildLocation(variable);
            globalCache.put(md, loc);
        }
    }

    private static SourceSection extend(SourceSection base) {
        if (base == null) {
            return null;
        }

        SourceSection section;
        try {
            final Source source = base.getSource();
            final int length = source.getLength() - base.getCharIndex();
            section = source.createSection(base.getCharIndex(), length);

        } catch (Throwable ignored) {
            section = base;
        }

        return section;
    }

    private SourceSection buildSection(MDFile file, long startLine, long startCol) {
        if (file == null) {
            return null;
        }

        final Source source = asSource(file);
        if (source == null) {
            return null;
        }

        final int line = (int) startLine;
        final int col = (int) startCol;

        SourceSection section;
        try {
            if (line <= 0) {
                // this happens e.g. for functions implicitly generated by llvm in section
                // '.text.startup'
                section = source.createSection(1);

            } else if (col <= 0) {
                // columns in llvm 3.2 metadata are usually always 0
                section = source.createSection(line);

            } else {
                section = source.createSection(line, col, 0);
            }

        } catch (Throwable ignored) {
            // if the source file has changed since it was last compiled the line and column
            // information in the metadata might not be accurate anymore
            section = null;
        }

        return section;
    }

    private Source asSource(MDFile mdFile) {
        if (sources.containsKey(mdFile)) {
            return sources.get(mdFile);
        }

        final Path path = getPath(mdFile);
        if (path == null) {
            sources.put(mdFile, null);
            return null;
        }

        final String mimeType = getMimeType(path.toString());

        Source source = null;
        try {
            final File file = path.toFile();
            if (file.exists() && file.canRead()) {
                source = Source.newBuilder(file).mimeType(mimeType).name(file.getName()).build();
            }
        } catch (Throwable ignored) {
        }

        sources.put(mdFile, source);
        return source;
    }

    private final class FileExtractor implements MetadataVisitor {

        private MDFile file;

        MDFile extractFile(MDBaseNode node) {
            file = null;
            node.accept(FileExtractor.this);
            return file;
        }

        @Override
        public void visit(MDFile md) {
            this.file = md;
        }

        @Override
        public void visit(MDCompileUnit md) {
            md.getFile().accept(this);
        }

        @Override
        public void visit(MDBasicType md) {
            md.getFile().accept(this);
        }

        @Override
        public void visit(MDCompositeType md) {
            md.getFile().accept(this);
        }

        @Override
        public void visit(MDDerivedType md) {
            md.getFile().accept(this);
        }

        @Override
        public void visit(MDGlobalVariable md) {
            MDBaseNode fileRef = md.getFile() != MDVoidNode.INSTANCE ? md.getFile() : md.getCompileUnit();
            fileRef.accept(this);
        }

        @Override
        public void visit(MDLexicalBlock md) {
            md.getFile().accept(this);
        }

        @Override
        public void visit(MDLexicalBlockFile md) {
            MDBaseNode fileRef = md.getFile() != MDVoidNode.INSTANCE ? md.getFile() : md.getScope();
            fileRef.accept(this);
        }

        @Override
        public void visit(MDLocalVariable md) {
            MDBaseNode fileRef = md.getFile() != MDVoidNode.INSTANCE ? md.getFile() : md.getScope();
            fileRef.accept(this);
        }

        @Override
        public void visit(MDMacroFile md) {
            md.getFile().accept(this);
        }

        @Override
        public void visit(MDModule md) {
            md.getScope().accept(this);
        }

        @Override
        public void visit(MDNamespace md) {
            md.getFile().accept(this);
        }

        @Override
        public void visit(MDSubprogram md) {
            MDBaseNode fileRef = md.getFile() != MDVoidNode.INSTANCE ? md.getFile() : md.getCompileUnit();
            fileRef.accept(this);
        }

        @Override
        public void visit(MDLocation md) {
            md.getScope().accept(this);
        }

        @Override
        public void visit(MDGlobalVariableExpression md) {
            md.getGlobalVariable().accept(this);
        }

        @Override
        public void visit(MDString md) {
            final MDBaseNode typeNode = metadata.identifyType(md.getString());
            if (typeNode != null) {
                typeNode.accept(this);
            }
        }
    }
}
