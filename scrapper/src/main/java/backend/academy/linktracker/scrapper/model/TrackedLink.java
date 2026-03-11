package backend.academy.linktracker.scrapper.model;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface TrackedLink {

    Link getLink();

    Optional<OffsetDateTime> getLastUpdated();

    void setLastUpdated(OffsetDateTime lastUpdated);

    LinkType getType();
}
