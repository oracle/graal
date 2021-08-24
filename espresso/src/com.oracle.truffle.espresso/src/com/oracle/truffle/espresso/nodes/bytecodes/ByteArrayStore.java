package com.oracle.truffle.espresso.nodes.bytecodes;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.quick.interop.ForeignArrayUtils;
import com.oracle.truffle.espresso.nodes.quick.interop.Utils;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * BASTORE bytecode with interop extensions. Similar to the BALOAD, supports both byte[] and
 * boolean[].
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
 * <li>Throws guest {@link ClassCastException} if the result cannot be converted to
 * byte/boolean.</li>
 * <li>Throws guest {@link ArrayStoreException} if the underlying interop array/buffer is
 * read-only.</li>
 * </ul>
 *
 * @see BooleanArrayStore
 */
@GenerateUncached
@NodeInfo(shortName = "BASTORE")
public abstract class ByteArrayStore extends Node {

    public abstract void execute(StaticObject receiver, int index, byte value);

    @Specialization
    void executeWithNullCheck(StaticObject array, int index, byte value,
                    @Cached NullCheck nullCheck,
                    @Cached ByteArrayStore.WithoutNullCheck byteArrayStore) {
        byteArrayStore.execute(nullCheck.execute(array), index, value);
    }

    @GenerateUncached
    @ImportStatic(Utils.class)
    @NodeInfo(shortName = "BASTORE !nullcheck")
    public abstract static class WithoutNullCheck extends Node {
        static final int LIMIT = 2;

        public abstract void execute(StaticObject receiver, int index, byte value);

        protected EspressoContext getContext() {
            return EspressoContext.get(this);
        }

        @Specialization(guards = "array.isEspressoObject()")
        void doEspresso(StaticObject array, int index, byte value) {
            assert !StaticObject.isNull(array);
            getContext().getInterpreterToVM().setArrayByte(value, index, array);
        }

        @Specialization(guards = {
                        "array.isForeignObject()",
                        "isBufferLikeByteArray(context, interop, array)"
        })
        void doBufferLike(StaticObject array, int index, byte value,
                        @Bind("getContext()") EspressoContext context,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile outOfBoundsProfile,
                        @Cached BranchProfile readOnlyProfile) {
            assert !StaticObject.isNull(array);
            try {
                interop.writeBufferByte(array.rawForeignObject(), index, value);
            } catch (InvalidBufferOffsetException e) {
                outOfBoundsProfile.enter();
                Meta meta = context.getMeta();
                throw meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, e.getMessage());
            } catch (UnsupportedMessageException e) {
                // Read-only foreign object.
                readOnlyProfile.enter();
                Meta meta = context.getMeta();
                throw meta.throwExceptionWithMessage(meta.java_lang_ArrayStoreException, e.getMessage());
            }
        }

        @Specialization(guards = {
                        "array.isForeignObject()",
                        "!isBufferLikeByteArray(context, interop, array)",
                        "isArrayLike(interop, array.rawForeignObject())"
        })
        void doArrayLike(StaticObject array, int index, byte value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getContext()") EspressoContext context,
                        @Cached BranchProfile exceptionProfile,
                        @Cached ConditionProfile isByteArrayProfile) {
            assert !StaticObject.isNull(array);
            if (isByteArrayProfile.profile(array.getKlass() == context.getMeta()._byte_array)) {
                ForeignArrayUtils.writeForeignArrayElement(array, index, value, interop, context.getMeta(), exceptionProfile);
            } else {
                assert array.getKlass() == context.getMeta()._boolean_array;
                boolean booleanValue = value != 0;
                ForeignArrayUtils.writeForeignArrayElement(array, index, booleanValue, interop, context.getMeta(), exceptionProfile);
            }
        }
    }
}
