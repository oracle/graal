/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot.isolate;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.ZoneId;
import java.util.Map;

import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractPolyglotHostService;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.LogHandler;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.MessageTransport;
import org.graalvm.polyglot.io.ProcessHandler;

interface PolyglotIsolateServices {

    void initialize(PolyglotHostServices hostServices, String internalResources);

    long buildEngine(String[] permittedLanguages, SandboxPolicy sandboxPolicy, OutputStream out, OutputStream err, InputStream in,
                    Map<String, String> options, Map<String, String> systemPropertiesOptions, boolean useSystemProperties,
                    boolean allowExperimentalOptions, boolean boundEngine, MessageTransport messageInterceptor, LogHandler logHandler,
                    AbstractPolyglotHostService polyglotHostService, Object hostLanguageServicePeer);

    long createContext(Object engineReceiver, SandboxPolicy sandboxPolicy, OutputStream out, OutputStream err, InputStream in,
                    boolean allowHostAccess, Object polyglotAccess, Object ioAccess, FileSystem fileSystem, boolean allowNativeAccess, boolean allowCreateThread,
                    boolean allowHostClassLoading, boolean allowInnerContextOptions, boolean allowExperimentalOptions, boolean allowCreateProcess,
                    Map<String, String> options, Map<String, String[]> arguments, String[] onlyLanguages, String currentWorkingDirectory, String tmpDir,
                    ProcessHandler processHandler, Object environmentAccess, Map<String, String> environment, ZoneId zoneId, long hostStackHeadRoom,
                    Object hostServicePeer, boolean allowValueSharing, boolean useSystemExit, LogHandler logHandler, ReflectionLibraryDispatch guestToHostObjectReceiver);

    ReflectionLibraryDispatch getGuestObjectReflection(Object contextReceiver);

    Object parseEval(Object contextReceiver, String language, long sourceHandle, boolean eval);

    void triggerGC();

    String heapDump(String path) throws IOException;

    IsolateSourceCache getSourceCache();

    void onIsolateTearDown();

    boolean isMemoryProtected();

    void ensureInstrumentCreated(Object contextReceiver, String instrumentId);

    long getEmbedderExceptionStackTrace(Object contextReceiver, Throwable exception, boolean inHost);
}
