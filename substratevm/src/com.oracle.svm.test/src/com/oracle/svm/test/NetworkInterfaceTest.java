package com.oracle.svm.test;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import org.junit.Assert;
import org.junit.Test;

public class NetworkInterfaceTest {

    @Test
    public void testLoopback() throws SocketException {
        Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
        boolean foundLoopback = false;
        while (ifaces.hasMoreElements()) {
            NetworkInterface each = ifaces.nextElement();
            foundLoopback = each.isLoopback() || foundLoopback;
        }

        Assert.assertTrue("At least one loopback found", foundLoopback);
    }
}
