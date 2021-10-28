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

    private static final WatchEvent.Kind<?>[] ALL_WATCH_KINDS = new WatchEvent.Kind<?>[]{
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.OVERFLOW};

    private static final WatchEvent.Kind<?>[] CREATE_KINDS = new WatchEvent.Kind<?>[]{
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.OVERFLOW};

    private static final WatchEvent.Kind<?>[] DELETE_KINDS = new WatchEvent.Kind<?>[]{
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.OVERFLOW};

    private static final WatchEvent.Kind<?>[] CREATE_DELETE_KINDS = new WatchEvent.Kind<?>[]{
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.OVERFLOW};

    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

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

        // The direct watched resources is mapped to the current known resource state.
        // The state is managed by means of a file checksum.
        private final Map<Path, ServiceWatcher.State> watchActions = Collections.synchronizedMap(new HashMap<>());

        // Track existing folders for which we need notification upon deletion.
        // This is relevant when parent folders of a watched resource are deleted.
        private final Map<Path, Set<Path>> watchedForDeletion = new HashMap<>();

        // Track non-existing folders for which we need notification upon creation.
        // This is relevant when parent folders of a watched resource was previously deleted,
        // and then recreated again.
        private final Map<Path, Set<Path>> watchedForCreation = new HashMap<>();

        // Map of registered file-system watches to allow us to cancel of watched path
        // in case it's no longer needed for detecting changes to leaf resources.
        private final Map<Path, WatchKey> registeredWatches = new HashMap<>();

        // Map all active file watches with the set of paths for which the watch was
        // registered to enable us to cancel watches when deleted folders are recreated.
        // A counter on each path reason is kept for managing when we should cancel the
        // file system watch on the registered path.
        private final Map<Path, Map<Path, Integer>> activeFileWatches = new HashMap<>();

        private WatcherThread() throws IOException {
            super("hotswap-watcher-1");
            this.setDaemon(true);
            this.watchService = FileSystems.getDefault().newWatchService();
        }

        public void addWatch(Path resourcePath, Runnable callback) throws IOException {
            watchActions.put(resourcePath, new ServiceWatcher.State(calculateChecksum(resourcePath), callback));
            // register watch on parent folder
            Path dir = resourcePath.getParent();
            if (dir == null) {
                throw new IOException("parent directory doesn't exist for: " + resourcePath);
            }
            registerFileSystemWatch(dir, resourcePath, ALL_WATCH_KINDS);
        }

        private void addWatchedDeletedFolder(Path path, Path leaf) {
            Set<Path> set = watchedForDeletion.get(path);
            if (set == null) {
                set = new HashSet<>();
                watchedForDeletion.put(path, set);
            }
            set.add(leaf);
        }

        private void addWatchedCreatedFolder(Path path, Path leaf) {
            Set<Path> set = watchedForCreation.get(path);
            if (set == null) {
                set = new HashSet<>();
                watchedForCreation.put(path, set);
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
                            // OK, let's do a directory scan for watched files
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

                        Path resourcePath = watchPath.resolve(fileName);

                        if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                            if (watchActions.containsKey(resourcePath)) {
                                handleDeletedResource(watchPath, resourcePath);
                            } else if (watchedForDeletion.containsKey(resourcePath)) {
                                handleDeletedFolderEvent(resourcePath);
                            }
                        } else if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                            if (watchActions.containsKey(resourcePath)) {
                                handleCreatedResource(watchPath, resourcePath);
                            } else if (watchedForCreation.containsKey(resourcePath)) {
                                handleCreatedFolderEvent(resourcePath);
                            }
                        } else if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                            if (watchActions.containsKey(resourcePath)) {
                                detectChange(resourcePath);
                            }
                        }
                    }
                    key.reset();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Espresso HotSwap service watcher thread was interrupted!");
            }
        }

        private void handleDeletedResource(Path watchPath, Path resourcePath) {
            // the parent folder could also be deleted,
            // so register a delete watch on the parent
            addWatchedDeletedFolder(watchPath, resourcePath);
            removeFilesystemWatchReason(watchPath, resourcePath);
            Path parent = watchPath.getParent();

            if (parent == null) {
                return;
            }
            try {
                registerFileSystemWatch(parent, watchPath, DELETE_KINDS);
            } catch (IOException e) {
                handleDeletedFolderEvent(watchPath);
                return;
            }

            if (!Files.exists(watchPath)) {
                handleDeletedFolderEvent(watchPath);
            } else {
                // We could have lost the file system watch on watchPath,
                // so re-establish the file system watch and do a fresh scan
                try {
                    registerFileSystemWatch(watchPath, resourcePath, ALL_WATCH_KINDS);
                } catch (IOException e) {
                    // OK, now watchPath has been deleted,
                    // so handle as deleted
                    handleDeletedFolderEvent(watchPath);
                }
                scanDir(watchPath);
            }
        }

        private void handleCreatedResource(Path watchPath, Path resourcePath) {
            removeFilesystemWatchReason(watchPath.getParent(), watchPath);
            Set<Path> set = watchedForDeletion.getOrDefault(watchPath, Collections.emptySet());
            set.remove(resourcePath);
            if (set.isEmpty()) {
                watchedForDeletion.remove(watchPath);
            }
            detectChange(resourcePath);
        }

        private synchronized void registerFileSystemWatch(Path path, Path reason, WatchEvent.Kind<?>[] kinds) throws IOException {
            // register the watch and store the watch key
            WatchKey watchKey = path.register(watchService, kinds);
            registeredWatches.put(path, watchKey);

            // keep track of the file watch
            Map<Path, Integer> reasons = activeFileWatches.get(path);
            if (reasons == null) {
                reasons = new HashMap<>(1);
                activeFileWatches.put(path, reasons);
            }
            // increment the counter for the reason
            reasons.put(reason, reasons.getOrDefault(reason, 0) + 1);
        }

        private synchronized void removeFilesystemWatchReason(Path path, Path reason) {
            // remove the reason from file watch state
            Map<Path, Integer> reasons = activeFileWatches.getOrDefault(path, Collections.emptyMap());
            int count = reasons.getOrDefault(reason, 0);
            if (count <= 1) {
                reasons.remove(reason);
            } else {
                // decrement the reason count
                reasons.put(reason, reasons.get(reason) - 1);
            }

            // only cancel the file system watch if the last reason
            // to have it was removed
            if (reasons.isEmpty()) {
                activeFileWatches.remove(path);
                // cancel the file watch and remove from state
                WatchKey watchKey = registeredWatches.remove(path);
                if (watchKey != null) {
                    watchKey.cancel();
                }
            }
        }

        private void handleCreatedFolderEvent(Path path) {
            Path parent = path.getParent();
            if (parent == null) { // find bugs complains about potential NPE
                // this should never happen
                return;
            }

            // get leaves and update state
            Set<Path> leaves = watchedForCreation.remove(path);

            // transfer delete watch from parent to path
            Set<Path> deleteLeaves = watchedForDeletion.getOrDefault(parent, Collections.emptySet());
            deleteLeaves.removeAll(leaves);
            if (deleteLeaves.isEmpty()) {
                // remove from internal state
                watchedForDeletion.remove(parent);
            }

            Set<Path> directResources = new HashSet<>();
            Set<Path> childrenToWatch = new HashSet<>();

            for (Path leaf : leaves) {
                addWatchedDeletedFolder(path, leaf);
                removeFilesystemWatchReason(parent.getParent(), parent);
                removeFilesystemWatchReason(parent, path);

                // mark direct resources found
                if (path.equals(leaf.getParent())) {
                    directResources.add(leaf);
                    // remove the reason for the file watches
                    continue;
                }

                // we need to find the direct child of the input path
                // towards the leaf and add creation watches on those children
                Path current = leaf;
                while (current != null && !current.equals(path)) {
                    Path currentParent = current.getParent();
                    if (path.equals(currentParent)) {
                        addWatchedCreatedFolder(current, leaf);
                        childrenToWatch.add(current);
                    }
                    current = currentParent;
                }
            }

            // done updating internal state, ready to register the new file watches now
            try {
                for (Path directResource : directResources) {
                    registerFileSystemWatch(path, directResource, ALL_WATCH_KINDS);
                }
                for (Path toWatch : childrenToWatch) {
                    registerFileSystemWatch(path, toWatch, CREATE_KINDS);
                }
                // we could have missed file creation within the path so do a scan
                scanDir(path);
            } catch (IOException e) {
                // couldn't register all file watches
                // check if path has been removed
                if (!Files.exists(path)) {
                    handleDeletedFolderEvent(path);
                } else {
                    // Checkstyle: stop warning message from guest code
                    System.err.println("[HotSwap API]: Unexpected exception while handling creation of path: " + path);
                    // Checkstyle: resume warning message from guest code
                    e.printStackTrace();
                }
            }
        }

        // The main idea is that we need to transfer the leaf paths
        // to the parent directory, since this will be the new parent
        // folder we keep track of.
        //
        // Furthermore, we need to transfer the created folder watches,
        // to the input path or create new ones.
        private void handleDeletedFolderEvent(Path path) {
            Path parent = path.getParent();
            // stop at the root, but this should never happen
            if (parent == null) {
                return;
            }

            // update state
            Set<Path> leaves = watchedForDeletion.remove(path);
            if (leaves == null) {
                // must have been recreated and handled already
                // so no further actions required here
                return;
            }

            for (Path leaf : leaves) {
                // transfer leaves to parent for deletion
                addWatchedDeletedFolder(parent, leaf);
                transferCreationWatch(path, leaf);
            }
            removeFilesystemWatchReason(parent, path);

            // done updating internal state

            // update file-system watches to new state
            try {
                Path grandParent = parent.getParent();
                if (grandParent != null) {
                    registerDeleteFileSystemWatch(grandParent, parent);
                }
                if (Files.exists(parent) && Files.isReadable(parent)) {
                    // OK, parent exists, so register create watch on path
                    registerCreateFileSystemWatch(parent, path);
                    // make a scan to make sure we didn't miss any events
                    // just prior to registering the file system watch
                    scanDir(parent);
                } else {
                    handleDeletedFolderEvent(parent);
                }
            } catch (IOException e) {
                // didn't exist, move on to parent then
                handleDeletedFolderEvent(parent);
            }
        }

        private void transferCreationWatch(Path path, Path leaf) {
            // we know path was deleted, so we need to find the direct
            // child of the path to the leaf if any and transfer the
            // create watch to the input path
            Path current = leaf;
            while (current != path) {
                Path parent = current.getParent();
                if (parent != null && parent.equals(path)) {
                    // found the direct child path!
                    // transfer creation watch from the
                    // found child to the input path
                    watchedForCreation.remove(current);
                    addWatchedCreatedFolder(path, leaf);
                    // remove the reason for the file system watch
                    removeFilesystemWatchReason(path, current);
                    break;
                }
                current = parent;
            }
        }

        private void registerDeleteFileSystemWatch(Path path, Path reason) throws IOException {
            // check if there's a create watch also registered
            if (!watchedForCreation.getOrDefault(path, Collections.emptySet()).isEmpty()) {
                // OK, then register both for creation and deletion
                registerFileSystemWatch(path, reason, CREATE_DELETE_KINDS);
            } else {
                registerFileSystemWatch(path, reason, DELETE_KINDS);
            }
        }

        private void registerCreateFileSystemWatch(Path path, Path reason) throws IOException {
            // check if there's a create watch also registered
            if (!watchedForDeletion.getOrDefault(path, Collections.emptySet()).isEmpty()) {
                // OK, then register both for creation and deletion
                registerFileSystemWatch(path, reason, CREATE_DELETE_KINDS);
            } else {
                registerFileSystemWatch(path, reason, CREATE_KINDS);
            }
        }

        private void detectChange(Path path) {
            ServiceWatcher.State state = watchActions.get(path);
            if (state.hasChanged(path)) {
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
                        handleCreatedResource(dir, path);
                    } else if (watchedForCreation.containsKey(path)) {
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
        return EMPTY_BYTE_ARRAY;
    }

    private static final class State {
        private byte[] checksum;
        private Runnable action;

        State(byte[] checksum, Runnable action) {
            this.checksum = checksum;
            this.action = action;
        }

        public Runnable getAction() {
            return action;
        }

        public boolean hasChanged(Path path) {
            byte[] currentChecksum = calculateChecksum(path);
            if (!MessageDigest.isEqual(currentChecksum, checksum)) {
                checksum = currentChecksum;
                return true;
            }
            // mark as if changed when we can't calculate the checksum
            return checksum.length == 0 || currentChecksum.length == 0;
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
