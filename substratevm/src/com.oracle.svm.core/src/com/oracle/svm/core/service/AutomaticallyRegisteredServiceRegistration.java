/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.service;

/**
 * Common contract for generated service-registration entries used by automatic registration
 * handlers. Generated implementations identify the annotated implementation class that should be
 * resolved through the image builder's class loader.
 * <p>
 * Do not implement this interface manually. It is intended only for annotation-processor-generated
 * classes.
 */
public interface AutomaticallyRegisteredServiceRegistration {
    /**
     * Returns the Java binary name of the annotated class represented by this generated service
     * entry.
     * <p>
     * For top-level classes this matches the fully qualified source name. For nested classes it
     * uses {@code $} separators, for example {@code com.example.Outer$Inner}. The
     * automatic-registration handler resolves that name through the image builder's
     * {@link ClassLoader}.
     */
    String getClassName();
}
