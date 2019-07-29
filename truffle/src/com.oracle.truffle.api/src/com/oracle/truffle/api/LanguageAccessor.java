/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.FileSystemNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.io.FileSystem;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

final class LanguageAccessor extends Accessor {

    static final LanguageAccessor ACCESSOR = new LanguageAccessor();

    private LanguageAccessor() {
    }

    static EngineSupport engineAccess() {
        return ACCESSOR.engineSupport();
    }

    static InstrumentSupport instrumentAccess() {
        return ACCESSOR.instrumentSupport();
    }

    static NodeSupport nodesAccess() {
        return ACCESSOR.nodeSupport();
    }

    static InteropSupport interopAccess() {
        return ACCESSOR.interopSupport();
    }

    static IOSupport ioAccess() {
        return ACCESSOR.ioSupport();
    }

    static final class LanguageImpl extends LanguageSupport {

        @Override
        public boolean isTruffleStackTrace(Throwable t) {
            return t instanceof TruffleStackTrace.LazyStackTrace;
        }

        @Override
        public StackTraceElement[] getInternalStackTraceElements(Throwable t) {
            TruffleStackTrace trace = ((TruffleStackTrace.LazyStackTrace) t).getInternalStackTrace();
            if (trace == null) {
                return new StackTraceElement[0];
            } else {
                return trace.getInternalStackTrace();
            }
        }

        @Override
        public void materializeHostFrames(Throwable original) {
            TruffleStackTrace.materializeHostFrames(original);
        }

        @Override
        public InstrumentInfo createInstrument(Object vmObject, String id, String name, String version) {
            return new InstrumentInfo(vmObject, id, name, version);
        }

        @Override
        public Object getVMObject(InstrumentInfo info) {
            return info.getVmObject();
        }

        @Override
        public void initializeLanguage(TruffleLanguage<?> impl, LanguageInfo language, Object languageVmObject, Object languageInstanceVMObject) {
            impl.languageInfo = language;
            impl.reference = engineAccess().getCurrentContextReference(languageVmObject);
            impl.vmObject = languageInstanceVMObject;
        }

        @SuppressWarnings("deprecation")
        @Override
        public boolean initializeMultiContext(TruffleLanguage<?> language) {
            language.initializeMultipleContexts();
            return language.initializeMultiContext();
        }

        @Override
        public Object getContext(TruffleLanguage.Env env) {
            Object c = env.getLanguageContext();
            if (c != TruffleLanguage.Env.UNSET_CONTEXT) {
                return c;
            } else {
                return null;
            }
        }

        @Override
        public TruffleLanguage<?> getSPI(TruffleLanguage.Env env) {
            return env.getSpi();
        }

        @Override
        public TruffleLanguage.Env createEnv(Object vmObject, TruffleLanguage<?> language, OutputStream stdOut, OutputStream stdErr, InputStream stdIn, Map<String, Object> config,
                        OptionValues options, String[] applicationArguments, FileSystem fileSystem, Supplier<Map<String, Collection<? extends TruffleFile.FileTypeDetector>>> fileTypeDetectors) {
            TruffleLanguage.Env env = new TruffleLanguage.Env(vmObject, language, stdOut, stdErr, stdIn, config, options, applicationArguments, fileSystem, fileTypeDetectors);
            LinkedHashSet<Object> collectedServices = new LinkedHashSet<>();
            LanguageInfo info = language.languageInfo;
            instrumentAccess().collectEnvServices(collectedServices, ACCESSOR.nodeSupport().getEngineObject(info), language);
            env.services = new ArrayList<>(collectedServices);
            return env;
        }

        @Override
        public Object createEnvContext(TruffleLanguage.Env env, List<Object> servicesCollector) {
            env.languageServicesCollector = servicesCollector;
            Object context;
            try {
                context = env.getSpi().createContext(env);
            } finally {
                env.languageServicesCollector = null;
            }
            env.context = context;
            Assumption contextUnchanged = env.contextUnchangedAssumption;
            env.contextUnchangedAssumption = Truffle.getRuntime().createAssumption("Language context unchanged");
            contextUnchanged.invalidate();
            return context;
        }

        @Override
        public TruffleContext createTruffleContext(Object impl) {
            return new TruffleContext(impl);
        }

        @Override
        public void postInitEnv(TruffleLanguage.Env env) {
            env.postInit();
        }

        @Override
        public boolean isContextInitialized(TruffleLanguage.Env env) {
            return env.isInitialized();
        }

        @Override
        @SuppressWarnings("unused")
        public CallTarget parse(TruffleLanguage.Env env, Source code, Node context, String... argumentNames) {
            return env.getSpi().parse(code, argumentNames);
        }

        @Override
        public ExecutableNode parseInline(TruffleLanguage.Env env, Source code, Node context, MaterializedFrame frame) {
            return env.getSpi().parseInline(code, context, frame);
        }

        @Override
        public LanguageInfo getLanguageInfo(TruffleLanguage.Env env) {
            return env.getSpi().languageInfo;
        }

