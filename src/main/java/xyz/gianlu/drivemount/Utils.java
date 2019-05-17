package xyz.gianlu.drivemount;

import com.google.api.services.drive.model.File;
import org.jetbrains.annotations.NotNull;

/**
 * @author Gianlu
 */
public final class Utils {

    private Utils() {
    }

    public static boolean isDirectory(@NotNull File file) {
        return file.getMimeType().equals("application/vnd.google-apps.folder");
    }

    public static boolean isGSuiteDocument(@NotNull File file) {
        return !isDirectory(file) && file.getMimeType().startsWith("application/vnd.google-apps.");
    }
}
