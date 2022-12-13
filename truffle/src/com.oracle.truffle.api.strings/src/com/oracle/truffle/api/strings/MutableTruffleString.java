/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.strings;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import static com.oracle.truffle.api.strings.TStringGuards.isStride0;
import static com.oracle.truffle.api.strings.TStringGuards.isStride1;
import static com.oracle.truffle.api.strings.TStringGuards.isStride2;
import static com.oracle.truffle.api.strings.TStringGuards.isUTF16;
import static com.oracle.truffle.api.strings.TStringGuards.isUTF32;
import static com.oracle.truffle.api.strings.TStringGuards.isUTF8;

import java.util.Arrays;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GeneratePackagePrivate;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.strings.TruffleString.AsTruffleStringNode;
import com.oracle.truffle.api.strings.TruffleString.Encoding;

/**
 * Represents a mutable variant of a {@link TruffleString}. This class also accepts all operations
 * of TruffleString. This class is not thread-safe and allows overwriting bytes in its internal byte
 * array or native pointer via {@link WriteByteNode}. The internal array or native pointer may also
 * be modified externally, but the corresponding MutableTruffleString must be notified of this via
 * {@link #notifyExternalMutation()}. MutableTruffleString is not a Truffle interop type, and must
 * be converted to an immutable {@link TruffleString} via {@link AsTruffleStringNode} before passing
 * a language boundary.
 *
 * @see TruffleString
 * @since 22.1
 */
public final class MutableTruffleString extends AbstractTruffleString {

    private int codePointLength = -1;
    private byte codeRange = (byte) TSCodeRange.getUnknown();

    private MutableTruffleString(Object data, int offset, int length, int stride, Encoding encoding) {
        super(data, offset, length, stride, encoding, 0);
        assert data instanceof byte[] || data instanceof NativePointer;
        if (Encoding.isFixedWidth(encoding)) {
            codePointLength = encoding.isSupported() ? length : length / JCodings.getInstance().minLength(encoding.jCoding);
        }
    }

    private static MutableTruffleString create(Object data, int offset, int length, Encoding encoding) {
        MutableTruffleString string = new MutableTruffleString(data, offset, length, encoding.naturalStride, encoding);
        if (AbstractTruffleString.DEBUG_ALWAYS_CREATE_JAVA_STRING) {
            string.toJavaStringUncached();
        }
        return string;
    }

    int codePointLength() {
        return codePointLength;
    }

    int codeRange() {
        return codeRange;
    }

    void updateCachedAttributes(int newCodePointLength, int newCodeRange) {
        assert newCodePointLength >= 0;
        assert TSCodeRange.isCodeRange(newCodeRange);
        this.codePointLength = newCodePointLength;
        this.codeRange = (byte) newCodeRange;
    }

    void invalidateCachedAttributes() {
        if (!Encoding.isFixedWidth(encoding())) {
            codePointLength = -1;
        }
        codeRange = (byte) TSCodeRange.getUnknown();
        hashCode = 0;
        if (data() instanceof NativePointer) {
            ((NativePointer) data()).invalidateCachedByteArray();
        }
    }

    /**
     * Notify this mutable string of an external modification of its internal content. This method
     * must be called after every direct write (not via {@link WriteByteNode}) to the byte array or
     * native pointer the string is using as internal storage. Exemplary usage scenario: Suppose a
     * {@link MutableTruffleString} was created by wrapping a native pointer via
     * {@link FromNativePointerNode}. If the native pointer is passed to a native function that may
     * modify the pointer's contents, this method must be called afterwards, to ensure consistency.
     *
     * @since 22.1
     */
    public void notifyExternalMutation() {
        invalidateCachedAttributes();
    }

    /**
     * Node to create a new {@link MutableTruffleString} from a byte array. See
     * {@link #execute(byte[], int, int, TruffleString.Encoding, boolean)} for details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class FromByteArrayNode extends Node {

        FromByteArrayNode() {
        }

        /**
         * Creates a new {@link MutableTruffleString} from a byte array. The array content is
         * assumed to be encoded in the given encoding already. This operation allows non-copying
         * string creation, i.e. the array parameter can be used directly by passing
         * {@code copy = false}. If the array is modified after non-copying string creation, the
         * string must be notified of this via {@link MutableTruffleString#notifyExternalMutation()}
         * .
         *
         * @since 22.1
         */
        public abstract MutableTruffleString execute(byte[] value, int byteOffset, int byteLength, Encoding encoding, boolean copy);

