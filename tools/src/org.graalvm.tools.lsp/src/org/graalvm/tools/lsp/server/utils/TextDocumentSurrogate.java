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

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.graalvm.tools.lsp.server.types.TextDocumentContentChangeEvent;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.Source.SourceBuilder;
import com.oracle.truffle.api.source.SourceSection;

/**
 * A data structure representing the state of text documents (source code files) which have been
 * opened at client-side.
 *
 */
public final class TextDocumentSurrogate {

    private final TruffleFile truffleFile;
    private final Env env;
    private final List<TextDocumentContentChangeEvent> changeEventsSinceLastSuccessfulParsing;
    private final Map<SourceSectionReference, List<CoverageData>> section2coverageData;
    private String editorText;
    private Boolean coverageAnalysisDone = Boolean.FALSE;
    private SourceWrapper sourceWrapper;
    private TextDocumentContentChangeEvent lastChange = null;
    private final List<String> completionTriggerCharacters;
    private final LanguageInfo languageInfo;
    private final DeclarationData declarationData;
    private EventBinding<LoadDeclarationListener> loadDeclarationBinding;

    private TextDocumentSurrogate(TextDocumentSurrogate blueprint) {
        this.truffleFile = blueprint.truffleFile;
        this.env = blueprint.env;
        this.section2coverageData = blueprint.section2coverageData;
        this.changeEventsSinceLastSuccessfulParsing = blueprint.changeEventsSinceLastSuccessfulParsing;
        this.editorText = blueprint.editorText;
        this.sourceWrapper = blueprint.sourceWrapper;
        this.lastChange = blueprint.lastChange;
        this.completionTriggerCharacters = blueprint.completionTriggerCharacters;
        this.languageInfo = blueprint.languageInfo;
        this.declarationData = blueprint.declarationData;
    }

    public TextDocumentSurrogate(final Env env, final TruffleFile truffleFile, final LanguageInfo languageInfo, final List<String> completionTriggerCharacters, DeclarationData declarationData) {
        this.truffleFile = truffleFile;
        this.env = env;
        this.completionTriggerCharacters = completionTriggerCharacters;
        this.section2coverageData = new HashMap<>();
        this.changeEventsSinceLastSuccessfulParsing = new ArrayList<>();
        this.languageInfo = languageInfo;
        this.declarationData = declarationData;
    }

    public URI getUri() {
        return truffleFile.toUri();
    }

    public String getLanguageId() {
        return languageInfo.getId();
    }

    public String getEditorText() {
        if (editorText != null) {
            return editorText;
        }
        Source source = getSource();
        return source == null ? null : (editorText = source.getCharacters().toString());
    }

    public void setEditorText(String editorText) {
        this.editorText = editorText;
    }

    public Boolean getTypeHarvestingDone() {
        return coverageAnalysisDone;
    }

    public void setCoverageAnalysisDone(Boolean coverageAnalysisDone) {
        this.coverageAnalysisDone = coverageAnalysisDone;
    }

    public SourceWrapper getSourceWrapper() {
        return sourceWrapper;
    }

    public List<String> getCompletionTriggerCharacters() {
        return completionTriggerCharacters;
    }

    public LanguageInfo getLanguageInfo() {
        return languageInfo;
    }

    @Override
    public int hashCode() {
        return truffleFile.hashCode();
    }

    public TextDocumentContentChangeEvent getLastChange() {
        return lastChange;
    }

