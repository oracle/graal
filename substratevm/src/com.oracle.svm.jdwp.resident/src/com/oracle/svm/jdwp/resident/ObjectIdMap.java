/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.resident;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

import jdk.internal.misc.Unsafe;

import jdk.graal.compiler.core.common.SuppressFBWarnings;
import org.graalvm.nativeimage.ImageSingletons;

/**
 * Mapping of objects to unique IDs and back. Allows concurrent access and automatically disposes
 * mapping of GCed values. IDs are linearly increasing long values.
 * <p>
 * This is a lock-free implementation. The {@link LockFreeHashMap} accesses the hash table via an
 * 'accessFlag', which assures exclusive resize of the table. The resize is done by prepending a
 * bigger hash table to the table chain. After resize, new objects are always put to the first and
 * the biggest table.
 * <p>
 * Objects are stored in arrays of {@link HashNode} nodes. The arrays must not be muted to assure
 * data consistency. When nodes need to be changed, a new array is created and is safely (CAS)
 * replaced in the table index.
 * <p>
 * Every inserted object gets a unique ID assigned. The ID is generated only after the object's node
 * is stored in a table to assure uniqueness of IDs. See
 * {@link HashNode#finalizeId(ObjectIdMap.LockFreeHashMap, ObjectIdMap.LockFreeHashMap.HashingTable)}.
 */
public final class ObjectIdMap {

    private static final int INITIAL_SIZE_BITS = 10; // The first table has size 2^INITIAL_SIZE_BITS
    // An attemt to resize when the current resizeCount % (table.length/RESIZE_ATTEMPT) == 0
    private static final int RESIZE_ATTEMPT = 16;

    private volatile LockFreeHashMap map;
    private static final long mapOffset = Unsafe.getUnsafe().objectFieldOffset(ObjectIdMap.class, "map");

