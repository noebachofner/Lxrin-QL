package ch.lxrin.ql.it;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The publish workflow's check that the Gradle Plugin Portal serves the plugin, against a local server that answers
 * like the portal: 200 for a hosted plugin, a 303 redirect to Maven Central for one it does not host, 404 otherwise.
 */
class PluginPortalCheckIT {

    private static final Path SCRIPT = Path.of(System.getProperty("lxrin.root"), ".github/scripts/check-plugin-portal.sh");
    private static final String MARKER = "/m2/ch/lxrin/ql/codegen/ch.lxrin.ql.codegen.gradle.plugin/";

    private HttpServer server;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/m2/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals(MARKER + "3.2.0/ch.lxrin.ql.codegen.gradle.plugin-3.2.0.pom")) {
                byte[] body = "<project/>".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            } else if (path.startsWith(MARKER + "3.1.0/")) {
                exchange.getResponseHeaders().add("Location", "https://repo.maven.apache.org/maven2" + path.substring(3));
                exchange.sendResponseHeaders(303, -1);
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private Result check(String version) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(List.of("bash", SCRIPT.toString(), "ch.lxrin.ql.codegen", version, "2", "0"))
                .redirectErrorStream(true);
        pb.environment().put("PLUGIN_PORTAL_URL", "http://127.0.0.1:" + server.getAddress().getPort() + "/m2");
        pb.environment().remove("GITHUB_ACTIONS");
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(1, TimeUnit.MINUTES));
        return new Result(process.exitValue(), output);
    }

    private record Result(int status, String output) {}

    @Test
    void passesOnlyWhenThePortalItselfServesTheMarker() throws Exception {
        Result hosted = check("3.2.0");
        assertEquals(0, hosted.status(), hosted.output());
        assertTrue(hosted.output().contains("The Gradle Plugin Portal serves ch.lxrin.ql.codegen 3.2.0"), hosted.output());

        Result redirected = check("3.1.0");
        assertEquals(1, redirected.status(), redirected.output());
        assertTrue(redirected.output().contains("Attempt 2/2: HTTP 303"), redirected.output());
        assertTrue(redirected.output().contains("does not serve ch.lxrin.ql.codegen 3.1.0"), redirected.output());

        Result missing = check("9.9.9");
        assertEquals(1, missing.status(), missing.output());
        assertTrue(missing.output().contains("HTTP 404"), missing.output());
    }
}
