/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.debug.scope;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public abstract class LLVMSourceLocation {

    public abstract static class LazySourceSection {
        public abstract SourceSection get();

        public abstract String getPath();

        public abstract int getLine();

        public abstract int getColumn();
    }

    private static final int DEFAULT_SCOPE_CAPACITY = 2;

    private static final SourceSection UNAVAILABLE_SECTION;
    private static final LLVMSourceLocation UNAVAILABLE_LOCATION;

    static {
        final Source source = Source.newBuilder("llvm", "Source unavailable!", "<unavailable>").mimeType("text/plain").build();
        UNAVAILABLE_SECTION = source.createUnavailableSection();
        UNAVAILABLE_LOCATION = new DefaultScope(null, Kind.UNKNOWN, "<unavailable>", UNAVAILABLE_SECTION);
    }

    private static final List<LLVMSourceSymbol> NO_SYMBOLS = Collections.emptyList();

    public enum Kind {
        TYPE("<type>"),
        LINE("<line>"),
        MODULE("<module>", "module "),
        COMMON_BLOCK("<common block>"),
        BLOCK("<block>"),
        FUNCTION("<function>"),
        NAMESPACE("<namespace>", "namespace "),
        COMPILEUNIT("<static>"),
        FILE("<file>"),
        GLOBAL("<global symbol>"),
        LOCAL("<local symbol>"),
        IR_MODULE("<module>", "module "),
        LABEL("<label>"),
        UNKNOWN("<scope>");

        private final String anonymousDescription;
        private final String namePrefix;

        Kind(String anonymousDescription) {
            this(anonymousDescription, null);
        }

        Kind(String anonymousDescription, String namePrefix) {
            this.anonymousDescription = anonymousDescription;
            this.namePrefix = namePrefix;
        }
    }

    private final LLVMSourceLocation parent;
    private final Kind kind;
    private final String name;

    private final LazySourceSection lazySourceSection;
    private SourceSection sourceSection;

    private LLVMSourceLocation(LLVMSourceLocation parent, Kind kind, String name, LazySourceSection lazySourceSection) {
        this.parent = parent;
        this.kind = kind;
        this.name = name;
        this.lazySourceSection = lazySourceSection;
        this.sourceSection = null;
    }

    private LLVMSourceLocation(LLVMSourceLocation parent, Kind kind, String name, SourceSection sourceSection) {
        this.parent = parent;
        this.kind = kind;
        this.name = name;
        this.lazySourceSection = null;
        this.sourceSection = sourceSection;
    }

    public synchronized SourceSection getSourceSection() {
        CompilerAsserts.neverPartOfCompilation();
        if (sourceSection == null && lazySourceSection != null) {
            sourceSection = lazySourceSection.get();
        }
        return sourceSection != null ? sourceSection : UNAVAILABLE_SECTION;
    }

    private static String asFileName(String path) {
        CompilerAsserts.neverPartOfCompilation();
        if (path == null) {
            return UNAVAILABLE_SECTION.getSource().getName();
        }
        String name = path;
        try {
            final Path asPath = Paths.get(name).getFileName();
            if (asPath != null) {
                name = asPath.toString();
            }
        } catch (InvalidPathException ignored) {
        }
        return name;
    }

    public String describeFile() {
        CompilerAsserts.neverPartOfCompilation();
        if (lazySourceSection != null) {
            return asFileName(lazySourceSection.getPath());
        } else if (sourceSection != null) {
            return sourceSection.getSource().getName();
        } else {
            return UNAVAILABLE_SECTION.getSource().getName();
        }
    }

    public int getLine() {
        CompilerAsserts.neverPartOfCompilation();
        if (lazySourceSection != null) {
            return lazySourceSection.getLine();
        } else if (sourceSection != null) {
            return sourceSection.getStartLine();
        } else {
            return UNAVAILABLE_SECTION.getStartLine();
        }
    }

    private int getColumn() {
        CompilerAsserts.neverPartOfCompilation();
        if (lazySourceSection != null) {
            return lazySourceSection.getColumn();
        } else if (sourceSection != null) {
            return sourceSection.getStartColumn();
        } else {
            return UNAVAILABLE_SECTION.getStartColumn();
        }
    }

    private String getPath() {
        CompilerAsserts.neverPartOfCompilation();
        if (lazySourceSection != null) {
            return lazySourceSection.getPath();
        } else if (sourceSection != null) {
            return sourceSection.getSource().getPath();
        } else {
            return UNAVAILABLE_SECTION.getSource().getPath();
        }
    }

    public String describeLocation() {
        CompilerAsserts.neverPartOfCompilation();
        final String sourceName = describeFile();
        final StringBuilder sb = new StringBuilder(sourceName);
        final int line = getLine();
        if (line >= 0) {
            sb.append(':').append(line);
            final int col = getColumn();
            if (col >= 0) {
                sb.append(':').append(col);
            }
        }
        return sb.toString();
    }

    public LLVMSourceLocation getParent() {
        return parent;
    }

    public Kind getKind() {
        return kind;
    }

    public void addSymbol(@SuppressWarnings("unused") LLVMSourceSymbol symbol) {
    }

    public boolean hasSymbols() {
        return false;
    }

    public List<LLVMSourceSymbol> getSymbols() {
        return NO_SYMBOLS;
    }

    public LLVMSourceLocation getCompileUnit() {
        if (kind == Kind.COMPILEUNIT) {
            return this;

        } else if (parent != null) {
            return parent.getCompileUnit();

        } else {
            return null;
        }
    }

    @TruffleBoundary
    public String getName() {
        if (kind == Kind.FILE) {
            return String.format("<%s>", describeFile());
        } else if (kind == Kind.LINE) {
            return String.format("<%s>", describeLocation());
        } else if (kind == null || kind == Kind.UNKNOWN) {
            return Kind.UNKNOWN.anonymousDescription;
        } else if (name != null) {
            final String prefix = kind.namePrefix;
            return prefix != null ? prefix + name : name;
        } else {
            return kind.anonymousDescription;
        }
    }

    @Override
    @TruffleBoundary
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o instanceof LLVMSourceLocation) {
            final LLVMSourceLocation that = (LLVMSourceLocation) o;

            if (hasSymbols() != that.hasSymbols()) {
                return false;
            }

            if (getKind() != that.getKind()) {
                return false;
            }

            if (!Objects.equals(getPath(), that.getPath())) {
                return false;
            }

            if (getLine() != that.getLine() || getColumn() != that.getColumn()) {
                return false;
            }

            return Objects.equals(getParent(), that.getParent());
        }

        return false;
    }

    @Override
    public int hashCode() {
        int result = getKind().hashCode();
        result = 31 * result + Objects.hashCode(getPath());
        result = 31 * result + getLine();
        result = 31 * result + getColumn();
        return result;
    }

    @Override
    public String toString() {
        return describeLocation();
    }

    private static class LineScope extends LLVMSourceLocation {

        LineScope(LLVMSourceLocation parent, Kind kind, String name, LazySourceSection lazySourceSection) {
            super(parent, kind, name, lazySourceSection);
        }

        LineScope(LLVMSourceLocation parent, Kind kind, String name, SourceSection sourceSection) {
            super(parent, kind, name, sourceSection);
        }
    }

    private static class DefaultScope extends LineScope {

        @CompilationFinal private List<LLVMSourceSymbol> symbols;

        DefaultScope(LLVMSourceLocation parent, Kind kind, String name, LazySourceSection lazySourceSection) {
            super(parent, kind, name, lazySourceSection);
            this.symbols = null;
        }

        DefaultScope(LLVMSourceLocation parent, Kind kind, String name, SourceSection sourceSection) {
            super(parent, kind, name, sourceSection);
            this.symbols = null;
        }

        @TruffleBoundary
        @Override
        public void addSymbol(LLVMSourceSymbol symbol) {
            CompilerAsserts.neverPartOfCompilation("Source-Scope may only grow when parsing!");
            if (symbols == null) {
                symbols = new ArrayList<>(DEFAULT_SCOPE_CAPACITY);
            }
            symbols.add(symbol);
        }

        @TruffleBoundary
        @Override
        public boolean hasSymbols() {
            return symbols != null && !symbols.isEmpty();
        }

        @Override
        public List<LLVMSourceSymbol> getSymbols() {
            return symbols != null ? symbols : NO_SYMBOLS;
        }
    }

    private static final class FunctionScope extends DefaultScope {

        private final LLVMSourceLocation compileUnit;

        FunctionScope(LLVMSourceLocation parent, Kind kind, String name, LazySourceSection lazySourceSection, LLVMSourceLocation compileUnit) {
            super(parent, kind, name, lazySourceSection);
            this.compileUnit = compileUnit;
        }

        @Override
        public String describeLocation() {
            return String.format("%s at %s", getName(), super.describeLocation());
        }

        @Override
        public LLVMSourceLocation getCompileUnit() {
            return compileUnit;
        }
    }

    public static final class TextModule extends DefaultScope implements Iterable<LLVMGlobal> {

        private final List<LLVMGlobal> globals;

        TextModule(String name, SourceSection sourceSection) {
            super(null, Kind.IR_MODULE, name, sourceSection);
            globals = new ArrayList<>();
        }

        public void addGlobal(LLVMGlobal global) {
            globals.add(global);
        }

        @Override
        public Iterator<LLVMGlobal> iterator() {
            return globals.iterator();
        }
    }

    public static TextModule createLLModule(String name, SourceSection sourceSection) {
        return new TextModule(name, sourceSection);
    }

    public static LLVMSourceLocation createLLInstruction(LLVMSourceLocation parent, SourceSection sourceSection) {
        return new LineScope(parent, Kind.LINE, "<line>", sourceSection);
    }

    public static LLVMSourceLocation create(LLVMSourceLocation parent, LLVMSourceLocation.Kind kind, String name, LazySourceSection lazySourceSection, LLVMSourceLocation compileUnit) {
        switch (kind) {
            case LINE:
            case GLOBAL:
            case LOCAL:
                return new LineScope(parent, kind, name, lazySourceSection);

            case FUNCTION:
                if (compileUnit != null) {
                    return new FunctionScope(parent, kind, name, lazySourceSection, compileUnit);
                } else {
                    return new DefaultScope(parent, kind, name, lazySourceSection);
                }

            default:
                return new DefaultScope(parent, kind, name, lazySourceSection);
        }
    }

    public static LLVMSourceLocation createBitcodeFunction(String name, SourceSection sourceSection) {
        return new DefaultScope(null, Kind.FUNCTION, name, sourceSection != null ? sourceSection : UNAVAILABLE_SECTION);
    }

    public static LLVMSourceLocation createUnknown(SourceSection sourceSection) {
        return new LineScope(null, Kind.UNKNOWN, "<unknown>", sourceSection != null ? sourceSection : UNAVAILABLE_SECTION);
    }

    /**
     * Returns the given location if it's non-null, otherwise a default "unavailable" location. This
     * is useful, e.g., if a non-null location is required to enable instrumentation.
     */
    public static LLVMSourceLocation orDefault(LLVMSourceLocation location) {
        return location != null ? location : UNAVAILABLE_LOCATION;
    }
}
