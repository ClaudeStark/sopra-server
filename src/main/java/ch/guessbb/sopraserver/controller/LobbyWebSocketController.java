package ch.guessbb.sopraserver.controller;

import ch.guessbb.sopraserver.constant.MessageType;
import ch.guessbb.sopraserver.objects.Lobby;
import ch.guessbb.sopraserver.security.AuthHeader;
import ch.guessbb.sopraserver.security.AuthService;
import ch.guessbb.sopraserver.service.LobbyService;
import ch.guessbb.sopraserver.websocket.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;


@Controller
public class LobbyWebSocketController {

    private final LobbyService lobbyService;
    private final AuthService authService;

    //@Autowired
    private final ObjectMapper objectMapper;

    public LobbyWebSocketController(LobbyService lobbyService, AuthService authService, ObjectMapper objectMapper) {
        this.lobbyService = lobbyService;
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @MessageMapping("/lobby/{lobbyId}/start")
    public void startGameAdmin(@DestinationVariable String lobbyId, Message message) {
        System.out.println("CHeck ob im controller");
        Lobby lobby = lobbyService.getLobbyById(Long.parseLong(lobbyId));

            
        // Authenticate the user
        // Convert payload to AuthHeader
        AuthHeader authHeader = objectMapper.convertValue(
            message.getPayload(),
            AuthHeader.class
        );

        if (!authService.authUser(authHeader)) {
            throw new  ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        //Check whether user is admin of the lobby
        Long userId = authHeader.getUserId();
        if (!lobby.getAdmin().getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the admin can start the game");
  }
        System.out.println("Check nach admin check");
        // Start the game
        lobbyService.startGame(Long.parseLong(lobbyId));
        
}

//Leave Lobby
@MessageMapping("/lobby/{lobbyId}/leave")
    public void leaveLobby(@DestinationVariable String lobbyId, Message message) {
        System.out.println("In LobbyWebSocketController");
        Lobby lobby = lobbyService.getLobbyById(Long.parseLong(lobbyId));

            
        // Authenticate the user
        // Convert payload to AuthHeader
        MessageType type = message.getType();
        if (type == MessageType.LEAVE_LOBBY) {
            AuthHeader authHeader = objectMapper.convertValue(message.getPayload(), AuthHeader.class);

            try {
                boolean isAuthenticated = authService.authUser(authHeader);
                System.out.println("isAuthenticated: " + isAuthenticated);
                if (!isAuthenticated) {
                    return;
                }

                // remove user from Lobby
                lobbyService.leaveLobby(Long.parseLong(lobbyId), authHeader.getUserId());
            }
            catch (ResponseStatusException e) {
            }
        }
}
}
