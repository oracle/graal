package com.oracle.svm.core.posix.jfr;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.posix.headers.Dirent;
import jdk.graal.compiler.word.Word;
//import jdk.jfr.internal.LogLevel;
//import jdk.jfr.internal.LogTag;
//import jdk.jfr.internal.Logger;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.handles.PrimitiveArrayView;
import com.oracle.svm.core.os.RawFileOperationSupport;
import com.oracle.svm.core.os.RawFileOperationSupport.FileAccessMode;
import com.oracle.svm.core.os.RawFileOperationSupport.FileCreationMode;
import com.oracle.svm.core.os.RawFileOperationSupport.RawFileDescriptor;


import jdk.graal.compiler.api.replacements.Fold;

import java.nio.charset.StandardCharsets;

import static com.oracle.svm.core.posix.headers.Fcntl.O_NOFOLLOW;
import static com.oracle.svm.core.posix.headers.Fcntl.O_RDONLY;
public class PosixJfrEmergencyDumpSupport implements com.oracle.svm.core.jfr.JfrEmergencyDumpSupport {
    private static final int CHUNK_FILE_HEADER_SIZE = 68;// TODO based on jdk file
    private static final int JVM_MAXPATHLEN = 4096;// TODO based on jdk file
    private static final byte FILE_SEPARATOR = "/".getBytes(StandardCharsets.UTF_8)[0];
    private static final byte[] DUMP_FILE_PREFIX = "hs_oom_pid_".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CHUNKFILE_EXTENSION_BYTES = ".jfr".getBytes(StandardCharsets.UTF_8); // TODO double check its utf8 you want.
    private Dirent.DIR directory;
    private byte[] pidBytes;
    private byte[] dumpPathBytes;
    private byte[] repositoryLocationBytes;
    private RawFileDescriptor emergencyFd;
    private PrimitiveArrayView pathBuffer;

    @Platforms(Platform.HOSTED_ONLY.class)
    public PosixJfrEmergencyDumpSupport() {
    }

    public void initialize() {
        pidBytes = String.valueOf(ProcessHandle.current().pid()).getBytes(StandardCharsets.UTF_8);
        pathBuffer =  PrimitiveArrayView.createForReadingAndWriting(new byte[JVM_MAXPATHLEN]);
        directory = org.graalvm.word.WordFactory.nullPointer(); // *** maybe not necessary.
        //TODO need to terminate the string with 0. otherwise length function will not work.
    }

    public void setRepositoryLocation(String dirText) {
        repositoryLocationBytes = dirText.getBytes(StandardCharsets.UTF_8);
    }

    private CCharPointer getRepositoryLocation() {
        clearPathBuffer();
        for (int i = 0; i < repositoryLocationBytes.length; i++) {
            getPathBuffer().write(i, repositoryLocationBytes[i]);
        }
        return getPathBuffer();
    }

    public void setDumpPath(String dumpPathText) {
        pathBuffer =  PrimitiveArrayView.createForReadingAndWriting(new byte[JVM_MAXPATHLEN]);
    }

    public String getDumpPath() {
        if (dumpPathBytes != null) {
            return new String(dumpPathBytes,StandardCharsets.UTF_8);
        }
        return "";
    }

    /** See JfrEmergencyDump::on_vm_error*/
    public void onVmError(){
        if (openEmergencyDumpFile()) {
            writeEmergencyDumpFile();
            getFileSupport().close(emergencyFd);
            emergencyFd = Word.nullPointer();
        }
    }

    /** See open_emergency_dump_file */
    private boolean openEmergencyDumpFile(){
        if (getFileSupport().isValid(emergencyFd)){
            return true;
        }
        emergencyFd = getFileSupport().create(createEmergencyDumpPath(), FileCreationMode.CREATE, FileAccessMode.READ_WRITE); //gives us O_CREAT | O_RDWR for creation mode and S_IREAD | S_IWRITE permissions
        if (!getFileSupport().isValid(emergencyFd)) {
            // Fallback. Try to create it in the current directory.
            dumpPathBytes = null;
            emergencyFd = getFileSupport().create(createEmergencyDumpPath(), FileCreationMode.CREATE, FileAccessMode.READ_WRITE);
        }
        System.out.println("openEmergencyDumpFile pathBuffer: "+ CTypeConversion.toJavaString(getPathBuffer()));
        return getFileSupport().isValid(emergencyFd);
    }

