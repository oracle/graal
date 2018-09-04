/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import java.util.ResourceBundle;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.oracle.truffle.api.interop.TruffleObject;

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

    private static Handler findDelegate() {
        final PolyglotContextImpl currentContext = getCurrentOuterContext();
        return currentContext != null ? currentContext.config.logHandler : null;
    }

    static PolyglotContextImpl getCurrentOuterContext() {
        PolyglotContextImpl currentContext = PolyglotContextImpl.current();
        if (currentContext != null) {
            while (currentContext.parent != null) {
                currentContext = currentContext.parent;
            }
        }
        return currentContext;
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
            Object[] copy = parameters;
            if (parameters != null && parameters.length > 0) {
                copy = new Object[parameters.length];
                final PolyglotContextImpl context = PolyglotContextImpl.current();
                for (int i = 0; i < parameters.length; i++) {
                    copy[i] = safeValue(parameters[i], context);
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

        @SuppressWarnings("deprecation")
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

        private static Object safeValue(final Object param, final PolyglotContextImpl context) {
            if (param == null || PolyglotImpl.EngineImpl.isPrimitive(param)) {
                return param;
            }
            if (param instanceof TruffleObject) {
                final PolyglotLanguage resolvedLanguage = PolyglotImpl.EngineImpl.findObjectLanguage(context, null, param);
                final PolyglotLanguageContext displayLanguageContext;
                if (resolvedLanguage != null) {
                    displayLanguageContext = context.contexts[resolvedLanguage.index];
                } else {
                    displayLanguageContext = context.getHostContext();
                }
                return VMAccessor.LANGUAGE.toStringIfVisible(displayLanguageContext.env, param, false);
            }
            return param.toString();
        }
    }
}
