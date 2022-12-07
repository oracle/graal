/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Executable;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URL;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.LogRecord;

import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.collections.UnmodifiableEconomicSet;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.HostAccess.MutableTargetMapping;
import org.graalvm.polyglot.HostAccess.TargetMappingPrecedence;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.PolyglotException.StackFrame;
import org.graalvm.polyglot.ResourceLimitEvent;
import org.graalvm.polyglot.ResourceLimits;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.TypeLiteral;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.io.MessageTransport;
import org.graalvm.polyglot.io.ProcessHandler;
import org.graalvm.polyglot.management.ExecutionEvent;
import org.graalvm.polyglot.management.ExecutionListener;

/**
 * This class is intended to be used by polyglot implementations. Methods in this class are not
 * intended to be used directly.
 *
 * This class and its inner classes break compatibility without notice. Do not use, unless you know
 * what you are doing.
 */
@SuppressWarnings("unused")
public abstract class AbstractPolyglotImpl {

    protected AbstractPolyglotImpl() {
    }

    public abstract static class ManagementAccess {
        protected ManagementAccess() {
            if (!getClass().getCanonicalName().equals("org.graalvm.polyglot.management.Management.ManagementAccessImpl")) {
                throw new AssertionError("Only one implementation of ManagementAccessImpl allowed. " + getClass().getCanonicalName());
            }
        }

        public abstract ExecutionListener newExecutionListener(AbstractExecutionListenerDispatch dispatch, Object receiver);

        public abstract ExecutionEvent newExecutionEvent(AbstractExecutionEventDispatch dispatch, Object event);

        public abstract Object getReceiver(ExecutionListener executionListener);

        public abstract AbstractExecutionListenerDispatch getDispatch(ExecutionListener executionListener);

        public abstract Object getReceiver(ExecutionEvent executionEvent);

        public abstract AbstractExecutionEventDispatch getDispatch(ExecutionEvent executionEvent);
    }

    public abstract static class IOAccessor {
        protected IOAccessor() {
            if (!getClass().getCanonicalName().equals("org.graalvm.polyglot.io.IOHelper.IOAccessorImpl")) {
                throw new AssertionError("Only one implementation of IOAccess allowed. " + getClass().getCanonicalName());
            }
        }

        public abstract ProcessHandler.ProcessCommand newProcessCommand(List<String> cmd, String cwd, Map<String, String> environment, boolean redirectErrorStream,
                        ProcessHandler.Redirect inputRedirect, ProcessHandler.Redirect outputRedirect, ProcessHandler.Redirect errorRedirect);

        public abstract ProcessHandler.Redirect createRedirectToStream(OutputStream stream);

        public abstract OutputStream getOutputStream(ProcessHandler.Redirect redirect);

        public abstract FileSystem getFileSystem(IOAccess ioAccess);

        public abstract boolean hasHostFileAccess(IOAccess ioAccess);

        public abstract boolean hasHostSocketAccess(IOAccess ioaccess);
    }

    public abstract static class APIAccess {

        protected APIAccess() {
            if (!getClass().getCanonicalName().equals("org.graalvm.polyglot.Engine.APIAccessImpl")) {
                throw new AssertionError("Only one implementation of APIAccess allowed. " + getClass().getCanonicalName());
            }
        }

        public abstract Engine newEngine(AbstractEngineDispatch dispatch, Object receiver, boolean registerInActiveEngines);

        public abstract Context newContext(AbstractContextDispatch dispatch, Object receiver, Engine engine);

        public abstract Language newLanguage(AbstractLanguageDispatch dispatch, Object receiver);

        public abstract Instrument newInstrument(AbstractInstrumentDispatch dispatch, Object receiver);

        public abstract Value newValue(AbstractValueDispatch dispatch, Object context, Object receiver);

        public abstract Source newSource(AbstractSourceDispatch dispatch, Object receiver);

        public abstract SourceSection newSourceSection(Source source, AbstractSourceSectionDispatch dispatch, Object receiver);

        public abstract PolyglotException newLanguageException(String message, AbstractExceptionDispatch dispatch, Object receiver);

        public abstract Object getReceiver(Instrument instrument);

        public abstract Object getReceiver(Language language);

        public abstract Object getReceiver(Engine engine);

        public abstract Object getReceiver(Context context);

        public abstract Object getReceiver(PolyglotException exception);

        public abstract Object getReceiver(Value value);

        public abstract Object getReceiver(ResourceLimits value);

        public abstract Object getReceiver(Source source);

        public abstract Object getReceiver(SourceSection sourceSection);

