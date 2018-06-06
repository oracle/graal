/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.truffle.nfi;

import org.graalvm.word.PointerBase;

import com.oracle.svm.truffle.nfi.libffi.LibFFIHeaderDirectives;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CFieldAddress;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.word.ComparableWord;

/**
 * Definition of the native data structures for TruffleEnv and TruffleContext.
 */
@CContext(LibFFIHeaderDirectives.class)
final class NativeAPI {

    interface TruffleContextHandle extends ComparableWord {
    }

    @CStruct("svm_truffle_context")
    public interface NativeTruffleContext extends PointerBase {

        @CField("functions")
        void setFunctions(TruffleThreadAPI api);

        @CField
        TruffleContextHandle contextHandle();

        @CField("contextHandle")
        void setContextHandle(TruffleContextHandle ctx);

        @CField
        Isolate isolate();

        @CField("isolate")
        void setIsolate(Isolate isolate);

        @CFieldAddress
        TruffleThreadAPI threadAPI();

        @CFieldAddress
        TruffleNativeAPI nativeAPI();
    }

    @CStruct("svm_truffle_env")
    public interface NativeTruffleEnv extends PointerBase {

        @CField("functions")
        void setFunctions(TruffleNativeAPI api);

        @CField
        NativeTruffleContext context();

        @CField("context")
        void setContext(NativeTruffleContext ctx);

        @CField
        IsolateThread isolateThread();

        @CField("isolateThread")
        void setIsolateThread(IsolateThread thread);
    }

    @CStruct(value = "struct __TruffleThreadAPI")
    public interface TruffleThreadAPI extends PointerBase {

        @CField("getTruffleEnv")
        void setGetTruffleEnvFunction(GetTruffleEnvFunction fn);

        @CField("attachCurrentThread")
        void setAttachCurrentThreadFunction(AttachCurrentThreadFunction fn);

        @CField("detachCurrentThread")
        void setDetachCurrentThreadFunction(DetachCurrentThreadFunction fn);
    }

    public interface GetTruffleEnvFunction extends CFunctionPointer {

        @InvokeCFunctionPointer
        NativeTruffleEnv getTruffleEnv(NativeTruffleContext ctx);
    }

    public interface AttachCurrentThreadFunction extends CFunctionPointer {

        @InvokeCFunctionPointer
        NativeTruffleEnv attachCurrentThread(NativeTruffleContext ctx);
    }

    public interface DetachCurrentThreadFunction extends CFunctionPointer {

        @InvokeCFunctionPointer
        void detachCurrentThread(NativeTruffleContext ctx);
    }

    @CStruct(value = "struct __TruffleNativeAPI")
    public interface TruffleNativeAPI extends PointerBase {

        @CField("getTruffleContext")
        void setGetTruffleContextFunction(GetTruffleContextFunction fn);

        @CField("newObjectRef")
        void setNewObjectRefFunction(NewObjectRefFunction fn);

        @CField("releaseObjectRef")
        void setReleaseObjectRefFunction(ReleaseObjectRefFunction fn);

        @CField("releaseAndReturn")
        void setReleaseAndReturnFunction(ReleaseAndReturnFunction fn);

        @CField("isSameObject")
        void setIsSameObjectFunction(IsSameObjectFunction fn);

        @CField("newClosureRef")
        void setNewClosureRefFunction(NewClosureRefFunction fn);

        @CField("releaseClosureRef")
        void setReleaseClosureRefFunction(ReleaseClosureRefFunction fn);

        @CField("getClosureObject")
        void setGetClosureObjectFunction(GetClosureObjectFunction fn);
    }

    public interface GetTruffleContextFunction extends CFunctionPointer {

        @InvokeCFunctionPointer
        NativeTruffleContext getTruffleContext(NativeTruffleEnv env);
    }

    public interface NewObjectRefFunction extends CFunctionPointer {

        @InvokeCFunctionPointer
        TruffleObjectHandle newObjectRef(NativeTruffleEnv env, TruffleObjectHandle object);
    }

    public interface ReleaseObjectRefFunction extends CFunctionPointer {

        @InvokeCFunctionPointer
        void releaseObjectRef(NativeTruffleEnv env, TruffleObjectHandle object);
    }

    public interface ReleaseAndReturnFunction extends CFunctionPointer {

        @InvokeCFunctionPointer
        TruffleObjectHandle releaseAndReturn(NativeTruffleEnv env, TruffleObjectHandle object);
    }

    public interface IsSameObjectFunction extends CFunctionPointer {

        @InvokeCFunctionPointer
        int isSameObject(NativeTruffleEnv env, TruffleObjectHandle object1, TruffleObjectHandle object2);
    }

    public interface NewClosureRefFunction extends CFunctionPointer {

        @InvokeCFunctionPointer
        void newClosureRef(NativeTruffleEnv env, PointerBase closure);
    }

    public interface ReleaseClosureRefFunction extends CFunctionPointer {

        @InvokeCFunctionPointer
        void releaseClosureRef(NativeTruffleEnv env, PointerBase closure);
    }

    public interface GetClosureObjectFunction extends CFunctionPointer {

        @InvokeCFunctionPointer
        TruffleObjectHandle getClosureObject(NativeTruffleEnv env, PointerBase closure);
    }
}
