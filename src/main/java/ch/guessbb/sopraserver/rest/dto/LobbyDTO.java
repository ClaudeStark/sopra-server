package ch.guessbb.sopraserver.rest.dto;
import ch.guessbb.sopraserver.constant.LobbyState;
import ch.guessbb.sopraserver.constant.LobbyVisibility;
import lombok.*;

@Getter
@Setter
public class LobbyDTO {
    private Long lobbyId;
    private String lobbyName;
    private Integer maxPlayers;
    private Integer currentPlayers;  // berechnet aus players.size()
    private LobbyVisibility visibility;
    private Integer maxRounds;
    private LobbyState lobbyState;
    private String lobbyCode;
}