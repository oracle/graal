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

import java.io.InputStream;
import java.io.OutputStream;
import java.time.ZoneId;
import java.util.Map;

import org.graalvm.nativebridge.ByLocalReference;
import org.graalvm.nativebridge.ByRemoteReference;
import org.graalvm.nativebridge.ForeignObject;
import org.graalvm.nativebridge.GenerateHotSpotToNativeBridge;
import org.graalvm.nativebridge.GenerateNativeToNativeBridge;
import org.graalvm.nativebridge.GenerateProcessToProcessBridge;
import org.graalvm.nativebridge.Idempotent;
import org.graalvm.nativebridge.IsolateDeathException;
import org.graalvm.nativebridge.IsolateDeathHandler;
import org.graalvm.nativebridge.Peer;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractPolyglotHostService;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.LogHandler;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.MessageTransport;
import org.graalvm.polyglot.io.ProcessHandler;
import com.oracle.truffle.polyglot.isolate.PolyglotMarshallerConfig.EnvironmentAccessByValue;
import com.oracle.truffle.polyglot.isolate.PolyglotMarshallerConfig.IOAccessByValue;
import com.oracle.truffle.polyglot.isolate.PolyglotMarshallerConfig.PolyglotAccessByValue;
import com.oracle.truffle.polyglot.isolate.PolyglotMarshallerConfig.ValueReceiver;

@GenerateHotSpotToNativeBridge(factory = PolyglotIsolateForeignFactory.class, implementation = GuestPolyglotIsolateServices.class)
@GenerateNativeToNativeBridge(factory = PolyglotIsolateForeignFactory.class, implementation = GuestPolyglotIsolateServices.class)
@GenerateProcessToProcessBridge(factory = PolyglotIsolateForeignFactory.class, implementation = GuestPolyglotIsolateServices.class)
abstract class ForeignPolyglotIsolateServices implements ForeignObject, PolyglotIsolateServices {

    @Override
    @IsolateDeathHandler(AsPolyglotIsolateCreateException.class)
    public abstract void initialize(@ByLocalReference(ForeignPolyglotHostServices.class) PolyglotHostServices hostServices, String internalResources);

    @Override
    @IsolateDeathHandler(AsPolyglotIsolateCreateException.class)
    public abstract boolean isMemoryProtected();

    @Override
    @IsolateDeathHandler(AsPolyglotIsolateCreateException.class)
    public abstract long buildEngine(String[] permittedLanguages,
                    SandboxPolicy sandboxPolicy,
                    @ByLocalReference(ForeignOutputStream.class) OutputStream out,
                    @ByLocalReference(ForeignOutputStream.class) OutputStream err,
                    @ByLocalReference(ForeignInputStream.class) InputStream in,
                    Map<String, String> options, Map<String, String> systemPropertiesOptions, boolean useSystemProperties,
                    boolean allowExperimentalOptions, boolean boundEngine,
                    @ByLocalReference(ForeignMessageTransport.class) MessageTransport messageInterceptor,
                    @ByLocalReference(ForeignLogHandler.class) LogHandler logHandler,
                    @ByLocalReference(ForeignPolyglotHostService.class) AbstractPolyglotHostService polyglotHostService,
                    @ByLocalReference(Peer.class) Object hostLanguageServicePeer);

    @Override
    @IsolateDeathHandler(AsPolyglotIsolateCreateException.class)
    public abstract long createContext(@ByRemoteReference(ForeignObject.class) Object engineReceiver,
                    SandboxPolicy sandboxPolicy,
                    @ByLocalReference(ForeignOutputStream.class) OutputStream out,
                    @ByLocalReference(ForeignOutputStream.class) OutputStream err,
                    @ByLocalReference(ForeignInputStream.class) InputStream in,
                    boolean allowHostAccess,
                    @PolyglotAccessByValue Object polyglotAccess,
                    @IOAccessByValue Object ioAccess,
                    @ByLocalReference(ForeignFileSystem.class) FileSystem fileSystem,
                    boolean allowNativeAccess, boolean allowCreateThread, boolean allowHostClassLoading, boolean allowInnerContextOptions,
                    boolean allowExperimentalOptions, boolean allowCreateProcess, Map<String, String> options, Map<String, String[]> arguments,
                    String[] onlyLanguages, String currentWorkingDirectory, String tmpDir, @ByLocalReference(ForeignProcessHandler.class) ProcessHandler processHandler,
                    @EnvironmentAccessByValue Object environmentAccess, Map<String, String> environment, ZoneId zoneId, long hostStackHeadRoom,
                    @ByLocalReference(Peer.class) Object hostLanguageServicePeer, boolean allowValueSharing, boolean useSystemExit,
                    @ByLocalReference(ForeignLogHandler.class) LogHandler logHandler,
                    @ByLocalReference(ForeignReflectionLibraryDispatch.class) ReflectionLibraryDispatch guestToHostObjectReceiver);

    @Override
    @ByRemoteReference(ForeignReflectionLibraryDispatch.class)
    @IsolateDeathHandler(AsPolyglotIsolateCreateException.class)
    public abstract ReflectionLibraryDispatch getGuestObjectReflection(@ByRemoteReference(ForeignObject.class) Object contextReceiver);

    @Override
    @Idempotent
    @ByRemoteReference(ForeignIsolateSourceCache.class)
    @IsolateDeathHandler(AsPolyglotIsolateCreateException.class)
    public abstract IsolateSourceCache getSourceCache();

    @Override
    @ValueReceiver
    public abstract Object parseEval(@ByRemoteReference(ForeignObject.class) Object receiverContext, String language, long sourceHandle, boolean eval);

    @Override
    @IsolateDeathHandler(AsPolyglotIsolateCreateException.class)
    public abstract void ensureInstrumentCreated(@ByRemoteReference(ForeignObject.class) Object contextReceiver, String instrumentId);

    @Override
    public abstract long getEmbedderExceptionStackTrace(@ByRemoteReference(ForeignObject.class) Object contextReceiver, Throwable exception, boolean inHost);

    static final class AsPolyglotIsolateCreateException {

        @SuppressWarnings("unused")
        static void handleIsolateDeath(Object receiver, IsolateDeathException isolateDeath) {
            throw new PolyglotIsolateCreateException(isolateDeath);
        }
    }
}
