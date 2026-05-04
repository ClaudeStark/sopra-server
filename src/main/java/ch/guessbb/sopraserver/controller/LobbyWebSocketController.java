package ch.guessbb.sopraserver.controller;

import ch.guessbb.sopraserver.constant.MessageType;
import ch.guessbb.sopraserver.entity.Lobby;
import ch.guessbb.sopraserver.security.AuthHeader;
import ch.guessbb.sopraserver.security.AuthService;
import ch.guessbb.sopraserver.service.LobbyService;
import ch.guessbb.sopraserver.websocket.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;


@Controller
public class LobbyWebSocketController {

    private final LobbyService lobbyService;
    private final AuthService authService;

    public LobbyWebSocketController(LobbyService lobbyService, AuthService authService) {
        this.lobbyService = lobbyService;
        this.authService = authService;
    }

    @MessageMapping("/lobby/{lobbyId}/start")
    public void startGameAdmin(
            @DestinationVariable String lobbyId,
            @Header("userId") String userId,
            @Header("token") String token) {

        AuthHeader authHeader = new AuthHeader(Long.parseLong(userId), token);
        if (!authService.authUser(authHeader)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        Lobby lobby = lobbyService.getLobbyById(Long.parseLong(lobbyId));
        if (!lobby.getAdmin().getUserId().equals(Long.parseLong(userId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the admin can start the game");
        }

        lobbyService.startGame(Long.parseLong(lobbyId));
    }

    @MessageMapping("/lobby/{lobbyId}/leave")
    public void leaveLobby(
            @DestinationVariable String lobbyId,
            @Header("userId") String userId,
            @Header("token") String token) {

        AuthHeader authHeader = new AuthHeader(Long.parseLong(userId), token);
        if (!authService.authUser(authHeader)) {
            return;
        }

        lobbyService.leaveLobby(Long.parseLong(lobbyId), Long.parseLong(userId));
    }
}
