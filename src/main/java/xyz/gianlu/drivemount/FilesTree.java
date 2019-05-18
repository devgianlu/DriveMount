package xyz.gianlu.drivemount;

import com.dokan.java.DokanyOperations;
import com.dokan.java.structure.ByHandleFileInformation;
import com.dokan.java.structure.DokanFileInfo;
import com.google.api.services.drive.model.File;
import com.sun.jna.platform.win32.WinNT;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Gianlu
 */
public class FilesTree {
    public final List<File> files = new ArrayList<>();
    public final List<FilesTree> directories = new ArrayList<>();
    public final String path;
    private final String name;
    public String id;
    public boolean populated = false;
    public FilesTree parent;
    private File directory;

    public FilesTree(@NotNull String name, @NotNull String path, @Nullable FilesTree parent) {
        this.name = name;
        this.path = path;
        this.parent = parent;
    }

    @Nullable
    private static FilesTree findDirectChild(@NotNull FilesTree parent, @NotNull String name) {
        for (FilesTree tree : parent.directories)
            if (Objects.equals(tree.name, name))
                return tree;

        return null;
    }

    public boolean isRoot() {
        return parent == null;
    }

    public void populate(@NotNull List<File> ff) {
        if (populated) return;

        for (File file : ff) {
            if (Utils.isGSuiteDocument(file) || file.getTrashed() || file.getShared() || !file.getParents().contains(id))
                continue;

            if (Utils.isDirectory(file)) {
                FilesTree tree = new FilesTree(file.getName(), path + file.getName() + "/", this);
                tree.directory = file;
                tree.id = file.getId();
                directories.add(tree);
            } else {
                files.add(file);
            }
        }

        populated = true;
    }

    public void writeTo(@NotNull DokanyOperations.FillWin32FindData rawFillFindData, int volumeSerialnumber, @NotNull DokanFileInfo dokanFileInfo) {
        for (FilesTree tree : directories) {
            ByHandleFileInformation info = new ByHandleFileInformation(new java.io.File(tree.path).toPath(), WinNT.FILE_ATTRIBUTE_DIRECTORY,
                    FileTime.fromMillis(tree.directory.getCreatedTime().getValue()), FileTime.fromMillis(tree.directory.getModifiedTime().getValue()),
                    FileTime.fromMillis(tree.directory.getModifiedTime().getValue()),
                    volumeSerialnumber, 0, tree.id.hashCode());
            rawFillFindData.fillWin32FindData(info.toWin32FindData(), dokanFileInfo);
        }

        for (File file : files) {
            ByHandleFileInformation info = Utils.getFileInformation(file, volumeSerialnumber);
            rawFillFindData.fillWin32FindData(info.toWin32FindData(), dokanFileInfo);
        }
    }

    @Nullable
    public FilesTree findDirectory(@NotNull String path) {
        if (path.equals("/")) return this;

        FilesTree current = this;
        String[] split = path.split("/");
        for (String str : split) {
            if (str.isEmpty()) continue;

            current = findDirectChild(current, str);
            if (current == null) return null;
        }

        return current;
    }
}
