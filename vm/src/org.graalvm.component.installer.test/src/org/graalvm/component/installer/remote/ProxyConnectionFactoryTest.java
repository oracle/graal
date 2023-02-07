/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.net.InetSocketAddress;
import java.net.URL;
import java.util.List;
import org.graalvm.component.installer.TestBase;
import org.graalvm.component.installer.persist.ProxyResource;
import org.graalvm.component.installer.remote.ProxyConnectionFactory.Connector;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 *
 * @author sdedic
 */
public class ProxyConnectionFactoryTest extends TestBase {

    @Rule public ProxyResource proxyResource = new ProxyResource();

    private static final String RESOURCE_URL = "test://remote-resource";
    private static final String HTTP_PROXY_ADDRESS = "http://proxy:80";

    private ProxyConnectionFactory instance;

    @Before
    public void setUp() throws Exception {
        URL u = new URL(RESOURCE_URL);
        instance = new ProxyConnectionFactory(this, u);
        // override proxies which may be loaded from testsuite environment:
        instance.setProxy(true, null);
        instance.setProxy(false, null);
    }

    /**
     * If no proxy is set, just direct connector is used.
     */
    @Test
    public void testNoProxy() throws Exception {
        List<Connector> cons = instance.makeConnectors(null, null);
        assertEquals(1, cons.size());
        Connector c = cons.get(0);
        assertTrue(c.isDirect());
    }

    private static void assertDirectConnector(List<Connector> lst) {
        assertTrue(lst.stream().filter(c -> c.isDirect()).findFirst().isPresent());
    }

    private static Connector findConnector(List<Connector> lst, String proxyHost) {
        for (Connector c : lst) {
            InetSocketAddress a = c.getProxyAddress();
            if (a != null && proxyHost.equals(a.getHostName())) {
                return c;
            }
        }
        return null;
    }

    @Test
    public void testHttpProxy() throws Exception {
        List<Connector> cons = instance.makeConnectors(HTTP_PROXY_ADDRESS, null);
        assertEquals(2, cons.size());
        assertDirectConnector(cons);
        assertNotNull(findConnector(cons, "proxy"));
    }

    @Test
    public void testHttpsProxy() throws Exception {
        List<Connector> cons = instance.makeConnectors(null, HTTP_PROXY_ADDRESS);
        assertEquals(2, cons.size());
        assertDirectConnector(cons);
        assertNotNull(findConnector(cons, "proxy"));
    }

    @Test
    public void testBothProxiesSame() throws Exception {
        List<Connector> cons = instance.makeConnectors(HTTP_PROXY_ADDRESS, HTTP_PROXY_ADDRESS);
        assertEquals(2, cons.size());
        assertDirectConnector(cons);
        assertNotNull(findConnector(cons, "proxy"));
    }

    @Test
    public void testHttpAndHttpsDifferent() throws Exception {
        List<Connector> cons = instance.makeConnectors(HTTP_PROXY_ADDRESS, "http://securerproxy:1111");
        assertEquals(3, cons.size());
        assertDirectConnector(cons);
        assertNotNull(findConnector(cons, "proxy"));
        assertNotNull(findConnector(cons, "securerproxy"));
    }

    @Test
    public void testNamedProxyWithSpace() throws Exception {
        List<Connector> cons = instance.makeConnectors(" proxy:80", null);
        assertEquals(2, cons.size());
        assertDirectConnector(cons);

        Connector c = findConnector(cons, "proxy");
        assertNotNull(c);
        assertEquals(80, c.getProxyAddress().getPort());
    }

    String errorMessage;

    @Test
    public void testMalformedProxy() throws Exception {
        class F extends FeedbackAdapter {

            @Override
            public void error(String key, Throwable t, Object... params) {
                super.error(key, t, params);
                errorMessage = key;
            }
        }
        F f = new F();
        delegateFeedback(f);
        List<Connector> cons = instance.makeConnectors(":proxy:80", null);
        assertEquals(1, cons.size());
        assertDirectConnector(cons);

        // check that warning about illegal proxy was printed
        assertTrue(errorMessage.startsWith("WARN_"));
    }

    @Test
    public void testNumericWithProtocol() throws Exception {
        List<Connector> cons = instance.makeConnectors("http://127.0.0.2:1080", null);
        assertEquals(2, cons.size());
        assertDirectConnector(cons);

        Connector c = findConnector(cons, "127.0.0.2");
        assertNotNull(c);
        assertEquals(1080, c.getProxyAddress().getPort());
    }

    @Test
    public void testNumericNoProtocol() throws Exception {
        List<Connector> cons = instance.makeConnectors("127.0.0.2:1080", null);
        assertEquals(2, cons.size());
        assertDirectConnector(cons);

        Connector c = findConnector(cons, "127.0.0.2");
        assertNotNull(c);
        assertEquals(1080, c.getProxyAddress().getPort());
    }

}
