package ch.lxrin.ql.maven;

import ch.lxrin.ql.codegen.SchemaSources;
import org.apache.maven.plugin.MojoExecutionException;

/**
 * Goal {@code snapshot}: reads the schema from the database (Docker or a JDBC URL) and
 * writes the schema snapshot ({@code src/main/lxrinql/schema.json} by default). Commit
 * it; {@code generate} can then run without Docker. Run it with
 * {@code mvn lxrin-ql:snapshot}.
 */
public class SnapshotMojo extends AbstractLxrinQlMojo {

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("LxrinQL snapshot skipped");
            return;
        }
        try {
            new SchemaSources().writeSnapshot(selection(), database(), snapshot(), log());
        } catch (Exception e) {
            throw failure("LxrinQL snapshot", e);
        }
    }
}
