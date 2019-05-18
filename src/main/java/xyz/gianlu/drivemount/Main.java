package xyz.gianlu.drivemount;

import com.dokan.java.FileSystemInformation;
import com.dokan.java.constants.dokany.MountOption;
import com.dokan.java.constants.microsoft.FileSystemFlag;
import com.dokan.java.structure.EnumIntegerSet;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.googleapis.apache.GoogleApacheHttpTransport;
import com.google.api.client.googleapis.auth.oauth2.*;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.About;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Gianlu
 */
public class Main {
    private static final JsonFactory JSON_FACTORY = new JacksonFactory();
    private static final String APP_NAME = "DriveMount";

    private static void launchUrl(@NotNull URI url) throws IOException {
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                desktop.browse(url);
                return;
            }
        }

        System.out.println("Visit the following page: " + url);
    }

    @NotNull
    private static Credential authorize(@NotNull HttpTransport transport) throws IOException, InterruptedException {
        InputStream googleServices = Main.class.getClassLoader().getResourceAsStream("google-services.json");
        if (googleServices == null) throw new IllegalStateException("Missing google-services.json file!");

        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(googleServices));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(transport, JSON_FACTORY, clientSecrets,
                Collections.singleton(DriveScopes.DRIVE))
                .setDataStoreFactory(new FileDataStoreFactory(new File("./appData/")))
                .build();

        StoredCredential stored = flow.getCredentialDataStore().get(APP_NAME);
        if (stored == null) {
            final Object lock = new Object();
            final AtomicReference<String> obtainedCode = new AtomicReference<>(null);
            int port = OAuthCallbackServer.start(new OAuthCallbackServer.Callback() {
                @Override
                public void obtainedCode(@NotNull String code) {
                    obtainedCode.set(code);
                    synchronized (lock) {
                        lock.notifyAll();
                    }
                }

                @Override
                public void failed() {
                    obtainedCode.set(null);
                    synchronized (lock) {
                        lock.notifyAll();
                    }
                }
            });

            String redirectUri = "http://localhost:" + port + "/";

            GoogleAuthorizationCodeRequestUrl url = flow.newAuthorizationUrl();
            url.setRedirectUri(redirectUri);
            launchUrl(url.toURI());

            synchronized (lock) {
                lock.wait();
            }

            String code = obtainedCode.get();
            if (code == null) throw new IllegalStateException("Failed obtaining authorization code!");

            GoogleTokenResponse token = flow.newTokenRequest(code)
                    .setRedirectUri(redirectUri).execute();

            return flow.createAndStoreCredential(token, APP_NAME);
        } else {
            return new GoogleCredential.Builder()
                    .setTransport(transport)
                    .setClientSecrets(clientSecrets)
                    .setJsonFactory(JSON_FACTORY)
                    .build()
                    .setRefreshToken(stored.getRefreshToken())
                    .setExpirationTimeMilliseconds(stored.getExpirationTimeMilliseconds())
                    .setAccessToken(stored.getAccessToken());
        }
    }

    public static void main(String[] args) throws GeneralSecurityException, IOException, InterruptedException {
        HttpTransport transport = GoogleApacheHttpTransport.newTrustedTransport();
        Credential credential = authorize(transport);

        Drive drive = new Drive.Builder(transport, JSON_FACTORY, credential)
                .setApplicationName(APP_NAME)
                .build();

        About about = drive.about().get().setFields("user").execute();

        GoogleDriveFileSystem fs = new GoogleDriveFileSystem(new FileSystemInformation(new EnumIntegerSet<>(FileSystemFlag.NONE)), drive);
        fs.mount(new File("K:\\").toPath(), "DriveMount (" + about.getUser().getEmailAddress() + ")", 30975, true,
                3000, 4096, 512, null, (short) 5, new EnumIntegerSet<>(MountOption.DEBUG_MODE));
    }
}
