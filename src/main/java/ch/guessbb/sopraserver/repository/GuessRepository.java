package ch.guessbb.sopraserver.repository;

import ch.guessbb.sopraserver.entity.Guess;
import ch.guessbb.sopraserver.entity.Round;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GuessRepository extends JpaRepository<Guess, Long> {
    Guess findByRoundAndUserUserId(Round round, Long userId);
    List<Guess> findByRound(Round round);

    void deleteByRound(Round round);
}