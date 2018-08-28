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
package org.graalvm.nativeimage;

import org.graalvm.nativeimage.impl.RuntimeOptionsSupport;
import org.graalvm.options.OptionDescriptors;

/**
 * Used for manipulating options at run time.
 *
 * @since 1.0
 */
public final class RuntimeOptions {

    private RuntimeOptions() {
    }

    /**
     * Set the value of the option with the provided name to the new value.
     *
     * @since 1.0
     */
    public static void set(String optionName, Object value) {
        ImageSingletons.lookup(RuntimeOptionsSupport.class).set(optionName, value);
    }

    /**
     * Get the value of the option with the provided name.
     *
     * @since 1.0
     */
    public static <T> T get(String optionName) {
        return ImageSingletons.lookup(RuntimeOptionsSupport.class).get(optionName);
    }

    /**
     * Returns all available run time options.
     *
     * @since 1.0
     */
    public static OptionDescriptors getOptions() {
        return ImageSingletons.lookup(RuntimeOptionsSupport.class).getOptions();
    }

    /**
     * Runs all startup hooks that were registered during image building. Startup hooks usually
     * depend on option values, so it is recommended (but not required) that all option values are
     * set before calling this method.
     * <p>
     * Invoking this method more than once has no effect, i.e., startup hooks are only executed at
     * the first invocation.
     *
     * @since 1.0
     */
    public static void runStartupHooks() {
        ImageSingletons.lookup(RuntimeOptionsSupport.class).runStartupHooks();
    }
}
