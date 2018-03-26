/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.UnsignedWord;

/**
 * Implement this interface to create a custom log handler. The custom log handler must be installed
 * in the {@code ImageSingletons} at {@code Feature.duringSetup()} time like so:
 * 
 * <pre>
 *    class LogHandlerFeature implements Feature {
 *       {@literal @}Override
 *        public void duringSetup(DuringSetupAccess access) {
 *            ImageSingletons.add(LogHandler.class, new CustomLogHandler());
 *        }
 *    }
 * </pre>
 * 
 * If no custom log handler is installed a default one would be provided at
 * {@code Feature.beforeAnalysis()} time. Installing a custom log handler at a later time, after the
 * default one has been installed, will result in an error.
 *
 */
public interface LogHandler {

    static LogHandler get() {
        return ImageSingletons.lookup(LogHandler.class);
    }

    /** Writes the raw bytes. */
    void log(CCharPointer bytes, UnsignedWord length);

    /** Forces the log to flush to its destination. */
    void flush();

    /** Exit the VM. Method must not return. No Java code should be run after this point. */
    void fatalError();
}
