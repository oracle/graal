/*
 * Copyright (c) 2021, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class IDGenerater {

    private final AtomicInteger nextID = new AtomicInteger(0);
    private final ReferenceQueue<BitcodeID> refQueue = new ReferenceQueue<>();
    private final IDReference first = new IDReference();

    public static final BitcodeID INVALID_ID = new BitcodeID(-1);

    public static final class BitcodeID {
        private final int id;

        private BitcodeID(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public boolean same(BitcodeID other) {
            return this.id == other.getId();
        }
    }

    private final class IDReference extends PhantomReference<BitcodeID> {

        private IDReference prev;
        private IDReference next;
        private final int id;

        private IDReference(BitcodeID id) {
            super(id, refQueue);
            this.id = id.id;

        }

        private IDReference() {
            super(null, null);
            this.prev = this;
            this.next = this;
            this.id = -1;
        }
    }

    private synchronized void add(IDReference allocation) {
        assert allocation.prev == null && allocation.next == null;

        IDReference second = first.next;
        allocation.prev = first;
        allocation.next = second;

        first.next = allocation;
        second.prev = allocation;
    }

    private static synchronized void remove(IDReference allocation) {
        allocation.prev.next = allocation.next;
        allocation.next.prev = allocation.prev;

        allocation.next = null;
        allocation.prev = null;
    }

    public BitcodeID generateID() {
        IDReference ref = (IDReference) refQueue.poll();
        if (ref != null) {
            // To see if any ids are really being reused. It is possible to log all reused ids with
            // the TruffleLogger.
            remove(ref);
            return createID(ref.id);
        }
        return createID(nextID.getAndIncrement());
    }

    private BitcodeID createID(int id) {
        BitcodeID bitcodeID = new BitcodeID(id);
        IDReference ref = new IDReference(bitcodeID);
        add(ref);
        return bitcodeID;
    }
}
