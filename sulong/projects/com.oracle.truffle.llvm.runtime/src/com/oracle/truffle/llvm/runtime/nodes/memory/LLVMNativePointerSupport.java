/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime.nodes.memory;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMNativeLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

public final class LLVMNativePointerSupport extends LLVMNode {

    private static LLVMNativePointerSupport UNCACHED = new LLVMNativePointerSupport(true);

    @Child private InternalLibrary delegate;

    private LLVMNativePointerSupport(boolean uncached) {
        delegate = new InternalLibrary(uncached);
    }

    public static LLVMNativePointerSupport create() {
        return new LLVMNativePointerSupport(false);
    }

    public static LLVMNativePointerSupport getUncached() {
        return UNCACHED;
    }

    public LLVMNativeLibrary getLibrary() {
        return delegate;
    }

    public boolean isPointer(Object receiver) {
        return delegate.isPointer(receiver);
    }

    public long asPointer(Object receiver) throws UnsupportedMessageException {
        return delegate.asPointer(receiver);
    }

    public LLVMNativePointer toNativePointer(Object receiver) {
        return delegate.toNativePointer(receiver);
    }

    public static final class InternalLibrary extends LLVMNativeLibrary implements GenerateAOT.Provider {
        @Child private IsPointerNode isPointerNode;
        @Child private AsPointerNode asPointerNode;
        @Child private ToNativePointerNode toNativePointerNode;

        private InternalLibrary(boolean uncached) {
            if (uncached) {
                isPointerNode = LLVMNativePointerSupportFactory.IsPointerNodeGen.getUncached();
                asPointerNode = LLVMNativePointerSupportFactory.AsPointerNodeGen.getUncached();
                toNativePointerNode = LLVMNativePointerSupportFactory.ToNativePointerNodeGen.getUncached();
            }
        }

        @Override
        public boolean isPointer(Object receiver) {
            if (isPointerNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isPointerNode = insert(LLVMNativePointerSupportFactory.IsPointerNodeGen.create());
            }
            return isPointerNode.execute(receiver);
        }

        @Override
        public long asPointer(Object receiver) throws UnsupportedMessageException {
            if (asPointerNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                asPointerNode = insert(LLVMNativePointerSupportFactory.AsPointerNodeGen.create());
            }
            return asPointerNode.execute(receiver);
        }

        @Override
        public LLVMNativePointer toNativePointer(Object receiver) {
            if (toNativePointerNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toNativePointerNode = insert(LLVMNativePointerSupportFactory.ToNativePointerNodeGen.create());
            }
            return toNativePointerNode.execute(receiver);
        }

        @Override
        public boolean accepts(Object receiver) {
            return true;
        }

        @Override
        public void prepareForAOT(TruffleLanguage<?> language, RootNode root) {
            isPointerNode = insert(LLVMNativePointerSupportFactory.IsPointerNodeGen.create());
            ((GenerateAOT.Provider) isPointerNode).prepareForAOT(language, root);
            asPointerNode = insert(LLVMNativePointerSupportFactory.AsPointerNodeGen.create());
            ((GenerateAOT.Provider) asPointerNode).prepareForAOT(language, root);
            toNativePointerNode = insert(LLVMNativePointerSupportFactory.ToNativePointerNodeGen.create());
            ((GenerateAOT.Provider) toNativePointerNode).prepareForAOT(language, root);
        }
    }

    @GenerateUncached
    public abstract static class IsPointerNode extends LLVMNode {

        public abstract boolean execute(Object receiver);

        @Specialization
        boolean doNativePointer(@SuppressWarnings("unused") LLVMNativePointer receiver) {
            return true;
        }

        @Specialization
        boolean doManagedPointer(@SuppressWarnings("unused") LLVMManagedPointer receiver, @Cached IsPointerHelper isPointerHelper) {
            return isPointerHelper.execute(receiver.getObject());
        }

        @Fallback
        boolean doOther(Object receiver, @Cached IsPointerHelper isPointerHelper) {
            return isPointerHelper.execute(receiver);
        }

