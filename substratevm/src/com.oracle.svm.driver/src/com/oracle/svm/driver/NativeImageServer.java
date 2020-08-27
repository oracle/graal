/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.driver;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.ClasspathUtils;
import com.oracle.svm.hosted.server.NativeImageBuildClient;
import com.oracle.svm.hosted.server.NativeImageBuildServer;
import com.oracle.svm.hosted.server.SubstrateServerMessage.ServerCommand;

final class NativeImageServer extends NativeImage {

    private static final String serverDirPrefix = "server-id-";
    private static final String machineDirPrefix = "machine-id-";
    private static final String sessionDirPrefix = "session-id-";
    private static final String defaultLockFileName = ".lock";

    private static final String pKeyMaxServers = "MaxServers";
    private static final String machineProperties = "machine.properties";

    private boolean useServer = false;
    private boolean verboseServer = false;
    private String sessionName = null;

    private volatile Server building = null;
    private final List<FileChannel> openFileChannels = new ArrayList<>();

    private final ServerOptionHandler serverOptionHandler;

    private NativeImageServer(BuildConfiguration buildConfiguration) {
        super(buildConfiguration);
        serverOptionHandler = new ServerOptionHandler(this);
        registerOptionHandler(serverOptionHandler);
    }

    static NativeImage create(BuildConfiguration config) {
        if (NativeImageServerHelper.isInConfiguration()) {
            return new NativeImageServer(config);
        }
        return new NativeImage(config);
    }

