package backend.academy.linktracker.bot.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class UserSession {

    private Long chatId;
    private UserState state = UserState.IDLE;

    private String currentLink;
    private String currentTags;

    public UserSession(Long chatId) {
        this.chatId = chatId;
    }

    public void resetState() {
        this.state = UserState.IDLE;
    }

    public void resetAll() {
        this.state = UserState.IDLE;
        this.currentLink = null;
        this.currentTags = null;
    }

}
