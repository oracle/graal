package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.runtime.StaticObject;

public abstract class InstanceOfNode extends QuickNode {

    final Klass typeToCheck;

    static final int INLINE_CACHE_SIZE_LIMIT = 5;

    protected abstract boolean executeInstanceOf(Klass instanceKlass);

    @SuppressWarnings("unused")
    @Specialization(limit = "INLINE_CACHE_SIZE_LIMIT", guards = "instanceKlass == cachedKlass")
    boolean instanceOfCached(Klass instanceKlass,
                    @Cached("instanceKlass") Klass cachedKlass,
                    @Cached("instanceOf(typeToCheck, cachedKlass)") boolean cachedAnswer) {
        return cachedAnswer;
    }

    @Specialization(replaces = "instanceOfCached")
    boolean instanceOfSlow(Klass instanceKlass) {
        // Brute instanceof checks, walk the whole klass hierarchy.
        return instanceOf(typeToCheck, instanceKlass);
    }

    InstanceOfNode(Klass typeToCheck) {
        assert !typeToCheck.isPrimitive();
        this.typeToCheck = typeToCheck;
    }

    @TruffleBoundary
    static boolean instanceOf(Klass typeToCheck, Klass instanceKlass) {
        // TODO(peterssen): Method lookup is uber-slow and non-spec-compliant.
        return typeToCheck.isAssignableFrom(instanceKlass);
    }

    @Override
    public final int invoke(final VirtualFrame frame, int top) {
        // TODO(peterssen): Maybe refrain from exposing the whole root node?.
        BytecodeNode root = (BytecodeNode) getParent();
        StaticObject receiver = root.peekObject(frame, top - 1);
        boolean result = StaticObject.notNull(receiver) && executeInstanceOf(receiver.getKlass());
        root.putKind(frame, top - 1, result, JavaKind.Boolean);
        return 0; // stack effect -> pop receiver, push boolean
    }
}
