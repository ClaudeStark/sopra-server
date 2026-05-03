package ch.guessbb.sopraserver.security;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuthHeader {
    private Long userId;
    private String token;
}