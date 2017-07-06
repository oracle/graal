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
package org.graalvm.polyglot.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.PolyglotException.StackFrame;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.Value;

@SuppressWarnings("unused")
public abstract class AbstractPolyglotImpl {

    protected AbstractPolyglotImpl() {
        if (!getClass().getName().equals("com.oracle.truffle.api.vm.PolyglotImpl")) {
            throw new AssertionError("Only one implementation Engine.Impl allowed.");
        }
    }

    public abstract static class APIAccess {

        protected APIAccess() {
            if (!getClass().getCanonicalName().equals("org.graalvm.polyglot.Engine.APIAccessImpl")) {
                throw new AssertionError("Only one implementation of APIAccess allowed. " + getClass().getCanonicalName());
            }
        }

        public abstract Engine newEngine(AbstractEngineImpl impl);

        public abstract Context newContext(AbstractContextImpl impl, Language languageImpl);

        @SuppressWarnings("deprecation")
        public abstract org.graalvm.polyglot.PolyglotContext newPolyglotContext(Engine engine, AbstractContextImpl impl);

        public abstract PolyglotException newLanguageException(String message, AbstractExceptionImpl impl);

        public abstract Language newLanguage(AbstractLanguageImpl impl);

        public abstract Instrument newInstrument(AbstractInstrumentImpl impl);

        public abstract Value newValue(Object value, AbstractValueImpl impl);

        public abstract Source newSource(Object impl);

        public abstract SourceSection newSourceSection(Source source, Object impl);

        public abstract Object getReceiver(Value value);

        public abstract AbstractValueImpl getImpl(Value value);

        public abstract AbstractStackFrameImpl getImpl(StackFrame value);

        public abstract AbstractLanguageImpl getImpl(Language value);

        public abstract AbstractInstrumentImpl getImpl(Instrument value);

        public abstract StackFrame newPolyglotStackTraceElement(AbstractStackFrameImpl impl);

    }

    // shared SPI

    APIAccess api;

    public final void setConstructors(APIAccess constructors) {
        this.api = constructors;
    }

    public APIAccess getAPIAccess() {
        return api;
    }

    public abstract Engine buildEngine(OutputStream out, OutputStream err, InputStream in, Map<String, String> arguments, long timeout, TimeUnit timeoutUnit, boolean sandbox,
                    long maximumAllowedAllocationBytes, boolean useSystemProperties);

    public abstract AbstractSourceImpl getSourceImpl();

    public abstract AbstractSourceSectionImpl getSourceSectionImpl();

    public abstract static class AbstractSourceImpl {

        protected final AbstractPolyglotImpl engineImpl;

        protected AbstractSourceImpl(AbstractPolyglotImpl engineImpl) {
            Objects.requireNonNull(engineImpl);
            this.engineImpl = engineImpl;
        }

        public abstract Source build(Object origin, URI uri, String name, String content, boolean interactive, boolean internal) throws IOException;

        public abstract String getName(Object impl);

        public abstract String getPath(Object impl);

        public abstract boolean isInteractive(Object impl);

        public abstract URL getURL(Object impl);

        public abstract URI getURI(Object impl);

        public abstract Reader getReader(Object impl);

        public abstract InputStream getInputStream(Object impl);

        public abstract int getLength(Object impl);

        public abstract String getCode(Object impl);

        public abstract String getCode(Object impl, int lineNumber);

        public abstract int getLineCount(Object impl);

        public abstract int getLineNumber(Object impl, int offset);

        public abstract int getColumnNumber(Object impl, int offset);

        public abstract int getLineStartOffset(Object impl, int lineNumber);

        public abstract int getLineLength(Object impl, int lineNumber);

        public abstract String toString(Object impl);

        public abstract int hashCode(Object impl);

        public abstract boolean equals(Object impl, Object otherImpl);

        public abstract boolean isInternal(Object impl);
    }

