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
package com.oracle.truffle.llvm.runtime.debug.scope;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceSymbol;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public final class LLVMSourceLocation {

    public enum Kind {
        TYPE,
        LOCATION,
        BLOCK,
        FUNCTION,
        NAMESPACE,
        COMPILEUNIT,
        FILE,
        UNKNOWN;
    }

    private final Kind kind;
    private final int line;
    private final int column;

    private String name = null;
    private LLVMSourceLocation parent = null;
    private LLVMSourceFile file = null;
    private LLVMSourceLocation compileUnit = null;
    private List<LLVMSourceLocation> children = null;
    private List<LLVMSourceSymbol> symbols = null;

    public LLVMSourceLocation(Kind kind, long line, long column) {
        this.kind = kind != null ? kind : Kind.UNKNOWN;
        this.line = (int) line;
        this.column = (int) column;
    }

    @TruffleBoundary
    public void addChild(LLVMSourceLocation child) {
        if (child != null) {
            if (children == null) {
                children = new LinkedList<>();
            }
            children.add(child);
        }
    }

    @TruffleBoundary
    public void addSymbol(LLVMSourceSymbol symbol) {
        if (symbol != null) {
            if (symbols == null) {
                symbols = new LinkedList<>();
            }
            symbols.add(symbol);
        }
    }

    @TruffleBoundary
    public boolean hasSymbols() {
        return symbols != null && !symbols.isEmpty();
    }

    @TruffleBoundary
    public List<LLVMSourceSymbol> getSymbols() {
        if (symbols != null) {
            return Collections.unmodifiableList(symbols);
        } else {
            return Collections.emptyList();
        }
    }

    public void setParent(LLVMSourceLocation parent) {
        this.parent = parent;
    }

    public LLVMSourceLocation getParent() {
        return parent;
    }

    public String getName() {
        switch (kind) {
            case NAMESPACE: {
                if (name != null) {
                    return "namespace " + name;
                } else {
                    return "namespace";
                }
            }

            case FILE: {
                final LLVMSourceFile sourceFile = getScopeFile(this);
                if (sourceFile != null) {
                    final Source source = sourceFile.toSource();
                    if (source != null) {
                        return source.getName();
                    }
                }
                return "<file>";
            }

            case COMPILEUNIT:
                return "Static";

            case FUNCTION: {
                if (name != null) {
                    return "function " + name;
                } else {
                    return "<function>";
                }
            }

            case BLOCK:
                return "<block>";

            case LOCATION:
                return "<line>";

            case TYPE: {
                if (name != null) {
                    return name;
                } else {
                    return "<type>";
                }
            }

            default:
                return "<scope>";
        }
    }

    public void setName(String name) {
        this.name = name;
    }

    public Kind getKind() {
        return kind;
    }

    public void setFile(LLVMSourceFile file) {
        this.file = file;
    }

    public void copyFile(LLVMSourceLocation source) {
        setFile(getScopeFile(source));
    }

    public LLVMSourceLocation getCompileUnit() {
        return compileUnit;
    }

    public void setCompileUnit(LLVMSourceLocation compileUnit) {
        this.compileUnit = compileUnit;
    }

    private SourceSection resolvedSection = null;

    public SourceSection getSourceSection() {
        return getSourceSection(false);
    }

    public SourceSection getSourceSection(boolean needsLength) {
        if (resolvedSection != null) {
            return resolvedSection;
        }

        final LLVMSourceFile scopeFile = getScopeFile(this);
        if (scopeFile != null) {
            buildSection(scopeFile, needsLength);
        }

        return resolvedSection;
    }

    @TruffleBoundary
    public LLVMSourceLocation findScope(SourceSection location) {
        if (resolvedSection != null && resolvedSection.equals(location)) {
            return this;
        }

        if (children != null) {
            for (LLVMSourceLocation child : children) {
                final LLVMSourceLocation searchResult = child.findScope(location);
                if (searchResult != null) {
                    return searchResult;
                }
            }
        }

        return null;
    }

    @TruffleBoundary
    private Source getSource(LLVMSourceFile scopeFile) {
        Source source = null;
        if (scopeFile != null) {
            source = scopeFile.toSource();
        }

        if (source != null) {
            return source;
        }

        // build an empty source to at least preserve the information we have
        int startLine = line >= 0 ? line : 1;
        final StringBuilder builder = new StringBuilder();
        for (int i = 1; i < startLine; i++) {
            builder.append('\n');
        }
        for (int i = 0; i <= column; i++) {
            builder.append(' ');
        }
        builder.append('\n');

        final String fileName = LLVMSourceFile.toName(scopeFile);
        final String mimeType = LLVMSourceFile.getMimeType(fileName);
        return Source.newBuilder(builder.toString()).mimeType(mimeType).name(fileName).build();
    }

    private void buildSection(LLVMSourceFile scopeFile, boolean needsLength) {
        try {
            final Source source = getSource(scopeFile);
            if (source == null) {
                return;

            } else if (line <= 0) {
                // this happens e.g. for functions implicitly generated by llvm in section
                // '.text.startup'
                resolvedSection = source.createSection(1);

            } else if (column <= 0) {
                // columns in llvm 3.2 metadata are usually always 0
                resolvedSection = source.createSection(line);

            } else {
                resolvedSection = source.createSection(line, column, 0);
            }

            if (needsLength) {
                final int length = source.getLength() - resolvedSection.getCharIndex();
                resolvedSection = source.createSection(line, column, length);
            }

        } catch (Throwable ignored) {
            // if the source file has changed since it was last compiled the line and column
            // information in the metadata might not be accurate anymore
        }
    }

    @Override
    @TruffleBoundary
    public String toString() {
        final LLVMSourceFile sourceFile = getScopeFile(this);
        return String.format("%s:%d:%d", sourceFile != null ? sourceFile : "<unavailable>", line, column);
    }

    @Override
    @TruffleBoundary
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final LLVMSourceLocation location = (LLVMSourceLocation) o;

        if (line != location.line || column != location.column || kind != location.kind) {
            return false;
        }

        if (name != null ? !name.equals(location.name) : location.name != null) {
            return false;
        }

        return file != null ? file.equals(location.file) : location.file == null;
    }

    @Override
    @TruffleBoundary
    public int hashCode() {
        int result = kind.hashCode();
        result = 31 * result + line;
        result = 31 * result + column;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        return result;
    }

    private static LLVMSourceFile getScopeFile(LLVMSourceLocation source) {
        for (LLVMSourceLocation scope = source; scope != null; scope = scope.parent) {
            if (scope.file != null) {
                return scope.file;
            }
        }
        return null;
    }
}
