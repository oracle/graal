package com.oracle.truffle.espresso.nodes.bytecodes;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.interop.ToEspressoNode;
import com.oracle.truffle.espresso.nodes.quick.interop.ForeignArrayUtils;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * AALOAD bytecode with interop extensions.
 *
 * <h3>Extensions</h3>
 * <ul>
 * <li>For {@link InteropLibrary#hasArrayElements(Object) array-like} foreign objects, LALOAD is
 * mapped to {@link InteropLibrary#readArrayElement(Object, long)}.</li>
 * </ul>
 *
 * <h3>Exceptions</h3>
 * <ul>
 * <li>Throws guest {@link ArrayIndexOutOfBoundsException} if the index is out of bounds.</li>
 * <li>Throws guest {@link ClassCastException} if the result cannot be casted to the array component
 * type.</li>
 * </ul>
 */
@GenerateUncached
@NodeInfo(shortName = "AALOAD")
public abstract class ReferenceArrayLoad extends Node {

    public abstract StaticObject execute(StaticObject receiver, int index);

    @Specialization
    StaticObject executeWithNullCheck(StaticObject array, int index,
                    @Cached NullCheck nullCheck,
                    @Cached WithoutNullCheck objectArrayLoad) {
        return objectArrayLoad.execute(nullCheck.execute(array), index);
    }

    @GenerateUncached
    @NodeInfo(shortName = "AALOAD !nullcheck")
    public abstract static class WithoutNullCheck extends Node {

        protected static final int LIMIT = 2;

        public abstract StaticObject execute(StaticObject receiver, int index);

        protected EspressoContext getContext() {
            return EspressoContext.get(this);
        }

        @Specialization(guards = "array.isEspressoObject()")
        StaticObject doEspresso(StaticObject array, int index) {
            assert !StaticObject.isNull(array);
            return getContext().getInterpreterToVM().getArrayObject(index, array);
        }

        @Specialization(guards = "array.isForeignObject()")
        StaticObject doForeign(StaticObject array, int index,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ToEspressoNode toEspressoNode,
                        @Cached BranchProfile exceptionProfile) {
            assert !StaticObject.isNull(array);
            Meta meta = getContext().getMeta();
            Object result = ForeignArrayUtils.readForeignArrayElement(array, index, interop, meta, exceptionProfile);
            ArrayKlass arrayKlass = (ArrayKlass) array.getKlass();
            try {
                return (StaticObject) toEspressoNode.execute(result, arrayKlass.getComponentType());
            } catch (UnsupportedTypeException e) {
                exceptionProfile.enter();
                throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, "Could not cast the foreign array element to the array component type");
            }
        }
    }
}
