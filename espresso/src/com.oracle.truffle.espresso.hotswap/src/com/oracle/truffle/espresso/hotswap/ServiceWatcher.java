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
import java.util.Objects;
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

    private final Map<String, Set<String>> services = new HashMap<>(4);
    private final WatchEvent.Kind<?>[] WATCH_KINDS = new WatchEvent.Kind[]{
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.OVERFLOW};

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
                serviceWatcherThread.addWatch(path.getFileName().toString(), path.getParent(), () -> callback.fire());
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
                    serviceWatcherThread.addWatch(service.getName(), path.getParent(), () -> onServiceChange(callback, serviceLoader, fullName));
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
                serviceWatcherThread.addWatch(service.getName(), path.getParent(), () -> onServiceChange(callback, serviceLoader, fullName));
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
        private final Map<ResourceInfo, ServiceWatcher.State> watchActions = Collections.synchronizedMap(new HashMap<>());
        private final Map<ResourceInfo, List<ResourceInfo>> deletedFolderMap = Collections.synchronizedMap(new HashMap<>());
        private final Map<ResourceInfo, List<ResourceInfo>> createdFolderMap = Collections.synchronizedMap(new HashMap<>());

        private WatcherThread() throws IOException {
            super("hotswap-watcher-1");
            this.setDaemon(true);
            this.watchService = FileSystems.getDefault().newWatchService();
        }

        public void addWatch(String resourceName, Path dir, Runnable callback) throws IOException {
            // register watch on parent folder
            ResourceInfo resourceInfo = new ResourceInfo(dir, resourceName);
            dir.register(watchService, WATCH_KINDS);
            watchActions.put(resourceInfo, new ServiceWatcher.State(calculateChecksum(dir, resourceName), callback));

            // also register an ENTRY_DELETE listener on the parents parent folder to manage
            // recreation on folders correctly
            dir.getParent().register(watchService, WATCH_KINDS);
            ResourceInfo parentInfo = new ResourceInfo(dir.getParent(), dir.getFileName().toString(), resourceInfo, resourceInfo);
            addDeletedFolder(parentInfo);
        }

        private void addDeletedFolder(ResourceInfo info) {
            List<ResourceInfo> list = deletedFolderMap.get(info);
            if (list == null) {
                list = new ArrayList<>();
                deletedFolderMap.put(info, list);
            }
            // check if resource with same leaf is already registered
            boolean present = false;
            for (ResourceInfo resourceInfo : list) {
                if (resourceInfo.leaf == info.leaf) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                list.add(info);
            }
        }

        private void addCreatedFolder(ResourceInfo info) {
            List<ResourceInfo> list = createdFolderMap.get(info);
            if (list == null) {
                list = new ArrayList<>();
                createdFolderMap.put(info, list);
            }
            // check if resource with same leaf is already registered
            boolean present = false;
            for (ResourceInfo resourceInfo : list) {
                if (resourceInfo.leaf == info.leaf) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                list.add(info);
            }
        }

        @Override
        public void run() {
            WatchKey key;
            try {
                while ((key = watchService.take()) != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                            // OK, let's do a directory scan for watch files
                            for (ResourceInfo resourceInfo : watchActions.keySet()) {
                                scanDir(resourceInfo);
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
                        ResourceInfo resourceInfo = new ResourceInfo(watchPath, fileName);
                        // resourceInfo.watchPath + "/" + resourceInfo.resourceName);
                        if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                            if (watchActions.containsKey(resourceInfo)) {
                                // IDE sometimes perform a delete -> create cycle
                                // we only fire actions when the resource was modified,
                                // so no need for further actions here
                                continue;
                            } else if (deletedFolderMap.containsKey(resourceInfo)) {
                                // get list tracked under this folder and remove
                                // from cache, since we only track current state
                                List<ResourceInfo> resourceInfos = deletedFolderMap.remove(resourceInfo);
                                for (ResourceInfo info : resourceInfos) {
                                    // The parent folder was deleted, so we must recursively
                                    // register creation listeners in the parent folder
                                    // hierarchy to handle recreation of folders.
                                    // We must also register a new deletion listener on the
                                    // parent to handle recursive deletion of a file tree.
                                    Path path = info.watchPath;
                                    boolean folderExist = false;
                                    while (!folderExist && path != null) {
                                        // keep track of file tree

                                        ResourceInfo newInfo = new ResourceInfo(path.getParent(), path.getFileName().toString(), info, info.leaf);
                                        try {
                                            path.register(watchService, WATCH_KINDS);
                                            path.getParent().register(watchService, WATCH_KINDS);
                                            addCreatedFolder(info);
                                            addDeletedFolder(newInfo);
                                            if (Files.exists(path) && Files.isReadable(path)) {
                                                // watch in place
                                                folderExist = true;
                                            }
                                        } catch (IOException e) {
                                            // parent folder also deleted, continue search
                                        }
                                        info = newInfo;
                                        path = path.getParent();
                                    }
                                }
                            }
                            continue;
                        }
                        if (watchActions.containsKey(resourceInfo)) {
                            detectChange(resourceInfo);
                            continue;
                        }
                        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                            // check for parent folder recreation
                            if (createdFolderMap.containsKey(resourceInfo)) {
                                // remove tracking of recreated folder
                                List<ResourceInfo> resourceInfos = createdFolderMap.remove(resourceInfo);
                                for (ResourceInfo info : resourceInfos) {
                                    // remove tracking of parent folder
                                    ResourceInfo parentResource = new ResourceInfo(info.watchPath.getParent(), info.watchPath.toFile().getName());
                                    deletedFolderMap.remove(parentResource);

                                    // recursively follow creation until leaf resource
                                    ResourceInfo current = info;
                                    while (current.child != null) {
                                        try {
                                            current.child.watchPath.register(watchService, WATCH_KINDS);
                                            if (current.child.child != null) {
                                                addCreatedFolder(current.child);
                                            }
                                            // watch for folder deletion
                                            current.watchPath.register(watchService, WATCH_KINDS);
                                            addDeletedFolder(current);
                                            // we could miss creation events inside the
                                            // folder, so kick off a directory scan
                                            scanDir(current.child);
                                        } catch (IOException e) {
                                            // continue search
                                        }
                                        current = current.child;
                                    }
                                }
                            }
                        }
                    }
                    key.reset();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Espresso HotSwap service watcher thread was interupted!");
            }
        }

        private void detectChange(ResourceInfo resourceInfo) {
            ServiceWatcher.State state = watchActions.get(resourceInfo);
            byte[] existingChecksum = state.getChecksum();
            byte[] newChecksum = state.updateChecksum(resourceInfo.watchPath, resourceInfo.resourceName);
            if (!MessageDigest.isEqual(existingChecksum, newChecksum)) {
                try {
                    state.getAction().run();
                } catch (Throwable t) {
                    // Checkstyle: stop warning message from guest code
                    System.err.println("[HotSwap API]: Unexpected exception while running resource change action for: " + resourceInfo.resourceName);
                    // Checkstyle: resume warning message from guest code
                    t.printStackTrace();
                }
            }
        }

        private void scanDir(ResourceInfo info) {
            try (Stream<Path> list = Files.list(info.watchPath)) {
                list.forEach((path) -> {
                    ResourceInfo resourceInfo = new ResourceInfo(info.watchPath, path.toFile().getName());
                    if (watchActions.containsKey(resourceInfo)) {
                        detectChange(resourceInfo);
                    }
                });
            } catch (IOException e) {
                // ignore deleted or invalid files here
            }
        }
    }

    private static byte[] calculateChecksum(Path watchPath, String resourceName) {
        try {
            byte[] buffer = new byte[4096];
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (InputStream is = Files.newInputStream(watchPath.resolve(resourceName)); DigestInputStream dis = new DigestInputStream(is, md)) {
                // read through the entire stream which updates the message digest underneath
                while (dis.read(buffer) != -1) {
                    dis.read();
                }
            }
            return md.digest();
        } catch (Exception e) {
            // Checkstyle: stop warning message from guest code
            System.err.println("[HotSwap API]: unable to calculate checksum for watched resource " + resourceName);
            // Checkstyle: resume warning message from guest code
        }
        return new byte[]{-1};
    }

    private static final class ResourceInfo {
        private final Path watchPath;
        private final String resourceName;
        private ResourceInfo child;
        private ResourceInfo leaf;

        private ResourceInfo(Path watchPath, String resourceName) {
            this.watchPath = watchPath;
            this.resourceName = resourceName;
        }

        public ResourceInfo(Path watchPath, String resourceName, ResourceInfo child, ResourceInfo leaf) {
            this(watchPath, resourceName);
            this.child = child;
            this.leaf = leaf;
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

    private static final class State {
        private byte[] checksum;
        private Runnable action;

        State(byte[] checksum, Runnable action) {
            this.checksum = checksum;
            this.action = action;
        }

        public byte[] updateChecksum(Path watchPath, String resourceName) {
            return checksum = calculateChecksum(watchPath, resourceName);
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
