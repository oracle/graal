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
 * FALOAD bytecode with interop extensions.
 *
 * <h3>Extensions</h3>
 * <ul>
 * <li>For {@link InteropLibrary#hasArrayElements(Object) array-like} foreign objects, FALOAD is
 * mapped to {@link InteropLibrary#readArrayElement(Object, long)}.</li>
 * </ul>
 *
 * <h3>Exceptions</h3>
 * <ul>
 * <li>Throws guest {@link ArrayIndexOutOfBoundsException} if the index is out of bounds.</li>
 * <li>Throws guest {@link ClassCastException} if the result cannot be converted to float.</li>
 * </ul>
 */
@GenerateUncached
@NodeInfo(shortName = "FALOAD")
public abstract class FloatArrayLoad extends Node {

    public abstract float execute(StaticObject receiver, int index);

    @Specialization
    float executeWithNullCheck(StaticObject array, int index,
                    @Cached NullCheck nullCheck,
                    @Cached WithoutNullCheck floatArrayLoad) {
        return floatArrayLoad.execute(nullCheck.execute(array), index);
    }

    @GenerateUncached
    @NodeInfo(shortName = "FALOAD !nullcheck")
    public abstract static class WithoutNullCheck extends Node {

        protected static final int LIMIT = 2;

        public abstract float execute(StaticObject receiver, int index);

        protected EspressoContext getContext() {
            return EspressoContext.get(this);
        }

        @Specialization(guards = "array.isEspressoObject()")
        float doEspresso(StaticObject array, int index) {
            assert !StaticObject.isNull(array);
            return getContext().getInterpreterToVM().getArrayFloat(index, array);
        }

        @Specialization(guards = "array.isForeignObject()")
        float doArrayLike(StaticObject array, int index,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary arrayInterop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary elemInterop,
                        @Cached BranchProfile exceptionProfile) {
            assert !StaticObject.isNull(array);
            Meta meta = getContext().getMeta();
            Object result = ForeignArrayUtils.readForeignArrayElement(array, index, arrayInterop, meta, exceptionProfile);
            try {
                return elemInterop.asFloat(result);
            } catch (UnsupportedMessageException e) {
                exceptionProfile.enter();
                throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, "Could not cast the foreign array element to float");
            }
        }
    }
}
