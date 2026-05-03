package ch.guessbb.sopraserver.repository;

import ch.guessbb.sopraserver.entity.Lobby;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LobbyRepository extends JpaRepository<Lobby, Long> {

    Optional<Lobby> findByLobbyCode(String lobbyCode);

    boolean existsByLobbyCode(String lobbyCode);
}