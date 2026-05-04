package ch.guessbb.sopraserver.repository;

import ch.guessbb.sopraserver.entity.RoundHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoundHistoryRepository extends JpaRepository<RoundHistory, Long> {
    List<RoundHistory> findByUserUserId(Long userId);
    List<RoundHistory> findByLobbyLobbyId(Long lobbyId);
}