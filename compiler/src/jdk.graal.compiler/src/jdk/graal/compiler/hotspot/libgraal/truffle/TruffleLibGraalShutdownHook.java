/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.libgraal.truffle;

import com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id;
import jdk.graal.compiler.serviceprovider.IsolateUtil;
import jdk.graal.compiler.serviceprovider.ServiceProvider;
import jdk.vm.ci.hotspot.HotSpotVMEventListener;
import jdk.vm.ci.services.JVMCIServiceLocator;

import java.lang.invoke.MethodHandle;

import static jdk.graal.compiler.hotspot.libgraal.truffle.BuildTime.getHostMethodHandleOrFail;

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

    static void registerShutdownHook() {
        registeredHook = new ShutdownHook();
    }

    static class ShutdownHook implements HotSpotVMEventListener {

        private static final Handles HANDLES = new Handles();

        ShutdownHook() {
        }

        @Override
        public void notifyShutdown() {
            try {
                HANDLES.onIsolateShutdown.invoke(IsolateUtil.getIsolateID());
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }

    private static final class Handles {
        final MethodHandle onIsolateShutdown = getHostMethodHandleOrFail(Id.OnIsolateShutdown);
    }
}
