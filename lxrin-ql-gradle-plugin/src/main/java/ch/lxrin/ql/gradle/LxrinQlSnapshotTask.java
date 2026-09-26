package ch.lxrin.ql.gradle;

import ch.lxrin.ql.codegen.SchemaSources;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * {@code lxrinQlSnapshot}: reads the schema from the database (a disposable PostgreSQL with
 * the migrations, so Docker is needed) and writes it to the snapshot file, which is meant
 * to be committed. {@code generateLxrinQl} can then run without Docker.
 */
@DisableCachingByDefault(because = "Writes a file that belongs to the project sources; reading the schema needs a database")
public abstract class LxrinQlSnapshotTask extends AbstractLxrinQlTask {

    /** The snapshot file, by default {@code src/main/lxrinql/schema.json}. */
    @OutputFile
    public abstract RegularFileProperty getSnapshotFile();

    /** Writes the snapshot. */
    @TaskAction
    public void writeSnapshot() {
        try {
            new SchemaSources().writeSnapshot(selection(), database(), getSnapshotFile().get().getAsFile().toPath(), log());
        } catch (Exception e) {
            throw new GradleException("LxrinQL snapshot failed: " + e.getMessage(), e);
        }
    }
}
