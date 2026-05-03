package ch.guessbb.sopraserver.rest.dto;

import ch.guessbb.sopraserver.constant.LobbyState;
import ch.guessbb.sopraserver.constant.LobbyVisibility;
import lombok.*;
import java.util.List;

@Getter
@Setter
public class MyLobbyDTO {
    private Long lobbyId;
    private String lobbyName;
    private String lobbyCode;
    private Long adminId;
    private Integer maxPlayers;
    private Integer currentPlayers;
    private LobbyVisibility visibility;
    private Integer maxRounds;
    private LobbyState lobbyState;
    private List<UserDTO> players;
}