    @SuppressWarnings("serial")
    private static final class ServerInstanceError extends RuntimeException {
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
            this.pid = Integer.parseInt(properties.get(pKeyPID));
            this.port = Integer.parseInt(properties.get(pKeyPort));
            if (this.port == 0) {
                ProcessProperties.destroyForcibly(this.pid);
                deleteAllFiles(this.serverDir);
                throw new ServerInstanceError();
            }
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
                result.add(ClasspathUtils.stringToClasspath(pathStr));
            }
            return result;
        }

        private int sendRequest(Consumer<byte[]> out, Consumer<byte[]> err, ServerCommand serverCommand, String... args) {
            List<String> argList = Arrays.asList(args);
            showVerboseMessage(verboseServer, "Sending to server [");
            showVerboseMessage(verboseServer, serverCommand.toString());
            if (argList.size() > 0) {
                showVerboseMessage(verboseServer, String.join(" \\\n", argList));
            }
            showVerboseMessage(verboseServer, "]");
            int exitCode = NativeImageBuildClient.sendRequest(serverCommand, String.join("\n", argList).getBytes(), port, out, err);
            showVerboseMessage(verboseServer, "Server returns: " + exitCode);
            return exitCode;
        }

        int sendBuildRequest(LinkedHashSet<Path> imageCP, LinkedHashSet<String> imageArgs) {
            int[] requestStatus = {1};
            withLockDirFileChannel(serverDir, lockFileChannel -> {
                boolean abortedOnce = false;
                boolean finished = false;
                while (!finished) {
                    try (FileLock lock = lockFileChannel.tryLock()) {
                        if (lock != null) {
                            finished = true;
                            if (abortedOnce) {
                                showMessage("DONE.");
                            }
                        } else {
                            /* Cancel strategy */
                            if (!abortedOnce) {
                                showMessagePart("A previous build is in progress. Aborting previous build...");
                                abortTask();
                                abortedOnce = true;
                            }
                            try {
                                Thread.sleep(3_000);
                            } catch (InterruptedException e) {
                                throw showError("Woke up from waiting for previous build to abort", e);
                            }
                            continue;
                        }

                        /* Now we have the server-lock and can send the build-request */
                        List<String> command = new ArrayList<>();
                        command.add(NativeImageBuildServer.TASK_PREFIX + "com.oracle.svm.hosted.NativeImageGeneratorRunner");

                        LinkedHashSet<Path> imagecp = new LinkedHashSet<>(serverClasspath);
                        imagecp.addAll(imageCP);
                        command.addAll(createImageBuilderArgs(imageArgs, imagecp));

                        showVerboseMessage(isVerbose(), "SendBuildRequest [");
                        showVerboseMessage(isVerbose(), String.join("\n", command));
                        showVerboseMessage(isVerbose(), "]");
                        try {
                            /* logfile main purpose is to know when was the last build request */
                            String logFileEntry = command.stream().collect(Collectors.joining(" ", Instant.now().toString() + ": ", "\n"));
                            Files.write(serverDir.resolve(buildRequestLog), logFileEntry.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                            updateLastBuildRequest();
                        } catch (IOException e) {
                            throw showError("Could not read/write into build-request log file", e);
                        }
                        // Checkstyle: stop
                        requestStatus[0] = sendRequest(
                                        byteStreamToByteConsumer(System.out),
                                        byteStreamToByteConsumer(System.err),
                                        ServerCommand.BUILD_IMAGE,
                                        command.toArray(new String[command.size()]));
                        // Checkstyle: resume
                    } catch (IOException e) {
                        throw showError("Error while trying to lock ServerDir " + serverDir, e);
                    }
                }
            });
            return requestStatus[0];
        }

        boolean isAlive() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            boolean alive = sendRequest(byteStreamToByteConsumer(baos), byteStreamToByteConsumer(baos), ServerCommand.GET_VERSION) == NativeImageBuildClient.EXIT_SUCCESS;
            showVerboseMessage(verboseServer, "Server version response: " + new String(baos.toByteArray()));
            return alive;
        }

        void abortTask() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            sendRequest(byteStreamToByteConsumer(baos), byteStreamToByteConsumer(baos), ServerCommand.ABORT_BUILD);
            showVerboseMessage(verboseServer, "Server abort response:" + new String(baos.toByteArray()));
        }

        synchronized void shutdown() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            sendRequest(byteStreamToByteConsumer(baos), byteStreamToByteConsumer(baos), ServerCommand.STOP_SERVER);
            showVerboseMessage(verboseServer, "Server stop response:" + new String(baos.toByteArray()));
            long terminationTimeout = System.currentTimeMillis() + 20_000;
            long killTimeout = terminationTimeout + 40_000;
            long killedTimeout = killTimeout + 2_000;
            showVerboseMessage(verboseServer, "Waiting for " + this + " to shutdown");
            /* Release port only after server stops responding to it */
            boolean sentSIGTERM = false;
            boolean sentSIGKILL = false;
            do {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    throw showError("Woke up from waiting for " + this + " to shutdown", e);
                }
                long now = System.currentTimeMillis();
                if (!sentSIGTERM && terminationTimeout < now) {
                    showWarning(this + " keeps responding to port " + port + " even after sending STOP_SERVER");
                    ProcessProperties.destroy(pid);
                    sentSIGTERM = true;
                } else if (!sentSIGKILL && killTimeout < now) {
                    showWarning(this + " keeps responding to port " + port + " even after destroying");
                    ProcessProperties.destroyForcibly(pid);
                    sentSIGKILL = true;
                } else if (killedTimeout < now) {
                    throw showError(this + " keeps responding to port " + port + " even after destroying forcefully");
                }
            } while (isAlive());
            deleteAllFiles(serverDir);
        }

        String getServerInfo() {
            StringBuilder sb = new StringBuilder();
            sb.append(getClass().getName());
            sb.append("\nServerDir: ").append(serverDir);
            sb.append("\nRunning for: ").append(getDurationString(since));
            sb.append("\nLast build: ");
            if (since.equals(lastBuildRequest)) {
                sb.append("None");
            } else {
                sb.append(getDurationString(lastBuildRequest));
            }
            sb.append("\nPID: ").append(pid);
            sb.append("\nPort: ").append(port);
            sb.append("\nJavaArgs: ").append(String.join(" ", serverJavaArgs));
            sb.append("\nBootClasspath: ").append(serverBootClasspath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
            sb.append("\nClasspath: ").append(serverClasspath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
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
                return " running for: " + getDurationString(since);
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

    private static Consumer<byte[]> byteStreamToByteConsumer(OutputStream stream) {
        return b -> {
            try {
                stream.write(b);
            } catch (IOException e) {
                throw new RuntimeException("Byte stream write failed.");
            }
        };
    }

    private String getSessionID() {
        if (sessionName != null) {
            return sessionDirPrefix + sessionName;
        }
        return sessionDirPrefix + Long.toHexString(Unistd.getsid(Math.toIntExact(ProcessProperties.getProcessID())));
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
        Path machineDir = getUserConfigDir().resolve(getMachineID());
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
    private Server getServerInstance(LinkedHashSet<Path> classpath, LinkedHashSet<Path> bootClasspath, List<String> javaArgs) {
        Server[] result = {null};
        /* Important - Creating new servers is a machine-exclusive operation */
        withFileChannel(getMachineDir().resolve("create-server.lock"), lockFileChannel -> {
            try (FileLock lock = lockFileChannel(lockFileChannel)) {
                /* Ensure that all dead server entries are gone before we start */
                List<Server> aliveServers = cleanupServers(false, true, true);

                /* Determine how many ports are allowed to get used for build-servers */
                String maxServersStr = loadProperties(getMachineDir().resolve(machineProperties)).get(pKeyMaxServers);
                if (maxServersStr == null || maxServersStr.isEmpty()) {
                    maxServersStr = getUserConfigProperties().get(pKeyMaxServers);
                }
                int maxServers;
                if (maxServersStr == null || maxServersStr.isEmpty()) {
                    maxServers = 2;
                } else {
                    maxServers = Math.max(1, Integer.parseInt(maxServersStr));
                }

                /* Maximize reuse by using same VM mem-args for all server-based image builds */
                String xmxValueStr = getXmxValue(maxServers);
                replaceArg(javaArgs, oXmx, xmxValueStr);
                String xmsValueStr = getXmsValue();
                long xmxValue = SubstrateOptionsParser.parseLong(xmxValueStr);
                long xmsValue = SubstrateOptionsParser.parseLong(xmsValueStr);
                if (WordFactory.unsigned(xmsValue).aboveThan(WordFactory.unsigned(xmxValue))) {
                    xmsValueStr = Long.toUnsignedString(xmxValue);
                }
                replaceArg(javaArgs, oXms, xmsValueStr);

                Path sessionDir = getSessionDir();
                List<Collection<Path>> builderPaths = new ArrayList<>(Arrays.asList(classpath, bootClasspath));
                if (config.useJavaModules()) {
                    builderPaths.addAll(Arrays.asList(config.getBuilderModulePath(), config.getBuilderUpgradeModulePath()));
                }
                Path javaExePath = canonicalize(config.getJavaExecutable());
                String serverUID = imageServerUID(javaExePath, javaArgs, builderPaths);
                Path serverDir = sessionDir.resolve(serverDirPrefix + serverUID);
                Optional<Server> reusableServer = aliveServers.stream().filter(s -> s.serverDir.equals(serverDir)).findFirst();
                if (reusableServer.isPresent()) {
                    Server server = reusableServer.get();
                    if (!server.isAlive()) {
                        throw showError("Found defunct image-build server:" + server.getServerInfo());
                    }
                    showVerboseMessage(verboseServer, "Reuse existing image-build server: " + server);
                    result[0] = server;
                } else {
                    if (aliveServers.size() >= maxServers) {
                        /* Server limit reached */
                        showVerboseMessage(verboseServer, "Image-build server limit reached -> remove least recently used");
                        /* Shutdown least recently used within session */
                        Server victim = findVictim(aliveServers);
                        /* If none found also consider servers from other sessions on machine */
                        if (victim != null) {
                            showMessage("Shutdown " + victim);
                            victim.shutdown();
                        } else {
                            showWarning("Image-build server limit exceeded. Use options --server{-list,-shutdown[-all]} to fix the problem.");
                        }
                    }
                    /* Instantiate new server and write properties file */
                    Server server = startServer(javaExePath, serverDir, 0, classpath, bootClasspath, javaArgs);
                    if (server == null) {
                        showWarning("Creating image-build server failed. Fallback to one-shot image building ...");
                    }
                    result[0] = server;
                }
            } catch (IOException e) {
                throw showError("ServerInstance-creation locking failed", e);
            }
        });
        return result[0];
    }

    private static Server findVictim(List<Server> aliveServers) {
        return aliveServers.stream()
                        .filter(Server::isAlive)
                        .min(Comparator.comparing(s -> s.lastBuildRequest))
                        .orElse(null);
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
                throw showError("Accessing MachineDir " + getMachineDir() + " failed", e);
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
                throw showError("Accessing SessionDir " + sessionDir + " failed", e);
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
    List<Server> cleanupServers(boolean serverShutdown, boolean machineWide, boolean quiet) {
        List<Path> sessionDirs = getSessionDirs(machineWide);

        /* Cleanup nfs-mounted previously deleted sessionDirs */
        for (Path sessionDir : sessionDirs) {
            if (isDeletedPath(sessionDir)) {
                deleteAllFiles(sessionDir);
            }
        }

        List<Server> aliveServers = new ArrayList<>();
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
                        } else {
                            aliveServers.add(server);
                        }
                    }
                } catch (IOException e) {
                    throw showError("Locking SessionDir " + sessionDir + " failed", e);
                }
            });
        }

        return aliveServers;
    }

    private Server startServer(Path javaExePath, Path serverDir, int serverPort, LinkedHashSet<Path> classpath, LinkedHashSet<Path> bootClasspath, List<String> javaArgs) {
        ProcessBuilder pb = new ProcessBuilder();
        pb.directory(serverDir.toFile());
        pb.redirectErrorStream(true);
        List<String> command = pb.command();
        command.add(javaExePath.toString());
        if (!bootClasspath.isEmpty()) {
            command.add(bootClasspath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator, "-Xbootclasspath/a:", "")));
        }
        command.addAll(Arrays.asList("-cp", classpath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator))));
        command.addAll(javaArgs);
        // Ensure Graal logs to System.err so users can see log output during server-based building.
        command.add("-Dgraal.LogFile=%e");
        command.add("com.oracle.svm.hosted.server.NativeImageBuildServer");
        command.add(NativeImageBuildServer.PORT_PREFIX + serverPort);
        Path logFilePath = serverDir.resolve("server.log");
        command.add(NativeImageBuildServer.LOG_PREFIX + logFilePath);
        showVerboseMessage(isVerbose(), "StartServer [");
        showVerboseMessage(isVerbose(), SubstrateUtil.getShellCommandString(command, true));
        showVerboseMessage(isVerbose(), "]");
        int childPid = NativeImageServerHelper.daemonize(() -> {
            try {
                ensureDirectoryExists(serverDir);
                showVerboseMessage(verboseServer, "Starting new server ...");
                Process process = pb.start();
                long serverPID = ProcessProperties.getProcessID(process);
                showVerboseMessage(verboseServer, "New image-build server pid: " + serverPID);
                int selectedPort = serverPort;
                if (selectedPort == 0) {
                    try (BufferedReader serverStdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        int readLineTries = 60;
                        ArrayList<String> lines = new ArrayList<>(readLineTries);
                        while ((line = serverStdout.readLine()) != null && --readLineTries > 0) {
                            lines.add(line);
                            if (line.startsWith(NativeImageBuildServer.PORT_LOG_MESSAGE_PREFIX)) {
                                String portStr = line.substring(NativeImageBuildServer.PORT_LOG_MESSAGE_PREFIX.length());
                                try {
                                    selectedPort = Integer.parseInt(portStr);
                                    break;
                                } catch (NumberFormatException ex) {
                                    /* Fall through */
                                }
                            }
                        }
                        if (selectedPort == 0) {
                            String serverOutputMessage = "";
                            if (!lines.isEmpty()) {
                                serverOutputMessage = "\nServer stdout/stderr:\n" + String.join("\n", lines);
                            }
                            throw showError("Could not determine port for sending image-build requests." + serverOutputMessage);
                        }
                        showVerboseMessage(verboseServer, "Image-build server selected port " + selectedPort);
                    }
                }
                writeServerFile(serverDir, selectedPort, serverPID, classpath, bootClasspath, javaArgs);
            } catch (Throwable e) {
                deleteAllFiles(serverDir);
                throw showError("Starting image-build server instance failed", e);
            }
        });

        int exitStatus = ProcessProperties.waitForProcessExit(childPid);
        showVerboseMessage(verboseServer, "Exit status forked child process: " + exitStatus);
        if (exitStatus == 0) {
            Server server;
            try {
                server = new Server(serverDir);
            } catch (Exception e) {
                showVerboseMessage(verboseServer, "Image-build server unusable.");
                /* Build without server */
                return null;
            }

            for (int i = 0; i < 6; i += 1) {
                /* Check if it is alive (accepts commands) */
                if (server.isAlive()) {
                    showVerboseMessage(verboseServer, "Image-build server found.");
                    return server;
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    break;
                }
            }
            showVerboseMessage(verboseServer, "Image-build server not responding.");
            server.shutdown();
        }
        return null;
    }

    private static void writeServerFile(Path serverDir, int port, long pid, LinkedHashSet<Path> classpath, LinkedHashSet<Path> bootClasspath, List<String> javaArgs) throws Exception {
        Properties sp = new Properties();
        sp.setProperty(Server.pKeyPort, String.valueOf(port));
        sp.setProperty(Server.pKeyPID, String.valueOf(pid));
        sp.setProperty(Server.pKeyJavaArgs, String.join(" ", javaArgs));
        sp.setProperty(Server.pKeyBCP, bootClasspath.stream().map(ClasspathUtils::classpathToString).collect(Collectors.joining(" ")));
        sp.setProperty(Server.pKeyCP, classpath.stream().map(ClasspathUtils::classpathToString).collect(Collectors.joining(" ")));
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
            throw showError("Using FileChannel for " + filePath + " failed", e);
        }
    }

    private static FileLock lockFileChannel(FileChannel channel) throws IOException {
        Thread lockWatcher = new Thread(() -> {
            try {
                Thread.sleep(TimeUnit.MINUTES.toMillis(10));
                try {
                    showWarning("Timeout while waiting for FileChannel.lock");
                    /* Trigger AsynchronousCloseException in channel.lock() */
                    channel.close();
                } catch (IOException e) {
                    throw showError("LockWatcher closing FileChannel of LockFile failed", e);
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

    private final class AbortBuildSignalHandler implements sun.misc.SignalHandler {
        private int attemptCount = 0;

        @Override
        public void handle(sun.misc.Signal signal) {
            Server current = building;
            if (attemptCount >= 3 || current == null) {
                killServer();
                closeFileChannels();
                System.exit(1);
            } else {
                attemptCount += 1;
                current.abortTask();
            }
        }

        private void closeFileChannels() {
            showVerboseMessage(isVerbose(), "CleanupHandler Begin");
            synchronized (openFileChannels) {
                for (FileChannel fileChannel : openFileChannels) {
                    try {
                        showVerboseMessage(isVerbose(), "Closing open FileChannel: " + fileChannel);
                        fileChannel.close();
                    } catch (Exception e) {
                        throw showError("Closing FileChannel failed", e);
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
    protected int buildImage(List<String> javaArgs, LinkedHashSet<Path> bcp, LinkedHashSet<Path> cp, LinkedHashSet<String> imageArgs, LinkedHashSet<Path> imagecp) {
        boolean printFlags = imageArgs.stream().anyMatch(arg -> arg.contains(enablePrintFlags) || arg.contains(enablePrintFlagsWithExtraHelp));
        if (useServer && !printFlags && !useDebugAttach()) {
            AbortBuildSignalHandler signalHandler = new AbortBuildSignalHandler();
            sun.misc.Signal.handle(new sun.misc.Signal("TERM"), signalHandler);
            sun.misc.Signal.handle(new sun.misc.Signal("INT"), signalHandler);

            Server server = getServerInstance(cp, bcp, javaArgs);
            if (server != null) {
                showVerboseMessage(verboseServer, "\n" + server.getServerInfo() + "\n");
                /* Send image build job to server */
                showMessage("Build on " + server);
                building = server;
                int status = server.sendBuildRequest(imagecp, imageArgs);
                if (!server.isAlive()) {
                    /* If server does not respond after image-build -> cleanup */
                    cleanupServers(false, false, true);
                }
                return status;
            }
        }
        return super.buildImage(javaArgs, bcp, cp, imageArgs, imagecp);
    }

    private static String imageServerUID(Path javaExecutable, List<String> vmArgs, List<Collection<Path>> builderPaths) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            throw showError("SHA-512 digest is not available", e);
        }
        digest.update(javaExecutable.toString().getBytes());
        for (Collection<Path> paths : builderPaths) {
            for (Path path : paths) {
                digest.update(path.toString().getBytes());
                updateHash(digest, path);
            }
        }
        for (String string : vmArgs) {
            digest.update(string.getBytes());
        }

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

    boolean useServer() {
        return useServer;
    }

    @Override
    protected void setDryRun(boolean val) {
        super.setDryRun(val);
        if (val) {
            useServer = false;
        }
    }

    void setVerboseServer(boolean val) {
        verboseServer = val;
    }

    boolean verboseServer() {
        return verboseServer;
    }

    void setSessionName(String val) {
        if (val != null && val.isEmpty()) {
            throw showError("Empty string not allowed as session-name");
        }
        sessionName = val;
    }
}
