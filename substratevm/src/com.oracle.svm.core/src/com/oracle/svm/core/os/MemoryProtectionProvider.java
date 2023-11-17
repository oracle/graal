/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.os;

import jdk.graal.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolates.ProtectionDomain;
import org.graalvm.word.PointerBase;

public interface MemoryProtectionProvider {

    @Fold
    static MemoryProtectionProvider singleton() {
        return ImageSingletons.lookup(MemoryProtectionProvider.class);
    }

    @Fold
    static boolean isAvailable() {
        return ImageSingletons.contains(MemoryProtectionProvider.class);
    }

    /**
     * Grant access to the isolate's memory.
     */
    void unlockCurrentIsolate();

    /**
     * Handle a PKU segfault.
     *
     * @param sigInfo pointer to the siginfo_t struct
     */
    void handleSegfault(PointerBase sigInfo);

    /**
     * Print debug information for a PKU segfault.
     *
     * @param sigInfo pointer to the siginfo_t struct
     */
    void printSignalInfo(PointerBase sigInfo);

    /**
     * Retrieve the protection domain implemented by this provider.
     *
     * @return the protection domain
     */
    ProtectionDomain getProtectionDomain();

    class UnsupportedDomainException extends Exception {
        private static final long serialVersionUID = 1L;

        @Override
        public String getMessage() {
            return "Protection domain unrecognized.";
        }
    }

    /**
     * Return a protection key representation of the given protection domain that can be used in the
     * native {@code CEntryPointCreateIsolateParameters}. It will return {@code DOMAIN_UNRECOGNIZED}
     * if the {@link MemoryProtectionProvider} implementation does not support the passed protection
     * domain.
     *
     * @param domain memory protection domain
     * @return the protection key for a given protection domain
     * @throws UnsupportedDomainException if the passed protection domain is not supported by this
     *             MemoryProtectionProvider
     */
    int asProtectionKey(ProtectionDomain domain) throws UnsupportedDomainException;
}
