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
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Gianlu
 */
public class CustomFileSystem extends DokanyFileSystemStub {
    private final Drive drive;
    private final AtomicInteger handleHandler = new AtomicInteger(0);

    public CustomFileSystem(@NotNull FileSystemInformation fileSystemInformation, @NotNull Drive drive) {
        super(fileSystemInformation, false);
        this.drive = drive;
    }

    @Override
    public int zwCreateFile(WString rawPath, WinBase.SECURITY_ATTRIBUTES securityContext, int rawDesiredAccess, int rawFileAttributes, int rawShareAccess, int rawCreateDisposition, int rawCreateOptions, DokanFileInfo dokanFileInfo) {
        System.out.println("CREATE FILE: " + rawPath);

        long val = this.handleHandler.incrementAndGet();
        if (val == 0) {
            val = this.handleHandler.incrementAndGet();
        }

        dokanFileInfo.Context = val;

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

    @Override
    public int getFileInformation(WString rawPath, ByHandleFileInformation handleFileInfo, DokanFileInfo dokanFileInfo) {
        if (dokanFileInfo.Context == 0)
            return Win32ErrorCodes.ERROR_INVALID_HANDLE;

        System.out.println("FILE INFO: " + rawPath);

        if (rawPath.toString().equals("\\")) {
            return Win32ErrorCodes.ERROR_SUCCESS;
        } else {
            return Win32ErrorCodes.ERROR_FILE_NOT_FOUND; // TODO
        }
    }

    @Override
    public int findFiles(WString rawPath, DokanyOperations.FillWin32FindData rawFillFindData, DokanFileInfo dokanFileInfo) {
        if (dokanFileInfo.Context == 0)
            return Win32ErrorCodes.ERROR_INVALID_HANDLE;

        System.out.println("FIND FILES: " + rawPath);

        List<File> files;
        if (rawPath.toString().equals("\\")) {
            try {
                files = drive.files().list().setFields("*").setSpaces("drive").execute().getFiles();
            } catch (IOException ex) {
                return Win32ErrorCodes.ERROR_IO_DEVICE;
            }
        } else {
            return Win32ErrorCodes.ERROR_FILE_NOT_FOUND; // TODO
        }

        for (File file : files) {
            if (file.getSize() == null || file.getTrashed() || file.getShared()) continue;

            ByHandleFileInformation info = getFileInformation(file);
            rawFillFindData.fillWin32FindData(info.toWin32FindData(), dokanFileInfo);
        }

        return Win32ErrorCodes.ERROR_SUCCESS;
    }

    @NotNull
    private ByHandleFileInformation getFileInformation(@NotNull File file) {
        long index = file.getId().hashCode();

        int fileAttr = 0;
        fileAttr |= 0; // attr.isArchive() ? WinNT.FILE_ATTRIBUTE_ARCHIVE : 0;
        fileAttr |= 0; // attr.isSystem() ? WinNT.FILE_ATTRIBUTE_SYSTEM : 0;
        fileAttr |= 0; // attr.isHidden() ? WinNT.FILE_ATTRIBUTE_HIDDEN : 0;
        fileAttr |= 0; // attr.isReadOnly() ? WinNT.FILE_ATTRIBUTE_READONLY : 0;
        fileAttr |= 0; // attr.isDirectory() ? WinNT.FILE_ATTRIBUTE_DIRECTORY : 0;
        fileAttr |= 0; // attr.isSymbolicLink() ? WinNT.FILE_ATTRIBUTE_REPARSE_POINT : 0;

        if (fileAttr == 0) fileAttr |= WinNT.FILE_ATTRIBUTE_NORMAL;

        String name = file.getOriginalFilename();
        if (name == null) name = file.getName();

        return new ByHandleFileInformation(new java.io.File(name).toPath(), fileAttr,
                FileTime.fromMillis(file.getCreatedTime().getValue()), FileTime.fromMillis(file.getModifiedTime().getValue()),
                FileTime.fromMillis(file.getModifiedTime().getValue()),
                this.volumeSerialnumber, file.getSize() == null ? 0 : file.getSize().longValue(), index);
    }

    @Override
    public void closeFile(WString rawPath, DokanFileInfo dokanFileInfo) {
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
}
