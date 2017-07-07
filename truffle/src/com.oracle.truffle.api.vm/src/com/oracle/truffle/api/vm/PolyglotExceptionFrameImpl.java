/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.vm;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractStackFrameImpl;

import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.vm.PolyglotImpl.VMObject;

final class PolyglotExceptionFrameImpl extends AbstractStackFrameImpl {

    private final PolyglotLanguageImpl language;
    private final SourceSection sourceLocation;
    private final String rootName;
    private final boolean host;
    private StackTraceElement stackTrace;

    private PolyglotExceptionFrameImpl(VMObject source, PolyglotLanguageImpl language,
                    SourceSection sourceLocation, String rootName, boolean isHost, StackTraceElement stackTrace) {
        super(source.getImpl());
        this.language = language;
        this.sourceLocation = sourceLocation;
        this.rootName = rootName;
        this.host = isHost;
        this.stackTrace = stackTrace;
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
            String methodName = rootName;
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
            b.append(formatSource(sourceLocation));
            b.append(")");
        }
        return b.toString();
    }

    static PolyglotExceptionFrameImpl createGuest(PolyglotExceptionImpl exception, TruffleStackTraceElement frame, boolean first) {
        if (frame == null) {
            return null;
        }
        RootNode targetRoot = frame.getTarget().getRootNode();
        if (targetRoot.isInternal()) {
            return null;
        }

        LanguageInfo info = targetRoot.getLanguageInfo();
        if (info == null) {
            return null;
        }

        PolyglotEngineImpl engine = exception.engine;
        PolyglotLanguageImpl language = engine.idToLanguage.get(info.getId());
        String rootName = targetRoot.getName();

        SourceSection location;
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

        return new PolyglotExceptionFrameImpl(exception, language, location, rootName, false, null);
    }

    static PolyglotExceptionFrameImpl createHost(PolyglotExceptionImpl exception, StackTraceElement hostStack) {
        PolyglotLanguageImpl language = exception.engine.idToLanguage.get(PolyglotEngineImpl.HOST_LANGUAGE_ID);

        // source section for the host language is currently null
        // we should potentially in the future create a source section for the host language
        // however it was not clear how a host language source section would consist of.
        SourceSection location = null;

        String rootname = hostStack.getClassName() + "." + hostStack.getMethodName();
        return new PolyglotExceptionFrameImpl(exception, language, location, rootname, true, hostStack);
    }

    private static String spaces(int length) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < length; i++) {
            b.append(' ');
        }
        return b.toString();
    }

    private static String formatSource(SourceSection sourceSection) {
        if (sourceSection == null) {
            return "Unknown";
        }
        Source source = sourceSection.getSource();
        if (source == null) {
            // safety check. likely not necsssary
            return "Unknown";
        }
        StringBuilder b = new StringBuilder();
        if (source.getPath() == null) {
            b.append(source.getName());
        } else {
            Path pathAbsolute = Paths.get(source.getPath());
            Path pathBase = new File("").getAbsoluteFile().toPath();
            try {
                Path pathRelative = pathBase.relativize(pathAbsolute);
                b.append(pathRelative.toFile());
            } catch (IllegalArgumentException e) {
                b.append(source.getName());
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