        public abstract AbstractValueDispatch getDispatch(Value value);

        public abstract Object getContext(Value value);

        public abstract AbstractStackFrameImpl getDispatch(StackFrame value);

        public abstract AbstractLanguageDispatch getDispatch(Language value);

        public abstract AbstractInstrumentDispatch getDispatch(Instrument value);

        public abstract AbstractEngineDispatch getDispatch(Engine engine);

        public abstract AbstractContextDispatch getDispatch(Context context);

        public abstract AbstractSourceDispatch getDispatch(Source source);

        public abstract AbstractSourceSectionDispatch getDispatch(SourceSection sourceSection);

        public abstract ResourceLimitEvent newResourceLimitsEvent(Context context);

        public abstract StackFrame newPolyglotStackTraceElement(AbstractStackFrameImpl dispatch, Object receiver);

        public abstract List<Object> getTargetMappings(HostAccess access);

        public abstract boolean allowsAccess(HostAccess access, AnnotatedElement element);

        public abstract boolean allowsImplementation(HostAccess access, Class<?> type);

        public abstract boolean isMethodScopingEnabled(HostAccess access);

        public abstract boolean isMethodScoped(HostAccess access, Executable e);

        public abstract boolean isArrayAccessible(HostAccess access);

        public abstract boolean isListAccessible(HostAccess access);

        public abstract boolean isBufferAccessible(HostAccess access);

        public abstract boolean isIterableAccessible(HostAccess access);

        public abstract boolean isIteratorAccessible(HostAccess access);

        public abstract boolean isMapAccessible(HostAccess access);

        public abstract boolean allowsPublicAccess(HostAccess hostAccess);

        public abstract boolean allowsAccessInheritance(HostAccess hostAccess);

        public abstract Object getHostAccessImpl(HostAccess conf);

        public abstract void setHostAccessImpl(HostAccess conf, Object impl);

        public abstract UnmodifiableEconomicSet<String> getEvalAccess(PolyglotAccess access, String language);

        public abstract UnmodifiableEconomicMap<String, UnmodifiableEconomicSet<String>> getEvalAccess(PolyglotAccess access);

        public abstract UnmodifiableEconomicSet<String> getBindingsAccess(PolyglotAccess access);

        public abstract String validatePolyglotAccess(PolyglotAccess access, Set<String> language);

        public abstract void engineClosed(Engine engine);

        public abstract MutableTargetMapping[] getMutableTargetMappings(HostAccess access);

    }

    // shared SPI

    private APIAccess api;
    private ManagementAccess management;
    private IOAccessor io;

    private AbstractPolyglotImpl next;
    private AbstractPolyglotImpl prev;

    public final void setMonitoring(ManagementAccess monitoring) {
        this.management = monitoring;
        AbstractPolyglotImpl nextImpl = next;
        if (nextImpl != null) {
            nextImpl.setMonitoring(monitoring);
        }
    }

    public final void setConstructors(APIAccess constructors) {
        this.api = constructors;
        initialize();
    }

    public final void setNext(AbstractPolyglotImpl next) {
        this.next = next;
        if (next != null) {
            next.prev = this;
        }
    }

    public final AbstractPolyglotImpl getNext() {
        if (next == null) {
            throw new AbstractMethodError("No implementation available.");
        }
        return next;
    }

    public final void setIO(IOAccessor ioAccess) {
        Objects.requireNonNull(ioAccess, "IOAccess must be non null.");
        this.io = ioAccess;
        AbstractPolyglotImpl nextImpl = this.next;
        if (nextImpl != null) {
            nextImpl.setIO(ioAccess);
        }
    }

    public final APIAccess getAPIAccess() {
        return api;
    }

    public final ManagementAccess getManagement() {
        return management;
    }

