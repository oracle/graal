/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer.remote;

/**
 * 
 * @author sdedic
 */
public class OptionalProxyConnectionFactory {
    /**
     * The max delay to connect to the final destination or open a proxy connection. In seconds.
     */
    private static final int DEFAULT_CONNECT_DELAY = Integer.getInteger("org.graalvm.component.installer.connectDelaySec", 5);

    /**
     * HTTP proxy settings. The default is taken from system environment variables.
     */
    String envHttpProxy = System.getenv("http_proxy"); // NOI18N

    /**
     * HTTPS proxy settings. The default is taken from system environment variables.
     */
    String envHttpsProxy = System.getenv("https_proxy"); // NOI18N

    /**
     * The configurable delay for this factory. Initialized to {@link #DEFAULT_CONNECT_DELAY}.
     */
    private int connectDelay = DEFAULT_CONNECT_DELAY;
}