        @Specialization
        static MutableTruffleString fromByteArray(byte[] value, int byteOffset, int byteLength, Encoding enc, boolean copy) {
            checkArrayRange(value, byteOffset, byteLength);
            checkByteLength(byteLength, enc);
            final byte[] array;
            final int offset;
            if (copy) {
                array = Arrays.copyOfRange(value, byteOffset, byteOffset + byteLength);
                offset = 0;
            } else {
                array = value;
                offset = byteOffset;
            }
            return MutableTruffleString.create(array, offset, byteLength >> enc.naturalStride, enc);
        }

        /**
         * Create a new {@link FromByteArrayNode}.
         *
         * @since 22.1
         */
        public static FromByteArrayNode create() {
            return MutableTruffleStringFactory.FromByteArrayNodeGen.create();
        }

        /**
         * Get the uncached version of {@link FromByteArrayNode}.
         *
         * @since 22.1
         */
        public static FromByteArrayNode getUncached() {
            return MutableTruffleStringFactory.FromByteArrayNodeGen.getUncached();
        }
    }

    /**
     * Shorthand for calling the uncached version of {@link FromByteArrayNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public static MutableTruffleString fromByteArrayUncached(byte[] value, int byteOffset, int byteLength, Encoding encoding, boolean copy) {
        return FromByteArrayNode.getUncached().execute(value, byteOffset, byteLength, encoding, copy);
    }

    /**
     * Node to create a new {@link MutableTruffleString} from an interop object representing a
     * native pointer. See {@link #execute(Object, int, int, TruffleString.Encoding, boolean)} for
     * details.
     *
     * @since 22.1
     */
    @ImportStatic({TStringGuards.class, TStringAccessor.class})
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class FromNativePointerNode extends Node {

        FromNativePointerNode() {
        }

        /**
         * Create a new {@link MutableTruffleString} from an interop object representing a native
         * pointer ({@code isPointer(pointerObject)} must return {@code true}). The pointer is
         * immediately unboxed with ({@code asPointer(pointerObject)}) and saved until the end of
         * the string's lifetime, i.e. {@link MutableTruffleString} assumes that the pointer address
         * does not change. The pointer's content is assumed to be encoded in the given encoding
         * already. If {@code copy} is {@code false}, the native pointer is used directly as the new
         * string's backing storage. Caution: If the pointer's content is modified after string
         * creation, the string must be notified of this via
         * {@link MutableTruffleString#notifyExternalMutation()}.
         *
         * <p>
         * <b>WARNING:</b> {@link MutableTruffleString} cannot reason about the lifetime of the
         * native pointer, so it is up to the user to <b>make sure that the native pointer is valid
         * to access and not freed as long the {@code pointerObject} is alive</b> (if {@code copy}
         * is {@code false}). To help with this the MutableTruffleString keeps a reference to the
         * given {@code pointerObject}, so the {@code pointerObject} is kept alive at least as long
         * as the MutableTruffleString is used. In order to be able to use the string past the
         * native pointer's life time, convert it to a managed string via
         * {@link MutableTruffleString.AsManagedNode} <b>before the native pointer is freed</b>.
         * </p>
         * <p>
         * If {@code copy} is {@code true}, the pointer's contents are copied to a Java byte array,
         * and the pointer can be freed safely after the operation completes.
         * </p>
         * This operation requires native access permissions
         * ({@code TruffleLanguage.Env#isNativeAccessAllowed()}).
         *
         * @since 22.1
         */
        public abstract MutableTruffleString execute(Object pointerObject, int byteOffset, int byteLength, Encoding encoding, boolean copy);

        @Specialization
        MutableTruffleString fromNativePointer(Object pointerObject, int byteOffset, int byteLength, Encoding enc, boolean copy,
                        @Cached(value = "createInteropLibrary()", uncached = "getUncachedInteropLibrary()") Node interopLibrary) {
            checkByteLength(byteLength, enc);
            NativePointer nativePointer = NativePointer.create(this, pointerObject, interopLibrary);
            final Object array;
            final int offset;
            if (copy) {
                array = TStringOps.arraycopyOfWithStride(this, nativePointer, byteOffset, byteLength, 0, byteLength, 0);
                offset = 0;
            } else {
                array = nativePointer;
                offset = byteOffset;
            }
            return MutableTruffleString.create(array, offset, byteLength >> enc.naturalStride, enc);
        }

        /**
         * Create a new {@link FromNativePointerNode}.
         *
         * @since 22.1
         */
        public static FromNativePointerNode create() {
            return MutableTruffleStringFactory.FromNativePointerNodeGen.create();
        }

        /**
         * Get the uncached version of {@link FromNativePointerNode}.
         *
         * @since 22.1
         */
        public static FromNativePointerNode getUncached() {
            return MutableTruffleStringFactory.FromNativePointerNodeGen.getUncached();
        }
    }

    /**
     * Shorthand for calling the uncached version of {@link FromNativePointerNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public static MutableTruffleString fromNativePointerUncached(Object pointerObject, int byteOffset, int byteLength, Encoding encoding, boolean copy) {
        return FromNativePointerNode.getUncached().execute(pointerObject, byteOffset, byteLength, encoding, copy);
    }

    /**
     * Node to get a {@link AbstractTruffleString} as a {@link MutableTruffleString}. See
     * {@link #execute(AbstractTruffleString, TruffleString.Encoding)} for details.
     *
     * @since 22.1
     */
    @ImportStatic({TStringGuards.class, TStringAccessor.class})
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class AsMutableTruffleStringNode extends Node {

        AsMutableTruffleStringNode() {
        }

        /**
         * If the given string is already a {@link MutableTruffleString}, return it. If it is a
         * {@link TruffleString}, create a new {@link MutableTruffleString}, copying the immutable
         * string's contents.
         *
         * @since 22.1
         */
        public abstract MutableTruffleString execute(AbstractTruffleString a, Encoding expectedEncoding);

        @Specialization
        static MutableTruffleString mutable(MutableTruffleString a, Encoding expectedEncoding) {
            a.checkEncoding(expectedEncoding);
            return a;
        }

        @Specialization
        static MutableTruffleString fromTruffleString(TruffleString a, Encoding expectedEncoding,
                        @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            return createCopying(a, expectedEncoding, copyToByteArrayNode);
        }

        /**
         * Create a new {@link AsMutableTruffleStringNode}.
         *
         * @since 22.1
         */
        public static AsMutableTruffleStringNode create() {
            return MutableTruffleStringFactory.AsMutableTruffleStringNodeGen.create();
        }

        /**
         * Get the uncached version of {@link AsMutableTruffleStringNode}.
         *
         * @since 22.1
         */
        public static AsMutableTruffleStringNode getUncached() {
            return MutableTruffleStringFactory.AsMutableTruffleStringNodeGen.getUncached();
        }
    }

    /**
     * Node to get the given {@link AbstractTruffleString} as a managed
     * {@link MutableTruffleString}, meaning that the resulting string's backing memory is not a
     * native pointer. See {@link #execute(AbstractTruffleString, TruffleString.Encoding)} for
     * details.
     *
     * @since 22.1
     */
    @ImportStatic({TStringGuards.class, TStringAccessor.class})
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class AsManagedNode extends Node {

        AsManagedNode() {
        }

        /**
         * If the given string is already a managed (i.e. not backed by a native pointer) string,
         * return it. Otherwise, copy the string's native pointer content into a Java byte array and
         * return a new string backed by the byte array.
         *
         * @since 22.1
         */
        public abstract MutableTruffleString execute(AbstractTruffleString a, Encoding expectedEncoding);

        @Specialization(guards = "!a.isNative()")
        static MutableTruffleString mutable(MutableTruffleString a, Encoding expectedEncoding) {
            a.checkEncoding(expectedEncoding);
            return a;
        }

        @Specialization(guards = "a.isNative() || a.isImmutable()")
        static MutableTruffleString fromTruffleString(AbstractTruffleString a, Encoding expectedEncoding,
                        @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            return createCopying(a, expectedEncoding, copyToByteArrayNode);
        }

        /**
         * Create a new {@link AsManagedNode}.
         *
         * @since 22.1
         */
        public static AsManagedNode create() {
            return MutableTruffleStringFactory.AsManagedNodeGen.create();
        }

        /**
         * Get the uncached version of {@link AsManagedNode}.
         *
         * @since 22.1
         */
        public static AsManagedNode getUncached() {
            return MutableTruffleStringFactory.AsManagedNodeGen.getUncached();
        }
    }

    /**
     * Node to write a byte into a mutable string.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class WriteByteNode extends Node {

        WriteByteNode() {
        }

        /**
         * Writes a byte into the given mutable string.
         *
         * @since 22.1
         */
        public abstract void execute(MutableTruffleString a, int byteIndex, byte value, Encoding expectedEncoding);

        @Specialization
        static void writeByte(MutableTruffleString a, int byteIndex, byte value, Encoding expectedEncoding) {
            a.checkEncoding(expectedEncoding);
            int byteLength = a.length() << a.stride();
            TruffleString.boundsCheckI(byteIndex, byteLength);
            TStringOps.writeS0(a.data(), a.offset(), byteLength, byteIndex, value);
            if (!(TSCodeRange.is7Bit(a.codeRange) && value >= 0)) {
                a.invalidateCachedAttributes();
            }
        }

        /**
         * Create a new {@link WriteByteNode}.
         *
         * @since 22.1
         */
        public static WriteByteNode create() {
            return MutableTruffleStringFactory.WriteByteNodeGen.create();
        }

        /**
         * Get the uncached version of {@link WriteByteNode}.
         *
         * @since 22.1
         */
        public static WriteByteNode getUncached() {
            return MutableTruffleStringFactory.WriteByteNodeGen.getUncached();
        }
    }

    /**
     * Shorthand for calling the uncached version of {@link WriteByteNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public void writeByteUncached(int byteIndex, byte value, Encoding expectedEncoding) {
        WriteByteNode.getUncached().execute(this, byteIndex, value, expectedEncoding);
    }

    /**
     * Node to create a new {@link MutableTruffleString} by concatenating two strings.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class ConcatNode extends Node {

        ConcatNode() {
        }

        /**
         * Creates a new {@link MutableTruffleString} by concatenating two strings. The
         * concatenation is performed eagerly since return value is mutable.
         *
         * @since 22.1
         */
        public abstract MutableTruffleString execute(AbstractTruffleString a, AbstractTruffleString b, Encoding expectedEncoding);

        @Specialization
        static MutableTruffleString concat(AbstractTruffleString a, AbstractTruffleString b, Encoding expectedEncoding,
                        @Cached TruffleString.ToIndexableNode toIndexableNodeA,
                        @Cached TruffleString.ToIndexableNode toIndexableNodeB,
                        @Cached TStringInternalNodes.ConcatMaterializeBytesNode materializeBytesNode,
                        @Cached BranchProfile outOfMemoryProfile) {
            a.checkEncoding(expectedEncoding);
            b.checkEncoding(expectedEncoding);
            int length = TruffleString.ConcatNode.addByteLengths(a, b, expectedEncoding.naturalStride, outOfMemoryProfile);
            int offset = 0;
            byte[] array = materializeBytesNode.execute(a, toIndexableNodeA.execute(a, a.data()), b, toIndexableNodeB.execute(b, b.data()), expectedEncoding, length,
                            expectedEncoding.naturalStride);
            return MutableTruffleString.create(array, offset, length, expectedEncoding);
        }

        /**
         * Create a new {@link ConcatNode}.
         *
         * @since 22.1
         */
        public static ConcatNode create() {
            return MutableTruffleStringFactory.ConcatNodeGen.create();
        }

        /**
         * Get the uncached version of {@link ConcatNode}.
         *
         * @since 22.1
         */
        public static ConcatNode getUncached() {
            return MutableTruffleStringFactory.ConcatNodeGen.getUncached();
        }
    }

    /**
     * Shorthand for calling the uncached version of {@link ConcatNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public MutableTruffleString concatUncached(AbstractTruffleString b, Encoding expectedEncoding) {
        return ConcatNode.getUncached().execute(this, b, expectedEncoding);
    }

    /**
     * Node to create a new mutable substring of a string. See
     * {@link #execute(AbstractTruffleString, int, int, TruffleString.Encoding)} for details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class SubstringNode extends Node {

        SubstringNode() {
        }

        /**
         * Create a new mutable substring of {@code a}, starting from {@code fromIndex}, with length
         * {@code length}. The substring is performed eagerly since return value is mutable.
         *
         * @since 22.1
         */
        public abstract MutableTruffleString execute(AbstractTruffleString a, int fromIndex, int length, Encoding expectedEncoding);

        @Specialization
        static MutableTruffleString substring(AbstractTruffleString a, int fromIndex, int length, Encoding expectedEncoding,
                        @Cached TruffleString.ToIndexableNode toIndexableNode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeANode,
                        @Cached TStringInternalNodes.GetCodePointLengthNode getCodePointLengthNode,
                        @Cached TStringInternalNodes.CodePointIndexToRawNode translateIndexNode,
                        @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            a.checkEncoding(expectedEncoding);
            a.boundsCheckRegion(fromIndex, length, getCodePointLengthNode);
            Object arrayA = toIndexableNode.execute(a, a.data());
            final int codeRangeA = getCodeRangeANode.execute(a);
            int fromIndexRaw = translateIndexNode.execute(a, arrayA, codeRangeA, expectedEncoding, 0, fromIndex, length == 0);
            int lengthRaw = translateIndexNode.execute(a, arrayA, codeRangeA, expectedEncoding, fromIndexRaw, length, true);
            int stride = expectedEncoding.naturalStride;
            return SubstringByteIndexNode.createSubstring(a, fromIndexRaw << stride, lengthRaw << stride, expectedEncoding, copyToByteArrayNode);
        }

        /**
         * Create a new {@link SubstringNode}.
         *
         * @since 22.1
         */
        public static SubstringNode create() {
            return MutableTruffleStringFactory.SubstringNodeGen.create();
        }

        /**
         * Get the uncached version of {@link SubstringNode}.
         *
         * @since 22.1
         */
        public static SubstringNode getUncached() {
            return MutableTruffleStringFactory.SubstringNodeGen.getUncached();
        }
    }

    /**
     * Shorthand for calling the uncached version of {@link SubstringNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public MutableTruffleString substringUncached(int byteOffset, int byteLength, Encoding expectedEncoding) {
        return SubstringNode.getUncached().execute(this, byteOffset, byteLength, expectedEncoding);
    }

    /**
     * {@link SubstringNode}, but with byte indices.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class SubstringByteIndexNode extends Node {

        SubstringByteIndexNode() {
        }

        /**
         * {@link SubstringNode}, but with byte indices.
         *
         * @since 22.1
         */
        public abstract MutableTruffleString execute(AbstractTruffleString a, int byteOffset, int byteLength, Encoding expectedEncoding);

        @Specialization
        static MutableTruffleString substringByteIndex(AbstractTruffleString a, int byteOffset, int byteLength, Encoding expectedEncoding,
                        @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            return createSubstring(a, byteOffset, byteLength, expectedEncoding, copyToByteArrayNode);
        }

        static MutableTruffleString createSubstring(AbstractTruffleString a, int byteOffset, int byteLength, Encoding expectedEncoding,
                        TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            a.checkEncoding(expectedEncoding);
            checkByteLength(byteLength, expectedEncoding);
            a.boundsCheckRegionRaw(rawIndex(byteOffset, expectedEncoding), rawIndex(byteLength, expectedEncoding));
            final byte[] array = new byte[byteLength];
            copyToByteArrayNode.execute(a, byteOffset, array, 0, byteLength, expectedEncoding);
            return MutableTruffleString.create(array, 0, byteLength >> expectedEncoding.naturalStride, expectedEncoding);
        }

        /**
         * Create a new {@link SubstringByteIndexNode}.
         *
         * @since 22.1
         */
        public static SubstringByteIndexNode create() {
            return MutableTruffleStringFactory.SubstringByteIndexNodeGen.create();
        }

        /**
         * Get the uncached version of {@link SubstringByteIndexNode}.
         *
         * @since 22.1
         */
        public static SubstringByteIndexNode getUncached() {
            return MutableTruffleStringFactory.SubstringByteIndexNodeGen.getUncached();
        }
    }

    /**
     * Shorthand for calling the uncached version of {@link SubstringByteIndexNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public MutableTruffleString substringByteIndexUncached(int byteOffset, int byteLength, Encoding expectedEncoding) {
        return SubstringByteIndexNode.getUncached().execute(this, byteOffset, byteLength, expectedEncoding);
    }

    /**
     * Node to get a given string in a specific encoding. See
     * {@link #execute(AbstractTruffleString, TruffleString.Encoding)} for details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class SwitchEncodingNode extends Node {

        SwitchEncodingNode() {
        }

        /**
         * Returns a version of string {@code a} that is encoded in the given encoding, which may be
         * the string itself or a converted version.
         * <p>
         * If no lossless conversion is possible, the string is converted on a best-effort basis; no
         * exception is thrown and characters which cannot be mapped in the target encoding are
         * replaced by {@code '\ufffd'} (for UTF-*) or {@code '?'}.
         *
         * @since 22.1
         */
        public abstract MutableTruffleString execute(AbstractTruffleString a, Encoding encoding);

        @Specialization(guards = "a.isCompatibleTo(encoding)")
        static MutableTruffleString compatibleMutable(MutableTruffleString a, @SuppressWarnings("unused") Encoding encoding) {
            return a;
        }

        @Specialization(guards = "!a.isCompatibleTo(encoding) || a.isImmutable()")
        static MutableTruffleString transcodeAndCopy(AbstractTruffleString a, Encoding encoding,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached AsMutableTruffleStringNode asMutableTruffleStringNode) {
            TruffleString switched = switchEncodingNode.execute(a, encoding);
            return asMutableTruffleStringNode.execute(switched, encoding);
        }

        /**
         * Create a new {@link MutableTruffleString.SwitchEncodingNode}.
         *
         * @since 22.1
         */
        public static MutableTruffleString.SwitchEncodingNode create() {
            return MutableTruffleStringFactory.SwitchEncodingNodeGen.create();
        }

        /**
         * Get the uncached version of {@link MutableTruffleString.SwitchEncodingNode}.
         *
         * @since 22.1
         */
        public static MutableTruffleString.SwitchEncodingNode getUncached() {
            return MutableTruffleStringFactory.SwitchEncodingNodeGen.getUncached();
        }
    }

    /**
     * Node to forcibly assign any encoding to a string. See
     * {@link #execute(AbstractTruffleString, TruffleString.Encoding, TruffleString.Encoding)} for
     * details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class ForceEncodingNode extends Node {

        ForceEncodingNode() {
        }

        /**
         * Returns a version of string {@code a} assigned to the given encoding. If the string is
         * already in the given encoding, it is returned. Otherwise, a new string containing the
         * same (copied) bytes but assigned to the new encoding is returned. <b>This node does not
         * transcode the string's contents in any way, it is the "encoding-equivalent" to a C-style
         * reinterpret-cast.</b>
         *
         * @since 22.1
         */
        public abstract MutableTruffleString execute(AbstractTruffleString a, Encoding expectedEncoding, Encoding targetEncoding);

        @Specialization(guards = "a.isCompatibleTo(targetEncoding)")
        static MutableTruffleString compatible(MutableTruffleString a, Encoding expectedEncoding, @SuppressWarnings("unused") Encoding targetEncoding) {
            a.checkEncoding(expectedEncoding);
            return a;
        }

        @Specialization(guards = "!a.isCompatibleTo(targetEncoding) || a.isImmutable()")
        static MutableTruffleString reinterpret(AbstractTruffleString a, Encoding expectedEncoding, Encoding targetEncoding,
                        @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            a.checkEncoding(expectedEncoding);
            return createCopying(a, expectedEncoding, targetEncoding, copyToByteArrayNode);
        }

        /**
         * Create a new {@link MutableTruffleString.ForceEncodingNode}.
         *
         * @since 22.1
         */
        public static MutableTruffleString.ForceEncodingNode create() {
            return MutableTruffleStringFactory.ForceEncodingNodeGen.create();
        }

        /**
         * Get the uncached version of {@link MutableTruffleString.ForceEncodingNode}.
         *
         * @since 22.1
         */
        public static MutableTruffleString.ForceEncodingNode getUncached() {
            return MutableTruffleStringFactory.ForceEncodingNodeGen.getUncached();
        }
    }

    @GenerateUncached
    abstract static class CalcLazyAttributesNode extends Node {

        abstract void execute(MutableTruffleString a);

        @Specialization
        void calc(MutableTruffleString a,
                        @Cached("createClassProfile()") ValueProfile dataClassProfile,
                        @Cached ConditionProfile asciiBytesLatinProfile,
                        @Cached ConditionProfile utf8Profile,
                        @Cached ConditionProfile utf8BrokenProfile,
                        @Cached ConditionProfile utf16Profile,
                        @Cached ConditionProfile utf16S0Profile,
                        @Cached ConditionProfile utf32Profile,
                        @Cached ConditionProfile utf32S0Profile,
                        @Cached ConditionProfile utf32S1Profile,
                        @Cached ConditionProfile exoticMaterializeNativeProfile,
                        @Cached ConditionProfile exoticValidProfile,
                        @Cached ConditionProfile exoticFixedWidthProfile) {
            final Object data = dataClassProfile.profile(a.data());
            final int encoding = a.encoding();
            final int offset = a.offset();
            final int length = a.length();
            final int codePointLength;
            final int codeRange;
            if (utf16Profile.profile(isUTF16(encoding))) {
                if (utf16S0Profile.profile(isStride0(a))) {
                    codeRange = TStringOps.calcStringAttributesLatin1(this, data, offset, length);
                    codePointLength = length;
                } else {
                    assert isStride1(a);
                    long attrs = TStringOps.calcStringAttributesUTF16(this, data, offset, length, false);
                    codePointLength = StringAttributes.getCodePointLength(attrs);
                    codeRange = StringAttributes.getCodeRange(attrs);
                }
            } else if (utf32Profile.profile(isUTF32(encoding))) {
                if (utf32S0Profile.profile(isStride0(a))) {
                    codeRange = TStringOps.calcStringAttributesLatin1(this, data, offset, length);
                } else if (utf32S1Profile.profile(isStride1(a))) {
                    codeRange = TStringOps.calcStringAttributesBMP(this, data, offset, length);
                } else {
                    assert isStride2(a);
                    codeRange = TStringOps.calcStringAttributesUTF32(this, data, offset, length);
                }
                codePointLength = length;
            } else {
                if (utf8Profile.profile(isUTF8(encoding))) {
                    long attrs = TStringOps.calcStringAttributesUTF8(this, data, offset, length, false, false, utf8BrokenProfile);
                    codeRange = StringAttributes.getCodeRange(attrs);
                    codePointLength = StringAttributes.getCodePointLength(attrs);
                } else if (asciiBytesLatinProfile.profile(TStringGuards.isAsciiBytesOrLatin1(encoding))) {
                    int cr = TStringOps.calcStringAttributesLatin1(this, data, offset, length);
                    codeRange = TStringGuards.is8Bit(cr) ? TSCodeRange.asciiLatinBytesNonAsciiCodeRange(encoding) : cr;
                    codePointLength = length;
                } else {
                    if (data instanceof NativePointer) {
                        ((NativePointer) data).materializeByteArray(a, exoticMaterializeNativeProfile);
                    }
                    long attrs = JCodings.getInstance().calcStringAttributes(this, data, offset, length, Encoding.get(encoding), 0, exoticValidProfile, exoticFixedWidthProfile);
                    codeRange = StringAttributes.getCodeRange(attrs);
                    codePointLength = StringAttributes.getCodePointLength(attrs);
                }
            }
            a.updateCachedAttributes(codePointLength, codeRange);
        }
    }

    static MutableTruffleString createCopying(AbstractTruffleString a, Encoding encoding,
                    TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
        return createCopying(a, encoding, encoding, a.byteLength(encoding), copyToByteArrayNode);
    }

    static MutableTruffleString createCopying(AbstractTruffleString a, Encoding expectedEncoding, Encoding targetEncoding,
                    TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
        int byteLength = a.byteLength(expectedEncoding);
        checkByteLength(byteLength, targetEncoding);
        return createCopying(a, expectedEncoding, targetEncoding, byteLength, copyToByteArrayNode);
    }

    static MutableTruffleString createCopying(AbstractTruffleString a, Encoding expectedEncoding, Encoding targetEncoding, int byteLength,
                    TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
        final byte[] array = new byte[byteLength];
        copyToByteArrayNode.execute(a, 0, array, 0, byteLength, expectedEncoding);
        return MutableTruffleString.create(array, 0, byteLength >> targetEncoding.naturalStride, targetEncoding);
    }
}
