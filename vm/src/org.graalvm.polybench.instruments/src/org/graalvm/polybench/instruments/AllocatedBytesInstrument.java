/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polybench.instruments;

import java.lang.management.ManagementFactory;
import java.util.HashSet;
import java.util.Set;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.ThreadsListener;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.sun.management.ThreadMXBean;

@TruffleInstrument.Registration(id = AllocatedBytesInstrument.ID, name = "Polybench Allocated Bytes Instrument")
public final class AllocatedBytesInstrument extends TruffleInstrument {

    public static final String ID = "allocated-bytes";
    @Option(name = "", help = "Enable the Allocated Bytes Instrument (default: false).", category = OptionCategory.EXPERT) static final OptionKey<Boolean> enabled = new OptionKey<>(false);
    private static final ThreadMXBean threadBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    private final Set<Thread> threads = new HashSet<>();
    private static final String GET_ALLOCATED_BYTES = "getAllocatedBytes";

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new AllocatedBytesInstrumentOptionDescriptors();
    }

    @CompilerDirectives.TruffleBoundary
    public synchronized double getAllocated() {
        double report = 0;
        for (Thread thread : threads) {
            report = report + threadBean.getThreadAllocatedBytes(thread.getId());
        }
        return report;
    }

    @Override
    protected synchronized void onCreate(Env env) {
        env.getInstrumenter().attachContextsListener(new ContextsListener() {
            @Override
            public void onContextCreated(TruffleContext c) {
            }

            @Override
            public void onLanguageContextCreated(TruffleContext context, LanguageInfo language) {
            }

            @Override
            public void onLanguageContextInitialized(TruffleContext context, LanguageInfo language) {
                try {
                    InteropLibrary interopLibrary = InteropLibrary.getUncached();
                    Object polyglotBindings = env.getPolyglotBindings();
                    if (!interopLibrary.isMemberExisting(polyglotBindings, GET_ALLOCATED_BYTES)) {
                        interopLibrary.writeMember(polyglotBindings, GET_ALLOCATED_BYTES, new GetAllocatedBytesFunction());
                    }
                } catch (UnsupportedMessageException | UnknownIdentifierException | UnsupportedTypeException e) {
                    throw CompilerDirectives.shouldNotReachHere("Exception during interop.");
                }
            }

            @Override
            public void onLanguageContextFinalized(TruffleContext context, LanguageInfo language) {

            }

            @Override
            public void onLanguageContextDisposed(TruffleContext context, LanguageInfo language) {

            }

            @Override
            public void onContextClosed(TruffleContext context) {

            }
        }, true);
        env.getInstrumenter().attachThreadsListener(new ThreadsListener() {
            @Override
            public void onThreadInitialized(TruffleContext context, Thread thread) {
                synchronized (AllocatedBytesInstrument.this) {
                    threads.add(thread);
                }
            }

            @Override
            public void onThreadDisposed(TruffleContext context, Thread thread) {
                synchronized (AllocatedBytesInstrument.this) {
                    threads.remove(thread);
                }
            }
        }, true);
    }

    @ExportLibrary(InteropLibrary.class)
    public class GetAllocatedBytesFunction implements TruffleObject {

        @ExportMessage
        final boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object... args) throws ArityException {
            if (args.length != 0) {
                throw ArityException.create(0, 0, args.length);
            }
            return getAllocated();
        }
    }
}
