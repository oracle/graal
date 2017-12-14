/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector.test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.tools.chromeinspector.ScriptsHandler;

public class ScriptsHandlerTest {

    /**
     * Test that all possible characters in URIs convert correctly.
     */
    @Test
    public void testNiceURIs() {
        char[] characters = new char[126 - 30 - ('9' - '1') - ('Z' - 'B') - ('z' - 'b')];
        int base = 31;
        for (char c = 32; c <= '0'; c++) {  // All various characters till '0'
            characters[c - base] = c;
        }
        base += ('9' - '0' - 1);            // Skip most digits
        for (char c = '9'; c <= 'A'; c++) {
            characters[c - base] = c;
        }
        base += ('Z' - 'A' - 1);            // Skip most uppercase letters
        for (char c = 'Z'; c <= 'a'; c++) {
            characters[c - base] = c;
        }
        base += ('z' - 'a' - 1);            // Skip most lowercase letters
        for (char c = 'z'; c <= 126; c++) {
            characters[c - base] = c;
        }

        String[] all = new String[characters.length];
        for (int i = 0; i < characters.length; i++) {
            char c = characters[i];
            all[i] = (c == 0) ? null : new String(new char[]{c});
        }

        for (String scheme : new String[]{null, "a"}) {
            for (String user : all) {
                for (char hostC : characters) {
                    if (hostC > 0 && (hostC < '.' || hostC == '/' || '9' < hostC && hostC < 'A') || 'Z' < hostC && hostC < 'a' || '{' <= hostC) {
                        continue;
                    }
                    String host = (hostC == 0) ? null : new String(new char[]{hostC});
                    if (hostC == '.') {
                        host = "a1";
                    }
                    for (int port = -1; port <= 10; port += 11) {
                        for (char pathC : characters) {
                            if (pathC == ':') {
                                continue;
                            }
                            if (scheme != null && pathC == 0) {
                                continue;
                            }
                            String path = (pathC == 0) ? null : (pathC == '/') ? new String(new char[]{pathC}) : new String(new char[]{'/', pathC});
                            for (String query : all) {
                                for (String fragment : new String[]{null, query}) {
                                    try {
                                        URI uri = new URI(scheme, user, host, port, path, query, fragment);
                                        checkConvertURINice(uri);
                                    } catch (URISyntaxException ex) {
                                        String uriMessage = "URI(" + scheme + ", " + user + ", " + host + ", " + port + ", " + path + ", " + query + ", " + fragment + ")";
                                        throw new AssertionError(uriMessage, ex);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            for (char sspC : characters) {
                if (sspC == 0 || sspC == ':' || '[' <= sspC && sspC <= ']') {
                    continue;
                }
                String ssp = new String(new char[]{sspC});
                for (String fragment : all) {
                    try {
                        URI uri = new URI(scheme, ssp, fragment);
                        checkConvertURINice(uri);
                    } catch (URISyntaxException ex) {
                        String uriMessage = "URI(" + scheme + ", " + ssp + ", " + fragment + ")";
                        throw new AssertionError(uriMessage, ex);
                    }
                }
            }
        }

        // Test multi-character paths:
        int l = 4;  // path length
        int[] indexes = new int[l];
        Arrays.fill(indexes, 1);  // Skip the 0 character
        char[] pathChars = new char[l + 1]; // The first one is slash
        pathChars[0] = '/';
        Arrays.fill(pathChars, 1, l + 1, characters[1]);
        while (true) {
            String path = new String(pathChars);
            try {
                // Test URI path
                URI uri = new URI(null, null, path, null);
                String niceStr = checkConvertURINice(uri);
                Assert.assertTrue(path, niceStr.endsWith(path));
            } catch (URISyntaxException ex) {
                String uriMessage = "URI from path '" + path + "'";
                throw new AssertionError(uriMessage, ex);
            }
            int i;
            for (i = 0; i < l && ++indexes[i] >= characters.length; i++) {
                indexes[i] = 1;
                pathChars[i + 1] = characters[1];
            }
            if (i >= l) {
                break;
            }
            if (i == 0 && characters[indexes[i]] == '/') {
                // No initial // in the path
                indexes[i]++;
            }
            pathChars[i + 1] = characters[indexes[i]];
        }
    }

    private static String checkConvertURINice(URI uri) throws URISyntaxException {
        String niceString = ScriptsHandler.getNiceStringFromURI(uri);
        URI uri2 = ScriptsHandler.getURIFromNiceString(niceString);
        Assert.assertEquals(niceString, uri, uri2);
        return niceString;
    }
}
