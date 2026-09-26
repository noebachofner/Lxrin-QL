package ch.lxrin.ql.spring;

import ch.lxrin.ql.error.LxrinQlException;
import ch.lxrin.ql.schema.Table;
import ch.lxrin.ql.spi.ExecutionObserver;
import ch.lxrin.ql.spi.StatementEvent;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.StringJoiner;

/**
 * Reports every statement as a Micrometer {@link Observation} named
 * {@code lxrin.ql.statement}, which gives metrics (timers) and tracing spans.
 * Low-cardinality keys: {@code kind}, {@code tables}, {@code origin}; the
 * SQL (without bind values) is a high-cardinality key.
 */
public final class ObservationExecutionObserver implements ExecutionObserver {

    /** The observation name. */
    public static final String NAME = "lxrin.ql.statement";

    private final ObservationRegistry registry;
    private final ThreadLocal<Deque<Observation>> running = ThreadLocal.withInitial(ArrayDeque::new);

    /** Creates the observer. */
    public ObservationExecutionObserver(ObservationRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void onStart(StatementEvent event) {
        StringJoiner tables = new StringJoiner(",");
        for (Table<?> t : event.tables()) tables.add(t.qualifiedName());
        Observation observation = Observation.createNotStarted(NAME, registry)
                .lowCardinalityKeyValue("kind", event.kind().name())
                .lowCardinalityKeyValue("tables", tables.toString())
                .lowCardinalityKeyValue("origin", event.origin().type().name())
                .highCardinalityKeyValue("sql", event.sql().sql())
                .contextualName("lxrin-ql " + event.kind().name().toLowerCase(java.util.Locale.ROOT))
                .start();
        running.get().push(observation);
    }

    @Override
    public void onSuccess(StatementEvent event, Duration took, long rows) {
        Observation observation = running.get().poll();
        if (observation != null) observation.highCardinalityKeyValue("rows", Long.toString(rows)).stop();
    }

    @Override
    public void onError(StatementEvent event, Duration took, LxrinQlException error) {
        Observation observation = running.get().poll();
        if (observation != null) {
            observation.error(error);
            observation.stop();
        }
    }
}