    public abstract static class AbstractSourceSectionImpl {

        protected AbstractSourceSectionImpl(AbstractPolyglotImpl polyglotImpl) {
            Objects.requireNonNull(polyglotImpl);
        }

        public abstract boolean isAvailable(Object impl);

        public abstract int getStartLine(Object impl);

        public abstract int getStartColumn(Object impl);

        public abstract int getEndLine(Object impl);

        public abstract int getEndColumn(Object impl);

        public abstract int getCharIndex(Object impl);

        public abstract int getCharLength(Object impl);

        public abstract int getCharEndIndex(Object impl);

        public abstract String getCode(Object impl);

        public abstract String toString(Object impl);

        public abstract int hashCode(Object impl);

        public abstract boolean equals(Object impl, Object obj);

    }

    public abstract static class AbstractContextImpl {

        protected AbstractContextImpl(AbstractPolyglotImpl impl) {
            Objects.requireNonNull(impl);
        }

        public abstract Value lookup(Object languageImpl, String key);

        public abstract Value importSymbol(String key);

        public abstract void exportSymbol(String key, Object value);

        public abstract boolean initializeLanguage(AbstractLanguageImpl languageImpl);

        public abstract Value eval(Object languageImpl, Object sourceImpl);

        public abstract Engine getEngineImpl();

        public abstract void close(boolean interuptExecution);

    }

    public abstract static class AbstractEngineImpl {

        protected AbstractEngineImpl(AbstractPolyglotImpl impl) {
            Objects.requireNonNull(impl);
        }

        public abstract Language getLanguage(String id);

        public abstract Instrument getInstrument(String id);

        // Runtime

        public abstract void ensureClosed(boolean cancelIfExecuting, boolean ignoreCloseFailure);

        public abstract Map<String, Instrument> getInstruments();

        public abstract Map<String, Language> getLanguages();

        public abstract String getVersion();

        @SuppressWarnings("deprecation")
        public abstract org.graalvm.polyglot.PolyglotContext createPolyglotContext(OutputStream out, OutputStream err, InputStream in, Map<String, String[]> arguments, Map<String, String> options);

        public abstract Language detectLanguage(Object sourceImpls);

        public abstract OptionDescriptors getOptions();

        public abstract Context createContext(OutputStream out, OutputStream err, InputStream in, Map<String, String> options, Map<String, String[]> arguments, Language primaryLanguage,
                        AbstractLanguageImpl onlyLanguage);

    }

    public abstract static class AbstractExceptionImpl {

        protected AbstractExceptionImpl(AbstractPolyglotImpl engineImpl) {
            Objects.requireNonNull(engineImpl);
        }

        public abstract boolean isInternalError();

        public abstract boolean isCancelled();

        public abstract boolean isExit();

        public abstract int getExitStatus();

        public abstract Iterable<StackFrame> getPolyglotStackTrace();

        public abstract boolean isSyntaxError();

        public abstract Value getGuestObject();

        public abstract boolean isIncompleteSource();

        public abstract void onCreate(PolyglotException api);

        public abstract void printStackTrace(PrintStream s);

        public abstract void printStackTrace(PrintWriter s);

        public abstract StackTraceElement[] getStackTrace();

        public abstract String getMessage();

        public abstract boolean isHostException();

        public abstract Throwable asHostException();

        public abstract SourceSection getSourceLocation();

    }

    public abstract static class AbstractStackFrameImpl {

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

    public abstract static class AbstractInstrumentImpl {

        protected AbstractInstrumentImpl(AbstractPolyglotImpl engineImpl) {
            Objects.requireNonNull(engineImpl);
        }

        public abstract String getId();

        public abstract String getName();

        public abstract OptionDescriptors getOptions();

        public abstract String getVersion();

        public abstract <T> T lookup(Class<T> type);

    }

    public abstract static class AbstractLanguageImpl {

