package ch.guessbb.sopraserver.controller;

import ch.guessbb.sopraserver.constant.MessageType;
import ch.guessbb.sopraserver.objects.Game;
import ch.guessbb.sopraserver.objects.Lobby;
import ch.guessbb.sopraserver.objects.UserGameStatus;
import ch.guessbb.sopraserver.rest.dto.GuessMessageDTO;
import ch.guessbb.sopraserver.service.GameService;
import ch.guessbb.sopraserver.service.LobbyService;
import ch.guessbb.sopraserver.websocket.Message;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import tools.jackson.databind.ObjectMapper;

@Controller
public class GameController {

    private final GameService gameService;
    private final ObjectMapper objectMapper;
    private final LobbyService lobbyService;
    private final boolean integrationTestMode = false;

    public GameController(GameService gameService, ObjectMapper objectMapper, LobbyService lobbyService) {
        this.gameService = gameService;
        this.objectMapper = objectMapper;
        this.lobbyService = lobbyService;
    }

    @MessageMapping("/game/{gameId}/guess")
    public void processGuessMessage(@DestinationVariable Long gameId, Message guessMessage) {
        //note: getLobbyById gets the fresh lobby instance from the database that may not have been updated with the most recent Game 
        //to retrieve current information such as scores and guess in rounds, use getGameById in the GameService 
        Lobby currentLobby = lobbyService.getLobbyById(gameId);
        MessageType type = guessMessage.getType();
        if (type == MessageType.GUESS_MESSAGE){
            GuessMessageDTO guessMessageDTO = objectMapper.convertValue(guessMessage.getPayload(), GuessMessageDTO.class);
            gameService.processGuessMessage(guessMessageDTO, currentLobby);
        }
    }

    @MessageMapping("/game/{gameId}/ready")
    public void readyForNextRound(@DestinationVariable Long gameId, Message readyForNextRoundMessage) {
        System.out.println("received ready for next round message for game " + gameId + " with payload " + readyForNextRoundMessage.getPayload());
        /*if (integrationTestMode) {
            User fakeUser = new User();
            fakeUser.setUserId(1L);
            fakeUser.setUsername("1");
            fakeUser.setPassword("1");

            Lobby fakeLobby = new Lobby();
            fakeLobby.setUsers(new ArrayList<>());
            fakeLobby.setScores(new HashMap<>());

            fakeLobby.setLobbyId(gameId);
            fakeLobby.addUser(fakeUser);
            fakeLobby.setCurrentRound(1);
            fakeLobby.setMaxRounds(2);

            Game game = gameService.setupGame(fakeLobby);
            fakeLobby.setGame(game);
            gameService.roundStart(fakeLobby);
        }*/
        if (integrationTestMode){
            Lobby currentLobby = lobbyService.getLobbyById(gameId);
            Game game = gameService.setupGame(currentLobby);
            currentLobby.setGame(game);
            MessageType type = readyForNextRoundMessage.getType();
            if (type == MessageType.READY_FOR_NEXT_ROUND){
                UserGameStatus userGameStatus = objectMapper.convertValue(readyForNextRoundMessage.getPayload(), UserGameStatus.class);
                gameService.readyForNextRound(userGameStatus, currentLobby);
        }
        }
        else {
        Lobby currentLobby = lobbyService.getLobbyById(gameId);
        MessageType type = readyForNextRoundMessage.getType();
        if (type == MessageType.READY_FOR_NEXT_ROUND){
            UserGameStatus userGameStatus = objectMapper.convertValue(readyForNextRoundMessage.getPayload(), UserGameStatus.class);
            gameService.readyForNextRound(userGameStatus, currentLobby);
        }
    }
    }
}
