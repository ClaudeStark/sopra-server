package ch.guessbb.sopraserver.rest.dto;

import ch.guessbb.sopraserver.constant.LobbyVisibility;
import lombok.*;

@Getter
@Setter
public class CreateLobbyPostDTO {
    private String lobbyName;
    private Integer maxPlayers;
    private LobbyVisibility visibility;
    private Integer maxRounds;
}