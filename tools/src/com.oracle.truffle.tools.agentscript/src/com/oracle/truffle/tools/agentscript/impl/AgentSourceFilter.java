/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.agentscript.impl;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.source.Source;
import java.util.function.Predicate;

final class AgentSourceFilter implements Predicate<Source> {
    private final Object fn;
    private final ThreadLocal<Boolean> querying;

    AgentSourceFilter(Object fn) {
        this.fn = fn;
        this.querying = new ThreadLocal<>();
    }

    @CompilerDirectives.TruffleBoundary
    @Override
    public boolean test(Source src) {
        if (src == null) {
            return false;
        }
        Boolean prev = this.querying.get();
        try {
            if (Boolean.TRUE.equals(prev)) {
                return false;
            }
            this.querying.set(true);
            final InteropLibrary iop = InteropLibrary.getFactory().getUncached();
            Object res = iop.execute(fn, new SourceEventObject(src));
            return (Boolean) res;
        } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException ex) {
            return false;
        } finally {
            this.querying.set(prev);
        }
    }
}