        @Override
        public void onThrowable(Node callNode, RootCallTarget root, Throwable e, Frame frame) {
            TruffleStackTrace.addStackFrameInfo(callNode, e, root, frame);
        }

        @Override
        public void initializeThread(TruffleLanguage.Env env, Thread current) {
            env.getSpi().initializeThread(env.context, current);
        }

        @Override
        public boolean isThreadAccessAllowed(TruffleLanguage.Env language, Thread thread, boolean singleThread) {
            return language.getSpi().isThreadAccessAllowed(thread, singleThread);
        }

        @Override
        public void initializeMultiThreading(TruffleLanguage.Env env) {
            env.getSpi().initializeMultiThreading(env.context);
        }

        @Override
        public void finalizeContext(TruffleLanguage.Env env) {
            env.getSpi().finalizeContext(env.context);
        }

        @Override
        public void disposeThread(TruffleLanguage.Env env, Thread current) {
            env.getSpi().disposeThread(env.context, current);
        }

        @Override
        public Object evalInContext(Source source, Node node, final MaterializedFrame mFrame) {
            CallTarget target = ACCESSOR.nodeSupport().getLanguage(node.getRootNode()).parse(source);
            try {
                if (target instanceof RootCallTarget) {
                    RootNode exec = ((RootCallTarget) target).getRootNode();
                    return exec.execute(mFrame);
                } else {
                    throw new IllegalStateException("" + target);
                }
            } catch (Exception ex) {
                if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                }
                throw new RuntimeException(ex);
            }
        }

        @Override
        public Object findExportedSymbol(TruffleLanguage.Env env, String globalName, boolean onlyExplicit) {
            return env.findExportedSymbol(globalName, onlyExplicit);
        }

        @Override
        public LanguageInfo getLanguageInfo(TruffleLanguage<?> language) {
            return language.languageInfo;
        }

        @Override
        public Object getVMObject(TruffleLanguage<?> language) {
            return language.vmObject;
        }

        @Override
        public void dispose(TruffleLanguage.Env env) {
            env.dispose();
        }

        @Override
        public String toStringIfVisible(TruffleLanguage.Env env, Object value, boolean checkVisibility) {
            return env.toStringIfVisible(value, checkVisibility);
        }

        @Override
        public Object findMetaObject(TruffleLanguage.Env env, Object obj) {
            return env.findMetaObjectImpl(obj);
        }

        @Override
        public SourceSection findSourceLocation(TruffleLanguage.Env env, Object obj) {
            return env.findSourceLocation(obj);
        }

        @Override
        public boolean isObjectOfLanguage(TruffleLanguage.Env env, Object value) {
            return env.isObjectOfLanguage(value);
        }

        @Override
        public Iterable<Scope> findLocalScopes(TruffleLanguage.Env env, Node node, Frame frame) {
            return env.findLocalScopes(node, frame);
        }

        @Override
        public Iterable<Scope> findTopScopes(TruffleLanguage.Env env) {
            return env.findTopScopes();
        }

        @Override
        public OptionDescriptors describeOptions(TruffleLanguage<?> language, String requiredGroup) {
            OptionDescriptors descriptors = language.getOptionDescriptors();
            if (descriptors == null) {
                return OptionDescriptors.EMPTY;
            }
            assert verifyDescriptors(language, requiredGroup, descriptors);
            return descriptors;
        }

        private static boolean verifyDescriptors(TruffleLanguage<?> language, String requiredGroup, OptionDescriptors descriptors) {
            String groupPlusDot = requiredGroup + ".";
            for (OptionDescriptor descriptor : descriptors) {
                if (!descriptor.getName().equals(requiredGroup) && !descriptor.getName().startsWith(groupPlusDot)) {
                    throw new IllegalArgumentException(String.format("Illegal option prefix in name '%s' specified for option described by language '%s'. " +
                                    "The option prefix must match the id of the language '%s'.",
                                    descriptor.getName(), language.getClass().getName(), requiredGroup));
                }
            }
            return true;
        }

        @Override
        public TruffleLanguage.Env patchEnvContext(TruffleLanguage.Env env, OutputStream stdOut, OutputStream stdErr, InputStream stdIn, Map<String, Object> config, OptionValues options,
                        String[] applicationArguments, FileSystem fileSystem, Supplier<Map<String, Collection<? extends TruffleFile.FileTypeDetector>>> fileTypeDetectors) {
            assert env.spi != null;
            final TruffleLanguage.Env newEnv = createEnv(
                            env.vmObject,
                            env.spi,
                            stdOut,
                            stdErr,
                            stdIn,
                            config,
                            options,
                            applicationArguments, fileSystem, fileTypeDetectors);

            newEnv.initialized = env.initialized;
            newEnv.context = env.context;
            env.valid = false;
            return env.getSpi().patchContext(env.context, newEnv) ? newEnv : null;
        }

