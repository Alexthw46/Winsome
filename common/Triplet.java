package common;

import java.io.Serializable;
import java.util.UUID;

public record Triplet(String args, int op, UUID token) implements Serializable {

}
