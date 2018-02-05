/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.driver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.oracle.svm.core.posix.PosixUtils;
import com.oracle.svm.core.posix.headers.Signal;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.hosted.server.NativeImageBuildClient;
import com.oracle.svm.hosted.server.SubstrateServerMessage.ServerCommand;

final class NativeImageServer extends NativeImage {

    private static final String serverDirPrefix = "server-id-";
    private static final String machineDirPrefix = "machine-id-";
    private static final String sessionDirPrefix = "session-id-";
    private static final String defaultLockFileName = ".lock";

    private static final String pKeyMaxPorts = "MaxPorts";
    private static final String pKeyPortsInUse = "PortsInUse";
    private static final String machineProperties = "machine.properties";

    private boolean useServer = true;
    private boolean verboseServer = false;
    private String sessionName = null;

    private volatile Server building = null;
    private final List<FileChannel> openFileChannels = new ArrayList<>();

    NativeImageServer() {
        super();
        registerOptionHandler(new ServerOptionHandler(this));
    }

    private final class Server {
        private static final String serverProperties = "server.properties";
        private static final String buildRequestLog = "build-request.log";

        private static final String pKeyPort = "Port";
        private static final String pKeyPID = "PID";
        private static final String pKeyJavaArgs = "JavaArgs";
        private static final String pKeyBCP = "BootClasspath";
        private static final String pKeyCP = "Classpath";

        final Path serverDir;
        final Instant since;
        Instant lastBuildRequest;
        final int port;
        final int pid;
        final LinkedHashSet<String> serverJavaArgs;
        final LinkedHashSet<Path> serverBootClasspath;
        final LinkedHashSet<Path> serverClasspath;

        private Server(Path serverDir) throws Exception {
            this.serverDir = serverDir;
            Path serverPropertiesPath = serverDir.resolve(serverProperties);
            Map<String, String> properties = loadProperties(serverPropertiesPath);
            this.port = Integer.parseInt(properties.get(pKeyPort));
            this.pid = Integer.parseInt(properties.get(pKeyPID));
            this.serverJavaArgs = new LinkedHashSet<>(Arrays.asList(properties.get(pKeyJavaArgs).split(" ")));
            this.serverBootClasspath = readClasspath(properties.get(pKeyBCP));
            this.serverClasspath = readClasspath(properties.get(pKeyCP));
            BasicFileAttributes sinceAttrs = Files.readAttributes(serverPropertiesPath, BasicFileAttributes.class);
            this.since = sinceAttrs.creationTime().toInstant();
            updateLastBuildRequest();
        }

        private void updateLastBuildRequest() throws IOException {
            Path buildRequestLogPath = serverDir.resolve(buildRequestLog);
            if (Files.isReadable(buildRequestLogPath)) {
                BasicFileAttributes buildAttrs = Files.readAttributes(buildRequestLogPath, BasicFileAttributes.class);
                lastBuildRequest = buildAttrs.lastModifiedTime().toInstant().plusSeconds(1);
            } else {
                lastBuildRequest = since;
            }
        }

        private LinkedHashSet<Path> readClasspath(String rawClasspathString) {
            LinkedHashSet<Path> result = new LinkedHashSet<>();
            for (String pathStr : rawClasspathString.split(" ")) {
                result.add(Paths.get(pathStr));
            }
            return result;
        }

        private int sendRequest(Consumer<String> out, Consumer<String> err, ServerCommand serverCommand, String... args) {
            List<String> argList = Arrays.asList(args);
            showVerboseMessage(verboseServer, "Sending to server [");
            showVerboseMessage(verboseServer, argList.stream().collect(Collectors.joining(" \\\n")));
            showVerboseMessage(verboseServer, "]");
            int exitCode = NativeImageBuildClient.sendRequest(serverCommand, String.join(" ", argList), port, out, err);
            showVerboseMessage(verboseServer, "Server returns: " + exitCode);
            return exitCode;
        }

