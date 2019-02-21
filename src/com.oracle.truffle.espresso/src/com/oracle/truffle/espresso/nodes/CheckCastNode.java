package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.runtime.StaticObject;

public abstract class CheckCastNode extends QuickNode {

    final Klass typeToCheck;
    static final int INLINE_CACHE_SIZE_LIMIT = 5;

    protected abstract boolean executeCheckCast(Klass instanceKlass);

    CheckCastNode(Klass typeToCheck) {
        assert !typeToCheck.isPrimitive();
        this.typeToCheck = typeToCheck;
    }

    @Specialization(limit = "INLINE_CACHE_SIZE_LIMIT", guards = "instanceKlass == cachedKlass")
    boolean checkCastCached(Klass instanceKlass,
                    @Cached("instanceKlass") Klass cachedKlass,
                    @Cached("checkCast(typeToCheck, cachedKlass)") boolean cachedAnswer) {
        return cachedAnswer;
    }

    @Specialization(replaces = "checkCastCached")
    boolean checkCastSlow(Klass instanceKlass) {
        // Brute checkcast, walk the whole klass hierarchy.
        return checkCast(typeToCheck, instanceKlass);
    }

    @TruffleBoundary
    static boolean checkCast(Klass typeToCheck, Klass instanceKlass) {
        return typeToCheck.isAssignableFrom(instanceKlass);
    }

    @Override
    public final int invoke(final VirtualFrame frame, int top) {
        BytecodeNode root = (BytecodeNode) getParent();
        StaticObject receiver = root.peekObject(frame, top - 1);
        // boolean result = StaticObject.isNull(receiver) || handCacheCall(receiver.getKlass());
        boolean result = StaticObject.isNull(receiver) || executeCheckCast(receiver.getKlass());
        if (!result) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ClassCastException.class);
        }
        // root.putKind(frame, top - 1, receiver, JavaKind.Object);
        return 0;
    }

    /*
     * static final int CACHE_SIZE = 5; private CastCache cache; boolean handCacheCall(Klass
     * instanceKlass) { CastCacheItem item = cache.getItem(instanceKlass); if (item == null) { item
     * = cache.addItem(instanceKlass); } return item.answer(); }
     * 
     * private static final class CastCacheItem { Klass cached; Boolean answer;
     * 
     * CastCacheItem(Klass klass, Boolean result) { this.cached = klass; this.answer = result; }
     * 
     * Boolean answer() { return answer; } }
     * 
     * private static final class CastCache { private CastCacheItem[] cache = new
     * CastCacheItem[CACHE_SIZE]; private Klass typeToCheck; private int size = 1; private int pos =
     * 0;
     * 
     * CastCache(Klass toCheck) { this.typeToCheck = toCheck; cache[0] = new CastCacheItem(toCheck,
     * true); }
     * 
     * @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN) private
     * CastCacheItem getItem(Klass item) { int i = pos; int locSize = this.size; do { CastCacheItem
     * tested = cache[i]; if (tested.cached == item) { pos = i; return tested; } i = (i == 0) ?
     * locSize - 1 : i - 1; } while (i != pos); return null; }
     * 
     * private CastCacheItem addItem(Klass item) { int newSize = size; if (size < CACHE_SIZE) { pos
     * = size - 1; newSize++; } pos = (pos + 1) % newSize; Boolean answer = checkCast(typeToCheck,
     * item); CastCacheItem newItem = new CastCacheItem(item, answer); // Needs to be atomic
     * cache[pos] = newItem; size = newSize; return newItem; } }
     */
}