/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.auximage;

import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.ComparableWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.shared.util.VMError;

final class AuxiliaryImagePersistenceControl {
    private static final int POLLING_FREQUENCY = 64;

    private final AuxiliaryImagePersistenceCallback callback;
    private int count;

    AuxiliaryImagePersistenceControl(AuxiliaryImagePersistenceCallback callback) {
        this.callback = callback;
        this.count = 0;
    }

    public AuxiliaryImagePersistenceCallback getCallback() {
        return callback;
    }

    public void poll() throws AuxiliaryImagePersistenceCancelledException {
        boolean cancel;
        try {
            cancel = callback.shouldCancel();
        } catch (Throwable t) {
            throw VMError.shouldNotReachHere("AuxiliaryImagePersistenceCallback must never throw", t);
        }
        if (cancel) {
            throw new AuxiliaryImagePersistenceCancelledException();
        }
    }

    /*
     * Used to avoid high-frequency polling.
     */
    public void maybePoll() throws AuxiliaryImagePersistenceCancelledException {
        count++;
        if (count == POLLING_FREQUENCY) {
            poll();
        }
    }
}

final class NonCancellableAuxiliaryImagePersistenceCallback implements AuxiliaryImagePersistenceCallback {
    @Override
    public boolean shouldCancel() {
        return false;
    }
}

final class MemoryBasedAuxiliaryImagePersistenceCallback implements AuxiliaryImagePersistenceCallback {
    private static final ComparableWord MEMORY_CODE_CONTINUE = Word.unsigned(0);

    private final WordPointer address;

    MemoryBasedAuxiliaryImagePersistenceCallback(WordPointer address) {
        this.address = address;
    }

    @Override
    public boolean shouldCancel() {
        ComparableWord currentValue = readValueFromAddress();
        return currentValue.notEqual(MEMORY_CODE_CONTINUE);
    }

    private ComparableWord readValueFromAddress() {
        return address.read();
    }
}
