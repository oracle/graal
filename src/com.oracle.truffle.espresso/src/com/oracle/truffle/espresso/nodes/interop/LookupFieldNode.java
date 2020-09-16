package com.oracle.truffle.espresso.nodes.interop;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;

@GenerateUncached
public abstract class LookupFieldNode extends Node {
    static final int LIMIT = 3;

    LookupFieldNode() {
    }

    public abstract Field execute(Klass klass, String name, boolean onlyStatic);

    @SuppressWarnings("unused")
    @Specialization(guards = {"onlyStatic == cachedStatic", "klass == cachedKlass", "cachedName.equals(name)"}, limit = "LIMIT")
    Field doCached(Klass klass, String name, boolean onlyStatic,
                    @Cached("onlyStatic") boolean cachedStatic,
                    @Cached("klass") Klass cachedKlass,
                    @Cached("name") String cachedName,
                    @Cached("doUncached(klass, name, onlyStatic)") Field cachedField) {
        assert cachedField == doUncached(klass, name, onlyStatic);
        return cachedField;
    }

    @TruffleBoundary
    @Specialization(replaces = "doCached")
    Field doUncached(Klass klass, String name, boolean onlyStatic) {
        for (Field f : klass.getDeclaredFields()) {
            if (f.isPublic() && (!onlyStatic || f.isStatic()) && name.equals(f.getNameAsString())) {
                return f;
            }
        }
        return null;
    }
}
