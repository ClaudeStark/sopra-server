package ch.guessbb.sopraserver.objects;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserGameStatus {
    private Long userId;
    private Boolean isReady;
}