package com.oracle.svm.hosted.analysis.ai.domain.heap;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Very small, generic heap/object abstraction that can be layered on top of
 * existing numeric domains. For now this is intentionally minimal: it tracks
 * abstract objects (identified by allocation-site IDs) and for arrays a
 * (possibly imprecise) length interval.
 *
 * The intent is that higher-level domains (e.g. numerical data-flow) can
 * either embed this domain or keep an instance alongside their existing
 * AbstractMemory to reason about object-level properties such as array
 * lengths, without changing the core fixpoint engine.
 */
public final class HeapObjectDomain implements AbstractDomain<HeapObjectDomain> {

    /** Immutable identifier for an abstract object (e.g. allocation site). */
    public static final class ObjectId {
        private final String id;

        public ObjectId(String id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public String id() {
            return id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ObjectId other)) {
                return false;
            }
            return id.equals(other.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return "ObjectId{" + id + '}';
        }
    }

    /**
     * Very small per-object information. We purposely only model what we need
     * now (array length), but the structure is extensible (fields, element
     * ranges, etc.).
     */
    public static final class ObjectInfo {
        private final String typeName;
        /** -1 when unknown / top, otherwise a concrete non-negative length. */
        private int concreteArrayLength = -1;

        public ObjectInfo(String typeName) {
            this.typeName = Objects.requireNonNull(typeName, "typeName");
        }

        public String typeName() {
            return typeName;
        }

        public boolean hasConcreteArrayLength() {
            return concreteArrayLength >= 0;
        }

        public int concreteArrayLength() {
            return concreteArrayLength;
        }

        public void setConcreteArrayLength(int len) {
            if (len < 0 && len != -1) {
                throw new IllegalArgumentException("array length must be >= 0 or -1 for unknown");
            }
            this.concreteArrayLength = len;
        }

        public ObjectInfo copy() {
            ObjectInfo copy = new ObjectInfo(typeName);
            copy.concreteArrayLength = concreteArrayLength;
            return copy;
        }

        @Override
        public String toString() {
            return "ObjectInfo{" +
                    "typeName='" + typeName + '\'' +
                    ", concreteArrayLength=" + concreteArrayLength +
                    '}';
        }
    }

    private boolean isTop;
    private boolean isBot;
    private final Map<ObjectId, ObjectInfo> heap;

    public HeapObjectDomain() {
        this.heap = new HashMap<>();
        this.isBot = false;
        this.isTop = false;
    }

    private HeapObjectDomain(Map<ObjectId, ObjectInfo> heap, boolean isTop, boolean isBot) {
        this.heap = heap;
        this.isTop = isTop;
        this.isBot = isBot;
    }

    public Map<ObjectId, ObjectInfo> heap() {
        return heap;
    }

    public ObjectInfo lookup(ObjectId id) {
        return heap.get(id);
    }

    public void put(ObjectId id, ObjectInfo info) {
        heap.put(id, info);
    }

    @Override
    public HeapObjectDomain copyOf() {
        Map<ObjectId, ObjectInfo> copyMap = new HashMap<>();
        for (Map.Entry<ObjectId, ObjectInfo> e : heap.entrySet()) {
            copyMap.put(e.getKey(), e.getValue().copy());
        }
        return new HeapObjectDomain(copyMap, isTop, isBot);
    }

    @Override
    public void joinWith(HeapObjectDomain other) {
        if (other == null || other.isBot) {
            return;
        }
        if (isBot) {
            // BOT join x = x
            this.isBot = other.isBot;
            this.isTop = other.isTop;
            this.heap.clear();
            this.heap.putAll(other.heap);
            return;
        }
        if (isTop || other.isTop) {
            setToTop();
            return;
        }
        // Very simple join: keep only objects present in both heaps and
        // intersect / generalize their lengths. For now, if lengths differ we
        // drop to unknown (-1).
        heap.keySet().retainAll(other.heap.keySet());
        for (Map.Entry<ObjectId, ObjectInfo> e : heap.entrySet()) {
            ObjectInfo mine = e.getValue();
            ObjectInfo theirs = other.heap.get(e.getKey());
            if (theirs == null) {
                continue;
            }
            if (mine.hasConcreteArrayLength() && theirs.hasConcreteArrayLength()) {
                if (mine.concreteArrayLength() != theirs.concreteArrayLength()) {
                    mine.setConcreteArrayLength(-1); // unknown
                }
            } else {
                mine.setConcreteArrayLength(-1);
            }
        }
    }

    @Override
    public void widenWith(HeapObjectDomain other) {
        // For now, widening is identical to join. If we later add truly
        // increasing chains we can extend this.
        joinWith(other);
    }

    @Override
    public void meetWith(HeapObjectDomain other) {
        if (other == null || other.isTop) {
            return;
        }
        if (isTop) {
            // TOP meet x = x
            this.isTop = other.isTop;
            this.isBot = other.isBot;
            this.heap.clear();
            this.heap.putAll(other.heap);
            return;
        }
        if (other.isBot) {
            setToBot();
            return;
        }
        // Meet keeps all objects from both heaps; for overlapping objects we
        // keep the more precise (if equal) or fall back to unknown.
        for (Map.Entry<ObjectId, ObjectInfo> e : other.heap.entrySet()) {
            ObjectId id = e.getKey();
            ObjectInfo theirs = e.getValue();
            ObjectInfo mine = heap.get(id);
            if (mine == null) {
                heap.put(id, theirs.copy());
            } else {
                if (mine.hasConcreteArrayLength() && theirs.hasConcreteArrayLength()) {
                    if (mine.concreteArrayLength() != theirs.concreteArrayLength()) {
                        mine.setConcreteArrayLength(-1);
                    }
                } else {
                    mine.setConcreteArrayLength(-1);
                }
            }
        }
    }

    @Override
    public void setToTop() {
        heap.clear();
        isTop = true;
        isBot = false;
    }

    @Override
    public void setToBot() {
        heap.clear();
        isTop = false;
        isBot = true;
    }

    @Override
    public boolean isTop() {
        return isTop;
    }

    @Override
    public boolean leq(HeapObjectDomain other) {
        if (other == null) {
            return false;
        }
        if (this.isBot) {
            return true;
        }
        if (other.isTop) {
            return true;
        }
        if (this.isTop) {
            return other.isTop;
        }
        if (other.isBot) {
            return this.isBot;
        }
        // This ⊑ other iff for every object we have, other has a compatible
        // (at least as imprecise) description.
        for (Map.Entry<ObjectId, ObjectInfo> e : heap.entrySet()) {
            ObjectInfo mine = e.getValue();
            ObjectInfo theirs = other.heap.get(e.getKey());
            if (theirs == null) {
                return false;
            }
            if (mine.hasConcreteArrayLength()) {
                if (!theirs.hasConcreteArrayLength()) {
                    // other is less precise (unknown) -> ok
                    continue;
                }
                if (mine.concreteArrayLength() != theirs.concreteArrayLength()) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean isBot() {
        return isBot;
    }

    @Override
    public String toString() {
        if (isTop) {
            return "HeapObjectDomain{TOP}";
        }
        if (isBot) {
            return "HeapObjectDomain{BOT}";
        }
        return "HeapObjectDomain{" + heap + '}';
    }
}

