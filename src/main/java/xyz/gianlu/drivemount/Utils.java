package xyz.gianlu.drivemount;

import com.dokan.java.structure.ByHandleFileInformation;
import com.google.api.services.drive.model.File;
import com.sun.jna.platform.win32.WinNT;
import org.jetbrains.annotations.NotNull;

import java.nio.file.attribute.FileTime;

/**
 * @author Gianlu
 */
public final class Utils {

    private Utils() {
    }

    public static boolean isRootDirectory(@NotNull String id) {
        return id.length() == 19;
    }

    public static boolean isDirectory(@NotNull File file) {
        return file.getMimeType().equals("application/vnd.google-apps.folder");
    }

    public static boolean isGSuiteDocument(@NotNull File file) {
        return !isDirectory(file) && file.getMimeType().startsWith("application/vnd.google-apps.");
    }

    @NotNull
    public static ByHandleFileInformation getFileInformation(@NotNull File file, int volumeSerialnumber) {
        long index = file.getId().hashCode();

        int fileAttr = 0;
        // fileAttr |= attr.isArchive() ? WinNT.FILE_ATTRIBUTE_ARCHIVE : 0;
        // fileAttr |= attr.isSystem() ? WinNT.FILE_ATTRIBUTE_SYSTEM : 0;
        // fileAttr |= attr.isHidden() ? WinNT.FILE_ATTRIBUTE_HIDDEN : 0;
        fileAttr |= file.getCapabilities().getCanEdit() ? 0 : WinNT.FILE_ATTRIBUTE_READONLY;
        fileAttr |= Utils.isDirectory(file) ? WinNT.FILE_ATTRIBUTE_DIRECTORY : 0;
        // fileAttr |= attr.isSymbolicLink() ? WinNT.FILE_ATTRIBUTE_REPARSE_POINT : 0;

        if (fileAttr == 0) fileAttr |= WinNT.FILE_ATTRIBUTE_NORMAL;

        String name = file.getOriginalFilename();
        if (name == null) name = file.getName();

        Long size = file.getSize();
        if (size == null) size = 0L;

        return new ByHandleFileInformation(new java.io.File(name).toPath(), fileAttr,
                FileTime.fromMillis(file.getCreatedTime().getValue()), FileTime.fromMillis(file.getModifiedTime().getValue()),
                FileTime.fromMillis(file.getModifiedTime().getValue()),
                volumeSerialnumber, size, index);
    }
}