        void sendBuildRequest(LinkedHashSet<Path> imageCP, LinkedHashSet<String> imageArgs) {
            withLockDirFileChannel(serverDir, lockFileChannel -> {
                try (FileLock lock = lockFileChannel.tryLock()) {
                    if (lock == null) {
                        showError("A previous build is still in progress. Try again later.");
                    }

                    /* Now we have the server-lock and can send the build-request */
                    List<String> command = new ArrayList<>();
                    command.add("-task=" + "com.oracle.svm.hosted.NativeImageGeneratorRunner");
                    LinkedHashSet<Path> imagecp = new LinkedHashSet<>(serverClasspath);
                    imagecp.addAll(imageCP);
                    command.addAll(Arrays.asList("-imagecp", imagecp.stream().map(Path::toString).collect(Collectors.joining(":"))));
                    command.addAll(imageArgs);
                    showVerboseMessage(isVerbose(), "SendBuildRequest [");
                    showVerboseMessage(isVerbose(), command.stream().collect(Collectors.joining("\n")));
                    showVerboseMessage(isVerbose(), "]");
                    try {
                        /* logfile main purpose is to know when was the last build request */
                        String logFileEntry = command.stream().collect(Collectors.joining(" ", Instant.now().toString() + ": ", "\n"));
                        Files.write(serverDir.resolve(buildRequestLog), logFileEntry.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                        updateLastBuildRequest();
                    } catch (IOException e) {
                        showError("Could not read/write into build-request log file", e);
                    }
                    int requestStatus = sendRequest(System.out::print, System.err::print, ServerCommand.BUILD_IMAGE, command.toArray(new String[command.size()]));
                    if (requestStatus != NativeImageBuildClient.EXIT_SUCCESS) {
                        showError("Processing image build request failed");
                    }
                } catch (IOException e) {
                    showError("Error while trying to lock ServerDir " + serverDir, e);
                }
            });
        }

        boolean isAlive() {
            StringBuilder sb = new StringBuilder();
            boolean alive = sendRequest(sb::append, sb::append, ServerCommand.GET_VERSION) == NativeImageBuildClient.EXIT_SUCCESS;
            showVerboseMessage(verboseServer, "Server version response: " + sb);
            return alive;
        }

        synchronized void shutdown() {
            Signal.kill(pid, Signal.SignalEnum.SIGTERM.getCValue());
            /* Release port only after server stops responding to it */
            long timeout = System.currentTimeMillis() + 60_000;
            showVerboseMessage(verboseServer, "Waiting for " + this + " to die");
            while (isAlive()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    showError("Woke up from waiting for " + this + " to die", e);
                }
                if (timeout < System.currentTimeMillis()) {
                    showError(this + " keeps responding to port " + port + " even after shutdown");
                }
            }
            deleteAllFiles(serverDir);
            releasePortNumber(port);
        }

        String getServerInfo() {
            StringBuilder sb = new StringBuilder();
            sb.append(getClass().getName());
            sb.append("\nServerDir: ").append(serverDir);
            sb.append("\nRunning since: ").append(getDurationString(since));
            sb.append("\nLast build: ");
            if (since.equals(lastBuildRequest)) {
                sb.append("None");
            } else {
                sb.append(getDurationString(lastBuildRequest));
            }
            sb.append("\nPID: ").append(pid);
            sb.append("\nPort: ").append(port);
            sb.append("\nJavaArgs: ").append(serverJavaArgs.stream().collect(Collectors.joining(" ")));
            sb.append("\nBootClasspath: ").append(serverBootClasspath.stream().map(Path::toString).collect(Collectors.joining(":")));
            sb.append("\nClasspath: ").append(serverClasspath.stream().map(Path::toString).collect(Collectors.joining(":")));
            return sb.append('\n').toString();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(getClass().getSimpleName());
            sb.append("(");
            sb.append("pid: ").append(pid);
            sb.append(", ");
            sb.append("port: ").append(port);
            sb.append(")");
            if (since.equals(lastBuildRequest)) {
                /* Subtle way to show that nothing was built yet */
                sb.append('*');
            }
            return sb.toString();
        }

