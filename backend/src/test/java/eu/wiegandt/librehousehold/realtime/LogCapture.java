package eu.wiegandt.librehousehold.realtime;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Collects the log lines of one class with a Logback {@link ListAppender}. Closing detaches the
 * appender, so a test sees only the lines written while it ran.
 */
public final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private LogCapture(Class<?> loggingClass) {
        logger = (Logger) LoggerFactory.getLogger(loggingClass);
        appender.start();
        logger.addAppender(appender);
    }

    public static LogCapture of(Class<?> loggingClass) {
        return new LogCapture(loggingClass);
    }

    /**
     * The captured lines as {@code LEVEL message}, with the arguments filled in.
     */
    public List<String> lines() {
        return appender.list.stream().map(LogCapture::line).toList();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
    }

    private static String line(ILoggingEvent event) {
        return event.getLevel() + " " + event.getFormattedMessage();
    }
}