        @GenerateUncached
        abstract static class IsPointerHelper extends LLVMNode {

            abstract boolean execute(Object receiver);

            @Specialization
            boolean doLong(@SuppressWarnings("unused") long x) {
                return true;
            }

            @Specialization
            boolean doByteArray(@SuppressWarnings("unused") byte[] x) {
                return false;
            }

            @Specialization
            boolean doFunctionDescriptor(LLVMFunctionDescriptor functionDescriptor) {
                return functionDescriptor.isPointer();
            }

            @Fallback
            @GenerateAOT.Exclude
            boolean doOther(Object x, @CachedLibrary(limit = "1") LLVMNativeLibrary natives) {
                return natives.isPointer(x);
            }
        }
    }

    @GenerateUncached
    public abstract static class AsPointerNode extends LLVMNode {

        public abstract long execute(Object receiver) throws UnsupportedMessageException;

        @Specialization
        long doNativePointer(LLVMNativePointer receiver) {
            return receiver.asNative();
        }

        @Specialization
        long doManagedPointer(LLVMManagedPointer receiver, @Cached AsPointerHelper asPointerHelper) {
            return asPointerHelper.execute(receiver.getObject()) + receiver.getOffset();
        }

        @Fallback
        long doOther(Object receiver, @Cached AsPointerHelper asPointerHelper) {
            return asPointerHelper.execute(receiver);
        }

        @GenerateUncached
        abstract static class AsPointerHelper extends LLVMNode {

            abstract long execute(Object receiver);

            @Specialization
            long doLong(long x) {
                return x;
            }

            @Specialization
            long doFunctionDescriptor(LLVMFunctionDescriptor functionDescriptor,
                            @Cached BranchProfile exception) {
                try {
                    return functionDescriptor.asPointer(exception);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new LLVMPolyglotException(this, e.getMessage());
                }
            }

            @Fallback
            @GenerateAOT.Exclude
            long doOther(Object x, @CachedLibrary(limit = "1") LLVMNativeLibrary natives) {
                try {
                    return natives.asPointer(x);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new LLVMPolyglotException(this, e.getMessage());
                }
            }
        }

    }

    @GenerateUncached
    public abstract static class ToNativePointerNode extends LLVMNode {

        public abstract LLVMNativePointer execute(Object receiver);

        @Specialization
        LLVMNativePointer doNativePointer(LLVMNativePointer receiver) {
            return receiver;
        }

        @Specialization
        LLVMNativePointer doManagedPointer(LLVMManagedPointer receiver, @Cached ToNativePointerHelper toNativePointerHelper) {
            return toNativePointerHelper.execute(receiver.getObject()).increment(receiver.getOffset());
        }

        @Fallback
        LLVMNativePointer doOther(Object receiver, @Cached ToNativePointerHelper toNativePointerHelper) {
            return toNativePointerHelper.execute(receiver);
        }

        @GenerateUncached
        abstract static class ToNativePointerHelper extends LLVMNode {

            abstract LLVMNativePointer execute(Object receiver);

            @Specialization
            LLVMNativePointer doLong(long x) {
                return LLVMNativePointer.create(x);
            }

            @Specialization
            LLVMNativePointer doByteArray(@SuppressWarnings("unused") byte[] x) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new LLVMPolyglotException(this, "Cannot convert virtual allocation object to native pointer.");
            }

            @Specialization
            LLVMNativePointer doFunctionDescriptor(LLVMFunctionDescriptor functionDescriptor,
                            @Cached BranchProfile exceptionProfile) {
                try {
                    return functionDescriptor.asNativePointer(exceptionProfile);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new LLVMPolyglotException(this, e.getMessage());
                }
            }

            @Fallback
            @GenerateAOT.Exclude
            LLVMNativePointer doOther(Object x, @CachedLibrary(limit = "1") LLVMNativeLibrary natives) {
                natives.accepts(x);
                return natives.toNativePointer(x);
            }
        }

    }

}
