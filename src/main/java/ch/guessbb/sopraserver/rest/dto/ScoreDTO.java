package ch.guessbb.sopraserver.rest.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScoreDTO {
    private Long userId;
    private Integer points;
}