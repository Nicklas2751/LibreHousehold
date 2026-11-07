package eu.wiegandt.librehousehold.realtime;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads a Server-Sent Events stream in integration tests. {@code RestTestClient} only returns a
 * response once it is complete, which a stream never is; the JDK {@link HttpClient} hands out the
 * lines while they arrive. A virtual thread parses them into {@link SseEvent}s, one per block
 * terminated by a blank line.
 *
 * <p>Open it with try-with-resources: {@link #close()} drops the connection, like a closed browser
 * tab.
 */
public final class SseTestClient implements AutoCloseable {

    private static final String TEXT_EVENT_STREAM = "text/event-stream";
    private static final String COOKIE_SEPARATOR = "; ";

    private final HttpClient httpClient;
    private final HttpResponse<Stream<String>> response;
    private final BlockingQueue<SseEvent> events = new LinkedBlockingQueue<>();
    private final CountDownLatch closed = new CountDownLatch(1);

    private SseTestClient(HttpClient httpClient, HttpResponse<Stream<String>> response) {
        this.httpClient = httpClient;
        this.response = response;
        Thread.ofVirtual().start(this::readEvents);
    }

    /**
     * Opens the stream and returns as soon as the response head has arrived, so status and headers
     * can be checked while the stream stays open.
     */
    public static SseTestClient open(URI uri, Map<String, String> cookies) throws IOException, InterruptedException {
        // HTTP/1.1 like EventSource behind the dev proxy; the default would try an h2c upgrade first
        var httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        var request = HttpRequest.newBuilder(uri)
                .header("Accept", TEXT_EVENT_STREAM)
                .header("Cookie", cookieHeader(cookies))
                .GET()
                .build();
        return new SseTestClient(httpClient, httpClient.send(request, HttpResponse.BodyHandlers.ofLines()));
    }

    public int status() {
        return response.statusCode();
    }

    public HttpHeaders headers() {
        return response.headers();
    }

    /**
     * The next block of the stream, which may also be a heartbeat comment or a bare {@code retry:}.
     *
     * @throws AssertionError if no block arrives within the timeout
     */
    public SseEvent awaitEvent(Duration timeout) throws InterruptedException {
        var event = events.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (event == null) {
            throw new AssertionError("No event within " + timeout);
        }
        return event;
    }

    /**
     * Whether the server ended the stream within the timeout.
     */
    public boolean awaitClosed(Duration timeout) throws InterruptedException {
        return closed.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        httpClient.shutdownNow();
        httpClient.close();
    }

    private void readEvents() {
        try (var lines = response.body()) {
            lines.forEach(new SseEventParser(events::add));
        } catch (UncheckedIOException connectionDropped) {
            // Expected after close(): the client drops the connection while the reader is blocked
        } finally {
            closed.countDown();
        }
    }

    private static String cookieHeader(Map<String, String> cookies) {
        return cookies.entrySet().stream()
                .map(cookie -> cookie.getKey() + "=" + cookie.getValue())
                .collect(Collectors.joining(COOKIE_SEPARATOR));
    }

    /**
     * One block of the stream. Fields the block did not contain are {@code null}, or empty for the
     * comments.
     *
     * @param name     the {@code event:} field
     * @param data     the {@code data:} lines, joined with a line feed
     * @param retry    the {@code retry:} field in milliseconds
     * @param comments the comment lines (e.g. heartbeats) without the leading colon
     */
    public record SseEvent(@Nullable String name, @Nullable String data, @Nullable Long retry, List<String> comments) {
    }

    /**
     * Parses the stream line by line as the SSE specification describes it. Runs only on the reader
     * thread, so its state needs no synchronization.
     */
    private static final class SseEventParser implements Consumer<String> {

        private static final String COMMENT_PREFIX = ":";
        private static final String FIELD_SEPARATOR = ":";
        private static final String DATA_LINE_SEPARATOR = "\n";

        private final Consumer<SseEvent> sink;
        private @Nullable String name;
        private final List<String> dataLines = new ArrayList<>();
        private @Nullable Long retry;
        private final List<String> comments = new ArrayList<>();

        private SseEventParser(Consumer<SseEvent> sink) {
            this.sink = sink;
        }

        @Override
        public void accept(String line) {
            if (line.isEmpty()) {
                dispatch();
                return;
            }
            if (line.startsWith(COMMENT_PREFIX)) {
                comments.add(withoutLeadingSpace(line.substring(COMMENT_PREFIX.length())));
                return;
            }
            readField(line);
        }

        private void readField(String line) {
            var separator = line.indexOf(FIELD_SEPARATOR);
            var field = separator < 0 ? line : line.substring(0, separator);
            var value = separator < 0 ? "" : withoutLeadingSpace(line.substring(separator + 1));
            switch (field) {
                case "event" -> name = value;
                case "data" -> dataLines.add(value);
                case "retry" -> retry = Long.valueOf(value);
                default -> {
                    // The specification ignores unknown fields such as id
                }
            }
        }

        private void dispatch() {
            if (name == null && dataLines.isEmpty() && retry == null && comments.isEmpty()) {
                return;
            }
            var data = dataLines.isEmpty() ? null : String.join(DATA_LINE_SEPARATOR, dataLines);
            sink.accept(new SseEvent(name, data, retry, List.copyOf(comments)));
            name = null;
            dataLines.clear();
            retry = null;
            comments.clear();
        }

        private static String withoutLeadingSpace(String value) {
            return value.startsWith(" ") ? value.substring(1) : value;
        }
    }
}
