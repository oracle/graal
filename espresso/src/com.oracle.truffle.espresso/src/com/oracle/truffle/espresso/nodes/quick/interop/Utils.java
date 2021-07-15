package com.oracle.truffle.espresso.nodes.quick.interop;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

final class Utils {
    static boolean isBufferLike(InteropLibrary interop, Object foreignObject) {
        assert !(foreignObject instanceof StaticObject);
        return interop.hasBufferElements(foreignObject);
    }

    static boolean isArrayLike(InteropLibrary interop, Object foreignObject) {
        assert !(foreignObject instanceof StaticObject);
        return interop.hasArrayElements(foreignObject);
    }

    static boolean isByteArray(EspressoContext context, StaticObject array) {
        return array.getKlass() == context.getMeta()._byte_array;
    }

    static boolean isBufferLikeByteArray(EspressoContext context, InteropLibrary interop, StaticObject array) {
        assert !StaticObject.isNull(array);
        assert array.isForeignObject();
        return isByteArray(context, array) && isBufferLike(interop, array.rawForeignObject());
    }
}
