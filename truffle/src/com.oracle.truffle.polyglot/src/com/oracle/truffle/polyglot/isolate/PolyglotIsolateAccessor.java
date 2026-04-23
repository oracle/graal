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

import com.oracle.truffle.api.impl.Accessor;
import org.graalvm.nativebridge.ForeignException;
import org.graalvm.nativebridge.ForeignObjectCleaner;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.ThreadScope;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.MessageTransport;
import org.graalvm.polyglot.io.ProcessHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

final class PolyglotIsolateAccessor extends Accessor {

    private static final PolyglotIsolateAccessor ACCESSOR = new PolyglotIsolateAccessor();
    static final EngineSupport ENGINE = ACCESSOR.engineSupport();
    static final LanguageSupport LANGUAGE = ACCESSOR.languageSupport();
    static final RuntimeSupport RUNTIME = ACCESSOR.runtimeSupport();
    static final ExceptionSupport EXCEPTION = ACCESSOR.exceptionSupport();

    private PolyglotIsolateAccessor() {
    }

    static final class PolyglotIsolateSupportImpl extends PolyglotIsolateSupport {

        @Override
        public boolean isIsolateHost() {
            return !ImageInfo.inImageRuntimeCode() || (ImageSingletons.contains(PolyglotIsolateHostFeatureEnabled.class) && PolyglotIsolateGuestSupport.isHost());
        }

        @Override
        public boolean isIsolateGuest() {
            return PolyglotIsolateGuestSupport.isGuest();
        }

        @Override
        public Engine buildIsolatedEngine(AbstractPolyglotImpl polyglot, Engine localEngine, String[] isolateLanguages, String[] permittedLanguages, SandboxPolicy sandboxPolicy, OutputStream out,
                        OutputStream err, InputStream in, Map<String, String> options, Map<String, String> systemPropertiesOptions, boolean useSystemProperties,
                        boolean allowExperimentalOptions, boolean boundEngine, MessageTransport messageInterceptor, boolean registerInActiveEngines, boolean externalProcess, long stackHeadroom,
                        String isolateLibrary, String isolateLauncher) {
            return PolyglotIsolateHostSupport.buildIsolatedEngine(polyglot, localEngine, isolateLanguages, permittedLanguages, sandboxPolicy, out, err, in,
                            options, systemPropertiesOptions, useSystemProperties, allowExperimentalOptions, boundEngine, messageInterceptor, registerInActiveEngines,
                            externalProcess, stackHeadroom, isolateLibrary, isolateLauncher);
        }

        @Override
        public ThreadScope createThreadScope(AbstractPolyglotImpl polyglot) {
            return PolyglotIsolateGuestSupport.createThreadScope(polyglot);
        }

        @Override
        public boolean isInCurrentEngineHostCallback(Object engine) {
            return PolyglotIsolateGuestSupport.lazy.polyglotHostServices.isInCurrentEngineHostCallback(engine);
        }

        @Override
        public boolean isDefaultProcessHandler(ProcessHandler processHandler) {
            return PolyglotIsolateGuestSupport.lazy.polyglotHostServices.isDefaultProcessHandler(processHandler);
        }

        @Override
        public boolean isInternalFileSystem(FileSystem fileSystem) {
            if (fileSystem instanceof ForeignFileSystem fs) {
                // In polyglot isolate using host file system, ask host.
                return PolyglotIsolateGuestSupport.lazy.polyglotHostServices.isInternalFileSystem(fs);
            }
            return PolyglotIsolateAccessor.ENGINE.isInternalFileSystem(fileSystem);
        }

        @Override
        public <T extends Throwable> T mergeHostStackTrace(Throwable forException, T exception) {
            Throwable hostStackTrace;
            Throwable isolateStackTrace;
            boolean originatedInHost;
            if (forException instanceof HSTruffleException) {
                hostStackTrace = exception;
                isolateStackTrace = new Exception();
                originatedInHost = true;
            } else {
                hostStackTrace = PolyglotIsolateGuestSupport.lazy.polyglotHostServices.getStackTrace();
                isolateStackTrace = exception;
                originatedInHost = false;
            }
            StackTraceElement[] merged = ForeignException.mergeStackTrace(PolyglotIsolateGuestSupport.lazy.polyglotHostServices.getPeer().getIsolate(),
                            hostStackTrace.getStackTrace(), isolateStackTrace.getStackTrace(), ForeignException.ExceptionKind.RETURNED,
                            originatedInHost);
            exception.setStackTrace(merged);
            return exception;
        }

        @Override
        public Object getEmbedderExceptionStackTrace(Object engine, Throwable exception, boolean fromHost) {
            if (exception instanceof NativeTruffleException nativeTruffleException) {
                // Delegate to isolate to get the exception stack trace
                ForeignContext foreignContext = nativeTruffleException.reference.context;
                long handle = foreignContext.getPolyglotIsolateServices().getEmbedderExceptionStackTrace(foreignContext, exception, fromHost);
                return NativeTruffleObject.createReference(handle, foreignContext);
            } else {
                return PolyglotIsolateAccessor.EXCEPTION.getEmbedderStackTrace(exception, engine, fromHost);
            }
        }

        @Override
        public Object getIsolate(Object engine) {
            Object receiver = PolyglotIsolateHostSupport.getPolyglot().getAPIAccess().getEngineReceiver(engine);
            if (!(receiver instanceof ForeignEngine)) {
                throw new IllegalStateException("Not an isolated engine.");
            }
            return ((ForeignEngine) receiver).getPeer().getIsolate();
        }

        @Override
        public void invokeCleaners() {
            CleanableWeakReference.clean();
            ForeignObjectCleaner.processPendingCleaners();
        }

        @Override
        public void triggerIsolateGC(Object engine) {
            Object receiver = PolyglotIsolateHostSupport.getPolyglot().getAPIAccess().getEngineReceiver(engine);
            if (!(receiver instanceof ForeignEngine)) {
                throw new IllegalStateException("Not an isolated engine.");
            }
            triggerIsolateGCImpl((ForeignEngine) receiver);
        }

        @Override
        public Path dumpIsolateHeap(Object engine, Path folder) throws IOException {
            Object receiver = PolyglotIsolateHostSupport.getPolyglot().getAPIAccess().getEngineReceiver(engine);
            if (!(receiver instanceof ForeignEngine foreignEngine)) {
                throw new IllegalStateException("Not an isolated engine.");
            }
            triggerIsolateGCImpl(foreignEngine);
            return Paths.get(foreignEngine.getPolyglotIsolateServices().heapDump(folder == null ? null : folder.toString()));
        }

        private void triggerIsolateGCImpl(ForeignEngine foreignEngine) {
            invokeCleaners();
            foreignEngine.getPolyglotIsolateServices().triggerGC();
        }
    }
}
