package backend.academy.linktracker.bot.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class Link {

    private String url;
    private List<String> tags;

}