    /** See create_emergency_dump_path */
    private CCharPointer createEmergencyDumpPath() {
        int idx = 0;

        clearPathBuffer();

        if(dumpPathBytes != null) {
            for (int i = 0; i < dumpPathBytes.length; i++) {
                getPathBuffer().write(idx++, dumpPathBytes[i]);
            }
            // Add delimiter
            getPathBuffer().write(idx++, FILE_SEPARATOR);
        }

        for (int i = 0; i < DUMP_FILE_PREFIX.length; i++) {
            getPathBuffer().write(idx++, DUMP_FILE_PREFIX[i]);
        }

        for (int i = 0; i < pidBytes.length; i++) {
            getPathBuffer().write(idx++, pidBytes[i]);
        }

        for (int i = 0; i < CHUNKFILE_EXTENSION_BYTES.length; i++) {
            getPathBuffer().write(idx++, CHUNKFILE_EXTENSION_BYTES[i]);
        }

        return getPathBuffer();
    }

    private void writeEmergencyDumpFile() {
        if (openDirectorySecure()) {
            if (directory.isNull()) {
                return;
            }
            int blockSize = 1024 * 1024;
            org.graalvm.word.Pointer copyBlock = com.oracle.svm.core.memory.NullableNativeMemory.malloc(blockSize, NmtCategory.JFR);
            if (copyBlock.isNull()) {
                    // TODO trouble importing these internal methods
//                Logger.log(LogTag.JFR_SYSTEM, LogLevel.ERROR, "Emergency dump failed. Could not allocate copy block.");
                return;
            }

            Dirent.dirent entry;
            while ((entry = Dirent.readdir(directory)).isNonNull()) {
                RawFileDescriptor chunkFd = filter(entry.d_name());
                if (getFileSupport().isValid(chunkFd)){
                    String name = CTypeConversion.toJavaString(entry.d_name());
                    System.out.println("Filter checks passed. Chunk file name: "+ name);

                    // Read it's size
                    long chunkFileSize = getFileSupport().size(chunkFd);
                    long bytesRead = 0;
                    long bytesWritten = 0;
                    while (bytesRead < chunkFileSize){
                        // Start at beginning
                        getFileSupport().seek(chunkFd, 0); // seems unneeded. idk why???
                        // Read from chunk file to copy block
                        long readResult = getFileSupport().read(chunkFd, copyBlock, org.graalvm.word.WordFactory.unsigned(blockSize));// *** i think this already retries until fully read[no just retries until gets a sucessful read]
                        if (readResult < 0){ // -1 if read failed
                            System.out.println("Log ERROR. Read failed.");
                            break;
                        }
                        bytesRead += readResult;
                        assert bytesRead - bytesWritten <= blockSize;
                        // Write from copy block to dump file
                        if (!getFileSupport().write(emergencyFd, copyBlock, org.graalvm.word.WordFactory.unsigned(bytesRead - bytesWritten))) { // *** may iterate until fully writtem
                            System.out.println("Log ERROR. Write failed.");
                            break;
                        }
                        bytesWritten = bytesRead;
                        System.out.println("bytesWritten " + bytesWritten);
                    }
                    getFileSupport().close(chunkFd);
                }
            }
            com.oracle.svm.core.memory.NullableNativeMemory.free(copyBlock);
        }
    }

    // *** copied from PosixPerfMemoryProvider
    private boolean openDirectorySecure() {
        int fd = restartableOpen(getRepositoryLocation(), O_RDONLY() | O_NOFOLLOW(), 0);
        if (fd == -1) {
            return false;
        }

//        if (!isDirFdSecure(fd)) { //TODO do we need this?
//            com.oracle.svm.core.posix.headers.Unistd.NoTransitions.close(fd);
//            return null;
//        }

        this.directory = Dirent.fdopendir(fd);
        if (directory.isNull()) {
            com.oracle.svm.core.posix.headers.Unistd.NoTransitions.close(fd);
            return false;
        }
        return true;
    }

