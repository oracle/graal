/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.io.ByteSequence;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.polyglot.PolyglotByteSequenceFactory.CacheFactory.ByteAtNodeGen;
import com.oracle.truffle.polyglot.PolyglotByteSequenceFactory.CacheFactory.LengthNodeGen;
import com.oracle.truffle.polyglot.PolyglotByteSequenceFactory.CacheFactory.SubSequenceNodeGen;
import com.oracle.truffle.polyglot.PolyglotByteSequenceFactory.CacheFactory.ToByteArrayNodeGen;

final class PolyglotByteSequence implements ByteSequence, PolyglotWrapper {

    static final int UNKNOWN_SEQUENCE_LENGTH = -1;
    static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    final Object guestObject;

    private final int start;
    private final int length;
    final PolyglotLanguageContext languageContext;

    final Cache cache;
    /**
     * Strong reference to the creator {@link Context} to prevent it from being garbage collected
     * and closed while this byte sequence is still reachable.
     */
    final Context contextAnchor;

    PolyglotByteSequence(Object buffer, PolyglotLanguageContext languageContext) {
        this(buffer, languageContext, 0, UNKNOWN_SEQUENCE_LENGTH);
    }

    PolyglotByteSequence(Object buffer, PolyglotLanguageContext languageContext, int start, int length) {
        this.guestObject = buffer;
        this.start = start;
        this.length = length;
        this.languageContext = languageContext;
        this.cache = Cache.lookup(languageContext, buffer.getClass());
        this.contextAnchor = languageContext.context.getContextAPI();
    }

    @CompilerDirectives.TruffleBoundary
    public static PolyglotByteSequence create(PolyglotLanguageContext languageContext, Object buffer) {
        return new PolyglotByteSequence(buffer, languageContext);
    }

    @CompilerDirectives.TruffleBoundary
    public static PolyglotByteSequence create(PolyglotLanguageContext languageContext, Object buffer, int st, int len) {
        return new PolyglotByteSequence(buffer, languageContext, st, len);
    }

    @Override
    public String toString() {
        return PolyglotWrapper.toString(this);
    }

