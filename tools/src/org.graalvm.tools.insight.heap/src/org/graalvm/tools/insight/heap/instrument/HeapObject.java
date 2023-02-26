/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.insight.heap.instrument;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.graalvm.tools.insight.Insight.SymbolProvider;
import org.graalvm.tools.insight.heap.HeapDump;

@SuppressWarnings({"static-method", "unused"})
@ExportLibrary(InteropLibrary.class)
final class HeapObject implements TruffleObject, SymbolProvider, Consumer<OutputStream> {
    private final TruffleInstrument.Env env;
    private final String path;
    /* @GuardedBy(this) */
    private OutputStream sink;
    /* @GuardedBy(this) */
    private HeapDump.Builder generator;
    private final MemoryDump memoryDump;
    private final boolean exposeCache;

    private static final String CACHE_CLEAR = "cacheClear";
    private static final String CACHE = "cache"; // A read-only heap cache
    private static final String DUMP = "dump";
    private static final String FLUSH = "flush";

    HeapObject(TruffleInstrument.Env env, String path, int cacheSize, CacheReplacement cacheReplacement, boolean exposeCache) {
        this.env = env;
        this.path = path;
        if (cacheSize != 0) {
            memoryDump = new MemoryDump(cacheSize, cacheReplacement, new Supplier<HeapDump.Builder>() {
                @Override
                public HeapDump.Builder get() {
                    try {
                        return getGenerator();
                    } catch (IOException ex) {
                        throw new HeapException(ex);
                    }
                }
            });
        } else {
            memoryDump = null;
        }
        this.exposeCache = exposeCache;
    }

    @Override
    public synchronized Map<String, ? extends Object> symbolsWithValues() throws Exception {
        if (path == null && getSink() == null) {
            return Collections.emptyMap();
        } else {
            return Collections.singletonMap("heap", this);
        }
    }

    @TruffleBoundary
    @ExportMessage
    @SuppressWarnings("fallthrough")
    Object invokeMember(String name, Object[] args) throws UnknownIdentifierException, UnsupportedTypeException, ArityException, UnsupportedMessageException {
        switch (name) {
            case DUMP:
                dump(args);
                return this;
            case FLUSH:
                checkArity(0, args);
                flush();
                return this;
            case CACHE_CLEAR:
                if (exposeCache) {
                    checkArity(0, args);
                    if (memoryDump != null) {
                        memoryDump.clear();
                    }
                    return this;
                }
                // fall through
            default:
                throw UnknownIdentifierException.create(name);
        }
    }

    private void checkArity(int arity, Object[] args) throws ArityException {
        if (args.length != arity) {
            throw ArityException.create(arity, arity, args.length);
        }
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    boolean isMemberInvocable(String member) {
        switch (member) {
            case DUMP:
            case FLUSH:
                return true;
            case CACHE_CLEAR:
                return exposeCache;
            default:
                return false;
        }
    }

    @ExportMessage
    boolean isMemberReadable(String member) {
        switch (member) {
            case CACHE:
                return exposeCache;
            default:
                return false;
        }
    }

    @TruffleBoundary
    @ExportMessage
    @SuppressWarnings("fallthrough")
    Object readMember(String name) throws UnknownIdentifierException {
        switch (name) {
            case CACHE:
                if (exposeCache) {
                    if (memoryDump != null) {
                        return memoryDump;
                    } else {
                        return NullObject.INSTANCE;
                    }
                }
                // fall through
            default:
                throw UnknownIdentifierException.create(name);
        }
    }

    @ExportMessage
    Object getMembers(boolean includeInternal) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    synchronized void close() {
        try {
            final HeapDump.Builder g = getGeneratorOrNull();
            if (g != null) {
                g.close();
            }
            setGenerator(null);
            setSink(null);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    @TruffleBoundary
    public synchronized void accept(OutputStream t) {
        if (t == null) {
            throw new NullPointerException();
        }
        if (path != null) {
            throw new IllegalStateException("Cannot use path (" + path + ") and stream (" + t + ") at once");
        }
        close();
        setSink(t);
    }

    void flush() throws UnsupportedTypeException, UnsupportedMessageException {
        if (memoryDump != null) {
            memoryDump.flush();
        }
    }

    private void dump(Object[] args) throws UnsupportedTypeException, UnsupportedMessageException {
        if (args != null) {
            if (memoryDump != null) {
                memoryDump.addDump(args);
            } else {
                generateDump(args);
            }
        }
    }

    private void generateDump(Object[] args) throws UnsupportedTypeException, UnsupportedMessageException {
        try {
            HeapGenerator heap = new HeapGenerator(getGenerator());
            heap.dump(args);
        } catch (IOException ex) {
            throw new HeapException(ex);
        }
    }

    synchronized HeapDump.Builder getGenerator() throws IOException {
        HeapDump.Builder g = getGeneratorOrNull();
        if (g == null) {
            if (getSink() == null) {
                TruffleFile file = env.getTruffleFile(null, path);
                final OutputStream rawStream = file.newOutputStream();
                setSink(new BufferedOutputStream(rawStream));
            }
            g = HeapDump.newHeapBuilder(getSink());
            setGenerator(g);
        }
        return g;
    }

    private HeapDump.Builder getGeneratorOrNull() throws IOException {
        assert Thread.holdsLock(this);
        return generator;
    }

    private void setGenerator(HeapDump.Builder generator) {
        assert Thread.holdsLock(this);
        this.generator = generator;
    }

    private void setSink(OutputStream sink) {
        assert Thread.holdsLock(this);
        this.sink = sink;
    }

    private OutputStream getSink() {
        assert Thread.holdsLock(this);
        return sink;
    }

    @ExportLibrary(InteropLibrary.class)
    static final class NullObject implements TruffleObject {

        static final NullObject INSTANCE = new NullObject();

        @ExportMessage
        boolean isNull() {
            return true;
        }
    }
}
