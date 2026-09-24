package ch.lxrin.ql.beans;

import ch.lxrin.ql.RecordingExecutor;
import ch.lxrin.ql.runtime.QueryContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.*;

class BeansTest {

    public static final class Repo {
        final QueryContext ctx;

        public Repo(QueryContext ctx) {
            this.ctx = ctx;
        }
    }

    public static final class Service {
        final Repo repo;
        final Clock clock;

        public Service() {
            this(null, null);
        }

        public Service(Repo repo, Clock clock) {
            this.repo = repo;
            this.clock = clock;
        }
    }

    public static final class A {
        public A(B b) {
        }
    }

    public static final class B {
        public B(A a) {
        }
    }

    @AfterEach
    void reset() {
        BEANS.reset();
        QueryContext.setDefault(null);
    }

    @Test
    void createsSingletonsWithConstructorInjection() {
        QueryContext ctx = QueryContext.builder().executor(new RecordingExecutor()).build();
        QueryContext.setDefault(ctx);
        Clock clock = Clock.systemUTC();
        BEANS.register(Clock.class, clock);
        Service service = BEANS.get(Service.class);
        assertSame(service, BEANS.get(Service.class));
        assertSame(BEANS.get(Repo.class), service.repo);
        assertSame(ctx, service.repo.ctx);
        assertSame(clock, service.clock);
    }

    @Test
    void reportsProblems() {
        IllegalStateException cycle = assertThrows(IllegalStateException.class, () -> BEANS.get(A.class));
        assertTrue(cycle.getMessage().contains("circular") || cycle.getCause().getMessage().contains("circular"));
        assertThrows(IllegalStateException.class, () -> BEANS.get(Runnable.class));
        assertThrows(IllegalStateException.class, () -> BEANS.get(Repo.class), "no default context");
    }

    @Test
    void registryCanBeReplaced() {
        BeanRegistry custom = new BeanRegistry() {
            @Override
            public <T> T get(Class<T> type) {
                return type.cast("from custom");
            }

            @Override
            public <T> void register(Class<T> type, T instance) {
            }
        };
        BEANS.setRegistry(custom);
        assertSame(custom, BEANS.registry());
        assertEquals("from custom", BEANS.get(String.class));
    }
}
