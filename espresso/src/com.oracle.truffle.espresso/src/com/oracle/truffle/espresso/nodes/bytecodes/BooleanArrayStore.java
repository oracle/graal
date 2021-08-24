package com.oracle.truffle.espresso.nodes.bytecodes;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.nodes.quick.interop.ForeignArrayUtils;
import com.oracle.truffle.espresso.nodes.quick.interop.Utils;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * BASTORE bytecode with interop extensions that only supports boolean[].
 *
 * <h3>Extensions</h3>
 * <ul>
 * <li>For Truffle buffers ({@link InteropLibrary#hasBufferElements(Object) buffer-like} foreign
 * objects) wrapped as {@code byte[]}, BASTORE is mapped to
 * {@link InteropLibrary#writeBufferByte(Object, long, byte)}.</li>
 * <li>For {@link InteropLibrary#hasArrayElements(Object) array-like} foreign objects, BASTORE is
 * mapped to {@link InteropLibrary#writeArrayElement(Object, long, Object)}.</li>
 * </ul>
 *
 * <h3>Exceptions</h3>
 * <ul>
 * <li>Throws guest {@link ArrayIndexOutOfBoundsException} if the index is out of bounds.</li>
 * <li>Throws guest {@link ClassCastException} if the read value cannot be converted to
 * boolean.</li>
 * <li>Throws guest {@link ArrayStoreException} if the underlying interop array/buffer is
 * read-only.</li>
 * </ul>
 */
@GenerateUncached
@NodeInfo(shortName = "boolean[] BASTORE")
public abstract class BooleanArrayStore extends Node {

    public abstract void execute(StaticObject receiver, int index, byte value);

    @Specialization
    void executeWithNullCheck(StaticObject array, int index, byte value,
                    @Cached NullCheck nullCheck,
                    @Cached ByteArrayStore.WithoutNullCheck byteArrayStore) {
        byteArrayStore.execute(nullCheck.execute(array), index, value);
    }

    @GenerateUncached
    @ImportStatic(Utils.class)
    @NodeInfo(shortName = "boolean[] BASTORE !nullcheck")
    public abstract static class WithoutNullCheck extends Node {
        static final int LIMIT = 2;

        public abstract void execute(StaticObject receiver, int index, byte value);

        protected EspressoContext getContext() {
            return EspressoContext.get(this);
        }

        @Specialization(guards = "array.isEspressoObject()")
        void doEspresso(StaticObject array, int index, byte value) {
            assert !StaticObject.isNull(array);
            assert array.getKlass() == EspressoContext.get(this).getMeta()._boolean_array;
            getContext().getInterpreterToVM().setArrayByte(value, index, array);
        }

        @Specialization(guards = {
                        "array.isForeignObject()",
                        "isArrayLike(interop, array.rawForeignObject())"
        })
        void doArrayLike(StaticObject array, int index, byte value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getContext()") EspressoContext context,
                        @Cached BranchProfile exceptionProfile) {
            assert !StaticObject.isNull(array);
            assert array.getKlass() == context.getMeta()._boolean_array;
            boolean booleanValue = value != 0;
            ForeignArrayUtils.writeForeignArrayElement(array, index, booleanValue, interop, context.getMeta(), exceptionProfile);
        }
    }
}
