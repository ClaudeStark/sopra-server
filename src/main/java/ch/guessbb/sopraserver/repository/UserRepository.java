package ch.guessbb.sopraserver.repository;

import ch.guessbb.sopraserver.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    User findByUserProfileUsername(String username);
    User findByUserProfileEmail(String email);
    User findByToken(String token);
}