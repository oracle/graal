package com.oracle.truffle.espresso.nodes.bytecodes;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.quick.interop.ForeignArrayUtils;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * DALOAD bytecode with interop extensions.
 *
 * <h3>Extensions</h3>
 * <ul>
 * <li>For {@link InteropLibrary#hasArrayElements(Object) array-like} foreign objects, DALOAD is
 * mapped to {@link InteropLibrary#readArrayElement(Object, long)}.</li>
 * </ul>
 *
 * <h3>Exceptions</h3>
 * <ul>
 * <li>Throws guest {@link ArrayIndexOutOfBoundsException} if the index is out of bounds.</li>
 * <li>Throws guest {@link ClassCastException} if the result cannot be converted to double.</li>
 * </ul>
 */
@GenerateUncached
@NodeInfo(shortName = "DALOAD")
public abstract class DoubleArrayLoad extends Node {

    public abstract double execute(StaticObject receiver, int index);

    @Specialization
    double executeWithNullCheck(StaticObject array, int index,
                    @Cached NullCheck nullCheck,
                    @Cached WithoutNullCheck doubleArrayLoad) {
        return doubleArrayLoad.execute(nullCheck.execute(array), index);
    }

    @GenerateUncached
    @NodeInfo(shortName = "DALOAD !nullcheck")
    public abstract static class WithoutNullCheck extends Node {

        protected static final int LIMIT = 2;

        public abstract double execute(StaticObject receiver, int index);

        protected EspressoContext getContext() {
            return EspressoContext.get(this);
        }

        @Specialization(guards = "array.isEspressoObject()")
        double doEspresso(StaticObject array, int index) {
            assert !StaticObject.isNull(array);
            return getContext().getInterpreterToVM().getArrayDouble(index, array);
        }

        @Specialization(guards = "array.isForeignObject()")
        double doArrayLike(StaticObject array, int index,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary arrayInterop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary elemInterop,
                        @Cached BranchProfile exceptionProfile) {
            assert !StaticObject.isNull(array);
            Meta meta = getContext().getMeta();
            Object result = ForeignArrayUtils.readForeignArrayElement(array, index, arrayInterop, meta, exceptionProfile);
            try {
                return elemInterop.asDouble(result);
            } catch (UnsupportedMessageException e) {
                exceptionProfile.enter();
                throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, "Could not cast the foreign array element to double");
            }
        }
    }
}
