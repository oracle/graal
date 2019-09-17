/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.Source.SourceBuilder;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.parser.metadata.MDBaseNode;
import com.oracle.truffle.llvm.parser.metadata.MDBasicType;
import com.oracle.truffle.llvm.parser.metadata.MDCommonBlock;
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
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation.LazySourceSection;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import java.io.IOException;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

final class DIScopeBuilder {

    private static final String MIMETYPE_PLAINTEXT = "text/plain";
    private static final String MIMETYPE_UNAVAILABLE = "sulong/unavailable";

    private static final String STDIN_FILENAME = "-";
    private static final String STDIN_SOURCE_TEXT = "STDIN";

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
            case "C":
            case "cpp":
                return "text/x-c";
            case "h":
                return "text/x-h";
            case "f":
            case "f90":
            case "for":
                return "text/x-fortran";
            case "rs":
                return "text/x-rust";
            case "ll":
                return "text/x-llvmir";
            default:
                return MIMETYPE_PLAINTEXT;
        }
    }

    private static final String RELPATH_PREFIX = "truffle-relpath://";
    private static final String RELPATH_PROPERTY_SEPARATOR = "//";

    private TruffleFile resolveAsTruffleRelativePath(String name) {
        if (!name.startsWith(RELPATH_PREFIX)) {
            return null;
        }

        final int propertyEndIndex = name.indexOf(RELPATH_PROPERTY_SEPARATOR, RELPATH_PREFIX.length());
        if (propertyEndIndex == -1) {
            throw new LLVMParserException(String.format("Invalid Source Path: \"%s\"", name));
        }

        final String property = name.substring(RELPATH_PREFIX.length(), propertyEndIndex);
        if (property.isEmpty()) {
            throw new LLVMParserException(String.format("Invalid Property: \"%s\" from \"%s\"", property, name));
        }

        final String pathPrefix = System.getProperty(property);
        if (pathPrefix == null) {
            throw new LLVMParserException(String.format("Property not found: \"%s\" from \"%s\"", property, name));
        }

        final int pathStartIndex = propertyEndIndex + RELPATH_PROPERTY_SEPARATOR.length();
        if (pathStartIndex >= name.length()) {
            throw new LLVMParserException(String.format("Invalid Source Path: \"%s\"", name));
        }

        final String relativePath = name.substring(pathStartIndex);
        try {
            return context.getEnv().getTruffleFile(pathPrefix).resolve(relativePath);
        } catch (InvalidPathException ex) {
            throw new LLVMParserException(ex.getMessage());
        }
    }

    private TruffleFile resolveWithSourcePath(String name, MDBaseNode directoryNode) {
        if (STDIN_FILENAME.equals(name)) {
            // stdin must not be resolved against the provided directory
            return null;
        }

        Path path;
        try {
            path = Paths.get(name);
        } catch (InvalidPathException ipe) {
            return null;
        }

        Env env = context.getEnv();

        if (path.isAbsolute()) {
            return env.getTruffleFile(path.toUri());
        }

        // relative path: search for source file
        String[] sourcePathList = env.getOptions().get(SulongEngineOption.SOURCE_PATH).split(SulongEngineOption.OPTION_ARRAY_SEPARATOR);

        // search in llvm.sourcePath
        for (String sourcePath : sourcePathList) {
            try {
                Path absPath = Paths.get(sourcePath, name);
                TruffleFile file = env.getTruffleFile(absPath.toUri());
                if (file.exists()) {
                    return file;
                }
            } catch (InvalidPathException | SecurityException ex) {
                // can not or not allowed to access source file
                // ignore, try next entry in search path
            }
        }

        // try path from bitcode file
        final String directory = MDString.getIfInstance(directoryNode);
        if (directory != null) {
            try {
                Path absPath = Paths.get(directory, name);
                TruffleFile file = env.getTruffleFile(absPath.toUri());
                if (file.exists()) {
                    return file;
                }
            } catch (InvalidPathException | SecurityException ex) {
                // can not or not allowed to access source file
                // ignore, return relative path
            }
        }

        // fallback to relative path
        return env.getTruffleFile(name);
    }

    private TruffleFile getSourceFile(MDFile file) {
        if (sourceFiles.containsKey(file)) {
            return sourceFiles.get(file);
        }

        String name = MDString.getIfInstance(file.getFile());
        TruffleFile sourceFile = resolveAsTruffleRelativePath(name);

        if (sourceFile == null) {
            sourceFile = resolveWithSourcePath(name, file.getDirectory());
        }

        sourceFiles.put(file, sourceFile);
        return sourceFile;
    }

    private final HashMap<MDBaseNode, LLVMSourceLocation> globalCache;
    private final HashMap<MDBaseNode, LLVMSourceLocation> localCache;
    private final HashMap<MDFile, TruffleFile> sourceFiles;
    private final HashMap<String, Source> sources;
    private final MetadataValueList metadata;
    private final FileExtractor fileExtractor;
    private final LLVMContext context;

    DIScopeBuilder(MetadataValueList metadata, LLVMContext context) {
        this.metadata = metadata;
        this.fileExtractor = new FileExtractor();
        this.globalCache = new HashMap<>();
        this.localCache = new HashMap<>();
        this.sourceFiles = new HashMap<>();
        this.sources = new HashMap<>();
        this.context = context;
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

    void importScope(MDBaseNode node, LLVMSourceLocation importedScope) {
        globalCache.put(node, importedScope);
    }

    private static final class LazySourceSectionImpl extends LazySourceSection {

        private final TruffleFile sourceFile;
        private final String path;
        private final int line;
        private final int column;
        private final HashMap<String, Source> sources;

        LazySourceSectionImpl(HashMap<String, Source> sources, TruffleFile sourceFile, String path, int line, int column) {
            this.sources = sources;
            this.sourceFile = sourceFile;
            this.path = path;
            this.line = line;
            this.column = column;
        }

        @Override
        public SourceSection get() {
            Source source = asSource(sources, sourceFile, path);
            if (source == null) {
                return null;
            }

            SourceSection section;
            try {
                if (MIMETYPE_UNAVAILABLE.equals(source.getMimeType())) {
                    section = source.createUnavailableSection();

                } else if (line < 0) {
                    section = source.createSection(0, source.getLength());

                } else if (line == 0) {
                    // this happens e.g. for functions implicitly generated by llvm in section
                    // '.text.startup'
                    section = source.createSection(1);

                } else if (column <= 0) {
                    // columns in llvm 3.2 metadata are usually always 0
                    section = source.createSection(line);

                } else {
                    section = source.createSection(line, column, line, column);
                }

            } catch (IllegalArgumentException ignored) {
                // if the source file has changed since it was last compiled the line and column
                // information in the metadata might not be accurate anymore
                section = null;
            }

            return section;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public int getLine() {
            return line;
        }

        @Override
        public int getColumn() {
            return column;
        }
    }

    private final class Builder implements MetadataVisitor {

        LLVMSourceLocation loc;

        private LLVMSourceLocation parent;
        private LLVMSourceLocation.Kind kind;
        private String name;
        private LazySourceSectionImpl sourceSection;

        private MDFile file;
        private long line;
        private long col;

        private Builder() {
            parent = null;
            kind = LLVMSourceLocation.Kind.UNKNOWN;
            name = null;
            sourceSection = null;
            file = null;
            line = -1;
            col = -1;
        }

        public LLVMSourceLocation build() {
            if (loc == null) {
                sourceSection = buildSection(file, line, col);
                loc = LLVMSourceLocation.create(parent, kind, name, sourceSection, null);
            }

            return loc;
        }

        @Override
        public void visit(MDLocation md) {
            parent = buildLocation(md.getScope());
            kind = LLVMSourceLocation.Kind.LINE;
            file = fileExtractor.extractFile(md);
            line = md.getLine();
            col = md.getColumn();
        }

        @Override
        public void visit(MDLexicalBlock md) {
            if (md.getScope() != MDVoidNode.INSTANCE) {
                parent = buildLocation(md.getScope());
            } else {
                parent = buildLocation(md.getFile());
            }
            kind = LLVMSourceLocation.Kind.BLOCK;
            file = fileExtractor.extractFile(md);
            line = md.getLine();
            col = md.getColumn();
        }

        @Override
        public void visit(MDLexicalBlockFile md) {
            if (md.getScope() != MDVoidNode.INSTANCE) {
                parent = buildLocation(md.getScope());
            } else {
                parent = buildLocation(md.getFile());
            }
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
            final LLVMSourceLocation compileUnit = buildLocation(md.getCompileUnit());

            sourceSection = buildSection(file, line, col);
            loc = LLVMSourceLocation.create(parent, kind, name, sourceSection, compileUnit);
        }

        @Override
        public void visit(MDNamespace md) {
            parent = buildLocation(md.getScope());
            kind = LLVMSourceLocation.Kind.NAMESPACE;
            name = MDNameExtractor.getName(md.getName());
        }

        @Override
        public void visit(MDCompileUnit md) {
            kind = LLVMSourceLocation.Kind.COMPILEUNIT;
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
            name = MDNameExtractor.getName(md.getName());
        }

        @Override
        public void visit(MDCommonBlock md) {
            parent = buildLocation(md.getScope());
            kind = LLVMSourceLocation.Kind.COMMON_BLOCK;
            name = MDNameExtractor.getName(md.getName());
            file = fileExtractor.extractFile(md);
            line = md.getLine();
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

    private LazySourceSectionImpl buildSection(MDFile file, long startLine, long startCol) {
        if (file == null) {
            return null;
        }

        final String relPath = MDString.getIfInstance(file.getFile());
        if (relPath == null || relPath.isEmpty()) {
            return null;
        }

        TruffleFile sourceFile = getSourceFile(file);
        return new LazySourceSectionImpl(sources, sourceFile, relPath, (int) startLine, (int) startCol);
    }

    private static Source asSource(Map<String, Source> sources, TruffleFile sourceFile, String path) {
        if (sources.containsKey(path)) {
            return sources.get(path);
        } else if (path == null) {
            return null;
        }

        String mimeType = getMimeType(path);
        Source source = null;
        if (sourceFile != null) {
            SourceBuilder builder = Source.newBuilder("llvm", sourceFile).mimeType(mimeType);
            try {
                source = builder.build();
            } catch (IOException | SecurityException ex) {
                // can't or not allowed to load the source file: fall back to CONTENT_NONE
                source = builder.content(Source.CONTENT_NONE).build();
            }
        } else {
            final String sourceText = STDIN_FILENAME.equals(path) ? STDIN_SOURCE_TEXT : path;
            source = Source.newBuilder("llvm", sourceText, sourceText).mimeType(MIMETYPE_UNAVAILABLE).build();
        }

        sources.put(path, source);
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

        @Override
        public void visit(MDCommonBlock md) {
            MDBaseNode fileRef = md.getFile() != MDVoidNode.INSTANCE ? md.getFile() : md.getScope();
            fileRef.accept(this);
        }
    }
}
