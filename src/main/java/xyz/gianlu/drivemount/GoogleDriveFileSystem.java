package xyz.gianlu.drivemount;

import com.dokan.java.DokanyFileSystemStub;
import com.dokan.java.DokanyOperations;
import com.dokan.java.DokanyUtils;
import com.dokan.java.FileSystemInformation;
import com.dokan.java.constants.microsoft.Win32ErrorCodes;
import com.dokan.java.structure.ByHandleFileInformation;
import com.dokan.java.structure.DokanFileInfo;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.About;
import com.google.api.services.drive.model.File;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Gianlu
 */
public class GoogleDriveFileSystem extends DokanyFileSystemStub {
    private static final Logger LOGGER = Logger.getLogger(GoogleDriveFileSystem.class);

    static {
        LOGGER.setLevel(Level.ALL);
    }

    private final Drive drive;
    private final FilesTree root;
    private final AtomicLong contextCounter = new AtomicLong(0);

    public GoogleDriveFileSystem(@NotNull FileSystemInformation fileSystemInformation, @NotNull Drive drive) {
        super(fileSystemInformation, false);
        this.drive = drive;
        this.root = new FilesTree("", "/", null);

        try {
            populate(root);
        } catch (IOException ex) {
            throw new RuntimeException("Failed initializing drive!", ex);
        }
    }

    private void populate(@NotNull FilesTree tree) throws IOException {
        if (tree.populated) return;

        if (tree.isRoot()) {
            List<File> files = drive.files().list()
                    .setFields("files(parents)").setQ("trashed=false")
                    .setSpaces("drive").execute().getFiles();

            String rootId = null;
            for (File file : files) {
                List<String> parents = file.getParents();
                if (parents.size() > 1) throw new UnsupportedOperationException("File has more than one parent.");

                if (Utils.isRootDirectory(parents.get(0))) {
                    rootId = parents.get(0);
                    break;
                }
            }

            if (rootId == null) // Drive is empty
                return;

            tree.id = rootId;
        }

        List<File> files = drive.files().list()
                .setFields("*").setSpaces("drive")
                .setQ(String.format("trashed=false and '%s' in parents and (not mimeType contains 'application/vnd.google-apps.' or mimeType='application/vnd.google-apps.folder')", tree.id))
                .execute().getFiles();

        tree.populate(files);
    }

    @Override
    public int zwCreateFile(WString rawPath, WinBase.SECURITY_ATTRIBUTES securityContext, int rawDesiredAccess, int rawFileAttributes, int rawShareAccess, int rawCreateDisposition, int rawCreateOptions, DokanFileInfo dokanFileInfo) {
        dokanFileInfo.Context = contextCounter.incrementAndGet();
        LOGGER.trace(String.format("Create file {path: %s, context: %d}", rawPath, dokanFileInfo.Context));

        // TODO: Create file

        return Win32ErrorCodes.ERROR_SUCCESS;
    }

    @Override
    public int getFileInformation(WString rawPath, ByHandleFileInformation handleFileInfo, DokanFileInfo dokanFileInfo) {
        if (dokanFileInfo.Context == 0)
            return Win32ErrorCodes.ERROR_INVALID_HANDLE;

        LOGGER.trace(String.format("Get file info {path: %s, context: %d}", rawPath, dokanFileInfo.Context));

        if (rawPath.toString().equals("\\")) {
            return Win32ErrorCodes.ERROR_SUCCESS;
        } else {
            return Win32ErrorCodes.ERROR_FILE_NOT_FOUND; // TODO: Get file information
        }
    }

    @Override
    public int findFiles(WString rawPath, DokanyOperations.FillWin32FindData rawFillFindData, DokanFileInfo dokanFileInfo) {
        if (dokanFileInfo.Context == 0)
            return Win32ErrorCodes.ERROR_INVALID_HANDLE;

        LOGGER.trace(String.format("Find files {path: %s, context: %d}", rawPath, dokanFileInfo.Context));

        if (rawPath.toString().equals("\\")) {
            root.writeTo(rawFillFindData, volumeSerialnumber, dokanFileInfo);
            return Win32ErrorCodes.ERROR_SUCCESS;
        }

        String path = rawPath.toString();
        path = path.replace('\\', '/');

        FilesTree dir = root.findDirectory(path);
        if (dir == null) return Win32ErrorCodes.ERROR_FILE_NOT_FOUND;

        try {
            populate(dir);
        } catch (IOException ex) {
            LOGGER.error(String.format("Failed populating directory. {path: %s, id: %s}", dir.path, dir.id), ex);
            return Win32ErrorCodes.ERROR_IO_DEVICE;
        }

        dir.writeTo(rawFillFindData, volumeSerialnumber, dokanFileInfo);
        return Win32ErrorCodes.ERROR_SUCCESS;
    }

    @Override
    public void closeFile(WString rawPath, DokanFileInfo dokanFileInfo) {
        LOGGER.trace(String.format("Close file {path: %s, context: %d}", rawPath, dokanFileInfo.Context));
        dokanFileInfo.Context = 0;
    }

    @Override
    public int getVolumeInformation(Pointer rawVolumeNameBuffer, int rawVolumeNameSize, IntByReference rawVolumeSerialNumber, IntByReference rawMaximumComponentLength, IntByReference rawFileSystemFlags, Pointer rawFileSystemNameBuffer, int rawFileSystemNameSize, DokanFileInfo dokanFileInfo) {
        rawVolumeNameBuffer.setWideString(0L, DokanyUtils.trimStrToSize(this.volumeName, rawVolumeNameSize));
        rawVolumeSerialNumber.setValue(this.volumeSerialnumber);
        rawMaximumComponentLength.setValue(this.fileSystemInformation.getMaxComponentLength());
        rawFileSystemFlags.setValue(this.fileSystemInformation.getFileSystemFeatures().toInt());
        rawFileSystemNameBuffer.setWideString(0L, DokanyUtils.trimStrToSize(this.fileSystemInformation.getFileSystemName(), rawFileSystemNameSize));
        return Win32ErrorCodes.ERROR_SUCCESS;
    }

    @Override
    public int getDiskFreeSpace(LongByReference freeBytesAvailable, LongByReference totalNumberOfBytes, LongByReference totalNumberOfFreeBytes, DokanFileInfo dokanFileInfo) {
        About.StorageQuota quota;
        try {
            quota = drive.about().get().setFields("storageQuota").execute().getStorageQuota();
        } catch (IOException ex) {
            return Win32ErrorCodes.ERROR_IO_DEVICE;
        }

        long limit = quota.getLimit();
        long usage = quota.getUsage();

        freeBytesAvailable.setValue(limit - usage);
        totalNumberOfFreeBytes.setValue(limit - usage);
        totalNumberOfBytes.setValue(limit);
        return Win32ErrorCodes.ERROR_SUCCESS;
    }
}
