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

import java.io.IOException;
import java.net.BindException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.StandardSocketOptions;
import java.nio.channels.ServerSocketChannel;
import java.util.Optional;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.io.Throw;
import com.oracle.truffle.espresso.runtime.EspressoContext;

/**
 * In the context of the EspressoLibs project, this class is designed to aggregate methods and
 * fields that potentially leak information about the host system. Depending on the context, leaking
 * such information might not be preferable due to security or privacy concerns.
 */
public class InformationLeak {
    private final EspressoContext context;

    public InformationLeak(EspressoContext ctx) {
        this.context = ctx;
    }

    public long getPid() {
        return ProcessHandle.current().pid();
    }

    @TruffleBoundary
    public ProcessHandle.Info getProcessHandleInfo(long pid) {
        Optional<ProcessHandle> processHandle = ProcessHandle.of(pid);
        return processHandle.map(ProcessHandle::info).orElse(null);
    }

    public void checkNetworkEnabled() {
        if (!context.getLanguage().enableNetworking()) {
            throw Throw.throwIllegalStateException("You are accessing deep LibNet classes even though networking is disabled", context);
        }
    }

    public boolean isIPv6Available0() {
        this.checkNetworkEnabled();
        try (Socket s = new Socket()) {
            s.bind(new InetSocketAddress(Inet6Address.getByName("::"), 0));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean isIPv4Available() {
        this.checkNetworkEnabled();
        try (Socket s = new Socket()) {
            s.bind(new InetSocketAddress(Inet4Address.getByName("0.0.0.0"), 0));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @TruffleBoundary
    public boolean isReusePortAvailable0() {
        this.checkNetworkEnabled();
        try (ServerSocketChannel channel = ServerSocketChannel.open()) {
            return channel.supportedOptions().contains(StandardSocketOptions.SO_REUSEPORT);
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        }
    }

    public boolean istty() {
        return context.getEnv().in() == System.in && context.getEnv().out() == System.out;
    }

    @TruffleBoundary
    public int isExclusvieBindAvailable() {
        try (ServerSocket socket1 = new ServerSocket()) {
            socket1.setReuseAddress(false);
            socket1.bind(new InetSocketAddress(8080));

            try (ServerSocket socket2 = new ServerSocket()) {
                socket2.setReuseAddress(false);
                socket2.bind(new InetSocketAddress(8080)); // Should fail if exclusive bind works
                return -1;
            } catch (BindException e) {
                return 1;
            }
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        }
    }

    public byte[] getMacAddress(NetworkInterface netIF) {
        try {
            return netIF.getHardwareAddress();
        } catch (SocketException e) {
            throw Throw.throwSocketException(e, context);
        }
    }
}