    public final IOAccessor getIO() {
        if (io == null) {
            try {
                Class.forName("org.graalvm.polyglot.io.IOHelper", true, getClass().getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
        return io;
    }

    protected void initialize() {
    }

    public Engine buildEngine(String[] permittedLanguages, OutputStream out, OutputStream err, InputStream in, Map<String, String> options, boolean useSystemProperties,
                    boolean allowExperimentalOptions,
                    boolean boundEngine, MessageTransport messageInterceptor, LogHandler logHandler, Object hostLanguage, boolean hostLanguageOnly, boolean registerInActiveEngines,
                    AbstractPolyglotHostService polyglotHostService) {
        return getNext().buildEngine(permittedLanguages, out, err, in, options, useSystemProperties, allowExperimentalOptions, boundEngine, messageInterceptor, logHandler, hostLanguage,
                        hostLanguageOnly, registerInActiveEngines, polyglotHostService);
    }

    public abstract int getPriority();

    public void preInitializeEngine() {
        getNext().preInitializeEngine();
    }

    public Object createHostLanguage(AbstractHostAccess access) {
        return getNext().createHostLanguage(access);
    }

    public void resetPreInitializedEngine() {
        getNext().resetPreInitializedEngine();
    }

    public Source build(String language, Object origin, URI uri, String name, String mimeType, Object content, boolean interactive, boolean internal, boolean cached, Charset encoding, URL url,
                    String path)
                    throws IOException {
        return getNext().build(language, origin, uri, name, mimeType, content, interactive, internal, cached, encoding, url, path);
    }

    public String findLanguage(File file) throws IOException {
        return getNext().findLanguage(file);
    }

    public String findLanguage(URL url) throws IOException {
        return getNext().findLanguage(url);
    }

    public String findLanguage(String mimeType) {
        return getNext().findLanguage(mimeType);
    }

    public String findMimeType(File file) throws IOException {
        return getNext().findMimeType(file);
    }

    public String findMimeType(URL url) throws IOException {
        return getNext().findMimeType(url);
    }

    public AbstractHostAccess createHostAccess() {
        return getNext().createHostAccess();
    }

    /**
     * Marker base class for native-image.
     */
    public abstract static class AbstractDispatchClass {

    }

    public abstract static class AbstractExecutionListenerDispatch extends AbstractDispatchClass {

        protected AbstractExecutionListenerDispatch(AbstractPolyglotImpl polyglotImpl) {
            Objects.requireNonNull(polyglotImpl);
        }

        public abstract void closeExecutionListener(Object impl);

    }

    public abstract static class AbstractExecutionEventDispatch extends AbstractDispatchClass {

        protected AbstractExecutionEventDispatch(AbstractPolyglotImpl polyglotImpl) {
            Objects.requireNonNull(polyglotImpl);
        }

        public abstract List<Value> getExecutionEventInputValues(Object impl);

        public abstract SourceSection getExecutionEventLocation(Object impl);

        public abstract String getExecutionEventRootName(Object impl);

        public abstract Value getExecutionEventReturnValue(Object impl);

        public abstract boolean isExecutionEventExpression(Object impl);

        public abstract boolean isExecutionEventStatement(Object impl);

        public abstract boolean isExecutionEventRoot(Object impl);

        public abstract PolyglotException getExecutionEventException(Object impl);

    }

    public abstract static class AbstractSourceDispatch extends AbstractDispatchClass {

        protected AbstractSourceDispatch(AbstractPolyglotImpl engineImpl) {
            Objects.requireNonNull(engineImpl);
        }

        public abstract String getName(Object impl);

        public abstract String getPath(Object impl);

        public abstract boolean isCached(Object impl);

        public abstract boolean isInteractive(Object impl);

        public abstract URL getURL(Object impl);

        public abstract URI getURI(Object impl);

        public abstract Reader getReader(Object impl);

        public abstract InputStream getInputStream(Object impl);

        public abstract int getLength(Object impl);

        public abstract CharSequence getCharacters(Object impl);

        public abstract CharSequence getCharacters(Object impl, int lineNumber);

        public abstract int getLineCount(Object impl);

        public abstract int getLineNumber(Object impl, int offset);

        public abstract int getColumnNumber(Object impl, int offset);

        public abstract int getLineStartOffset(Object impl, int lineNumber);

        public abstract int getLineLength(Object impl, int lineNumber);

        public abstract String toString(Object impl);

        public abstract int hashCode(Object impl);

        public abstract boolean equals(Object impl, Object otherImpl);

        public abstract boolean isInternal(Object impl);

        public abstract ByteSequence getBytes(Object impl);

        public abstract boolean hasCharacters(Object impl);

        public abstract boolean hasBytes(Object impl);

        public abstract String getMimeType(Object impl);

        public abstract String getLanguage(Object impl);

    }

    public abstract static class AbstractSourceSectionDispatch extends AbstractDispatchClass {

        protected AbstractSourceSectionDispatch(AbstractPolyglotImpl polyglotImpl) {
            Objects.requireNonNull(polyglotImpl);
        }

        public abstract boolean isAvailable(Object impl);

        public abstract boolean hasLines(Object impl);

        public abstract boolean hasColumns(Object impl);

        public abstract boolean hasCharIndex(Object impl);

        public abstract int getStartLine(Object impl);

        public abstract int getStartColumn(Object impl);

        public abstract int getEndLine(Object impl);

        public abstract int getEndColumn(Object impl);

        public abstract int getCharIndex(Object impl);

        public abstract int getCharLength(Object impl);

        public abstract int getCharEndIndex(Object impl);

        public abstract CharSequence getCode(Object impl);

        public abstract String toString(Object impl);

        public abstract int hashCode(Object impl);

        public abstract boolean equals(Object impl, Object obj);

    }

    public abstract static class AbstractContextDispatch extends AbstractDispatchClass {

        protected AbstractContextDispatch(AbstractPolyglotImpl impl) {
            Objects.requireNonNull(impl);
        }

        public abstract boolean initializeLanguage(Object receiver, String languageId);

        public abstract Value eval(Object receiver, String language, Source source);

        public abstract Value parse(Object receiver, String language, Source source);

        public abstract void close(Object receiver, boolean cancelIfExecuting);

        public abstract boolean interrupt(Object receiver, Duration timeout);

        public abstract Value asValue(Object receiver, Object hostValue);

        public abstract void explicitEnter(Object receiver);

        public abstract void explicitLeave(Object receiver);

        public abstract Value getBindings(Object receiver, String language);

        public abstract Value getPolyglotBindings(Object receiver);

        public abstract void resetLimits(Object receiver);

        public abstract void safepoint(Object receiver);

        public abstract void setAPI(Object receiver, Context key);

    }

    public abstract static class AbstractEngineDispatch extends AbstractDispatchClass {

        protected AbstractEngineDispatch(AbstractPolyglotImpl impl) {
            Objects.requireNonNull(impl);
        }

        public abstract void setAPI(Object receiver, Engine key);

        public abstract Language requirePublicLanguage(Object receiver, String id);

        public abstract Instrument requirePublicInstrument(Object receiver, String id);

        // Runtime
        public abstract void close(Object receiver, Object apiObject, boolean cancelIfExecuting);

        public abstract Map<String, Instrument> getInstruments(Object receiver);

        public abstract Map<String, Language> getLanguages(Object receiver);

        public abstract OptionDescriptors getOptions(Object receiver);

        public abstract Context createContext(Object receiver, OutputStream out, OutputStream err, InputStream in,
                        boolean allowHostLookup,
                        HostAccess hostAccess,
                        PolyglotAccess polyglotAccess,
                        boolean allowNativeAccess,
                        boolean allowCreateThread, boolean allowHostClassLoading, boolean allowInnerContextOptions, boolean allowExperimentalOptions,
                        Predicate<String> classFilter,
                        Map<String, String> options,
                        Map<String, String[]> arguments, String[] onlyLanguages, IOAccess ioAccess, LogHandler logHandler, boolean allowCreateProcess, ProcessHandler processHandler,
                        EnvironmentAccess environmentAccess, Map<String, String> environment, ZoneId zone, Object limitsImpl, String currentWorkingDirectory, ClassLoader hostClassLoader,
                        boolean allowValueSharing, boolean useSystemExit);

        public abstract String getImplementationName(Object receiver);

        public abstract Set<Source> getCachedSources(Object receiver);

        public abstract String getVersion(Object receiver);

        public abstract ExecutionListener attachExecutionListener(Object engine, Consumer<ExecutionEvent> onEnter,
                        Consumer<ExecutionEvent> onReturn,
                        boolean expressions,
                        boolean statements,
                        boolean roots,
                        Predicate<Source> sourceFilter, Predicate<String> rootFilter, boolean collectInputValues, boolean collectReturnValues, boolean collectExceptions);

        public abstract void shutdown(Object engine);

        public abstract RuntimeException hostToGuestException(Object engineReceiver, Throwable throwable);

    }

    public abstract static class AbstractExceptionDispatch extends AbstractDispatchClass {

        protected AbstractExceptionDispatch(AbstractPolyglotImpl engineImpl) {
            Objects.requireNonNull(engineImpl);
        }

        public abstract boolean isInternalError(Object receiver);

        public abstract boolean isCancelled(Object receiver);

        public abstract boolean isExit(Object receiver);

        public abstract int getExitStatus(Object receiver);

        public abstract Iterable<StackFrame> getPolyglotStackTrace(Object receiver);

        public abstract boolean isSyntaxError(Object receiver);

        public abstract Value getGuestObject(Object receiver);

        public abstract boolean isIncompleteSource(Object receiver);

        public abstract void onCreate(Object receiver, PolyglotException api);

        public abstract void printStackTrace(Object receiver, PrintStream s);

        public abstract void printStackTrace(Object receiver, PrintWriter s);

        public abstract StackTraceElement[] getStackTrace(Object receiver);

        public abstract String getMessage(Object receiver);

        public abstract boolean isHostException(Object receiver);

        public abstract Throwable asHostException(Object receiver);

        public abstract SourceSection getSourceLocation(Object receiver);

        public abstract boolean isResourceExhausted(Object receiver);

        public abstract boolean isInterrupted(Object receiver);

    }

    public abstract static class AbstractStackFrameImpl extends AbstractDispatchClass {

        protected AbstractStackFrameImpl(AbstractPolyglotImpl engineImpl) {
            Objects.requireNonNull(engineImpl);
        }

        public abstract StackTraceElement toHostFrame();

        public abstract SourceSection getSourceLocation();

        public abstract String getRootName();

        public abstract Language getLanguage();

        public abstract boolean isHostFrame();

        public abstract String toStringImpl(int languageColumn);

    }

    public abstract static class AbstractInstrumentDispatch extends AbstractDispatchClass {

        protected AbstractInstrumentDispatch(AbstractPolyglotImpl engineImpl) {
            Objects.requireNonNull(engineImpl);
        }

        public abstract String getId(Object receiver);

        public abstract String getName(Object receiver);

        public abstract OptionDescriptors getOptions(Object receiver);

        public abstract String getVersion(Object receiver);

        public abstract <T> T lookup(Object receiver, Class<T> type);

        public abstract String getWebsite(Object receiver);
    }

    public abstract static class AbstractLanguageDispatch extends AbstractDispatchClass {

        protected AbstractLanguageDispatch(AbstractPolyglotImpl engineImpl) {
            Objects.requireNonNull(engineImpl);
        }

        public abstract String getName(Object receiver);

        public abstract String getImplementationName(Object receiver);

        public abstract boolean isInteractive(Object receiver);

        public abstract String getVersion(Object receiver);

        public abstract String getId(Object receiver);

        public abstract OptionDescriptors getOptions(Object receiver);

        public abstract Set<String> getMimeTypes(Object receiver);

        public abstract String getDefaultMimeType(Object receiver);

        public abstract String getWebsite(Object receiver);
    }

    public abstract static class AbstractHostAccess extends AbstractDispatchClass {

        protected AbstractHostAccess(AbstractPolyglotImpl impl) {
            Objects.requireNonNull(impl);
        }

        public abstract Object toGuestValue(Object internalContext, Object hostValue);

        public abstract <T> List<T> toList(Object internalContext, Object guestValue, boolean implementFunction, Class<T> elementClass, Type elementType);

        public abstract <K, V> Map<K, V> toMap(Object internalContext, Object foreignObject, boolean implementsFunction, Class<K> keyClass, Type keyType, Class<V> valueClass, Type valueType);

        public abstract <K, V> Map.Entry<K, V> toMapEntry(Object internalContext, Object foreignObject, boolean implementsFunction,
                        Class<K> keyClass, Type keyType, Class<V> valueClass, Type valueType);

        public abstract <T> Function<?, ?> toFunction(Object internalContext, Object function, Class<?> returnClass, Type returnType, Class<?> paramClass, Type paramType);

        public abstract Object toObjectProxy(Object internalContext, Class<?> clazz, Object obj) throws IllegalArgumentException;

        public abstract <T> T toFunctionProxy(Object internalContext, Class<T> functionalType, Object function);

        public abstract <T> Iterable<T> toIterable(Object internalContext, Object iterable, boolean implementFunction, Class<T> elementClass, Type elementType);

        public abstract <T> Iterator<T> toIterator(Object internalContext, Object iterable, boolean implementFunction, Class<T> elementClass, Type elementType);

        public abstract PolyglotException toPolyglotException(Object internalContext, Throwable e);

        public abstract Value toValue(Object internalContext, Object receiver);

        public abstract String getValueInfo(Object internalContext, Object value);

        public abstract Value[] toValues(Object internalContext, Object[] values, int startIndex);

        public abstract Value[] toValues(Object internalContext, Object[] values);

        public abstract void rethrowPolyglotException(Object internalContext, PolyglotException e);

        public abstract RuntimeException toEngineException(RuntimeException e);

        public abstract boolean isEngineException(RuntimeException e);

        public abstract RuntimeException unboxEngineException(RuntimeException e);
    }

    public abstract static class AbstractPolyglotHostService extends AbstractDispatchClass {

        protected AbstractPolyglotHostService(AbstractPolyglotImpl polyglot) {
            Objects.requireNonNull(polyglot);
        }

        public abstract void notifyClearExplicitContextStack(Object contextReceiver);

        public abstract void notifyContextCancellingOrExiting(Object contextReceiver, boolean exit, int exitCode, boolean resourceLimit, String message);

        public abstract void notifyContextClosed(Object contextReceiver, boolean cancelIfExecuting, boolean resourceLimit, String message);

        public abstract void notifyEngineClosed(Object engineReceiver, boolean cancelIfExecuting);

        public abstract RuntimeException hostToGuestException(AbstractHostLanguageService hostLanguageService, Throwable throwable);
    }

    public abstract static class AbstractHostLanguageService extends AbstractDispatchClass {

        protected AbstractHostLanguageService(AbstractPolyglotImpl polyglot) {
            Objects.requireNonNull(polyglot);
        }

        public abstract void release();

        public abstract void initializeHostContext(Object internalContext, Object context, HostAccess access, ClassLoader cl, Predicate<String> clFilter, boolean hostCLAllowed,
                        boolean hostLookupAllowed);

        public abstract void throwHostLanguageException(String message);

        public abstract void addToHostClassPath(Object context, Object truffleFile);

        public abstract Object toGuestValue(Object context, Object hostValue, boolean asValue);

        public abstract Object asHostDynamicClass(Object context, Class<?> value);

        public abstract Object asHostStaticClass(Object context, Class<?> value);

        public abstract Object findDynamicClass(Object context, String classValue);

        public abstract Object findStaticClass(Object context, String classValue);

        public abstract Object createToHostTypeNode();

        public abstract <T> T toHostType(Object hostNode, Object hostContext, Object value, Class<T> targetType, Type genericType);

        public abstract boolean isHostValue(Object value);

        public abstract Object unboxHostObject(Object hostValue);

        public abstract Object unboxProxyObject(Object hostValue);

        public abstract Throwable unboxHostException(Throwable hostValue);

        public abstract Object toHostObject(Object context, Object value);

        public abstract RuntimeException toHostException(Object hostContext, Throwable exception);

        public abstract boolean isHostException(Object exception);

        public abstract boolean isHostFunction(Object obj);

        public abstract boolean isHostObject(Object obj);

        public abstract boolean isHostSymbol(Object obj);

        public abstract Object createHostAdapter(Object hostContextObject, Object[] types, Object classOverrides);

        public abstract boolean isHostProxy(Object value);

        public abstract Error toHostResourceError(Throwable hostException);

        public abstract int findNextGuestToHostStackTraceElement(StackTraceElement firstElement, StackTraceElement[] hostStack, int nextElementIndex);

        public abstract Object migrateValue(Object hostContext, Object value, Object valueContext);

        public abstract void pin(Object receiver);

        public abstract void hostExit(int exitCode);

    }

    public abstract static class AbstractValueDispatch extends AbstractDispatchClass {

        protected AbstractValueDispatch(AbstractPolyglotImpl impl) {
            Objects.requireNonNull(impl);
        }

        public boolean hasArrayElements(Object context, Object receiver) {
            return false;
        }

        public abstract Value getArrayElement(Object context, Object receiver, long index);

        public abstract void setArrayElement(Object context, Object receiver, long index, Object value);

        public abstract boolean removeArrayElement(Object context, Object receiver, long index);

        public abstract long getArraySize(Object context, Object receiver);

        // region Buffer Methods

        public boolean hasBufferElements(Object context, Object receiver) {
            return false;
        }

        public abstract boolean isBufferWritable(Object context, Object receiver);

        public abstract long getBufferSize(Object context, Object receiver);

        public abstract byte readBufferByte(Object context, Object receiver, long byteOffset);

        public abstract void writeBufferByte(Object context, Object receiver, long byteOffset, byte value);

        public abstract short readBufferShort(Object context, Object receiver, ByteOrder order, long byteOffset);

        public abstract void writeBufferShort(Object context, Object receiver, ByteOrder order, long byteOffset, short value);

        public abstract int readBufferInt(Object context, Object receiver, ByteOrder order, long byteOffset);

        public abstract void writeBufferInt(Object context, Object receiver, ByteOrder order, long byteOffset, int value);

        public abstract long readBufferLong(Object context, Object receiver, ByteOrder order, long byteOffset);

        public abstract void writeBufferLong(Object context, Object receiver, ByteOrder order, long byteOffset, long value);

        public abstract float readBufferFloat(Object context, Object receiver, ByteOrder order, long byteOffset);

        public abstract void writeBufferFloat(Object context, Object receiver, ByteOrder order, long byteOffset, float value);

        public abstract double readBufferDouble(Object context, Object receiver, ByteOrder order, long byteOffset);

        public abstract void writeBufferDouble(Object context, Object receiver, ByteOrder order, long byteOffset, double value);

        // endregion

        public boolean hasMembers(Object context, Object receiver) {
            return false;
        }

        public abstract Value getMember(Object context, Object receiver, String key);

        public boolean hasMember(Object context, Object receiver, String key) {
            return false;
        }

        public Context getContext(Object context) {
            return null;
        }

        public Set<String> getMemberKeys(Object context, Object receiver) {
            return Collections.emptySet();
        }

        public abstract void putMember(Object context, Object receiver, String key, Object member);

        public abstract boolean removeMember(Object context, Object receiver, String key);

        public boolean canExecute(Object context, Object receiver) {
            return false;
        }

        public abstract Value execute(Object context, Object receiver, Object[] arguments);

        public abstract Value execute(Object context, Object receiver);

        public boolean canInstantiate(Object context, Object receiver) {
            return false;
        }

        public abstract Value newInstance(Object context, Object receiver, Object[] arguments);

        public abstract void executeVoid(Object context, Object receiver, Object[] arguments);

        public abstract void executeVoid(Object context, Object receiver);

        public boolean canInvoke(Object context, String identifier, Object receiver) {
            return false;
        }

        public abstract Value invoke(Object context, Object receiver, String identifier, Object[] arguments);

        public abstract Value invoke(Object context, Object receiver, String identifier);

        public boolean isString(Object context, Object receiver) {
            return false;
        }

        public abstract String asString(Object context, Object receiver);

        public boolean isBoolean(Object context, Object receiver) {
            return false;
        }

        public abstract boolean asBoolean(Object context, Object receiver);

        public boolean fitsInInt(Object context, Object receiver) {
            return false;
        }

        public abstract int asInt(Object context, Object receiver);

        public boolean fitsInLong(Object context, Object receiver) {
            return false;
        }

        public abstract long asLong(Object context, Object receiver);

        public boolean fitsInDouble(Object context, Object receiver) {
            return false;
        }

        public abstract double asDouble(Object context, Object receiver);

        public boolean fitsInFloat(Object context, Object receiver) {
            return false;
        }

        public abstract float asFloat(Object context, Object receiver);

        public boolean isNull(Object context, Object receiver) {
            return false;
        }

        public boolean isNativePointer(Object context, Object receiver) {
            return false;
        }

        public boolean fitsInByte(Object context, Object receiver) {
            return false;
        }

        public abstract byte asByte(Object context, Object receiver);

        public boolean fitsInShort(Object context, Object receiver) {
            return false;
        }

        public abstract short asShort(Object context, Object receiver);

        public abstract long asNativePointer(Object context, Object receiver);

        public boolean isHostObject(Object context, Object receiver) {
            return false;
        }

        public boolean isProxyObject(Object context, Object receiver) {
            return false;
        }

        public abstract Object asHostObject(Object context, Object receiver);

        public abstract Object asProxyObject(Object context, Object receiver);

        public abstract String toString(Object context, Object receiver);

        public abstract Value getMetaObject(Object context, Object receiver);

        public boolean isNumber(Object context, Object receiver) {
            return false;
        }

        public abstract <T> T as(Object context, Object receiver, Class<T> targetType);

        public abstract <T> T as(Object context, Object receiver, TypeLiteral<T> targetType);

        public abstract SourceSection getSourceLocation(Object context, Object receiver);

        public boolean isDate(Object context, Object receiver) {
            return false;
        }

        public abstract LocalDate asDate(Object context, Object receiver);

        public boolean isTime(Object context, Object receiver) {
            return false;
        }

        public abstract LocalTime asTime(Object context, Object receiver);

        public abstract Instant asInstant(Object context, Object receiver);

        public boolean isTimeZone(Object context, Object receiver) {
            return false;
        }

        public abstract ZoneId asTimeZone(Object context, Object receiver);

        public boolean isDuration(Object context, Object receiver) {
            return false;
        }

        public abstract Duration asDuration(Object context, Object receiver);

        public boolean isException(Object context, Object receiver) {
            return false;
        }

        public abstract RuntimeException throwException(Object context, Object receiver);

        public boolean isMetaObject(Object context, Object receiver) {
            return false;
        }

        public abstract String getMetaQualifiedName(Object context, Object receiver);

        public abstract String getMetaSimpleName(Object context, Object receiver);

        public abstract boolean isMetaInstance(Object context, Object receiver, Object instance);

        public abstract boolean hasMetaParents(Object context, Object receiver);

        public abstract Value getMetaParents(Object context, Object receiver);

        public abstract boolean equalsImpl(Object context, Object receiver, Object obj);

        public abstract int hashCodeImpl(Object context, Object receiver);

        public boolean hasIterator(Object context, Object receiver) {
            return false;
        }

        public abstract Value getIterator(Object context, Object receiver);

        public boolean isIterator(Object context, Object receiver) {
            return false;
        }

        public abstract boolean hasIteratorNextElement(Object context, Object receiver);

        public abstract Value getIteratorNextElement(Object context, Object receiver);

        public boolean hasHashEntries(Object context, Object receiver) {
            return false;
        }

        public abstract long getHashSize(Object context, Object receiver);

        public boolean hasHashEntry(Object context, Object receiver, Object key) {
            return false;
        }

        public abstract Value getHashValue(Object context, Object receiver, Object key);

        public abstract Value getHashValueOrDefault(Object context, Object receiver, Object key, Object defaultValue);

        public abstract void putHashEntry(Object context, Object receiver, Object key, Object value);

        public abstract boolean removeHashEntry(Object context, Object receiver, Object key);

        public abstract Value getHashEntriesIterator(Object context, Object receiver);

        public abstract Value getHashKeysIterator(Object context, Object receiver);

        public abstract Value getHashValuesIterator(Object context, Object receiver);

        public abstract void pin(Object languageContext, Object receiver);
    }

    public Class<?> loadLanguageClass(String className) {
        return getNext().loadLanguageClass(className);
    }

    public Context getCurrentContext() {
        return getNext().getCurrentContext();
    }

    public Value asValue(Object o) {
        return getNext().asValue(o);
    }

    public <S, T> Object newTargetTypeMapping(Class<S> sourceType, Class<T> targetType, Predicate<S> acceptsValue, Function<S, T> convertValue, TargetMappingPrecedence precedence) {
        return getNext().newTargetTypeMapping(sourceType, targetType, acceptsValue, convertValue, precedence);
    }

    public Object buildLimits(long statementLimit, Predicate<Source> statementLimitSourceFilter, Consumer<ResourceLimitEvent> onLimit) {
        return getNext().buildLimits(statementLimit, statementLimitSourceFilter, onLimit);
    }

    public FileSystem newDefaultFileSystem() {
        return getNext().newDefaultFileSystem();
    }

    public FileSystem allowLanguageHomeAccess(FileSystem fileSystem) {
        return getNext().allowLanguageHomeAccess(fileSystem);
    }

    public FileSystem newReadOnlyFileSystem(FileSystem fileSystem) {
        return getNext().newReadOnlyFileSystem(fileSystem);
    }

    public ProcessHandler newDefaultProcessHandler() {
        return getNext().newDefaultProcessHandler();
    }

    public boolean isDefaultProcessHandler(ProcessHandler processHandler) {
        return getNext().isDefaultProcessHandler(processHandler);
    }

    public ThreadScope createThreadScope() {
        return getNext().createThreadScope();
    }

    public LogHandler newLogHandler(Object logHandlerOrStream) {
        return getNext().newLogHandler(logHandlerOrStream);
    }

    /**
     * Creates a union of all available option descriptors including prev implementations. This
     * allows to validate the full set of options.
     */
    protected final OptionDescriptors createAllEngineOptionDescriptors() {
        AbstractPolyglotImpl current = this;
        while (current.prev != null) {
            current = current.prev;
        }
        OptionDescriptors union = OptionDescriptors.EMPTY;
        while (current != null) {
            union = OptionDescriptors.createUnion(current.createEngineOptionDescriptors(), union);
            current = current.next;
        }
        return union;
    }

    /**
     * Returns all additional option descriptors of the current polyglot impl or <code>null</code>.
     * Do not delegate to {@link #getNext()} in this method.
     */
    protected OptionDescriptors createEngineOptionDescriptors() {
        return OptionDescriptors.EMPTY;
    }

    public final AbstractPolyglotImpl getRootImpl() {
        AbstractPolyglotImpl current = this;
        while (current.prev != null) {
            current = current.prev;
        }
        return current;
    }

    public abstract static class ThreadScope implements AutoCloseable {

        protected ThreadScope(AbstractPolyglotImpl engineImpl) {
            Objects.requireNonNull(engineImpl);
        }

        @Override
        public abstract void close();
    }

    public abstract static class LogHandler {

        protected LogHandler(AbstractPolyglotImpl polyglot) {
            Objects.requireNonNull(polyglot);
        }

        public abstract void publish(LogRecord logRecord);

        public abstract void flush();

        public abstract void close();

    }

}
