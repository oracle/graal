package org.graalvm.compiler.truffle.pelang.obj;

import org.graalvm.compiler.truffle.pelang.PELangState;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;

public abstract class PELangPropertyCacheNode extends Node {

    protected static final int CACHE_LIMIT = 3;

    protected static boolean checkShape(Shape shape, DynamicObject receiver) {
        return shape != null && shape.check(receiver);
    }

    protected static Shape lookupShape(DynamicObject receiver) {
        CompilerAsserts.neverPartOfCompilation();
        assert PELangState.isPELangObject(receiver);
        return receiver.getShape();
    }

    protected static Location lookupLocation(Shape shape, String name) {
        // Initialization of cached values always happens in a slow path
        CompilerAsserts.neverPartOfCompilation();
        Property property = shape.getProperty(name);
        return (property == null) ? null : property.getLocation();
    }

    protected static Location lookupLocation(Shape shape, String name, Object value) {
        Location location = lookupLocation(shape, name);
        return (location == null || !location.canSet(value)) ? null : location;
    }

    protected static Shape defineProperty(Shape oldShape, String name, Object value) {
        return oldShape.defineProperty(name, value, 0);
    }

}
