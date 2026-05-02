package ch.guessbb.sopraserver.repository;

import ch.guessbb.sopraserver.entity.GameResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository("gameRepository")
public interface GameRepository extends JpaRepository<GameResult, Long> {

    GameResult findByGameId(Long gameId);
}
