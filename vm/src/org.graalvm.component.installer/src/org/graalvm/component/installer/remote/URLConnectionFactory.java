package org.graalvm.component.installer.remote;

import java.net.URL;
import java.net.URLConnection;
import java.util.function.Consumer;

/**
 *
 * @author sdedic
 */
public interface URLConnectionFactory {
    URLConnection   createConnection(URL u, Consumer<URLConnection> configCallback);
}
