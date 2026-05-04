package ch.guessbb.sopraserver.rest.dto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserPutDTO {
    private String username;
    private String password;
    private String email;
    private String userBio;
}