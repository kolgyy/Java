package backend.academy.linktracker.scrapper.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Link {

    private Long id;
    private String url;
    private List<String> tags;
    private List<String> filters;
}
