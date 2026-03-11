package backend.academy.linktracker.scrapper.model;

import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@RequiredArgsConstructor
public class UnknownTrackedLink implements TrackedLink {

    private final Link link;

    @Setter
    private OffsetDateTime lastUpdated;

    @Override
    public Optional<OffsetDateTime> getLastUpdated() {
        return Optional.ofNullable(lastUpdated);
    }

    @Override
    public LinkType getType() {
        return LinkType.UNKNOWN;
    }
}
