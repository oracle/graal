/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.graalvm.component.installer.remote;

/**
 *
 * @author sdedic
 */
public class OptionalProxyConnectionFactory {
    String envHttpProxy = System.getenv("http_proxy"); // NOI18N
    String envHttpsProxy = System.getenv("https_proxy"); // NOI18N

    private static final int DEFAULT_CONNECT_DELAY = Integer.getInteger("org.graalvm.component.installer.connectDelaySec", 5);
    private int connectDelay = DEFAULT_CONNECT_DELAY;
}
