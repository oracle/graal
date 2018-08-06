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
package com.oracle.truffle.polyglot;

import java.util.concurrent.atomic.AtomicInteger;

final class PolyglotThread extends Thread {

    private final PolyglotLanguageContext languageContext;

    Object context;

    PolyglotThread(PolyglotLanguageContext languageContext, Runnable runnable) {
        super(runnable, createDefaultName(languageContext));
        this.languageContext = languageContext;
        setUncaughtExceptionHandler(languageContext.getPolyglotExceptionHandler());
    }

    private static String createDefaultName(PolyglotLanguageContext creator) {
        return "Polyglot-" + creator.language.getId() + "-" + THREAD_INIT_NUMBER.getAndIncrement();
    }

    boolean isOwner(PolyglotContextImpl testContext) {
        return languageContext.context == testContext;
    }

    @Override
    public void run() {
        Object prev = languageContext.enterThread(this);
        assert prev == null;
        try {
            super.run();
        } finally {
            languageContext.leaveThread(prev, this);
        }
    }

    private static final AtomicInteger THREAD_INIT_NUMBER = new AtomicInteger(0);

}
