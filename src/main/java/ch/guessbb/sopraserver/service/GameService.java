package ch.guessbb.sopraserver.service;


import ch.guessbb.sopraserver.constant.LobbyState;
import ch.guessbb.sopraserver.constant.MessageType;
import ch.guessbb.sopraserver.entity.*;
import ch.guessbb.sopraserver.objects.*;
import ch.guessbb.sopraserver.repository.*;
import ch.guessbb.sopraserver.rest.dto.GuessMessageDTO;
import ch.guessbb.sopraserver.rest.dto.ResultDTO;
import ch.guessbb.sopraserver.rest.dto.RoundStartDTO;
import ch.guessbb.sopraserver.trains.TrainPositionFetcher;
import ch.guessbb.sopraserver.websocket.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;


@Service
@Transactional
public class GameService {
    private final TrainPositionFetcher trainPositionFetcher;
    private final RoundRepository roundRepository;
    private final GuessRepository guessRepository;
    private final LobbyRepository lobbyRepository;
    private final UserRepository userRepository;
    private final Map<Long, ScheduledFuture<?>> activeTimers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    private final SimpMessagingTemplate messagingTemplate;
    private final Map<Long, Boolean> scoresPublished = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final RoundHistoryRepository roundHistoryRepository;

    public GameService(TrainPositionFetcher trainPositionFetcher, RoundRepository roundRepository, GuessRepository guessRepository, LobbyRepository lobbyRepository, UserRepository userRepository, SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper, RoundHistoryRepository roundHistoryRepository) {
        this.trainPositionFetcher = trainPositionFetcher;
        this.roundRepository = roundRepository;
        this.guessRepository = guessRepository;
        this.lobbyRepository = lobbyRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.roundHistoryRepository = roundHistoryRepository;
    }

    public void setupGame(Lobby currentLobby) {
        try {
            List<Train> trains = trainPositionFetcher.fetchTrains(currentLobby.getMaxRounds());
            for (Train train : trains) {
                trainPositionFetcher.interpolatePosition(train);
            }

            Long lobbyId = currentLobby.getLobbyId();
            scoresPublished.put(lobbyId, false);

            // Runden als Entities in DB speichern
            for (int i = 0; i < currentLobby.getMaxRounds(); i++) {
                Round round = new Round();
                round.setLobby(currentLobby);
                round.setRoundNumber(i + 1);
                round.setTrainData(objectMapper.writeValueAsString(trains.get(i)));
                roundRepository.save(round);

                // Guess Einträge pro Spieler erstellen
                for (User player : currentLobby.getPlayers()) {
                    Guess guess = new Guess();
                    guess.setRound(round);
                    guess.setUser(player);
                    guess.setHasGuessed(false);
                    guessRepository.save(guess);
                }
            }

            roundRepository.flush();
            guessRepository.flush();

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch trains", e);
        }
    }