        protected AbstractLanguageImpl(AbstractPolyglotImpl engineImpl) {
            Objects.requireNonNull(engineImpl);
        }

        public abstract String getName();

        public abstract String getImplementationName();

        public abstract boolean isInteractive();

        public abstract String getVersion();

        public abstract String getId();

        public abstract OptionDescriptors getOptions();

        public abstract Engine getEngineAPI();

        public abstract boolean isHost();

    }

    public abstract static class AbstractValueImpl {

        protected AbstractValueImpl(AbstractPolyglotImpl impl) {
            Objects.requireNonNull(impl);
        }

        public boolean hasArrayElements(Object receiver) {
            return false;
        }

        public Value getArrayElement(Object receiver, long index) {
            throw unsupported(receiver, "getArrayElement(long)", "hasArrayElements()");
        }

        public void setArrayElement(Object receiver, long index, Object value) {
            throw unsupported(receiver, "setArrayElement(long, Object)", "hasArrayElements()");
        }

        public long getArraySize(Object receiver) {
            throw unsupported(receiver, "getArraySize()", "hasArrayElements()");
        }

        public boolean hasMembers(Object receiver) {
            return false;
        }

        public Value getMember(Object receiver, String key) {
            throw unsupported(receiver, "getMember(String)", "hasMembers()");
        }

        public boolean hasMember(Object receiver, String key) {
            return false;
        }

        public Set<String> getMemberKeys(Object receiver) {
            throw unsupported(receiver, "getMemberKeys()", "hasMembers()");
        }

        public void putMember(Object receiver, String key, Object member) {
            throw unsupported(receiver, "putMember(String, Object)", "hasMembers()");
        }

        public boolean canExecute(Object receiver) {
            return false;
        }

        public Value execute(Object receiver, Object[] arguments) {
            throw unsupported(receiver, "execute(Object...)", "canExecute()");
        }

        public boolean isString(Object receiver) {
            return false;
        }

        public String asString(Object receiver) {
            throw unsupported(receiver, "asString()", "isString()");
        }

        public boolean isBoolean(Object receiver) {
            return false;
        }

        public boolean asBoolean(Object receiver) {
            throw unsupported(receiver, "asBoolean()", "isBoolean()");
        }

        public boolean fitsInInt(Object receiver) {
            return false;
        }

        public int asInt(Object receiver) {
            throw unsupported(receiver, "asInt()", "isNumber()");
        }

        public boolean fitsInLong(Object receiver) {
            return false;
        }

        public long asLong(Object receiver) {
            throw unsupported(receiver, "asLong()", "isNumber()");
        }

        public boolean fitsInDouble(Object receiver) {
            return false;
        }

        public double asDouble(Object receiver) {
            throw unsupported(receiver, "asDouble()", "isNumber()");
        }

        public boolean fitsInFloat(Object receiver) {
            return false;
        }

        public float asFloat(Object receiver) {
            throw unsupported(receiver, "asFloat()", "isNumber()");
        }

        public boolean isNull(Object receiver) {
            return false;
        }

        public boolean isNativePointer(Object receiver) {
            return false;
        }

        public long asNativePointer(Object receiver) {
            throw unsupported(receiver, "asNativePointer()", "isNativeObject()");
        }

        public boolean isHostObject(Object receiver) {
            return false;
        }

        public Object asHostObject(Object receiver) {
            throw unsupported(receiver, "asHostObject()", "isHostObject()");
        }

        protected abstract RuntimeException unsupported(Object receiver, String message, String useToCheck);

        public abstract String toString(Object receiver);

        public abstract Value getMetaObject(Object receiver);

        public boolean fitsInByte(Object receiver) {
            return false;
        }

        public byte asByte(Object receiver) {
            throw unsupported(receiver, "asByte()", "isNumber()");
        }

        public boolean isNumber(Object receiver) {
            return false;
        }

    }

    public abstract Class<?> loadLanguageClass(String className);

}