    public void setLastChange(TextDocumentContentChangeEvent lastChange) {
        this.lastChange = lastChange;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextDocumentSurrogate) {
            return truffleFile.equals(((TextDocumentSurrogate) obj).truffleFile);
        }
        return false;
    }

    public List<TextDocumentContentChangeEvent> getChangeEventsSinceLastSuccessfulParsing() {
        return changeEventsSinceLastSuccessfulParsing;
    }

    public List<CoverageData> getCoverageData(SourceSection section) {
        return section2coverageData.get(SourceSectionReference.from(section));
    }

    public List<CoverageData> getCoverageData(SourceSectionReference section) {
        return section2coverageData.get(section);
    }

    public Set<URI> getCoverageUris(SourceSection section) {
        List<CoverageData> coverageDataObjects = section2coverageData.get(SourceSectionReference.from(section));
        return coverageDataObjects == null ? null : coverageDataObjects.stream().map(coverageData -> coverageData.getCovarageUri()).collect(Collectors.toSet());
    }

    public void addLocationCoverage(SourceSectionReference section, CoverageData coverageData) {
        if (!section2coverageData.containsKey(section)) {
            section2coverageData.put(section, new ArrayList<>());
        }
        section2coverageData.get(section).add(coverageData);
    }

    public boolean isLocationCovered(SourceSectionReference section) {
        return section2coverageData.containsKey(section);
    }

    public boolean hasCoverageData() {
        return !section2coverageData.isEmpty();
    }

    public void clearCoverage() {
        section2coverageData.clear();
    }

    public void clearCoverage(URI runScriptUri) {
        for (Iterator<Entry<SourceSectionReference, List<CoverageData>>> iterator = section2coverageData.entrySet().iterator(); iterator.hasNext();) {
            Entry<SourceSectionReference, List<CoverageData>> entry = iterator.next();
            for (Iterator<CoverageData> iteratorData = entry.getValue().iterator(); iteratorData.hasNext();) {
                CoverageData coverageData = iteratorData.next();
                if (coverageData.getCovarageUri().equals(runScriptUri)) {
                    iteratorData.remove();
                }
            }
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
    }

    public List<SourceSectionReference> getCoverageLocations() {
        return new ArrayList<>(section2coverageData.keySet());
    }

    public void replace(SourceSectionReference oldSection, SourceSectionReference newSection) {
        List<CoverageData> removedCoverageData = section2coverageData.remove(oldSection);
        assert removedCoverageData != null;
        section2coverageData.put(newSection, removedCoverageData);
    }

    public Source buildSource() {
        SourceBuilder builder = Source.newBuilder(languageInfo.getId(), truffleFile).cached(false);
        if (editorText != null) {
            return builder.content(editorText).build();
        }

        try {
            // No content defined, need to read content from file
            return builder.build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public SourceWrapper prepareParsing() {
        Source source = buildSource();
        declarationData.cleanDeclarations(source);
        // TODO: Bug: Must include StatementTag to receive nodes tagged with DeclarationTag.
        loadDeclarationBinding = env.getInstrumenter().attachLoadSourceSectionListener(
                        SourceSectionFilter.newBuilder().sourceIs(source).tagIs(StandardTags.DeclarationTag.class, StandardTags.WriteVariableTag.class, StandardTags.StatementTag.class).build(),
                        new LoadDeclarationListener(), false);
        sourceWrapper = new SourceWrapper(source);
        return sourceWrapper;
    }

    public void notifyParsingDone(CallTarget callTarget) {
        loadDeclarationBinding.dispose();
        boolean successful = callTarget != null;
        if (successful) {
            sourceWrapper.setParsingSuccessful(true);
            sourceWrapper.setCallTarget(callTarget);
            changeEventsSinceLastSuccessfulParsing.clear();
        }
    }

    public boolean isSourceCodeReadyForCodeCompletion() {
        return sourceWrapper.isParsingSuccessful();
    }

    public Source getSource() {
        return sourceWrapper != null ? sourceWrapper.getSource() : null;
    }

    public TextDocumentSurrogate copy() {
        return new TextDocumentSurrogate(this);
    }

    private final class LoadDeclarationListener implements LoadSourceSectionListener {

        private final InteropLibrary interop = InteropLibrary.getFactory().getUncached();

        @Override
        public void onLoad(LoadSourceSectionEvent event) {
            InstrumentableNode inode = (InstrumentableNode) event.getNode();
            Object declarationObject = inode.getNodeObject();
            Set<Class<?>> tags = env.getInstrumenter().queryTags((Node) inode);
            boolean declaration = tags.contains(StandardTags.DeclarationTag.class);
            boolean writeVar = tags.contains(StandardTags.WriteVariableTag.class);
            // TODO: Bug: Had to include StatementTag to receive nodes tagged with DeclarationTag.
            if (declaration || writeVar) {
                // We have declaration
            } else {
                declarationObject = null;
            }
            if (declarationObject != null) {
                assert interop.hasMembers(declarationObject) : declarationObject;
                try {
                    String name;
                    String kind;
                    String type;
                    String description;
                    Boolean deprecation;
                    SourceSection symbolSection = ((Node) inode).getSourceSection();
                    int[] scopeSection;
                    if (declaration) {
                        name = (String) interop.readMember(declarationObject, StandardTags.DeclarationTag.NAME);
                        kind = (String) interop.readMember(declarationObject, StandardTags.DeclarationTag.KIND);
                        type = (String) readExisting(declarationObject, StandardTags.DeclarationTag.TYPE);
                        description = (String) readExisting(declarationObject, StandardTags.DeclarationTag.DESCRIPTION);
                        deprecation = (Boolean) readExisting(declarationObject, StandardTags.DeclarationTag.DEPRECATED);
                        scopeSection = getSectionArray(readExisting(declarationObject, StandardTags.DeclarationTag.SCOPE_SECTION));
                    } else if (writeVar) {
                        name = (String) interop.readMember(declarationObject, StandardTags.WriteVariableTag.NAME);
                        kind = "Variable";
                        type = (String) readExisting(declarationObject, StandardTags.WriteVariableTag.TYPE);
                        description = null;
                        deprecation = false;
                        scopeSection = getSectionArray(readExisting(declarationObject, StandardTags.WriteVariableTag.SCOPE_SECTION));
                    } else {
                        return;
                    }
                    if (scopeSection == null) {
                        scopeSection = getDefaultScope(symbolSection, (Node) inode);
                    }
                    declarationData.addDeclaration(name, kind, type, description, deprecation, symbolSection, scopeSection);
                } catch (UnsupportedMessageException | UnknownIdentifierException ex) {
                    env.getLogger(TextDocumentSurrogate.class).log(Level.INFO, declarationObject.toString(), ex);
                }
            }
        }

        private Object readExisting(Object declarationObject, String member) {
            if (interop.isMemberExisting(declarationObject, member)) {
                try {
                    return interop.readMember(declarationObject, member);
                } catch (UnknownIdentifierException | UnsupportedMessageException ex) {
                    env.getLogger(TextDocumentSurrogate.class).log(Level.INFO, declarationObject.toString(), ex);
                }
            }
            return null;
        }

        private int[] getSectionArray(Object arr) {
            if (arr == null || !interop.hasArrayElements(arr)) {
                return null;
            }
            int[] range;
            try {
                int size = (int) interop.getArraySize(arr);
                range = new int[size];
                for (int i = 0; i < size; i++) {
                    range[i] = ((Number) interop.readArrayElement(arr, i)).intValue();
                }
            } catch (InvalidArrayIndexException | UnsupportedMessageException ex) {
                env.getLogger(TextDocumentSurrogate.class).log(Level.INFO, arr.toString(), ex);
                range = null;
            }
            return range;
        }

        private int[] getDefaultScope(SourceSection symbolSection, Node node) {
            int[] scope = new int[4];
            scope[0] = symbolSection.getStartLine();
            scope[1] = symbolSection.getStartColumn();
            Node parent = findInstrumentableParent(node);
            if (parent != null) {
                SourceSection pss = parent.getSourceSection();
                scope[2] = pss.getEndLine();
                scope[3] = pss.getEndColumn();
            } else {
                scope[2] = symbolSection.getSource().getLineCount();
                scope[3] = symbolSection.getSource().getLineLength(scope[2]);
            }
            return scope;
        }

        private Node findInstrumentableParent(Node node) {
            Node parent = node;
            while ((parent = parent.getParent()) != null) {
                if (parent instanceof InstrumentableNode && ((InstrumentableNode) parent).isInstrumentable() && parent.getSourceSection() != null) {
                    return parent;
                }
            }
            return null;
        }
    }
}
