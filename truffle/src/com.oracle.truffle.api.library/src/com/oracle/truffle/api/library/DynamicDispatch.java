package com.oracle.truffle.api.library;

/**
 * Interface for library receiver types that want to allow dynamic dispatch to library exports.
 */
public interface DynamicDispatch {

    /**
     * Returns a class that {@link ExportLibrary exports} at least one library with an explicit
     * receiver. The result of this method must be stable, i.e. multiple calls to dispatch for the
     * same instance must lead to the same result.
     */
    Class<?> dispatch();

}
