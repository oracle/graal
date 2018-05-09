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
package com.oracle.truffle.api;

import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public final class TruffleLogger extends Logger {
    private final String languageId;

    private TruffleLogger(final String languageId, final String loggerName, final String resourceBundleName) {
        super(loggerName, resourceBundleName);
        this.languageId = languageId;
    }

    public static Logger getLogger(final String languageId, final Class<?> forClass) {
        return getLogger(languageId, forClass.getName(), null);
    }

    public static Logger getLogger(final String languageId, final String loggerName) {
        return getLogger(languageId, loggerName, null);
    }

    public static Logger getLogger(final String languageId, final String loggerName, final String resourceBundleName) {
        final LogManager logManager = LogManager.getLogManager();
        Logger found = logManager.getLogger(loggerName);
        if (found == null) {
            for (final TruffleLogger logger = new TruffleLogger(languageId, loggerName, resourceBundleName); found == null;) {
                if (logManager.addLogger(logger)) {
                    logger.addHandlerInternal(TruffleLanguage.AccessAPI.engineAccess().getPolyglotLogHandler());
                    found = logger;
                    break;
                }
                found = logManager.getLogger(loggerName);
            }
        }
        return verifyLoggerInstance(found, languageId);
    }

    @Override
    public void addHandler(Handler handler) {
        if (handler != TruffleLanguage.AccessAPI.engineAccess().getPolyglotLogHandler()) {
            super.addHandler(handler);
        }
    }

    @Override
    public void removeHandler(Handler handler) {
        if (handler != TruffleLanguage.AccessAPI.engineAccess().getPolyglotLogHandler()) {
            super.removeHandler(handler);
        }
    }

    private void addHandlerInternal(final Handler handler) {
        super.addHandler(handler);
    }

    private static Logger verifyLoggerInstance(final Logger logger, final String languageId) {
        if (!(logger instanceof TruffleLogger)) {
            throw new IllegalStateException(String.format(
                            "Language %s is using non TruffleLogger: %s",
                            languageId,
                            logger.getName()));
        }
        return logger;
    }
}
