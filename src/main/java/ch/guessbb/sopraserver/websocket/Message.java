package ch.guessbb.sopraserver.websocket;

import ch.guessbb.sopraserver.constant.MessageType;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Message {
    private MessageType type;
    private Object payload;
}