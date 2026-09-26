package ch.lxrin.ql.it.envers;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;

import java.util.UUID;

/** The table {@code note} as a JPA entity audited by Hibernate Envers. */
@Entity
@Table(name = "note")
@Audited
public class EnversNote {

    @Id
    private UUID id;
    private String title;
    private String body;

    protected EnversNote() {
    }

    public EnversNote(UUID id, String title) {
        this.id = id;
        this.title = title;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }
}
