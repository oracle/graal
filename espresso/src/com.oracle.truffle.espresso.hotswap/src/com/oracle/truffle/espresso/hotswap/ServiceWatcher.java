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

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
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
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

final class ServiceWatcher {

    private static final String PREFIX = "META-INF/services/";

    private static final WatchEvent.Kind<?>[] WATCH_KINDS = new WatchEvent.Kind[]{
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.OVERFLOW};

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
            try {
                Path path = Paths.get(url.toURI());
                serviceWatcherThread.addWatch(path, () -> callback.fire());
                return true;
            } catch (URISyntaxException e) {
                return false;
            }
        }
        return false;
    }

    public synchronized void addServiceWatcher(Class<?> service, ClassLoader loader, HotSwapAction callback) throws IOException {
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
                try {
                    Path path = Paths.get(url.toURI());
                    // listen for changes to the service registration file
                    initialURLs.add(url);
                    serviceWatcherThread.addWatch(path, () -> onServiceChange(callback, serviceLoader, fullName));
                } catch (URISyntaxException e) {
                    throw new IOException(e);
                }
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
            try {
                Path path = Paths.get(url.toURI());
                // listen for changes to the service registration file
                serviceWatcherThread.addWatch(path, () -> onServiceChange(callback, serviceLoader, fullName));
            } catch (Exception e) {
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
        private final Map<Path, ServiceWatcher.State> watchActions = Collections.synchronizedMap(new HashMap<>());
        private final Map<Path, Set<Path>> deletedFolderMap = Collections.synchronizedMap(new HashMap<>());
        private final Map<Path, Set<Path>> createdFolderMap = Collections.synchronizedMap(new HashMap<>());

        private WatcherThread() throws IOException {
            super("hotswap-watcher-1");
            this.setDaemon(true);
            this.watchService = FileSystems.getDefault().newWatchService();
        }

        public void addWatch(Path resourcePath, Runnable callback) throws IOException {
            watchActions.put(resourcePath, new ServiceWatcher.State(calculateChecksum(resourcePath), callback));
            // register watch on parent folder
            Path dir = resourcePath.getParent();
            dir.register(watchService, WATCH_KINDS);

            // also register an ENTRY_DELETE listener on the parents parent folder to manage
            // recreation on folders correctly
            addDeletedFolder(dir, resourcePath);
            dir.getParent().register(watchService, WATCH_KINDS);
        }

        private void addDeletedFolder(Path path, Path leaf) {
            Set<Path> set = deletedFolderMap.get(path);
            if (set == null) {
                set = new HashSet<>();
                deletedFolderMap.put(path, set);
            }
            set.add(leaf);
        }

        private void addCreatedFolder(Path path, Path leaf) {
            Set<Path> set = createdFolderMap.get(path);
            if (set == null) {
                set = new HashSet<>();
                createdFolderMap.put(path, set);
            }
            set.add(leaf);
        }

        @Override
        public void run() {
            WatchKey key;
            try {
                while ((key = watchService.take()) != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                            // OK, let's do a directory scan for watch files
                            for (Path path : watchActions.keySet()) {
                                scanDir(path.getParent());
                            }
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
                        Path resourcePath = watchPath.resolve(fileName);
                        if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                            if (watchActions.containsKey(resourcePath)) {
                                // IDE sometimes perform a delete -> create cycle
                                // we only fire actions when the resource was modified,
                                // so no need for further actions here
                                continue;
                            } else if (deletedFolderMap.containsKey(resourcePath)) {
                                handleDeletedFolderEvent(resourcePath);
                            }
                            continue;
                        }
                        if (watchActions.containsKey(resourcePath)) {
                            detectChange(resourcePath);
                            continue;
                        }
                        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                            // check for parent folder recreation
                            if (createdFolderMap.containsKey(resourcePath)) {
                                handleCreatedFolderEvent(resourcePath);
                            }
                        }
                    }
                    key.reset();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Espresso HotSwap service watcher thread was interupted!");
            }
        }

        private void handleCreatedFolderEvent(Path resourcePath) {
            // remove tracking of recreated folder
            Set<Path> leaves = createdFolderMap.remove(resourcePath);
            // remove delete tracking of leaves of the parent folder
            deletedFolderMap.getOrDefault(resourcePath.getParent(), Collections.emptySet()).removeAll(leaves);
            for (Path leaf : leaves) {
                Path parent = leaf.getParent();
                if (parent.equals(resourcePath)) {
                    try {
                        addDeletedFolder(parent, leaf);
                        // re-register for this leaf
                        parent.register(watchService, WATCH_KINDS);
                        // register for parent deletion
                        parent.getParent().register(watchService, WATCH_KINDS);
                    } catch (IOException e) {
                        // continue search for other leaves
                    }
                    // scan dir to make sure we haven't missed
                    // recreation event
                    scanDir(resourcePath);
                } else {
                    boolean folderExist = false;
                    Path current = leaf;
                    while (!current.equals(resourcePath) && !folderExist) {
                        // track creation of leaf parent folders
                        // up until the created resource path for the event
                        if (Files.exists(current.getParent())) {
                            folderExist = true;
                            addDeletedFolder(current.getParent(), leaf);
                            addCreatedFolder(current, leaf);
                            try {
                                current.getParent().register(watchService, WATCH_KINDS);
                                current.getParent().getParent().register(watchService, WATCH_KINDS);
                            } catch (IOException e) {

                            }
                            scanDir(current.getParent());
                        }
                        current = current.getParent();
                    }
                }
            }
        }

        private void handleDeletedFolderEvent(Path resourcePath) {
            Path path = resourcePath;
            boolean folderExist = false;

            while (!folderExist && path != null) {
                Set<Path> leaves = deletedFolderMap.remove(path);
                if (deletedFolderMap == null) {
                    // the folder was recreated in the mean time
                    // so nothing further to do here
                    break;
                }
                // register parent and watch for re-creation for all leaves
                for (Path leaf : leaves) {
                    addCreatedFolder(path, leaf);
                    addDeletedFolder(path.getParent(), leaf);
                }
                try {
                    // watch for creation
                    path.getParent().register(watchService, WATCH_KINDS);
                    // watch for deletion
                    path.getParent().getParent().register(watchService, WATCH_KINDS);
                    if (Files.exists(path) && Files.isReadable(path)) {
                        // watch in place
                        folderExist = true;
                        scanDir(path);
                    }
                } catch (IOException e) {
                    // parent folder also deleted, continue search
                }
                path = path.getParent();
            }
        }

        private void detectChange(Path path) {
            ServiceWatcher.State state = watchActions.get(path);
            byte[] existingChecksum = state.getChecksum();
            byte[] newChecksum = state.updateChecksum(path);
            if (!MessageDigest.isEqual(existingChecksum, newChecksum)) {
                try {
                    state.getAction().run();
                } catch (Throwable t) {
                    // Checkstyle: stop warning message from guest code
                    System.err.println("[HotSwap API]: Unexpected exception while running resource change action for: " + path);
                    // Checkstyle: resume warning message from guest code
                    t.printStackTrace();
                }
            }
        }

        private void scanDir(Path dir) {
            try (Stream<Path> list = Files.list(dir)) {
                list.forEach((path) -> {
                    if (watchActions.containsKey(path)) {
                        detectChange(path);
                    } else if (createdFolderMap.containsKey(path)) {
                        handleCreatedFolderEvent(path);
                    }
                });
            } catch (IOException e) {
                // ignore deleted or invalid files here
            }
        }
    }

    private static byte[] calculateChecksum(Path resourcePath) {
        try {
            byte[] buffer = new byte[4096];
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (InputStream is = Files.newInputStream(resourcePath); DigestInputStream dis = new DigestInputStream(is, md)) {
                // read through the entire stream which updates the message digest underneath
                while (dis.read(buffer) != -1) {
                    dis.read();
                }
            }
            return md.digest();
        } catch (Exception e) {
            // Checkstyle: stop warning message from guest code
            System.err.println("[HotSwap API]: unable to calculate checksum for watched resource " + resourcePath);
            // Checkstyle: resume warning message from guest code
        }
        return new byte[]{-1};
    }

    private static final class State {
        private byte[] checksum;
        private Runnable action;

        State(byte[] checksum, Runnable action) {
            this.checksum = checksum;
            this.action = action;
        }

        public byte[] updateChecksum(Path resourcePath) {
            return checksum = calculateChecksum(resourcePath);
        }

        public byte[] getChecksum() {
            return checksum;
        }

        public Runnable getAction() {
            return action;
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