        @Override
        public Object createFileSystemContext(FileSystem fileSystem, Supplier<Map<String, Collection<? extends TruffleFile.FileTypeDetector>>> fileTypeDetectors) {
            return new TruffleFile.FileSystemContext(fileSystem, fileTypeDetectors);
        }

        @Override
        public Object getCurrentFileSystemContext() {
            Object polyglotContextImpl = engineAccess().getCurrentOuterContext();
            if (polyglotContextImpl == null) {
                throw new IllegalStateException("No current context");
            }
            FileSystem fileSystem = engineAccess().getFileSystem(polyglotContextImpl);
            Supplier<Map<String, Collection<? extends TruffleFile.FileTypeDetector>>> fileTypeDetectorsSupplier = engineAccess().getFileTypeDetectorsSupplier(polyglotContextImpl);
            return new TruffleFile.FileSystemContext(fileSystem, fileTypeDetectorsSupplier);
        }

        @Override
        public String getMimeType(TruffleFile file, Set<String> validMimeTypes) throws IOException {
            return file.getMimeType(validMimeTypes);
        }

        @Override
        public Charset getEncoding(TruffleFile file, String mimeType) throws IOException {
            String useMimeType = mimeType == null ? file.getMimeType() : mimeType;
            return useMimeType == null ? null : file.getEncoding(useMimeType);
        }

        @Override
        public Object getLanguageInstance(TruffleLanguage<?> language) {
            if (language == null) {
                return null;
            }
            return language.vmObject;
        }

        @Override
        public void configureLoggers(Object polyglotContext, Map<String, Level> logLevels, Object... loggers) {
            for (Object loggerCache : loggers) {
                if (logLevels == null) {
                    ((TruffleLogger.LoggerCache) loggerCache).removeLogLevelsForContext(polyglotContext);
                } else {
                    ((TruffleLogger.LoggerCache) loggerCache).addLogLevelsForContext(polyglotContext, logLevels);
                }
            }
        }

        @Override
        public boolean areOptionsCompatible(TruffleLanguage<?> language, OptionValues firstContextOptions, OptionValues newContextOptions) {
            return language.areOptionsCompatible(firstContextOptions, newContextOptions);
        }

        @Override
        public TruffleLanguage<?> getLanguage(TruffleLanguage.Env env) {
            return env.getSpi();
        }

        @Override
        public TruffleFile getTruffleFile(String path, Object fileSystemContext) {
            TruffleFile.FileSystemContext ctx = (TruffleFile.FileSystemContext) fileSystemContext;
            return new TruffleFile(ctx, ctx.fileSystem.parsePath(path));
        }

        @Override
        public TruffleFile getTruffleFile(URI uri, Object fileSystemContext) {
            TruffleFile.FileSystemContext ctx = (TruffleFile.FileSystemContext) fileSystemContext;
            try {
                return new TruffleFile(ctx, ctx.fileSystem.parsePath(uri));
            } catch (UnsupportedOperationException e) {
                throw new FileSystemNotFoundException("FileSystem for: " + uri.getScheme() + " scheme is not supported.");
            }
        }

        @Override
        public boolean isDefaultFileSystem(Object fileSystemContext) {
            TruffleFile.FileSystemContext ctx = (TruffleFile.FileSystemContext) fileSystemContext;
            return engineAccess().isDefaultFileSystem(ctx.fileSystem);
        }

        @Override
        public TruffleFile getTruffleFile(String path, FileSystem fileSystem, Supplier<Map<String, Collection<? extends TruffleFile.FileTypeDetector>>> fileTypeDetectorsSupplier) {
            TruffleFile.FileSystemContext ctx = new TruffleFile.FileSystemContext(fileSystem, fileTypeDetectorsSupplier);
            return getTruffleFile(path, ctx);
        }

        @Override
        public TruffleFile getTruffleFile(URI uri, FileSystem fileSystem, Supplier<Map<String, Collection<? extends TruffleFile.FileTypeDetector>>> fileTypeDetectorsSupplier) {
            TruffleFile.FileSystemContext ctx = new TruffleFile.FileSystemContext(fileSystem, fileTypeDetectorsSupplier);
            return getTruffleFile(uri, ctx);
        }

        @Override
        public Object getDefaultLoggers() {
            return TruffleLogger.LoggerCache.getInstance();
        }

        @Override
        public Object createEngineLoggers(Object polyglotEngine, Map<String, Level> logLevels) {
            return TruffleLogger.createLoggerCache(polyglotEngine, logLevels);
        }

        @Override
        public void closeEngineLoggers(Object loggers) {
            ((TruffleLogger.LoggerCache) loggers).close();
        }

        @Override
        public TruffleLogger getLogger(String id, String loggerName, Object loggers) {
            return TruffleLogger.getLogger(id, loggerName, (TruffleLogger.LoggerCache) loggers);
        }

        @Override
        public SecurityException throwSecurityException(String message) {
            throw new TruffleSecurityException(message);
        }
    }
}
