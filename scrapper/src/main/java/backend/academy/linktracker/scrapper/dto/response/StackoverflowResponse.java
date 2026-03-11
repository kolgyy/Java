package backend.academy.linktracker.scrapper.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record StackoverflowResponse(List<Item> items) {

    public record Item(
            @JsonProperty("question_id") long questionId,
            @JsonProperty("last_activity_date") long lastActivityDate) {}
}
