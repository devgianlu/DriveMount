package xyz.gianlu.drivemount;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Gianlu
 */
public class OAuthCallbackServer implements Runnable, Closeable {
    private static final Logger LOGGER = Logger.getLogger(OAuthCallbackServer.class);
    private static final Pattern FIND_CODE_PATTERN = Pattern.compile("code=(.+)[&|\\s]");
    private static final String RESPONSE_OK_MESSAGE = "Authentication successful! Your new drive should be appeared.";
    private static final String RESPONSE_FAILED_MESSAGE = "Authentication failed!";
    private final ServerSocket socket;
    private final Callback callback;
    private volatile boolean shouldStop = false;

    private OAuthCallbackServer(int port, @NotNull Callback callback) throws IOException {
        socket = new ServerSocket(port);
        this.callback = callback;
    }

    public static int start(@NotNull Callback callback) throws IOException {
        int port = ThreadLocalRandom.current().nextInt(10000, 40000);
        new Thread(new OAuthCallbackServer(port, callback)).start();
        return port;
    }

    @Override
    public void run() {
        while (!shouldStop) {
            try {
                Socket client = socket.accept();
                if (handle(client)) break;
            } catch (IOException ex) {
                LOGGER.error("Failed handling request!", ex);
                callback.failed();
            }
        }

        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private boolean handle(@NotNull Socket client) throws IOException {
        Scanner scanner = new Scanner(client.getInputStream());

        String requestLine = null;
        while (scanner.hasNextLine()) {
            if (requestLine == null) {
                requestLine = scanner.nextLine();
            } else {
                if (scanner.nextLine().isEmpty())
                    break;
            }
        }

        if (requestLine == null)
            throw new IllegalStateException();

        OutputStream out = client.getOutputStream();

        Matcher matcher = FIND_CODE_PATTERN.matcher(requestLine);
        if (matcher.find()) {
            String code = matcher.group(1);
            callback.obtainedCode(code);

            out.write("HTTP/1.1 200 OK\r\n".getBytes());
            out.write("Content-Length: ".getBytes());
            out.write(String.valueOf(RESPONSE_OK_MESSAGE.length()).getBytes());
            out.write("\r\n\r\n".getBytes());
            out.write(RESPONSE_OK_MESSAGE.getBytes());
            out.write("\r\n".getBytes());
            out.flush();
            return true;
        }

        LOGGER.warn("Did not find code in request line: " + requestLine);

        out.write("HTTP/1.1 400 Bad Request\r\n".getBytes());
        out.write("Content-Length: ".getBytes());
        out.write(String.valueOf(RESPONSE_FAILED_MESSAGE.length()).getBytes());
        out.write("\r\n\r\n".getBytes());
        out.write(RESPONSE_FAILED_MESSAGE.getBytes());
        out.write("\r\n".getBytes());
        out.flush();

        return false;
    }

    @Override
    public void close() {
        shouldStop = true;
    }

    public interface Callback {
        void obtainedCode(@NotNull String code);

        void failed();
    }
}
