package com.oracle.truffle.espresso.nodes.bytecodes;

import com.oracle.truffle.api.CompilerDirectives;
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
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.quick.interop.ForeignArrayUtils;
import com.oracle.truffle.espresso.nodes.quick.interop.Utils;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * BALOAD bytecode with interop extensions. Similar to the BALOAD, supports both byte[] and
 * boolean[].
 *
 * <h3>Extensions</h3>
 * <ul>
 * <li>For Truffle buffers ({@link InteropLibrary#hasBufferElements(Object) buffer-like} foreign
 * objects) wrapped as {@code byte[]}, BALOAD is mapped to
 * {@link InteropLibrary#readBufferByte(Object, long)}.</li>
 * <li>For {@link InteropLibrary#hasArrayElements(Object) array-like} foreign objects, BALOAD is
 * mapped to {@link InteropLibrary#readArrayElement(Object, long)}.</li>
 * </ul>
 *
 * <h3>Exceptions</h3>
 * <ul>
 * <li>Throws guest {@link ArrayIndexOutOfBoundsException} if the index is out of bounds.</li>
 * <li>Throws guest {@link ClassCastException} if the result cannot be converted to
 * byte/boolean.</li>
 * </ul>
 *
 * @see BooleanArrayLoad
 */
@GenerateUncached
@NodeInfo(shortName = "BALOAD")
public abstract class ByteArrayLoad extends Node {

    public abstract byte execute(StaticObject receiver, int index);

    @Specialization
    byte executeWithNullCheck(StaticObject array, int index,
                    @Cached NullCheck nullCheck,
                    @Cached WithoutNullCheck byteArrayLoad) {
        return byteArrayLoad.execute(nullCheck.execute(array), index);
    }

    @GenerateUncached
    @ImportStatic(Utils.class)
    @NodeInfo(shortName = "BALOAD !nullcheck")
    public abstract static class WithoutNullCheck extends Node {

        protected static final int LIMIT = 2;

        public abstract byte execute(StaticObject receiver, int index);

        protected EspressoContext getContext() {
            return EspressoContext.get(this);
        }

        @Specialization(guards = "array.isEspressoObject()")
        byte doEspresso(StaticObject array, int index) {
            assert !StaticObject.isNull(array);
            return getContext().getInterpreterToVM().getArrayByte(index, array);
        }

        @Specialization(guards = {
                        "array.isForeignObject()",
                        "isBufferLikeByteArray(context, interop, array)"
        })
        byte doBufferLike(StaticObject array, int index,
                        @SuppressWarnings("unused") @Bind("getContext()") EspressoContext context,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile outOfBoundsProfile) {
            assert !StaticObject.isNull(array);
            try {
                return interop.readBufferByte(array.rawForeignObject(), index);
            } catch (InvalidBufferOffsetException e) {
                outOfBoundsProfile.enter();
                Meta meta = context.getMeta();
                throw meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, e.getMessage());
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        @Specialization(guards = {
                        "array.isForeignObject()",
                        "!isBufferLikeByteArray(context, arrayInterop, array)",
                        "isArrayLike(arrayInterop, array.rawForeignObject())"
        })
        byte doArrayLike(StaticObject array, int index,
                        @SuppressWarnings("unused") @Bind("getContext()") EspressoContext context,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary arrayInterop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary elemInterop,
                        @Cached BranchProfile exceptionProfile,
                        @Cached ConditionProfile isByteArrayProfile) {
            assert !StaticObject.isNull(array);
            Meta meta = context.getMeta();
            Object result = ForeignArrayUtils.readForeignArrayElement(array, index, arrayInterop, meta, exceptionProfile);

            if (isByteArrayProfile.profile(array.getKlass() == meta._byte_array)) {
                try {
                    return elemInterop.asByte(result);
                } catch (UnsupportedMessageException e) {
                    exceptionProfile.enter();
                    throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, "Could not cast the foreign array element to byte");
                }
            } else {
                assert array.getKlass() == meta._boolean_array;
                try {
                    boolean element = elemInterop.asBoolean(result);
                    return (byte) (element ? 1 : 0);
                } catch (UnsupportedMessageException e) {
                    exceptionProfile.enter();
                    throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, "Could not cast the foreign array element to boolean");
                }
            }
        }
    }
}
