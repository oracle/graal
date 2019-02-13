/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.nfi;

import java.util.ArrayList;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class LocalNativeScope implements AutoCloseable {

    private final LocalNativeScope parent;
    private final short scopeId;

    private int pinCount;
    private final PinnedObject[] pinned;

    private ArrayList<Object> localHandles;

    LocalNativeScope(LocalNativeScope parent, int patchCount) {
        this.parent = parent;
        if (parent == null) {
            scopeId = 1;
        } else {
            assert parent.scopeId < Short.MAX_VALUE;
            scopeId = (short) (parent.scopeId + 1);
        }

        pinCount = 0;
        pinned = patchCount > 0 ? new PinnedObject[patchCount] : null;

        localHandles = null; // lazy
    }

    public TruffleObjectHandle createLocalHandle(Object obj) {
        if (localHandles == null) {
            localHandles = new ArrayList<>();
        }

        int idx = localHandles.size();
        localHandles.add(obj);
        assert localHandles.get(idx) == obj;

        return (TruffleObjectHandle) WordFactory.unsigned(idx).shiftLeft(16).or(scopeId & 0xFFFF).not();
    }

    private LocalNativeScope findScope(short id) {
        LocalNativeScope cur = this;
        while (cur != null && cur.scopeId > id) {
            cur = cur.parent;
        }
        if (cur != null && cur.scopeId == id) {
            return cur;
        } else {
            // dead scope
            return null;
        }
    }

    public Object resolveLocalHandle(TruffleObjectHandle handle) {
        Word word = ((Word) handle).not();
        short handleScopeId = (short) word.and(0xFFFF).rawValue();

        LocalNativeScope scope = findScope(handleScopeId);
        if (scope != null) {
            int idx = (int) word.unsignedShiftRight(16).rawValue();
            return scope.localHandles.get(idx);
        } else {
            // dead scope
            return null;
        }
    }

    @TruffleBoundary
    PointerBase pinArray(Object arr) {
        PinnedObject ret = PinnedObject.create(arr);
        pinned[pinCount++] = ret;
        return ret.addressOfArrayElement(0);
    }

    @TruffleBoundary
    PointerBase pinString(String str) {
        byte[] array = TruffleNFISupport.javaStringToUtf8(str);
        return pinArray(array);
    }

    @Override
    public void close() {
        for (int i = 0; i < pinCount; i++) {
            pinned[i].close();
        }

        TruffleNFISupport.closeLocalScope(this, parent);
    }
}
