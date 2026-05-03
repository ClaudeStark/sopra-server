package ch.guessbb.sopraserver.rest.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginPostDTO {
    private String username;
    private String password;
}