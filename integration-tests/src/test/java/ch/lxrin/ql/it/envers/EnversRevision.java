package ch.lxrin.ql.it.envers;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionListener;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** The revision table {@code revision(id, revised_at, user_id)} as an Envers revision entity. */
@Entity
@Table(name = "revision")
@RevisionEntity(EnversRevision.UserListener.class)
public class EnversRevision {

    /** The user Envers stores in new revisions. */
    public static final AtomicReference<UUID> CURRENT_USER = new AtomicReference<>();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @RevisionNumber
    private Long id;

    @RevisionTimestamp
    @Column(name = "revised_at")
    private Instant revisedAt;

    @Column(name = "user_id")
    private UUID userId;

    public Long getId() {
        return id;
    }

    public Instant getRevisedAt() {
        return revisedAt;
    }

    public UUID getUserId() {
        return userId;
    }

    /** Sets the user of a new revision. */
    public static final class UserListener implements RevisionListener {
        @Override
        public void newRevision(Object revisionEntity) {
            ((EnversRevision) revisionEntity).userId = CURRENT_USER.get();
        }
    }
}
