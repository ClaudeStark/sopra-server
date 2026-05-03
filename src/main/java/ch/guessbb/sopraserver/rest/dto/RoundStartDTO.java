package ch.guessbb.sopraserver.rest.dto;

import ch.guessbb.sopraserver.objects.Train;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoundStartDTO {
    private Integer roundNumber;
    private Integer maxRounds;
    private Train train;
}