        private String getLivenessInfo(boolean alive) {
            if (alive) {
                return " since: " + getDurationString(since);
            } else {
                return " DEAD";
            }
        }

        private String getLastBuildInfo() {
            if (lastBuildRequest.equals(since)) {
                return " <No builds>";
            } else {
                return " last build: " + getDurationString(lastBuildRequest);
            }
        }
    }

    private String getSessionID() {
        if (sessionName != null) {
            return sessionDirPrefix + sessionName;
        }
        return sessionDirPrefix + Long.toHexString(Unistd.getsid(Unistd.getpid()));
    }

    private static String getMachineID() {
        try {
            return Files.lines(Paths.get("/etc/machine-id")).collect(Collectors.joining("", machineDirPrefix, ""));
        } catch (Exception e) {
            /* Fallback - involves DNS (much slower) */
            return machineDirPrefix + "hostid-" + Long.toHexString(Unistd.gethostid());
        }
    }

    private Path getMachineDir() {
        Path machineDir = getHomeDir().resolve(".native-image").resolve(getMachineID());
        ensureDirectoryExists(machineDir);
        return machineDir;
    }

    private Path getSessionDir() {
        Path sessionDir = getMachineDir().resolve((getSessionID()));
        ensureDirectoryExists(sessionDir);
        return sessionDir;
    }

