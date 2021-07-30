/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.espresso.hotswap;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.Watchable;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

final class ServiceWatcher {

    private static final String PREFIX = "META-INF/services/";
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    private final Map<String, Set<String>> services = new HashMap<>(4);

    private WatcherThread serviceWatcherThread;
    private URLWatcher urlWatcher;

    public boolean addResourceWatcher(ClassLoader loader, String resource, HotSwapAction callback) throws IOException {
        ensureInitialized();
        URL url;
        if (loader == null) {
            url = ClassLoader.getSystemResource(resource);
        } else {
            url = loader.getResource(resource);
        }
        if (url == null) {
            throw new IOException("Resource " + resource + " not found from class loader: " + loader.getClass().getName());
        }

        if ("file".equals(url.getProtocol())) {
            // parent directory to register watch on
            File file = new File(url.getFile()).getParentFile();
            serviceWatcherThread.addWatch(new File(url.getFile()).getName(), Paths.get(file.toURI()), () -> callback.fire());
        }
    }

    public synchronized void addServiceWatcher(Class<?> service, ClassLoader loader, HotSwapAction callback) {
        try {
            ensureInitialized();
            // cache initial service implementations
            Set<String> serviceImpl = Collections.synchronizedSet(new HashSet<>());

            ServiceLoader<?> serviceLoader = ServiceLoader.load(service, loader);
            Iterator<?> iterator = serviceLoader.iterator();
            while (iterator.hasNext()) {
                try {
                    Object o = iterator.next();
                    serviceImpl.add(o.getClass().getName());
                } catch (ServiceConfigurationError e) {
                    // ignore services that we're not able to instantiate
                }
            }
            String fullName = PREFIX + service.getName();
            services.put(fullName, serviceImpl);

            // pick up the initial URLs for the service class
            ArrayList<URL> initialURLs = new ArrayList<>();
            Enumeration<URL> urls;
            if (loader == null) {
                urls = ClassLoader.getSystemResources(fullName);
            } else {
                urls = loader.getResources(fullName);
            }

            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                if ("file".equals(url.getProtocol())) {
                    // parent directory to register watch on
                    File file = new File(url.getFile()).getParentFile();
                    // listen for changes to the service registration file
                    initialURLs.add(url);
                    serviceWatcherThread.addWatch(service.getName(), Paths.get(file.toURI()), () -> onServiceChange(callback, serviceLoader, fullName));
                }
            }

            // listen for changes to URLs in the resources
            urlWatcher.addWatch(new URLServiceState(initialURLs, loader, fullName, (url) -> {
                try {
                    callback.fire();
                } catch (Throwable t) {
                    // Checkstyle: stop warning message from guest code
                    System.err.println("[HotSwap API]: Unexpected exception while running service change action");
                    // Checkstyle: resume warning message from guest code
                    t.printStackTrace();
                }
                // parent directory to register watch on
                File file = new File(url.getFile()).getParentFile();
                // listen for changes to the service registration file
                try {
                    serviceWatcherThread.addWatch(service.getName(), Paths.get(file.toURI()), () -> onServiceChange(callback, serviceLoader, fullName));
                } catch (IOException e) {
                    // perhaps fallback to reloading and fetching from service loader at intervals?
                }
                return null;
            }, () -> {
                try {
                    callback.fire();
                } catch (Throwable t) {
                    // Checkstyle: stop warning message from guest code
                    System.err.println("[HotSwap API]: Unexpected exception while running service change action");
                    // Checkstyle: resume warning message from guest code
                    t.printStackTrace();
                }
            }));
        } catch (IOException e) {
            // perhaps fallback to reloading and fetching from service loader at intervals?
            return;
        }
    }

    private void ensureInitialized() throws IOException {
        // start watcher threads
        if (serviceWatcherThread == null) {
            serviceWatcherThread = new WatcherThread();
            serviceWatcherThread.start();
            urlWatcher = new URLWatcher();
            urlWatcher.startWatching();
        }
    }

    private synchronized void onServiceChange(HotSwapAction callback, ServiceLoader<?> serviceLoader, String fullName) {
        // sanity-check that the file modification has led to actual changes in services
        serviceLoader.reload();
        Set<String> currentServiceImpl = services.getOrDefault(fullName, Collections.emptySet());
        Set<String> changedServiceImpl = Collections.synchronizedSet(new HashSet<>(currentServiceImpl.size() + 1));
        Iterator<?> it = serviceLoader.iterator();
        boolean callbackFired = false;
        while (it.hasNext()) {
            try {
                Object o = it.next();
                changedServiceImpl.add(o.getClass().getName());
                if (!currentServiceImpl.contains(o.getClass().getName())) {
                    // new service implementation detected
                    callback.fire();
                    callbackFired = true;
                    break;
                }
            } catch (ServiceConfigurationError e) {
                // services that we're not able to instantiate are irrelevant
            }
        }

        if (!callbackFired && changedServiceImpl.size() != currentServiceImpl.size()) {
            // removed service implementation, fire callback
            callback.fire();
        }
        // update the cached services
        services.put(fullName, changedServiceImpl);
    }

    private final class WatcherThread extends Thread {

        private final WatchService watchService;
        private final Map<ResourceInfo, Runnable> watchActions = Collections.synchronizedMap(new HashMap<>());

        private WatcherThread() throws IOException {
            super("hotswap-watcher-1");
            this.setDaemon(true);
            this.watchService = FileSystems.getDefault().newWatchService();
        }

        public void addWatch(String resourceName, Path dir, Runnable callback) throws IOException {
            dir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
            watchActions.put(new ResourceInfo(dir, resourceName), callback);
        }

        @Override
        public void run() {
            WatchKey key;
            try {
                while ((key = watchService.take()) != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                            continue;
                        }
                        String fileName = event.context().toString();
                        Path watchPath = null;
                        Watchable watchable = key.watchable();
                        if (watchable instanceof Path) {
                            watchPath = (Path) watchable;
                        }
                        if (watchPath == null) {
                            continue;
                        }
                        // object used for comparison with cache
                        ResourceInfo resourceInfo = new ResourceInfo(watchPath, fileName, false);
                        if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                            if (watchActions.containsKey(resourceInfo)) {
                                // IDEs will typically perform a delete -> create on build
                                // so we need to re-register the path to the service when/if
                                // the resource has been recreated again
                                for (ResourceInfo info : watchActions.keySet()) {
                                    if (info.equals(resourceInfo)) {
                                        watchForRecreation(info);
                                        break;
                                    }
                                }
                            }
                            continue;
                        }
                        if (watchActions.containsKey(resourceInfo)) {
                            resourceInfo.updateChecksum();
                            try {
                                watchActions.get(resourceInfo).run();
                            } catch (Throwable t) {
                                // Checkstyle: stop warning message from guest code
                                System.err.println("[HotSwap API]: Unexpected exception while running resource change action for: " + resourceInfo.resourceName);
                                // Checkstyle: resume warning message from guest code
                                t.printStackTrace();
                            }
                        }
                    }
                    key.reset();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Espresso HotSwap service watcher thread was interupted!");
            }
        }

        private void watchForRecreation(ResourceInfo resourceInfo) {
            // wait up to 1 min. for the file to be recreated
            long stopWaiting = System.currentTimeMillis() + 60 * 1000;

            try {
                Path resourcePath = resourceInfo.watchPath.resolve(resourceInfo.resourceName);
                boolean recreated = false;
                while (!recreated && System.currentTimeMillis() < stopWaiting) {
                    if (resourcePath.toFile().exists()) {
                        recreated = true;
                    } else {
                        Thread.sleep(5);
                    }
                }
                if (recreated) {
                    // compare recreated resource with info to see if actual changes were made
                    String existingChecksum = resourceInfo.getChecksum();
                    String newChecksum = calculateChecksum(resourceInfo.watchPath, resourceInfo.resourceName);
                    if (!existingChecksum.equals(newChecksum)) {
                        resourceInfo.updateChecksum(newChecksum);
                        // fire the change listener
                        try {
                            watchActions.get(resourceInfo).run();
                        } catch (Throwable t) {
                            // Checkstyle: stop warning message from guest code
                            System.err.println("[HotSwap API]: Unexpected exception while running resource change action for: " + resourceInfo.resourceName);
                            // Checkstyle: resume warning message from guest code
                            t.printStackTrace();
                        }
                    }
                    // re-add watch on parent path
                    resourceInfo.watchPath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
                }
            } catch (Exception e) {

            }
        }
    }

    private static String calculateChecksum(Path watchPath, String resourceName) {
        try {
            byte[] b = Files.readAllBytes(watchPath.resolve(resourceName));
            return bytesToHex(MessageDigest.getInstance("MD5").digest(b));
        } catch (Exception e) {
            // Checkstyle: stop warning message from guest code
            System.err.println("[HotSwap API]: unable to calculate checksum for watched resource " + resourceName);
            // Checkstyle: resume warning message from guest code
        }
        return "1";
    }

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    private static final class ResourceInfo {
        private final Path watchPath;
        private final String resourceName;
        private String checksum;

        private ResourceInfo(Path watchPath, String resourceName, boolean calculateChecksum) {
            this.watchPath = watchPath;
            this.resourceName = resourceName;
            checksum = calculateChecksum ? calculateChecksum(watchPath, resourceName) : null;
        }

        private ResourceInfo(Path watchPath, String resourceName) {
            this(watchPath, resourceName, true);
        }

        public void updateChecksum() {
            checksum = calculateChecksum(watchPath, resourceName);
        }

        public void updateChecksum(String newChecksum) {
            checksum = newChecksum;
        }

        public String getChecksum() {
            return checksum;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ResourceInfo that = (ResourceInfo) o;
            return watchPath.equals(that.watchPath) && resourceName.equals(that.resourceName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(watchPath, resourceName);
        }
    }

    private final class URLWatcher {

        private final List<URLServiceState> cache = Collections.synchronizedList(new ArrayList<>());

        public void addWatch(URLServiceState state) {
            synchronized (cache) {
                cache.add(state);
            }
        }

        public void startWatching() {
            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    synchronized (cache) {
                        for (URLServiceState urlServiceState : cache) {
                            urlServiceState.detectChanges();
                        }
                    }
                }
            };
            ScheduledExecutorService es = Executors.newSingleThreadScheduledExecutor();
            es.scheduleWithFixedDelay(task, 500, 500, TimeUnit.MILLISECONDS);
        }
    }

    private final class URLServiceState {
        private List<URL> knownURLs;
        private final ClassLoader classLoader;
        private final String fullName;
        private final Function<URL, Void> onAddedURL;
        private final Runnable onRemovedURL;

        private URLServiceState(ArrayList<URL> initialURLs, ClassLoader classLoader, String fullName, Function<URL, Void> onAddedURL, Runnable onRemovedURL) {
            this.knownURLs = Collections.synchronizedList(initialURLs);
            this.classLoader = classLoader;
            this.fullName = fullName;
            this.onAddedURL = onAddedURL;
            this.onRemovedURL = onRemovedURL;
        }

        private void detectChanges() {
            try {
                boolean changed = false;
                ArrayList<URL> currentURLs = new ArrayList<>(knownURLs.size());
                Enumeration<URL> urls;
                if (classLoader == null) {
                    urls = ClassLoader.getSystemResources(fullName);
                } else {
                    urls = classLoader.getResources(fullName);
                }

                while (urls.hasMoreElements()) {
                    URL url = urls.nextElement();
                    if ("file".equals(url.getProtocol())) {
                        currentURLs.add(url);
                        if (!knownURLs.contains(url)) {
                            changed = true;
                            onAddedURL.apply(url);
                        }
                    }
                }
                if (currentURLs.size() != knownURLs.size()) {
                    changed = true;
                    onRemovedURL.run();
                }
                if (changed) {
                    // update cache
                    knownURLs = currentURLs;
                }
            } catch (IOException e) {
                return;
            }
        }
    }
}
