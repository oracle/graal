/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.api.meta;


/**
 * A implementation of {@link JavaMethod} for an unresolved method.
 */
public class UnresolvedMethod implements JavaMethod {

    private final String name;
    private final JavaType holder;
    private final Signature signature;

    public UnresolvedMethod(JavaType holder, String name, Signature signature) {
        this.name = name;
        this.holder = holder;
        this.signature = signature;
    }

    public String name() {
        return name;
    }

    public JavaType holder() {
        return holder;
    }

    public Signature signature() {
        return signature;
    }

    @Override
    public String toString() {
        return MetaUtil.format("%H.%n(%p) [unresolved]", this);
    }
}
