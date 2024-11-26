package com.oracle.truffle.espresso.classfile.resolver.meta;

import com.oracle.truffle.espresso.shared.descriptors.Symbol;
import com.oracle.truffle.espresso.shared.descriptors.Symbol.Name;

/**
 * A {@link Named} object must provide a {@link #getSymbolicName() symbolic name}.
 */
public interface Named {
    /**
     * @return The symbolic name of this object.
     */
    Symbol<Name> getSymbolicName();
}
