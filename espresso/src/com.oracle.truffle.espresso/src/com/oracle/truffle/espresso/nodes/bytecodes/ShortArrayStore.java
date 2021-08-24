package com.oracle.truffle.espresso.nodes.bytecodes;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.nodes.quick.interop.ForeignArrayUtils;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * SASTORE bytecode with interop extensions.
 *
 * <h3>Extensions</h3>
 * <ul>
 * <li>For {@link InteropLibrary#hasArrayElements(Object) array-like} foreign objects, SASTORE is
 * mapped to {@link InteropLibrary#writeArrayElement(Object, long, Object)}.</li>
 * </ul>
 *
 * <h3>Exceptions</h3>
 * <ul>
 * <li>Throws guest {@link ArrayIndexOutOfBoundsException} if the index is out of bounds.</li>
 * <li>Throws guest {@link ClassCastException} if the underlying interop array does not accept
 * writing a short.</li>
 * <li>Throws guest {@link ArrayStoreException} if the underlying interop array is read-only.</li>
 * </ul>
 */
@GenerateUncached
@NodeInfo(shortName = "SASTORE")
public abstract class ShortArrayStore extends Node {

    public abstract void execute(StaticObject receiver, int index, short value);

    @Specialization
    void executeWithNullCheck(StaticObject array, int index, short value,
                    @Cached NullCheck nullCheck,
                    @Cached WithoutNullCheck shortArrayStore) {
        shortArrayStore.execute(nullCheck.execute(array), index, value);
    }

    @GenerateUncached
    @NodeInfo(shortName = "SASTORE !nullcheck")
    public abstract static class WithoutNullCheck extends Node {
        static final int LIMIT = 2;

        public abstract void execute(StaticObject receiver, int index, short value);

        protected EspressoContext getContext() {
            return EspressoContext.get(this);
        }

        @Specialization(guards = "array.isEspressoObject()")
        void doEspresso(StaticObject array, int index, short value) {
            assert !StaticObject.isNull(array);
            getContext().getInterpreterToVM().setArrayShort(value, index, array);
        }

        @Specialization(guards = "array.isForeignObject()")
        void doArrayLike(StaticObject array, int index, short value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile exceptionProfile) {
            assert !StaticObject.isNull(array);
            ForeignArrayUtils.writeForeignArrayElement(array, index, value, interop, getContext().getMeta(), exceptionProfile);
        }
    }
}
