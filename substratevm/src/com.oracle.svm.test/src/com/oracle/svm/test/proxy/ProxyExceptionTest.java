/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.test.proxy;

import java.lang.reflect.Proxy;

import org.junit.Test;

public class ProxyExceptionTest {

    /**
     * <p>
     * When we are creating a proxy, which contains a method with a class (<b>initialized at
     * runtime</b>) as a parameter, will cause exception in runtime. Cause of this issue is that
     * {@link com.oracle.svm.hosted.snippets.SubstrateGraphBuilderPlugins} didn't manage to make a
     * constant out of proxies methods (which are accessed reflectively at runtime).
     * </p>
     */
    @Test
    public void test1() {
        Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(), new Class<?>[]{CustomProxy.class}, new CustomInvocationHandler());
    }
}
