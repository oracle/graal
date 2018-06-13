package org.graalvm.compiler.truffle.pelang.obj;

import org.graalvm.compiler.truffle.pelang.PELangNull;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.Shape;

public abstract class PELangPropertyReadCacheNode extends PELangPropertyCacheNode {

    public abstract Object executeRead(DynamicObject receiver, String name);

    @Specialization(limit = "CACHE_LIMIT", //
                    guards = {
                                    "cachedName.equals(name)",
                                    "checkShape(shape, receiver)"
                    }, //
                    assumptions = {
                                    "shape.getValidAssumption()"
                    })
    protected static Object readCached(
                    DynamicObject receiver,
                    @SuppressWarnings("unused") String name,
                    @SuppressWarnings("unused") @Cached("name") String cachedName,
                    @Cached("lookupShape(receiver)") Shape shape,
                    @Cached("lookupLocation(shape, name)") Location location) {
        return (location == null) ? PELangNull.getInstance() : location.get(receiver, shape);
    }

    @TruffleBoundary
    @Specialization(replaces = {"readCached"}, guards = "receiver.getShape().isValid()")
    protected Object readUncached(DynamicObject receiver, String name) {
        Object result = receiver.get(name);
        return (result == null) ? PELangNull.getInstance() : result;
    }

    @Specialization(guards = "!receiver.getShape().isValid()")
    protected Object updateShape(DynamicObject receiver, String name) {
        CompilerDirectives.transferToInterpreter();
        receiver.updateShape();
        return readUncached(receiver, name);
    }

    public static PELangPropertyReadCacheNode create() {
        return PELangPropertyReadCacheNodeGen.create();
    }

}
