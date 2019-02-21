package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.runtime.StaticObject;

public final class CheckCastNode extends QuickNode {
    // private final Klass typeToCheck;
    private static final int CACHE_SIZE = 5;
    private CastCache cache;

    private boolean executeCheckCast(Klass instanceKlass) {
        CastCacheItem item = cache.get_item(instanceKlass);
        if (item == null) {
            item = cache.add_item(instanceKlass);
        }
        return item.answer();
    }

    CheckCastNode(Klass typeToCheck) {
        assert !typeToCheck.isPrimitive();
        // this.typeToCheck = typeToCheck;
        this.cache = new CastCache(typeToCheck);
    }

    @TruffleBoundary
    private static boolean CheckCast(Klass typeToCheck, Klass instanceKlass) {
        return typeToCheck.isAssignableFrom(instanceKlass);
    }

    @Override
    public final int invoke(final VirtualFrame frame, int top) {
        BytecodeNode root = (BytecodeNode) getParent();
        StaticObject receiver = root.peekObject(frame, top - 1);
        boolean result = StaticObject.isNull(receiver) || executeCheckCast(receiver.getKlass());
        if (!result) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ClassCastException.class);
        }
        root.putKind(frame, top - 1, receiver, JavaKind.Object);
        return 0;
    }

    private static final class CastCacheItem {
        Klass cached;
        Boolean answer;

        CastCacheItem(Klass klass, Boolean result) {
            this.cached = klass;
            this.answer = result;
        }

        Boolean answer() {
            return answer;
        }
    }

    private static final class CastCache {
        private CastCacheItem[] cache = new CastCacheItem[CACHE_SIZE];
        private Klass typeToCheck;
        private int size = 1;
        private int pos = 0;

        CastCache(Klass toCheck) {
            this.typeToCheck = toCheck;
            cache[0] = new CastCacheItem(toCheck, true);
        }

        @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
        private CastCacheItem get_item(Klass item) {
            int i = pos;
            int size = this.size;
            do {
                CastCacheItem tested = cache[i];
                if (tested.cached == item) {
                    pos = i;
                    return tested;
                }
                i = (i == 0) ? size - 1 : i - 1;
            } while (i != pos);
            return null;
        }

        private CastCacheItem add_item(Klass item) {
            int new_size = size;
            if (size < CACHE_SIZE) {
                pos = size - 1;
                new_size++;
            }
            pos = (pos + 1) % new_size;
            Boolean answer = CheckCast(typeToCheck, item);
            CastCacheItem new_item = new CastCacheItem(item, answer);
            // Needs to be atomic
            cache[pos] = new_item;
            size = new_size;
            return new_item;
        }
    }
}