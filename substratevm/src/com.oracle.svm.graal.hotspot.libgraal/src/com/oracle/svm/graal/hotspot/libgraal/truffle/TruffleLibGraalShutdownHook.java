/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hotspot.libgraal.truffle;

import jdk.graal.compiler.serviceprovider.IsolateUtil;
import jdk.graal.compiler.serviceprovider.ServiceProvider;
import org.graalvm.jniutils.JNI.JClass;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JavaVM;
import org.graalvm.jniutils.JNIUtil;

import com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal;

import jdk.vm.ci.hotspot.HotSpotVMEventListener;
import jdk.vm.ci.services.JVMCIServiceLocator;

@ServiceProvider(JVMCIServiceLocator.class)
public class TruffleLibGraalShutdownHook extends JVMCIServiceLocator {

    private static volatile ShutdownHook registeredHook;

    @Override
    protected <S> S getProvider(Class<S> service) {
        ShutdownHook hook = registeredHook;
        if (hook != null && service == HotSpotVMEventListener.class) {
            return service.cast(hook);
        }
        return null;
    }

    static void registerShutdownHook(JNIEnv env, JClass runtimeClass) {
        JavaVM vm = JNIUtil.GetJavaVM(env);
        ShutdownHook hook = registeredHook;
        assert hook == null || hook.javaVm.isNull() || hook.javaVm.equal(vm);
        registeredHook = new ShutdownHook(vm, new TruffleFromLibGraalCalls(env, runtimeClass));
    }

    static class ShutdownHook implements HotSpotVMEventListener {

        private final JavaVM javaVm;
        private final TruffleFromLibGraalCalls calls;

        ShutdownHook(JavaVM javaVm, TruffleFromLibGraalCalls calls) {
            this.javaVm = javaVm;
            this.calls = calls;
        }

        @Override
        @TruffleFromLibGraal(TruffleFromLibGraal.Id.OnIsolateShutdown)
        public void notifyShutdown() {
            JNIEnv env = JNIUtil.GetEnv(javaVm);
            assert env.isNonNull();
            TruffleLibGraalShutdownHookGen.callOnIsolateShutdown(calls, env, IsolateUtil.getIsolateID());
        }

    }

}
