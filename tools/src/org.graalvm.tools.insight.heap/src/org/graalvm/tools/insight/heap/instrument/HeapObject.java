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
package org.graalvm.tools.insight.heap.instrument;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
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
import org.graalvm.tools.insight.Insight.SymbolProvider;
import org.graalvm.tools.insight.heap.HeapDump;

@SuppressWarnings({"static-method", "unused"})
@ExportLibrary(InteropLibrary.class)
final class HeapObject implements TruffleObject, SymbolProvider {
    private final TruffleInstrument.Env env;
    private final String path;
    private HeapDump.Builder generator;

    HeapObject(TruffleInstrument.Env env, String path) {
        this.env = env;
        this.path = path;
    }

    @Override
    public Map<String, ? extends Object> symbolsWithValues() throws Exception {
        if (path == null) {
            return Collections.emptyMap();
        } else {
            return Collections.singletonMap("heap", this);
        }
    }

    synchronized HeapDump.Builder getGenerator() throws IOException {
        if (generator == null) {
            TruffleFile file = env.getTruffleFile(path);
            final OutputStream rawStream = file.newOutputStream();
            final OutputStream bufferedStream = new BufferedOutputStream(rawStream);
            generator = HeapDump.newHeapBuilder(bufferedStream);
        }
        return generator;
    }

    @TruffleBoundary
    @ExportMessage
    Object invokeMember(String name, Object[] args) throws UnknownIdentifierException, UnsupportedTypeException, UnsupportedMessageException {
        if (name.equals("dump")) {
            try {
                HeapGenerator heap = new HeapGenerator(getGenerator());
                heap.dump(args);
                return this;
            } catch (IOException ex) {
                throw new HeapException(ex);
            }
        }
        throw UnknownIdentifierException.create(name);
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    boolean isMemberInvocable(String member) {
        return "dump".equals(member);
    }

    @ExportMessage
    Object getMembers(boolean includeInternal) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    void close() {
        try {
            generator.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
