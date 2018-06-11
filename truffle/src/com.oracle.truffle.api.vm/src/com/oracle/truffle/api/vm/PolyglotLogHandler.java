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
            handler.publish(record);
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

    static LogRecord createLogRecord(final Level level, String loggerName, final String message, final String className, final String methodName, final Object[] parameters, final Throwable thrown) {
        return new ImmutableLogRecord(level, loggerName, message, className, methodName, parameters, thrown);
    }

    private static final class ImmutableLogRecord extends LogRecord {

        private static final long serialVersionUID = 1L;

        private ImmutableLogRecord(final Level level, final String loggerName, final String message, final String className, final String methodName, final Object[] parameters,
                        final Throwable thrown) {
            super(level, message);
            super.setLoggerName(loggerName);
            if (className != null) {
                super.setSourceClassName(className);
            }
            if (methodName != null) {
                super.setSourceMethodName(methodName);
            }
            Object[] copy = null;
            if (parameters != null) {
                copy = new Object[parameters.length];
                final PolyglotContextImpl[] contextHolder = new PolyglotContextImpl[1];
                for (int i = 0; i < parameters.length; i++) {
                    copy[i] = safeValue(parameters[i], contextHolder);
                }
            }
            super.setParameters(copy);
            super.setThrown(thrown);
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
