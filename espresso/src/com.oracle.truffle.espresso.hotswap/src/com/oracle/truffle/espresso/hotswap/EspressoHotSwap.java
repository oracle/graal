/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.hotswap;

import java.io.IOException;

/**
 * Provides access to the enhanced HotSwap capabilities of Espresso. Every method allows
 * registration of HotSwap actions that will be fired on relevant class redefinition changes.
 *
 * Note: This class will not register anything unless running Java on truffle.
 *
 * @since 21.2
 */
public final class EspressoHotSwap {

    private EspressoHotSwap() {
        throw new RuntimeException("No instance of EspressoHotSwap can be created");
    }

    private static final HotSwapHandler handler = HotSwapHandler.create();

    /**
     * Registration of a HotSwap plugin for which all generic actions are fired during HotSwap. One
     * example of such action is the {@link HotSwapPlugin#postHotSwap(Class[])} that is fired for a
     * registered plugin.
     *
     * Note: The Plugin API is expected to change to allow plugins to receive finer-grained changes.
     *
     * @param plugin the plugin to register
     * @return true if registration was successful
     * @since 21.2
     */
    public static boolean registerPlugin(HotSwapPlugin plugin) {
        if (handler != null) {
            handler.addPlugin(plugin);
        }
        return handler != null;
    }

    /**
     * Registration of a generic post HotSwap action that will be fired after class redefinition
     * completed.
     *
     * @param action the action to fire
     * @return true if registration was successful
     * @since 21.2
     */
    public static boolean registerPostHotSwapAction(HotSwapAction action) {
        if (handler != null) {
            handler.registerPostHotSwapAction(action);
        }
        return handler != null;
    }

    /**
     * Registration of a HotSwap action that will be fired in case the {@code klass} changes.
     *
     * @param action the action to fire
     * @return true if registration was successful
     * @since 21.2
     */
    public static boolean registerHotSwapAction(Class<?> klass, HotSwapAction action) {
        if (handler != null) {
            handler.registerHotSwapAction(klass, action);
        }
        return handler != null;
    }

    /**
     * Registration of a HotSwap action that is fired if the {@code klass} or any subclass thereof
     * has a changed static initializer. Use {@code onChange} to control if the action should only
     * fire when the static initializer actually changed.
     *
     * @param klass the class instance
     * @param onChange if action should be fired only when the static initializer changes
     * @param action the action to fire
     * @return true if registration was successful
     * @since 21.2
     */
    public static boolean registerClassInitHotSwap(Class<?> klass, boolean onChange, HotSwapAction action) {
        if (handler != null) {
            handler.registerStaticClassInitHotSwap(klass, onChange, action);
        }
        return handler != null;
    }

    /**
     * Registration of a HotSwap action that will be fired if changes are detected to the specified
     * resource.
     *
     * @param loader the class loader to lookup the service type
     * @param resource the resource to register a change listener on
     * @param action the action to fire
     * @return true if registration was successful
     * @since 21.3
     */
    public static boolean registerResourceListener(ClassLoader loader, String resource, HotSwapAction action) throws IOException {
        if (handler != null) {
            return handler.registerResourceListener(loader, resource, action);
        }
        return false;
    }

    /**
     * Registration of a HotSwap action that will be fired if changes are detected to the declared
     * META-INF/services for {@code serviceType}.
     *
     * @param serviceType the class instance of the service type
     * @param loader the class loader to lookup the service type
     * @param action the action to fire
     * @return true if registration was successful
     * @since 21.2
     */
    public static boolean registerMetaInfServicesListener(Class<?> serviceType, ClassLoader loader, HotSwapAction action) throws IOException {
        if (handler != null) {
            handler.registerMetaInfServicesListener(serviceType, loader, action);
        }
        return handler != null;
    }
}
