package ch.guessbb.sopraserver.rest.dto;

import ch.guessbb.sopraserver.objects.Train;
import ch.guessbb.sopraserver.objects.UserResult;
import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResultDTO {
    private Integer currentRound;
    private List<UserResult> userResults;
    private Train train;
}