    public void processGuessMessage(GuessMessageDTO guessMessage, Lobby currentLobby) {
        Long lobbyId = currentLobby.getLobbyId();
        Long userId = guessMessage.getUserId();

        if (!canSubmitGuess(lobbyId)) {
            return;
        }

        // Aktuelle Runde aus DB holen
        List<ch.guessbb.sopraserver.entity.Round> rounds = roundRepository.findByLobbyOrderByRoundNumberAsc(currentLobby);
        int currentRoundIndex = rounds.size() - 1; // letzte Runde ist aktuelle
        ch.guessbb.sopraserver.entity.Round currentRound = rounds.get(currentRoundIndex);

        // Train aus JSON deserialisieren
        Train currentTrain;
        try {
            currentTrain = objectMapper.readValue(currentRound.getTrainData(), Train.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize train data", e);
        }

        // Score berechnen
        double guessDistance = calculateGuessDistance(currentTrain, guessMessage.getXCoordinate(), guessMessage.getYCoordinate());
        int points = calculateScore(currentTrain, guessDistance);
        double roundedDistanceKm = Math.round((guessDistance / 1000.0) * 100.0) / 100.0;

        // Guess in DB updaten
        Guess guess = guessRepository.findByRoundAndUserUserId(currentRound, userId);
        guess.setLat(guessMessage.getXCoordinate().floatValue());
        guess.setLon(guessMessage.getYCoordinate().floatValue());
        guess.setPoints(points);
        guess.setDistanceToTrain((float) roundedDistanceKm);
        guess.setHasGuessed(true);
        guessRepository.save(guess);

        // Prüfen ob alle geraten haben
        boolean allGuessed = rounds.get(currentRoundIndex)
                .equals(currentRound) && checkAllGuessed(currentRound);

        if (allGuessed) {
            ScheduledFuture<?> timer = activeTimers.get(lobbyId);
            if (timer != null) timer.cancel(false);
            allowedToPublish(currentLobby);
        }

        Message message = new Message(MessageType.GAME_STATE, userId);
        messagingTemplate.convertAndSend("/topic/game/" + lobbyId, message);
    }

    private boolean checkAllGuessed( Round round) {
        return guessRepository.findByRound(round)
                .stream()
                .allMatch(Guess::getHasGuessed);
    }

    public void readyForNextRound(UserGameStatus userGameStatus, Lobby currentLobby) {
        Boolean allAreReady = updateUserGameStatus(userGameStatus, currentLobby);

        if (allAreReady) {
            roundStart(currentLobby);
        }
    }

    private final Map<Long, Map<Long, Boolean>> roundReadyStatus = new ConcurrentHashMap<>();

    public Boolean updateUserGameStatus(UserGameStatus userGameStatus, Lobby currentLobby) {
        Long lobbyId = currentLobby.getLobbyId();
        Long userId = userGameStatus.getUserId();

        // Ready Status in Memory speichern
        roundReadyStatus.computeIfAbsent(lobbyId, k -> new ConcurrentHashMap<>())
                .put(userId, userGameStatus.getIsReady());

        Map<Long, Boolean> readyMap = roundReadyStatus.get(lobbyId);

        // Prüfen ob alle Spieler ready sind
        for (User player : currentLobby.getPlayers()) {
            Boolean isReady = readyMap.get(player.getUserId());
            if (isReady == null || !isReady) {
                return false;
            }
        }

        // Alle ready → Map zurücksetzen für nächste Runde
        roundReadyStatus.remove(lobbyId);
        return true;
    }

    public boolean canSubmitGuess(long gameId) {
        return activeTimers.containsKey(gameId);
    }

    public void roundStart(Lobby currentLobby) {
        Long lobbyId = currentLobby.getLobbyId();

        // Aktuelle Runde aus DB holen
        List<Round> rounds = roundRepository.findByLobbyOrderByRoundNumberAsc(currentLobby);

        // Nächste Runde bestimmen
        long completedRounds = rounds.stream()
                .filter(r -> guessRepository.findByRound(r).stream().allMatch(Guess::getHasGuessed))
                .count();
        int currentRoundNumber = (int) completedRounds + 1;

        scoresPublished.put(lobbyId, false);

        // Train aus DB holen und Koordinaten verstecken
        Round currentRound = rounds.get(currentRoundNumber - 1);
        Train trainWithoutCoordinates;
        try {
            trainWithoutCoordinates = objectMapper.readValue(currentRound.getTrainData(), Train.class);
            trainWithoutCoordinates.setCurrentX(0);
            trainWithoutCoordinates.setCurrentY(0);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize train data", e);
        }

        RoundStartDTO roundStartDTO = new RoundStartDTO(currentRoundNumber, currentLobby.getMaxRounds(), trainWithoutCoordinates);
        Message message = new Message(MessageType.ROUND_START, roundStartDTO);
        messagingTemplate.convertAndSend("/topic/game/" + lobbyId, message);

        ScheduledFuture<?> timer = scheduler.schedule(
                () -> roundEnd(currentLobby),
                45,
                TimeUnit.SECONDS
        );
        activeTimers.put(lobbyId, timer);
    }

    public void roundEnd(Lobby currentLobby) {
        Long lobbyId = currentLobby.getLobbyId();
        messagingTemplate.convertAndSend("/topic/game/" + lobbyId,
                new Message(MessageType.ROUND_END, null));

        ScheduledFuture<?> lastMessagesTimer = scheduler.schedule(
                () -> allowedToPublish(currentLobby),
                3,
                TimeUnit.SECONDS
        );
        activeTimers.put(lobbyId, lastMessagesTimer);
    }

    public void allowedToPublish(Lobby currentLobby) {
        if (!scoresPublished.get(currentLobby.getLobbyId())) {
            publishScores(currentLobby);
        }
    }

    public void publishScores(Lobby currentLobby) {
        Long lobbyId = currentLobby.getLobbyId();
        activeTimers.remove(lobbyId);
        scoresPublished.put(lobbyId, true);

        // Aktuelle Runde aus DB holen
        List<Round> rounds = roundRepository.findByLobbyOrderByRoundNumberAsc(currentLobby);
        long completedRounds = rounds.stream()
                .filter(r -> guessRepository.findByRound(r).stream().allMatch(Guess::getHasGuessed))
                .count();
        int currentRoundNumber = (int) completedRounds;
        Round currentRound = rounds.get(currentRoundNumber - 1);

        // Train aus JSON holen
        Train train;
        try {
            train = objectMapper.readValue(currentRound.getTrainData(), Train.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize train data", e);
        }

        // Guesses aus DB holen
        List<Guess> guesses = guessRepository.findByRound(currentRound);

        // Total points pro Spieler berechnen
        List<UserResult> userResults = new ArrayList<>();
        for (Guess guess : guesses) {
            Long userId = guess.getUser().getUserId();

            // Total points aus allen Runden
            int totalPoints = roundRepository.findByLobbyOrderByRoundNumberAsc(currentLobby)
                    .stream()
                    .flatMap(r -> guessRepository.findByRound(r).stream())
                    .filter(g -> g.getUser().getUserId().equals(userId))
                    .mapToInt(g -> g.getPoints() != null ? g.getPoints() : 0)
                    .sum();

            int roundPoints = guess.getPoints() != null ? guess.getPoints() : 0;
            long xCoordinate = guess.getLat() != null ? guess.getLat().longValue() : 0;
            long yCoordinate = guess.getLon() != null ? guess.getLon().longValue() : 0;
            double distance = guess.getDistanceToTrain() != null ? guess.getDistanceToTrain() : Double.MAX_VALUE;

            userResults.add(new UserResult(userId, totalPoints, roundPoints, xCoordinate, yCoordinate, distance));
        }

        ResultDTO resultDTO = new ResultDTO(currentRoundNumber, userResults, train);
        Message message = new Message(MessageType.SCORES, resultDTO);
        messagingTemplate.convertAndSend("/topic/game/" + lobbyId, message);

        if (currentLobby.getMaxRounds() == currentRoundNumber) {
            gameTearDown(currentLobby);
        }
    }

    /**
     * Calculates a score (0–1000) for a player's train position guess.
     *
     * Uses Gaussian decay: score = 1000 * e^(-k * errorRatio²)
     * where errorRatio = guessDistance / totalLineLength
     *
     * Decay constant k is chosen so that errorRatio = 1.0 (guess is off by a full
     * line length) yields a score of ~5, giving a near-zero floor for bad guesses.
     *
     */


    public int calculateScore(Train train, double guessDistance) {
        // 1. Total length of the train line (origin → destination)
        double ldx = train.getLineDestination().getXCoordinate()
                - train.getLineOrigin().getXCoordinate();
        double ldy = train.getLineDestination().getYCoordinate()
                - train.getLineOrigin().getYCoordinate();
        double totalLineLength = Math.sqrt(Math.pow(ldx, 2) + Math.pow(ldy, 2));

        System.out.println("calculateScore: origin=(" + train.getLineOrigin().getXCoordinate() + "," + train.getLineOrigin().getYCoordinate() + 
                           "), dest=(" + train.getLineDestination().getXCoordinate() + "," + train.getLineDestination().getYCoordinate() +
                           "), lineLength=" + totalLineLength + ", guessDistance=" + guessDistance);

        // Edge case: degenerate line (origin == destination)
        // Fall back to a fixed reference distance of 1 km in EPSG:3857 meters
        if (totalLineLength < 1.0) {
            totalLineLength = 1000.0;
            System.out.println("Using fallback line length: 1000");
        }

        // 2. Relative error ratio (clamped — can't do worse than a full line length)
        double errorRatio = guessDistance / totalLineLength;

        // 3. Power-modified exponential decay
        //    p controls curve shape: lower p = steeper near 0, flatter tail
        //    k is anchored so that errorRatio = 0.5 → exactly 100 pts
        //    k = ln(10) / 0.5^p
        final double p = 1.5;
        final double k = Math.log(5.0) / Math.pow(0.5, p);
        double rawScore = 1000.0 * Math.exp(-k * Math.pow(errorRatio, p));
        System.out.println("errorRatio=" + errorRatio + ", p=" + p + ", k=" + k + ", rawScore=" + rawScore);

        // 4. Round and clamp to [0, 1000]
        int finalScore = (int) Math.min(1000, Math.max(0, Math.round(rawScore)));
        System.out.println("finalScore=" + finalScore);

        double absoluteKm = guessDistance / 1000.0; // EPSG:3857 meters → km
        final double lambda = 0.01; // tune this: higher = harsher absolute penalty
        double dampener = Math.exp(-lambda * absoluteKm);

        finalScore = (int)(finalScore * dampener);

        return finalScore;
    }

    /**
     * Helper method to calculate the distance between the player's guess and the train's actual position.
     */
    public double calculateGuessDistance(Train train, Long playerX, Long playerY) {
        double dx = playerX - train.getCurrentX();
        double dy = playerY - train.getCurrentY();
        return Math.sqrt(Math.pow(dx, 2) + Math.pow(dy, 2));
    }

    public void gameTearDown(Lobby currentLobby) {
        Long lobbyId = currentLobby.getLobbyId();

        // RoundHistory für jeden Spieler pro Runde erstellen
        List<Round> rounds = roundRepository.findByLobbyOrderByRoundNumberAsc(currentLobby);
        for (Round round : rounds) {
            List<Guess> guesses = guessRepository.findByRound(round);
            for (Guess guess : guesses) {
                RoundHistory roundHistory = new RoundHistory();
                roundHistory.setLobby(currentLobby);
                roundHistory.setUser(guess.getUser());
                roundHistory.setRoundNumber(round.getRoundNumber());
                roundHistory.setPoints(guess.getPoints() != null ? guess.getPoints() : 0);
                roundHistory.setDistanceToTrain(guess.getDistanceToTrain() != null ? guess.getDistanceToTrain() : 0f);
                roundHistoryRepository.save(roundHistory);
            }
        }

        // Scoreboard updaten pro Spieler
        for (User player : currentLobby.getPlayers()) {
            Long userId = player.getUserId();
            List<RoundHistory> playerHistory = roundHistoryRepository.findByUserUserId(userId);

            UserScoreboard scoreboard = player.getUserScoreboard();
            scoreboard.setPlayedGames(scoreboard.getPlayedGames() + 1);
            scoreboard.setPlayedRounds(scoreboard.getPlayedRounds() + rounds.size());
            scoreboard.setTotalPoints(playerHistory.stream().mapToLong(r -> r.getPoints()).sum());
            scoreboard.setBestRoundPoints(playerHistory.stream().mapToLong(r -> r.getPoints()).max().orElse(0));
            scoreboard.setGuessingPrecision((float) playerHistory.stream().mapToDouble(r -> r.getDistanceToTrain()).average().orElse(0));
            player.setUserScoreboard(scoreboard);
            userRepository.save(player);
        }

        // Lobby auf FINISHED setzen
        currentLobby.setLobbyState(LobbyState.FINISHED);
        lobbyRepository.save(currentLobby);

        // Runden und Guesses löschen
        for (Round round : rounds) {
            guessRepository.deleteByRound(round);
        }
        roundRepository.deleteByLobby(currentLobby);

        // Cleanup
        activeTimers.remove(lobbyId);
        scoresPublished.remove(lobbyId);
    }

    public void cleanupAllTimers() {

        activeTimers.forEach((gameId, timer) -> {
            if (timer != null) {
                timer.cancel(false);
            }
        });

        activeTimers.clear();
    }
}
