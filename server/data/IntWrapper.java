package server.data;

import com.fasterxml.jackson.annotation.JsonProperty;

public class IntWrapper {
    @JsonProperty("value")
    volatile Integer value;

    public IntWrapper(){}

    public IntWrapper(int val){
        this();
        value = val;
    }

    public synchronized void setValue(int val) {
        this.value = val;
    }
    public synchronized int getValue() {
        return value;
    }

    public synchronized void increase(){
        value += 1;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
