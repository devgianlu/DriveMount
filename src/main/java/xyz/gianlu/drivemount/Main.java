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

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Scanner;

/**
 * @author Gianlu
 */
public class Main {
    private static final JsonFactory JSON_FACTORY = new JacksonFactory();

    private static Credential authorize(HttpTransport transport) throws IOException {
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new StringReader("{\"installed\":{\"client_id\":\"727426688073-gfr9ere6hvitifkcbi5jg31pqb73hqrd.apps.googleusercontent.com\",\"project_id\":\"drivemount-java\",\"auth_uri\":\"https://accounts.google.com/o/oauth2/auth\",\"token_uri\":\"https://oauth2.googleapis.com/token\",\"auth_provider_x509_cert_url\":\"https://www.googleapis.com/oauth2/v1/certs\",\"client_secret\":\"1mV346QMsoe7jGD7PmvRHpTT\",\"redirect_uris\":[\"urn:ietf:wg:oauth:2.0:oob\",\"http://localhost\"]}}"));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(transport, JSON_FACTORY, clientSecrets,
                Collections.singleton(DriveScopes.DRIVE))
                .setDataStoreFactory(new FileDataStoreFactory(new File("./")))
                .build();

        StoredCredential stored = flow.getCredentialDataStore().get("TEST");
        if (stored == null) {
            GoogleAuthorizationCodeRequestUrl url = flow.newAuthorizationUrl();
            url.setRedirectUri("http://localhost/");
            System.out.println(url.toURL());

            Scanner scanner = new Scanner(System.in);
            String code = scanner.nextLine();
            GoogleTokenResponse token = flow.newTokenRequest(code).setRedirectUri("http://localhost/").execute();

            return flow.createAndStoreCredential(token, "TEST");
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

    public static void main(String[] args) throws GeneralSecurityException, IOException {
        HttpTransport transport = GoogleApacheHttpTransport.newTrustedTransport();
        Credential credential = authorize(transport);

        Drive drive = new Drive.Builder(transport, JSON_FACTORY, credential)
                .setApplicationName("DriveMount")
                .build();

        About about = drive.about().get().setFields("*").execute();
        System.out.println(about);

        System.out.println(drive.files().list()
                .setFields("*" /* Dev only */)
                .setPageSize(1000 /* Max */)
                .execute().getFiles());

        GoogleDriveFileSystem fs = new GoogleDriveFileSystem(new FileSystemInformation(new EnumIntegerSet<>(FileSystemFlag.NONE)), drive);
        fs.mount(new File("K:\\").toPath(), "DriveMount (" + about.getUser().getEmailAddress() + ")", 30975, true,
                3000, 4096, 512, null, (short) 5, new EnumIntegerSet<>(MountOption.DEBUG_MODE));
    }
}