    private static long getObjectArrayByteOffset(long index) {
        long offset = Unsafe.getUnsafe().arrayBaseOffset(Object[].class);
        int scale = Unsafe.getUnsafe().arrayIndexScale(Object[].class);
        try {
            return Math.addExact(offset, Math.multiplyExact(index, scale));
        } catch (ArithmeticException ex) {
            throw new IndexOutOfBoundsException(index);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T getElementVolatile(T[] array, long index) {
        long arrayByteOffset = getObjectArrayByteOffset(index);
        return (T) Unsafe.getUnsafe().getReferenceVolatile(array, arrayByteOffset);
    }

    private static boolean compareAndSetElement(Object[] array, long index, Object existingElement, Object newElement) {
        long arrayByteOffset = getObjectArrayByteOffset(index);
        return Unsafe.getUnsafe().compareAndSetReference(array, arrayByteOffset, existingElement, newElement);
    }

    private LockFreeHashMap getMap() {
        LockFreeHashMap theMap = map;
        if (theMap == null) {
            // We have no map yet
            theMap = new LockFreeHashMap();
            LockFreeHashMap oldMap = (LockFreeHashMap) Unsafe.getUnsafe().compareAndExchangeReference(this, mapOffset, null, theMap);
            if (oldMap != null) {
                // It was set already
                theMap = oldMap;
            }
        }
        return theMap;
    }

    /**
     * Returns the ID, or -1 when the object is not tracked.
     */
    public long getIdExisting(Object obj) {
        if (obj == null) {
            return 0;
        }
        return getMap().getIdExisting(obj);
    }

    public long getIdOrCreateWeak(Object obj) {
        return getMap().getIdOrCreateWeak(obj);
    }

    public Object getObject(long id) {
        if (id == 0) {
            return null;
        }
        return getMap().getObject(id);
    }

    /**
     * Decreases the hold count by one, replaces HashNodeStrong with HashNodeWeak when holdCount is
     * zero.
     *
     * @param id The object id
     * @return true when the object reference exists.
     */
    public boolean enableCollection(long id) {
        return enableCollection(id, 1, false);
    }

    /**
     * Decreases the hold count by {@code disposeIfNotHold} and when holdCount is zero then either
     * dispose the node, or replace HashNodeStrong with HashNodeWeak.
     *
     * @param id The object id
     * @param refCount the count to decrease the hold count by.
     * @param disposeIfNotHold whether to dispose the object ID when hold count decrements to zero.
     * @return true when the object reference existed.
     */
    public boolean enableCollection(long id, int refCount, boolean disposeIfNotHold) {
        return getMap().enableCollection(id, refCount, disposeIfNotHold);
    }

    /**
     * Increases the hold count by one, replaces HashNodeWeak with HashNodeStrong when holdCount is
     * zero.
     *
     * @param id The object id
     * @return true when the object reference exists.
     */
    public boolean disableCollection(long id) {
        return getMap().disableCollection(id);
    }

    /**
     * Check if the object was collected.
     *
     * @param id The object id.
     * @return <code>true</code> when the object was collected, <code>false</code> when the object
     *         still exists in memory, <code>null</code> when the id never referenced an object.
     */
    @SuppressFBWarnings(value = "NP_BOOLEAN_RETURN_NULL", justification = "Intentional.")
    public Boolean isCollected(long id) {
        return getMap().isCollected(id);
    }

    public void reset() {
        LockFreeHashMap theMap;
        do {
            theMap = map;
            if (theMap == null) {
                return;
            }
        } while (!Unsafe.getUnsafe().compareAndSetReference(this, mapOffset, theMap, null));
        // Reset 'theMap', it will not be used any more
        theMap.reset();
    }

    public <T> T toObject(long objectId, Class<T> targetClass) {
        Object object = getObject(objectId);
        return targetClass.cast(object);
    }

    public long toId(Object object) {
        return getIdOrCreateWeak(object);
    }

    private static class LockFreeHashMap {

        private volatile TableAccessFlag accessFlag;
        private static final long accessFlagOffset = Unsafe.getUnsafe().objectFieldOffset(LockFreeHashMap.class, "accessFlag");
        private final ReferenceQueue<Object> refQueue = new ReferenceQueue<>();
        private volatile Thread refQueueThread;
        private volatile long lastId;
        private static final long lastIdOffset = Unsafe.getUnsafe().objectFieldOffset(LockFreeHashMap.class, "lastId");

        LockFreeHashMap() {
        }

        long getNextId() {
            long id = lastId;
            do {
                long witnessId = Unsafe.getUnsafe().compareAndExchangeLong(this, lastIdOffset, id, id + 1);
                if (witnessId != id) {
                    // Try again
                    id = witnessId;
                } else {
                    // id + 1 was written successfully
                    return id + 1;
                }
            } while (true);
        }

        private Thread startCleanupThread() {
            Thread queueThread = Thread.ofPlatform().name("JDWP Object map cleanup queue").unstarted(() -> {
                while (true) {
                    try {
                        Reference<?> ref = refQueue.remove();
                        HashNode node = (HashNodeWeak) ref;
                        dispose(node);
                    } catch (InterruptedException ex) {
                        break;
                    }
                }
            });
            if (ImageSingletons.contains(ThreadStartDeathSupport.class)) {
                ThreadStartDeathSupport.get().setDebuggerThreadObjectQueue(queueThread);
            }
            queueThread.setDaemon(true);
            queueThread.start();
            return queueThread;
        }

        void reset() {
            Thread queueThread = refQueueThread;
            if (queueThread != null) {
                queueThread.interrupt();
                if (ImageSingletons.contains(ThreadStartDeathSupport.class)) {
                    ThreadStartDeathSupport.get().setDebuggerThreadObjectQueue(null);
                }
            }
            // GC of this object and the ReferenceQueue will make all its elements eligible for GC.
            if (queueThread != null) {
                try { // The queue thread should finish eventually.
                    queueThread.join();
                } catch (InterruptedException e) {
                    // The join was interrupted, we give up
                }
            }
        }

        private TableAccessFlag getTableAccess() {
            TableAccessFlag flag = accessFlag;
            if (flag == null) {
                // No table was created yet, create the first one:
                HashingTable table = new HashingTable(1 << INITIAL_SIZE_BITS, null);
                flag = new TableAccessFlag(0, table);
                TableAccessFlag oldFlag = (TableAccessFlag) Unsafe.getUnsafe().compareAndExchangeReference(this, accessFlagOffset, null, flag);
                if (oldFlag == null) {
                    // We have successfully set the first table
                    refQueueThread = startCleanupThread();
                } else {
                    // It was set already
                    flag = oldFlag;
                }
            }
            return flag;
        }

        private boolean setNewTableAccess(TableAccessFlag oldFlag, TableAccessFlag newFlag) {
            return Unsafe.getUnsafe().compareAndSetReference(this, accessFlagOffset, oldFlag, newFlag);
        }

        /**
         * Returns the ID, or -1 when the object is not tracked.
         */
        public long getIdExisting(Object obj) {
            if (obj == null) {
                return 0;
            }
            TableAccessFlag flag = accessFlag;
            if (flag == null) {
                return -1; // Have no tables yet
            }
            HashingTable table = flag.table();
            int hash = System.identityHashCode(obj);
            HashNode node = getIdExisting(table, obj, hash);
            if (node != null) {
                return node.getId();
            } else {
                return -1;
            }
        }

        private static HashNode getIdExisting(HashingTable table, Object obj, int hash) {
            for (HashingTable t = table; t != null; t = t.getNext()) {
                HashNode node = t.getIdExisting(obj, hash);
                if (node != null) {
                    return node;
                }
            }
            return null;
        }

        public long getIdOrCreateWeak(Object obj) {
            long id = getIdExisting(obj);
            if (id != -1) {
                return id;
            }

            int hash = System.identityHashCode(obj);
            HashNode node = null;
            boolean[] needsFinalizeId = new boolean[]{false};
            do {
                TableAccessFlag tableAccess = getTableAccess();
                HashingTable table = tableAccess.table();
                boolean needsResize = table.needsResize();
                if (needsResize) {
                    int hashTableLength = table.hashToObjectTable.length;
                    if (tableAccess.resizeCount % (hashTableLength / RESIZE_ATTEMPT) == 0) {
                        // Let's try to do resize
                        int newSize = table.hashToObjectTable.length << 1;
                        HashingTable newTable = new HashingTable(newSize, table);
                        TableAccessFlag newTableAccess = new TableAccessFlag(0, newTable);
                        if (!setNewTableAccess(tableAccess, newTableAccess)) {
                            // The resize was not successful
                            continue;
                        } else {
                            // We're resized
                            table = newTable;
                            tableAccess = newTableAccess;
                        }
                    } else {
                        // Just increase the resize request count
                        TableAccessFlag newTableAccess = new TableAccessFlag(accessFlag.resizeCount + 1, table);
                        if (!setNewTableAccess(tableAccess, newTableAccess)) {
                            // Try next time
                            continue;
                        }
                    }
                }
                // Write the value to the table
                node = table.getIdOrCreateWeak(obj, hash, needsFinalizeId);
                if (needsFinalizeId[0]) {
                    // A new node was written to the table.
                    // We need to verify that the table wasn't resized in between
                    if (tableAccess.table == accessFlag.table) {
                        // We wrote it into the current table, great.
                        // Assign a new ID:
                        node.finalizeId(this, table);
                    } else {
                        // The table was resized in between. We can not be sure
                        // whether it wasn't put into the new table already
                        continue;
                    }
                }
                // We have the object's node. We exit the loop if the ID is set.
                assert node != null;
            } while (node == null || node.getId() < 0);

            return node.getId();
        }

        public Object getObject(long id) {
            if (id == 0) {
                return null;
            }
            TableAccessFlag flag = accessFlag;
            if (flag == null) {
                return null; // Have no tables yet
            }
            for (HashingTable table = flag.table(); table != null; table = table.getNext()) {
                Object obj = table.getObject(id);
                if (obj != null) {
                    return obj;
                }
            }
            return null;
        }

        private void dispose(HashNode node) {
            TableAccessFlag flag = accessFlag;
            if (flag == null) {
                return; // Have no tables yet
            }
            disposeAll(flag.table(), node);
        }

        private static void disposeAll(HashingTable table, HashNode node) {
            for (HashingTable t = table; t != null; t = t.getNext()) {
                t.dispose(node);
            }
        }

        boolean enableCollection(long id, int refCount, boolean disposeIfNotHold) {
            if (refCount < 0) {
                throw new IllegalArgumentException("Negative refCount not permitted: " + refCount);
            }
            TableAccessFlag flag = accessFlag;
            if (flag == null) {
                return false; // Have no tables yet
            }
            for (HashingTable table = flag.table(); table != null; table = table.getNext()) {
                boolean sucess = table.enableCollection(id, refCount, disposeIfNotHold);
                if (sucess) {
                    return sucess;
                }
            }
            return false;
        }

        public boolean disableCollection(long id) {
            TableAccessFlag flag = accessFlag;
            if (flag == null) {
                return false; // Have no tables yet
            }
            for (HashingTable table = flag.table(); table != null; table = table.getNext()) {
                boolean sucess = table.disableCollection(id);
                if (sucess) {
                    return sucess;
                }
            }
            return false;
        }

        @SuppressFBWarnings(value = "NP_BOOLEAN_RETURN_NULL", justification = "Intentional.")
        public Boolean isCollected(long id) {
            if (id <= 0 || id > lastId) {
                // Non-existing object
                return null;
            }
            Object obj = getObject(id);
            return null == obj;
        }

        /**
         * This class contains the hashing table that stores the hash -&gt; object mapping and also
         * the ID -&gt; hash code mapping for lookups by the ID.
         * <p>
         * <code>hashToObjectTable</code> is an array indexed by object's system hash code and
         * contains arrays of <code>HashNode</code>. <br>
         * <code>idToHashTable</code> is an array indexed by unique IDs and contains lists of
         * object's hash codes.
         */
        final class HashingTable {

            private final HashNode[][] hashToObjectTable; // object table by a hash code index
            private final HashListNode[] idToHashTable; // hash values by ID index
            private final HashingTable next;
            private volatile int size;
            private static final long sizeOffset = Unsafe.getUnsafe().objectFieldOffset(HashingTable.class, "size");

            HashingTable(int size, HashingTable next) {
                idToHashTable = new HashListNode[size];
                hashToObjectTable = new HashNode[size][];
                this.next = next;
            }

            HashingTable getNext() {
                return next;
            }

            HashNode getIdExisting(Object obj, int hash) {
                int index = (hashToObjectTable.length - 1) & hash;
                HashNode[] chain = getElementVolatile(hashToObjectTable, index);
                if (chain != null) {
                    for (HashNode node : chain) {
                        if (obj == node.getObject()) {
                            return node;
                        }
                    }
                }
                return null;
            }

            private void incrementSize() {
                changeSize(+1);
            }

            private void decrementSize() {
                changeSize(-1);
            }

            private void changeSize(int increment) {
                int oldSize = size;
                int s;
                do {
                    s = Unsafe.getUnsafe().compareAndExchangeInt(this, sizeOffset, oldSize, oldSize + increment);
                    if (s != oldSize) {
                        // Try again
                        oldSize = s;
                    } else {
                        break;
                    }
                } while (true);
            }

            boolean needsResize() {
                return size > hashToObjectTable.length && (hashToObjectTable.length << 1) > 0;
            }

            private HashNode getIdOrCreateWeak(Object obj, int hash, boolean[] needsFinalizeId) {
                int index = (hashToObjectTable.length - 1) & hash;
                HashNode[] oldChain;
                HashNode[] newChain;
                do {
                    oldChain = getElementVolatile(hashToObjectTable, index);
                    if (oldChain != null) {
                        // Search the old node for the object
                        for (HashNode node : oldChain) {
                            if (node.getObject() == obj) {
                                // The node is there already
                                needsFinalizeId[0] = false;
                                return node;
                            }
                        }
                        newChain = new HashNode[oldChain.length + 1];
                        System.arraycopy(oldChain, 0, newChain, 1, oldChain.length);
                    } else {
                        newChain = new HashNode[1];
                    }
                    newChain[0] = new HashNodeWeak(hash, obj, refQueue);
                } while (!compareAndSetElement(hashToObjectTable, index, oldChain, newChain));
                incrementSize();
                // A new node with uninitialized ID
                needsFinalizeId[0] = true;
                return newChain[0];
            }

            void newId(HashNode node) {
                // A node got a new ID assigned.
                // We need to add that to our 'hashes' table.
                long newId = node.getId();
                int hash = node.getHash();
                int hashIndex = (int) ((idToHashTable.length - 1) & newId);
                HashListNode oldList;
                HashListNode newList;
                do {
                    oldList = getElementVolatile(idToHashTable, hashIndex);
                    newList = new HashListNode(hash, oldList);
                } while (!compareAndSetElement(idToHashTable, hashIndex, oldList, newList));
            }

            public Object getObject(long id) {
                if (id == 0) {
                    return null;
                }
                int hashIndex = (int) ((idToHashTable.length - 1) & id);
                HashListNode list = getElementVolatile(idToHashTable, hashIndex);
                while (list != null) {
                    int hash = list.hash();
                    int index = (hashToObjectTable.length - 1) & hash;
                    HashNode[] chain = getElementVolatile(hashToObjectTable, index);
                    if (chain != null) {
                        for (HashNode node : chain) {
                            if (id == node.getId()) {
                                return node.getObject();
                            }
                        }
                    }
                    list = list.next();
                }
                return null;
            }

            private void dispose(HashNode node) {
                long id = node.getId();
                int hash = node.getHash();
                int index = (hashToObjectTable.length - 1) & hash;
                retryLoop: do {
                    HashNode[] oldChain = getElementVolatile(hashToObjectTable, index);
                    if (oldChain != null) {
                        for (int i = 0; i < oldChain.length; i++) {
                            if (oldChain[i].getId() == id) {
                                // Remove 'i' element from the oldChain, forming a newChain
                                HashNode[] newChain;
                                if (oldChain.length == 1) {
                                    newChain = null;
                                } else {
                                    // Copy chain, skipping the removed node at position 'i'
                                    newChain = removeNode(oldChain, i);
                                }
                                if (!compareAndSetElement(hashToObjectTable, index, oldChain, newChain)) {
                                    // We failed to write the new chain, try again
                                    continue retryLoop;
                                } else {
                                    // Successfully removed.
                                    // Node with the given ID is in the table just once.
                                    disposeId(id, hash);
                                    decrementSize();
                                    break;
                                }
                            }
                        }
                    }
                    // not found
                    break;
                } while (true);
            }

            private static HashNode[] removeNode(HashNode[] oldChain, int index) {
                HashNode[] newChain = new HashNode[oldChain.length - 1];
                if (index > 0) {
                    System.arraycopy(oldChain, 0, newChain, 0, index);
                }
                if (index < oldChain.length) {
                    System.arraycopy(oldChain, index + 1, newChain, index, newChain.length - index);
                }
                return newChain;
            }

            private static HashNode[] replaceNode(HashNode[] oldChain, int index, HashNode newNode) {
                if (oldChain.length == 1) {
                    return new HashNode[]{newNode};
                }
                HashNode[] newChain = new HashNode[oldChain.length];
                // Copy it all for simplicity
                System.arraycopy(oldChain, 0, newChain, 0, oldChain.length);
                newChain[index] = newNode;
                return newChain;
            }

            private void disposeId(long id, int hash) {
                int hashIndex = (int) ((idToHashTable.length - 1) & id);
                HashListNode oldList;
                retryLoop: do {
                    oldList = getElementVolatile(idToHashTable, hashIndex);
                    HashListNode nPrev = null;
                    for (HashListNode n = oldList; n != null; nPrev = n, n = n.next()) {
                        if (n.hash() == hash) {
                            // Remove 'n' from the chain
                            HashListNode newList;
                            if (nPrev == null) {
                                newList = n.next();
                            } else {
                                HashListNode end = n.next();
                                while (nPrev != null) {
                                    HashListNode nn = new HashListNode(nPrev.hash(), end);
                                    end = nn;
                                    HashListNode lastPrev = nPrev;
                                    // The chains are short, find the previous
                                    nPrev = null;
                                    for (HashListNode on = oldList; on != lastPrev; on = on.next()) {
                                        nPrev = on;
                                    }
                                }
                                newList = end;
                            }
                            if (compareAndSetElement(idToHashTable, hashIndex, oldList, newList)) {
                                // Disposed.
                                return;
                            } else {
                                // We failed to write the new chain, try again
                                continue retryLoop;
                            }
                        }
                    }
                    // not found
                    break;
                } while (true);
            }

            boolean enableCollection(long id, int refCount, boolean disposeIfNotHold) {
                int hashIndex = (int) ((idToHashTable.length - 1) & id);
                HashListNode list = getElementVolatile(idToHashTable, hashIndex);
                for (; list != null; list = list.next()) {
                    int index = (hashToObjectTable.length - 1) & list.hash();
                    retryLoop: do {
                        HashNode[] chain = getElementVolatile(hashToObjectTable, index);
                        if (chain != null) {
                            for (int i = 0; i < chain.length; i++) {
                                HashNode node = chain[i];
                                if (id == node.getId()) {
                                    if (node instanceof HashNodeStrong strongNode) {
                                        int holdCount = strongNode.changeHoldCount(-refCount);
                                        if (holdCount == 0 || holdCount == Integer.MIN_VALUE) {
                                            // The node needs to be replaced with a weak one.
                                            if (disposeIfNotHold) {
                                                disposeAll(this, node);
                                            } else {
                                                // Replace the strong node with a weak one
                                                HashNode weak = new HashNodeWeak(node.getHash(), node.getObject(), refQueue, node.getId());
                                                HashNode[] newChain = replaceNode(chain, i, weak);
                                                if (compareAndSetElement(hashToObjectTable, index, chain, newChain)) {
                                                    // We changed the node. We must wipe out any
                                                    // occurrences of this ID from other tables
                                                    if (this.next != null) {
                                                        disposeAll(this.next, node);
                                                    }
                                                } else {
                                                    continue retryLoop;
                                                }
                                            }
                                        }
                                    } else if (disposeIfNotHold) {
                                        // We're weak already
                                        disposeAll(this, node);
                                    }
                                    return true; // We found the ID
                                }
                            }
                        }
                        break; // not found
                    } while (true);
                }
                return false;
            }

            public boolean disableCollection(long id) {
                int hashIndex = (int) ((idToHashTable.length - 1) & id);
                HashListNode list = getElementVolatile(idToHashTable, hashIndex);
                for (; list != null; list = list.next()) {
                    int index = (hashToObjectTable.length - 1) & list.hash();
                    retryLoop: do {
                        HashNode[] chain = getElementVolatile(hashToObjectTable, index);
                        if (chain != null) {
                            for (int i = 0; i < chain.length; i++) {
                                HashNode node = chain[i];
                                if (id == node.getId()) {
                                    if (node instanceof HashNodeStrong strongNode) {
                                        strongNode.changeHoldCount(+1);
                                    } else {
                                        Object obj = node.getObject();
                                        if (obj == null) {
                                            // GCed
                                            return false;
                                        }
                                        // Replace node with HashNodeStrong
                                        HashNode strong = new HashNodeStrong(node.getHash(), obj, node.getId());
                                        HashNode[] newChain = replaceNode(chain, i, strong);
                                        if (compareAndSetElement(hashToObjectTable, index, chain, newChain)) {
                                            // We changed the node. We must wipe out any occurrences
                                            // of this ID from other tables
                                            if (this.next != null) {
                                                disposeAll(this.next, node);
                                            }
                                        } else {
                                            continue retryLoop;
                                        }
                                    }
                                    return true; // We found the ID
                                }
                            }
                        }
                        break; // not found
                    } while (true);
                }
                return false;
            }

        }

        private record TableAccessFlag(int resizeCount, HashingTable table) {

        }

    }

    private sealed interface HashNode permits HashNodeWeak, HashNodeStrong {

        /**
         * Get a unique ID, or -1 if it was not assigned yet.
         */
        long getId();

        /**
         * Assign a new unique ID.
         */
        long finalizeId(LockFreeHashMap map, LockFreeHashMap.HashingTable table);

        /**
         * Get the object's hash code.
         */
        int getHash();

        /**
         * Get the object, or {@code null} when collected.
         */
        Object getObject();

    }

    private static final class HashNodeWeak extends WeakReference<Object> implements HashNode {

        private volatile long id;
        private final int hash;

        HashNodeWeak(int hash, Object referent, ReferenceQueue<Object> refQueue) {
            this(hash, referent, refQueue, -1);
        }

        HashNodeWeak(int hash, Object referent, ReferenceQueue<Object> refQueue, long id) {
            super(referent, refQueue);
            this.id = id;
            this.hash = hash;
        }

        @Override
        public long getId() {
            return id;
        }

        @Override
        public long finalizeId(LockFreeHashMap map, LockFreeHashMap.HashingTable table) {
            long theId = id;
            if (theId <= -1) {
                theId = map.getNextId();
                // Only the node's creator will finalize, pure set is safe
                id = theId;
            }
            table.newId(this);
            return theId;
        }

        @Override
        public int getHash() {
            return hash;
        }

        @Override
        public Object getObject() {
            return get();
        }

    }

    private static final class HashNodeStrong implements HashNode {

        private final long id;
        private final int hash;
        private final Object object;
        private volatile int holdCount = 1;
        private static final long holdCountOffset = Unsafe.getUnsafe().objectFieldOffset(HashNodeStrong.class, "holdCount");

        HashNodeStrong(int hash, Object object, long id) {
            this.id = id;
            this.hash = hash;
            this.object = object;
        }

        @Override
        public long getId() {
            return id;
        }

        @Override
        public long finalizeId(LockFreeHashMap map, LockFreeHashMap.HashingTable table) {
            // The strong nodes are used as a replacement of existing weak nodes,
            // which have the ID initialized already.
            throw new UnsupportedOperationException();
        }

        int changeHoldCount(int increment) {
            int oldCount = holdCount;
            if (oldCount == 0) {
                // Locked when reached 0, the node is replaced with a weak one.
                return Integer.MIN_VALUE;
            }
            int count;
            do {
                int newCount = oldCount + increment;
                if (newCount < 0) {
                    newCount = 0;
                }
                count = Unsafe.getUnsafe().compareAndExchangeInt(this, holdCountOffset, oldCount, newCount);
                if (count != oldCount) {
                    // Try again
                    oldCount = count;
                } else {
                    return newCount;
                }
            } while (true);
        }

        @Override
        public int getHash() {
            return hash;
        }

        @Override
        public Object getObject() {
            return object;
        }

    }

    private record HashListNode(int hash, HashListNode next) {
    }

}