    // *** copied from PosixPerfMemoryProvider
    @Uninterruptible(reason = "LibC.errno() must not be overwritten accidentally.")
    private static int restartableOpen(CCharPointer directory, int flags, int mode) {
        int result;
        do {
            result = com.oracle.svm.core.posix.headers.Fcntl.NoTransitions.open(directory, flags, mode);
        } while (result == -1 && LibC.errno() == com.oracle.svm.core.posix.headers.Errno.EINTR());

        return result;
    }

    /** See RepositoryIterator::filter */
    private RawFileDescriptor filter(CCharPointer fn){

        // check filename extension
        int filenameLength = (int) SubstrateUtil.strlen(fn).rawValue();
        if (filenameLength <= CHUNKFILE_EXTENSION_BYTES.length){
            return org.graalvm.word.WordFactory.nullPointer();
        }

        // Verify file extension
        for (int i = 0; i < CHUNKFILE_EXTENSION_BYTES.length; i++) {
            int idx1 = CHUNKFILE_EXTENSION_BYTES.length - i - 1;
            int idx2 = filenameLength - i - 1;
            //System.out.println("expected byte " + chunkfileExtensionBytes[idx1] + " actual byte " + ((org.graalvm.word.Pointer) fn).readByte(idx2));
            if (CHUNKFILE_EXTENSION_BYTES[idx1] != ((org.graalvm.word.Pointer) fn).readByte(idx2)) {
                System.out.println("failed extension check");
                return org.graalvm.word.WordFactory.nullPointer();
            }
        }

        String name = CTypeConversion.toJavaString(fullyQualified(fn));
        System.out.println("fully qualified: "+ name);

        // Verify if you can open it and receive a valid file descriptor
        RawFileDescriptor chunkFd = getFileSupport().open(fullyQualified(fn) ,FileAccessMode.READ_WRITE);
        if (!getFileSupport().isValid(chunkFd)) {
            System.out.println("failed open check");
            return org.graalvm.word.WordFactory.nullPointer();
        }

        // Verify file size
        long chunkFileSize = getFileSupport().size(chunkFd);
        if (chunkFileSize < CHUNK_FILE_HEADER_SIZE) {
            System.out.println("failed size check");
            return org.graalvm.word.WordFactory.nullPointer();
        }

        return chunkFd;
    }

    /** Given a chunk file name, it returns the fully qualified filename. See RepositoryIterator::fully_qualified */
    private CCharPointer fullyQualified(CCharPointer fn){
        long fnLength =  SubstrateUtil.strlen(fn).rawValue() ;
        int idx = 0;

        clearPathBuffer();

        // TODO HS uses _path_buffer_file_name_offset to avoid building this part of th path each time.
        //  Cached in RepositoryIterator::RepositoryIterator and used in fully_qualified
        for (int i = 0; i < repositoryLocationBytes.length; i++) {
            getPathBuffer().write(idx++, repositoryLocationBytes[i]);
        }

        // Add delimiter
        getPathBuffer().write(idx++, FILE_SEPARATOR);


        for (int i = 0; i < fnLength; i++) {
            getPathBuffer().write(idx++, fn.read(i));
        }
        return getPathBuffer();
    }

    private CCharPointer getPathBuffer(){
        return pathBuffer.addressOfArrayElement(0);
    }

    private void clearPathBuffer(){
        LibC.memset(getPathBuffer(), Word.signed(0), Word.unsigned(JVM_MAXPATHLEN));
    }

    @Fold
    static RawFileOperationSupport getFileSupport() {
        return RawFileOperationSupport.bigEndian();
    }

    public void teardown() {
        // *** this must survive as long as we need the dump path c pointer.
        Dirent.closedir(directory);
        pathBuffer.close();
    }
}

@com.oracle.svm.core.feature.AutomaticallyRegisteredFeature
class PosixJfrEmergencyDumpFeature extends com.oracle.svm.core.jfr.JfrEmergencyDumpFeature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        PosixJfrEmergencyDumpSupport support = new PosixJfrEmergencyDumpSupport();
        org.graalvm.nativeimage.ImageSingletons.add(com.oracle.svm.core.jfr.JfrEmergencyDumpSupport.class, support);
        org.graalvm.nativeimage.ImageSingletons.add(PosixJfrEmergencyDumpSupport.class, support);
    }
}
