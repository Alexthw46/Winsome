package server.data;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NumberWrapper<T extends Number> {
    @JsonProperty("value")
    volatile T value;

    public NumberWrapper(){}

    public NumberWrapper(T val){
        this();
        value = val;
    }

    public synchronized void setValue(T val) {
        this.value = val;
    }
    public synchronized T getValue() {
        return value;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
