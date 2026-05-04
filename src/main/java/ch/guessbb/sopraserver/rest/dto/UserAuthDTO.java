package ch.guessbb.sopraserver.rest.dto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserAuthDTO {
    private Long userId;
    private String token;
}