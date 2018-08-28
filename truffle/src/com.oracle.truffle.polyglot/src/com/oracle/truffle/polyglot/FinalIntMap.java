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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;

/*
 * An internal map designed for partial evaluation.
 * Only contains final object to int assocations. get returns a constant when partially evaluated.
 */
final class FinalIntMap {

    @CompilationFinal Entry first;

    @ExplodeLoop(kind = LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    int get(Object key) {
        Entry current = first;
        while (current != null) {
            if (current.key == key) {
                return current.value;
            }
            current = current.next;
        }
        return -1;
    }

    void put(Object key, int value) {
        CompilerAsserts.neverPartOfCompilation();
        assert get(key) == -1 : "replace not supported by this map implementation";
        assert value >= 0 : "only positive integers supported";
        Entry prev = null;
        Entry current = first;
        while (current != null) {
            prev = current;
            current = current.next;
        }
        Entry entry = new Entry(key, value);
        if (prev == null) {
            assert current == first;
            first = entry;
        } else {
            prev.next = entry;
        }

    }

    static final class Entry {

        final Object key;
        final int value;
        @CompilationFinal Entry next;

        Entry(Object key, int value) {
            this.key = key;
            this.value = value;
        }

    }

}
