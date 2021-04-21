package com.oracle.truffle.llvm.runtime;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.BitSet;
import java.util.concurrent.atomic.AtomicInteger;

public final class IDGenerater {

    private final AtomicInteger nextID = new AtomicInteger(0);
    private final ReferenceQueue<BitcodeID> refQueue = new ReferenceQueue<>();
    private final IDReference first = new IDReference();

    public final static BitcodeID INVALID_ID = new BitcodeID(-1);;

    public static class BitcodeID {
        private final int id;
        private BitcodeID(int id){
            this.id = id;
        }

        public int getId() {
            return id;
        }
    }

    private class IDReference extends PhantomReference<BitcodeID> {

        private IDReference prev;
        private IDReference next;
        private final int id;

        private IDReference(BitcodeID id){
            super(id, refQueue);
            this.id = id.id;

        }

        private IDReference(){
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
        if (ref != null ) {
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
