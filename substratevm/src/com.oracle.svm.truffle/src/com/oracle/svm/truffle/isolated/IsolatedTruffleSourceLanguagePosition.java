/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.isolated;

import java.net.URI;
import java.net.URISyntaxException;

import org.graalvm.nativeimage.c.function.CEntryPoint;

import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.isolated.ClientHandle;
import com.oracle.svm.graal.isolated.ClientIsolateThread;
import com.oracle.svm.graal.isolated.CompilerHandle;
import com.oracle.svm.graal.isolated.IsolatedCompileClient;
import com.oracle.svm.graal.isolated.IsolatedCompileContext;
import com.oracle.svm.graal.isolated.IsolatedHandles;
import com.oracle.svm.graal.isolated.IsolatedObjectProxy;
import com.oracle.truffle.compiler.TruffleSourceLanguagePosition;

final class IsolatedTruffleSourceLanguagePosition extends IsolatedObjectProxy<TruffleSourceLanguagePosition> implements TruffleSourceLanguagePosition {
    private final int lineNumber;
    private final int offsetStart;
    private final int offsetEnd;
    private final int nodeId;

    IsolatedTruffleSourceLanguagePosition(ClientHandle<TruffleSourceLanguagePosition> handle, int lineNumber, int offsetStart, int offsetEnd, int nodeId) {
        super(handle);
        this.lineNumber = lineNumber;
        this.offsetStart = offsetStart;
        this.offsetEnd = offsetEnd;
        this.nodeId = nodeId;
    }

    @Override
    public String getDescription() {
        CompilerHandle<String> descriptionHandle = getDescription0(IsolatedCompileContext.get().getClient(), handle);
        return IsolatedCompileContext.get().unhand(descriptionHandle);
    }

    @Override
    public int getOffsetEnd() {
        return offsetEnd;
    }

    @Override
    public int getOffsetStart() {
        return offsetStart;
    }

    @Override
    public int getLineNumber() {
        return lineNumber;
    }

    @Override
    public URI getURI() {
        CompilerHandle<String> uriStringHandle = getURIString0(IsolatedCompileContext.get().getClient(), handle);
        String uriString = IsolatedCompileContext.get().unhand(uriStringHandle);
        try {
            return (uriString != null) ? new URI(uriString) : null;
        } catch (URISyntaxException e) {
            throw VMError.shouldNotReachHere("URI string should always be parseable", e);
        }
    }

    @Override
    public String getLanguage() {
        CompilerHandle<String> languageHandle = getLanguage0(IsolatedCompileContext.get().getClient(), handle);
        return IsolatedCompileContext.get().unhand(languageHandle);
    }

    @Override
    public String getNodeClassName() {
        CompilerHandle<String> nodeClassName = getLanguage0(IsolatedCompileContext.get().getClient(), handle);
        return IsolatedCompileContext.get().unhand(nodeClassName);
    }

    @Override
    public int getNodeId() {
        return nodeId;
    }

    @CEntryPoint(exceptionHandler = IsolatedCompileClient.WordExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(callerEpilogue = IsolatedCompileClient.ExceptionRethrowCallerEpilogue.class)
    private static CompilerHandle<String> getDescription0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<TruffleSourceLanguagePosition> positionHandle) {
        String description = IsolatedCompileClient.get().unhand(positionHandle).getDescription();
        return IsolatedCompileClient.get().createStringInCompiler(description);
    }

    @CEntryPoint(exceptionHandler = IsolatedCompileClient.WordExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(callerEpilogue = IsolatedCompileClient.ExceptionRethrowCallerEpilogue.class)
    private static CompilerHandle<String> getURIString0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<TruffleSourceLanguagePosition> positionHandle) {
        URI uri = IsolatedCompileClient.get().unhand(positionHandle).getURI();
        return (uri != null) ? IsolatedCompileClient.get().createStringInCompiler(uri.toString()) : IsolatedHandles.nullHandle();
    }

    @CEntryPoint(exceptionHandler = IsolatedCompileClient.WordExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(callerEpilogue = IsolatedCompileClient.ExceptionRethrowCallerEpilogue.class)
    private static CompilerHandle<String> getLanguage0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<TruffleSourceLanguagePosition> positionHandle) {
        String language = IsolatedCompileClient.get().unhand(positionHandle).getLanguage();
        return IsolatedCompileClient.get().createStringInCompiler(language);
    }

    @CEntryPoint(exceptionHandler = IsolatedCompileClient.WordExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(callerEpilogue = IsolatedCompileClient.ExceptionRethrowCallerEpilogue.class)
    private static CompilerHandle<String> getNodeClassName0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<TruffleSourceLanguagePosition> positionHandle) {
        String language = IsolatedCompileClient.get().unhand(positionHandle).getNodeClassName();
        return IsolatedCompileClient.get().createStringInCompiler(language);
    }
}
