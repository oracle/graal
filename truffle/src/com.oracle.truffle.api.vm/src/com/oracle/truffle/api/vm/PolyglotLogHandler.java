/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ObjectStreamException;
import java.util.Arrays;
import java.util.ResourceBundle;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

final class PolyglotLogHandler extends Handler {

    static final Handler INSTANCE = new PolyglotLogHandler();

    private PolyglotLogHandler() {
    }

    @Override
    public void publish(final LogRecord record) {
        final Handler handler = findDelegate();
        if (handler != null) {
            handler.publish(new ImmutableLogRecord(record));
        }
    }

    @Override
    public void flush() {
        final Handler handler = findDelegate();
        if (handler != null) {
            handler.flush();
        }
    }

    @Override
    public void close() throws SecurityException {
        final Handler handler = findDelegate();
        if (handler != null) {
            handler.close();
        }
    }

    private Handler findDelegate() {
        Handler result = null;
        final PolyglotContextImpl currentContext = PolyglotContextImpl.current();
        if (currentContext != null) {
            result = currentContext.logHandler;
        }
        return result;
    }

    private static final class ImmutableLogRecord extends LogRecord {

        private static final long serialVersionUID = 1L;

        private final LogRecord delegate;

        private ImmutableLogRecord(final LogRecord delegate) {
            super(delegate.getLevel(), delegate.getMessage());
            this.delegate = delegate;
            final Object[] params = delegate.getParameters();
            Object[] copy = null;
            if (params != null) {
                copy = new Object[params.length];
                final PolyglotContextImpl[] contextHolder = new PolyglotContextImpl[1];
                for (int i = 0; i < params.length; i++) {
                    copy[i] = safeValue(params[i], contextHolder);
                }
            }
            super.setParameters(copy);
        }

        @Override
        public String getLoggerName() {
            return delegate.getLoggerName();
        }

        @Override
        public ResourceBundle getResourceBundle() {
            return delegate.getResourceBundle();
        }

        @Override
        public String getResourceBundleName() {
            return delegate.getResourceBundleName();
        }

        @Override
        public long getSequenceNumber() {
            return delegate.getSequenceNumber();
        }

        @Override
        public String getSourceClassName() {
            return delegate.getSourceClassName();
        }

        @Override
        public String getSourceMethodName() {
            return delegate.getSourceMethodName();
        }

        @Override
        public Object[] getParameters() {
            final Object[] params = super.getParameters();
            return params == null ? null : Arrays.copyOf(params, params.length);
        }

        @Override
        public int getThreadID() {
            return delegate.getThreadID();
        }

        @Override
        public long getMillis() {
            return delegate.getMillis();
        }

        @Override
        public Throwable getThrown() {
            return delegate.getThrown();
        }

        @Override
        public void setLevel(Level level) {
            throw new UnsupportedOperationException("Setting Level is not supported.");
        }

        @Override
        public void setLoggerName(String name) {
            throw new UnsupportedOperationException("Setting Logger Name is not supported.");
        }

        @Override
        public void setMessage(String message) {
            throw new UnsupportedOperationException("Setting Messag is not supported.");
        }

        @Override
        public void setMillis(long millis) {
            throw new UnsupportedOperationException("Setting Millis is not supported.");
        }

        @Override
        public void setParameters(Object[] parameters) {
            throw new UnsupportedOperationException("Setting Parameters is not supported.");
        }

        @Override
        public void setResourceBundle(ResourceBundle bundle) {
            throw new UnsupportedOperationException("Setting Resource Bundle is not supported.");
        }

        @Override
        public void setResourceBundleName(String name) {
            throw new UnsupportedOperationException("Setting Resource Bundle Name is not supported.");
        }

        @Override
        public void setSequenceNumber(long seq) {
            throw new UnsupportedOperationException("Setting Sequence Number is not supported.");
        }

        @Override
        public void setSourceClassName(String sourceClassName) {
            throw new UnsupportedOperationException("Setting Parameters is not supported.");
        }

        @Override
        public void setSourceMethodName(String sourceMethodName) {
            throw new UnsupportedOperationException("Setting Source Method Name is not supported.");
        }

        @Override
        public void setThreadID(int threadID) {
            throw new UnsupportedOperationException("Setting Thread ID is not supported.");
        }

        @Override
        public void setThrown(Throwable thrown) {
            throw new UnsupportedOperationException("Setting Throwable is not supported.");
        }

        private Object writeReplace() throws ObjectStreamException {
            final LogRecord serializableForm = new LogRecord(getLevel(), getMessage());
            serializableForm.setLoggerName(delegate.getLoggerName());
            serializableForm.setMillis(delegate.getMillis());
            serializableForm.setParameters(super.getParameters());
            serializableForm.setResourceBundle(delegate.getResourceBundle());
            serializableForm.setResourceBundleName(delegate.getResourceBundleName());
            serializableForm.setSequenceNumber(delegate.getSequenceNumber());
            serializableForm.setSourceClassName(delegate.getSourceClassName());
            serializableForm.setSourceMethodName(delegate.getSourceMethodName());
            serializableForm.setThreadID(delegate.getThreadID());
            serializableForm.setThrown(delegate.getThrown());
            return serializableForm;
        }

        @SuppressWarnings("deprecation")
        private static Object safeValue(final Object param, PolyglotContextImpl[] contextHolder) {
            if (param == null || PolyglotImpl.EngineImpl.isPrimitive(param)) {
                return param;
            }
            if (contextHolder[0] == null) {
                contextHolder[0] = PolyglotContextImpl.current();
            }
            final PolyglotLanguage resolvedLanguage = PolyglotImpl.EngineImpl.findObjectLanguage(contextHolder[0], null, param);
            final PolyglotLanguageContext displayLanguageContext;
            if (resolvedLanguage != null) {
                displayLanguageContext = contextHolder[0].contexts[resolvedLanguage.index];
            } else {
                displayLanguageContext = contextHolder[0].getHostContext();
            }
            return VMAccessor.LANGUAGE.toStringIfVisible(displayLanguageContext.env, param, false);
        }
    }
}
