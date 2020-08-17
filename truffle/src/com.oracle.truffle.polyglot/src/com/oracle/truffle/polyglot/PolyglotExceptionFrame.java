/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractStackFrameImpl;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

final class PolyglotExceptionFrame extends AbstractStackFrameImpl {

    private final PolyglotLanguage language;
    private final SourceSection sourceLocation;
    private final String rootName;
    private final boolean host;
    private StackTraceElement stackTrace;
    private final String formattedSource;

    private PolyglotExceptionFrame(PolyglotExceptionImpl source, PolyglotLanguage language,
                    SourceSection sourceLocation, String rootName, boolean isHost, StackTraceElement stackTrace) {
        super(source.polyglot);
        this.language = language;
        this.sourceLocation = sourceLocation;
        this.rootName = rootName;
        this.host = isHost;
        this.stackTrace = stackTrace;
        if (!isHostFrame()) {
            this.formattedSource = formatSource(sourceLocation, source.getFileSystemContext(language));
        } else {
            this.formattedSource = null;
        }
    }

    @Override
    public org.graalvm.polyglot.SourceSection getSourceLocation() {
        return this.sourceLocation;
    }

    @Override
    public Language getLanguage() {
        return this.language.api;
    }

    @Override
    public String getRootName() {
        return this.rootName;
    }

    @Override
    public boolean isHostFrame() {
        return this.host;
    }

    @Override
    public StackTraceElement toHostFrame() {
        if (stackTrace == null) {
            String declaringClass = "<" + language.getId() + ">";
            String methodName = rootName == null ? "" : rootName;
            String fileName = sourceLocation != null ? sourceLocation.getSource().getName() : "Unknown";
            int startLine = sourceLocation != null ? sourceLocation.getStartLine() : -1;
            stackTrace = new StackTraceElement(declaringClass, methodName, fileName, startLine);
        }
        return stackTrace;
    }

    @Override
    public String toStringImpl(int langColumn) {
        StringBuilder b = new StringBuilder();
        String languageId;
        if (isHostFrame()) {
            languageId = "";
        } else {
            languageId = language.getId();
            b.append(spaces(Math.max(langColumn, languageId.length()) - languageId.length())).append("<").append(languageId).append("> ");
        }
        if (isHostFrame()) {
            b.append(stackTrace.toString());
        } else {
            b.append(rootName);
            b.append("(");
            assert formattedSource != null;
            b.append(formattedSource);
            b.append(")");
        }
        return b.toString();
    }

    static PolyglotExceptionFrame createGuest(PolyglotExceptionImpl exception, TruffleStackTraceElement frame, boolean first) {
        if (frame == null) {
            return null;
        }
        RootNode targetRoot = frame.getTarget().getRootNode();
        if (targetRoot.isInternal() && !exception.showInternalStackFrames) {
            return null;
        }

        LanguageInfo info = targetRoot.getLanguageInfo();
        if (info == null) {
            return null;
        }

        PolyglotEngineImpl engine = exception.engine;
        PolyglotLanguage language = null;
        SourceSection location = null;
        String rootName = targetRoot.getName();
        if (engine != null) {
            language = engine.idToLanguage.get(info.getId());

            Node callNode = frame.getLocation();
            if (callNode != null) {
                com.oracle.truffle.api.source.SourceSection section = callNode.getEncapsulatingSourceSection();
                if (section != null) {
                    Source source = engine.getAPIAccess().newSource(language.getId(), section.getSource());
                    location = engine.getAPIAccess().newSourceSection(source, section);
                } else {
                    location = null;
                }
            } else {
                location = first ? exception.getSourceLocation() : null;
            }
        }
        return new PolyglotExceptionFrame(exception, language, location, rootName, false, null);
    }

    static PolyglotExceptionFrame createHost(PolyglotExceptionImpl exception, StackTraceElement hostStack) {
        PolyglotLanguage language = exception.engine != null ? exception.engine.hostLanguage : null;

        // source section for the host language is currently null
        // we should potentially in the future create a source section for the host language
        // however it was not clear how a host language source section would consist of.
        SourceSection location = null;

        String rootname = hostStack.getClassName() + "." + hostStack.getMethodName();
        return new PolyglotExceptionFrame(exception, language, location, rootname, true, hostStack);
    }

    private static String spaces(int length) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < length; i++) {
            b.append(' ');
        }
        return b.toString();
    }

    private static String formatSource(SourceSection sourceSection, Object fileSystemContext) {
        if (sourceSection == null) {
            return "Unknown";
        }
        Source source = sourceSection.getSource();
        if (source == null) {
            // safety check. likely not necsssary
            return "Unknown";
        }
        StringBuilder b = new StringBuilder();
        String path = source.getPath();
        if (path == null) {
            b.append(source.getName());
        } else {
            if (fileSystemContext != null) {
                try {
                    TruffleFile pathAbsolute = EngineAccessor.LANGUAGE.getTruffleFile(path, fileSystemContext);
                    TruffleFile pathBase = EngineAccessor.LANGUAGE.getTruffleFile("", fileSystemContext).getAbsoluteFile();
                    TruffleFile pathRelative = pathBase.relativize(pathAbsolute);
                    b.append(pathRelative.getPath());
                } catch (IllegalArgumentException | UnsupportedOperationException | SecurityException e) {
                    b.append(path);
                }
            } else {
                b.append(path);
            }
        }

        b.append(":").append(formatIndices(sourceSection, true));
        return b.toString();
    }

    private static String formatIndices(SourceSection sourceSection, boolean needsColumnSpecifier) {
        StringBuilder b = new StringBuilder();
        boolean singleLine = sourceSection.getStartLine() == sourceSection.getEndLine();
        if (singleLine) {
            b.append(sourceSection.getStartLine());
        } else {
            b.append(sourceSection.getStartLine()).append("-").append(sourceSection.getEndLine());
        }
        if (needsColumnSpecifier) {
            b.append(":");
            if (sourceSection.getCharLength() <= 1) {
                b.append(sourceSection.getCharIndex());
            } else {
                b.append(sourceSection.getCharIndex()).append("-").append(sourceSection.getCharIndex() + sourceSection.getCharLength() - 1);
            }
        }
        return b.toString();
    }
}
