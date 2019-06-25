package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * Utility class to spy Espresso's underlying representation from the guest.
 */
@EspressoSubstitutions
public class Target_com_oracle_truffle_espresso_InternalUtils {

    @Substitution
    public static @Host(byte[].class) StaticObject getUnderlyingFieldArray(@Host(Object.class) StaticObject self) {
        return new StaticObject(self.getKlass().getMeta().Byte.getArrayClass(), self.cloneFields());
    }

    @Substitution
    public static @Host(String.class) StaticObject toVerboseString(@Host(Object.class) StaticObject self) {
        return self.getKlass().getMeta().toGuestString(self.toVerboseString());
    }
}
