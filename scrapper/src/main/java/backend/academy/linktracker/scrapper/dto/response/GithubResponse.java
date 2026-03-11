package backend.academy.linktracker.scrapper.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record GithubResponse(@JsonProperty("updated_at") OffsetDateTime updatedAt) {}