    @Override
    public int hashCode() {
        return PolyglotWrapper.hashCode(languageContext, guestObject);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof PolyglotByteSequence) {
            return PolyglotWrapper.equals(languageContext, guestObject, ((PolyglotByteSequence) o).guestObject);
        } else {
            return false;
        }
    }

    @Override
    public int length() {
        return (Integer) cache.length.call(null, languageContext, guestObject, start, length);
    }

    @Override
    public byte byteAt(int index) {
        return (byte) cache.byteAt.call(null, languageContext, guestObject, start, length, index);
    }

    @Override
    public byte[] toByteArray() {
        return (byte[]) cache.toByteArray.call(null, languageContext, guestObject, start, length);
    }

    @Override
    public ByteSequence subSequence(int startIndex, int endIndex) {
        return (ByteSequence) cache.subSequence.call(null, languageContext, guestObject, start, length, startIndex, endIndex);
    }

    @Override
    public Object getGuestObject() {
        return guestObject;
    }

    @Override
    public PolyglotContextImpl getContext() {
        return languageContext.context;
    }

    @Override
    public PolyglotLanguageContext getLanguageContext() {
        return languageContext;
    }

    static final class Cache {

        final PolyglotLanguageInstance languageInstance;

        final Class<?> receiverClass;

        final CallTarget byteAt;
        final CallTarget toByteArray;
        final CallTarget subSequence;
        final CallTarget length;

        Cache(PolyglotLanguageInstance languageInstance, Class<?> receiverClass) {
            this.languageInstance = languageInstance;
            this.receiverClass = receiverClass;
            this.byteAt = ByteAtNodeGen.create(this).getCallTarget();
            this.toByteArray = ToByteArrayNodeGen.create(this).getCallTarget();
            this.subSequence = SubSequenceNodeGen.create(this).getCallTarget();
            this.length = LengthNodeGen.create(this).getCallTarget();
        }

        static Cache lookup(PolyglotLanguageContext languageContext, Class<?> receiverClass) {
            Cache cache = HostToGuestRootNode.lookupHostCodeCache(languageContext, receiverClass, Cache.class);
            if (cache == null) {
                cache = HostToGuestRootNode.installHostCodeCache(languageContext, receiverClass, new Cache(languageContext.getLanguageInstance(), receiverClass), Cache.class);
            }
            assert cache.receiverClass == receiverClass;
            return cache;
        }

        abstract static class PolyglotByteSequenceNode extends HostToGuestRootNode {

            static final int LIMIT = 5;

            final Cache cache;

            PolyglotByteSequenceNode(Cache cache) {
                super(cache.languageInstance);
                this.cache = cache;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected Class<? extends TruffleObject> getReceiverType() {
                return (Class<? extends TruffleObject>) cache.receiverClass;
            }

            @Override
            public final String getName() {
                return "PolyglotByteSequence<" + cache.receiverClass + ">." + getOperationName();
            }

            protected abstract String getOperationName();

        }

        abstract static class LengthNode extends PolyglotByteSequenceNode {

            LengthNode(Cache cache) {
                super(cache);
            }

            @Specialization(limit = "LIMIT")
            @SuppressWarnings("unused")
            static Object doCached(PolyglotLanguageContext languageContext, Object receiver, Object[] args,
                            @Bind Node node,
                            @CachedLibrary("receiver") InteropLibrary interop,
                            @Cached InlinedBranchProfile error) {
                Object length = args[ARGUMENT_OFFSET + 1];
                assert length instanceof Integer;
                int intLength = (int) length;
                if (intLength == UNKNOWN_SEQUENCE_LENGTH) {
                    long size;
                    try {
                        size = interop.getBufferSize(receiver);
                    } catch (UnsupportedMessageException e) {
                        throw CompilerDirectives.shouldNotReachHere(e);
                    }
                    assert size >= 0;
                    assert size <= Integer.MAX_VALUE;
                    return (int) size;
                } else {
                    return intLength;
                }
            }

            @Override
            protected String getOperationName() {
                return "size";
            }

        }

        abstract static class ByteAtNode extends PolyglotByteSequenceNode {

            ByteAtNode(Cache cache) {
                super(cache);
            }

            @Specialization(limit = "LIMIT")
            @SuppressWarnings({"unused"})
            static Object doCached(PolyglotLanguageContext languageContext, Object receiver, Object[] args,
                            @Bind Node node,
                            @CachedLibrary("receiver") InteropLibrary interop,
                            @Cached InlinedBranchProfile error) {
                Object start = args[ARGUMENT_OFFSET];
                assert start instanceof Integer;
                Object length = args[ARGUMENT_OFFSET + 1];
                assert length instanceof Integer;
                Object key = args[ARGUMENT_OFFSET + 2];
                assert key instanceof Integer;
                long startOffset = (int) start;
                int intLength = (int) length;
                int offset = (int) key;
                if (intLength != UNKNOWN_SEQUENCE_LENGTH && offset + Byte.BYTES > intLength) {
                    error.enter(node);
                    throw PolyglotInteropErrors.invalidBufferOffset(languageContext, receiver, offset, Byte.BYTES);
                }
                try {
                    return interop.readBufferByte(receiver, startOffset + offset);
                } catch (InvalidBufferOffsetException e) {
                    error.enter(node);
                    throw PolyglotInteropErrors.invalidBufferOffset(languageContext, receiver, e.getByteOffset() - startOffset, e.getLength());
                } catch (UnsupportedMessageException e) {
                    error.enter(node);
                    throw PolyglotInteropErrors.bufferUnsupported(languageContext, receiver, "byteAt()");
                }
            }

            @Override
            protected String getOperationName() {
                return "byteAt";
            }

        }

        abstract static class ToByteArrayNode extends PolyglotByteSequenceNode {

            ToByteArrayNode(Cache cache) {
                super(cache);
            }

            @Specialization(limit = "LIMIT")
            @SuppressWarnings({"unused"})
            static Object doCached(PolyglotLanguageContext languageContext, Object receiver, Object[] args,
                            @Bind Node node,
                            @CachedLibrary("receiver") InteropLibrary interop,
                            @Cached InlinedBranchProfile error) {
                Object start = args[ARGUMENT_OFFSET];
                assert start instanceof Integer;
                Object length = args[ARGUMENT_OFFSET + 1];
                assert length instanceof Integer;
                long startOffset = (int) start;
                int intLength = (int) length;
                long size;
                if (intLength == UNKNOWN_SEQUENCE_LENGTH) {
                    try {
                        size = interop.getBufferSize(receiver);
                    } catch (UnsupportedMessageException e) {
                        error.enter(node);
                        throw PolyglotInteropErrors.bufferUnsupported(languageContext, receiver, "toByteArray()");
                    }
                } else {
                    size = intLength;
                }
                if (size > MAX_ARRAY_SIZE) {
                    error.enter(node);
                    throw PolyglotInteropErrors.bufferUnsupported(languageContext, receiver, "toByteArray()",
                                    "The buffer size %d is greater than the maximum array size %d", size, MAX_ARRAY_SIZE);
                }
                int intSize = (int) size;
                byte[] outArray = new byte[intSize];
                try {
                    interop.readBuffer(receiver, startOffset, outArray, 0, intSize);
                    return outArray;
                } catch (InvalidBufferOffsetException e) {
                    error.enter(node);
                    throw PolyglotInteropErrors.invalidBufferOffset(languageContext, receiver, e.getByteOffset() - startOffset, e.getLength());
                } catch (UnsupportedMessageException e) {
                    error.enter(node);
                    throw PolyglotInteropErrors.bufferUnsupported(languageContext, receiver, "toByteArray()");
                }
            }

            @Override
            protected String getOperationName() {
                return "toByteArray";
            }

        }

        abstract static class SubSequenceNode extends PolyglotByteSequenceNode {

            SubSequenceNode(Cache cache) {
                super(cache);
            }

            @Specialization(limit = "LIMIT")
            @SuppressWarnings({"unused"})
            static Object doCached(PolyglotLanguageContext languageContext, Object receiver, Object[] args,
                            @Bind Node node,
                            @CachedLibrary("receiver") InteropLibrary interop,
                            @Cached InlinedBranchProfile error) {
                Object start = args[ARGUMENT_OFFSET];
                assert start instanceof Integer;
                Object length = args[ARGUMENT_OFFSET + 1];
                assert length instanceof Integer;
                Object startIndex = args[ARGUMENT_OFFSET + 2];
                assert startIndex instanceof Integer;
                Object endIndex = args[ARGUMENT_OFFSET + 3];
                assert endIndex instanceof Integer;
                int startOffset = (int) start;
                int intLength = (int) length;
                int intStartIndex = (int) startIndex;
                int intEndIndex = (int) endIndex;
                long size;
                if (intLength == UNKNOWN_SEQUENCE_LENGTH) {
                    try {
                        size = interop.getBufferSize(receiver);
                    } catch (UnsupportedMessageException e) {
                        error.enter(node);
                        throw PolyglotInteropErrors.bufferUnsupported(languageContext, receiver, "subSequence()");
                    }
                } else {
                    size = intLength;
                }
                int resultLength = intEndIndex - intStartIndex;
                if (intStartIndex < 0 || intEndIndex < intStartIndex || intEndIndex > size) {
                    error.enter(node);
                    throw PolyglotInteropErrors.invalidBufferOffset(languageContext, receiver, intStartIndex, resultLength);
                }
                return PolyglotByteSequence.create(languageContext, receiver, startOffset + intStartIndex, resultLength);
            }

            @Override
            protected String getOperationName() {
                return "subSequence";
            }

        }
    }
}
