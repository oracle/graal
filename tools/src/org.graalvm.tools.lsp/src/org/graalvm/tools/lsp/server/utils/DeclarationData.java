/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.lsp.server.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import org.graalvm.collections.Pair;

/**
 * Data gathered from {@link StandardTags.DeclarationTag declaration tags}.
 */
public final class DeclarationData {

    private final Map<String, SourceDeclarations> declarations = new HashMap<>();
    private final Map<String, Pair<Symbol, String>> globalVars = new HashMap<>();
    private final Map<String, Pair<Symbol, String>> globalTypes = new HashMap<>();

    public enum SymbolKind {
        FUNCTION,
        METHOD,
        VARIABLE
    }

    public synchronized void addDeclaration(String name, String/*SymbolKind*/ kind, String type, String description, Boolean deprecated, SourceSection symbolSection, int[] scopeSection) {
        String uri = symbolSection.getSource().getURI().toString();
        SourceDeclarations sourceDeclarations = declarations.get(uri);
        if (sourceDeclarations == null) {
            sourceDeclarations = new SourceDeclarations(uri);
            declarations.put(uri, sourceDeclarations);
        }
        SourceRange symbolRange = new SourceRange(symbolSection);
        SourceRange scopeRange = scopeSection != null ? new SourceRange(scopeSection) : null;
        sourceDeclarations.addDeclaration(name, kind, type, description, deprecated, symbolRange, scopeRange);
    }

    synchronized void cleanDeclarations(Source source) {
        String uri = source.getURI().toString();
        declarations.remove(uri);
        removeFromUri(globalVars, uri);
        removeFromUri(globalTypes, uri);
    }

