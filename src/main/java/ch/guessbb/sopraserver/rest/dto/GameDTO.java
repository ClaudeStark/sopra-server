package ch.guessbb.sopraserver.rest.dto;

import ch.guessbb.sopraserver.objects.Round;
import ch.guessbb.sopraserver.objects.Train;

import java.util.List;

public class GameDTO {

    private Long gameId;

    private List<Round> rounds;

    private List<Train> trains;


    public Long getGameId() {return gameId;}
    public void setGameId(Long gameId) {this.gameId = gameId;}

    public List<Round> getRounds() {return rounds;}
    public void setRounds(List<Round> rounds) {this.rounds = rounds;}

    public List<Train> getTrains() {return trains;}
    public void setTrains(List<Train> trains) {this.trains = trains;}

}
