package ch.lxrin.ql.maven;

import ch.lxrin.ql.codegen.SchemaSources;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

/**
 * Goal {@code check-snapshot}: fails if the schema snapshot does not match the migrations,
 * e.g. in CI ({@code mvn lxrin-ql:check-snapshot}). With Docker it compares the complete
 * snapshot; without Docker only the hash of the migrations.
 */
public class CheckSnapshotMojo extends AbstractLxrinQlMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("LxrinQL snapshot check skipped");
            return;
        }
        boolean upToDate;
        try {
            upToDate = new SchemaSources().checkSnapshot(selection(), database(), snapshot(), log());
        } catch (Exception e) {
            throw failure("LxrinQL snapshot check", e);
        }
        if (!upToDate) {
            throw new MojoFailureException("The LxrinQL schema snapshot " + snapshotFile
                    + " is out of date. Run mvn lxrin-ql:snapshot and commit the file.");
        }
    }
}
