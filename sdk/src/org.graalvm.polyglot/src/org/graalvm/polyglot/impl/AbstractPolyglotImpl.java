/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.invoke.MethodHandles;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Executable;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.file.Path;
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

import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.HostAccess.MutableTargetMapping;
import org.graalvm.polyglot.HostAccess.TargetMappingPrecedence;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.MessageTransport;
import org.graalvm.polyglot.io.ProcessHandler;

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
            if (!getClass().getCanonicalName().equals("org.graalvm.polyglot.management.Management.ManagementAccessImpl") &&
                            !getClass().getCanonicalName().equals("org.graalvm.polyglot.impl.ModuleToUnnamedManagementAccessGen")) {
                throw new AssertionError("Only one implementation of ManagementAccessImpl allowed. " + getClass().getCanonicalName());
            }
        }

        public abstract Object newExecutionListener(AbstractExecutionListenerDispatch dispatch, Object receiver);

        public abstract Object newExecutionEvent(AbstractExecutionEventDispatch dispatch, Object event);

        public abstract Object getExecutionListenerReceiver(Object executionListener);

        public abstract AbstractExecutionListenerDispatch getExecutionListenerDispatch(Object executionListener);

        public abstract Object getExecutionEventReceiver(Object executionEvent);

        public abstract AbstractExecutionEventDispatch getExecutionEventDispatch(Object executionEvent);
    }

    public abstract static class IOAccessor {
        protected IOAccessor() {
            if (!getClass().getCanonicalName().equals("org.graalvm.polyglot.io.IOHelper.IOAccessorImpl") &&
                            !getClass().getCanonicalName().equals("org.graalvm.polyglot.impl.ModuleToUnnamedIOAccessorGen")) {
                throw new AssertionError("Only one implementation of IOAccess allowed. " + getClass().getCanonicalName());
            }
        }

        public abstract Object createIOAccess(String name, boolean allowHostFileAccess, boolean allowSocketAccess, FileSystem fileSystem);

        public abstract FileSystem getFileSystem(Object ioAccess);

        public abstract boolean hasHostFileAccess(Object ioAccess);

        public abstract boolean hasHostSocketAccess(Object ioaccess);

        public abstract boolean isVetoException(Throwable exception);

        public abstract Exception createVetoException(String message);

    }

    public abstract static class APIAccess {

        protected APIAccess() {
            String name = getClass().getCanonicalName();
            if (!name.equals("org.graalvm.polyglot.Engine.APIAccessImpl")) {
                throw new AssertionError("Only one implementation of APIAccess allowed. " + getClass().getCanonicalName());
            }
        }

        public abstract Object newEngine(AbstractEngineDispatch dispatch, Object receiver, boolean registerInActiveEngines);

        public abstract Object newContext(AbstractContextDispatch dispatch, Object receiver, Object engine);

        public abstract Object newLanguage(AbstractLanguageDispatch dispatch, Object receiver);

        public abstract Object newInstrument(AbstractInstrumentDispatch dispatch, Object receiver);

        public abstract Object newValue(AbstractValueDispatch dispatch, Object context, Object receiver);

        public abstract Object[] newValueArray(int size);

        public abstract Object newSource(AbstractSourceDispatch dispatch, Object receiver);

        public abstract Object newSourceSection(Object source, AbstractSourceSectionDispatch dispatch, Object receiver);

        public abstract Object getSourceSectionSource(Object sourceSection);

        public abstract RuntimeException newLanguageException(String message, AbstractExceptionDispatch dispatch, Object receiver);

        public abstract boolean isInstrument(Object instrument);

        public abstract boolean isLanguage(Object language);

        public abstract boolean isEngine(Object engine);

        public abstract boolean isContext(Object context);

        public abstract boolean isPolyglotException(Object exception);

        public abstract boolean isValue(Object value);

        public abstract boolean isSource(Object value);

        public abstract boolean isSourceSection(Object value);

        public abstract Class<?> getValueClass();

        public abstract Object getInstrumentReceiver(Object instrument);

        public abstract Object getLanguageReceiver(Object language);

        public abstract Object getEngineReceiver(Object engine);

        public abstract Object getContextReceiver(Object context);

        public abstract Object getPolyglotExceptionReceiver(RuntimeException exception);

        public abstract Object getValueReceiver(Object value);

        public abstract Object getResourceLimitsReceiver(Object value);

        public abstract Object getSourceReceiver(Object source);

        public abstract Object getSourceSectionReceiver(Object sourceSection);

        public abstract AbstractStackFrameImpl getStackFrameReceiver(Object value);

        public abstract AbstractValueDispatch getValueDispatch(Object value);

        public abstract Object getValueContext(Object value);

        public abstract AbstractStackFrameImpl getStackFrameDispatch(Object value);

        public abstract AbstractLanguageDispatch getLanguageDispatch(Object value);

        public abstract AbstractInstrumentDispatch getInstrumentDispatch(Object value);

        public abstract AbstractEngineDispatch getEngineDispatch(Object engine);

        public abstract AbstractContextDispatch getContextDispatch(Object context);

        public abstract AbstractSourceDispatch getSourceDispatch(Object source);

        public abstract AbstractSourceSectionDispatch getSourceSectionDispatch(Object sourceSection);

        public abstract Object newResourceLimitsEvent(Object context);

        public abstract Object newPolyglotStackTraceElement(AbstractStackFrameImpl dispatch, RuntimeException polyglotException);

        public abstract List<Object> getTargetMappings(Object hostAccess);

        public abstract boolean allowsAccess(Object hostAccess, AnnotatedElement element);

        public abstract boolean allowsImplementation(Object hostAccess, Class<?> type);

        public abstract boolean isMethodScopingEnabled(Object hostAccess);

        public abstract boolean isMethodScoped(Object hostAccess, Executable e);

        public abstract boolean isArrayAccessible(Object hostAccess);

        public abstract boolean isListAccessible(Object hostAccess);

        public abstract boolean isBufferAccessible(Object hostAccess);

        public abstract boolean isIterableAccessible(Object hostAccess);

        public abstract boolean isIteratorAccessible(Object hostAccess);

        public abstract boolean isMapAccessible(Object hostAccess);

        public abstract boolean isBigIntegerAccessibleAsNumber(Object hostAccess);

        public abstract boolean allowsPublicAccess(Object hostAccess);

        public abstract boolean allowsAccessInheritance(Object hostAccess);

        public abstract Object getHostAccessImpl(Object hostAccess);

        public abstract MethodHandles.Lookup getMethodLookup(Object hostAccess);

        public abstract void setHostAccessImpl(Object hostAccess, Object impl);

        public abstract Set<String> getEvalAccess(Object polyglotAccess, String language);

        public abstract Map<String, Set<String>> getEvalAccess(Object polyglotAccess);

        public abstract Set<String> getBindingsAccess(Object polyglotAccess);

        public abstract String validatePolyglotAccess(Object polyglotAccess, Set<String> language);

        public abstract void engineClosed(Object engine);

        public abstract MutableTargetMapping[] getMutableTargetMappings(Object access);

        public abstract Map<String, String> readOptionsFromSystemProperties();

        public abstract boolean isByteSequence(Object origin);

        public abstract Class<?> getByteSequenceClass();

        public abstract ByteSequence asByteSequence(Object origin);

        public abstract Object toByteSequence(Object origin);

        public abstract int byteSequenceLength(Object origin);

        public abstract byte byteSequenceByteAt(Object origin, int index);

        public abstract boolean isProxyArray(Object proxy);

        public abstract boolean isProxyDate(Object proxy);

        public abstract boolean isProxyDuration(Object proxy);

        public abstract boolean isProxyExecutable(Object proxy);

        public abstract boolean isProxyHashMap(Object proxy);

        public abstract boolean isProxyInstant(Object proxy);

        public abstract boolean isProxyInstantiable(Object proxy);

        public abstract boolean isProxyIterable(Object proxy);

        public abstract boolean isProxyIterator(Object proxy);

        public abstract boolean isProxyNativeObject(Object proxy);

        public abstract boolean isProxyObject(Object proxy);

        public abstract boolean isProxyTime(Object proxy);

        public abstract boolean isProxyTimeZone(Object proxy);

        public abstract boolean isProxy(Object proxy);

        public abstract Class<?> getProxyArrayClass();

        public abstract Class<?> getProxyDateClass();

        public abstract Class<?> getProxyDurationClass();

        public abstract Class<?> getProxyExecutableClass();

        public abstract Class<?> getProxyHashMapClass();

        public abstract Class<?> getProxyInstantClass();

        public abstract Class<?> getProxyInstantiableClass();

        public abstract Class<?> getProxyIterableClass();

        public abstract Class<?> getProxyIteratorClass();

        public abstract Class<?> getProxyNativeObjectClass();

        public abstract Class<?> getProxyObjectClass();

        public abstract Class<?> getProxyTimeClass();

        public abstract Class<?> getProxyTimeZoneClass();

        public abstract Class<?> getProxyClass();

        public abstract Object callProxyExecutableExecute(Object proxy, Object[] objects);

        public abstract Object callProxyNativeObjectAsPointer(Object proxy);

        public abstract Object callProxyInstantiableNewInstance(Object proxy, Object[] objects);

        public abstract Object callProxyArrayGet(Object proxy, long index);

        public abstract void callProxyArraySet(Object proxy, long index, Object value);

        public abstract boolean callProxyArrayRemove(Object proxy, long index);

        public abstract Object callProxyArraySize(Object proxy);

        public abstract Object callProxyObjectMemberKeys(Object proxy);

        public abstract Object callProxyObjectGetMember(Object proxy, String member);

        public abstract void callProxyObjectPutMember(Object proxy, String member, Object value);

        public abstract boolean callProxyObjectRemoveMember(Object proxy, String member);

        public abstract Object callProxyObjectHasMember(Object proxy, String string);

        public abstract ZoneId callProxyTimeZoneAsTimeZone(Object proxy);

        public abstract LocalDate callProxyDateAsDate(Object proxy);

        public abstract LocalTime callProxyTimeAsTime(Object proxy);

        public abstract Instant callProxyInstantAsInstant(Object proxy);

        public abstract Duration callProxyDurationAsDuration(Object proxy);

        public abstract Object callProxyIterableGetIterator(Object proxy);

        public abstract Object callProxyIteratorHasNext(Object proxy);

        public abstract Object callProxyIteratorGetNext(Object proxy);

        public abstract Object callProxyHashMapHasHashEntry(Object proxy, Object object);

        public abstract Object callProxyHashMapGetHashSize(Object proxy);

        public abstract Object callProxyHashMapGetHashValue(Object proxy, Object object);

        public abstract void callProxyHashMapPutHashEntry(Object proxy, Object object, Object object2);

        public abstract Object callProxyHashMapRemoveHashEntry(Object proxy, Object object);

        public abstract Object callProxyHashMapGetEntriesIterator(Object proxy);

        public abstract Object getIOAccessNone();

        public abstract Object getIOAccessAll();

        public abstract Object getEnvironmentAccessNone();

        public abstract Object getEnvironmentAccessInherit();

        public abstract Object getPolyglotAccessNone();

        public abstract Object getPolyglotAccessAll();

        public abstract Object createPolyglotAccess(Set<String> bindingsAccess, Map<String, Set<String>> evalAccess);

        public abstract Object getHostAccessNone();

        /*
         * Note: callValue methods should be refactored. Truffle should not need to call Value APIs.
         * Please do not add more callValue methods to this list.
         */
        public abstract <T> T callValueAs(Object delegateBindings, Class<T> targetType);

        public abstract <T> T callValueAs(Object delegateBindings, Class<T> rawType, Type type);

        public abstract Object callValueGetMetaObject(Object delegateBindings);

        public abstract long callValueGetArraySize(Object keys);

        public abstract Object callValueGetArrayElement(Object keys, int i);

        public abstract boolean callValueIsString(Object arrayElement);

        public abstract String callValueAsString(Object arrayElement);

        public abstract Object getContextEngine(Object context);

        public abstract void contextEnter(Object localContext);

        public abstract void contextLeave(Object localContext);

        public abstract void contextClose(Object localContext, boolean cancelIfClosing);

        public abstract Object contextAsValue(Object localContext, Object hostValue);

        public abstract Class<?> getPolyglotExceptionClass();

        public abstract Object callContextGetCurrent();

        public abstract Object callContextAsValue(Object current, Object classOverrides);

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
        AbstractPolyglotImpl nextImpl = next;
        if (nextImpl != null) {
            nextImpl.setConstructors(constructors);
        }
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

    public final AbstractPolyglotImpl getNextOrNull() {
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
        return io;
    }

    public void initialize() {
    }

    public Object buildEngine(String[] permittedLanguages, SandboxPolicy sandboxPolicy, OutputStream out, OutputStream err, InputStream in, Map<String, String> options,
                    boolean allowExperimentalOptions, boolean boundEngine, MessageTransport messageInterceptor, Object logHandler, Object hostLanguage,
                    boolean hostLanguageOnly, boolean registerInActiveEngines, Object polyglotHostService) {
        return getNext().buildEngine(permittedLanguages, sandboxPolicy, out, err, in, options, allowExperimentalOptions, boundEngine, messageInterceptor, logHandler, hostLanguage,
                        hostLanguageOnly, registerInActiveEngines, polyglotHostService);
    }

    public void onEngineCreated(Object polyglotEngine) {
        getNext().onEngineCreated(polyglotEngine);
    }

    public abstract int getPriority();

    public void preInitializeEngine() {
        getNext().preInitializeEngine();
    }

    public Object createHostLanguage(Object access) {
        return getNext().createHostLanguage(access);
    }

    public void resetPreInitializedEngine() {
        getNext().resetPreInitializedEngine();
    }

    public Object buildSource(String language, Object origin, URI uri, String name, String mimeType, Object content, boolean interactive, boolean internal, boolean cached, Charset encoding, URL url,
                    String path)
                    throws IOException {
        return getNext().buildSource(language, origin, uri, name, mimeType, content, interactive, internal, cached, encoding, url, path);
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

    public Object createHostAccess() {
        return getNext().createHostAccess();
    }

    public boolean isHostFileSystem(FileSystem fileSystem) {
        return getNext().isHostFileSystem(fileSystem);
    }

    public boolean copyResources(Path targetFolder, String... components) throws IOException {
        return getNext().copyResources(targetFolder, components);
    }

    public String getTruffleVersion() {
        return null;
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

        AbstractExecutionListenerDispatch() {
        }

        public abstract void closeExecutionListener(Object impl);

    }

    public abstract static class AbstractExecutionEventDispatch extends AbstractDispatchClass {

        protected AbstractExecutionEventDispatch(AbstractPolyglotImpl polyglotImpl) {
            Objects.requireNonNull(polyglotImpl);
        }

        AbstractExecutionEventDispatch() {
        }

        public abstract List<Object> getExecutionEventInputValues(Object impl);

        public abstract Object getExecutionEventLocation(Object impl);

        public abstract String getExecutionEventRootName(Object impl);

        public abstract Object getExecutionEventReturnValue(Object impl);

        public abstract boolean isExecutionEventExpression(Object impl);

        public abstract boolean isExecutionEventStatement(Object impl);

        public abstract boolean isExecutionEventRoot(Object impl);

        public abstract RuntimeException getExecutionEventException(Object impl);

    }

    public abstract static class AbstractSourceDispatch extends AbstractDispatchClass {

        AbstractSourceDispatch() {
        }

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

        public abstract byte[] getByteArray(Object impl);

        public abstract boolean hasCharacters(Object impl);

        public abstract boolean hasBytes(Object impl);

        public abstract String getMimeType(Object impl);

        public abstract String getLanguage(Object impl);

    }

    public abstract static class AbstractSourceSectionDispatch extends AbstractDispatchClass {

        AbstractSourceSectionDispatch() {
        }

        protected AbstractSourceSectionDispatch(AbstractPolyglotImpl engineImpl) {
            Objects.requireNonNull(engineImpl);
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

        protected AbstractContextDispatch(AbstractPolyglotImpl engineImpl) {
            Objects.requireNonNull(engineImpl);
        }

        AbstractContextDispatch() {
        }

        public abstract boolean initializeLanguage(Object receiver, String languageId);

        public abstract Object eval(Object receiver, String language, Object source);

        public abstract Object parse(Object receiver, String language, Object source);

        public abstract void close(Object receiver, boolean cancelIfExecuting);

        public abstract boolean interrupt(Object receiver, Duration timeout);

        public abstract Object asValue(Object receiver, Object hostValue);

        public abstract void explicitEnter(Object receiver);

        public abstract void explicitLeave(Object receiver);

        public abstract Object getBindings(Object receiver, String language);

        public abstract Object getPolyglotBindings(Object receiver);

        public abstract void resetLimits(Object receiver);

        public abstract void safepoint(Object receiver);

        public abstract void setAPI(Object receiver, Object key);

    }

    public abstract static class AbstractEngineDispatch extends AbstractDispatchClass {

        protected AbstractEngineDispatch(AbstractPolyglotImpl impl) {
            Objects.requireNonNull(impl);
        }

        AbstractEngineDispatch() {
        }

        public abstract void setAPI(Object receiver, Object key);

        public abstract Object requirePublicLanguage(Object receiver, String id);

        public abstract Object requirePublicInstrument(Object receiver, String id);

        // Runtime
        public abstract void close(Object receiver, Object apiObject, boolean cancelIfExecuting);

        public abstract Map<String, Object> getInstruments(Object receiver);

        public abstract Map<String, Object> getLanguages(Object receiver);

        public abstract OptionDescriptors getOptions(Object receiver);

        public abstract Object createContext(Object receiver, SandboxPolicy sandboxPolicy, OutputStream out, OutputStream err, InputStream in,
                        boolean allowHostLookup,
                        Object hostAccess,
                        Object polyglotAccess,
                        boolean allowNativeAccess,
                        boolean allowCreateThread, boolean allowHostClassLoading, boolean allowInnerContextOptions, boolean allowExperimentalOptions,
                        Predicate<String> classFilter,
                        Map<String, String> options,
                        Map<String, String[]> arguments, String[] onlyLanguages, Object ioAccess, Object logHandler, boolean allowCreateProcess, ProcessHandler processHandler,
                        Object environmentAccess, Map<String, String> environment, ZoneId zone, Object limitsImpl, String currentWorkingDirectory, String tmpDir,
                        ClassLoader hostClassLoader, boolean allowValueSharing, boolean useSystemExit);

        public abstract String getImplementationName(Object receiver);

        public abstract Set<Object> getCachedSources(Object receiver);

        public abstract String getVersion(Object receiver);

        public abstract Object attachExecutionListener(Object engine, Consumer<Object> onEnter,
                        Consumer<Object> onReturn,
                        boolean expressions,
                        boolean statements,
                        boolean roots,
                        Predicate<Object> sourceFilter, Predicate<String> rootFilter, boolean collectInputValues, boolean collectReturnValues, boolean collectExceptions);

        public abstract void shutdown(Object engine);

        public abstract RuntimeException hostToGuestException(Object engineReceiver, Throwable throwable);

        public abstract SandboxPolicy getSandboxPolicy(Object engineReceiver);

    }

    public abstract static class AbstractExceptionDispatch extends AbstractDispatchClass {

        protected AbstractExceptionDispatch(AbstractPolyglotImpl engineImpl) {
            Objects.requireNonNull(engineImpl);
        }

        AbstractExceptionDispatch() {
        }

        public abstract boolean isInternalError(Object receiver);

        public abstract boolean isCancelled(Object receiver);

        public abstract boolean isExit(Object receiver);

        public abstract int getExitStatus(Object receiver);

        public abstract Iterable<Object> getPolyglotStackTrace(Object receiver);

        public abstract boolean isSyntaxError(Object receiver);

        public abstract Object getGuestObject(Object receiver);

        public abstract boolean isIncompleteSource(Object receiver);

        public abstract void onCreate(Object receiver, RuntimeException polyglotException);

        public abstract void printStackTrace(Object receiver, PrintStream s);

        public abstract void printStackTrace(Object receiver, PrintWriter s);

        public abstract StackTraceElement[] getStackTrace(Object receiver);

        public abstract String getMessage(Object receiver);

        public abstract boolean isHostException(Object receiver);

        public abstract Throwable asHostException(Object receiver);

        public abstract Object getSourceLocation(Object receiver);

        public abstract boolean isResourceExhausted(Object receiver);

        public abstract boolean isInterrupted(Object receiver);

    }

    public abstract static class AbstractStackFrameImpl extends AbstractDispatchClass {

        AbstractStackFrameImpl() {
        }

        protected AbstractStackFrameImpl(AbstractPolyglotImpl engineImpl) {
            Objects.requireNonNull(engineImpl);
        }

        public abstract StackTraceElement toHostFrame();

        public abstract Object getSourceLocation();

        public abstract int getBytecodeIndex();

        public abstract String getRootName();

        public abstract Object getLanguage();

        public abstract boolean isHostFrame();

        public abstract String toStringImpl(int languageColumn);

    }

    public abstract static class AbstractInstrumentDispatch extends AbstractDispatchClass {

        AbstractInstrumentDispatch() {
        }

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

        AbstractLanguageDispatch() {
        }

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

        public abstract Object toByteSequence(Object internalContext, Object guestValue);

        public abstract <K, V> Map<K, V> toMap(Object internalContext, Object foreignObject, boolean implementsFunction, Class<K> keyClass, Type keyType, Class<V> valueClass, Type valueType);

        public abstract <K, V> Map.Entry<K, V> toMapEntry(Object internalContext, Object foreignObject, boolean implementsFunction,
                        Class<K> keyClass, Type keyType, Class<V> valueClass, Type valueType);

        public abstract <T> Function<?, ?> toFunction(Object internalContext, Object function, Class<?> returnClass, Type returnType, Class<?> paramClass, Type paramType);

        public abstract Object toObjectProxy(Object internalContext, Class<?> clazz, Type genericType, Object obj) throws IllegalArgumentException;

        public abstract <T> T toFunctionProxy(Object internalContext, Class<T> functionalType, Type genericType, Object function);

        public abstract <T> Iterable<T> toIterable(Object internalContext, Object iterable, boolean implementFunction, Class<T> elementClass, Type elementType);

        public abstract <T> Iterator<T> toIterator(Object internalContext, Object iterable, boolean implementFunction, Class<T> elementClass, Type elementType);

        public abstract RuntimeException toPolyglotException(Object internalContext, Throwable e);

        public abstract Object toValue(Object internalContext, Object receiver);

        public abstract String getValueInfo(Object internalContext, Object value);

        public abstract Object[] toValues(Object internalContext, Object[] values, int startIndex);

        public abstract Object[] toValues(Object internalContext, Object[] values);

        public abstract void rethrowPolyglotException(Object internalContext, RuntimeException polyglotException);

        public abstract RuntimeException toEngineException(RuntimeException e);

        public abstract boolean isEngineException(RuntimeException e);

        public abstract RuntimeException unboxEngineException(RuntimeException e);

        public abstract Class<?> getValueClass();

        public abstract Class<?> getPoylglotExceptionClass();

        public abstract boolean isPolyglotException(RuntimeException e);
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

        public abstract void initializeHostContext(Object internalContext, Object context, Object hostAccess, ClassLoader cl, Predicate<String> clFilter, boolean hostCLAllowed,
                        boolean hostLookupAllowed);

        public abstract void throwHostLanguageException(String message);

        public abstract void addToHostClassPath(Object context, Object truffleFile);

        public abstract Object toGuestValue(Object context, Object hostValue, boolean asValue);

        public abstract Object asHostDynamicClass(Object context, Class<?> value);

        public abstract Object asHostStaticClass(Object context, Class<?> value);

        public abstract Object findDynamicClass(Object context, String classValue);

        public abstract Object findStaticClass(Object context, String classValue);

        public abstract <T> T toHostType(Object hostNode, Object targetNode, Object hostContext, Object value, Class<T> targetType, Type genericType);

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

        public abstract boolean allowsPublicAccess();

        public final boolean isHostStackTraceVisibleToGuest() {
            return allowsPublicAccess();
        }

    }

    public abstract static class AbstractValueDispatch extends AbstractDispatchClass {

        AbstractValueDispatch() {
        }

        protected AbstractValueDispatch(AbstractPolyglotImpl engineImpl) {
            Objects.requireNonNull(engineImpl);
        }

        public boolean hasArrayElements(Object context, Object receiver) {
            return false;
        }

        public abstract Object getArrayElement(Object context, Object receiver, long index);

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

        public abstract void readBuffer(Object context, Object receiver, long byteOffset, byte[] destination, int destinationOffset, int length);

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

        public abstract Object getMember(Object context, Object receiver, String key);

        public boolean hasMember(Object context, Object receiver, String key) {
            return false;
        }

        public Object getContext(Object context) {
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

        public abstract Object execute(Object context, Object receiver, Object[] arguments);

        public abstract Object execute(Object context, Object receiver);

        public boolean canInstantiate(Object context, Object receiver) {
            return false;
        }

        public abstract Object newInstance(Object context, Object receiver, Object[] arguments);

        public abstract void executeVoid(Object context, Object receiver, Object[] arguments);

        public abstract void executeVoid(Object context, Object receiver);

        public boolean canInvoke(Object context, String identifier, Object receiver) {
            return false;
        }

        public abstract Object invoke(Object context, Object receiver, String identifier, Object[] arguments);

        public abstract Object invoke(Object context, Object receiver, String identifier);

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

        public boolean fitsInBigInteger(Object context, Object receiver) {
            return false;
        }

        public abstract BigInteger asBigInteger(Object context, Object receiver);

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

        public abstract Object getMetaObject(Object context, Object receiver);

        public boolean isNumber(Object context, Object receiver) {
            return false;
        }

        public abstract <T> T asClass(Object context, Object receiver, Class<T> targetType);

        public abstract <T> T asTypeLiteral(Object context, Object receiver, Class<T> rawType, Type genericType);

        public abstract Object getSourceLocation(Object context, Object receiver);

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

        public abstract Object getMetaParents(Object context, Object receiver);

        public abstract boolean equalsImpl(Object context, Object receiver, Object obj);

        public abstract int hashCodeImpl(Object context, Object receiver);

        public boolean hasIterator(Object context, Object receiver) {
            return false;
        }

        public abstract Object getIterator(Object context, Object receiver);

        public boolean isIterator(Object context, Object receiver) {
            return false;
        }

        public abstract boolean hasIteratorNextElement(Object context, Object receiver);

        public abstract Object getIteratorNextElement(Object context, Object receiver);

        public boolean hasHashEntries(Object context, Object receiver) {
            return false;
        }

        public abstract long getHashSize(Object context, Object receiver);

        public boolean hasHashEntry(Object context, Object receiver, Object key) {
            return false;
        }

        public abstract Object getHashValue(Object context, Object receiver, Object key);

        public abstract Object getHashValueOrDefault(Object context, Object receiver, Object key, Object defaultValue);

        public abstract void putHashEntry(Object context, Object receiver, Object key, Object value);

        public abstract boolean removeHashEntry(Object context, Object receiver, Object key);

        public abstract Object getHashEntriesIterator(Object context, Object receiver);

        public abstract Object getHashKeysIterator(Object context, Object receiver);

        public abstract Object getHashValuesIterator(Object context, Object receiver);

        public abstract void pin(Object languageContext, Object receiver);
    }

    public Class<?> loadLanguageClass(String className) {
        return getNext().loadLanguageClass(className);
    }

    public Object getCurrentContext() {
        return getNext().getCurrentContext();
    }

    public Object asValue(Object o) {
        return getNext().asValue(o);
    }

    public <S, T> Object newTargetTypeMapping(Class<S> sourceType, Class<T> targetType, Predicate<S> acceptsValue, Function<S, T> convertValue, TargetMappingPrecedence precedence) {
        return getNext().newTargetTypeMapping(sourceType, targetType, acceptsValue, convertValue, precedence);
    }

    public Object buildLimits(long statementLimit, Predicate<Object> statementLimitSourceFilter, Consumer<Object> onLimit) {
        return getNext().buildLimits(statementLimit, statementLimitSourceFilter, onLimit);
    }

    public FileSystem newDefaultFileSystem(String hostTmpDir) {
        return getNext().newDefaultFileSystem(hostTmpDir);
    }

    public FileSystem allowInternalResourceAccess(FileSystem fileSystem) {
        return getNext().allowInternalResourceAccess(fileSystem);
    }

    public FileSystem newReadOnlyFileSystem(FileSystem fileSystem) {
        return getNext().newReadOnlyFileSystem(fileSystem);
    }

    public FileSystem newNIOFileSystem(java.nio.file.FileSystem fileSystem) {
        return getNext().newNIOFileSystem(fileSystem);
    }

    public ByteSequence asByteSequence(Object object) {
        return getNext().asByteSequence(object);
    }

    public ProcessHandler newDefaultProcessHandler() {
        return getNext().newDefaultProcessHandler();
    }

    public Object newIOAccess(String name, boolean allowHostFileAccess, boolean allowHostSocketAccess, FileSystem customFileSystem) {
        return getNext().newIOAccess(name, allowHostFileAccess, allowHostSocketAccess, customFileSystem);
    }

    public boolean isDefaultProcessHandler(ProcessHandler processHandler) {
        return getNext().isDefaultProcessHandler(processHandler);
    }

    public boolean isInternalFileSystem(FileSystem fileSystem) {
        return getNext().isInternalFileSystem(fileSystem);
    }

    public ThreadScope createThreadScope() {
        return getNext().createThreadScope();
    }

    public boolean isInCurrentEngineHostCallback(Object engine) {
        return getNext().isInCurrentEngineHostCallback(engine);
    }

    public Object newLogHandler(Object logHandlerOrStream) {
        return getNext().newLogHandler(logHandlerOrStream);
    }

    public OptionDescriptors createUnionOptionDescriptors(OptionDescriptors... optionDescriptors) {
        return getNext().createUnionOptionDescriptors(optionDescriptors);
    }

    public Object newFileSystem(FileSystem fs) {
        return getNext().newFileSystem(fs);
    }

    public void validateVirtualThreadCreation(OptionValues engineOptions) {
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
            union = createUnionOptionDescriptors(current.createEngineOptionDescriptors(), union);
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

        ThreadScope() {
        }

        @Override
        public abstract void close();
    }

    public abstract static class LogHandler {

        public abstract void publish(LogRecord logRecord);

        public abstract void flush();

        public abstract void close();

    }

}
