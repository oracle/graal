/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.libs;

import java.io.Console;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.StandardSocketOptions;
import java.nio.channels.ServerSocketChannel;
import java.util.Enumeration;
import java.util.Optional;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.io.Throw;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.OS;

/**
 * In the context of the EspressoLibs project, this class is designed to aggregate methods and
 * fields that potentially leak information about the host system. Depending on the context, leaking
 * such information might not be preferable due to security or privacy concerns. However, it is
 * assumed that permission checks are done in the caller's of InformationLeak. Please note it leaks
 * host information, meaning return types should not be StaticObjects!
 */
public class InformationLeak {
    private static final Method IS_TERMINAL_METHOD = getIsTerminalMethod();

    private final EspressoContext context;
    private volatile boolean isIPv6Initialized = false;
    private boolean isIPv6Available = false;

    public InformationLeak(EspressoContext ctx) {
        this.context = ctx;
    }

    private static Method getIsTerminalMethod() {
        try {
            return Console.class.getMethod("isTerminal");
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    public long getPid() {
        return ProcessHandle.current().pid();
    }

    @TruffleBoundary
    public ProcessHandle.Info getProcessHandleInfo(long pid) {
        assert context.getEnv().isCreateProcessAllowed();
        Optional<ProcessHandle> processHandle = ProcessHandle.of(pid);
        return processHandle.map(ProcessHandle::info).orElse(null);
    }

    public boolean isIPv6Available() {
        if (isIPv6Initialized) {
            return isIPv6Available;
        }
        assert context.getEnv().isSocketIOAllowed();
        synchronized (this) {
            try (Socket s = new Socket()) {
                s.bind(new InetSocketAddress(Inet6Address.getByName("::"), 0));
                isIPv6Available = true;
            } catch (IOException e) {
                isIPv6Available = false;
            } finally {
                isIPv6Initialized = true;
            }
        }
        return isIPv6Available;
    }

    public boolean isIPv4Available() {
        assert context.getEnv().isSocketIOAllowed();
        try (Socket s = new Socket()) {
            s.bind(new InetSocketAddress(Inet4Address.getByName("0.0.0.0"), 0));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @TruffleBoundary
    public boolean isReusePortAvailable0() {
        assert context.getEnv().isSocketIOAllowed();
        try (ServerSocketChannel channel = ServerSocketChannel.open()) {
            return channel.supportedOptions().contains(StandardSocketOptions.SO_REUSEPORT);
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        }
    }

    @TruffleBoundary
    public boolean istty() {
        if (!(context.getEnv().in() == System.in && context.getEnv().out() == System.out)) {
            return false;
        }
        Console console = System.console();
        if (console == null) {
            return false;
        }
        if (IS_TERMINAL_METHOD != null) {
            try {
                return (boolean) IS_TERMINAL_METHOD.invoke(console);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new Error(e);
            }
        } else {
            return true;
        }
    }

    public String consoleEncoding() {
        if (OS.getCurrent() == OS.Windows) {
            if (istty()) {
                throw Throw.throwUnsupported("console encoding for windows is currently unimplemented", context);
            }
        }
        return null;
    }

    @TruffleBoundary
    public int isExclusiveBindAvailable() {
        assert context.getEnv().isSocketIOAllowed();
        // this property is only available on windows!
        if (OS.getCurrent() == OS.Windows) {
            /*
             * The guest (and host) sets this property as EXCLUSIVE_BIND in sun.nio.ch.Net. We use
             * exactly the same mechanism here. If we return 1 this implies our host system (which
             * is used to implement the networking functionalities) will have exclusiveBind enabled
             * by default. To ensure consistency with the guest, we need to make sure to set up the
             * guest property sun.net.useExclusiveBind accordingly.
             */
            String exclBindProp = System.getProperty("sun.net.useExclusiveBind");
            if (exclBindProp != null && (exclBindProp.isEmpty() || Boolean.parseBoolean(exclBindProp))) {
                return 1;
            }
        }
        return -1;
    }

    public byte[] getMacAddress(NetworkInterface netIF) {
        assert context.getEnv().isSocketIOAllowed();
        try {
            return netIF.getHardwareAddress();
        } catch (SocketException e) {
            throw Throw.throwSocketException(e, context);
        }
    }

    public Enumeration<NetworkInterface> getNetworkInterfaces() {
        assert context.getEnv().isSocketIOAllowed();
        try {
            return NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            throw Throw.throwSocketException(e, context);
        }
    }
}