    private static void removeFromUri(Map<String, Pair<Symbol, String>> map, String uri) {
        Iterator<Map.Entry<String, Pair<Symbol, String>>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().getRight().equals(uri)) {
                it.remove();
            }
        }
    }

    public synchronized Collection<Symbol> getGlobalDeclaredSymbols() {
        if (globalVars.isEmpty()) {
            return Collections.emptySet();
        }
        List<Symbol> symbols = new ArrayList<>(globalVars.size());
        for (Pair<Symbol, String> p : globalVars.values()) {
            symbols.add(p.getLeft());
        }
        return Collections.unmodifiableCollection(symbols);
    }

    public synchronized Collection<Symbol> getDeclaredSymbols(SourceSection section) {
        String uri = section.getSource().getURI().toString();
        SourceDeclarations sourceDeclarations = declarations.get(uri);
        if (sourceDeclarations == null) {
            return getGlobalDeclaredSymbols();
        }
        List<Symbol> symbols = new ArrayList<>();
        collectDeclaredSymbols(sourceDeclarations.symbols, section.getStartLine(), section.getStartColumn(), symbols);
        for (Pair<Symbol, String> p : globalVars.values()) {
            symbols.add(p.getLeft());
        }
        return symbols;
    }

    public synchronized Symbol findType(String type, SourceSection section) {
        String uri = section.getSource().getURI().toString();
        SourceDeclarations sourceDeclarations = declarations.get(uri);
        if (sourceDeclarations != null) {
            Symbol symbol = sourceDeclarations.types.get(type);
            if (symbol != null) {
                return symbol;
            }
        }
        Pair<Symbol, String> p = globalTypes.get(type);
        return (p != null) ? p.getLeft() : null;
    }

    public final class SourceDeclarations {

        private final String uri;
        private final Map<String, Symbol> types = new HashMap<>();
        private final Set<Symbol> symbols = new HashSet<>(); // Roots of trees of declared symbols

        SourceDeclarations(String uri) {
            this.uri = uri;
        }

        private synchronized void addDeclaration(String name, String kind, String type, String description, Boolean deprecated, SourceRange symbolRange, SourceRange scopeRange) {
            // Find in which Symbol the given symbol range is:
            Symbol container = null;
            Set<Symbol> parents = symbols;
            parents: while (!parents.isEmpty()) {
                for (Symbol p : parents) {
                    if (symbolRange.isIn(p.symbolRange)) {
                        container = p;
                        parents = p.children;
                        continue parents;
                    } else if (symbolRange.equals(p.symbolRange) /*&& name.equals(p.name)*/) {
                        return; // it's there already
                    }
                }
                break;
            }
            Symbol symbol = new Symbol(name, kind, type, description, deprecated, symbolRange, scopeRange);
            if (container != null) {
                container.addChild(symbol);
                if (type == null) { // The Symbol is a type itself
                    types.put(name, symbol);
                }
            } else {
                symbols.add(symbol);
                if (type == null) { // The Symbol is a type itself
                    globalTypes.put(name, Pair.create(symbol, uri));
                } else {
                    globalVars.put(name, Pair.create(symbol, uri));
                }
            }
            // Test if the symbol is a parent of existing symbols
            moveChildrenUnder(symbol, symbolRange, symbols);
        }

        void moveChildrenUnder(Symbol symbol, SourceRange symbolRange, Set<Symbol> children) {
            Iterator<Symbol> iterator = children.iterator();
            while (iterator.hasNext()) {
                Symbol ch = iterator.next();
                if (ch.symbolRange.isIn(symbolRange)) {
                    iterator.remove();
                    if (ch.parent == null) { // it was a top-level symbol
                        String name = ch.getName();
                        if (ch.getType() == null) {
                            globalTypes.remove(name);
                            types.put(name, ch);
                        } else {
                            globalVars.remove(name);
                        }
                    }
                    symbol.addChild(ch);
                } else if (ch != symbol) {
                    moveChildrenUnder(symbol, symbolRange, ch.children);
                }
            }
        }
    }

    private static void collectDeclaredSymbols(Set<Symbol> symbols, int line, int column, Collection<Symbol> symbolsCollection) {
        for (Symbol s : symbols) {
            SourceRange scopeRange = s.scopeRange;
            if (scopeRange != null && scopeRange.contains(line, column)) {
                symbolsCollection.add(s);
                collectDeclaredSymbols(s.getChildren(), line, column, symbolsCollection);
            }
        }
    }

    public static final class Symbol {

        private final String name;
        private final String/*SymbolKind*/ kind;
        private final String type;
        private final String description;
        private final boolean deprecated;
        private final SourceRange symbolRange;
        private final SourceRange scopeRange;
        private Set<Symbol> children = Collections.emptySet();
        private Symbol parent;

        // Declared variable has type != null, declared type has type == null
        Symbol(String name, String kind, String type, String description, Boolean deprecated, SourceRange symbolRange, SourceRange scopeRange) {
            this.name = name;
            this.kind = kind;
            this.type = type;
            this.description = description;
            this.deprecated = deprecated != null && deprecated;
            this.symbolRange = symbolRange;
            this.scopeRange = scopeRange;
        }

        void addChild(Symbol child) {
            if (children.isEmpty()) {
                children = new HashSet<>();
            }
            children.add(child);
            child.parent = this;
        }

        public String getName() {
            return name;
        }

        public String getKind() {
            return kind;
        }

        public String getType() {
            return type;
        }

        public String getDescription() {
            return description;
        }

        public boolean isDeprecated() {
            return deprecated;
        }

        public Set<Symbol> getChildren() {
            return children;
        }

        public Symbol getParent() {
            return parent;
        }

        @Override
        public String toString() {
            return "DeclarationData.Symbol[" + kind + " (" + type + ") " + name + "; " + description + (deprecated ? ", deprecated " : "") + symbolRange + "]";
        }

    }

    public static final class SourceRange {
        final int startLine;
        final int startColumn;
        final int endLine;
        final int endColumn;

        SourceRange(SourceSection sourceSection) {
            this.startLine = sourceSection.getStartLine();
            this.startColumn = sourceSection.getStartColumn();
            this.endLine = sourceSection.getEndLine();
            this.endColumn = sourceSection.getEndColumn();
        }

        SourceRange(int[] range) {
            this.startLine = range[0];
            this.startColumn = range[1];
            this.endLine = range[2];
            this.endColumn = range[3];
        }

        boolean isIn(SourceRange outerRange) {
            boolean inOrSame = (outerRange.startLine < startLine || outerRange.startLine == startLine && outerRange.startColumn <= startColumn) &&
                            (endLine < outerRange.endLine || endLine == outerRange.endLine && endColumn <= outerRange.endColumn);
            if (inOrSame) {
                if (outerRange.startLine == startLine && endLine == outerRange.endLine) {
                    return outerRange.startColumn < startColumn || endColumn < outerRange.endColumn;
                } else {
                    return true;
                }
            } else {
                return false;
            }
        }

        boolean contains(int line, int column) {
            return (startLine < line || startLine == line && startColumn <= column) &&
                            (line < endLine || line == endLine && column <= endColumn);
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 97 * hash + this.startLine;
            hash = 97 * hash + this.startColumn;
            hash = 97 * hash + this.endLine;
            hash = 97 * hash + this.endColumn;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof SourceRange)) {
                return false;
            }
            SourceRange other = (SourceRange) obj;
            return this.startLine == other.startLine &&
                            this.startColumn == other.startColumn &&
                            this.endLine == other.endLine &&
                            this.endColumn == other.endColumn;
        }

        @Override
        public String toString() {
            return "<"+startLine+":"+startColumn+" - "+endLine+":"+endColumn+">";
        }

    }

}