    private static String getDurationString(Instant since) {
        long seconds = ChronoUnit.SECONDS.between(since, Instant.now());
        long hours = TimeUnit.SECONDS.toHours(seconds);
        seconds -= TimeUnit.HOURS.toSeconds(hours);
        long minutes = TimeUnit.SECONDS.toMinutes(seconds);
        seconds -= TimeUnit.MINUTES.toSeconds(minutes);
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    @SuppressWarnings("try")
    private Server getServerInstance(LinkedHashSet<Path> classpath, LinkedHashSet<Path> bootClasspath, LinkedHashSet<String> javaArgs) {
        /* Maximize reuse by using same VM memory arguments for all server-based image builds */
        replaceArg(javaArgs, oXms, getXmsValue());
        replaceArg(javaArgs, oXmx, getXmxValue());

        Server[] result = {null};
        /* Important - Creating new servers is a machine-exclusive operation */
        withFileChannel(getMachineDir().resolve("create-server.lock"), lockFileChannel -> {
            try (FileLock lock = lockFileChannel(lockFileChannel)) {
                /* Ensure that all dead server entries are gone before we start */
                cleanupServers(false, false, true);
                Path sessionDir = getSessionDir();
                String serverUID = imageServerUID(classpath, bootClasspath, javaArgs);
                Path serverDir = sessionDir.resolve(serverDirPrefix + serverUID);
                if (Files.isDirectory(serverDir)) {
                    try {
                        Server server = new Server(serverDir);
                        if (!server.isAlive()) {
                            showError("Found defunct server:" + server.getServerInfo());
                        }
                        showVerboseMessage(verboseServer, "Reuse running server: " + server);
                        result[0] = server;
                    } catch (Exception e) {
                        showError("Found corrupt ServerDir: " + serverDir, e);
                    }
                } else {
                    int serverPort = acquirePortNumber();
                    if (serverPort < 0) {
                        /* Server limit reached */
                        showVerboseMessage(verboseServer, "Server limit reached -> remove least recently used server");
                        /* Shutdown least recently used within session */
                        Server victim = findVictim(getSessionDirs(false));
                        /* If none found also consider servers from other sessions on machine */
                        if (victim == null) {
                            victim = findVictim(getSessionDirs(true));
                        }
                        if (victim != null) {
                            showVerboseMessage(verboseServer, "Selected server to remove " + victim);
                            victim.shutdown();
                            serverPort = acquirePortNumber();
                            if (serverPort < 0) {
                                showWarning("Cannot acquire new server port despite removing " + victim);
                            }
                        } else {
                            showWarning("Native image server limit exceeded. Use options -server{-list,shutdown[-all]} to fix the problem.");
                        }
                    }
                    if (serverPort >= 0) {
                        /* Instantiate new server and write properties file */
                        Server server = startServer(serverDir, serverPort, classpath, bootClasspath, javaArgs);
                        if (server != null) {
                            showVerboseMessage(verboseServer, "Created new server: " + server);
                        }
                        result[0] = server;
                    }
                }
            } catch (IOException e) {
                showError("ServerInstance-creation locking failed", e);
            }
        });
        return result[0];
    }

    private Server findVictim(List<Path> sessionDirs) {
        return findServers(sessionDirs).stream()
                        .filter(Server::isAlive)
                        .sorted(Comparator.comparing(s -> s.lastBuildRequest))
                        .findFirst().orElse(null);
    }

    private List<Path> getSessionDirs(boolean machineWide) {
        List<Path> sessionDirs = Collections.singletonList(getSessionDir());
        if (machineWide) {
            try {
                sessionDirs = Files.list(getMachineDir())
                                .filter(Files::isDirectory)
                                .filter(sessionDir -> sessionDir.getFileName().toString().startsWith(sessionDirPrefix))
                                .collect(Collectors.toList());
            } catch (IOException e) {
                showError("Accessing MachineDir " + getMachineDir() + " failed", e);
            }
        }
        return sessionDirs;
    }

    private List<Server> findServers(List<Path> sessionDirs) {
        ArrayList<Server> servers = new ArrayList<>();
        for (Path sessionDir : sessionDirs) {
            try {
                Files.list(sessionDir).filter(Files::isDirectory).forEach(serverDir -> {
                    if (serverDir.getFileName().toString().startsWith(serverDirPrefix)) {
                        try {
                            servers.add(new Server(serverDir));
                        } catch (Exception e) {
                            showVerboseMessage(verboseServer, "Found corrupt ServerDir " + serverDir);
                            deleteAllFiles(serverDir);
                        }
                    }
                });
            } catch (IOException e) {
                showError("Accessing SessionDir " + sessionDir + " failed", e);
            }
        }
        return servers;
    }

    void listServers(boolean machineWide, boolean details) {
        List<Server> servers = findServers(getSessionDirs(machineWide));
        for (Server server : servers) {
            String sessionInfo = machineWide ? "Session " + server.serverDir.getParent().getFileName() + " " : "";
            showMessage(sessionInfo + server + server.getLivenessInfo(server.isAlive()) + server.getLastBuildInfo());
            if (details) {
                showMessage("Details:");
                showMessage(server.getServerInfo());
            }
        }
    }

    void wipeMachineDir() {
        deleteAllFiles(getMachineDir());
    }

    @SuppressWarnings("try")
    void cleanupServers(boolean serverShutdown, boolean machineWide, boolean quiet) {
        List<Path> sessionDirs = getSessionDirs(machineWide);
        for (Path sessionDir : sessionDirs) {
            withLockDirFileChannel(sessionDir, lockFileChannel -> {
                try (FileLock lock = lockFileChannel.lock()) {
                    List<Server> servers = findServers(Collections.singletonList(sessionDir));
                    for (Server server : servers) {
                        boolean alive = server.isAlive();
                        if (!alive || serverShutdown) {
                            if (!quiet) {
                                showMessage("Cleanup " + server + server.getLivenessInfo(alive));
                            }
                            server.shutdown();
                        }
                    }
                } catch (IOException e) {
                    showError("Locking SessionDir " + sessionDir + " failed", e);
                }
            });
        }

        /* Cleanup empty SessionDirs - Potentially unsafe code */
        for (Path sessionDir : sessionDirs) {
            try {
                if (Files.list(sessionDir).allMatch(p -> p.getFileName().toString().equals(defaultLockFileName))) {
                    deleteAllFiles(sessionDir);
                }
            } catch (IOException e) {
                showError("Accessing SessionDir " + sessionDir + " failed");
            }
        }
    }

    private Server startServer(Path serverDir, int serverPort, LinkedHashSet<Path> classpath, LinkedHashSet<Path> bootClasspath, LinkedHashSet<String> javaArgs) {
        ProcessBuilder pb = new ProcessBuilder();
        pb.directory(serverDir.toFile());
        List<String> command = pb.command();
        command.add(getJavaHome().resolve("bin/java").toString());
        if (!bootClasspath.isEmpty()) {
            command.add(bootClasspath.stream().map(Path::toString).collect(Collectors.joining(":", "-Xbootclasspath/a:", "")));
        }
        command.addAll(Arrays.asList("-cp", classpath.stream().map(Path::toString).collect(Collectors.joining(":"))));
        command.addAll(javaArgs);
        command.add("com.oracle.svm.hosted.server.NativeImageBuildServer");
        command.add("-port=" + serverPort);
        command.add("-logFile=" + serverDir.resolve("server.log"));
        showVerboseMessage(isVerbose(), "StartServer [");
        showVerboseMessage(isVerbose(), command.stream().collect(Collectors.joining(" \\\n")));
        showVerboseMessage(isVerbose(), "]");
        int childPid = daemonize(() -> {
            try {
                ensureDirectoryExists(serverDir);
                Process process = pb.start();
                int serverPID = PosixUtils.getpid(process);
                writeServerFile(serverDir, serverPort, serverPID, classpath, bootClasspath, javaArgs);
            } catch (Exception e) {
                e.printStackTrace();
                deleteAllFiles(serverDir);
            }
        });
        if (childPid >= 0) {
            Server server = null;
            long timeout = System.currentTimeMillis() + 8_000;
            while (timeout > System.currentTimeMillis()) {
                try {
                    /* Wait for server.properties to appear in serverDir */
                    if (server == null) {
                        server = new Server(serverDir);
                    }
                    /* Once we see the server check if it is alive (accepts commands) */
                    if (server.isAlive()) {
                        return server;
                    }
                } catch (Exception e) {
                    /* It might take a few moments before server becomes visible */
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    String serverStr = server == null ? "server" : server.toString();
                    showError("Woke up from waiting for " + serverStr + " to become alive", e);
                }
            }
        }
        return null;
    }

    /*
     * Ensures started server keeps running even after native-image completes.
     */
    private static int daemonize(Runnable runnable) {
        int pid = Unistd.fork();
        switch (pid) {
            case 0:
                break;
            default:
                return pid;
        }
        runnable.run();
        System.exit(0);
        return -1;
    }

    private static void writeServerFile(Path serverDir, int port, int pid, LinkedHashSet<Path> classpath, LinkedHashSet<Path> bootClasspath, LinkedHashSet<String> javaArgs) throws Exception {
        Properties sp = new Properties();
        sp.setProperty(Server.pKeyPort, String.valueOf(port));
        sp.setProperty(Server.pKeyPID, String.valueOf(pid));
        sp.setProperty(Server.pKeyJavaArgs, javaArgs.stream().collect(Collectors.joining(" ")));
        sp.setProperty(Server.pKeyBCP, bootClasspath.stream().map(String::valueOf).collect(Collectors.joining(" ")));
        sp.setProperty(Server.pKeyCP, classpath.stream().map(String::valueOf).collect(Collectors.joining(" ")));
        Path serverPropertiesPath = serverDir.resolve(Server.serverProperties);
        try (OutputStream os = Files.newOutputStream(serverPropertiesPath)) {
            sp.store(os, "");
        }
    }

    private void withLockDirFileChannel(Path lockDir, Consumer<FileChannel> consumer) {
        withFileChannel(lockDir.resolve(defaultLockFileName), consumer);
    }

    private void withFileChannel(Path filePath, Consumer<FileChannel> consumer) {
        try {
            FileChannel fileChannel;
            synchronized (openFileChannels) {
                fileChannel = FileChannel.open(filePath, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
                openFileChannels.add(fileChannel);
            }

            consumer.accept(fileChannel);

            synchronized (openFileChannels) {
                openFileChannels.remove(fileChannel);
                fileChannel.close();
            }
        } catch (IOException e) {
            showError("Using FileChannel for " + filePath + " failed", e);
        }
    }

    private FileLock lockFileChannel(FileChannel channel) throws IOException {
        Thread lockWatcher = new Thread(() -> {
            try {
                Thread.sleep(TimeUnit.MINUTES.toMillis(10));
                try {
                    showWarning("Timeout while waiting for FileChannel.lock");
                    /* Trigger AsynchronousCloseException in channel.lock() */
                    channel.close();
                } catch (IOException e) {
                    showError("LockWatcher closing FileChannel of LockFile failed", e);
                }
            } catch (InterruptedException e) {
                /* Sleep interrupted -> Lock acquired") */
            }
        });
        lockWatcher.start();
        FileLock lock = channel.lock();
        lockWatcher.interrupt();
        return lock;
    }

    @SuppressWarnings("try")
    private int acquirePortNumber() {
        int firstPortNumber = 26681;
        int[] selectedPort = {-1};
        Path machineDir = getMachineDir();
        withLockDirFileChannel(machineDir, lockFileChannel -> {
            try (FileLock lock = lockFileChannel(lockFileChannel)) {
                Path machinePropertiesPath = machineDir.resolve(machineProperties);
                TreeSet<Integer> inUseSet = new TreeSet<>();
                Properties mp = new Properties();
                int maxPorts = 2;
                if (Files.isReadable(machinePropertiesPath)) {
                    try (InputStream is = Files.newInputStream(machinePropertiesPath)) {
                        mp.load(is);
                        String portInUseValue = mp.getProperty(pKeyPortsInUse);
                        if (portInUseValue != null && !portInUseValue.isEmpty()) {
                            inUseSet.addAll(Arrays.stream(portInUseValue.split(" ")).map(Integer::parseInt).collect(Collectors.toList()));
                        }
                        String maxPortsStr = mp.getProperty(pKeyMaxPorts);
                        if (maxPortsStr != null && !maxPortsStr.isEmpty()) {
                            maxPorts = Math.max(1, Integer.parseInt(maxPortsStr));
                        }
                    }
                }
                for (int candidatePort = firstPortNumber; candidatePort < firstPortNumber + maxPorts; candidatePort++) {
                    if (!inUseSet.contains(candidatePort)) {
                        selectedPort[0] = candidatePort;
                        inUseSet.add(selectedPort[0]);
                        break;
                    }
                }
                mp.setProperty(pKeyPortsInUse, inUseSet.stream().map(String::valueOf).collect(Collectors.joining(" ")));
                try (OutputStream os = Files.newOutputStream(machinePropertiesPath)) {
                    mp.store(os, "");
                }
            } catch (IOException e) {
                showError("Acquiring new server port number locking failed", e);
            }
        });
        if (selectedPort[0] < firstPortNumber) {
            showVerboseMessage(verboseServer, "Acquired new server port number failed");
        }
        return selectedPort[0];
    }

    @SuppressWarnings("try")
    private void releasePortNumber(int port) {
        Path machineDir = getMachineDir();
        withLockDirFileChannel(machineDir, lockFileChannel -> {
            try (FileLock lock = lockFileChannel(lockFileChannel)) {
                Path machinePropertiesPath = machineDir.resolve(machineProperties);
                TreeSet<Integer> inUseSet = new TreeSet<>();
                Properties mp = new Properties();
                if (Files.isReadable(machinePropertiesPath)) {
                    try (InputStream is = Files.newInputStream(machinePropertiesPath)) {
                        mp.load(is);
                        String portInUseValue = mp.getProperty(pKeyPortsInUse);
                        if (portInUseValue != null) {
                            inUseSet.addAll(Arrays.stream(portInUseValue.split(" ")).map(Integer::parseInt).collect(Collectors.toList()));
                        }
                    }
                }
                inUseSet.remove(port);
                mp.setProperty(pKeyPortsInUse, inUseSet.stream().map(String::valueOf).collect(Collectors.joining(" ")));
                try (OutputStream os = Files.newOutputStream(machinePropertiesPath)) {
                    mp.store(os, "");
                }
            } catch (Exception e) {
                showError("Locking for releasing server port number " + port + " failed", e);
            }
        });
    }

    private final class SignalHandler implements sun.misc.SignalHandler {
        @Override
        public void handle(sun.misc.Signal signal) {
            killServer();
            closeFileChannels();
            System.exit(1);
        }

        private void closeFileChannels() {
            showVerboseMessage(isVerbose(), "CleanupHandler Begin");
            synchronized (openFileChannels) {
                for (FileChannel fileChannel : openFileChannels) {
                    try {
                        showVerboseMessage(isVerbose(), "Closing open FileChannel: " + fileChannel);
                        fileChannel.close();
                    } catch (Exception e) {
                        showError("Closing FileChannel failed", e);
                    }
                }
            }
            showVerboseMessage(isVerbose(), "CleanupHandler End");
        }

        private void killServer() {
            showVerboseMessage(isVerbose(), "Caught interrupt. Kill Server.");
            Server current = building;
            if (current != null) {
                current.shutdown();
            }
        }
    }

    @Override
    protected void buildImage(LinkedHashSet<String> javaArgs, LinkedHashSet<Path> bcp, LinkedHashSet<Path> cp, LinkedHashSet<String> imageArgs, LinkedHashSet<Path> imagecp) {
        if (useServer && !javaArgs.contains("-Xdebug")) {
            SignalHandler signalHandler = new SignalHandler();
            sun.misc.Signal.handle(new sun.misc.Signal("TERM"), signalHandler);
            sun.misc.Signal.handle(new sun.misc.Signal("INT"), signalHandler);

            Server server = getServerInstance(cp, bcp, javaArgs);
            if (server != null) {
                showVerboseMessage(verboseServer, "\n" + server.getServerInfo() + "\n");
                /* Send image build job to server */
                showMessage("Build on " + server);
                building = server;
                server.sendBuildRequest(imagecp, imageArgs);
                if (!server.isAlive()) {
                    /* If server does not respond after image-build -> cleanup */
                    cleanupServers(false, false, true);
                }
                return;
            }
        }
        super.buildImage(javaArgs, bcp, cp, imageArgs, imagecp);
    }

    private static String imageServerUID(LinkedHashSet<Path> classpath, LinkedHashSet<Path> bootClasspath, LinkedHashSet<String> vmArgs) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            throw showError("SHA-512 digest is not available", e);
        }
        for (Path path : classpath) {
            digest.update(path.toString().getBytes());
        }
        for (Path path : bootClasspath) {
            digest.update(path.toString().getBytes());
        }
        for (String string : vmArgs) {
            digest.update(string.getBytes());
        }

        classpath.forEach(pathElement -> updateHash(digest, pathElement));
        bootClasspath.forEach(pathElement -> updateHash(digest, pathElement));
        vmArgs.stream().map(String::getBytes).forEach(digest::update);

        byte[] digestBytes = digest.digest();
        StringBuilder sb = new StringBuilder(digestBytes.length * 2);
        for (byte b : digestBytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static void updateHash(MessageDigest md, Path pathElement) {
        try {
            if (!(Files.isReadable(pathElement) && pathElement.getFileName().toString().endsWith(".jar"))) {
                throw showError("Build server classpath must only contain valid jar-files: " + pathElement);
            }
            md.update(Files.readAllBytes(pathElement));
        } catch (IOException e) {
            throw showError("Problem reading classpath entries: " + e.getMessage());
        }
    }

    void setUseServer(boolean val) {
        useServer = val;
    }

    void setVerboseServer(boolean val) {
        verboseServer = val;
    }

    void setSessionName(String val) {
        if (val != null && val.isEmpty()) {
            throw showError("Empty string not allowed as session-name");
        }
        sessionName = val;
    }
}
