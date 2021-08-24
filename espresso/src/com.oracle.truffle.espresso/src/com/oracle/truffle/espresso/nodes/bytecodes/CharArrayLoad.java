package com.oracle.truffle.espresso.nodes.bytecodes;

import com.oracle.truffle.api.dsl.Bind;
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
 * CALOAD bytecode with interop extensions.
 *
 * <h3>Extensions</h3>
 * <ul>
 * <li>For {@link InteropLibrary#hasArrayElements(Object) array-like} foreign objects, CALOAD is
 * mapped to {@link InteropLibrary#readArrayElement(Object, long)}.</li>
 * </ul>
 *
 * <h3>Exceptions</h3>
 * <ul>
 * <li>Throws guest {@link ArrayIndexOutOfBoundsException} if the index is out of bounds.</li>
 * <li>Throws guest {@link ClassCastException} if the result cannot be converted to char.</li>
 * </ul>
 */
@GenerateUncached
@NodeInfo(shortName = "CALOAD")
public abstract class CharArrayLoad extends Node {

    public abstract char execute(StaticObject receiver, int index);

    @Specialization
    char executeWithNullCheck(StaticObject array, int index,
                    @Cached NullCheck nullCheck,
                    @Cached WithoutNullCheck charArrayLoad) {
        return charArrayLoad.execute(nullCheck.execute(array), index);
    }

    @GenerateUncached
    @NodeInfo(shortName = "CALOAD !nullcheck")
    public abstract static class WithoutNullCheck extends Node {

        static final int LIMIT = 2;

        public abstract char execute(StaticObject array, int index);

        protected EspressoContext getContext() {
            return EspressoContext.get(this);
        }

        @Specialization(guards = "array.isForeignObject()")
        char doForeign(StaticObject array, int index,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary arrayInterop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary elemInterop,
                        @Bind("getContext()") EspressoContext context,
                        @Cached BranchProfile exceptionProfile) {
            assert !StaticObject.isNull(array);
            Meta meta = context.getMeta();
            Object element = ForeignArrayUtils.readForeignArrayElement(array, index, arrayInterop, meta, exceptionProfile);
            try {
                String string1 = elemInterop.asString(element);
                if (string1.length() == 1) {
                    return string1.charAt(0);
                }
            } catch (UnsupportedMessageException e) {
                // fall-through
            }
            exceptionProfile.enter();
            throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, "Could not cast foreign array element to char");
        }

        @Specialization(guards = "array.isEspressoObject()")
        char doEspresso(StaticObject array, int index) {
            assert !StaticObject.isNull(array);
            return getContext().getInterpreterToVM().getArrayChar(index, array);
        }
    }
}
