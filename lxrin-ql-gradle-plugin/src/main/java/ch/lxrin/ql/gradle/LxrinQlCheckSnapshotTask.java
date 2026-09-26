package ch.lxrin.ql.gradle;

import ch.lxrin.ql.codegen.SchemaSources;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

/**
 * {@code lxrinQlCheckSnapshot}: fails if the snapshot file does not match the migrations,
 * e.g. in CI. With Docker it reads the schema and compares the complete snapshot; without
 * Docker it compares only the hash of the migrations.
 */
@UntrackedTask(because = "A check that must run every time it is requested")
public abstract class LxrinQlCheckSnapshotTask extends AbstractLxrinQlTask {

    /** The snapshot file. */
    @Internal
    public abstract RegularFileProperty getSnapshotFile();

    /** The snapshot file as an input; it may be missing. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSnapshotInput();

    /** Checks the snapshot. */
    @TaskAction
    public void check() {
        boolean upToDate;
        try {
            upToDate = new SchemaSources().checkSnapshot(selection(), database(), getSnapshotFile().get().getAsFile().toPath(), log());
        } catch (Exception e) {
            throw new GradleException("LxrinQL snapshot check failed: " + e.getMessage(), e);
        }
        if (!upToDate) {
            throw new GradleException("The LxrinQL schema snapshot " + getSnapshotFile().get().getAsFile()
                    + " is out of date. Run ./gradlew lxrinQlSnapshot and commit the file.");
        }
    }
}
